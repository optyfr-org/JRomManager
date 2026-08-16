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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import lombok.Getter;

/**
 * ListModel of systems. Holds multiple {@link Systm} elements and supports standard iteration.
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public final class Systms implements Serializable, Iterable<Systm> {
    /**
     * The internal {@link ArrayList} of {@link Systm}s.
     * 
     * @return the list of systems
     */
    private final @Getter List<Systm> systems = new ArrayList<>();

    /**
     * Add a {@link Systm} to the list.
     * 
     * @param system the {@link Systm} to add
     * 
     * @return true if successful, false otherwise
     */
    public boolean add(final Systm system) {
        return systems.add(system);
    }

    /**
     * Returns an iterator over the registered systems.
     * 
     * @return an iterator of {@link Systm}
     */
    @Override
    public Iterator<Systm> iterator() {
        return systems.iterator();
    }

    /**
     * Constructs an empty Systms object.
     */
    public Systms() {
        super();
    }
}
