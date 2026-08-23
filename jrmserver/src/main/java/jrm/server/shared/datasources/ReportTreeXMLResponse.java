package jrm.server.shared.datasources;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import javax.xml.stream.XMLStreamException;

import jrm.profile.report.Note;
import jrm.profile.report.Report;
import jrm.profile.report.ReportIntf;
import jrm.profile.report.Subject;
import jrm.profile.report.SubjectSet;
import jrm.server.shared.datasources.XMLRequest.Operation;

/**
 * Handles XML responses for fetching hierarchical report trees within the server datasources subsystem. Provides paginated node
 * retrieval for root subjects and child notes, supporting detail extraction operations.
 */
public class ReportTreeXMLResponse extends XMLResponse {

    /** Constant representing a single data record element in the XML response. */
    private static final String RECORD = "record";
    /** Constant representing the parent identifier attribute/key. */
    private static final String PARENT_ID = "ParentID";
    /** Constant representing the status indicator for subject sets. */
    private static final String STATUS = "status";

    /**
     * Constructs a {@code ReportTreeXMLResponse} bound to the provided request. Initializes the underlying XML writer and path
     * abstraction layer.
     *
     * @param request the incoming XML request containing session and operation data
     * 
     * @throws IOException if an I/O error occurs during initialization
     * @throws XMLStreamException if an error occurs while initializing the XML stream writer
     */
    public ReportTreeXMLResponse(XMLRequest request) throws IOException, XMLStreamException {
        super(request);
    }

    /**
     * Fetches and serializes the report tree structure based on the provided operation parameters. Dispatches to either root-level
     * pagination or specific child node retrieval depending on {@code parentID}. Supports loading temporary reports from an
     * external source file if specified in the operation data.
     *
     * @param operation the parsed XML operation containing parameters such as pagination rows and source file paths
     * 
     * @throws XMLStreamException if an error occurs while writing the XML response
     * @throws IOException if an I/O error occurs during temporary report loading or path resolution
     */
    @Override
    protected void fetch(Operation operation) throws XMLStreamException, IOException {
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");

        final var report = resolveReport(operation);

        final var parentID = Integer.parseInt(operation.getData(PARENT_ID));
        if (parentID == 0) {
            fetchRoot(operation, report);
        } else {
            fetchNode(report, parentID);
        }
        writer.writeEndElement();
    }

    /**
     * Retrieves and writes the child nodes (notes) belonging to a specific parent subject into the XML response. Calculates
     * pagination metadata and serializes each note as a record element with attributes including ID, title, class, and folder
     * status.
     *
     * @param report the active or temporary report containing the filtered subject hierarchy
     * @param parentID the unique identifier of the parent subject whose children are being fetched
     * 
     * @throws XMLStreamException if an error occurs while writing XML elements or attributes
     */
    private void fetchNode(Report report, final int parentID) throws XMLStreamException {
        final var subject = report.getHandler().getFilteredReport().findSubject(parentID);
        if (subject != null) {
            int nodecount = subject.size();
            writer.writeElement("startRow", "0");
            writer.writeElement("endRow", Integer.toString(nodecount - 1));
            writer.writeElement("totalRows", Integer.toString(nodecount));
            writer.writeStartElement("data");
            for (Note n : subject) {
                writer.writeStartElement(RECORD);
                writer.writeAttribute("ID", Integer.toString(n.getId()));
                writer.writeAttribute(PARENT_ID, Integer.toString(parentID));
                writer.writeAttribute("title", n.getDocument());
                writer.writeAttribute("class", n.getClass().getSimpleName());
                writer.writeAttribute("isFolder", Boolean.toString(false));
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
    }

    /**
     * Retrieves and writes the root-level subjects of the report into the XML response. Computes pagination boundaries, overall
     * statistics, and serializes each subject as a record with status and fixability indicators.
     *
     * @param operation the current XML operation providing pagination parameters (start/end row)
     * @param report the active or temporary report containing the top-level subjects
     * 
     * @throws XMLStreamException if an error occurs while writing XML elements or attributes
     */
    private void fetchRoot(Operation operation, Report report) throws XMLStreamException {
        int start;
        int end;
        int nodecount = report.getHandler().getFilteredReport().size();
        start = Math.min(nodecount - 1, operation.getStartRow());
        writer.writeElement("startRow", Integer.toString(start));
        end = Math.min(nodecount - 1, operation.getEndRow());
        writer.writeElement("endRow", Integer.toString(end));
        writer.writeElement("totalRows", Integer.toString(nodecount));
        writer.writeElement("infos", report.getStats().getStatus());
        writer.writeStartElement("summary");
        writer.writeCData(report.getSummaryText());
        writer.writeEndElement();

        if (nodecount > 0) {
            writer.writeStartElement("data");
            for (int i = start; i <= end; i++) {
                Subject s = report.getHandler().getFilteredReport().get(i);
                writer.writeStartElement(RECORD);
                writer.writeAttribute("ID", Integer.toString(s.getId()));
                writer.writeAttribute(PARENT_ID, Integer.toString(0));
                writer.writeAttribute("title", s.getDocument());
                writer.writeAttribute("class", s.getClass().getSimpleName());
                if (s instanceof SubjectSet ss) {
                    writer.writeAttribute(STATUS, ss.getStatus().toString());
                    writer.writeAttribute("hasNotes", Boolean.toString(ss.hasNotes()));
                    writer.writeAttribute("isFixable", Boolean.toString(ss.isFixable()));
                }
                writer.writeAttribute("isFolder", Boolean.toString(!s.getNotes().isEmpty()));
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
    }

    /**
     * Handles custom operations specifically dedicated to extracting detailed information for a single report node. Loads the
     * appropriate report context, resolves the parent subject, and writes the detailed metadata (name, hashes, CDATA body) if the
     * matching ID is found.
     *
     * @param operation the custom operation containing the target node ID and optional source file reference
     * 
     * @throws XMLStreamException if an error occurs while constructing the detailed XML record
     * @throws IOException if an I/O error occurs during report context resolution or loading
     */
    @Override
    protected void custom(Operation operation) throws XMLStreamException, IOException {
        if ("copy".equals(operation.getOperationId().toString())) {
            writeCopy(resolveReport(operation));
            return;
        }
        if ("detail".equals(operation.getOperationId().toString())) {
            writeDetail(operation, resolveReport(operation));
            return;
        }
        super.custom(operation);
    }

    private Report resolveReport(Operation operation) {
        if (!operation.hasData("src"))
            return request.session.getReport();
        final var srcfile = pathAbstractor.getAbsolutePath(operation.getData("src")).toFile();
        final var reportfile = ReportIntf.getReportFile(request.session, srcfile);
        final var lastModified = reportfile.lastModified();
        final var cached = cachedTmpReport(reportfile, lastModified);
        if (cached.isPresent())
            return cached.get();
        final var loaded = Report.load(request.session, srcfile);
        return publishTmpReport(reportfile, lastModified, loaded);
    }

    private Optional<Report> cachedTmpReport(File reportfile, long lastModified) {
        synchronized (request.session) {
            final var tmp = request.session.getTmpReport();
            if (isCurrentTmpReport(tmp, reportfile, lastModified))
                return Optional.of(tmp);
        }
        return Optional.empty();
    }

    private Report publishTmpReport(File reportfile, long lastModified, Report loaded) {
        synchronized (request.session) {
            final var tmp = request.session.getTmpReport();
            if (isCurrentTmpReport(tmp, reportfile, lastModified))
                return tmp;
            request.session.setTmpReport(loaded);
            return loaded;
        }
    }

    private boolean isCurrentTmpReport(Report tmp, File reportfile, long lastModified) {
        return tmp != null && tmp.getReportFile(request.session).equals(reportfile) && tmp.getFileModified() == lastModified;
    }

    private void writeCopy(Report report) throws XMLStreamException {
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");
        writer.writeStartElement("data");
        writer.writeStartElement(RECORD);
        writer.writeAttribute("ID", "0");
        writer.writeStartElement("Text");
        writer.writeCData(report != null ? report.toCopyableText() : "");
        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndElement();
    }

    private void writeDetail(Operation operation, Report report) throws XMLStreamException {
        final var parentID = Integer.parseInt(operation.getData(PARENT_ID));
        final var subject = report.getHandler().getFilteredReport().findSubject(parentID);
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");
        writer.writeStartElement("data");
        for (Note n : subject) {
            if (n.getId() == Integer.valueOf(operation.getData("ID"))) {
                writer.writeStartElement(RECORD);
                writer.writeAttribute("ID", Integer.toString(n.getId()));
                writer.writeAttribute(PARENT_ID, Integer.toString(parentID));
                writer.writeAttribute("Name", n.getName());
                writer.writeAttribute("CRC", n.getCrc());
                writer.writeAttribute("SHA1", n.getSha1());
                writer.writeStartElement("Detail");
                writer.writeCData(n.getDetail());
                writer.writeEndElement();
                writer.writeEndElement();
            }
        }
        writer.writeEndElement();
        writer.writeEndElement();
    }
}
