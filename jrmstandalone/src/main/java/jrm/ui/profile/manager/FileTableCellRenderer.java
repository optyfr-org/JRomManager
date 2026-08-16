/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui.profile.manager;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

import jrm.locale.Messages;
import jrm.profile.manager.ProfileNFO;

/**
 * Cell renderer for profile file table cells.
 * <p>
 * Displays profile names with color-coded status indicators:
 * green for up-to-date, orange for needs update, red for status unknown.
 * Right-aligns numeric columns and provides tooltips with detailed information.
 *
 * @author optyfr
 */
public class FileTableCellRenderer extends DefaultTableCellRenderer {

    /**
     * Constructs a new file table cell renderer.
     */
    public FileTableCellRenderer() {
        super();
    }

    @Override
    public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
        if (column == 0 && table.getModel() instanceof FileTableModel ftm) {
            final ProfileNFO nfo = ftm.getNfoAt(row);
            super.getTableCellRendererComponent(table, nfo.getName(), isSelected, hasFocus, row, column);
            switch (nfo.getMame().getStatus()) {
                case UPTODATE:
                    setForeground(Color.decode("0x00aa00")); //$NON-NLS-1$
                    setToolTipText(String.format(Messages.getString("FileTableCellRenderer.IsUpToDate"), nfo.getName())); //$NON-NLS-1$
                    break;
                case NEEDUPDATE:
                    setForeground(Color.decode("0xcc8800")); //$NON-NLS-1$
                    setToolTipText(String.format(Messages.getString("FileTableCellRenderer.NeedUpdateFromMame"), nfo.getName())); //$NON-NLS-1$
                    break;
                case NOTFOUND:
                    setForeground(Color.decode("0xcc0000")); //$NON-NLS-1$
                    setToolTipText(String.format(Messages.getString("FileTableCellRenderer.StatusUnknownMameNotFound"), nfo.getName())); //$NON-NLS-1$
                    break;
                default:
                    setForeground(Color.black);
                    setToolTipText(getText());
                    break;
            }
        } else {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setForeground(Color.black);
            if (column > 1)
                setHorizontalAlignment(SwingConstants.CENTER);
            else
                setToolTipText(getText());
        }
        setBackground(isSelected ? Color.decode("0xBBBBDD") : Color.white); //$NON-NLS-1$
        return this;
    }
}
