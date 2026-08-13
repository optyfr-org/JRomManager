/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.manager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;

import jrm.misc.Log;
import lombok.experimental.UtilityClass;

/**
 * Structural checks for files that may be launched as a MAME/MESS executable.
 * Basename must contain {@code mame} or {@code mess} so MESS and SDLMAME remain valid
 * while unrelated binaries are not started.
 */
@UtilityClass
public class MameExecutable {

    private static final Set<String> REJECTED_EXTENSIONS = Set.of(
            "bat", "cmd", "com", "ps1", "psm1", "vbs", "vbe", "js", "jse", "wsf", "wsh", "wsc",
            "msc", "jar", "py", "pl", "rb", "php", "sh", "bash", "zsh", "csh", "ksh", "fish",
            "hta", "scr", "pif", "lnk", "class", "msi");

    /**
     * @param executableFile candidate MAME/MESS binary
     * @return {@code true} if the file exists, is a regular native executable, and is not a script
     */
    public static boolean isLaunchable(final File executableFile) {
        if (executableFile == null) {
            return false;
        }
        try {
            final var canonicalFile = executableFile.getCanonicalFile();
            if (!canonicalFile.exists() || !canonicalFile.isFile() || !canonicalFile.canExecute()) {
                return false;
            }
            final var name = canonicalFile.getName();
            final var ext = FilenameUtils.getExtension(name).toLowerCase(Locale.ROOT);
            if (REJECTED_EXTENSIONS.contains(ext) || !hasSupportedEmulatorName(name)) {
                return false;
            }
            return hasNativeBinaryMagic(canonicalFile);
        } catch (IOException e) {
            Log.err("Failed to validate MAME executable path", e);
            return false;
        }
    }

    /**
     * @param header captured start of process output
     * @param softwareList {@code true} when {@code -listsoftware} was requested
     * @return {@code true} if the header looks like MAME/MESS listxml or software-list XML
     */
    public static boolean isMameListOutput(final CharSequence header, final boolean softwareList) {
        if (header == null || header.isEmpty()) {
            return false;
        }
        final var lower = header.toString().toLowerCase(Locale.ROOT);
        if (!lower.contains("<?xml")) {
            return false;
        }
        if (softwareList) {
            return lower.contains("<softwarelists") || lower.contains("<softwarelist")
                    || lower.contains("doctype softwarelist");
        }
        return lower.contains("<mame") || lower.contains("<mess")
                || lower.contains("doctype mame") || lower.contains("doctype mess");
    }

    static boolean hasSupportedEmulatorName(final String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        final var base = FilenameUtils.getBaseName(name).toLowerCase(Locale.ROOT);
        return base.contains("mame") || base.contains("mess");
    }

    private static boolean hasNativeBinaryMagic(final File file) throws IOException {
        final var magic = new byte[4];
        try (InputStream in = Files.newInputStream(file.toPath())) {
            final var read = in.read(magic);
            if (read < 2) {
                return false;
            }
            if (magic[0] == '#' && magic[1] == '!') {
                return false;
            }
            if (magic[0] == 'M' && magic[1] == 'Z') {
                return true;
            }
            if (read >= 4 && magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F') {
                return true;
            }
            if (read >= 4 && isMachO(magic)) {
                return true;
            }
            return false;
        }
    }

    private static boolean isMachO(final byte[] magic) {
        return matches(magic, 0xFE, 0xED, 0xFA, 0xCE)
                || matches(magic, 0xFE, 0xED, 0xFA, 0xCF)
                || matches(magic, 0xCE, 0xFA, 0xED, 0xFE)
                || matches(magic, 0xCF, 0xFA, 0xED, 0xFE)
                || matches(magic, 0xCA, 0xFE, 0xBA, 0xBE)
                || matches(magic, 0xCA, 0xFE, 0xBA, 0xBF);
    }

    private static boolean matches(final byte[] magic, final int b0, final int b1, final int b2, final int b3) {
        return (magic[0] & 0xFF) == b0 && (magic[1] & 0xFF) == b1 && (magic[2] & 0xFF) == b2 && (magic[3] & 0xFF) == b3;
    }
}
