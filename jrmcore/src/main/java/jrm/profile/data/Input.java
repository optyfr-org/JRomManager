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

import org.apache.commons.lang3.BooleanUtils;

/**
 * Describes the input controls of a machine (such as players, coins, service mode, or tilt) used primarily for ROM listing and
 * driver status filtering.
 *
 * @author optyfr
 */
public final class Input implements Serializable {
    /**
     * Number of supported players.
     */
    protected int players = 0;

    /**
     * Number of coin slots.
     */
    protected int coins = 0;

    /**
     * Indicates whether the machine supports a service mode.
     */
    protected boolean service = false;

    /**
     * Indicates whether the machine supports tilt controls.
     */
    protected boolean tilt = false;

    /**
     * Package-private default constructor for Input.
     */
    Input() {
    }

    /**
     * Sets the number of supported players.
     *
     * @param players the player string value that will be converted to an integer
     */
    public void setPlayers(final String players) {
        this.players = Integer.parseInt(players);
    }

    /**
     * Sets the number of coin slots.
     *
     * @param coins the coins string value that will be converted to an integer
     */
    public void setCoins(final String coins) {
        this.coins = Integer.parseInt(coins);
    }

    /**
     * Sets the service mode support flag.
     *
     * @param service the service boolean yes/no string representation
     */
    public void setService(final String service) {
        this.service = BooleanUtils.toBoolean(service);
    }

    /**
     * Sets the tilt support flag.
     *
     * @param tilt the tilt boolean yes/no string representation
     */
    public void setTilt(final String tilt) {
        this.tilt = BooleanUtils.toBoolean(tilt);
    }
}
