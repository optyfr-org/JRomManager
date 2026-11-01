/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui.profile.manager;

import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

import jrm.profile.manager.Dir;
import jrm.security.Session;

/**
 * Selection listener for the directory tree in the profile manager.
 * <p>
 * When a directory node is selected, updates the profiles table to show
 * all profile files contained in that directory.
 */
public class DirTreeSelectionListener implements TreeSelectionListener {

    /** The profiles table to update when a directory is selected. */
    JTable profilesList;

    /** The current security session for accessing profile data. */
    private final Session session;

    /**
     * Instantiates a new dir tree selection listener.
     *
     * @param profilesList the profiles list
     */
    public DirTreeSelectionListener(final Session session, final JTable profilesList) {
        this.session = session;
        this.profilesList = profilesList;
    }

    @Override
    public void valueChanged(final TreeSelectionEvent e) {
        final JTree tree = (JTree) e.getSource();
        final DirNode selectedNode = (DirNode) tree.getLastSelectedPathComponent();
        if (selectedNode != null) {
            ((FileTableModel) profilesList.getModel()).populate(session, (Dir) selectedNode.getUserObject());
            profilesList.getColumn(profilesList.getColumnName(0)).setCellRenderer(new FileTableCellRenderer());
        }
    }

}
