/*
 * Copyright (C) 2024 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.security;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Serializes and deserializes application objects with an optional HMAC integrity envelope.
 * <p>
 * New writes always produce a signed payload:
 * </p>
 * <pre>
 * magic "JRMH" | version (1 byte) | hmacLen (4 BE) | hmac | serialized Java object stream
 * </pre>
 * <p>
 * Loads accept, in order:
 * </p>
 * <ol>
 * <li>Signed {@code JRMH} envelopes (HMAC verified with the session work-path key)</li>
 * <li>Legacy DirScan length-prefixed HMAC envelopes ({@code hmacLen | hmac | data})</li>
 * <li>Legacy bare Java object streams (start with {@code 0xACED}), filtered only</li>
 * </ol>
 * <p>
 * Legacy unsigned files remain readable so existing caches, reports, and profile metadata keep working;
 * the next save upgrades them to the signed format.
 * </p>
 */
public final class SignedObjectStore {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] MAGIC = { 'J', 'R', 'M', 'H' };
    private static final byte VERSION = 1;
    /** Java serialization stream header magic ({@code STREAM_MAGIC}). */
    private static final int JAVA_STREAM_MAGIC = 0xACED;

    private SignedObjectStore() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Serializes {@code object} and writes it to {@code file} under a session-bound HMAC envelope.
     *
     * @param session the active user session (key material is derived from the work path)
     * @param file destination file
     * @param object object to serialize
     * @throws IOException if writing fails
     */
    public static void write(final Session session, final File file, final Object object) throws IOException {
        final var serialized = serialize(object);
        final var hmac = computeHmac(session, serialized);
        try (final var fos = new FileOutputStream(file);
                final var bos = new BufferedOutputStream(fos)) {
            bos.write(MAGIC);
            bos.write(VERSION);
            writeInt(bos, hmac.length);
            bos.write(hmac);
            bos.write(serialized);
        }
    }

    /**
     * Reads and deserializes an object from {@code file}, verifying HMAC when present.
     *
     * @param session the active user session
     * @param file source file
     * @return the deserialized object
     * @throws IOException if reading or verification fails
     * @throws ClassNotFoundException if a class cannot be resolved
     */
    public static Object read(final Session session, final File file) throws IOException, ClassNotFoundException {
        return read(session, file, DeserializationFilter.Mode.DEFAULT, -1);
    }

    /**
     * Reads and deserializes an object from {@code file} with a custom graph-depth limit.
     *
     * @param session the active user session
     * @param file source file
     * @param maxDepth maximum object graph depth, or {@code -1} for the default
     * @return the deserialized object
     * @throws IOException if reading or verification fails
     * @throws ClassNotFoundException if a class cannot be resolved
     */
    public static Object read(final Session session, final File file, final int maxDepth) throws IOException, ClassNotFoundException {
        return read(session, file, DeserializationFilter.Mode.DEFAULT, maxDepth);
    }

    /**
     * Reads and deserializes an object from {@code file} with a custom allowlist mode and depth limit.
     *
     * @param session the active user session
     * @param file source file
     * @param mode deserialization allowlist mode
     * @param maxDepth maximum object graph depth, or {@code -1} for the default
     * @return the deserialized object
     * @throws IOException if reading or verification fails
     * @throws ClassNotFoundException if a class cannot be resolved
     */
    public static Object read(final Session session, final File file, final DeserializationFilter.Mode mode, final int maxDepth)
            throws IOException, ClassNotFoundException {
        return readBytes(session, Files.readAllBytes(file.toPath()), mode, maxDepth);
    }

    /**
     * Reads all bytes from {@code in} (up to {@code length} when positive) and deserializes.
     *
     * @param session the active user session
     * @param in source stream
     * @param length expected length, or {@code -1} to read until EOF
     * @param maxDepth maximum object graph depth, or {@code -1} for the default
     * @return the deserialized object
     * @throws IOException if reading or verification fails
     * @throws ClassNotFoundException if a class cannot be resolved
     */
    public static Object read(final Session session, final InputStream in, final int length, final int maxDepth)
            throws IOException, ClassNotFoundException {
        return read(session, in, length, DeserializationFilter.Mode.DEFAULT, maxDepth);
    }

    /**
     * Reads all bytes from {@code in} with a custom allowlist mode.
     *
     * @param session the active user session
     * @param in source stream
     * @param length expected length, or {@code -1} to read until EOF
     * @param mode deserialization allowlist mode
     * @param maxDepth maximum object graph depth, or {@code -1} for the default
     * @return the deserialized object
     * @throws IOException if reading or verification fails
     * @throws ClassNotFoundException if a class cannot be resolved
     */
    public static Object read(final Session session, final InputStream in, final int length, final DeserializationFilter.Mode mode,
            final int maxDepth) throws IOException, ClassNotFoundException {
        final byte[] bytes;
        if (length > 0) {
            bytes = in.readNBytes(length);
        } else {
            bytes = in.readAllBytes();
        }
        return readBytes(session, bytes, mode, maxDepth);
    }

    /**
     * Deserializes from an in-memory payload, supporting signed and legacy layouts.
     *
     * @param session the active user session
     * @param fileBytes full file contents
     * @param mode deserialization allowlist mode
     * @param maxDepth maximum object graph depth, or {@code -1} for the default
     * @return the deserialized object
     * @throws IOException if reading or verification fails
     * @throws ClassNotFoundException if a class cannot be resolved
     */
    public static Object readBytes(final Session session, final byte[] fileBytes, final DeserializationFilter.Mode mode,
            final int maxDepth) throws IOException, ClassNotFoundException {
        if (fileBytes == null || fileBytes.length < 2) {
            throw new IOException("Serialized payload too short");
        }
        if (hasMagic(fileBytes)) {
            return readSignedV1(session, fileBytes, mode, maxDepth);
        }
        if (isJavaStream(fileBytes)) {
            return deserialize(fileBytes, mode, maxDepth);
        }
        // Legacy DirScan envelope: hmacLen (4 BE) | hmac | data
        return readLegacyLengthPrefixedHmac(session, fileBytes, mode, maxDepth);
    }

    private static Object readSignedV1(final Session session, final byte[] fileBytes, final DeserializationFilter.Mode mode,
            final int maxDepth) throws IOException, ClassNotFoundException {
        if (fileBytes.length < MAGIC.length + 1 + 4) {
            throw new IOException("Signed payload header too short");
        }
        final int version = fileBytes[MAGIC.length] & 0xFF;
        if (version != VERSION) {
            throw new IOException("Unsupported signed payload version: " + version);
        }
        final int hmacLength = readInt(fileBytes, MAGIC.length + 1);
        final int dataOffset = MAGIC.length + 1 + 4 + hmacLength;
        if (hmacLength <= 0 || dataOffset > fileBytes.length) {
            throw new IOException("Invalid signed payload header");
        }
        final var expectedHmac = Arrays.copyOfRange(fileBytes, MAGIC.length + 1 + 4, dataOffset);
        final var serialized = Arrays.copyOfRange(fileBytes, dataOffset, fileBytes.length);
        verifyHmac(session, serialized, expectedHmac);
        return deserialize(serialized, mode, maxDepth);
    }

    private static Object readLegacyLengthPrefixedHmac(final Session session, final byte[] fileBytes,
            final DeserializationFilter.Mode mode, final int maxDepth) throws IOException, ClassNotFoundException {
        if (fileBytes.length < 4) {
            throw new IOException("Legacy HMAC payload too short");
        }
        final int hmacLength = readInt(fileBytes, 0);
        final int dataOffset = 4 + hmacLength;
        if (hmacLength <= 0 || hmacLength > 1024 || dataOffset > fileBytes.length) {
            throw new IOException("Invalid legacy HMAC payload header");
        }
        final var expectedHmac = Arrays.copyOfRange(fileBytes, 4, dataOffset);
        final var serialized = Arrays.copyOfRange(fileBytes, dataOffset, fileBytes.length);
        verifyHmac(session, serialized, expectedHmac);
        return deserialize(serialized, mode, maxDepth);
    }

    private static Object deserialize(final byte[] serialized, final DeserializationFilter.Mode mode, final int maxDepth)
            throws IOException, ClassNotFoundException {
        final var effectiveMode = mode == null ? DeserializationFilter.Mode.DEFAULT : mode;
        final int depth = maxDepth > 0 ? maxDepth : 100;
        try (final var ois = DeserializationFilter.openObjectInputStream(new ByteArrayInputStream(serialized), effectiveMode, depth)) {
            return ois.readObject();
        }
    }

    private static byte[] serialize(final Object object) throws IOException {
        final var baos = new ByteArrayOutputStream();
        try (final var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);
        }
        return baos.toByteArray();
    }

    private static void verifyHmac(final Session session, final byte[] data, final byte[] expectedHmac) throws IOException {
        final var actualHmac = computeHmac(session, data);
        if (!MessageDigest.isEqual(expectedHmac, actualHmac)) {
            throw new SecurityException("Serialized object integrity check failed");
        }
    }

    private static byte[] computeHmac(final Session session, final byte[] data) {
        try {
            final var mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(getHmacKey(session));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    private static SecretKeySpec getHmacKey(final Session session) {
        final var workPath = session.getUser().getSettings().getWorkPath().toString();
        final var keyMaterial = ("JRM-CACHE-INTEGRITY-" + workPath).getBytes(StandardCharsets.UTF_8);
        try {
            final var digest = MessageDigest.getInstance("SHA-256");
            return new SecretKeySpec(digest.digest(keyMaterial), HMAC_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static boolean hasMagic(final byte[] bytes) {
        if (bytes.length < MAGIC.length) {
            return false;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (bytes[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isJavaStream(final byte[] bytes) {
        return bytes.length >= 2 && ((bytes[0] & 0xFF) << 8 | (bytes[1] & 0xFF)) == JAVA_STREAM_MAGIC;
    }

    private static int readInt(final byte[] bytes, final int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static void writeInt(final BufferedOutputStream out, final int value) throws IOException {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
