/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.misc;

import lombok.experimental.UtilityClass;

/**
 * Utility class to determine the current operating system.
 * <p>
 * This class exposes helper methods to identify if the current platform is Windows, macOS, Unix/Linux, or Solaris.
 * </p>
 * 
 * @author optyfr
 */
public @UtilityClass class OSValidator {
    /**
     * The name of the operating system retrieved from system properties and normalized to lower-case.
     */
    private static String os = System.getProperty("os.name").toLowerCase(); //$NON-NLS-1$

    /**
     * Checks if the current operating system is Windows.
     * 
     * @return {@code true} if running on Windows, {@code false} otherwise
     */
    public static boolean isWindows() {
        return OSValidator.os.contains("win"); //$NON-NLS-1$
    }

    /**
     * Checks if the current operating system is macOS.
     * 
     * @return {@code true} if running on macOS, {@code false} otherwise
     */
    public static boolean isMac() {
        return OSValidator.os.contains("mac"); //$NON-NLS-1$
    }

    /**
     * Checks if the current operating system is a Unix-like flavor (such as Linux, AIX, etc.).
     * 
     * @return {@code true} if running on a Unix-like platform, {@code false} otherwise
     */
    public static boolean isUnix() {
        return OSValidator.os.contains("nix") || OSValidator.os.contains("nux") || OSValidator.os.contains("aix"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    /**
     * Checks if the current operating system is Solaris (SunOS).
     * 
     * @return {@code true} if running on Solaris, {@code false} otherwise
     */
    public static boolean isSolaris() {
        return OSValidator.os.contains("sunos"); //$NON-NLS-1$
    }
}
