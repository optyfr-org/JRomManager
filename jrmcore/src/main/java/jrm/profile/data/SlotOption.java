/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.data;

import lombok.Getter;
import lombok.Setter;

/**
 * A SlotOption defines a specific configuration option available within a {@link Slot}. It maps to a specific emulator device name
 * and includes a default setting flag.
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public class SlotOption extends NameBase {
    /**
     * Name of the used device.
     * 
     * @param devName the device name to set
     * 
     * @return the device name
     */
    private @Setter @Getter String devName;

    /**
     * Is this the default slot option.
     * 
     * @param def true if this is the default slot option, false otherwise
     */
    private @Setter boolean def = false;

    /**
     * Retrieves the slot option name.
     * 
     * @return the name of the slot option
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Checks if this slot option is the default one.
     * 
     * @return true if default, false otherwise
     */
    public boolean isDef() {
        return def;
    }

    /**
     * Compares the specified object with this slot option for equality.
     * 
     * @param obj the reference object to compare with
     * 
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    /**
     * Returns the hash code value for this slot option.
     * 
     * @return the hash code value
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * Constructs a new SlotOption with default values.
     */
    public SlotOption() {
        super();
    }
}
