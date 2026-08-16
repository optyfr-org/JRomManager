/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.filter;

import jrm.profile.data.PropertyStub;

/**
 * Represents a player count or multiplayer capability mode (e.g., 2 Players, 4 Players, etc.), grouping all compatible retro gaming
 * machines/games matching that mode.
 * <p>
 * This class maps games categorized under specific player numbers/modes as defined in the <code>nplayers.ini</code> configuration
 * file.
 * </p>
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public final class NPlayer extends GamesList implements PropertyStub {
    /**
     * The name of this multiplayer capability or player count mode.
     */
    public final String name;

    /**
     * Constructs a new {@code NPlayer} instance with the specified mode name.
     * 
     * @param name the descriptive name of the player mode
     */
    public NPlayer(final String name) {
        this.name = name;
    }

    /**
     * Returns a string representation of this multiplayer mode, including its name and the total number of games classified under
     * it.
     * 
     * @return a string combining the name and game count of this player mode
     */
    @Override
    public String toString() {
        return name + " (" + games.size() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Resolves the configuration property key associated with this player filter.
     * 
     * @return the fully qualified configuration property key string
     */
    @Override
    public String getPropertyName() {
        return "filter.nplayer." + name; //$NON-NLS-1$
    }
}
