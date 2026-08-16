/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.scan;

/**
 * Exception thrown when a critical error occurs during a profile or directory scan operation, such as missing path inputs, invalid
 * directories, or misconfigured scanning properties.
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public class ScanException extends Exception {
    /**
     * Constructs a new {@code ScanException} with the specified detail message.
     * 
     * @param message the detail message explaining the reason for the exception.
     */
    public ScanException(String message) {
        super(message);
    }
}
