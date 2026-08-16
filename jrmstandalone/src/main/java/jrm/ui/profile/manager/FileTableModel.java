/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui.profile.manager;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import jrm.aui.status.StatusRendererFactory;
import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.profile.manager.Dir;
import jrm.profile.manager.ProfileNFO;
import jrm.security.Session;
import lombok.Getter;

/**
 * Table model for displaying profile files in a directory.
 * <p>
 * Shows profile information including name, version, set/ROM/disk counts,
 * and various timestamps. Implements {@link StatusRendererFactory} to provide
 * status indicators for each profile.
 *
 * @author optyfr
 */
public class FileTableModel extends AbstractTableModel implements StatusRendererFactory {

    /** The current directory being displayed. */
    private transient @Getter Dir currDir = null;

    /** Column headers for the profile table. */
    private final String[] columns = new String[] { Messages.getString("FileTableModel.Profile"), Messages.getString("FileTableModel.Version"), //$NON-NLS-1$//$NON-NLS-2$
            Messages.getString("FileTableModel.HaveSets"), Messages.getString("FileTableModel.HaveRoms"), Messages.getString("FileTableModel.HaveDisks"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            Messages.getString("FileTableModel.Created"), Messages.getString("FileTableModel.Scanned"), Messages.getString("FileTableModel.Fixed") }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** Data types for each column. */
    private final Class<?>[] columnsClass = new Class<?>[] { Object.class, String.class, String.class, String.class, String.class, String.class, String.class, String.class };

    /** Preferred column widths for the table. */
    protected static final @Getter int[] columnsWidths = new int[] { 100, 50, -14, -14, -9, -19, -19, -19 };

    /** List of profile information objects for the current directory. */
    private transient List<ProfileNFO> rows;

    /** The current security session for accessing profile data. */
    private transient Session session;

    /**
     * Instantiates a new file table model.
     *
     * @param dir the dir
     */
    public FileTableModel(final Session session, final Dir dir) {
        super();
        populate(session, dir);
    }

    /**
     * Instantiates a new file table model.
     */
    public FileTableModel() {
        super();
    }

    /**
     * Populate.
     */
    public void populate(final Session session) {
        populate(session, currDir);
    }

    /**
     * Populate.
     *
     * @param dir the dir
     */
    public void populate(final Session session, final Dir dir) {
        this.session = session;
        currDir = dir;
        rows = ProfileNFO.list(session, dir.getFile());
        fireTableDataChanged();
    }

    @Override
    public boolean isCellEditable(final int row, final int column) {
        return column == 0;
    }

    /**
     * Gets the file at.
     *
     * @param row the row
     * 
     * @return the file at
     */
    public File getFileAt(final int row) {
        return getNfoAt(row).getFile();
    }

    /**
     * Gets the nfo at.
     *
     * @param row the row
     * 
     * @return the nfo at
     */
    public ProfileNFO getNfoAt(final int row) {
        return rows.get(row);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public Object getValueAt(final int rowIndex, final int columnIndex) {
        return getValueAt(rows.get(rowIndex), columnIndex);
    }

    public Object getValueAt(ProfileNFO pnfo, final int columnIndex) {
        return internalGetValueAt(pnfo, columnIndex);
    }

    private static Object internalGetValueAt(ProfileNFO pnfo, final int columnIndex) {
        switch (columnIndex) {
            case 0:
                return pnfo.getName();
            case 1:
                return pnfo.getHTMLVersion();
            case 2:
                return pnfo.getHTMLHaveSets();
            case 3:
                return pnfo.getHTMLHaveRoms();
            case 4:
                return pnfo.getHTMLHaveDisks();
            case 5:
                return pnfo.getHTMLCreated();
            case 6:
                return pnfo.getHTMLScanned();
            case 7:
                return pnfo.getHTMLFixed();
            default:
                break;
        }
        return null;
    }

    @Override
    public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
        if (columnIndex == 0) {
            final ProfileNFO pnfo = rows.get(rowIndex);
            Arrays.asList("", ".properties", ".cache").forEach(ext -> { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                final var oldfile = new File(currDir.getFile(), pnfo.getName() + ext);
                final var newfile = new File(currDir.getFile(), aValue + ext);
                if (oldfile.renameTo(newfile))
                    Log.warn(() -> "Can't rename " + oldfile.getName() + " to " + newfile.getName());
            });
            final var newNfoFile = new File(currDir.getFile(), aValue.toString());
            if (session.getCurrProfile() != null && session.getCurrProfile().getNfo().getFile().equals(pnfo.getFile()))
                session.getCurrProfile().getNfo().relocate(session, newNfoFile);
            pnfo.relocate(session, newNfoFile);
            fireTableCellUpdated(rowIndex, rowIndex);
        }
    }

    @Override
    public String getColumnName(final int columnIndex) {
        return columns[columnIndex];
    }

    @Override
    public Class<?> getColumnClass(final int columnIndex) {
        return columnsClass[columnIndex];
    }

}
