package jrm.ui.basic;

import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractCellEditor;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

/**
 * A custom table cell editor and renderer for displaying buttons in a JTable.
 * <p>
 * This class provides a way to add interactive buttons to table cells, allowing users to
 * trigger actions when clicking on them.
 * </p>
 *
 * @see AbstractCellEditor
 * @see TableCellEditor
 * @see TableCellRenderer
 */
public class JTableButton extends AbstractCellEditor implements TableCellEditor, TableCellRenderer {
    /**
     * Handler interface for button press events within table cells.
     */
    public interface TableButtonPressedHandler {
        /**
         * Called when the button is pressed.
         * 
         * @param row The row in which the button is in the table.
         * @param column The column the button is in in the table.
         */
        void onButtonPress(int row, int column);
    }

    /** The list of registered button press handlers. */
    private transient List<TableButtonPressedHandler> handlers;
    /** The map of row indices to their corresponding button instances. */
    private Map<Integer, JButton> buttons;

    /**
     * Constructs a new table button cell editor/renderer.
     */
    public JTableButton() {
        handlers = new ArrayList<>();
        buttons = new HashMap<>();
    }

    /**
     * Adds a button press callback handler.
     *
     * @param handler the {@link TableButtonPressedHandler} to add
     */
    public void addHandler(TableButtonPressedHandler handler) {
        if (handlers != null) {
            handlers.add(handler);
        }
    }

    /**
     * Removes a previously registered button press callback handler.
     *
     * @param handler the {@link TableButtonPressedHandler} to remove
     */
    public void removeHandler(TableButtonPressedHandler handler) {
        if (handlers != null) {
            handlers.remove(handler);
        }
    }

    /**
     * Removes the button component at the specified row index.
     *
     * @param row the row index whose button should be removed
     */
    public void removeRow(int row) {
        if (buttons.containsKey(row)) {
            buttons.remove(row);
        }
    }

    /**
     * Moves the button component from one row index to another.
     *
     * @param oldRow the source row index
     * @param newRow the destination row index
     */
    public void moveRow(int oldRow, int newRow) {
        if (buttons.containsKey(oldRow)) {
            JButton button = buttons.remove(oldRow);
            buttons.put(newRow, button);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a {@link JButton} for the specified row, creating one if it does not yet exist.
     * The button's text is set from the cell value if it is a string, and a press handler is
     * registered to notify all registered handlers.
     * </p>
     *
     * @param table the {@link JTable} rendering this cell
     * @param value the value of the cell
     * @param selected whether the cell is selected
     * @param focus whether the cell has focus
     * @param row the row index
     * @param column the column index
     * @return the {@link Component} for rendering
     */
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, final int row, final int column) {
        JButton button = null;
        if (buttons.containsKey(row)) {
            button = buttons.get(row);
        } else {
            button = new JButton();
            if (value instanceof String s) {
                button.setText(s);
            }
            button.addActionListener(_ -> {
                if (handlers != null)
                    for (TableButtonPressedHandler handler : handlers)
                        handler.onButtonPress(row, column);
            });
            buttons.put(row, button);
        }

        return button;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a {@link JButton} for the specified row, creating one if it does not yet exist.
     * The button's text is set from the cell value if it is a string.
     * </p>
     *
     * @param table the {@link JTable} editing this cell
     * @param value the value of the cell
     * @param selected whether the cell is selected
     * @param row the row index
     * @param column the column index
     * @return the {@link Component} for editing
     */
    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean selected, int row, int column) {
        JButton button = null;
        if (buttons.containsKey(row)) {
            button = buttons.get(row);
        } else {
            button = new JButton();
            if (value instanceof String s)
                button.setText(s);
            buttons.put(row, button);
        }

        return button;
    }

    /**
     * Sets the text of the button at the specified row.
     *
     * @param row the row index of the button
     * @param text the new button text
     */
    public void setButtonText(int row, String text) {
        JButton button = null;
        if (buttons.containsKey(row)) {
            button = buttons.get(row);
            button.setText(text);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return always {@code null}
     */
    @Override
    public Object getCellEditorValue() {
        return null;
    }

    /**
     * Releases all registered handlers.
     */
    public void dispose() {
        if (handlers != null) {
            handlers.clear();
        }
    }
}
