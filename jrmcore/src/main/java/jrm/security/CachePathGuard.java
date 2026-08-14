/*
 * Copyright (C) 2024 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.security;

import java.nio.file.Path;

/**
 * Identifies workspace paths consumed by Java deserialization loaders or that hold the cache HMAC key.
 * Used by HTTP upload/download and file-chooser write paths so those primitives cannot poison or steal
 * signed cache/report payloads.
 */
public final class CachePathGuard {

    private static final String[] PROTECTED_WORK_SUBDIRS = { "reports", "cache", "work", "settings" };

    private CachePathGuard() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * @param filename a single path component
     * @return {@code true} if the name is a persisted cache/report/key file
     */
    public static boolean isProtectedFilename(final String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        if (CacheIntegrityKey.FILENAME.equalsIgnoreCase(filename)) {
            return true;
        }
        final var lower = filename.toLowerCase();
        return lower.endsWith(".cache") || lower.endsWith(".nfo") || lower.endsWith(".results");
    }

    /**
     * @param session the active user session
     * @param resolved an absolute or relative path already pointing at the intended file or parent
     * @return {@code true} if {@code resolved} is under a protected work subdirectory
     */
    public static boolean isProtectedLocation(final Session session, final Path resolved) {
        if (session == null || resolved == null) {
            return true;
        }
        try {
            final var normalized = resolved.toAbsolutePath().normalize();
            final var workPath = session.getUser().getSettings().getWorkPath().toAbsolutePath().normalize();
            for (final String subdir : PROTECTED_WORK_SUBDIRS) {
                if (isUnderWorkSubdir(normalized, workPath, subdir)) {
                    return true;
                }
            }
            return false;
        } catch (final Exception _) {
            return true;
        }
    }

    /**
     * @param session the active user session
     * @param parent the destination parent directory
     * @param filename the destination file name, or {@code null} to check {@code parent} only
     * @return {@code true} if the upload/write target is a protected cache location
     */
    public static boolean isProtectedTarget(final Session session, final Path parent, final String filename) {
        if (isProtectedFilename(filename)) {
            return true;
        }
        if (parent == null) {
            return true;
        }
        try {
            var resolved = parent.toAbsolutePath().normalize();
            if (filename != null && !filename.isBlank()) {
                resolved = resolved.resolve(filename).normalize();
            }
            return isProtectedLocation(session, resolved);
        } catch (final Exception _) {
            return true;
        }
    }

    /**
     * @param session the active user session
     * @param file the fully resolved file or directory
     * @return {@code true} if the path must not be served or overwritten by user file APIs
     */
    public static boolean isProtectedFile(final Session session, final Path file) {
        if (file == null) {
            return true;
        }
        final var name = file.getFileName() != null ? file.getFileName().toString() : null;
        return isProtectedFilename(name) || isProtectedLocation(session, file);
    }

    private static boolean isUnderWorkSubdir(final Path resolved, final Path workPath, final String subdir) {
        final var root = workPath.resolve(subdir).toAbsolutePath().normalize();
        return resolved.startsWith(root);
    }
}
