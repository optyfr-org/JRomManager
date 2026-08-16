package jrm.profile.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import lombok.Getter;

/**
 * A container wrapper representing a serializable collection of {@link Source} entries. Implements {@link Iterable} over the
 * enclosed sources.
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public class Sources implements Serializable, Iterable<Source> {
    /**
     * Backing list containing the registered DAT metadata sources.
     * 
     * @return the list of registered sources
     */
    private final @Getter List<Source> srces = new ArrayList<>();

    /**
     * Registers a metadata source into the collection list.
     * 
     * @param source the source to add
     * 
     * @return true if added successfully, false otherwise
     */
    public boolean add(final Source source) {
        return srces.add(source);
    }

    /**
     * Returns an iterator over the sources.
     * 
     * @return an iterator over {@link Source} elements
     */
    @Override
    public Iterator<Source> iterator() {
        return srces.iterator();
    }

    /** Default constructor for Sources. Initializes an empty collection of sources. */
    public Sources() {
        /* default constructor */ }
}
