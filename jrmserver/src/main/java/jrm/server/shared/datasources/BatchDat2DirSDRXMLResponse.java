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
 * XML response handler for batch DAT to directory Source-Destination Results (SDR).
 * <p>
 * This class processes XML requests to manage the list of source and destination paths used in batch DAT to directory operations,
 * supporting fetch, add, update, and remove operations.
 * </p>
 */
public class BatchDat2DirSDRXMLResponse extends SDRXMLResponse {

    /**
     * Constructs a new batch DAT to directory SDR XML response.
     *
     * @param request the XML request containing the operation to process
     * 
     * @throws IOException if an I/O error occurs during initialization
     * @throws XMLStreamException if an XML stream error occurs during initialization
     */
    public BatchDat2DirSDRXMLResponse(XMLRequest request) throws IOException, XMLStreamException {
        super(request);
    }

    /**
     * Fetches the current list of DAT to directory SDRs and writes them to the XML response.
     *
     * @param operation the operation containing request parameters
     * 
     * @throws XMLStreamException if an error occurs while writing the XML stream
     */
    @Override
    protected void fetch(Operation operation) throws XMLStreamException {
        SDRList<SrcDstResult> sdrl = SrcDstResult.fromJSON(request.getSession().getUser().getSettings().getProperty(SettingsEnum.dat2dir_sdr));
        needSave(sdrl, SettingsEnum.dat2dir_sdr);
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
            final SDRList<SrcDstResult> sdrl = SrcDstResult.fromJSON(request.getSession().getUser().getSettings().getProperty(SettingsEnum.dat2dir_sdr));
            needSave(sdrl, SettingsEnum.dat2dir_sdr);
            final SrcDstResult sdr = new SrcDstResult(operation.getData("src"));
            Optional<SrcDstResult> candidate = sdrl.stream().filter(s -> s.getSrc().equals(operation.getData("src"))).findAny();
            if (!candidate.isPresent()) {
                sdrl.add(sdr);
                request.getSession().getUser().getSettings().setProperty(SettingsEnum.dat2dir_sdr, AbstractSrcDstResult.toJSON(sdrl));
                request.getSession().getUser().getSettings().saveSettings();
                writeResponseSingle(sdr);
            } else {
                failure("Entry already exists");
            }
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
            failure(ID_IS_MISSING_IN_REQUEST);
            return;
        }
        final SDRList<SrcDstResult> sdrl = SrcDstResult.fromJSON(request.getSession().getUser().getSettings().getProperty(SettingsEnum.dat2dir_sdr));
        needSave(sdrl, SettingsEnum.dat2dir_sdr);
        Optional<SrcDstResult> candidate = sdrl.stream().filter(sdr -> sdr.getId().equals(operation.getData("id"))).findFirst();
        if (!candidate.isPresent()) {
            failure("not in list");
            return;
        }
        if (operation.hasData("src") || operation.hasData("dst") || operation.hasData(SELECTED)) {
            final SrcDstResult sdr = candidate.get();
            if (operation.hasData("src")) {
                sdr.setSrc(operation.getData("src"));
            }
            if (operation.hasData("dst")) {
                sdr.setDst(operation.getData("dst"));
            }
            if (operation.hasData(SELECTED)) {
                sdr.setSelected(Boolean.parseBoolean(operation.getData(SELECTED)));
            }
            request.getSession().getUser().getSettings().setProperty(SettingsEnum.dat2dir_sdr, AbstractSrcDstResult.toJSON(sdrl));
            request.getSession().getUser().getSettings().saveSettings();
            writeResponseSingle(sdr);
        } else {
            failure("field to update is missing in request");
        }
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
            final SDRList<SrcDstResult> sdrl = SrcDstResult.fromJSON(request.getSession().getUser().getSettings().getProperty(SettingsEnum.dat2dir_sdr));
            needSave(sdrl, SettingsEnum.dat2dir_sdr);
            Optional<SrcDstResult> candidate = sdrl.stream().filter(sdr -> sdr.getId().equals(operation.getData("id"))).findFirst();
            if (candidate.isPresent()) {
                final AbstractSrcDstResult sdr = candidate.get();
                sdrl.remove(sdr);
                request.getSession().getUser().getSettings().setProperty(SettingsEnum.dat2dir_sdr, AbstractSrcDstResult.toJSON(sdrl));
                request.getSession().getUser().getSettings().saveSettings();
                writeResponseKey(sdr);
            } else {
                failure("not in the list");
            }
        } else {
            failure(ID_IS_MISSING_IN_REQUEST);
        }
    }
}
