/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui.profile.manager;

import java.awt.Component;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import jrm.ui.MainFrame;

/**
 * Cell renderer for directory tree nodes in the profile manager.
 * <p>
 * Displays folder icons (open/closed) for directory nodes in the tree.
 *
 * @author optyfr
 */
public class DirTreeCellRenderer extends DefaultTreeCellRenderer {

    /**
     * Instantiates a new dir tree cell renderer.
     */
    public DirTreeCellRenderer() {
        super();
        setOpenIcon(MainFrame.getIcon("/jrm/resicons/folder_open.png")); //$NON-NLS-1$
        setClosedIcon(MainFrame.getIcon("/jrm/resicons/folder_closed.png")); //$NON-NLS-1$
    }

    @Override
    public Component getTreeCellRendererComponent(final JTree tree, final Object value, final boolean sel, final boolean expanded, final boolean leaf, final int row,
            final boolean hasFocus) {
        return super.getTreeCellRendererComponent(tree, value, sel, expanded, false, row, hasFocus);
    }
}
