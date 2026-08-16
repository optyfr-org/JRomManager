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

import lombok.Getter;

/**
 * Describes a machine driver containing status, emulation flags, cocktail mode support, and save state details. This is commonly
 * used for filtering games.
 *
 * @author optyfr
 */
public class Driver implements Serializable {
    /**
     * The global status of the machine driver. Defaults to {@link StatusType#preliminary}.
     *
     * @return the status type
     */
    private @Getter StatusType status = StatusType.preliminary;

    /**
     * The emulation status of the machine driver. Defaults to {@link StatusType#preliminary}.
     *
     * @return the emulation status type
     */
    private @Getter StatusType emulation = StatusType.preliminary;

    /**
     * The cocktail mode status of the machine driver. Defaults to {@link StatusType#preliminary}.
     *
     * @return the cocktail status type
     */
    private @Getter StatusType cocktail = StatusType.preliminary;

    /**
     * The save state support of the machine driver. Defaults to {@link SaveStateType#unsupported}.
     *
     * @return the save state support type
     */
    private @Getter SaveStateType saveState = SaveStateType.unsupported;

    /**
     * Enumerates the various status types of a machine driver.
     */
    public enum StatusType {
        /**
         * Driver status is considered good.
         */
        good, // NOSONAR
        /**
         * Driver status is imperfect (has minor emulation glitches).
         */
        imperfect, // NOSONAR
        /**
         * Driver status is preliminary (not fully working or barely booting).
         */
        preliminary // NOSONAR
    }

    /**
     * Enumerates the save state types of a machine driver.
     */
    public enum SaveStateType {
        /**
         * Save states are supported.
         */
        supported, // NOSONAR
        /**
         * Save states are unsupported.
         */
        unsupported // NOSONAR
    }

    /**
     * Package-private constructor for Driver.
     */
    Driver() {
    }

    /**
     * Sets the global driver status from a string representation. Supports trim and case-insensitive resolution.
     *
     * @param status the status string matching a {@link StatusType} value name
     */
    public void setStatus(final String status) {
        if (status != null) {
            final String cleaned = status.trim().toLowerCase();
            for (StatusType type : StatusType.values()) {
                if (type.name().equalsIgnoreCase(cleaned)) {
                    this.status = type;
                    break;
                }
            }
        }
    }

    /**
     * Sets the emulation status from a string representation. Supports trim and case-insensitive resolution.
     *
     * @param status the emulation status string matching a {@link StatusType} value name
     */
    public void setEmulation(final String status) {
        if (status != null) {
            final String cleaned = status.trim().toLowerCase();
            for (StatusType type : StatusType.values()) {
                if (type.name().equalsIgnoreCase(cleaned)) {
                    emulation = type;
                    break;
                }
            }
        }
    }

    /**
     * Sets the cocktail mode status from a string representation. Supports trim and case-insensitive resolution.
     *
     * @param status the cocktail status string matching a {@link StatusType} value name
     */
    public void setCocktail(final String status) {
        if (status != null) {
            final String cleaned = status.trim().toLowerCase();
            for (StatusType type : StatusType.values()) {
                if (type.name().equalsIgnoreCase(cleaned)) {
                    cocktail = type;
                    break;
                }
            }
        }
    }

    /**
     * Sets the save state support type from a string representation. Supports trim and case-insensitive resolution.
     *
     * @param status the save state support string matching a {@link SaveStateType} value name
     */
    public void setSaveState(final String status) {
        if (status != null) {
            final String cleaned = status.trim().toLowerCase();
            for (SaveStateType type : SaveStateType.values()) {
                if (type.name().equalsIgnoreCase(cleaned)) {
                    saveState = type;
                    break;
                }
            }
        }
    }
}
