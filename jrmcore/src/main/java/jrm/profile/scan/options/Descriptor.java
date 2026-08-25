/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.scan.options;

/**
 * An interface representing a describable component or option within the ROM scanning and management system. Implementations of
 * this interface provide a localized or user-friendly text description of their purpose.
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public interface Descriptor {
    /**
     * Retrieves the user-friendly description of this component or option.
     * 
     * @return a {@link String} containing the text description of this component.
     */
    String getDesc();
}
