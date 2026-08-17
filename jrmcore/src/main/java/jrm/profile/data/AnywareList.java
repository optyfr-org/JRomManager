/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.data;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import jrm.profile.Profile;

/**
 * A list of {@link Anyware} objects. This class serves as an abstract base for specialized collections of arcade machines or
 * software systems.
 * 
 * @author optyfr
 *
 * @param <T> extends {@link Anyware} (generally a {@link Machine} or a {@link Software})
 */
public abstract class AnywareList<T extends Anyware> extends NameBase implements AWList<T>, ByName<T> {
    /**
     * The profile associated with this list. Used to retrieve filter and configuration options.
     */
    Profile profile;

    /**
     * {@link T} list cache (according current {@link Profile#filterList})
     */
    protected transient List<T> filteredList;

    /**
     * The constructor, will initialize transients fields.
     * 
     * @param profile the {@link Profile} to associate with this list
     */
    protected AnywareList(Profile profile) {
        this.profile = profile;
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
    public void resetCache() {
        this.filteredList = null;
    }

    /**
     * resets {@link T} list cache and fire a TableChanged event to listeners.
     * 
     * @param filter the new {@link EnumSet} of {@link AnywareStatus} filter to apply
     */
    public void setFilterCache(final Set<AnywareStatus> filter) {
        profile.setFilterList(filter);
    }

    /**
     * Gets the current filters applied to this list.
     * 
     * @return a {@link Set} of {@link AnywareStatus} representing the active filters
     */
    public Set<AnywareStatus> getFilter() {
        return profile.getFilterList();
    }

    /**
     * get the overall current status according the status of all its currently filtered {@link Anyware}s.
     * 
     * @return an {@link AnywareStatus} representing the combined status of all items in this list
     */
    public AnywareStatus getStatus() {
        AnywareStatus status = AnywareStatus.COMPLETE;
        var ok = false;
        for (final Iterator<T> iterator = getFilteredStream().iterator(); iterator.hasNext();) {
            final AnywareStatus estatus = iterator.next().getStatus();
            if (estatus == AnywareStatus.PARTIAL || estatus == AnywareStatus.MISSING)
                status = AnywareStatus.PARTIAL;
            else if (estatus == AnywareStatus.COMPLETE)
                ok = true;
            else if (estatus == AnywareStatus.UNKNOWN) {
                status = AnywareStatus.UNKNOWN;
                break;
            }
        }
        if (status == AnywareStatus.PARTIAL && !ok)
            status = AnywareStatus.MISSING;
        return status;
    }

    /**
     * count the number of correct wares we have in this list.
     * 
     * @return a long which is the total counted
     */
    public abstract long countHave();

    /**
     * count the number of wares contained in this list, whether they are OK or not.
     * 
     * @return a long which is the sum of all the wares
     */
    public abstract long countAll();

    /**
     * Find the index of a given {@link Anyware} in the filtered list.
     * 
     * @param anyware the given {@link Anyware}
     * 
     * @return the int index or -1 if not found
     */
    public int find(final Anyware anyware) {
        return getFilteredList().indexOf(anyware);
    }

    /**
     * Find the first index of the {@link Anyware} for which its name starts with the search string.
     * 
     * @param search the {@link String} to search for
     * 
     * @return the int index or -1 if not found
     */
    public int find(final String search) {
        return find(getFilteredStream().filter(s -> s.getName().toLowerCase().startsWith(search.toLowerCase())).findFirst().orElse(null));
    }

    /**
     * Compares the specified object with this list for equality.
     * 
     * @param obj the object to compare with
     * 
     * @return {@code true} if the objects are equal, {@code false} otherwise
     */
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    /**
     * Returns the hash code value for this list.
     * 
     * @return the hash code value of this list
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
