package jrm.ui.batch;

import jrm.aui.profile.report.ReportTreeHandler;
import jrm.batch.TrntChkReport;
import jrm.ui.profile.report.ReportTreeModelGeneric;

/**
 * Tree model for torrent check reports.
 */
public class BatchTrrntChkReportTreeModel extends ReportTreeModelGeneric<TrntChkReport> {
    public BatchTrrntChkReportTreeModel(final TrntChkReport root) {
        super(new BatchTrrntChkReportNode(root));
        orgRoot = root;
        root.setHandler(this);
        initClone();
    }

    public BatchTrrntChkReportTreeModel(final ReportTreeHandler<TrntChkReport> handler) {
        super(new BatchTrrntChkReportNode(handler.getFilteredReport()));
        getFilteredReport().setHandler(this);
        orgRoot = handler.getOriginalReport();
        orgRoot.setHandler(this);
    }

    @Override
    public BatchTrrntChkReportNode getNodeInstance(TrntChkReport report) {
        return new BatchTrrntChkReportNode(report);
    }
}
