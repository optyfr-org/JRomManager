/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.data;

import java.io.Serializable;

/**
 * Interface describing operations related to entity name manipulation and name-based caches. It provides methods for resetting,
 * checking, and retrieving elements of type {@code T} from both filtered and non-filtered caches based on their names.
 *
 * @author optyfr
 * 
 * @param <T> any class that extends {@link NameBase}
 */
public interface ByName<T extends NameBase> extends Serializable {
    /**
     * Requests the re-evaluation and reset of the filtered name map cache.
     */
    public void resetFilteredName();

    /**
     * Checks if the specified name exists in the filtered name map cache.
     *
     * @param name the entity name to search for
     * 
     * @return {@code true} if the name is found in the filtered cache, {@code false} otherwise
     */
    public boolean containsFilteredName(String name);

    /**
     * Checks if the specified name exists in the non-filtered name map cache.
     *
     * @param name the entity name to search for
     * 
     * @return {@code true} if the name is found in the non-filtered cache, {@code false} otherwise
     */
    public boolean containsName(String name);

    /**
     * Retrieves the entity of type {@code T} by its name from the filtered name map cache.
     *
     * @param name the entity name to retrieve
     * 
     * @return the entity of type {@code T} associated with the name, or {@code null} if not found
     */
    public T getFilteredByName(String name);

    /**
     * Retrieves the entity of type {@code T} by its name from the non-filtered name map cache.
     *
     * @param name the entity name to retrieve
     * 
     * @return the entity of type {@code T} associated with the name, or {@code null} if not found
     */
    public T getByName(String name);

    /**
     * Associates the specified entity of type {@code T} with its name in the non-filtered name map cache.
     *
     * @param t the entity of type {@code T} to add to the cache
     * 
     * @return the previous entity of type {@code T} associated with the name, or {@code null} if there was no mapping
     */
    T putByName(T t);
}
