/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui.batch;

import java.awt.Component;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import jrm.batch.TrntChkReport.Child;
import jrm.batch.TrntChkReport.Status;
import jrm.misc.Log;
import jrm.ui.MainFrame;
import jrm.ui.batch.BatchTrrntChkReportNode.ChildNode;

/**
 * Tree cell renderer for torrent check report trees.
 * <p>
 * Displays report nodes with status-based color-coded icons.
 *
 * @author optyfr
 */
public class BatchTrrntChkReportTreeCellRenderer extends DefaultTreeCellRenderer {

    /**
     * Constructs a new report tree cell renderer.
     */
    public BatchTrrntChkReportTreeCellRenderer() {
        super();
    }

    @Override
    public Component getTreeCellRendererComponent(final JTree tree, final Object value, final boolean sel, final boolean expanded, final boolean leaf, final int row,
            final boolean hasFocus) {
        try {
            if (!(value instanceof ChildNode)) {
                super.getTreeCellRendererComponent(tree, "Root", sel, expanded, leaf, row, hasFocus);
                return this;
            }
            Child node = ((ChildNode) value).getChild();
            String title = node.getData().getTitle();
            if (node.getData().getLength() != null)
                title += " (" + node.getData().getLength() + ")";
            title += " [" + node.getData().getStatus() + "]";
            super.getTreeCellRendererComponent(tree, title, sel, expanded, leaf, row, hasFocus);
            if (!leaf)
                setIcon(MainFrame.getIcon("/jrm/resicons/folder" + (expanded ? "_open" : "_closed") + statusColor(node.getData().getStatus()) + ".png"));
            else
                setIcon(MainFrame.getIcon("/jrm/resicons/icons/bullet" + statusColor(node.getData().getStatus()) + ".png"));
        } catch (Exception e) {
            Log.err(e.getMessage(), e);
        }
        return this;
    }

    /**
     * Returns the color suffix for the given status.
     *
     * @param status the torrent check status
     * @return the color suffix (e.g., "_green", "_red")
     */
    protected String statusColor(final Status status) {
        switch (status) {
            case OK:
                return "_green";
            case MISSING:
                return "_red";
            case SHA1:
                return "_purple";
            case SIZE:
                return "_blue";
            case SKIPPED:
                return "_orange";
            case UNKNOWN:
                return "_gray";
            default:
                return "";
        }
    }
}
