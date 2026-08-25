/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui.profile.data;

import java.awt.Component;

import javax.swing.ImageIcon;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import jrm.locale.Messages;
import jrm.profile.data.Anyware;
import jrm.profile.data.Software;
import jrm.ui.MainFrame;
import jrm.ui.basic.CenteredTableCellRenderer;
import lombok.RequiredArgsConstructor;

/**
 * Provides cell renderers and column definitions for the software list table.
 * <p>
 * Defines columns for status, name, description, have-count, clone-of, and selection.
 * Uses color-coded folder icons to indicate software completion status.
 */
public final class SoftwareListRenderer {
    /** The Constant columns. */
    protected static final String[] columns = new String[] { Messages.getString("SoftwareListRenderer.Status"), Messages.getString("SoftwareListRenderer.Name"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("SoftwareListRenderer.Description"), Messages.getString("SoftwareListRenderer.Have"), Messages.getString("SoftwareListRenderer.CloneOf"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            Messages.getString("SoftwareListRenderer.Selected") }; //$NON-NLS-1$

    /** The Constant columnsTypes. */
    protected static final Class<?>[] columnsTypes = new Class<?>[] { Object.class, Object.class, String.class, String.class, Object.class, Boolean.class };

    /** The Constant columnsWidths. */
    protected static final int[] columnsWidths = new int[] { -20, 40, 200, -45, 40, -20 };

    /** The Constant columnsRenderers. */
    protected static final TableCellRenderer[] columnsRenderers = new TableCellRenderer[] { new SoftwareCellRenderer(false), new AnywareCellRenderer(), null,
            new CenteredTableCellRenderer(), new SoftwareCellRenderer(true), null };

    /**
     * Instantiates a new software list renderer.
     */
    private SoftwareListRenderer() {
    }

    private static final class AnywareCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
            if (value instanceof Anyware aw) {
                return super.getTableCellRendererComponent(table, aw.getBaseName(), isSelected, hasFocus, row, column);
            }
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
    }

    @RequiredArgsConstructor
    private static final class SoftwareCellRenderer extends DefaultTableCellRenderer {
        /** The Constant folder_closed_green. */
        private static final ImageIcon folder_closed_green = MainFrame.getIcon("/jrm/resicons/folder_closed_green.png"); //$NON-NLS-1$

        /** The Constant folder_closed_orange. */
        private static final ImageIcon folder_closed_orange = MainFrame.getIcon("/jrm/resicons/folder_closed_orange.png"); //$NON-NLS-1$

        /** The Constant folder_closed_red. */
        private static final ImageIcon folder_closed_red = MainFrame.getIcon("/jrm/resicons/folder_closed_red.png"); //$NON-NLS-1$

        /** The Constant folder_closed_gray. */
        private static final ImageIcon folder_closed_gray = MainFrame.getIcon("/jrm/resicons/folder_closed_gray.png"); //$NON-NLS-1$

        private final boolean withName;

        @Override
        public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
            if (value instanceof Software sw) {
                super.getTableCellRendererComponent(table, withName ? sw.getBaseName() : "", isSelected, hasFocus, row, column); //$NON-NLS-1$
                switch (sw.getStatus()) {
                    case COMPLETE:
                        setIcon(folder_closed_green);
                        break;
                    case PARTIAL:
                        setIcon(folder_closed_orange);
                        break;
                    case MISSING:
                        setIcon(folder_closed_red);
                        break;
                    case UNKNOWN:
                    default:
                        setIcon(folder_closed_gray);
                        break;
                }
                return this;
            }
            setIcon(null);
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
    }

}
