/*
 * Copyright (C) 2015 Christophe De Troyer Copyright (C) 2018 Optyfr This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public
 * License for more details. You should have received a copy of the GNU General Public License along with this program; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.io.torrent.bencoding.types;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Representation of a bencoded dictionary. Maps {@link BByteString} keys to any other {@link IBencodable} values, preserving order.
 * 
 * @author Christophe De Troyer
 * @author Optyfr
 */
public class BDictionary implements IBencodable {
    /**
     * The internal backing map storing the key-value associations. Uses LinkedHashMap to preserve the insertion order of keys.
     */
    private final Map<BByteString, IBencodable> dictionary;

    /**
     * Constructs a new, empty bencoded dictionary.
     */
    public BDictionary() {
        // LinkedHashMap to preserve order.
        this.dictionary = new LinkedHashMap<>();
    }

    // Logic methods

    /**
     * Adds a key-value entry to this dictionary.
     *
     * @param key the byte string key
     * @param value the bencodable value associated with the key
     */
    public void add(BByteString key, IBencodable value) {
        this.dictionary.put(key, value);
    }

    /**
     * Finds and retrieves a value associated with the specified key.
     *
     * @param key the byte string key to look up
     * 
     * @return the associated bencodable object, or {@code null} if not found
     */
    public Object find(BByteString key) {
        return dictionary.get(key);
    }

    // Bencoding

    /**
     * Returns the bencoded string format of this dictionary. Format: {@code d<key1><value1>...<keyN><valueN>e} where all keys are
     * strings.
     *
     * @return the standard bencoded string representation
     */
    @Override
    public String bencodedString() {
        final var sb = new StringBuilder();
        sb.append("d"); //$NON-NLS-1$
        for (Map.Entry<BByteString, IBencodable> entry : this.dictionary.entrySet()) {
            sb.append(entry.getKey().bencodedString());
            sb.append(entry.getValue().bencodedString());
        }
        sb.append("e"); //$NON-NLS-1$
        return sb.toString();
    }

    /**
     * Encodes this dictionary into the standard bencoded byte array format. Begins with 'd', followed by the concatenated bencoded
     * keys and values, ending with 'e'.
     *
     * @return the bencoded byte array
     */
    @Override
    public byte[] bencode() {
        // Get the total size of the keys and values.
        final var bytes = new ArrayList<Byte>();
        bytes.add((byte) 'd');

        for (Map.Entry<BByteString, IBencodable> entry : this.dictionary.entrySet()) {
            final var keyBencoded = entry.getKey().bencode();
            final var valBencoded = entry.getValue().bencode();
            for (byte b : keyBencoded)
                bytes.add(b);
            for (byte b : valBencoded)
                bytes.add(b);
        }
        bytes.add((byte) 'e');
        var bencoded = new byte[bytes.size()];

        for (var i = 0; i < bytes.size(); i++)
            bencoded[i] = bytes.get(i);

        return bencoded;
    }

    // Overridden methods

    /**
     * Returns a readable multi-line string representation of this dictionary's contents.
     *
     * @return the formatted string
     */
    @Override
    public String toString() {
        final var sb = new StringBuilder();
        sb.append("\n[\n"); //$NON-NLS-1$
        for (Map.Entry<BByteString, IBencodable> entry : this.dictionary.entrySet()) {
            sb.append(entry.getKey()).append(" :: ").append(entry.getValue()).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        sb.append("]"); //$NON-NLS-1$

        return sb.toString();
    }
}
