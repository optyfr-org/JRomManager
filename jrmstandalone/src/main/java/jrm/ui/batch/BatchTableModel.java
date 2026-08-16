package jrm.ui.batch;

import java.awt.Color;
import java.awt.Component;
import java.io.File;

import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

import jrm.locale.Messages;
import jrm.ui.basic.JTableButton;
import jrm.ui.basic.SDRTableModel;

/**
 * Table model for batch operations.
 * <p>
 * Manages data for batch processing tables with source/destination columns.
 */
public class BatchTableModel extends SDRTableModel {

    private final class FCellRenderer extends FileCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (row == getCurrentRow() && column == getCurrentCol())
                setBackground(Color.decode("#DDFFDD")); //$NON-NLS-1$
            else
                setBackground(Color.white);
            if (value instanceof File f) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setText(trimmedStringCalculator(f.getPath(), table, this, table.getColumnModel().getColumn(column).getWidth() - 10));
                return this;
            }
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        }
    }

    private final String[] headers;
    private final String[] headersTT;
    private final Class<?>[] types = { File.class, File.class, String.class, String.class, Boolean.class };
    private final int[] widths = { 0, 0, 0, -70, -22 };

    public BatchTableModel() {
        this.headers = new String[] { Messages.getString("BatchTableModel.SrcDats"), Messages.getString("BatchTableModel.DstDirs"), Messages.getString("BatchTableModel.Result"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "Details", "Selected" };
        this.headersTT = this.headers;
    }

    public BatchTableModel(String[] headers) {
        this.headers = headers;
        this.headersTT = headers;
    }

    public void setButtonHandler(JTableButton.TableButtonPressedHandler buttonHandler) {
        buttons.addHandler(buttonHandler);
    }

    private JTableButton buttons = new JTableButton();

    private final TableCellRenderer[] cellRenderers = { new FileCellRenderer(), new FCellRenderer(), new StatusCellRenderer(), buttons, null };

    private final TableCellEditor[] cellEditors = { null, null, null, buttons, null };

    @Override
    public int getRowCount() {
        return getData().size();
    }

    @Override
    public int getColumnCount() {
        return cellRenderers.length;
    }

    @Override
    public String getColumnName(int columnIndex) {
        return headers[columnIndex];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return types[columnIndex];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex >= 3;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        switch (columnIndex) {
            case 0:
                return getData().get(rowIndex).getSrc();
            case 1:
                return getData().get(rowIndex).getDst();
            case 2:
                return getData().get(rowIndex).getResult();
            case 3:
                return "Detail";
            case 4:
                return getData().get(rowIndex).isSelected();
            default:
                return null;
        }
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        switch (columnIndex) {
            case 0:
                getData().get(rowIndex).setSrc(((File) aValue).getPath());
                break;
            case 1:
                getData().get(rowIndex).setDst(((File) aValue).getPath());
                break;
            case 2:
                getData().get(rowIndex).setResult((String) aValue);
                break;
            case 3:
                return;
            case 4:
                getData().get(rowIndex).setSelected((Boolean) aValue);
                break;
            default:
                break;
        }
        fireTableChanged(new TableModelEvent(this, rowIndex, rowIndex, columnIndex, TableModelEvent.UPDATE));
    }

    /**
     * @return the cellRenderers
     */
    @Override
    public TableCellRenderer[] getCellRenderers() {
        return cellRenderers;
    }

    /**
     * @return the cellRenderers
     */
    @Override
    public TableCellEditor[] getCellEditors() {
        return cellEditors;
    }

    @Override
    public int getColumnWidth(int columnIndex) {
        return widths[columnIndex];
    }

    @Override
    public String getColumnTT(int columnIndex) {
        return headersTT[columnIndex];
    }

}
