package jrm.ui.profile.data;

import java.util.Set;

import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import jrm.profile.data.Anyware;
import jrm.profile.data.EntityStatus;
import jrm.ui.basic.AbstractEnhTableModel;

/**
 * Table model for displaying individual entity entries of an anyware.
 * <p>
 * Shows detailed information about each ROM/disk entity including name,
 * size, checksums (CRC, MD5, SHA1), and status.
 */
public class AnywareModel extends AbstractEnhTableModel {
    /** The underlying anyware whose entities are displayed. */
    private final Anyware anyware;

    /**
     * Constructs a new anyware model.
     *
     * @param anyware the anyware whose entities to display
     */
    public AnywareModel(Anyware anyware) {
        this.anyware = anyware;
    }

    @Override
    public int getRowCount() {
        return anyware.getEntities().size();
    }

    @Override
    public int getColumnCount() {
        return AnywareRenderer.columns.length;
    }

    @Override
    public String getColumnName(int columnIndex) {
        return AnywareRenderer.columns[columnIndex];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return AnywareRenderer.columnsTypes[columnIndex];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        switch (columnIndex) {
            case 0:
                return anyware.getEntities().get(rowIndex);
            case 1:
                return anyware.getEntities().get(rowIndex);
            case 2:
                return anyware.getEntities().get(rowIndex).getProperty("size"); //$NON-NLS-1$
            case 3:
                return anyware.getEntities().get(rowIndex).getProperty("crc"); //$NON-NLS-1$
            case 4:
                return anyware.getEntities().get(rowIndex).getProperty("md5"); //$NON-NLS-1$
            case 5:
                return anyware.getEntities().get(rowIndex).getProperty("sha1"); //$NON-NLS-1$
            case 6:
                return anyware.getEntities().get(rowIndex).getProperty("merge"); //$NON-NLS-1$
            case 7:
                return anyware.getEntities().get(rowIndex).getProperty("status"); //$NON-NLS-1$
            default:
                return null;
        }
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        // do nothing
    }

    @Override
    public TableCellRenderer[] getCellRenderers() {
        return AnywareRenderer.columnsRenderers;
    }

    @Override
    public int getColumnWidth(int columnIndex) {
        return AnywareRenderer.columnsWidths[columnIndex];
    }

    @Override
    public String getColumnTT(int columnIndex) {
        return AnywareRenderer.columns[columnIndex];
    }

    /**
     * get the declared renderer for a given column
     * 
     * @param columnIndex the requested column index
     * 
     * @return a {@link TableCellRenderer} associated with the given columnindex
     */
    @Override
    public TableCellRenderer getColumnRenderer(final int columnIndex) {
        return columnIndex < AnywareRenderer.columnsRenderers.length && AnywareRenderer.columnsRenderers[columnIndex] != null ? AnywareRenderer.columnsRenderers[columnIndex]
                : new DefaultTableCellRenderer();
    }

    /**
     * resets entities list cache and fire a TableChanged event to listeners
     */
    public void reset() {
        anyware.resetCache();
        fireTableChanged(new TableModelEvent(this));
    }

    /**
     * Set a new Entity status set filter and reset list cache
     * 
     * @param filter the new entity status set filter to apply
     */
    public void setFilter(final Set<EntityStatus> filter) {
        anyware.setFilterCache(filter);
        reset();
    }

}
