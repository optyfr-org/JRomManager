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

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import jrm.locale.Messages;
import jrm.profile.data.Anyware;
import jrm.profile.data.AnywareStatus;
import jrm.profile.data.Machine;
import jrm.profile.data.Samples;
import jrm.ui.MainFrame;
import jrm.ui.basic.CenteredTableCellRenderer;

/**
 * Provides cell renderers and column definitions for the machine list table.
 * <p>
 * Defines columns for status, name, description, have-count, clone-of, rom-of,
 * sample-of, and selection. Uses color-coded folder icons to indicate machine
 * completion status and specialized renderers for different entity types.
 */
public final class MachineListRenderer {
    /** The Constant folder_closed_green. */
    private static final ImageIcon folder_closed_green = MainFrame.getIcon("/jrm/resicons/folder_closed_green.png"); //$NON-NLS-1$

    /** The Constant folder_closed_orange. */
    private static final ImageIcon folder_closed_orange = MainFrame.getIcon("/jrm/resicons/folder_closed_orange.png"); //$NON-NLS-1$

    /** The Constant folder_closed_red. */
    private static final ImageIcon folder_closed_red = MainFrame.getIcon("/jrm/resicons/folder_closed_red.png"); //$NON-NLS-1$

    /** The Constant folder_closed_gray. */
    private static final ImageIcon folder_closed_gray = MainFrame.getIcon("/jrm/resicons/folder_closed_gray.png"); //$NON-NLS-1$

    /** The columns. */
    protected static final String[] columns = new String[] { Messages.getString("MachineListRenderer.Status"), Messages.getString("MachineListRenderer.Name"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("MachineListRenderer.Description"), Messages.getString("MachineListRenderer.Have"), Messages.getString("MachineListRenderer.CloneOf"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            Messages.getString("MachineListRenderer.RomOf"), Messages.getString("MachineListRenderer.SampleOf"), Messages.getString("MachineListRenderer.Selected") }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** The columns types. */
    protected static final Class<?>[] columnsTypes = new Class<?>[] { Object.class, Object.class, String.class, String.class, Object.class, Object.class, String.class,
            Boolean.class };

    /** The columns widths. */
    protected static final int[] columnsWidths = new int[] { -20, 40, 200, -45, 40, 40, 40, -20 };

    /** The columns renderers. */
    protected static final TableCellRenderer[] columnsRenderers = new TableCellRenderer[] { new AnywareCellRenderer(), new MachineCellRenderer(), new ToolTipCellRenderer(),
            new CenteredTableCellRenderer(), new AnywareCellRenderer(), new AnywareCellRenderer(), new SamplesCellRenderer(), null };

    /**
     * Instantiates a new machine list renderer.
     */
    private MachineListRenderer() {
    }

    private static final class ToolTipCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setToolTipText(getText());
            return this;
        }
    }

    private static final class MachineCellRenderer extends DefaultTableCellRenderer {
        final ImageIcon applicationOSXTerminal = MainFrame.getIcon("/jrm/resicons/icons/application_osx_terminal.png"); //$NON-NLS-1$
        final ImageIcon computer = MainFrame.getIcon("/jrm/resicons/icons/computer.png"); //$NON-NLS-1$
        final ImageIcon wrench = MainFrame.getIcon("/jrm/resicons/icons/wrench.png"); //$NON-NLS-1$
        final ImageIcon joystick = MainFrame.getIcon("/jrm/resicons/icons/joystick.png"); //$NON-NLS-1$

        @Override
        public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
            if (value instanceof Machine m) {
                super.getTableCellRendererComponent(table, m.getBaseName(), isSelected, hasFocus, row, column);
                if (m.isIsbios())
                    setIcon(applicationOSXTerminal);
                else if (m.isIsdevice())
                    setIcon(computer);
                else if (m.isIsmechanical())
                    setIcon(wrench);
                else
                    setIcon(joystick);
                setText(m.getBaseName());
                return this;
            }
            setIcon(null);
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
    }

    private static final class SamplesCellRenderer extends StatusCellRenderer {
        @Override
        public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
            if (value instanceof Samples s) {
                super.getTableCellRendererComponent(table, s.getBaseName(), isSelected, hasFocus, row, column);
                setIcon(s.getStatus());
                return this;
            }
            setIcon((Icon) null);
            if (value != null)
                setIcon(MachineListRenderer.folder_closed_gray);
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
    }

    private static final class AnywareCellRenderer extends StatusCellRenderer {
        @Override
        public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
            if (value instanceof Anyware a) {
                super.getTableCellRendererComponent(table, a.getBaseName(), isSelected, hasFocus, row, column);
                setIcon(a.getStatus());
                return this;
            }
            setIcon((Icon) null);
            if (value != null)
                setIcon(MachineListRenderer.folder_closed_gray);
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
    }

    private abstract static class StatusCellRenderer extends DefaultTableCellRenderer {
        protected void setIcon(AnywareStatus status) {
            switch (status) {
                case COMPLETE:
                    setIcon(MachineListRenderer.folder_closed_green);
                    break;
                case PARTIAL:
                    setIcon(MachineListRenderer.folder_closed_orange);
                    break;
                case MISSING:
                    setIcon(MachineListRenderer.folder_closed_red);
                    break;
                case UNKNOWN:
                default:
                    setIcon(MachineListRenderer.folder_closed_gray);
                    break;
            }
        }
    }

}
