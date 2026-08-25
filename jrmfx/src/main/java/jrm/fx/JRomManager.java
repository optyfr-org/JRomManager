package jrm.fx;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;


import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

import jrm.aui.status.NeutralRenderer;
import jrm.aui.status.StatusRendererFactory;
import jrm.fx.ui.MainFrame;

import jrm.misc.Log;
import jrm.security.Session;
import jrm.security.Sessions;
import lombok.Getter;

/**
 * Main entry point for the JRomManager JavaFX desktop application.
 * <p>
 * Parses command-line arguments, initializes the logging subsystem and single-user session,
 * acquires an exclusive file lock to prevent concurrent instances, and launches the JavaFX
 * {@link MainFrame}.
 *
 * @since 2.5
 */
public class JRomManager {
    /**
     * The main application frame.
     * @return the main application frame
     */
    private static @Getter MainFrame mainFrame;

    /**
     * Command-line argument holder parsed by JCommander.
     */
    @Parameters(separators = " =")
    private static class Args {
        /** Whether to run in multi-user mode. */
        @Parameter(names = { "--multiuser", "-m" }, description = "Multi-user mode")
        private boolean multiuser = false;
        /** Whether to skip the update check on startup. */
        @Parameter(names = { "--noupdate", "-n" }, description = "Don't search for update")
        private boolean noupdate = false;
        /** Whether to enable debug-level logging. */
        @Parameter(names = { "--debug", "-d" }, description = "Activate debug mode")
        private boolean debug = false;
    }

    /**
     * Application entry point.
     * <p>
     * Sets the file encoding, initializes the session and logging subsystem, acquires the
     * instance lock, and launches the JavaFX {@link MainFrame}.
     *
     * @param args command-line arguments forwarded to {@link Args}
     */
    public static void main(final String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        Sessions.setSingleMode(true);
        StatusRendererFactory.Factory.setInstance(new NeutralRenderer());
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
        if (JRomManager.lockInstance(session, JRomManager.class.getSimpleName() + ".lock")) //$NON-NLS-1$
        {
            // Launch FX Application
            MainFrame.launch();
        }
    }

    /**
     * Write lock file and keep it locked (rw) until program shutdown.
     * 
     * @param session  the current user session providing the work path
     * @param lockFile the lock file name
     * @return true if successful, false otherwise
     */
    private static boolean lockInstance(final Session session, final String lockFile) {
        try {
            final var fc = getLock(session, lockFile);
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
            } else
                fc.close();
        } catch (final Exception e) {
            Log.err("Unable to create and/or lock file: " + lockFile, e); //$NON-NLS-1$
        }
        return false;
    }

    /**
     * Opens or creates the lock file in the session work path and returns a writable channel.
     *
     * @param session  the current user session providing the work path
     * @param lockFile the lock file name
     * @return a {@link FileChannel} for the lock file
     * @throws IOException if the file cannot be created or opened
     */
    private static FileChannel getLock(final Session session, final String lockFile) throws IOException {
        return FileChannel.open(session.getUser().getSettings().getWorkPath().resolve(lockFile), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE,
                StandardOpenOption.DELETE_ON_CLOSE);
    }

}
