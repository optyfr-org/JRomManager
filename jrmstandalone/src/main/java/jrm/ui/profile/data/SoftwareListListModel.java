package jrm.ui.profile.data;

import java.util.Set;

import javax.swing.event.TableModelEvent;

import jrm.profile.data.AnywareStatus;
import jrm.profile.data.SoftwareListList;

/**
 * Table model for displaying a list of software lists.
 * <p>
 * Provides data for the software list list table, showing aggregated
 * statistics for each software list.
 */
public class SoftwareListListModel extends AnywareListListModel {
    /** The underlying software list list data. */
    private SoftwareListList softwareListList;

    /**
     * Constructs a new software list list model.
     *
     * @param softwareListList the software list list to display
     */
    public SoftwareListListModel(SoftwareListList softwareListList) {
        this.softwareListList = softwareListList;
    }

    @Override
    public int getRowCount() {
        return softwareListList.getFilteredList().size();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        switch (columnIndex) {
            case 0:
                return softwareListList.getObject(rowIndex);
            case 1:
                return softwareListList.getDescription(rowIndex);
            case 2:
                return softwareListList.getHaveTot(rowIndex);
            default:
                return null;
        }
    }

    @Override
    public void reset() {
        softwareListList.resetCache();
        fireTableChanged(new TableModelEvent(this));
    }

    @Override
    public void setFilter(final Set<AnywareStatus> filter) {
        softwareListList.setFilterCache(filter);
        reset();
    }
}
