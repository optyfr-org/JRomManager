/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui.profile.report;

import java.awt.Component;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import jrm.aui.status.StatusRendererFactory;
import jrm.misc.Log;
import jrm.profile.report.ContainerTZip;
import jrm.profile.report.ContainerUnknown;
import jrm.profile.report.ContainerUnneeded;
import jrm.profile.report.EntryAdd;
import jrm.profile.report.EntryMissing;
import jrm.profile.report.EntryMissingDuplicate;
import jrm.profile.report.EntryOK;
import jrm.profile.report.EntryUnneeded;
import jrm.profile.report.EntryWrongHash;
import jrm.profile.report.EntryWrongName;
import jrm.profile.report.Note;
import jrm.profile.report.RomSuspiciousCRC;
import jrm.profile.report.Subject;
import jrm.profile.report.SubjectSet;
import jrm.ui.MainFrame;
import jrm.ui.profile.report.ReportNode.SubjectNode;
import jrm.ui.profile.report.ReportNode.SubjectNode.NoteNode;

/**
 * Tree cell renderer for scan report trees.
 * <p>
 * Displays report nodes with appropriate icons based on their status and type.
 *
 * @author optyfr
 */
public class ReportTreeCellRenderer extends DefaultTreeCellRenderer {

    /**
     * Constructs a new report tree cell renderer.
     */
    public ReportTreeCellRenderer() {
        super();
    }

    @Override
    public Component getTreeCellRendererComponent(final JTree tree, Object value, final boolean sel, final boolean expanded, final boolean leaf, final int row,
            final boolean hasFocus) {
        try {
            value = switch (value) {
                case ReportNode rn -> rn.getReport();
                case SubjectNode sn -> sn.getSubject();
                case NoteNode nn -> nn.getNote();
                default -> value;
            };
            if (value instanceof Subject s)
                super.getTreeCellRendererComponent(tree, s.getDocument(), sel, expanded, leaf, row, hasFocus);
            else if (value instanceof Note n)
                super.getTreeCellRendererComponent(tree, n.getDocument(), sel, expanded, leaf, row, hasFocus);
            else if (value instanceof StatusRendererFactory srf)
                super.getTreeCellRendererComponent(tree, srf.getDocument(), sel, expanded, leaf, row, hasFocus);
            setIcon(value, expanded, leaf);
        } catch (Exception e) {
            Log.err(e.getMessage(), e);
        }
        return this;
    }

    /**
     * @param value
     * @param expanded
     * @param leaf
     */
    private void setIcon(Object value, final boolean expanded, final boolean leaf) {
        switch (value) {
            case RomSuspiciousCRC _ -> setIcon(MainFrame.getIcon("/jrm/resicons/icons/information.png")); //$NON-NLS-1$
            case ContainerUnknown _ -> setIcon(MainFrame.getIcon("/jrm/resicons/icons/error.png")); //$NON-NLS-1$
            case ContainerUnneeded _ -> setIcon(MainFrame.getIcon("/jrm/resicons/icons/error.png")); //$NON-NLS-1$
            case ContainerTZip _ -> setIcon(MainFrame.getIcon("/jrm/resicons/icons/compress.png")); //$NON-NLS-1$
            case EntryOK _ -> setIcon(MainFrame.getIcon("/jrm/resicons/icons/bullet_green.png")); //$NON-NLS-1$
            case EntryAdd _ -> setIcon(MainFrame.getIcon("/jrm/resicons/icons/bullet_blue.png")); //$NON-NLS-1$
            case EntryMissingDuplicate _ -> setIcon(MainFrame.getIcon("/jrm/resicons/icons/bullet_purple.png")); //$NON-NLS-1$
            case EntryMissing _ -> setIcon(MainFrame.getIcon("/jrm/resicons/icons/bullet_red.png")); //$NON-NLS-1$
            case EntryUnneeded _ -> setIcon(MainFrame.getIcon("/jrm/resicons/icons/bullet_black.png")); //$NON-NLS-1$
            case EntryWrongHash _ -> setIcon(MainFrame.getIcon("/jrm/resicons/icons/bullet_orange.png")); //$NON-NLS-1$
            case EntryWrongName _ -> setIcon(MainFrame.getIcon("/jrm/resicons/icons/bullet_pink.png")); //$NON-NLS-1$
            case SubjectSet ss when leaf && SubjectSet.Status.FOUND.equals(ss.getStatus()) -> setIcon(MainFrame.getIcon("/jrm/resicons/icons/bullet_green.png")); //$NON-NLS-1$
            default -> {
                if (!leaf)
                    setIcon(MainFrame.getIcon(getFolderIcon(value, expanded)));
            }
        }
    }

    /**
     * @param value
     * @param expanded
     * 
     * @return
     */
    private String getFolderIcon(Object value, final boolean expanded) {
        String icon = "/jrm/resicons/folder"; //$NON-NLS-1$
        icon += expanded ? "_open" : "_closed";
        if (value instanceof SubjectSet ss) {
            switch (ss.getStatus()) {
                case FOUND:
                    if (ss.hasNotes())
                        icon += ss.isFixable() ? "_purple" : "_orange";
                    else
                        icon += "_green"; //$NON-NLS-1$
                    break;
                case CREATE, CREATEFULL:
                    icon += ss.isFixable() ? "_blue" : "_orange";
                    break;
                case MISSING:
                    icon += "_red"; //$NON-NLS-1$
                    break;
                case UNNEEDED:
                    icon += "_gray"; //$NON-NLS-1$
                    break;
                default:
                    break;
            }
        }
        icon += ".png"; //$NON-NLS-1$
        return icon;
    }
}
