/*
 * Copyright (C) 2024 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.security;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.spec.SecretKeySpec;

import jrm.misc.Log;

/**
 * Provides a per-user HMAC key for {@link SignedObjectStore}.
 * <p>
 * The key is 32 cryptographically random bytes, generated once per workspace and stored under
 * {@code settings/.cache-hmac}. It is not derived from the work path, so knowing the workspace
 * location is not enough to forge cache signatures.
 * </p>
 * <p>
 * The key is persisted so large profile caches survive process restarts. The file is owner-only
 * when the filesystem supports it, and {@link CachePathGuard} blocks HTTP upload/download/extract
 * of this path.
 * </p>
 */
public final class CacheIntegrityKey {

    static final String FILENAME = ".cache-hmac";
    static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int KEY_LENGTH = 32;
    private static final String SETTINGS_DIR = "settings";

    private static final Map<String, SecretKeySpec> KEYS = new ConcurrentHashMap<>();

    private CacheIntegrityKey() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns the HMAC key for {@code session}'s workspace, generating and persisting it if needed.
     *
     * @param session the active user session
     * @return a 256-bit HMAC-SHA256 key
     */
    public static SecretKeySpec get(final Session session) {
        final Path keyFile = keyFile(session);
        return KEYS.computeIfAbsent(keyFile.toAbsolutePath().normalize().toString(), _ -> loadOrCreate(keyFile));
    }

    /**
     * Resolves the on-disk key path for {@code session}.
     *
     * @param session the active user session
     * @return the key file path
     */
    public static Path keyFile(final Session session) {
        return session.getUser().getSettings().getWorkPath().resolve(SETTINGS_DIR).resolve(FILENAME);
    }

    static void clearCache() {
        KEYS.clear();
    }

    private static SecretKeySpec loadOrCreate(final Path keyFile) {
        try {
            Files.createDirectories(keyFile.getParent());
            if (!Files.exists(keyFile)) {
                createKeyFile(keyFile);
            }
            restrictPermissions(keyFile);
            final byte[] key = Files.readAllBytes(keyFile);
            if (key.length != KEY_LENGTH) {
                throw new IllegalStateException("Invalid cache integrity key length: " + key.length);
            }
            return new SecretKeySpec(key, HMAC_ALGORITHM);
        } catch (final IOException e) {
            throw new IllegalStateException("Cannot load cache integrity key", e);
        }
    }

    private static void createKeyFile(final Path keyFile) throws IOException {
        final byte[] key = new byte[KEY_LENGTH];
        newSecureRandom().nextBytes(key);
        try (final var channel = FileChannel.open(keyFile, EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            channel.write(ByteBuffer.wrap(key));
        } catch (final FileAlreadyExistsException _) {
            // Another thread or process won the create race; the caller will read that file.
        }
    }

    private static SecureRandom newSecureRandom() {
        try {
            return SecureRandom.getInstanceStrong();
        } catch (final Exception e) {
            Log.warn(() -> "SecureRandom.getInstanceStrong() unavailable, using default: " + e.getMessage());
            return new SecureRandom();
        }
    }

    private static void restrictPermissions(final Path keyFile) {
        try {
            Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("rw-------"));
        } catch (final UnsupportedOperationException | IOException _) {
            // Windows has no POSIX owner-only bits; leave default ACLs so the process can still
            // delete the file (TempDir cleanup) while CachePathGuard blocks HTTP access.
        }
    }
}
