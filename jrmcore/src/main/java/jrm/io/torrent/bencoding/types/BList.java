/*
 * Copyright (C) 2015 Christophe De Troyer Copyright (C) 2018 Optyfr This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public
 * License for more details. You should have received a copy of the GNU General Public License along with this program; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.io.torrent.bencoding.types;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Representation of a bencoded list. Stores a list of {@link IBencodable} objects, preserving order.
 * 
 * @author Christophe De Troyer
 * @author Optyfr
 */
public class BList implements IBencodable {
    /**
     * The internal backing list of bencodable objects.
     */
    private final List<IBencodable> list;

    /**
     * Constructs a new, empty bencoded list.
     */
    public BList() {
        this.list = new LinkedList<>();
    }

    // Logic methods

    /**
     * Returns an iterator over the elements in this list.
     *
     * @return an iterator of {@link IBencodable} elements
     */
    public Iterator<IBencodable> getIterator() {
        return list.iterator();
    }

    /**
     * Adds a bencodable element to the end of this list.
     *
     * @param o the bencodable element to add
     */
    public void add(IBencodable o) {
        this.list.add(o);
    }

    // Bencoding

    /**
     * Returns the bencoded string format of this list. Format: {@code l<elements>e}.
     *
     * @return the standard bencoded string representation
     */
    public String bencodedString() {
        final var sb = new StringBuilder();

        sb.append("l"); //$NON-NLS-1$

        for (final var entry : this.list)
            sb.append(entry.bencodedString());

        sb.append("e"); //$NON-NLS-1$

        return sb.toString();
    }

    /**
     * Encodes this list into the standard bencoded byte array format. Begins with 'l', followed by the concatenated bencoded
     * elements, ending with 'e'.
     *
     * @return the bencoded byte array
     */
    public byte[] bencode() {
        try (final var baos = new ByteArrayOutputStream()) {
            baos.write('l');
            for (final var entry : this.list) {
                final var enc = entry.bencode();
                baos.write(enc, 0, enc.length);
            }
            baos.write('e');
            return baos.toByteArray();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    // Overridden methods

    /**
     * Returns a readable string representation of this list.
     *
     * @return the formatted list string
     */
    @Override
    public String toString() {
        final var sb = new StringBuilder();
        sb.append("("); //$NON-NLS-1$
        for (Object entry : this.list) {
            sb.append(entry.toString());
        }
        sb.append(") "); //$NON-NLS-1$

        return sb.toString();
    }
}
