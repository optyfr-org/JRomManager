/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.data;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import jrm.misc.Log;

/**
 * The abstract base class for {@link Entity} and {@link Sample}. Its main purpose is to define parent relationships and scan status
 * tracking.
 *
 * @author optyfr
 */
public abstract class EntityBase extends NameBase {
    /** The serial version UID for serialization compatibility. */
    private static final long serialVersionUID = 1L;

    /**
     * The scan status, defaulting to {@link EntityStatus#UNKNOWN}.
     */
    protected EntityStatus ownStatus = EntityStatus.UNKNOWN;

    /**
     * The parent {@link AnywareBase} reference.
     */
    protected transient AnywareBase parent;

    /**
     * The constructor with its required parent.
     *
     * @param parent the required {@link AnywareBase} parent
     */
    protected EntityBase(AnywareBase parent) {
        this.parent = parent;
    }

    /**
     * Retrieves the entity status.
     *
     * @return an {@link EntityStatus} value
     */
    public abstract EntityStatus getStatus();

    /**
     * Sets the {@link Entity} status.
     *
     * @param status the {@link EntityStatus} to set
     */
    public void setStatus(EntityStatus status) {
        this.ownStatus = status;
    }

    /**
     * Retrieves the parent casted according to the given class.
     *
     * @param type the class to cast, must extend {@link AnywareBase}
     * @param <T> a class which extends {@link AnywareBase}
     * 
     * @return the type-casted parent
     */
    protected <T extends AnywareBase> T getParent(final Class<T> type) {
        return type.cast(parent);
    }

    /**
     * Retrieves the parent object.
     *
     * @return the parent (cannot be null)
     */
    public abstract AnywareBase getParent();

    /**
     * Special method to get the value of a field outside its scope using reflection. <br>
     * Checks public getters first, then falls back to recursive field traversal. <b style='color:red'>*** USE WITH CAUTION ***</b>
     *
     * @param name the property name as a string (case sensitive)
     * 
     * @return the value as an {@link Object}, or {@code null} if the field does not exist or cannot be accessed
     */
    public Object getProperty(String name) {
        if (name == null || name.isEmpty())
            return null;

        // Try public getters first to support virtual properties and clean access
        final String titleName = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            final Method method = this.getClass().getMethod("get" + titleName);
            return method.invoke(this);
        } catch (Exception _) {
            try {
                final Method method = this.getClass().getMethod("is" + titleName);
                return method.invoke(this);
            } catch (Exception _) {
                // Ignore getter failure, fall back to direct field retrieval
            }
        }

        // Traversal of superclasses to find declared fields (public, protected, or
        // private)
        Class<?> current = this.getClass();
        while (current != null && current != Object.class) {
            try {
                final Field field = current.getDeclaredField(name);
                field.setAccessible(true); // NOSONAR
                return field.get(this);
            } catch (NoSuchFieldException _) {
                current = current.getSuperclass();
            } catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
                Log.err(e.getMessage(), e);
                break;
            }
        }
        return null;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     *
     * @param obj the reference object with which to compare
     * 
     * @return {@code true} if this object is equal to the obj argument; {@code false} otherwise
     */
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    /**
     * Returns a hash code value for the entity.
     *
     * @return a hash code value for this entity
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
