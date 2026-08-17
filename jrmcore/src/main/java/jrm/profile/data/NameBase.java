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
 * The base class for named data entities.
 *
 * @author optyfr
 */
abstract class NameBase implements Serializable, Comparable<NameBase> {
    private static final long serialVersionUID = 1L;

    /**
     * The name of the entity.
     */
    protected String name = ""; // required //$NON-NLS-1$

    /**
     * Retrieves the forged name of the entity, which may be modified depending on its concrete subclass implementation.
     *
     * @return the name of the entity
     */
    public abstract String getName();

    /**
     * Retrieves the untouched (original, non-forged) name of the entity.
     *
     * @return the name as defined initially by {@link #setName(String)}
     */
    public final String getBaseName() {
        return name;
    }

    /**
     * Retrieves a normalized version of the name where backslashes are replaced by forward slashes.
     *
     * @return the normalized name string
     */
    public final String getNormalizedName() {
        return getName().replace('\\', '/');
    }

    /**
     * Sets the name of the entity.
     *
     * @param name the name to set
     */
    public final void setName(final String name) {
        this.name = name;
    }

    /**
     * Compares this entity with another named entity by name lexicographically.
     *
     * @param o the other entity to compare with
     * 
     * @return a negative integer, zero, or a positive integer as this name is less than, equal to, or greater than the specified
     *         entity's name
     */
    @Override
    public final int compareTo(final NameBase o) {
        return name.compareTo(o.name);
    }

    /**
     * Indicates whether some other object is "equal to" this one by comparing their name strings.
     *
     * @param obj the reference object with which to compare
     * 
     * @return {@code true} if names match, {@code false} otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof NameBase nb)
            return name.equals(nb.name);
        return super.equals(obj);
    }

    /**
     * Returns a hash code value based on the entity's name.
     *
     * @return a hash code value for this entity
     */
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Returns a string representation of this entity, which is its forged name.
     *
     * @return the name of the entity
     */
    @Override
    public String toString() {
        return getName();
    }
}
