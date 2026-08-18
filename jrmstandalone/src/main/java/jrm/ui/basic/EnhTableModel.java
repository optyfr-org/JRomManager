/**
 * 
 */
package jrm.ui.basic;

import java.awt.Component;

import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

/**
 * Enhanced table model interface that extends {@link TableModel} with cell rendering, editing, and column configuration capabilities.
 * <p>
 * This interface provides methods for defining custom cell renderers and editors per column,
 * configuring column widths (including fixed and auto-sized columns), and managing tooltip text
 * for table columns.
 * </p>
 *
 * @author optyfr
 * @see TableModel
 * @see javax.swing.table.TableCellRenderer
 * @see javax.swing.table.TableCellEditor
 */
public interface EnhTableModel extends TableModel {
    /**
     * Returns the array of cell renderers for each column.
     *
     * @return an array of {@link TableCellRenderer} objects, one per column
     */
    public abstract TableCellRenderer[] getCellRenderers();

    /**
     * Returns the array of cell editors for each column.
     * <p>
     * Default implementation returns an empty array, indicating no custom editors.
     * </p>
     *
     * @return an array of {@link TableCellEditor} objects, one per column
     */
    public default TableCellEditor[] getCellEditors() {
        return new TableCellEditor[0];
    }

    /**
     * Returns the cell renderer for the specified column.
     *
     * @param columnIndex the zero-based column index
     * @return the {@link TableCellRenderer} associated with the specified column
     */
    public default TableCellRenderer getColumnRenderer(int columnIndex) {
        return getCellRenderers()[columnIndex];
    }

    /**
     * Returns the cell editor for the specified column.
     *
     * @param columnIndex the zero-based column index
     * @return the {@link TableCellEditor} associated with the specified column
     */
    public default TableCellEditor getColumnEditor(int columnIndex) {
        final var editors = getCellEditors();
        if (editors == null || columnIndex < 0 || columnIndex >= editors.length)
            return null;
        return editors[columnIndex];
    }

    /**
     * Returns the preferred width for the specified column.
     * <p>
     * Positive values indicate preferred width in pixels. Negative values indicate fixed width
     * (the absolute value is used as the fixed width).
     * </p>
     *
     * @param columnIndex the zero-based column index
     * @return the column width in pixels; negative values indicate fixed width
     */
    public abstract int getColumnWidth(int columnIndex);

    /**
     * Returns the tooltip text for the specified column.
     *
     * @param columnIndex the zero-based column index
     * @return the tooltip text for the column header
     */
    public abstract String getColumnTT(int columnIndex);

    /**
     * Applies the configured column widths to the specified table.
     * <p>
     * For positive widths, sets the minimum and preferred width. For negative widths, sets
     * the minimum, maximum, and preferred width to the absolute value, creating a fixed-width column.
     * </p>
     *
     * @param table the {@link JTable} to configure
     */
    public default void applyColumnsWidths(JTable table) {
        for (int i = 0; i < getColumnCount(); i++) {
            int width = getColumnWidth(i);
            TableColumn column = table.getColumnModel().getColumn(i);
            if (width > 0) {
                column.setMinWidth(width / 2);
                column.setPreferredWidth(width);
            } else if (width < -20) {
                column.setMinWidth(-width);
                column.setMaxWidth(-width);
            } else if (width < 0) {
                final Component component = column.getCellRenderer().getTableCellRendererComponent(table, null, false, false, 0, i);
                final var format = "%0" + (-width) + "d";
                final int pixwidth = component.getFontMetrics(component.getFont()).stringWidth(String.format(format, 0)); // $NON-NLS-1$
                                                                                                                          // //$NON-NLS-2$
                column.setMinWidth(pixwidth / 2);
                column.setPreferredWidth(pixwidth);
                column.setMaxWidth(pixwidth);
            }
        }
    }
}
