package jrm.server.shared.datasources;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import jrm.server.shared.datasources.XMLRequest.Operation;
import jrm.xml.SimpleAttribute;

/**
 * Handles XML responses for selecting a remote root directory or path. This class processes fetch operations to list available root
 * directories or predefined workspace paths (e.g., Work, Shared, Presets) based on the operation context.
 */
public class RemoteRootChooserXMLResponse extends XMLResponse {

    /** Constant representing the workspace "Work" directory path placeholder. */
    private static final String WORK = "%work";

    /**
     * Constructs a new RemoteRootChooserXMLResponse.
     *
     * @param request the incoming XML request containing operation details
     * 
     * @throws IOException if an I/O error occurs during initialization
     * @throws XMLStreamException if an XML writing error occurs during initialization
     */
    public RemoteRootChooserXMLResponse(XMLRequest request) throws IOException, XMLStreamException {
        super(request);
    }

    /**
     * Fetches the available root paths and writes them to the XML response. The paths returned depend on whether the session is a
     * multiuser server session and the specific context of the operation (e.g., importing settings, listing source directories).
     *
     * @param operation the operation details from the request, containing the context
     * 
     * @throws XMLStreamException if an error occurs while writing the XML response
     * @throws IOException if an I/O error occurs while accessing the file system
     */
    @Override
    protected void fetch(Operation operation) throws XMLStreamException, IOException {
        Map<String, Path> paths = getPaths(operation);
        writer.writeStartElement("response");
        writer.writeElement("status", "0");
        writer.writeElement("startRow", "0");
        writer.writeElement("endRow", Long.toString(paths.size() - 1L));
        writer.writeElement("totalRows", Long.toString(paths.size()));
        writer.writeStartElement("data");
        for (final var root : paths.entrySet()) {
            writer.writeElement("record", new SimpleAttribute("Name", root.getKey()), new SimpleAttribute("Path", root.getValue()));
        }
        writer.writeEndElement();
        writer.writeEndElement();
    }

    /**
     * Determines the appropriate root paths to return based on the operation context and session type.
     *
     * @param operation the operation details containing the context data
     * 
     * @return a map of display names to their corresponding {@link Path} objects
     */
    protected Map<String, Path> getPaths(Operation operation) {
        Map<String, Path> paths = new LinkedHashMap<>();
        if (request.session.isServer() && request.session.isMultiuser()) {
            switch (operation.getData("context")) {
                case "listSrcDir", "importDat", "addDatSrc", "addDat", "tfSrcDir", "tfDstDat" -> {
                    paths.put("Work", Paths.get(WORK));
                    paths.put("Shared", Paths.get("%shared"));
                }
                case "importSettings", "exportSettings" -> paths.put("Presets", Paths.get("%presets"));
                default -> paths.put("Work", Paths.get(WORK));
            }
            return paths;
        }
        paths.put("Work", Paths.get(WORK));
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            try {
                if (Files.isDirectory(root) && Files.exists(root))
                    paths.put((root.getFileName() != null ? root.getFileName() : root).toString(), root);
            } catch (Exception _) {
                // do nothing
            }
        }
        return paths;
    }
}