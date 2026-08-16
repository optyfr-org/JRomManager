/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui;

import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.HashMap;
import java.util.Optional;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.SerializationUtils;

import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.security.Session;
import jrm.ui.basic.Popup;
import jrm.ui.batch.BatchPanel;
import jrm.ui.profile.ProfileViewer;
import jrm.ui.profile.report.ReportFrame;
import lombok.Getter;
import lombok.Setter;

/**
 * The main application window for JRomManager.
 * <p>
 * Hosts the tabbed interface containing the Profiles, Scanner, Dir2Dat, Batch Tools,
 * and Settings panels. Manages the global look-and-feel, window bounds persistence,
 * icon caching, and shutdown hooks for saving user settings.
 */
public class MainFrame extends JFrame implements Popup {

    /**
     * The global profile viewer instance.
     * @param profileViewer the profile viewer to set
     * @return the profile viewer
     */
    private static @Getter @Setter ProfileViewer profileViewer = null;

    /**
     * The global report frame instance.
     * @param reportFrame the report frame to set
     * @return the report frame
     */
    private static @Getter @Setter ReportFrame reportFrame = null;

    /**
     * The main tabbed pane holding all top-level panels.
     * @return the main tabbed pane
     */
    private @Getter JTabbedPane mainPane;

    /** The profile management panel. */
    private ProfilePanel profilesPanel;

    /** The user session associated with this frame. */
    private transient Session session;

    /**
     * Constructs the main application frame.
     * <p>
     * Sets up the look-and-feel, creates the working directory structure,
     * builds all tab panels, restores saved window bounds, and registers
     * a shutdown hook to persist settings.
     *
     * @param session the user session providing access to settings and profile data
     */
    public MainFrame(Session session) {
        super();
        this.session = session;
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                session.getUser().getSettings().setProperty("MainFrame.Bounds", Hex.encodeHexString(SerializationUtils.serialize(getBounds()))); //$NON-NLS-1$
            }
        });
        try {
            UIManager.setLookAndFeel(
                    session.getUser().getSettings().getProperty("LookAndFeel", UIManager.getSystemLookAndFeelClassName()/* UIManager.getCrossPlatformLookAndFeelClassName() */)); //$NON-NLS-1$
            final File workdir = session.getUser().getSettings().getWorkPath().toFile(); // $NON-NLS-1$
            final File xmldir = new File(workdir, "xmlfiles"); //$NON-NLS-1$
            xmldir.mkdir();
            // ResourceBundle.getBundle("jrm.resources.Messages"); //$NON-NLS-1$
        } catch (final Exception e) {
            JOptionPane.showMessageDialog(null, e, "Exception", JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
            Log.err(e.getMessage(), e);
        }
        build();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (session.getCurrProfile() != null)
                session.getCurrProfile().saveSettings();
            session.getUser().getSettings().saveSettings();
        }));
    }

    /**
     * Gets the version.
     *
     * @return the version
     */
    private String getVersion() {
        String version = ""; //$NON-NLS-1$
        final Package pkg = this.getClass().getPackage();
        if (pkg.getSpecificationVersion() != null) {
            version += pkg.getSpecificationVersion(); // $NON-NLS-1$
            if (pkg.getImplementationVersion() != null)
                version += "." + pkg.getImplementationVersion(); //$NON-NLS-1$
        }
        return version;
    }

    /**
     * Initialize Main GUI.
     */
    private void build() {
        setIconImage(getIcon("/jrm/resicons/rom.png").getImage()); //$NON-NLS-1$
        setTitle(Messages.getString("MainFrame.Title") + " " + getVersion()); //$NON-NLS-1$ $NON-NLS-2$
        setBounds(50, 50, 1007, 601);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(new BorderLayout(0, 0));

        mainPane = new JTabbedPane(SwingConstants.TOP);
        getContentPane().add(mainPane);

        MainFrame.setReportFrame(new ReportFrame(session, MainFrame.this));

        buildProfileTab();

        buildScannerTab();

        buildDir2DatTab();

        buildBatchToolsTab();

        buildSettingsTab();

        pack();

        try {
            setBounds(SerializationUtils.deserialize(Hex.decodeHex(
                    session.getUser().getSettings().getProperty("MainFrame.Bounds", Hex.encodeHexString(SerializationUtils.serialize(new Rectangle(50, 50, 720, 300))))))); //$NON-NLS-1$
        } catch (final DecoderException e1) {
            Log.err(e1.getMessage(), e1);
        }

    }

    /** Builds and adds the Profiles tab to the main pane. */
    private void buildProfileTab() {
        profilesPanel = new ProfilePanel(session);
        mainPane.addTab(Messages.getString("MainFrame.Profiles"), MainFrame.getIcon("/jrm/resicons/icons/script.png"), profilesPanel, null); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Builds and adds the Scanner tab to the main pane.
     * <p>
     * The scanner tab is initially disabled until a profile is loaded.
     */
    private void buildScannerTab() {
        ScannerPanel scannerPanel = new ScannerPanel(session);
        profilesPanel.setProfileLoader(scannerPanel);
        scannerPanel.setMainPane(mainPane);
        mainPane.addTab(Messages.getString("MainFrame.Scanner"), MainFrame.getIcon("/jrm/resicons/icons/drive_magnify.png"), scannerPanel, null); //$NON-NLS-1$ //$NON-NLS-2$
        mainPane.setEnabledAt(1, false);
    }

    /** Builds and adds the Dir2Dat tab to the main pane. */
    private void buildDir2DatTab() {
        Dir2DatPanel dir2datPanel = new Dir2DatPanel(session);
        mainPane.addTab(Messages.getString("MainFrame.Dir2Dat"), MainFrame.getIcon("/jrm/resicons/icons/drive_go.png"), dir2datPanel, null); //$NON-NLS-1$ //$NON-NLS-2$

    }

    /** Builds and adds the Batch Tools tab to the main pane. */
    private void buildBatchToolsTab() {
        BatchPanel batchToolsPanel = new BatchPanel(session);
        mainPane.addTab(Messages.getString("MainFrame.BatchTools"), MainFrame.getIcon("/jrm/resicons/icons/application_osx_terminal.png"), batchToolsPanel, null); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** Builds and adds the Settings tab to the main pane. */
    private void buildSettingsTab() {
        final SettingsPanel settingsPanel = new SettingsPanel(session);
        mainPane.addTab(Messages.getString("MainFrame.Settings"), MainFrame.getIcon("/jrm/resicons/icons/cog.png"), settingsPanel, null); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** Cache of loaded {@link ImageIcon} instances keyed by resource path. */
    private static HashMap<String, ImageIcon> iconsCache = new HashMap<>();
    /** The module providing icon resources, if available on the module layer. */
    private static Optional<Module> iconsModule = ModuleLayer.boot().findModule("res.icons");

    /**
     * Returns a cached {@link ImageIcon} for the given resource path.
     * <p>
     * Attempts to load from the {@code res.icons} module first, falling back to
     * the classpath. The result is cached for subsequent calls.
     *
     * @param res the resource path (e.g. {@code "/jrm/resicons/icons/cog.png"})
     * @return the icon, or an empty {@link ImageIcon} if the resource cannot be found
     */
    public static ImageIcon getIcon(String res) {
        if (!iconsCache.containsKey(res)) {
            iconsModule.ifPresentOrElse(module -> {
                try (final var in = module.getResourceAsStream(res)) {
                    iconsCache.put(res, in != null ? new ImageIcon(in.readAllBytes()) : new ImageIcon());
                } catch (Exception e) {
                    Log.err(e.getMessage(), e);
                    iconsCache.put(res, new ImageIcon());
                }
            }, () -> {
                try (final var in = MainFrame.class.getResourceAsStream(res)) {
                    iconsCache.put(res, in != null ? new ImageIcon(in.readAllBytes()) : new ImageIcon());
                } catch (Exception e) {
                    Log.err(e.getMessage(), e);
                    iconsCache.put(res, new ImageIcon());
                }
            });
        }
        return iconsCache.get(res);
    }

}
