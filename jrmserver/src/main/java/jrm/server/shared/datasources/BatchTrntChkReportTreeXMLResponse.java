package jrm.server.shared.datasources;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import javax.xml.stream.XMLStreamException;

import jrm.batch.TrntChkReport;
import jrm.batch.TrntChkReport.Child;
import jrm.batch.TrntChkReport.Status;
import jrm.profile.report.ReportIntf;
import jrm.server.shared.datasources.XMLRequest.Operation;

/**
 * XML response handler for batch transaction check report tree data.
 * <p>
 * This class processes XML requests to retrieve hierarchical transaction check report data, supporting the fetching of root nodes
 * and child nodes based on a parent ID.
 * </p>
 */
public class BatchTrntChkReportTreeXMLResponse extends XMLResponse {

    /** XML attribute name for the parent node identifier. */
    private static final String PARENT_ID = "ParentID";
    /** XML attribute name for the status. */
    private static final String STATUS = "status";

    /**
     * Constructs a new batch transaction check report tree XML response.
     *
     * @param request the XML request containing the operation to process
     * 
     * @throws IOException if an I/O error occurs during initialization
     * @throws XMLStreamException if an XML stream error occurs during initialization
     */
    public BatchTrntChkReportTreeXMLResponse(XMLRequest request) throws IOException, XMLStreamException {
        super(request);
    }

    /**
     * Fetches the transaction check report tree data and writes it to the XML response.
     * <p>
     * Loads or retrieves the cached report based on the source file path. Depending on the requested parent ID, it delegates to
     * either {@link #fetchRoot(Operation, TrntChkReport, Boolean)} or {@link #fetchNode(TrntChkReport, long)}.
     * </p>
     *
     * @param operation the operation containing request parameters (e.g., "src", "showOK", "ParentID")
     * 
     * @throws XMLStreamException if an error occurs while writing the XML stream
     */
    @Override
    protected void fetch(Operation operation) throws XMLStreamException {
        final var report = resolveReport(operation);
        if (report != null) {
            writer.writeStartElement("response");
            writer.writeElement(STATUS, "0");
            Boolean showok = Optional.ofNullable(operation.getData("showOK")).map(Boolean::valueOf).orElse(true);
            writer.writeElement("showOK", showok.toString());
            final var parentID = parseParentId(operation.getData(PARENT_ID));
            if (parentID == 0)
                fetchRoot(operation, report, showok);
            else
                fetchNode(report, parentID);
            writer.writeEndElement();
        } else
            success();
    }

    private TrntChkReport resolveReport(Operation operation) {
        if (!operation.hasData("src"))
            return null;
        final var srcfile = pathAbstractor.getAbsolutePath(operation.getData("src")).toFile();
        final var reportfile = ReportIntf.getReportFile(request.getSession(), srcfile);
        final var lastModified = reportfile.lastModified();
        final var cached = cachedTmpTCReport(reportfile, lastModified);
        if (cached != null)
            return cached;
        final var loaded = TrntChkReport.load(request.getSession(), srcfile);
        return publishTmpTCReport(reportfile, lastModified, loaded);
    }

    private TrntChkReport cachedTmpTCReport(File reportfile, long lastModified) {
        synchronized (request.session) {
            final var tmp = request.session.getTmpTCReport();
            if (isCurrentTmpTCReport(tmp, reportfile, lastModified))
                return tmp;
        }
        return null;
    }

    private TrntChkReport publishTmpTCReport(File reportfile, long lastModified, TrntChkReport loaded) {
        synchronized (request.session) {
            final var tmp = request.session.getTmpTCReport();
            if (isCurrentTmpTCReport(tmp, reportfile, lastModified))
                return tmp;
            request.session.setTmpTCReport(loaded);
            return loaded;
        }
    }

    private boolean isCurrentTmpTCReport(TrntChkReport tmp, File reportfile, long lastModified) {
        return tmp != null && tmp.getReportFile(request.getSession()).equals(reportfile) && tmp.getFileModified() == lastModified;
    }

    private static long parseParentId(String raw) {
        if (raw == null || raw.isBlank())
            return 0L;
        return Long.parseLong(raw);
    }

    /**
     * Fetches and writes the child nodes of a specific parent node to the XML response.
     *
     * @param report the loaded transaction check report
     * @param parentID the unique identifier of the parent node whose children are to be fetched
     * 
     * @throws XMLStreamException if an error occurs while writing the XML stream
     */
    private void fetchNode(TrntChkReport report, long parentID) throws XMLStreamException {
        Child parent = report.getAll().get(parentID);
        final var children = parent != null ? parent.getChildren() : null;
        int nodecount = children != null ? children.size() : 0;
        writer.writeElement("startRow", "0");
        writer.writeElement("endRow", Integer.toString(Math.max(nodecount - 1, 0)));
        writer.writeElement("totalRows", Integer.toString(nodecount));
        writer.writeStartElement("data");
        if (children != null)
            for (Child n : children) {
                writer.writeStartElement("record");
                writer.writeAttribute("ID", Long.toString(n.getUid()));
                writer.writeAttribute(PARENT_ID, Long.toString(parentID));
                writer.writeAttribute("title", n.getData().getTitle());
                if (n.getData().getLength() != null)
                    writer.writeAttribute("length", n.getData().getLength().toString());
                writer.writeAttribute(STATUS, n.getData().getStatus().toString());
                writer.writeAttribute("isFolder", Boolean.toString(n.getChildren() != null && !n.getChildren().isEmpty()));
                writer.writeEndElement();
            }
        writer.writeEndElement();
    }

    /**
     * Fetches and writes the root nodes of the transaction check report to the XML response.
     * <p>
     * Filters the root nodes based on the {@code showok} parameter. If {@code showok} is false, nodes with a status of
     * {@link Status#OK} are excluded. Supports pagination via the operation's start and end row parameters.
     * </p>
     *
     * @param operation the operation containing pagination parameters (start row, end row)
     * @param report the loaded transaction check report
     * @param showok whether to include nodes with an OK status in the response
     * 
     * @throws XMLStreamException if an error occurs while writing the XML stream
     */
    private void fetchRoot(Operation operation, TrntChkReport report, Boolean showok) throws XMLStreamException {
        List<Child> nodes = report.getNodes().stream().filter(n -> showok || n.getData().getStatus() != Status.OK).toList();
        int start;
        int end;
        var nodecount = nodes.size();
        final int last = Math.max(nodecount - 1, 0);
        start = Math.clamp(operation.getStartRow(), 0, last);
        end = Math.max(start, Math.clamp(operation.getEndRow(), 0, last));
        writer.writeElement("startRow", Integer.toString(start));
        writer.writeElement("endRow", Integer.toString(end));
        writer.writeElement("totalRows", Integer.toString(nodecount));

        if (nodecount > 0) {
            writer.writeStartElement("data");
            for (int i = start; i <= end; i++) {
                Child n = nodes.get(i);
                writer.writeStartElement("record");
                writer.writeAttribute("ID", Long.toString(n.getUid()));
                writer.writeAttribute(PARENT_ID, "0");
                writer.writeAttribute("title", n.getData().getTitle());
                if (n.getData().getLength() != null)
                    writer.writeAttribute("length", n.getData().getLength().toString());
                writer.writeAttribute(STATUS, n.getData().getStatus().toString());
                writer.writeAttribute("isFolder", Boolean.toString(n.getChildren() != null && !n.getChildren().isEmpty()));
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
    }
}