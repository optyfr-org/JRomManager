package jrm.ui.profile.data;

import java.util.Set;

import javax.swing.event.TableModelEvent;

import jrm.profile.data.AnywareStatus;
import jrm.profile.data.MachineListList;

/**
 * Table model for displaying a list of machine lists.
 * <p>
 * Combines machine lists and software lists into a single table model,
 * showing aggregated statistics for each list.
 */
public class MachineListListModel extends AnywareListListModel {
    /** The underlying machine list list data. */
    private MachineListList machineListList;

    /** The nested software list list model for displaying software lists. */
    private SoftwareListListModel sllmodel;

    /**
     * Constructs a new machine list list model.
     *
     * @param machineListList the machine list list to display
     */
    public MachineListListModel(MachineListList machineListList) {
        this.machineListList = machineListList;
        sllmodel = new SoftwareListListModel(machineListList.getSoftwareListList());
    }

    @Override
    public int getRowCount() {
        return machineListList.getList().size() + sllmodel.getRowCount();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < machineListList.getList().size()) {
            switch (columnIndex) {
                case 0:
                    return machineListList.getObject(rowIndex);
                case 1:
                    return machineListList.getDescription(rowIndex);
                case 2:
                    return machineListList.getHaveTot(rowIndex);
                default:
                    return null;
            }
        } else
            return sllmodel.getValueAt(rowIndex - machineListList.getList().size(), columnIndex);
    }

    @Override
    public void reset() {
        machineListList.resetCache();
        fireTableChanged(new TableModelEvent(this));
    }

    @Override
    public void setFilter(final Set<AnywareStatus> filter) {
        machineListList.setFilterCache(filter);
        reset();
    }
}
