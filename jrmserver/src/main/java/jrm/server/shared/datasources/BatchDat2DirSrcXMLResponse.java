package jrm.server.shared.datasources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.lang3.StringUtils;

import jrm.misc.SettingsEnum;
import jrm.server.shared.datasources.XMLRequest.Operation;
import jrm.xml.SimpleAttribute;

/**
 * XML response handler for batch DAT to directory source directories.
 * <p>
 * This class processes XML requests to manage the list of source directories used in batch DAT to directory operations, supporting
 * fetch, add, and remove operations.
 * </p>
 */
public class BatchDat2DirSrcXMLResponse extends XMLResponse {

    /** XML element name for a record. */
    private static final String RECORD = "record";
    /** XML element name for the status. */
    private static final String STATUS = "status";
    /** XML element name for the response wrapper. */
    private static final String RESPONSE = "response";

    /**
     * Constructs a new batch DAT to directory source directories XML response.
     *
     * @param request the XML request containing the operation to process
     * 
     * @throws IOException if an I/O error occurs during initialization
     * @throws XMLStreamException if an XML stream error occurs during initialization
     */
    public BatchDat2DirSrcXMLResponse(XMLRequest request) throws IOException, XMLStreamException {
        super(request);
    }

    /**
     * Fetches the current list of source directories and writes them to the XML response.
     *
     * @param operation the operation containing request parameters
     * 
     * @throws XMLStreamException if an error occurs while writing the XML stream
     */
    @Override
    protected void fetch(Operation operation) throws XMLStreamException {
        final String[] srcdirs;
        synchronized (request.getSession().getUser().getSettings()) {
            srcdirs = getSrcDirs();
        }
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");
        fetchArray(operation, srcdirs.length, (i, _) -> writeRecord(srcdirs[i]));
        writer.writeEndElement();
    }

    /**
     * Retrieves the array of source directories from the user's settings.
     *
     * @return an array of source directory paths
     */
    private String[] getSrcDirs() {
        return StringUtils.split(request.getSession().getUser().getSettings().getProperty(SettingsEnum.dat2dir_srcdirs), '|');
    }

    /**
     * Adds new source directories to the settings if they do not already exist.
     *
     * @param operation the operation containing the "name" data to add
     * 
     * @throws XMLStreamException if an error occurs while writing the XML stream
     */
    @Override
    protected void add(Operation operation) throws XMLStreamException {
        if (operation.hasData("name")) {
            final List<String> names;
            synchronized (request.getSession().getUser().getSettings()) {
                final List<String> lsrcdirs = new ArrayList<>(Arrays.asList(getSrcDirs()));
                names = operation.getDatas("name").stream().filter(n -> !lsrcdirs.contains(n)).toList();
                if (!names.isEmpty()) {
                    lsrcdirs.addAll(names);
                    save(lsrcdirs);
                }
            }
            if (!names.isEmpty()) {
                writeResponse(operation, names);
            } else {
                failure("Entry already exists");
            }
        } else {
            failure("name is missing in request");
        }
    }

    /**
     * Writes the response for newly added source directories.
     *
     * @param operation the operation containing request parameters
     * @param names the list of newly added source directory names
     * 
     * @throws XMLStreamException if an error occurs while writing the XML stream
     */
    private void writeResponse(Operation operation, final List<String> names) throws XMLStreamException {
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");
        fetchList(operation, names, (name, _) -> writeRecord(name));
        writer.writeEndElement();
    }

    /**
     * Saves the updated list of source directories to the user's settings.
     *
     * @param lsrcdirs the updated list of source directory paths
     */
    private void save(final List<String> lsrcdirs) {
        request.getSession().getUser().getSettings().setProperty(SettingsEnum.dat2dir_srcdirs, String.join("|", lsrcdirs));
        request.getSession().getUser().getSettings().saveSettings();
    }

    /**
     * Writes a single source directory record to the XML stream.
     *
     * @param name the name of the source directory
     * 
     * @throws XMLStreamException if an error occurs while writing the XML stream
     */
    private void writeRecord(final String name) throws XMLStreamException {
        writer.writeElement(RECORD, new SimpleAttribute("name", name));
    }

    /**
     * Removes existing source directories from the settings.
     *
     * @param operation the operation containing the "name" data to remove
     * 
     * @throws XMLStreamException if an error occurs while writing the XML stream
     */
    @Override
    protected void remove(Operation operation) throws XMLStreamException {
        if (operation.hasData("name")) {
            final List<String> names;
            synchronized (request.getSession().getUser().getSettings()) {
                final List<String> lsrcdirs = new ArrayList<>(Arrays.asList(getSrcDirs()));
                names = operation.getDatas("name").stream().filter(lsrcdirs::contains).toList();
                if (!names.isEmpty()) {
                    lsrcdirs.removeAll(names);
                    save(lsrcdirs);
                }
            }
            if (!names.isEmpty()) {
                writeResponse(operation, names);
            } else {
                failure("Entry does not exist");
            }
        } else {
            failure("name is missing in request");
        }
    }
}