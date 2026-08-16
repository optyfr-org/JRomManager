package jrm.ui.batch;

import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.io.File;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Cell renderer for File objects in tables.
 * <p>
 * Displays file paths with ellipsis truncation when the path is too long.
 */
class FileCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        setBackground(Color.white);
        if (value instanceof File f) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setText(trimmedStringCalculator(f.getPath(), table, this, table.getColumnModel().getColumn(column).getWidth() - 10));
            return this;
        }
        return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

    }

    protected static String trimmedStringCalculator(String inputText, JTable table, JLabel component, int width) {
        String ellipses = "..."; //$NON-NLS-1$
        final var textToBeDisplayed = new StringBuilder(); // $NON-NLS-1$
        FontMetrics fm = table.getFontMetrics(component.getFont());
        for (int i = inputText.length() - 1; i >= 0; i--)
            if (fm.stringWidth(ellipses + textToBeDisplayed) <= width)
                textToBeDisplayed.insert(0, inputText);
        if (0 != CharSequence.compare(textToBeDisplayed, inputText))
            return String.join(ellipses, textToBeDisplayed);
        return inputText;
    }

}