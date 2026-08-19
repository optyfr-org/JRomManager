/*
 * Copyright (C) 2015 Christophe De Troyer Copyright (C) 2018 Optyfr This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public
 * License for more details. You should have received a copy of the GNU General Public License along with this program; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.io.torrent.bencoding;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

import jrm.misc.Log;
import lombok.experimental.UtilityClass;

/**
 * Utility library providing byte manipulation, hexadecimal encoding, file reading, and cryptographic hashing operations for torrent
 * metadata processing.
 *
 * @author Christophe De Troyer
 * @author Optyfr
 */
public @UtilityClass class Utils {
    /**
     * Hexadecimal lookup array containing digits from 0-9 and uppercase A-F.
     */
    protected static final char[] hexArray = "0123456789ABCDEF".toCharArray(); //$NON-NLS-1$

    /**
     * Takes an array of bytes and converts them to an uppercase hexadecimal string.
     *
     * @param bytes the byte array to encode
     * 
     * @return the encoded hexadecimal string
     */
    public static String bytesToHex(byte[] bytes) {
        final var hexChars = new char[bytes.length * 2];

        for (var j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4]; // Get left part of byte
            hexChars[j * 2 + 1] = hexArray[v & 0x0F]; // Get right part of byte
        }
        return new String(hexChars);
    }

    /**
     * Reads and returns the byte at the specified index position from a file on disk.
     *
     * @param path the path of the file to read from
     * @param nth the 0-based offset of the byte to read
     * 
     * @return the read byte, or {@code 0} if an I/O error occurred
     */
    public static byte readNthByteFromFile(String path, long nth) {
        try (final var rf = new RandomAccessFile(path, "r")) {
            if (rf.length() <= nth)
                throw new EOFException("Reading outside of bounds of file"); //$NON-NLS-1$

            rf.seek(nth);
            return rf.readByte();
        } catch (IOException e) {
            Log.err(e.getMessage(), e);
        }
        return 0;
    }

    /**
     * Checks whether the bit at the specified 0-based position is set to 1 in the given byte.
     *
     * @param b the source byte to check
     * @param position the 0-based position starting from the least significant bit
     * 
     * @return {@code true} if the bit is set (1), otherwise {@code false}
     */
    public static boolean isBitSet(byte b, int position) {
        return ((b >> position) & 1) == 1;
    }

    /**
     * Prints a formatted binary representation of a byte to the standard output.
     *
     * @param b the byte to print
     */
    public static void printByte(byte b) {
        String s1 = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0'); //$NON-NLS-1$
        System.out.println(s1);// NOSONAR // 10000001
    }

    /**
     * Checks whether all bytes in the given array represent printable ASCII characters (i.e. MSB is 0).
     *
     * @param data the byte array to check
     * 
     * @return {@code true} if all bytes are valid printable ASCII characters, otherwise {@code false}
     */
    public static boolean allAscii(byte[] data) {
        for (byte b : data)
            if (isBitSet(b, 7))
                return false;
        return true;
    }

    /**
     * Calculates the cryptographic SHA-1 hash for the given input byte array.
     *
     * @param input the raw byte array to hash
     * 
     * @return the lowercase hexadecimal SHA-1 digest, or {@code null} if the hash calculation failed
     */
    public static String sumSHA(byte[] input) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1"); //$NON-NLS-1$
            return byteArray2Hex(md.digest(input));
        } catch (NoSuchAlgorithmException e) {
            Log.err(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Formats a byte array into a lowercase hexadecimal string.
     *
     * @param bytes the raw bytes to convert
     * 
     * @return the lowercase hexadecimal string representation
     */
    private static String byteArray2Hex(final byte[] bytes) {
        try (final var formatter = new Formatter();) {
            for (byte b : bytes) {
                formatter.format("%02x", b); //$NON-NLS-1$
            }
            return formatter.toString();
        }
    }

    /**
     * Converts a standard String into its equivalent ASCII byte array.
     *
     * @param s the string to convert
     * 
     * @return a byte array containing ASCII representations of the string characters
     */
    public static byte[] stringToAsciiBytes(String s) {
        final var ascii = new byte[s.length()];
        for (var charIdx = 0; charIdx < s.length(); charIdx++) {
            ascii[charIdx] = (byte) s.charAt(charIdx);
        }
        return ascii;
    }
}
