/*
 * Copyright (C) 2015 Christophe De Troyer Copyright (C) 2018 Optyfr This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public
 * License for more details. You should have received a copy of the GNU General Public License along with this program; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.io.torrent.bencoding.types;


import jrm.io.torrent.bencoding.Utils;

/**
 * Representation of a bencoded integer. Standard format: {@code i<value>e} where value is represented as base-10 ASCII.
 * 
 * @author Christophe De Troyer
 * @author Optyfr
 */
public class BInt implements IBencodable {
    /**
     * The internal backing 64-bit integer value.
     */
    private final Long value;

    /**
     * Constructs a new bencoded integer with the specified value.
     *
     * @param value the 64-bit integer value
     */
    public BInt(Long value) {
        this.value = value;
    }

    // Bencoding

    /**
     * Returns the bencoded string format of this integer. Format: {@code i<value>e}.
     *
     * @return the standard bencoded string representation
     */
    public String bencodedString() {
        return "i" + value + "e"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Encodes this integer into the standard bencoded byte array format. Format starts with 'i', followed by base-10 ASCII
     * representation of value, ending with 'e'.
     *
     * @return the bencoded byte array
     */
    public byte[] bencode() {
        final byte[] sizeInAsciiBytes = Utils.stringToAsciiBytes(value.toString());
        final var bencoded = new byte[sizeInAsciiBytes.length + 2];
        bencoded[0] = (byte) 'i';
        System.arraycopy(sizeInAsciiBytes, 0, bencoded, 1, sizeInAsciiBytes.length);
        bencoded[bencoded.length - 1] = (byte) 'e';
        return bencoded;
    }

    // Getters and setters

    /**
     * Gets the internal 64-bit integer value.
     *
     * @return the integer value
     */
    public Long getValue() {
        return value;
    }

    // Overridden methods

    /**
     * Compares this BInt with another object for equality.
     *
     * @param o the other object to compare
     * 
     * @return {@code true} if the other object is a BInt containing an identical value, otherwise {@code false}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        final var bInt = (BInt) o;

        return value.equals(bInt.value);
    }

    /**
     * Returns the hash code value for this BInt, based on its 64-bit integer value.
     *
     * @return the integer hash code
     */
    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /**
     * Returns a readable string representation of this integer value.
     *
     * @return the string representation of the underlying value
     */
    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
