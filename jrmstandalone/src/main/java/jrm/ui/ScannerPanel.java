package jrm.ui;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import jrm.locale.Messages;
import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.misc.ProfileSettingsEnum;
import jrm.profile.Profile;
import jrm.profile.fix.Fix;
import jrm.profile.manager.ProfileNFO;
import jrm.profile.scan.Scan;
import jrm.profile.scan.ScanException;
import jrm.profile.scan.options.ScanAutomation;
import jrm.security.Session;
import jrm.ui.basic.JRMFileChooser;
import jrm.ui.profile.ProfileViewer;
import jrm.ui.profile.data.SourcesModel;
import jrm.ui.profile.data.SystmsModel;
import jrm.ui.progress.SwingWorkerProgress;

/**
 * Panel for scanning ROM sets and comparing them against a loaded profile.
 * <p>
 * Provides controls for scanning directories, applying filters, fixing issues,
 * and managing scan presets. Integrates with multiple sub-panels for configuration,
 * directory selection, filtering, and automation options.
 */
public class ScannerPanel extends JPanel implements ProfileLoader {
    /** Error message constant. */
    private static final String ERROR_STR = "Error";

    /** The tabbed pane containing scanner configuration panels. */
    private JTabbedPane scannerTabbedPane;

    /** The scanner settings configuration panel. */
    private ScannerSettingsPanel scannerSettingsPanel;

    /** The scanner directory selection panel. */
    private ScannerDirPanel scannerDirPanel;

    /** The scanner filters panel. */
    private ScannerFiltersPanel scannerFilters;

    /** The scanner advanced filters panel. */
    private ScannerAdvFilterPanel scannerAdvFilters;

    /** The scanner automation options panel. */
    private ScannerAutomationPanel scannerAutomation;

    /** The button to fix detected issues. */
    private JButton btnFix;

    /** The button to initiate a scan. */
    private JButton btnScan;

    /** The label displaying profile information. */
    private JLabel lblProfileinfo;

    /** The main tabbed pane from the parent frame. */
    private JTabbedPane mainPane;
    /** Vertical separator. */
    private JSeparator separator2;
    /** Button to load a scan preset. */
    private JButton btnLoadPreset;
    /** Button to save a scan preset. */
    private JButton btnSavePreset;
    /** Vertical separator. */
    private JSeparator separator1;

    /**
     * Constructs the Scanner panel.
     * <p>
     * Initializes all sub-panels (settings, directories, filters, automation),
     * sets up action listeners for scan and fix operations, and loads saved settings.
     *
     * @param session the current user session providing access to settings and profile data
     */
    public ScannerPanel(final Session session) {
        final GridBagLayout gblScannerTab = new GridBagLayout();
        gblScannerTab.columnWidths = new int[] { 104, 0 };
        gblScannerTab.rowHeights = new int[] { 0, 0, 24, 0 };
        gblScannerTab.columnWeights = new double[] { 1.0, Double.MIN_VALUE };
        gblScannerTab.rowWeights = new double[] { 0.0, 1.0, 0.0, Double.MIN_VALUE };
        this.setLayout(gblScannerTab);

        JPanel scannerBtnPanel = new JPanel();
        final GridBagConstraints gbcScannerBtnPanel = new GridBagConstraints();
        gbcScannerBtnPanel.insets = new Insets(0, 0, 5, 0);
        gbcScannerBtnPanel.fill = GridBagConstraints.BOTH;
        gbcScannerBtnPanel.gridx = 0;
        gbcScannerBtnPanel.gridy = 0;
        this.add(scannerBtnPanel, gbcScannerBtnPanel);

        JButton btnInfo = new JButton(Messages.getString("MainFrame.btnInfo.text")); //$NON-NLS-1$
        btnInfo.addActionListener(_ -> {
            if (MainFrame.getProfileViewer() == null)
                MainFrame.setProfileViewer(new ProfileViewer(session, SwingUtilities.getWindowAncestor(this), session.getCurrProfile()));
            MainFrame.getProfileViewer().setVisible(true);
        });
        btnInfo.setIcon(MainFrame.getIcon("/jrm/resicons/icons/information.png")); //$NON-NLS-1$
        scannerBtnPanel.add(btnInfo);

        separator1 = new JSeparator();
        separator1.setOrientation(SwingConstants.VERTICAL);
        separator1.setSize(new Dimension(2, 20));
        separator1.setPreferredSize(new Dimension(2, 20));
        scannerBtnPanel.add(separator1);

        btnScan = new JButton(Messages.getString("MainFrame.btnScan.text")); //$NON-NLS-1$
        btnScan.setIcon(MainFrame.getIcon("/jrm/resicons/icons/magnifier.png")); //$NON-NLS-1$
        scannerBtnPanel.add(btnScan);
        btnScan.setEnabled(false);

        JButton btnReport = new JButton(Messages.getString("MainFrame.btnReport.text")); //$NON-NLS-1$
        btnReport.setIcon(MainFrame.getIcon("/jrm/resicons/icons/report.png")); //$NON-NLS-1$
        btnReport.addActionListener(_ -> EventQueue.invokeLater(() -> MainFrame.getReportFrame().setVisible(true)));
        scannerBtnPanel.add(btnReport);

        btnFix = new JButton(Messages.getString("MainFrame.btnFix.text")); //$NON-NLS-1$
        btnFix.setIcon(MainFrame.getIcon("/jrm/resicons/icons/tick.png")); //$NON-NLS-1$
        scannerBtnPanel.add(btnFix);
        btnFix.addActionListener(_ -> fix(session));
        btnFix.setEnabled(false);

        separator2 = new JSeparator();
        separator2.setOrientation(SwingConstants.VERTICAL);
        separator2.setSize(new Dimension(2, 20));
        separator2.setPreferredSize(new Dimension(2, 20));
        scannerBtnPanel.add(separator2);

        btnLoadPreset = new JButton("Import Settings");
        btnLoadPreset.setIcon(MainFrame.getIcon("/jrm/resicons/icons/table_refresh.png")); //$NON-NLS-1$
        btnLoadPreset.addActionListener(_ -> {
            final List<FileFilter> filters = Arrays.asList(new FileNameExtensionFilter("Properties", "properties"));
            final Path presets = session.getUser().getSettings().getWorkPath().resolve("presets");
            try {
                Files.createDirectories(presets);
                new JRMFileChooser<Void>(JFileChooser.OPEN_DIALOG, JFileChooser.FILES_ONLY, presets.toFile(), null, filters, "Import Settings", true)
                        .show(SwingUtilities.getWindowAncestor(ScannerPanel.this), chooser -> {
                            session.getCurrProfile().loadSettings(chooser.getSelectedFile());
                            session.getCurrProfile().loadCatVer(null);
                            session.getCurrProfile().loadNPlayers(null);
                            initProfileSettings(session);
                            return null;
                        });
            } catch (IOException e1) {
                Log.err(e1.getMessage(), e1);
            }
        });
        scannerBtnPanel.add(btnLoadPreset);

        btnSavePreset = new JButton("Export Settings");
        btnSavePreset.setIcon(MainFrame.getIcon("/jrm/resicons/icons/table_save.png")); //$NON-NLS-1$
        btnSavePreset.addActionListener(_ -> {
            final List<FileFilter> filters = Arrays.asList(new FileNameExtensionFilter("Properties", "properties"));
            final Path presets = session.getUser().getSettings().getWorkPath().resolve("presets");
            try {
                Files.createDirectories(presets);
                new JRMFileChooser<Void>(JFileChooser.SAVE_DIALOG, JFileChooser.FILES_ONLY, presets.toFile(), null, filters, "Export Settings", true)
                        .show(SwingUtilities.getWindowAncestor(ScannerPanel.this), chooser -> {
                            session.getCurrProfile().saveSettings(chooser.getSelectedFile());
                            return null;
                        });
            } catch (IOException e1) {
                Log.err(e1.getMessage(), e1);
            }

        });
        scannerBtnPanel.add(btnSavePreset);
        btnScan.addActionListener(_ -> scan(session, true));

        scannerTabbedPane = new JTabbedPane(SwingConstants.TOP);
        final GridBagConstraints gbcScannerTabbedPane = new GridBagConstraints();
        gbcScannerTabbedPane.fill = GridBagConstraints.BOTH;
        gbcScannerTabbedPane.gridx = 0;
        gbcScannerTabbedPane.gridy = 1;
        this.add(scannerTabbedPane, gbcScannerTabbedPane);

        buildScannerDirTab(session);
        buildScannerSettingsTab(session);
        buildScannerFiltersTab(session);
        buildScannerAdvFiltersTab(session);
        buildScannerAutomationTab(session);

        lblProfileinfo = new JLabel(""); //$NON-NLS-1$
        lblProfileinfo.setBorder(new BevelBorder(BevelBorder.LOWERED, null, null, null, null));
        final GridBagConstraints gbcLblProfileinfo = new GridBagConstraints();
        gbcLblProfileinfo.insets = new Insets(0, 2, 0, 2);
        gbcLblProfileinfo.fill = GridBagConstraints.BOTH;
        gbcLblProfileinfo.gridx = 0;
        gbcLblProfileinfo.gridy = 2;
        this.add(lblProfileinfo, gbcLblProfileinfo);

    }

    private void buildScannerDirTab(final Session session) {
        scannerDirPanel = new ScannerDirPanel(session);
        scannerTabbedPane.addTab(Messages.getString("MainFrame.scannerDirectories.title"), MainFrame.getIcon("/jrm/resicons/icons/folder.png"), scannerDirPanel, null); //$NON-NLS-1$
    }

    private void buildScannerSettingsTab(final Session session) // NOSONAR
    {
        scannerSettingsPanel = new ScannerSettingsPanel();
        scannerTabbedPane.addTab(Messages.getString("MainFrame.scannerSettingsPanel.title"), MainFrame.getIcon("/jrm/resicons/icons/cog.png"), scannerSettingsPanel, null); //$NON-NLS-1$

    }

    private void buildScannerFiltersTab(final Session session) {
        scannerFilters = new ScannerFiltersPanel(session);
        scannerTabbedPane.addTab(Messages.getString("MainFrame.Filters"), MainFrame.getIcon("/jrm/resicons/icons/arrow_join.png"), scannerFilters, null); //$NON-NLS-1$

    }

    private void buildScannerAdvFiltersTab(final Session session) {
        scannerAdvFilters = new ScannerAdvFilterPanel(session);
        scannerTabbedPane.addTab(Messages.getString("MainFrame.AdvFilters"), MainFrame.getIcon("/jrm/resicons/icons/arrow_in.png"), scannerAdvFilters, null); //$NON-NLS-1$

    }

    private void buildScannerAutomationTab(final Session session) // NOSONAR
    {
        scannerAutomation = new ScannerAutomationPanel();
        scannerTabbedPane.addTab(Messages.getString("MainFrame.Automation"), MainFrame.getIcon("/jrm/resicons/icons/link.png"), scannerAutomation, null); //$NON-NLS-1$

    }

    /**
     * Scan.
     */
    private void scan(final Session session, final boolean automate) {
        String txtdstdir = scannerDirPanel.txtRomsDest.getText();
        if (txtdstdir.isEmpty()) {
            scannerDirPanel.btnRomsDest.doClick();
            txtdstdir = scannerDirPanel.txtRomsDest.getText();
        }
        if (txtdstdir.isEmpty())
            return;

        new SwingWorkerProgress<Void, Void>(SwingUtilities.getWindowAncestor(this)) {

            @Override
            protected Void doInBackground() throws Exception {
                session.setCurrScan(new Scan(session.getCurrProfile(), this));
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    btnFix.setEnabled(session.getCurrScan() != null && session.getCurrScan().actions.stream().mapToInt(Collection::size).sum() > 0);
                    close();
                    /* update entries in profile viewer */
                    if (MainFrame.getProfileViewer() != null)
                        MainFrame.getProfileViewer().reload();
                    ScanAutomation automation = ScanAutomation.valueOf(session.getCurrProfile().getSettings().getProperty(ProfileSettingsEnum.automation_scan));
                    updateReportFrame(automation);
                    if (automate && btnFix.isEnabled() && automation.hasFix()) {
                        fix(session);
                    }
                } catch (InterruptedException e) {
                    Log.err(e.getMessage(), e);
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    handleScanError(e);
                }
            }
        }.execute();
    }

    private void updateReportFrame(ScanAutomation automation) {
        if (MainFrame.getReportFrame() != null) {
            if (automation.hasReport())
                MainFrame.getReportFrame().setVisible(true);
            MainFrame.getReportFrame().setNeedUpdate(true);
        }
    }

    private void handleScanError(Exception e) {
        Optional.ofNullable(e.getCause()).ifPresentOrElse(cause -> {
            Log.err(cause.getMessage(), cause);
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(ScannerPanel.this), cause.getMessage(), ERROR_STR, JOptionPane.ERROR_MESSAGE);
        }, () -> {
            Log.err(e.getMessage(), e);
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(ScannerPanel.this), e.getMessage(), ERROR_STR, JOptionPane.ERROR_MESSAGE);
        });
    }

    /**
     * Fix.
     */
    private void fix(final Session session) {
        new SwingWorkerProgress<Void, Void>(SwingUtilities.getWindowAncestor(this)) {
            private boolean toFix = false;

            @Override
            protected Void doInBackground() throws Exception {
                if (session.getCurrProfile().hasPropsChanged() && handlePropsChanged(session))
                    return null;
                final Fix fix = new Fix(session.getCurrProfile(), session.getCurrScan(), this);
                toFix = fix.getActionsRemain() > 0;
                return null;
            }

            /**
             * Ask the user how to handle changed profile properties, optionally re-scanning before fixing.
             *
             * @param session the current session
             * @return {@code true} if the fix should be aborted, {@code false} otherwise
             * @throws ScanException 
             * @throws BreakException 
             */
            private boolean handlePropsChanged(final Session session) throws BreakException, ScanException {
                final int choice = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(ScannerPanel.this), Messages.getString("MainFrame.WarnSettingsChanged"), //$NON-NLS-1$
                        Messages.getString("MainFrame.RescanBeforeFix"), JOptionPane.YES_NO_CANCEL_OPTION); //$NON-NLS-1$
                if (choice == JOptionPane.NO_OPTION)
                    return false;
                if (choice == JOptionPane.YES_OPTION) {
                    session.setCurrScan(new Scan(session.getCurrProfile(), this));
                    toFix = session.getCurrScan().actions.stream().mapToInt(Collection::size).sum() > 0;
                    return !toFix;
                }
                return true;
            }

            @Override
            protected void done() {
                try {
                    get();
                    btnFix.setEnabled(toFix);
                    close();
                    /* update entries in profile viewer */
                    if (MainFrame.getProfileViewer() != null)
                        MainFrame.getProfileViewer().reload();
                    ScanAutomation automation = ScanAutomation.valueOf(session.getCurrProfile().getSettings().getProperty(ProfileSettingsEnum.automation_scan));
                    if (automation.hasScanAgain())
                        scan(session, false);
                } catch (InterruptedException e) {
                    Log.err(e.getMessage(), e);
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    handleScanError(e);
                }
            }

        }.execute();

    }

    /**
     * Inits the scan settings.
     */
    public void initProfileSettings(final Session session) {
        scannerSettingsPanel.initProfileSettings(session.getCurrProfile().getSettings());
        scannerDirPanel.initProfileSettings(session);
        scannerFilters.initProfileSettings(session);
        scannerAdvFilters.initProfileSettings(session);
        scannerAutomation.initProfileSettings(session.getCurrProfile().getSettings());
    }

    /**
     * Load profile.
     *
     * @param profile the profile
     */
    @Override
    public void loadProfile(final Session session, final ProfileNFO profile) {
        if (session.getCurrProfile() != null)
            session.getCurrProfile().saveSettings();

        if (MainFrame.getProfileViewer() != null)
            MainFrame.getProfileViewer().clear();

        new SwingWorkerProgress<Void, Void>(SwingUtilities.getWindowAncestor(this)) {
            private boolean success = false;

            @Override
            protected Void doInBackground() throws Exception {
                success = (null != (Profile.load(session, profile, this)));
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    session.getReport().setProfile(session.getCurrProfile());
                    if (MainFrame.getProfileViewer() != null)
                        MainFrame.getProfileViewer().reset(session.getCurrProfile());
                    mainPane.setEnabledAt(1, success);
                    btnScan.setEnabled(success);
                    btnFix.setEnabled(false);
                    if (success && session.getCurrProfile() != null) {
                        lblProfileinfo.setText(session.getCurrProfile().getName());
                        scannerFilters.checkBoxListSystems.setModel(new SystmsModel(session.getCurrProfile().getSystems()));
                        scannerFilters.checkBoxListSources.setModel(new SourcesModel(session.getCurrProfile().getSources()));
                        initProfileSettings(session);
                        mainPane.setSelectedIndex(1);
                    }
                    this.close();
                } catch (InterruptedException e) {
                    Log.err(e.getMessage(), e);
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    Optional.ofNullable(e.getCause()).ifPresentOrElse(cause -> {
                        Log.err(cause.getMessage(), cause);
                        JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(ScannerPanel.this), cause.getMessage(), ERROR_STR, JOptionPane.ERROR_MESSAGE);
                    }, () -> {
                        Log.err(e.getMessage(), e);
                        JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(ScannerPanel.this), e.getMessage(), ERROR_STR, JOptionPane.ERROR_MESSAGE);
                    });
                }
            }
        }.execute();

    }

    JTabbedPane getMainPane() {
        return mainPane;
    }

    void setMainPane(JTabbedPane mainPane) {
        this.mainPane = mainPane;
    }

}
