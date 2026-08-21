package jrm.server.shared.datasources;

import java.io.IOException;
import java.util.Optional;

import javax.xml.stream.XMLStreamException;

import jrm.aui.basic.AbstractSrcDstResult;
import jrm.aui.basic.SDRList;
import jrm.aui.basic.SrcDstResult;
import jrm.misc.SettingsEnum;
import jrm.server.shared.datasources.XMLRequest.Operation;

/**
 * XML response handler for batch transaction check Source-Destination Results (SDR).
 * <p>
 * This class processes XML requests to manage the list of source and destination paths used in batch transaction check operations,
 * supporting fetch, add, update, and remove operations.
 * </p>
 */
public class BatchTrntChkSDRXMLResponse extends SDRXMLResponse {

    /**
     * Constructs a new batch transaction check SDR XML response.
     *
     * @param request the XML request containing the operation to process
     * 
     * @throws IOException if an I/O error occurs during initialization
     * @throws XMLStreamException if an XML stream error occurs during initialization
     */
    public BatchTrntChkSDRXMLResponse(XMLRequest request) throws IOException, XMLStreamException {
        super(request);
    }

    /**
     * Fetches the current list of transaction check SDRs and writes them to the XML response.
     *
     * @param operation the operation containing request parameters
     * 
     * @throws XMLStreamException if an error occurs while writing the XML stream
     */
    @Override
    protected void fetch(Operation operation) throws XMLStreamException {
        SDRList<SrcDstResult> sdrl = SrcDstResult.fromJSON(request.getSession().getUser().getSettings().getProperty(SettingsEnum.trntchk_sdr));
        needSave(sdrl, SettingsEnum.trntchk_sdr);
        writeResponse(operation, sdrl);
    }

    /**
     * Adds a new source-destination result to the settings if it does not already exist.
     *
     * @param operation the operation containing the "src" data to add
     * 
     * @throws XMLStreamException if an error occurs while writing the XML stream
     */
    @Override
    protected void add(Operation operation) throws XMLStreamException {
        if (operation.hasData("src")) {
            final var sdr = new SrcDstResult(operation.getData("src"));
            synchronized (request.getSession().getUser().getSettings()) {
                final SDRList<SrcDstResult> sdrl = SrcDstResult.fromJSON(request.getSession().getUser().getSettings().getProperty(SettingsEnum.trntchk_sdr));
                needSave(sdrl, SettingsEnum.trntchk_sdr);
                Optional<SrcDstResult> candidate = sdrl.stream().filter(s -> s.getSrc().equals(operation.getData("src"))).findAny();
                if (!candidate.isPresent()) {
                    sdrl.add(sdr);
                    request.getSession().getUser().getSettings().setProperty(SettingsEnum.trntchk_sdr, AbstractSrcDstResult.toJSON(sdrl));
                    request.getSession().getUser().getSettings().saveSettings();
                } else {
                    failure("Entry already exists");
                    return;
                }
            }
            writeResponseSingle(sdr);
        } else {
            failure(SRC_IS_MISSING_IN_REQUEST);
        }
    }

    /**
     * Updates an existing source-destination result identified by its ID.
     *
     * @param operation the operation containing the "id" of the entry to update, and optionally "src", "dst", or "selected"
     * 
     * @throws XMLStreamException if an error occurs while writing the XML stream
     */
    @Override
    protected void update(Operation operation) throws XMLStreamException {
        if (!operation.hasData("id")) {
            failure(SRC_IS_MISSING_IN_REQUEST);
            return;
        }
        final AbstractSrcDstResult result;
        synchronized (request.getSession().getUser().getSettings()) {
            final SDRList<SrcDstResult> sdrl = SrcDstResult.fromJSON(request.getSession().getUser().getSettings().getProperty(SettingsEnum.trntchk_sdr));
            needSave(sdrl, SettingsEnum.trntchk_sdr);
            final Optional<SrcDstResult> candidate = sdrl.stream().filter(sdr -> sdr.getId().equals(operation.getData("id"))).findFirst();
            if (!candidate.isPresent()) {
                failure("not in list");
                return;
            }
            if (!operation.hasData("src") && !operation.hasData("dst") && !operation.hasData(SELECTED)) {
                failure("field to update is missing in request");
                return;
            }
            result = candidate.get();
            if (operation.hasData("src")) {
                result.setSrc(operation.getData("src"));
            }
            if (operation.hasData("dst")) {
                result.setDst(operation.getData("dst"));
            }
            if (operation.hasData(SELECTED)) {
                result.setSelected(Boolean.parseBoolean(operation.getData(SELECTED)));
            }
            request.getSession().getUser().getSettings().setProperty(SettingsEnum.trntchk_sdr, AbstractSrcDstResult.toJSON(sdrl));
            request.getSession().getUser().getSettings().saveSettings();
        }
        writeResponseSingle(result);
    }

    /**
     * Removes a source-destination result from the settings by its ID.
     *
     * @param operation the operation containing the "id" of the entry to remove
     * 
     * @throws XMLStreamException if an error occurs while writing the XML stream
     */
    @Override
    protected void remove(Operation operation) throws XMLStreamException {
        if (operation.hasData("id")) {
            final AbstractSrcDstResult result;
            synchronized (request.getSession().getUser().getSettings()) {
                final SDRList<SrcDstResult> sdrl = SrcDstResult.fromJSON(request.getSession().getUser().getSettings().getProperty(SettingsEnum.trntchk_sdr));
                needSave(sdrl, SettingsEnum.trntchk_sdr);
                final Optional<SrcDstResult> candidate = sdrl.stream().filter(sdr -> sdr.getId().equals(operation.getData("id"))).findFirst();
                if (candidate.isPresent()) {
                    result = candidate.get();
                    sdrl.remove(result);
                    request.getSession().getUser().getSettings().setProperty(SettingsEnum.trntchk_sdr, AbstractSrcDstResult.toJSON(sdrl));
                    request.getSession().getUser().getSettings().saveSettings();
                } else {
                    failure("not in the list");
                    return;
                }
            }
            writeResponseKey(result);
        } else {
            failure(SRC_IS_MISSING_IN_REQUEST);
        }
    }
}
