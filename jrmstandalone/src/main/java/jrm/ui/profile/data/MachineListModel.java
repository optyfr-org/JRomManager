package jrm.ui.profile.data;

import java.util.Optional;
import java.util.Set;

import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellRenderer;

import jrm.profile.data.AnywareStatus;
import jrm.profile.data.Machine;
import jrm.profile.data.MachineList;

/**
 * Table model for displaying a list of machines.
 * <p>
 * Provides data for the machine list table with columns for status, name, description,
 * have count, clone/rom/sample relationships, and selection state.
 */
public class MachineListModel extends AnywareListModel<Machine> {
    /** The underlying machine list data. */
    MachineList machineList;

    /**
     * Constructs a new machine list model.
     *
     * @param machineList the machine list to display
     */
    public MachineListModel(MachineList machineList) {
        this.machineList = machineList;
    }

    @Override
    public TableCellRenderer[] getCellRenderers() {
        return MachineListRenderer.columnsRenderers;
    }

    @Override
    public int getColumnWidth(int columnIndex) {
        return MachineListRenderer.columnsWidths[columnIndex];
    }

    @Override
    public String getColumnTT(int columnIndex) {
        return MachineListRenderer.columns[columnIndex];
    }

    @Override
    public int getRowCount() {
        return machineList.getFilteredList().size();
    }

    @Override
    public int getColumnCount() {
        return MachineListRenderer.columns.length;
    }

    @Override
    public String getColumnName(int columnIndex) {
        return MachineListRenderer.columns[columnIndex];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return MachineListRenderer.columnsTypes[columnIndex];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 7;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        final Machine machine = machineList.getFilteredList().get(rowIndex);
        switch (columnIndex) {
            case 0:
                return machine;
            case 1:
                return machine;
            case 2:
                return machine.description.toString();
            case 3:
                return String.format("%d/%d", machine.countHave(), machine.countAll()); //$NON-NLS-1$
            case 4:
                return Optional.ofNullable(machine.getCloneof()).map(cloneof -> machineList.containsName(cloneof) ? machineList.getByName(cloneof) : cloneof).orElse(null);
            case 5:
                return Optional.ofNullable(machine.getRomof()).filter(romof -> !romof.equals(machine.getCloneof()))
                        .map(romof -> machineList.containsName(romof) ? machineList.getByName(romof) : romof).orElse(null);
            case 6:
                return Optional.ofNullable(machine.getSampleof())
                        .map(sampleof -> machineList.samplesets.containsName(sampleof) ? machineList.samplesets.getByName(sampleof) : sampleof).orElse(null);
            case 7:
                return machine.isSelected();
            default:
                return null;
        }
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex == 7 && aValue instanceof Boolean b) {
            final Machine machine = machineList.getFilteredList().get(rowIndex);
            machine.setSelected(b);
        }
    }

    @Override
    public TableCellRenderer getColumnRenderer(int columnIndex) {
        return MachineListRenderer.columnsRenderers[columnIndex];
    }

    @Override
    public void reset() {
        machineList.resetCache();
        fireTableChanged(new TableModelEvent(this));
    }

    @Override
    public MachineList getList() {
        return machineList;
    }

    @Override
    public void setFilter(Set<AnywareStatus> filter) {
        machineList.setFilterCache(filter);
        reset();
    }
}
