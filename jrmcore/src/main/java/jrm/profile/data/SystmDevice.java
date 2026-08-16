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
 * Device system. Represents system options mapped under the Type.DEVICE classification.
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public class SystmDevice implements Systm { // NOSONAR
    /**
     * The static DEVICE instance.
     */
    public static final SystmDevice DEVICE = new SystmDevice();

    /**
     * Retrieves the system type.
     * 
     * @return Type.DEVICE
     */
    @Override
    public Type getType() {
        return Type.DEVICE;
    }

    /**
     * Retrieves the system reference.
     * 
     * @return the static DEVICE instance
     */
    @Override
    public Systm getSystem() {
        return SystmDevice.DEVICE;
    }

    /**
     * Formats the system to its string representation.
     * 
     * @return formatted type string
     */
    @Override
    public String toString() {
        return "[" + getType() + "]"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Retrieves the system name.
     * 
     * @return "device"
     */
    @Override
    public String getName() {
        return "device"; //$NON-NLS-1$
    }

    /**
     * Default constructor for SystmDevice. This constructor does not perform any specific initialization and is used to create
     * instances of the SystmDevice class.
     */
    public SystmDevice() {
        /* default constructor */ }

}
