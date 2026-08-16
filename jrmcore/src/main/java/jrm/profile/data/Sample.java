/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.data;

/**
 * A Sample entity defined in system machine audio sets. Represents an audio wave file required by certain arcade games for sound
 * emulation.
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public final class Sample extends EntityBase {
    /**
     * The Sample constructor.
     * 
     * @param parent the {@link Samples} parent list container
     * @param name the name of the sample (with or without .wav extension)
     */
    public Sample(AnywareBase parent, String name) {
        super(parent);
        setName(name);
    }

    /**
     * Retrieves the normalized name of the sample. Guarantees a ".wav" file extension suffix.
     * 
     * @return the sample filename with a .wav extension
     */
    @Override
    public String getName() {
        if (!name.endsWith(".wav")) //$NON-NLS-1$
            return name + ".wav"; //$NON-NLS-1$
        return name;
    }

    /**
     * Retrieves the current status of the sample.
     * 
     * @return the entity status
     */
    @Override
    public EntityStatus getStatus() {
        return ownStatus;
    }

    /**
     * Checks equality between this Sample and another object by comparing their string representations.
     * 
     * @param obj the reference object to compare with
     * 
     * @return true if the string representations are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        return this.toString().equals(obj.toString());
    }

    /**
     * Returns the hash code value for the sample.
     * 
     * @return the hash code based on the base entity implementation
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * Retrieves the parent container of the sample.
     * 
     * @return the parent anyware container base
     */
    @Override
    public AnywareBase getParent() {
        return parent;
    }

}
