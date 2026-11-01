/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm;

/**
 * Alternative entry point that launches JRomManager in multi-user mode with update checks disabled.
 * <p>
 * This class simply delegates to {@link JRomManager#main(String[])} with the
 * {@code --multiuser} and {@code --noupdate} flags pre-set, providing a convenient
 * shortcut for running the application in a multi-user environment.
 *
 * @author optyfr
 * @version %I%, %G%
 * @since 1.0
 */
public final class JRomManagerMulti {
    /**
     * Launches JRomManager in multi-user mode without searching for updates.
     *
     * @param args command-line arguments (ignored; multi-user and no-update flags are always applied)
     */
    public static void main(final String[] args) { // NOSONAR - JVM entry-point signature is fixed; args intentionally ignored
        JRomManager.main(new String[] { "--multiuser", "--noupdate" });
    }
}
