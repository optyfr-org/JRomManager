/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.data;

import java.util.ArrayList;

import lombok.Getter;
import lombok.Setter;

/**
 * A Slot representation which holds multiple {@link SlotOption} options, typically referencing {@link Device} entities for software
 * loading and configuration. Extends {@link ArrayList} of {@link SlotOption}.
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public class Slot extends ArrayList<SlotOption> {
    /**
     * The name of the Slot.
     * 
     * @param name the slot name to set
     * 
     * @return the slot name
     */
    private @Setter @Getter String name;

    /**
     * Compares the specified object with this slot for equality.
     * 
     * @param o the reference object to compare with
     * 
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    /**
     * Returns the hash code value for this slot.
     * 
     * @return the hash code value
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /** Constructs a new Slot with no options and an empty name. */
    public Slot() {
        super();
    }
}
