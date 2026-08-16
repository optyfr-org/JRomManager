package jrm.ui.profile.data;

import java.util.ArrayList;
import java.util.Collection;

import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;

/**
 * List model providing a sorted collection of year strings for combo boxes.
 * <p>
 * Used in profile filter panels to allow filtering machines by release year.
 */
public final class YearsModel extends AbstractListModel<String> implements ComboBoxModel<String> {

    /** The currently selected object. */
    transient Object selectedObject = null;

    /** The internal sorted list of year strings. */
    final ArrayList<String> years;

    /**
     * Constructs a new years model, building a sorted list from the given collection.
     *
     * @param years a {@link Collection} of year {@link String}s
     */
    public YearsModel(final Collection<String> years) {
        this.years = new ArrayList<>(years);
        this.years.sort(String::compareTo);
    }

    /**
     * Returns the index-position of the specified object in the list.
     *
     * @param anObject The combo box value
     * 
     * @return an int representing the index position, where 0 is the first position
     */
    public int getIndexOf(final Object anObject) {
        return years.indexOf(anObject);
    }

    // implements javax.swing.ComboBoxModel
    /**
     * Set the value of the selected item. The selected item may be null.
     *
     * @param anObject The combo box value or null for no selection.
     */
    @Override
    public void setSelectedItem(final Object anObject) {
        if ((selectedObject != null && !selectedObject.equals(anObject)) || selectedObject == null && anObject != null) {
            selectedObject = anObject;
            fireContentsChanged(this, -1, -1);
        }
    }

    // implements javax.swing.ComboBoxModel
    @Override
    public Object getSelectedItem() {
        return selectedObject;
    }

    // implements javax.swing.ListModel
    @Override
    public int getSize() {
        return years.size();
    }

    // implements javax.swing.ListModel
    @Override
    public String getElementAt(final int index) {
        if (index >= 0 && index < years.size())
            return years.get(index);
        else
            return null;
    }

}
