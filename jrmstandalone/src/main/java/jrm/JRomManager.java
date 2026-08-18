/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm;

import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;

import org.apache.commons.io.FilenameUtils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

import jrm.aui.status.Html4Renderer;
import jrm.aui.status.StatusRendererFactory;
import jrm.misc.Log;
import jrm.security.Session;
import jrm.security.Sessions;
import jrm.ui.MainFrame;
import lombok.Getter;

/**
 * Main entry point for the JRomManager application.
 * <p>
 * This class initializes the application, parses command-line arguments, sets up logging,
 * creates a file lock to prevent multiple instances, and launches the main GUI window.
 * <p>
 * JRomManager is a retro-gaming ROM management tool that helps users organize, validate,
 * and manage ROM collections for various emulators including MAME and others.
 *
 * @author optyfr
 * @version %I%, %G%
 * @since 1.0
 */
public final class JRomManager {
    /**
     * The main application window instance.
     * @return the main application window
     */
    private static @Getter MainFrame mainFrame;

    /**
     * Command-line argument container for the application.
     * <p>
     * Uses JCommander annotations to parse command-line parameters.
     */
    @Parameters(separators = " =")
    private static class Args {
        /** Whether to enable multi-user mode. */
        @Parameter(names = { "-m", "--multiuser" }, description = "Multi-user mode")
        private boolean multiuser = false;
        /** Whether to skip searching for application updates. */
        @Parameter(names = { "-n", "--noupdate" }, description = "Don't search for update")
        private boolean noupdate = false;
        /** Whether to activate debug mode with verbose logging. */
        @Parameter(names = { "-d", "--debug" }, description = "Activate debug mode")
        private boolean debug = false;
    }

    /**
     * Application entry point.
     * <p>
     * Initializes the application by setting up UTF-8 encoding, configuring single-user mode,
     * parsing command-line arguments, initializing logging, acquiring a file lock to prevent
     * multiple instances, and displaying the main window.
     *
     * @param args command-line arguments; supports {@code -m/--multiuser} for multi-user mode,
     *             {@code -n/--noupdate} to skip update checks, and {@code -d/--debug} for debug mode
     */
    public static void main(final String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        Sessions.setSingleMode(true);
        StatusRendererFactory.Factory.setInstance(new Html4Renderer());
        final var jArgs = new Args();
        final var cmd = JCommander.newBuilder().addObject(jArgs).build();
        try {
            cmd.parse(args);
        } catch (ParameterException e) {
            Log.err(e.getMessage(), e);
            cmd.usage();
            System.exit(1);
        }
        final var session = Sessions.getSession(jArgs.multiuser, jArgs.noupdate);
        Log.init(session.getUser().getSettings().getLogPath() + "/JRM.%g.log", jArgs.debug, 1024 * 1024, 5);
        if (!jArgs.debug)
            Log.setLevel(Level.parse(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.debug_level)));
        if (JRomManager.lockInstance(session, FilenameUtils.removeExtension(JRomManager.class.getSimpleName()) + ".lock")) //$NON-NLS-1$
        {
            // Open main window
            mainFrame = new MainFrame(session);
            mainFrame.setVisible(true);
        }
    }

    /**
     * Acquires an exclusive file lock to prevent multiple application instances.
     * <p>
     * Creates a lock file in the user's work directory and attempts to acquire an exclusive lock.
     * The lock is held for the duration of the application and released via a shutdown hook.
     * The lock file is automatically deleted on close.
     *
     * @param session the current user session containing settings and paths
     * @param lockFile the name of the lock file to create (typically {@code "JRomManager.lock"})
     * @return {@code true} if the lock was successfully acquired, {@code false} if another instance is already running
     */
    private static boolean lockInstance(final Session session, final String lockFile) {
        try {
            final var fc = FileChannel.open(session.getUser().getSettings().getWorkPath().resolve(lockFile), StandardOpenOption.CREATE, StandardOpenOption.READ,
                    StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE);
            final var fl = fc.tryLock();
            if (fl != null) {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        fl.release();
                        fc.close();
                    } catch (final Exception e) {
                        Log.err("Unable to remove lock file: " + lockFile, e); //$NON-NLS-1$
                    }

                }));
                return true;
            }
            fc.close();
        } catch (final Exception e) {
            Log.err("Unable to create and/or lock file: " + lockFile, e); //$NON-NLS-1$
        }
        return false;
    }
}
