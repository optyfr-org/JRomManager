package jrm.aui.basic;

import java.util.ArrayList;
import java.util.Collection;

import lombok.Getter;

/**
 * SDRList is a custom implementation of an ArrayList that includes a flag to indicate whether the list needs to be saved. It
 * provides methods to check if the list needs saving and to set the flag accordingly.
 *
 * @param <T> the type of elements in this list
 */
public class SDRList<T> extends ArrayList<T> {

    /**
     * A flag to indicate whether the list needs to be saved. It is set to true when the list is modified and false when it is
     * saved.
     * 
     * @return true if the list needs to be saved, false otherwise
     */
    @Getter
    boolean needSave = false;

    /**
     * Constructs an empty SDRList.
     */
    public SDRList() {
        super();
    }

    /**
     * Constructs an SDRList containing the elements of the specified collection.
     *
     * @param c the collection whose elements are to be placed into this list
     */
    public SDRList(Collection<? extends T> c) {
        super(c);
    }
    
    /**
     * Compares this SDRList to the specified object for equality. Only returns true if the other object is also an SDRList and the
     * contents are equal.
     *
     * @param o the object to compare for equality
     * @return true if the specified object is an SDRList with equal contents, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof SDRList && super.equals(o);
    }
    
    @Override
    public int hashCode() {
        return super.hashCode();
    } 
}