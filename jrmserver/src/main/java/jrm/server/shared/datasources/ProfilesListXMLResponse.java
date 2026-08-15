package jrm.server.shared.datasources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.lang3.StringUtils;

import jrm.profile.manager.ProfileNFO;
import jrm.server.shared.datasources.XMLRequest.Operation;
import lombok.val;

/**
 * Handles XML responses for managing the list of profiles. This class processes operations such as fetching the list of profiles,
 * adding new profiles, removing existing profiles, and performing custom operations like dropping the cache for a specific profile.
 */
public class ProfilesListXMLResponse extends XMLResponse {

    /** XML element name for the operation status. */
    private static final String STATUS = "status";

    /** XML element name for the root response wrapper. */
    private static final String RESPONSE = "response";

    /** XML attribute name for the parent directory identifier. */
    private static final String PARENT = "Parent";

    /** XML element name for the xmlfiles directory context. */
    private static final String XMLFILES = "xmlfiles";

    private static final String FILE = "File";

    private static final String PATH_TRAVERSAL = "Invalid file path: path traversal detected";

    /**
     * Constructs a new ProfilesListXMLResponse.
     *
     * @param request the incoming XML request containing operation details
     * 
     * @throws IOException if an I/O error occurs during initialization
     * @throws XMLStreamException if an XML writing error occurs during initialization
     */
    public ProfilesListXMLResponse(XMLRequest request) throws IOException, XMLStreamException {
        super(request);
    }

    /**
     * Fetches the list of profiles in the specified directory and writes it to the XML response.
     *
     * @param operation the operation details from the request, potentially containing the parent directory path
     * 
     * @throws XMLStreamException if an error occurs while writing the XML response
     */
    @Override
    protected void fetch(Operation operation) throws XMLStreamException {
        Path dir = resolveParentDir(operation);
        val rows = ProfileNFO.list(request.getSession(), dir.toFile());
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");
        writer.writeElement("startRow", "0");
        writer.writeElement("parent", pathAbstractor.getRelativePath(dir).toString());
        writer.writeElement("endRow", Integer.toString(rows.size() - 1));
        writer.writeElement("totalRows", Integer.toString(rows.size()));
        writer.writeStartElement("data");
        for (var i = 0; i < rows.size(); i++)
            writeRecord(rows.get(i));
        writer.writeEndElement();
        writer.writeEndElement();
    }

    /**
     * Writes the XML representation of a single profile record.
     *
     * @param nfo the profile NFO object containing the metadata to be written
     * 
     * @throws XMLStreamException if an error occurs while writing to the XML stream
     */
    private void writeRecord(final ProfileNFO nfo) throws XMLStreamException {
        writer.writeEmptyElement("record");
        writer.writeAttribute("Name", nfo.getName());
        writer.writeAttribute(PARENT, pathAbstractor.getRelativePath(nfo.getFile().getParentFile().toPath()).toString());
        writer.writeAttribute(FILE, nfo.getFile().getName());
        writer.writeAttribute("version", nfo.getHTMLVersion());
        writer.writeAttribute("haveSets", nfo.getHTMLHaveSets());
        writer.writeAttribute("haveRoms", nfo.getHTMLHaveRoms());
        writer.writeAttribute("haveDisks", nfo.getHTMLHaveDisks());
        writer.writeAttribute("created", nfo.getHTMLCreated());
        writer.writeAttribute("scanned", nfo.getHTMLScanned());
        writer.writeAttribute("fixed", nfo.getHTMLFixed());
    }

    /**
     * Adds a new profile to the list by copying a source file to the target directory.
     *
     * @param operation the operation details containing the source path, target parent directory, and target file name
     * 
     * @throws XMLStreamException if an error occurs while writing the XML response
     */
    @Override
    protected void add(Operation operation) throws XMLStreamException {
        if (operation.hasData("Src")) {
            try {
                Path dir = resolveParentDir(operation);
                val src = pathAbstractor.getAbsolutePath(operation.getData("Src"));
                if (Files.exists(src) && Files.isRegularFile(src)) {
                    copyAndWriteProfile(dir, src, operation);
                } else
                    failure("Source file does not exist");
            } catch (SecurityException ex) {
                failure(ex.getMessage());
            }
        } else
            failure("Src is needed");
    }

    private void copyAndWriteProfile(Path dir, Path src, Operation operation) throws XMLStreamException {
        try {
            Path dst = resolveContainedFile(dir, operation.getData(FILE));
            if (!src.equals(dst))
                Files.copy(src, dst, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
            final var nfo = ProfileNFO.load(request.getSession(), dst.toFile());
            writer.writeStartElement(RESPONSE);
            writer.writeElement(STATUS, "0");
            writer.writeStartElement("data");
            writeRecord(nfo);
            writer.writeEndElement();
            writer.writeEndElement();
        } catch (IOException ex) {
            failure(ex.getMessage());
        }
    }

    /**
     * Removes an existing profile from the list and deletes its associated files.
     *
     * @param operation the operation details containing the parent directory and the file name to remove
     * 
     * @throws XMLStreamException if an error occurs while writing the XML response
     */
    @Override
    protected void remove(Operation operation) throws XMLStreamException {
        try {
            Path dir = resolveParentDir(operation);
            val dst = resolveContainedFile(dir, operation.getData(FILE));
            ProfileNFO nfo = ProfileNFO.load(request.getSession(), dst.toFile());
            if (request.session.getCurrProfile() == null || !request.getSession().getCurrProfile().getNfo().equals(nfo)) {
                if (nfo.delete()) {
                    writer.writeStartElement(RESPONSE);
                    writer.writeElement(STATUS, "0");
                    writer.writeStartElement("data");
                    writer.writeEmptyElement("record");
                    writer.writeAttribute(PARENT, pathAbstractor.getRelativePath(nfo.getFile().getParentFile().toPath()).toString());
                    writer.writeAttribute(FILE, nfo.getFile().getName());
                    writer.writeEndElement();
                    writer.writeEndElement();
                } else
                    failure("Failed to delete profile");
            } else
                failure("Can't delete current loaded profile");
        } catch (SecurityException ex) {
            failure(ex.getMessage());
        }
    }

    /**
     * Performs custom operations on a profile, such as dropping its cache file.
     *
     * @param operation the operation details containing the custom operation ID and target file information
     * 
     * @throws XMLStreamException if an error occurs while writing the XML response
     * @throws IOException if an I/O error occurs while accessing the file system
     */
    @Override
    protected void custom(Operation operation) throws XMLStreamException, IOException {
        if ("DropCache".equals(operation.getOperationId().toString())) {
            try {
                Path dir = resolveParentDir(operation);
                val dst = resolveContainedFile(dir, operation.getData(FILE));
                if (Files.isRegularFile(dst)) {
                    val cache = resolveContainedFile(dir, operation.getData(FILE) + ".cache");
                    if (Files.exists(cache) && !cache.toFile().delete())
                        failure("Can't delete " + cache);
                    else
                        success();
                } else
                    failure("Can't find " + dst);
            } catch (SecurityException ex) {
                failure(ex.getMessage());
            }
        } else
            super.custom(operation);
    }

    /**
     * Resolves the profiles parent directory from the operation, defaulting to the session {@code xmlfiles} folder.
     */
    private Path resolveParentDir(Operation operation) {
        Path dir = request.getSession().getUser().getSettings().getWorkPath().resolve(XMLFILES).toAbsolutePath().normalize();
        if (operation.hasData(PARENT) && !StringUtils.isEmpty(operation.getData(PARENT)))
            dir = pathAbstractor.getAbsolutePath(operation.getData(PARENT)).toAbsolutePath().normalize();
        return dir;
    }

    /**
     * Resolves {@code fileName} as a single path segment under {@code dir}. Rejects absolute paths, multi-segment
     * relative paths, and any result that escapes {@code dir} after normalization.
     *
     * @throws SecurityException if the name is missing, not a plain file name, or escapes the base directory
     */
    static Path resolveContainedFile(Path dir, String fileName) {
        if (StringUtils.isEmpty(fileName))
            throw new SecurityException(PATH_TRAVERSAL);
        final Path name = Paths.get(fileName);
        if (name.isAbsolute() || name.getNameCount() != 1 || ".".equals(fileName) || "..".equals(fileName))
            throw new SecurityException(PATH_TRAVERSAL);
        final Path base = dir.toAbsolutePath().normalize();
        final Path dst = base.resolve(name.getFileName().toString()).toAbsolutePath().normalize();
        if (!dst.startsWith(base) || dst.equals(base))
            throw new SecurityException(PATH_TRAVERSAL);
        return dst;
    }
}