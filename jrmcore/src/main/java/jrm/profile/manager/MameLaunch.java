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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jrm.misc.ProfileSettingsEnum;
import jrm.profile.Profile;
import jrm.profile.data.Anyware;
import jrm.profile.data.Machine;
import jrm.profile.data.Software;
import jrm.security.PathAbstractor;
import jrm.security.Session;
import lombok.experimental.UtilityClass;

/**
 * Builds ProcessBuilder argv for a MAME/MESS launch after validating names and ROM paths.
 * ProcessBuilder already avoids a shell; this rejects flag-like DAT names and dest dirs
 * that would be parsed as extra MAME options or extra {@code -rompath} entries.
 */
@UtilityClass
public class MameLaunch {

    private static final Pattern SHORT_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.+-]{0,63}");
    private static final int MAX_PATH_LENGTH = 4096;

    /**
     * @param profile current profile
     * @param software {@code true} to include software dest dirs when enabled
     * @return sanitized absolute ROM/disk dest dirs
     */
    public static List<String> romPaths(final Profile profile, final boolean software) {
        final var raw = new ArrayList<String>();
        raw.add(profile.getProperty(ProfileSettingsEnum.roms_dest_dir));
        if (software && Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.swroms_dest_dir_enabled, Boolean.class))) {
            raw.add(profile.getProperty(ProfileSettingsEnum.swroms_dest_dir));
        }
        if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.disks_dest_dir_enabled, Boolean.class))) {
            raw.add(profile.getProperty(ProfileSettingsEnum.disks_dest_dir));
        }
        if (software && Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.swdisks_dest_dir_enabled, Boolean.class))) {
            raw.add(profile.getProperty(ProfileSettingsEnum.swdisks_dest_dir));
        }
        return sanitizeRomPaths(profile.getSession(), raw);
    }

    /**
     * @param session session used to resolve abstract dest dirs; may be {@code null}
     * @param rawPaths configured dest dir strings
     * @return sanitized absolute paths, omitting blank or unsafe entries
     */
    public static List<String> sanitizeRomPaths(final Session session, final List<String> rawPaths) {
        final var sanitized = new ArrayList<String>();
        if (rawPaths == null) {
            return sanitized;
        }
        for (final var raw : rawPaths) {
            final var path = sanitizeRomPath(session, raw);
            if (path != null) {
                sanitized.add(path);
            }
        }
        return sanitized;
    }

    /**
     * @param executable launchable MAME/MESS binary
     * @param machineName DAT machine short name
     * @param homePath MAME home directory
     * @param romPaths sanitized dest dirs
     * @return argv for {@link ProcessBuilder}
     */
    public static List<String> machine(final File executable, final String machineName, final String homePath, final List<String> romPaths) {
        final var args = new ArrayList<String>();
        args.add(requireExecutable(executable).getAbsolutePath());
        args.add(requireShortName(machineName, "machine name"));
        args.add("-homepath");
        args.add(requireHomePath(homePath));
        args.add("-rompath");
        args.add(joinRomPaths(romPaths));
        return args;
    }

    /**
     * @param executable launchable MAME/MESS binary
     * @param machineName selected compatible machine
     * @param deviceInstance device instance name without a leading dash, or {@code null}/blank to omit
     * @param softwareName DAT software short name
     * @param homePath MAME home directory
     * @param romPaths sanitized dest dirs
     * @return argv for {@link ProcessBuilder}
     */
    public static List<String> software(final File executable, final String machineName, final String deviceInstance,
            final String softwareName, final String homePath, final List<String> romPaths) {
        final var args = new ArrayList<String>();
        args.add(requireExecutable(executable).getAbsolutePath());
        args.add(requireShortName(machineName, "machine name"));
        if (deviceInstance != null && !deviceInstance.isBlank()) {
            args.add("-" + requireShortName(deviceInstance, "device name"));
        }
        args.add(requireShortName(softwareName, "software name"));
        args.add("-homepath");
        args.add(requireHomePath(homePath));
        args.add("-rompath");
        args.add(joinRomPaths(romPaths));
        return args;
    }

    /**
     * @param ware software being launched
     * @param machine selected compatible machine
     * @return matching device instance name, or {@code null} when none
     */
    public static String deviceInstance(final Anyware ware, final Machine machine) {
        if (!(ware instanceof Software software) || machine == null || software.getParts().isEmpty()) {
            return null;
        }
        final var expected = software.getParts().get(0).getIntrface();
        for (final var dev : machine.getDevices()) {
            if (Objects.equals(expected, dev.getIntrface()) && dev.getInstance() != null) {
                return dev.getInstance().getName();
            }
        }
        return null;
    }

    /**
     * @param name candidate MAME short name
     * @param what label used in the error message
     * @return {@code name} when it is a safe MAME identifier
     */
    public static String requireShortName(final String name, final String what) {
        if (name == null || name.isBlank() || name.charAt(0) == '-' || !SHORT_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid " + what + " for MAME launch");
        }
        return name;
    }

    static String sanitizeRomPath(final Session session, final String raw) {
        if (raw == null || raw.isBlank() || raw.startsWith("-") || !isSafePathText(raw)) {
            return null;
        }
        try {
            final Path path = session != null ? PathAbstractor.getAbsolutePath(session, raw) : Paths.get(raw);
            final String resolved = path.toAbsolutePath().normalize().toString();
            if (!isSafePathText(resolved) || resolved.startsWith("-")) {
                return null;
            }
            return resolved;
        } catch (SecurityException | InvalidPathException _) {
            return null;
        }
    }

    private static File requireExecutable(final File executable) {
        if (!MameExecutable.isLaunchable(executable)) {
            throw new IllegalArgumentException("MAME executable does not exist or is not a native executable");
        }
        return executable;
    }

    private static String requireHomePath(final String homePath) {
        if (homePath == null || homePath.isBlank() || !isSafePathText(homePath) || homePath.startsWith("-")) {
            throw new IllegalArgumentException("Invalid MAME home path");
        }
        return homePath;
    }

    private static String joinRomPaths(final List<String> romPaths) {
        if (romPaths == null || romPaths.isEmpty()) {
            return "";
        }
        return romPaths.stream().collect(Collectors.joining(";"));
    }

    private static boolean isSafePathText(final String value) {
        if (value.length() > MAX_PATH_LENGTH) {
            return false;
        }
        for (var i = 0; i < value.length(); i++) {
            final var c = value.charAt(i);
            if (c == ';' || c == '"' || c < 0x20) {
                return false;
            }
        }
        return true;
    }
}
