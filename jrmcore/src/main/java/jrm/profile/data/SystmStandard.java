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
 * Standard system. Represents system options mapped under the Type.STANDARD classification.
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public class SystmStandard implements Systm {
    /**
     * The static STANDARD instance.
     */
    public static final SystmStandard STANDARD = new SystmStandard();

    /**
     * Retrieves the system type.
     * 
     * @return Type.STANDARD
     */
    @Override
    public Type getType() {
        return Type.STANDARD;
    }

    /**
     * Retrieves the system reference.
     * 
     * @return standard static instance
     */
    @Override
    public Systm getSystem() {
        return SystmStandard.STANDARD;
    }

    /**
     * Formats standard system into a descriptive string.
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
     * @return "standard"
     */
    @Override
    public String getName() {
        return "standard"; //$NON-NLS-1$
    }

    /**
     * Default constructor for SystmStandard. This constructor does not perform any specific initialization and is used to create
     * instances of the SystmStandard class.
     */
    public SystmStandard() {
        /* default constructor */ }
}
