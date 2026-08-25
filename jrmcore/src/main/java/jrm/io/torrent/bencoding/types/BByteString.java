/*
 * Copyright (C) 2015 Christophe De Troyer Copyright (C) 2018 Optyfr This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public
 * License for more details. You should have received a copy of the GNU General Public License along with this program; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.io.torrent.bencoding.types;

import java.util.Arrays;

import jrm.io.torrent.bencoding.Utils;

/**
 * Representation of a bencoded byte string. Consists of an arbitrary sequence of bytes, which may or may not be ASCII printable.
 *
 * @author Christophe De Troyer
 * @author Optyfr
 */
public class BByteString implements IBencodable {
    /**
     * The raw byte array data stored inside this byte string.
     */
    private final byte[] data;

    /**
     * Constructs a BByteString containing the specified raw byte array.
     *
     * @param data the raw byte content
     */
    public BByteString(byte[] data) {
        this.data = data;
    }

    /**
     * Constructs a BByteString by converting a standard Java String into its default byte representation.
     *
     * @param name the string value to convert and store
     */
    public BByteString(String name) {
        this.data = name.getBytes();
    }

    // Getters and setters

    /**
     * Gets the raw byte array stored inside this byte string.
     *
     * @return the raw data bytes
     */
    public byte[] getData() {
        return data;
    }

    // Bencoding

    /**
     * Returns the bencoded string format of this byte string. Format: {@code <length>:<data>} (e.g., "4:spam").
     *
     * @return the standard bencoded string representation
     */
    public String bencodedString() {
        return data.length + ":" + new String(data); //$NON-NLS-1$
    }

    /**
     * Encodes this byte string into the standard bencoded byte array format. Consists of the length string in ASCII, followed by
     * ':' and the raw bytes.
     *
     * @return the bencoded byte array
     */
    public byte[] bencode() {
        final byte[] lengthStringAsBytes = Utils.stringToAsciiBytes(Long.toString(data.length));
        final var bencoded = new byte[lengthStringAsBytes.length + 1 + data.length];

        bencoded[lengthStringAsBytes.length] = ':';
        // Copy the length array in first.
        System.arraycopy(lengthStringAsBytes, 0, bencoded, 0, lengthStringAsBytes.length);
        // Copy in the actual data.
        for (var i = 0; i < data.length; i++)
            bencoded[i + lengthStringAsBytes.length + 1] = data[i];

        return bencoded;
    }

    // Overridden methods

    /**
     * Returns a readable string representation. If the contents are entirely printable ASCII, it is returned directly; otherwise, a
     * placeholder showing the byte count is returned.
     *
     * @return a readable string representation of the data
     */
    @Override
    public String toString() {
        if (Utils.allAscii(data)) {
            return new String(this.data);
        } else {
            return "<non-ascii bytes:" + this.data.length + ">"; //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    /**
     * Compares this BByteString with another object for equality.
     *
     * @param o the other object to compare
     * 
     * @return {@code true} if the other object is a BByteString containing identical bytes, otherwise {@code false}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        BByteString that = (BByteString) o;

        return Arrays.equals(data, that.data);
    }

    /**
     * Returns the hash code value for this BByteString, based on its byte array contents.
     *
     * @return the integer hash code
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }
}
