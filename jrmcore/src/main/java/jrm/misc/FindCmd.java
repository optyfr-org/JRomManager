/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.misc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;

/**
 * Utility class used to locate executable commands in the system path.
 * <p>
 * This class uses standard platform-specific search tools: {@code where.exe} on Windows and {@code which} on Unix-like operating
 * systems.
 * </p>
 * 
 * @author optyfr
 */
public @UtilityClass class FindCmd {
    /**
     * Searches the system path for the specified command, considering only fixed, unwriteable
     * directories to prevent executable planting attacks. If the command is not found, the
     * specified default value is returned.
     *
     * @param command the command name to locate (without path or extension)
     * @param defaultValue the default fallback value to return if the command cannot be located
     *
     * @return the full absolute path to the command if successful, or {@code defaultValue} if not found
     */
    public static String findCmd(final String command, final String defaultValue) {
        final var path = System.getenv("PATH"); //$NON-NLS-1$
        if (path == null || path.isEmpty())
            return defaultValue;

        final var isWindows = OSValidator.isWindows();
        final var separator = isWindows ? ";" : ":"; //$NON-NLS-1$ //$NON-NLS-2$

        final String[] exts = getExts(isWindows, separator);

        for (final var dir : path.split(Pattern.quote(separator))) {
            if (dir.isEmpty())
                continue; // skip empty entries (would mean current dir on Unix)
            final var result = searchInDir(Path.of(dir), command, exts);
            if (result != null)
                return result;
        }
        return defaultValue;
    }

    /**
     * Searches for the specified command within a single directory, considering only fixed,
     * unwriteable directories to prevent executable planting attacks.
     *
     * @param dirPath the directory to search in
     * @param cmd the command name to locate
     * @param exts the file extensions to probe
     *
     * @return the full absolute path to the command if found, or {@code null} otherwise
     */
    private static String searchInDir(final Path dirPath, final String cmd, final String[] exts) {
        // Only search fixed, unwriteable directories — skip writable ones
        // to prevent executable planting attacks
        if (Files.isDirectory(dirPath) && !Files.isWritable(dirPath)) {
            for (final var ext : exts) {
                final var cmdFile = dirPath.resolve(cmd + ext).toFile();
                if (cmdFile.isFile() && cmdFile.canExecute())
                    return cmdFile.getAbsolutePath();
            }
        }
        return null;
    }

    private static String[] getExts(final boolean isWindows, final String separator) {
        // Determine which file extensions to probe
        final String[] exts;
        if (isWindows) {
            final var pathext = System.getenv("PATHEXT"); //$NON-NLS-1$
            exts = pathext != null && !pathext.isEmpty()
                ? pathext.split(Pattern.quote(separator))
                : new String[] {".EXE", ".BAT", ".CMD", ".COM"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        } else {
            exts = new String[] {""}; //$NON-NLS-1$
        }
        return exts;
    }

    /**
     * Searches the system path for the specified command. If not found, the original command name is returned.
     *
     * @param cmd the command name to locate (without path or extension)
     * 
     * @return the full absolute path to the command if successful, or {@code cmd} if not found
     */
    public static String findCmd(final String cmd) {
        return findCmd(cmd, cmd);
    }

    /**
     * Locates the 7z (7-Zip) command utility used for ZIP compression.
     *
     * @return the full absolute path to {@code 7z} if successful, otherwise {@code "7z"}
     */
    public static String findZip() {
        return findCmd("7z"); //$NON-NLS-1$
    }

    /**
     * Locates the {@code torrentzip} or {@code trrntzip} command utility.
     *
     * @return the full absolute path to {@code trrntzip} if successful, otherwise {@code "trrntzip"}
     */
    public static String findTZip() {
        return findCmd("trrntzip"); //$NON-NLS-1$
    }

    /**
     * Locates the standard 7-Zip executable command utility.
     *
     * @return the full absolute path to {@code 7z} if successful, otherwise {@code "7z"}
     */
    public static String find7z() {
        return findCmd("7z"); //$NON-NLS-1$
    }

    /**
     * Locates the MAME executable command utility by scanning known names in order of preference: {@code mame}, {@code mame64},
     * {@code xmame}, {@code advmame}.
     *
     * @return the full absolute path to the located MAME executable, or {@code null} if none are found
     */
    public static String findMame() {
        for (String cmd : new String[] { "mame", "mame64", "xmame", "advmame" })
            if ((cmd = findCmd(cmd, null)) != null)
                return cmd;
        return null;
    }
}
