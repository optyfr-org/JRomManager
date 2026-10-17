package jrm.ui.profile.data;

import java.util.Set;

import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellRenderer;

import jrm.profile.data.AnywareStatus;
import jrm.profile.data.Software;
import jrm.profile.data.SoftwareList;

/**
 * Table model for displaying a list of software entries.
 * <p>
 * Provides data for the software list table with columns for status, name,
 * description, have count, clone relationships, and selection state.
 */
public class SoftwareListModel extends AnywareListModel<Software> {
    /** The underlying software list data. */
    private SoftwareList softwareList;

    /**
     * Constructs a new software list model.
     *
     * @param softwareList the software list to display
     */
    public SoftwareListModel(SoftwareList softwareList) {
        this.softwareList = softwareList;
    }

    @Override
    public TableCellRenderer[] getCellRenderers() {
        return SoftwareListRenderer.columnsRenderers;
    }

    @Override
    public int getColumnWidth(int columnIndex) {
        return SoftwareListRenderer.columnsWidths[columnIndex];
    }

    @Override
    public String getColumnTT(int columnIndex) {
        return SoftwareListRenderer.columns[columnIndex];
    }

    @Override
    public int getRowCount() {
        return softwareList.getFilteredList().size();
    }

    @Override
    public int getColumnCount() {
        return SoftwareListRenderer.columns.length;
    }

    @Override
    public String getColumnName(int columnIndex) {
        return SoftwareListRenderer.columns[columnIndex];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return SoftwareListRenderer.columnsTypes[columnIndex];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 5;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        final Software software = softwareList.getFilteredList().get(rowIndex);
        switch (columnIndex) {
            case 0:
                return software;
            case 1:
                return software;
            case 2:
                return software.description.toString();
            case 3:
                return String.format("%d/%d", software.countHave(), software.getRoms().size() + software.getDisks().size()); //$NON-NLS-1$
            case 4:
                return software.getCloneof() != null ? softwareList.getByName(software.getCloneof()) : null;
            case 5:
                return software.isSelected();
            default:
                return null;
        }
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex == 5 && aValue instanceof Boolean b) {
            final Software software = softwareList.getFilteredList().get(rowIndex);
            software.setSelected(b);
        }
    }

    @Override
    public void reset() {
        softwareList.resetCache();
        fireTableChanged(new TableModelEvent(this));
    }

    @Override
    public TableCellRenderer getColumnRenderer(int columnIndex) {
        return SoftwareListRenderer.columnsRenderers[columnIndex];
    }

    @Override
    public SoftwareList getList() {
        return softwareList;
    }

    @Override
    public void setFilter(Set<AnywareStatus> filter) {
        softwareList.setFilterCache(filter);
        reset();
    }
}
