/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui.profile.report;

import jrm.aui.profile.report.ReportTreeHandler;
import jrm.profile.report.Report;

/**
 * Tree model for scan reports.
 *
 * @author optyfr
 */
public final class ReportTreeModel extends ReportTreeModelGeneric<Report> {
    /**
     * Constructs a new report tree model.
     *
     * @param root the root report
     */
    private ReportTreeModel(final Report root) // NOSONAR
    {
        super(new ReportNode(root));
        orgRoot = root;
        root.setHandler(this);
        initClone();
    }

    public ReportTreeModel(final ReportTreeHandler<Report> handler) {
        super(new ReportNode(handler.getFilteredReport()));
        getFilteredReport().setHandler(this);
        orgRoot = handler.getOriginalReport();
        orgRoot.setHandler(this);
    }

    @Override
    public ReportNode getNodeInstance(Report report) {
        return new ReportNode(report);
    }

}
