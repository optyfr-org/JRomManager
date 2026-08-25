/*
 * Copyright (C) 2015 Christophe De Troyer Copyright (C) 2018 Optyfr This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public
 * License for more details. You should have received a copy of the GNU General Public License along with this program; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.io.torrent.bencoding;

import jrm.io.torrent.TorrentException;
import jrm.io.torrent.bencoding.types.BByteString;
import jrm.io.torrent.bencoding.types.BDictionary;
import jrm.io.torrent.bencoding.types.BInt;
import jrm.io.torrent.bencoding.types.BList;
import jrm.io.torrent.bencoding.types.IBencodable;

/**
 * Parser utility that decodes a raw bencoded byte array stream into structured, strongly-typed {@link IBencodable} objects.
 * 
 * @author Christophe De Troyer
 * @author Optyfr
 */
public class Reader {
    /**
     * Maximum container nesting depth for lists and dictionaries.
     */
    static final int MAX_NESTING_DEPTH = 100;

    /**
     * The raw byte buffer being parsed.
     */
    private final byte[] datablob;

    /**
     * The current reading pointer/index in the byte array.
     */
    private int currentByteIndex;

    /**
     * Constructs a new bencoded reader for the specified byte array.
     *
     * @param datablob the raw bencoded byte array to parse
     */
    public Reader(byte[] datablob) {
        this.datablob = datablob;
        this.currentByteIndex = 0;
    }

    // Logic methods

    /**
     * Reads and decodes the root bencoded object from the byte buffer.
     *
     * @return the decoded {@link IBencodable} object (dictionary, list, string, or integer)
     * 
     * @throws TorrentException if the bencoded structure is corrupt or incomplete
     */
    public IBencodable read() throws TorrentException {
        return readSingleType(0);
    }

    /**
     * Identifies and delegates decoding to the appropriate type-specific parser based on the prefix byte at the current index.
     *
     * @param depth current container nesting depth
     * @return the decoded {@link IBencodable} object
     * 
     * @throws TorrentException if an unsupported prefix byte is encountered or nesting exceeds {@link #MAX_NESTING_DEPTH}
     */
    private IBencodable readSingleType(final int depth) throws TorrentException {
        if (depth >= MAX_NESTING_DEPTH)
            throw new TorrentException("Bencoding nesting exceeds maximum depth of " + MAX_NESTING_DEPTH); //$NON-NLS-1$
        final var currentByte = readCurrentByte();

        switch (currentByte) {
            case 'i':
                return readInteger();
            case 'l':
                return readList(depth);
            case 'd':
                return readDictionary(depth);
            default:
                // Assume a byte string
                if (currentByte >= 48 && currentByte <= 57)
                    return readString();
                throw new TorrentException("Unrecognized Bencoding type. Starting with " + Character.toString((char) currentByte)); //$NON-NLS-1$
        }
    }

    /**
     * Parses a bencoded byte string from the current index. Format: {@code <length>:<data>} (e.g., {@code 4:spam})
     *
     * @return a {@link BByteString} representing the parsed byte string
     * 
     * @throws TorrentException if the length is invalid or the data does not match the length
     */
    private BByteString readString() throws TorrentException {
        var stringLengthAsAscii = new StringBuilder(); // $NON-NLS-1$
        var current = readCurrentByte();

        // Read the length first
        while (current >= 48 && current <= 57) {
            stringLengthAsAscii.append(Character.toString((char) current));
            currentByteIndex++;
            current = readCurrentByte();
        }

        if (readCurrentByte() != ':')
            throw new TorrentException("Error parsing string. Was expecting ':' but got " + Character.toString((char) readCurrentByte())); //$NON-NLS-1$
        currentByteIndex++; // Skip the ':'

        int dataLength = Integer.parseInt(stringLengthAsAscii.toString());
        final var data = new byte[dataLength];

        // Read actual data bytes
        for (var i = 0; i < dataLength; i++) {
            data[i] = readCurrentByte();
            currentByteIndex++;
        }

        return new BByteString(data);
    }

    /**
     * Parses a bencoded list from the current index. Format: {@code l<elements>e}
     *
     * @param depth current container nesting depth
     * @return a {@link BList} containing the decoded elements
     * @throws TorrentException if the list does not end with 'e' or nesting exceeds {@link #MAX_NESTING_DEPTH}
     */
    private BList readList(final int depth) throws TorrentException {
        // If we got here, the current byte is an 'l'.
        if (readCurrentByte() != 'l')
            throw new TorrentException("Error parsing list. Was expecting 'l' but got " + Character.toString((char) readCurrentByte())); //$NON-NLS-1$
        currentByteIndex++; // Skip the 'l'

        final var list = new BList();
        while (readCurrentByte() != 'e')
            list.add(readSingleType(depth + 1));
        currentByteIndex++; // Skip the 'e'

        return list;
    }

    /**
     * Parses a bencoded dictionary from the current index. Format: {@code d<key><value>e} where keys must be byte strings.
     *
     * @param depth current container nesting depth
     * @return a {@link BDictionary} representing the key-value map
     * @throws TorrentException if keys are not strings, the dictionary does not end with 'e', or nesting exceeds {@link #MAX_NESTING_DEPTH}
     */
    private BDictionary readDictionary(final int depth) throws TorrentException {
        // If we got here, the current byte is a 'd'.
        if (readCurrentByte() != 'd')
            throw new TorrentException("Error parsing dictionary. Was expecting 'd' but got " + Character.toString((char) readCurrentByte())); //$NON-NLS-1$
        currentByteIndex++; // Skip the 'd'

        final var dict = new BDictionary();
        while (readCurrentByte() != 'e') {
            // Each dictionary *must* map BByteStrings to any other value.
            BByteString key = (BByteString) readSingleType(depth + 1);
            IBencodable value = readSingleType(depth + 1);

            dict.add(key, value);
        }
        currentByteIndex++; // Skip the 'e'

        return dict;
    }

    /**
     * Parses an integer in Bencode format. Example: 123 == i123e. An integer is encoded as i&lt;integer encoded in base ten
     * ASCII&gt;e. Leading zeros are not allowed. Negative values are prefixed with a minus sign. Negative zero is not permitted.
     *
     * @return BInt representing the value of the parsed integer.
     * 
     * @throws TorrentException if the integer formatting is invalid or does not end with 'e'
     */
    private BInt readInteger() throws TorrentException {
        // If we got here, the current byte is an 'i'.
        if (readCurrentByte() != 'i')
            throw new TorrentException("Error parsing integer. Was expecting an 'i' but got " + readCurrentByte()); //$NON-NLS-1$
        currentByteIndex++;// Skip the 'i'.

        // Read in the integer number by number.
        // They are represented as ASCII numbers.
        var intString = new StringBuilder(); // $NON-NLS-1$
        var current = readCurrentByte();
        // 45 negative mark
        while (current >= 48 && current <= 57 || current == 45) {
            intString.append(Character.toString((char) current));
            currentByteIndex++;
            current = readCurrentByte();
        }

        if (readCurrentByte() != 'e')
            throw new TorrentException("Error parsing integer. Was expecting 'e' at end but got " + readCurrentByte()); //$NON-NLS-1$

        currentByteIndex++; // Skip past 'e'
        return new BInt(Long.parseLong(intString.toString()));
    }

    // Helpers

    /**
     * Returns the byte in the current position of the file.
     *
     * @return byte
     */
    private byte readCurrentByte() {
        return datablob[currentByteIndex];
    }
}
