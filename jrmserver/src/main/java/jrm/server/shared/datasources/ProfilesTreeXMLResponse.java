package jrm.server.shared.datasources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.io.FileUtils;

import jrm.misc.Tree.Node;
import jrm.profile.manager.Dir;
import jrm.profile.manager.DirTree;
import jrm.server.shared.datasources.XMLRequest.Operation;
import lombok.val;

/**
 * Handles XML responses for managing the profiles directory tree. This class processes operations such as fetching the tree
 * structure, adding new directories, updating existing directory names, and removing directories. It interacts with the user's
 * workspace to maintain a hierarchical view of profile lists.
 */
public class ProfilesTreeXMLResponse extends XMLResponse {

    /** XML element name for the operation status. */
    private static final String STATUS = "status";

    /** XML element name for the root response wrapper. */
    private static final String RESPONSE = "response";

    /** XML attribute name for the parent directory identifier. */
    private static final String PARENT_ID = "ParentID";

    /** XML attribute name indicating if the node represents a folder. */
    private static final String IS_FOLDER = "isFolder";

    /** XML attribute name for the directory or file title/name. */
    private static final String TITLE = "title";

    /** XML element name for a single record in the data payload. */
    private static final String RECORD = "record";

    /**
     * Constructs a new ProfilesTreeXMLResponse.
     *
     * @param request the incoming XML request containing operation details
     * 
     * @throws IOException if an I/O error occurs during initialization
     * @throws XMLStreamException if an XML writing error occurs during initialization
     */
    public ProfilesTreeXMLResponse(XMLRequest request) throws IOException, XMLStreamException {
        super(request);
    }

    /**
     * Recursively counts the total number of leaf nodes in the directory tree.
     *
     * @param node the current tree node to evaluate
     * 
     * @return the total count of leaf nodes under the given node
     */
    private int countNode(Node<Dir> node) {
        var count = 0;
        for (val child : node) {
            if (child.getChildCount() > 0)
                count += countNode(child);
            else
                count++;
        }
        return count;
    }

    /**
     * Recursively writes the XML representation of a directory tree node and its children.
     *
     * @param writer the XML stream writer to output the data
     * @param node the current tree node to process
     * @param parentID the string identifier of the parent node, or null if it is a root node
     * @param id an atomic integer used to generate and track unique node identifiers
     * 
     * @throws XMLStreamException if an error occurs while writing to the XML stream
     */
    private void outputNode(XMLStreamWriter writer, Node<Dir> node, String parentID, AtomicInteger id) throws XMLStreamException {
        var strID = id.toString();
        if (id.get() > 0) {
            request.getSession().putProfileList(id.get(), node.getData().getFile().toPath());
            writer.writeStartElement(RECORD);
            writer.writeAttribute("ID", id.toString());
            writer.writeAttribute("Path", pathAbstractor.getRelativePath(node.getData().getFile().toPath()).toString());
            writer.writeAttribute(TITLE, node.getData().getFile().getName());
            writer.writeAttribute(IS_FOLDER, "true");
            if (parentID != null)
                writer.writeAttribute(PARENT_ID, parentID);
            writer.writeEndElement();
        } else
            request.getSession().newProfileList();
        id.incrementAndGet();
        for (val child : node)
            outputNode(writer, child, strID, id);
    }

    /**
     * Fetches the complete directory tree structure and writes it to the XML response. It creates the root "xmlfiles" directory if
     * it does not exist.
     *
     * @param operation the operation details from the request (unused in fetch, but required by signature)
     * 
     * @throws XMLStreamException if an error occurs while writing the XML response
     * @throws IOException if an I/O error occurs while accessing the file system
     */
    @Override
    protected void fetch(Operation operation) throws XMLStreamException, IOException {
        val rootpath = request.getSession().getUser().getSettings().getWorkPath().resolve("xmlfiles").toAbsolutePath().normalize();
        Files.createDirectories(rootpath);
        val root = new DirTree(rootpath.toFile());
        int nodecount = countNode(root.getRoot());
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");
        writer.writeElement("startRow", "0");
        writer.writeElement("endRow", Integer.toString(nodecount - 1));
        writer.writeElement("totalRows", Integer.toString(nodecount));
        writer.writeStartElement("data");
        outputNode(writer, root.getRoot(), null, new AtomicInteger());
        writer.writeEndElement();
        writer.writeEndElement();
    }

    /**
     * Adds a new directory to the profile list based on the request data.
     *
     * @param operation the operation details containing the target path and the new directory title
     * 
     * @throws XMLStreamException if an error occurs while writing the XML response
     * @throws IOException if an I/O error occurs while creating the directory
     */
    @Override
    protected void add(Operation operation) throws XMLStreamException, IOException {
        try {
            var key = request.getSession().getLastProfileListKey() + 1;
            var basepath = operation.getData("Path");
            if (basepath == null || basepath.isEmpty())
                basepath = request.getSession().getUser().getSettings().getWorkPath().resolve("xmlfiles").toAbsolutePath().normalize().toString();
            var title = operation.getData(TITLE);
            var path = Paths.get(basepath).resolve(title).toAbsolutePath().normalize();
            if (!path.startsWith(Paths.get(basepath).toAbsolutePath().normalize()))
                throw new SecurityException("Invalid path: path traversal detected");
            path = Files.createDirectory(path);
            request.getSession().putProfileList(key, path);
            writer.writeStartElement(RESPONSE);
            writer.writeElement(STATUS, "0");
            writer.writeStartElement("data");
            writer.writeStartElement(RECORD);
            writer.writeAttribute("ID", Integer.toString(key));
            writer.writeAttribute("Path", pathAbstractor.getRelativePath(path).toString());
            writer.writeAttribute(TITLE, operation.getData(TITLE));
            writer.writeAttribute(IS_FOLDER, "true");
            writer.writeAttribute(PARENT_ID, operation.getData(PARENT_ID));
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndElement();
        } catch (SecurityException ex) {
            failure(ex.getMessage());
        }
    }

    /**
     * Updates (renames) an existing directory in the profile list.
     *
     * @param operation the operation details containing the directory ID and the new title
     * 
     * @throws XMLStreamException if an error occurs while writing the XML response
     * @throws IOException if an I/O error occurs while moving/renaming the directory
     */
    @Override
    protected void update(Operation operation) throws XMLStreamException, IOException {
        try {
            var id = Integer.valueOf(operation.getData("ID"));
            var path = request.getSession().getProfileList(id);
            if(path==null) {
                failure();
                return;
            }
            var title = operation.getData(TITLE);
            var parent = path.getParent();
            var newPath = parent.resolve(title).toAbsolutePath().normalize();
            if (!newPath.startsWith(parent.toAbsolutePath().normalize()))
                throw new SecurityException("Invalid path: path traversal detected");
            path = Files.move(path, newPath);
            request.getSession().putProfileList(id, path);
            writer.writeStartElement(RESPONSE);
            writer.writeElement(STATUS, "0");
            writer.writeStartElement("data");
            writer.writeStartElement(RECORD);
            writer.writeAttribute("ID", id.toString());
            writer.writeAttribute("Path", pathAbstractor.getRelativePath(path).toString());
            writer.writeAttribute(TITLE, title);
            writer.writeAttribute(IS_FOLDER, "true");
            writer.writeAttribute(PARENT_ID, operation.getOldValues().get(PARENT_ID));
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndElement();
        } catch (SecurityException ex) {
            failure(ex.getMessage());
        }
    }

    /**
     * Removes a directory and all its contents from the profile list.
     *
     * @param operation the operation details containing the directory ID to remove
     * 
     * @throws XMLStreamException if an error occurs while writing the XML response
     * @throws IOException if an I/O error occurs while deleting the directory
     */
    @Override
    protected void remove(Operation operation) throws XMLStreamException, IOException {
        var id = Integer.valueOf(operation.getData("ID"));
        var path = request.getSession().getProfileList(id);
        if(path==null) {
            failure();
            return;
        }
        FileUtils.deleteDirectory(path.toFile());
        request.getSession().removeProfileList(id);
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");
        writer.writeStartElement("data");
        writer.writeStartElement(RECORD);
        writer.writeAttribute("ID", id.toString());
        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndElement();
    }
}