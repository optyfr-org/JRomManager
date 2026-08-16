/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.data;

import java.io.IOException;
import java.io.Serializable;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jrm.profile.Profile;
import lombok.Getter;
import lombok.Setter;

/**
 * A list of {@link Anyware} objects lists. Represents a parent container aggregating collections of software or arcade machines.
 * 
 * @author optyfr
 *
 * @param <T> extends {@link AnywareList} (generally a {@link Machine} or a {@link Software})
 */
public abstract class AnywareListList<T extends AnywareList<? extends Anyware>> implements Serializable, AWList<T> {
    /**
     * The profile associated with this list of lists. Used to retrieve filter levels and configuration metrics.
     * 
     * @param profile the profile to set
     * 
     * @return the associated profile
     */
    protected @Getter @Setter Profile profile;

    /**
     * {@link T} list cache (according current {@link Profile#filterListLists})
     */
    protected transient List<T> filteredList;

    /**
     * The constructor, will initialize transients fields.
     * 
     * @param profile the {@link Profile} to associate with this list of lists
     */
    protected AnywareListList(Profile profile) {
        this.profile = profile;
        initTransient();
    }

    /**
     * the Serializable method for special serialization handling (in that case : initialize transient default values)
     * 
     * @param in the serialization inputstream
     * 
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if the class of a serialized object cannot be found
     */
    private void readObject(final java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initTransient();
    }

    /**
     * The method called to initialize transient and static fields.
     */
    protected void initTransient() {
        filteredList = null;
    }

    /**
     * resets {@link T} list cache and fire a TableChanged event to listeners.
     */
    public abstract void resetCache();

    /**
     * resets {@link T} list cache and fire a TableChanged event to listeners.
     * 
     * @param filter the new {@link EnumSet} of {@link AnywareStatus} filter to apply
     */
    public abstract void setFilterCache(final Set<AnywareStatus> filter);

    /**
     * Counts the number of items in this list of lists.
     * 
     * @return the total number of lists as an integer
     */
    public abstract int count();

    /**
     * Gets the {@link AnywareList} object at the specified index.
     * 
     * @param i the index of the object to retrieve
     * 
     * @return the {@link AnywareList} at the specified index
     */
    public abstract AnywareList<? extends Anyware> getObject(int i); // NOSONAR

    /**
     * Gets the description of the item at the specified index.
     * 
     * @param i the index of the item
     * 
     * @return a {@link String} containing the description
     */
    public abstract String getDescription(int i);

    /**
     * Gets the total number of possessed/correct elements for the item at the specified index as a formatted string.
     * 
     * @param i the index of the item
     * 
     * @return a formatted {@link String} representing the owned elements count
     */
    public abstract String getHaveTot(int i);
}
