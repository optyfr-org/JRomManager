package jrm.server.shared.datasources;

import java.io.IOException;
import java.util.Optional;

import javax.xml.stream.XMLStreamException;

import jrm.aui.basic.AbstractSrcDstResult;
import jrm.aui.basic.SDRList;
import jrm.aui.basic.SrcDstResult;
import jrm.misc.SettingsEnum;
import jrm.server.shared.datasources.XMLRequest.Operation;
import jrm.xml.SimpleAttribute;

/**
 * Provides a base implementation for generating XML responses that contain Source-Destination Result (SDR) records. Handles
 * standard response structures, including single entries, full lists, and key-based identifiers, as well as automatic settings
 * persistence when required.
 */
abstract class SDRXMLResponse extends XMLResponse {
    /** Message constant indicating that the source information was missing from the incoming request. */
    protected static final String SRC_IS_MISSING_IN_REQUEST = "Src is missing in request";
    /** Message constant indicating that the identifier was missing from the incoming request. */
    protected static final String ID_IS_MISSING_IN_REQUEST = "id is missing in request";
    /** XML attribute key representing the operation result status. */
    protected static final String RESULT = "result";
    /** XML element name used for a single record within the response. */
    protected static final String RECORD = "record";
    /** XML attribute key indicating whether an item has been selected by the user. */
    protected static final String SELECTED = "selected";
    /** XML root element name encapsulating the response payload. */
    protected static final String RESPONSE = "response";
    /** XML element name indicating the status code of the operation. */
    protected static final String STATUS = "status";

    /**
     * Constructs an SDR XML response bound to the specified request. Initializes the underlying XML writer and establishes the path
     * abstraction layer.
     *
     * @param request the incoming XML request containing session and operation data
     * 
     * @throws IOException if an I/O error occurs during initialization
     * @throws XMLStreamException if an error occurs while initializing the XML stream writer
     */
    protected SDRXMLResponse(XMLRequest request) throws IOException, XMLStreamException {
        super(request);
    }

    /**
     * Writes a single {@link AbstractSrcDstResult} entry into the XML output stream as a distinct record. Serializes the ID,
     * source, destination, result state, and selection flag.
     *
     * @param sdr the source-destination result object to serialize into the XML output
     * 
     * @throws XMLStreamException if an error occurs while writing the XML elements or attributes
     */
    protected void writeRecord(AbstractSrcDstResult sdr) throws XMLStreamException {
        writer.writeElement(RECORD, new SimpleAttribute("id", sdr.getId()), new SimpleAttribute("src", sdr.getSrc()),
                new SimpleAttribute("dst", Optional.ofNullable(sdr.getDst()).orElse("")), new SimpleAttribute(RESULT, sdr.getResult()),
                new SimpleAttribute(SELECTED, sdr.isSelected()));
    }

    /**
     * Writes the complete list of {@link SrcDstResult} objects into the XML response structure. Calculates pagination metadata and
     * serializes every item in the collection using {@link #writeRecord(AbstractSrcDstResult)}.
     *
     * @param operation the current XML operation controlling pagination boundaries
     * @param sdrl the list of source-destination results to be serialized
     * 
     * @throws XMLStreamException if an error occurs while writing the XML elements
     */
    protected void writeResponse(Operation operation, SDRList<SrcDstResult> sdrl) throws XMLStreamException {
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");
        fetchList(operation, sdrl, (sdr, _) -> writeRecord(sdr));
        writer.writeEndElement();
    }

    /**
     * Writes a single {@link AbstractSrcDstResult} entry enclosed within a standardized response wrapper. The structure includes a
     * success status and a dedicated data block wrapping the single record.
     *
     * @param sdr the specific source-destination result to serialize
     * 
     * @throws XMLStreamException if an error occurs while writing the XML elements
     */
    protected void writeResponseSingle(final AbstractSrcDstResult sdr) throws XMLStreamException {
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");
        writer.writeStartElement("data");
        writeRecord(sdr);
        writer.writeEndElement();
        writer.writeEndElement();
    }

    /**
     * Writes a single record containing only its unique identifier wrapped in a standard response envelope. Useful for operations
     * where lightweight confirmation is sufficient without transmitting full entity details.
     *
     * @param sdr the source-destination result whose ID should be written
     * 
     * @throws XMLStreamException if an error occurs while writing the XML elements
     */
    protected void writeResponseKey(final AbstractSrcDstResult sdr) throws XMLStreamException {
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");
        writer.writeStartElement("data");
        writer.writeElement(RECORD, new SimpleAttribute("id", sdr.getId()));
        writer.writeEndElement();
        writer.writeEndElement();
    }

    /**
     * Checks if the provided list requires saving and persistently updates the user's settings profile accordingly. Converts the
     * list contents to a JSON string representation before updating and saving the specified setting property.
     *
     * @param sdrl the list of results to check for persistence requirements
     * @param ppt the {@link SettingsEnum} key identifying which user preference should be updated
     */
    protected void needSave(SDRList<SrcDstResult> sdrl, SettingsEnum ppt) {
        if (sdrl.isNeedSave()) {
            request.getSession().getUser().getSettings().setProperty(ppt, AbstractSrcDstResult.toJSON(sdrl));
            request.getSession().getUser().getSettings().saveSettings();
        }
    }
}
