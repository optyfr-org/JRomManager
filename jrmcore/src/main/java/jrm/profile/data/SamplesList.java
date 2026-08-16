/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.data;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import lombok.Getter;

/**
 * Set of unique {@link Samples} sets. This class maps sample sets by their unique names.
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public class SamplesList implements ByName<Samples>, Iterable<Samples> {
    /**
     * {@link HashMap} of {@link Samples} set with {@link Samples#name} as key.
     * 
     * @return the map of sample sets by name
     */
    private final @Getter Map<String, Samples> sampleSets = new HashMap<>();

    /**
     * Checks if the sample list contains a set with the specified name.
     * 
     * @param name the name to look for
     * 
     * @return true if the name exists, false otherwise
     */
    @Override
    public boolean containsName(String name) {
        return sampleSets.containsKey(name);
    }

    /**
     * Retrieves the sample set with the specified name.
     * 
     * @param name the name of the sample set
     * 
     * @return the matching {@link Samples} set, or null if not found
     */
    @Override
    public Samples getByName(String name) {
        return sampleSets.get(name);
    }

    /**
     * Stores the given sample set under its unique name.
     * 
     * @param t the {@link Samples} set to add
     * 
     * @return the previously associated {@link Samples} set, or null if there was none
     */
    @Override
    public Samples putByName(Samples t) {
        return sampleSets.put(t.name, t);
    }

    /**
     * Returns an iterator over all sample sets in this list.
     * 
     * @return an iterator over {@link Samples} sets
     */
    @Override
    public Iterator<Samples> iterator() {
        return sampleSets.values().iterator();
    }

    /**
     * Returns the number of sample sets in this list.
     * 
     * @return the count of sample sets
     */
    public int size() {
        return sampleSets.size();
    }

    /**
     * Resets the filtered name cache. Unused in this implementation.
     */
    @Override
    public void resetFilteredName() {
        // unused
    }

    /**
     * Checks if the list contains a sample set with the specified filtered name. Delegates directly to
     * {@link #containsName(String)}.
     * 
     * @param name the filtered name to check
     * 
     * @return true if found, false otherwise
     */
    @Override
    public boolean containsFilteredName(String name) {
        return containsName(name);
    }

    /**
     * Retrieves the sample set associated with the specified filtered name. Delegates directly to {@link #getByName(String)}.
     * 
     * @param name the filtered name of the sample set
     * 
     * @return the matching {@link Samples} set, or null if not found
     */
    @Override
    public Samples getFilteredByName(String name) {
        return getByName(name);
    }

    /**
     * Constructs a new empty SamplesList. This constructor initializes the internal map to store sample sets by their unique names.
     */
    public SamplesList() {
        // default constructor
    }
}
