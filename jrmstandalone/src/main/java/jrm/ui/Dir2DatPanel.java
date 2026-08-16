package jrm.ui;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;

import jrm.locale.Messages;
import jrm.profile.manager.Export.ExportType;
import jrm.profile.scan.Dir2Dat;
import jrm.profile.scan.DirScan;
import jrm.profile.scan.DirScan.Options;
import jrm.security.Session;
import jrm.ui.basic.JFileDropMode;
import jrm.ui.basic.JFileDropTextField;
import jrm.ui.basic.JRMFileChooser;
import jrm.ui.basic.JTextFieldHintUI;
import jrm.ui.progress.SwingWorkerProgress;

/**
 * Panel for scanning a directory and generating a DAT file from its contents.
 * <p>
 * Provides controls for selecting source and destination directories, configuring scan options
 * (subfolders, deep scan, hash algorithms), and specifying DAT header metadata (name, description,
 * version, author, etc.). Supports multiple DAT formats including Logiqx XML and MAME-compatible formats.
 */
public class Dir2DatPanel extends JPanel {
    /** Preference key for the destination DAT file path. */
    private static final String MAIN_FRAME_CHOOSE_DAT_DST = "MainFrame.ChooseDatDst";
    /** Preference key for the source directory path. */
    private static final String MAIN_FRAME_CHOOSE_DAT_SRC = "MainFrame.ChooseDatSrc";
    /** Text field for the source directory to scan. */
    private JFileDropTextField tfDir2DatSrc;
    /** Text field for the destination DAT file path. */
    private JFileDropTextField tfDir2DatDst;
    /** Button group for selecting the DAT output format. */
    private final ButtonGroup btngrpDir2DatFormat = new ButtonGroup();
    /** Text field for the DAT name header. */
    private JTextField tfDir2DatName;
    /** Text field for the DAT description header. */
    private JTextField tfDir2DatDescription;
    /** Text field for the DAT version header. */
    private JTextField tfDir2DatVersion;
    /** Text field for the DAT author header. */
    private JTextField tfDir2DatAuthor;
    /** Text field for the DAT comment header. */
    private JTextField tfDir2DatComment;
    /** Text field for the DAT category header. */
    private JTextField tfDir2DatCategory;
    /** Text field for the DAT date header. */
    private JTextField tfDir2DatDate;
    /** Text field for the DAT email header. */
    private JTextField tfDir2DatEMail;
    /** Text field for the DAT homepage URL header. */
    private JTextField tfDir2DatHomepage;
    /** Text field for the DAT URL header. */
    private JTextField tfDir2DatURL;

    /**
     * Constructs the Dir2Dat panel.
     * <p>
     * Initializes the UI with source/destination directory selectors, scan option checkboxes,
     * and DAT header metadata fields. All settings are persisted to the user's preferences.
     *
     * @param session the current user session providing access to settings
     */
    public Dir2DatPanel(final Session session) {
        GridBagLayout gblDir2DatTab = new GridBagLayout();
        gblDir2DatTab.columnWidths = new int[] { 0, 0, 0 };
        gblDir2DatTab.rowHeights = new int[] { 0, 0, 0 };
        gblDir2DatTab.columnWeights = new double[] { 1.0, 1.0, Double.MIN_VALUE };
        gblDir2DatTab.rowWeights = new double[] { 1.0, 0.0, Double.MIN_VALUE };
        this.setLayout(gblDir2DatTab);

        JPanel panelDir2DatOptions = new JPanel();
        panelDir2DatOptions.setBorder(new TitledBorder(null, Messages.getString("MainFrame.Options"), TitledBorder.LEADING, TitledBorder.TOP, null, null)); //$NON-NLS-1$
        GridBagConstraints gbcPanelDir2DatOptions = new GridBagConstraints();
        gbcPanelDir2DatOptions.insets = new Insets(0, 5, 5, 5);
        gbcPanelDir2DatOptions.fill = GridBagConstraints.BOTH;
        gbcPanelDir2DatOptions.gridx = 0;
        gbcPanelDir2DatOptions.gridy = 0;
        this.add(panelDir2DatOptions, gbcPanelDir2DatOptions);
        GridBagLayout gblPanelDir2DatOptions = new GridBagLayout();
        gblPanelDir2DatOptions.columnWidths = new int[] { 0, 0, 0, 0 };
        gblPanelDir2DatOptions.rowHeights = new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        gblPanelDir2DatOptions.columnWeights = new double[] { 1.0, 0.0, 1.0, Double.MIN_VALUE };
        gblPanelDir2DatOptions.rowWeights = new double[] { 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, Double.MIN_VALUE };
        panelDir2DatOptions.setLayout(gblPanelDir2DatOptions);

        JCheckBox cbDir2DatScanSubfolders = new JCheckBox(Messages.getString("MainFrame.chckbxScanSubfolders.text")); //$NON-NLS-1$
        cbDir2DatScanSubfolders.setSelected(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_scan_subfolders, Boolean.class)); // $NON-NLS-1$
        cbDir2DatScanSubfolders
                .addItemListener(e -> session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_scan_subfolders, e.getStateChange() == ItemEvent.SELECTED)); // $NON-NLS-1$
        GridBagConstraints gbcCBDir2DatScanSubfolders = new GridBagConstraints();
        gbcCBDir2DatScanSubfolders.anchor = GridBagConstraints.WEST;
        gbcCBDir2DatScanSubfolders.insets = new Insets(0, 0, 5, 5);
        gbcCBDir2DatScanSubfolders.gridx = 1;
        gbcCBDir2DatScanSubfolders.gridy = 1;
        panelDir2DatOptions.add(cbDir2DatScanSubfolders, gbcCBDir2DatScanSubfolders);

        JCheckBox cbDir2DatDeepScan = new JCheckBox(Messages.getString("MainFrame.chckbxDeepScanFor.text")); //$NON-NLS-1$
        cbDir2DatDeepScan.setSelected(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_deep_scan, Boolean.class)); // $NON-NLS-1$
        cbDir2DatDeepScan.addItemListener(e -> session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_deep_scan, e.getStateChange() == ItemEvent.SELECTED)); // $NON-NLS-1$
        GridBagConstraints gbcCBDir2DatDeepScan = new GridBagConstraints();
        gbcCBDir2DatDeepScan.anchor = GridBagConstraints.WEST;
        gbcCBDir2DatDeepScan.insets = new Insets(0, 0, 5, 5);
        gbcCBDir2DatDeepScan.gridx = 1;
        gbcCBDir2DatDeepScan.gridy = 2;
        panelDir2DatOptions.add(cbDir2DatDeepScan, gbcCBDir2DatDeepScan);

        JCheckBox cbDir2DatAddMd5 = new JCheckBox(Messages.getString("MainFrame.chckbxAddMd.text")); //$NON-NLS-1$
        cbDir2DatAddMd5.setSelected(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_add_md5, Boolean.class)); // $NON-NLS-1$
        cbDir2DatAddMd5.addItemListener(e -> session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_add_md5, e.getStateChange() == ItemEvent.SELECTED)); // $NON-NLS-1$
        GridBagConstraints gbcCBDir2DatAddMd5 = new GridBagConstraints();
        gbcCBDir2DatAddMd5.anchor = GridBagConstraints.WEST;
        gbcCBDir2DatAddMd5.insets = new Insets(0, 0, 5, 5);
        gbcCBDir2DatAddMd5.gridx = 1;
        gbcCBDir2DatAddMd5.gridy = 3;
        panelDir2DatOptions.add(cbDir2DatAddMd5, gbcCBDir2DatAddMd5);

        JCheckBox cbDir2DatAddSha1 = new JCheckBox(Messages.getString("MainFrame.chckbxAddShamd.text")); //$NON-NLS-1$
        cbDir2DatAddSha1.setSelected(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_add_sha1, Boolean.class)); // $NON-NLS-1$
        cbDir2DatAddSha1.addItemListener(e -> session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_add_sha1, e.getStateChange() == ItemEvent.SELECTED)); // $NON-NLS-1$
        GridBagConstraints gbcCBDir2DatAddSha1 = new GridBagConstraints();
        gbcCBDir2DatAddSha1.anchor = GridBagConstraints.WEST;
        gbcCBDir2DatAddSha1.insets = new Insets(0, 0, 5, 5);
        gbcCBDir2DatAddSha1.gridx = 1;
        gbcCBDir2DatAddSha1.gridy = 4;
        panelDir2DatOptions.add(cbDir2DatAddSha1, gbcCBDir2DatAddSha1);

        JCheckBox cbDir2DatJunkSubfolders = new JCheckBox(Messages.getString("MainFrame.chckbxJunkSubfolders.text")); //$NON-NLS-1$
        cbDir2DatJunkSubfolders.setSelected(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_junk_folders, Boolean.class)); // $NON-NLS-1$
        cbDir2DatJunkSubfolders
                .addItemListener(e -> session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_junk_folders, e.getStateChange() == ItemEvent.SELECTED)); // $NON-NLS-1$
        GridBagConstraints gbcCBDir2DatJunkSubfolders = new GridBagConstraints();
        gbcCBDir2DatJunkSubfolders.anchor = GridBagConstraints.WEST;
        gbcCBDir2DatJunkSubfolders.insets = new Insets(0, 0, 5, 5);
        gbcCBDir2DatJunkSubfolders.gridx = 1;
        gbcCBDir2DatJunkSubfolders.gridy = 5;
        panelDir2DatOptions.add(cbDir2DatJunkSubfolders, gbcCBDir2DatJunkSubfolders);

        JCheckBox cbDir2DatDoNotScan = new JCheckBox(Messages.getString("MainFrame.chckbxDoNotScan.text")); //$NON-NLS-1$
        cbDir2DatDoNotScan.setSelected(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_do_not_scan_archives, Boolean.class)); // $NON-NLS-1$
        cbDir2DatDoNotScan
                .addItemListener(e -> session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_do_not_scan_archives, e.getStateChange() == ItemEvent.SELECTED)); // $NON-NLS-1$
        GridBagConstraints gbcCBDir2DatDoNotScan = new GridBagConstraints();
        gbcCBDir2DatDoNotScan.anchor = GridBagConstraints.WEST;
        gbcCBDir2DatDoNotScan.insets = new Insets(0, 0, 5, 5);
        gbcCBDir2DatDoNotScan.gridx = 1;
        gbcCBDir2DatDoNotScan.gridy = 6;
        panelDir2DatOptions.add(cbDir2DatDoNotScan, gbcCBDir2DatDoNotScan);

        JCheckBox cbDir2DatMatchCurrentProfile = new JCheckBox(Messages.getString("MainFrame.chckbxMatchCurrentProfile.text")); //$NON-NLS-1$
        cbDir2DatMatchCurrentProfile.setSelected(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_match_profile, Boolean.class)); // $NON-NLS-1$
        cbDir2DatMatchCurrentProfile
                .addItemListener(e -> session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_match_profile, e.getStateChange() == ItemEvent.SELECTED)); // $NON-NLS-1$
        GridBagConstraints gbcCBDir2DatMatchCurrentProfile = new GridBagConstraints();
        gbcCBDir2DatMatchCurrentProfile.anchor = GridBagConstraints.WEST;
        gbcCBDir2DatMatchCurrentProfile.insets = new Insets(0, 0, 5, 5);
        gbcCBDir2DatMatchCurrentProfile.gridx = 1;
        gbcCBDir2DatMatchCurrentProfile.gridy = 7;
        panelDir2DatOptions.add(cbDir2DatMatchCurrentProfile, gbcCBDir2DatMatchCurrentProfile);

        JCheckBox cbDir2DatIncludeEmptyDirs = new JCheckBox(Messages.getString("MainFrame.chckbxIncludeEmptyDirs.text")); //$NON-NLS-1$
        cbDir2DatIncludeEmptyDirs.setSelected(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_include_empty_dirs, Boolean.class)); // $NON-NLS-1$
        cbDir2DatIncludeEmptyDirs
                .addItemListener(e -> session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_include_empty_dirs, e.getStateChange() == ItemEvent.SELECTED)); // $NON-NLS-1$
        GridBagConstraints gbcCBDir2DatIncludeEmptyDirs = new GridBagConstraints();
        gbcCBDir2DatIncludeEmptyDirs.anchor = GridBagConstraints.WEST;
        gbcCBDir2DatIncludeEmptyDirs.insets = new Insets(0, 0, 5, 5);
        gbcCBDir2DatIncludeEmptyDirs.gridx = 1;
        gbcCBDir2DatIncludeEmptyDirs.gridy = 8;
        panelDir2DatOptions.add(cbDir2DatIncludeEmptyDirs, gbcCBDir2DatIncludeEmptyDirs);

        JPanel panelDir2DatHeaders = new JPanel();
        panelDir2DatHeaders.setBorder(new TitledBorder(null, Messages.getString("MainFrame.Headers"), TitledBorder.LEADING, TitledBorder.TOP, null, null)); //$NON-NLS-1$
        GridBagConstraints gbcPanelDir2DatHeaders = new GridBagConstraints();
        gbcPanelDir2DatHeaders.insets = new Insets(0, 0, 5, 5);
        gbcPanelDir2DatHeaders.fill = GridBagConstraints.BOTH;
        gbcPanelDir2DatHeaders.gridx = 1;
        gbcPanelDir2DatHeaders.gridy = 0;
        this.add(panelDir2DatHeaders, gbcPanelDir2DatHeaders);
        GridBagLayout gblPanelDir2DatHeaders = new GridBagLayout();
        gblPanelDir2DatHeaders.columnWidths = new int[] { 0, 0, 0 };
        gblPanelDir2DatHeaders.rowHeights = new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        gblPanelDir2DatHeaders.columnWeights = new double[] { 0.0, 1.0, Double.MIN_VALUE };
        gblPanelDir2DatHeaders.rowWeights = new double[] { 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, Double.MIN_VALUE };
        panelDir2DatHeaders.setLayout(gblPanelDir2DatHeaders);

        JLabel lblDir2DatName = new JLabel(Messages.getString("MainFrame.lblName.text")); //$NON-NLS-1$
        GridBagConstraints gbcLblDir2DatName = new GridBagConstraints();
        gbcLblDir2DatName.anchor = GridBagConstraints.EAST;
        gbcLblDir2DatName.insets = new Insets(0, 0, 5, 5);
        gbcLblDir2DatName.gridx = 0;
        gbcLblDir2DatName.gridy = 1;
        panelDir2DatHeaders.add(lblDir2DatName, gbcLblDir2DatName);

        tfDir2DatName = new JTextField();
        tfDir2DatName.setText(""); //$NON-NLS-1$
        GridBagConstraints gbcTFDir2DatName = new GridBagConstraints();
        gbcTFDir2DatName.insets = new Insets(0, 0, 5, 0);
        gbcTFDir2DatName.fill = GridBagConstraints.HORIZONTAL;
        gbcTFDir2DatName.gridx = 1;
        gbcTFDir2DatName.gridy = 1;
        panelDir2DatHeaders.add(tfDir2DatName, gbcTFDir2DatName);
        tfDir2DatName.setColumns(20);

        JLabel lblDir2DatDescription = new JLabel(Messages.getString("MainFrame.lblDescription.text")); //$NON-NLS-1$
        GridBagConstraints gbcLblDir2DatDescription = new GridBagConstraints();
        gbcLblDir2DatDescription.anchor = GridBagConstraints.EAST;
        gbcLblDir2DatDescription.insets = new Insets(0, 0, 5, 5);
        gbcLblDir2DatDescription.gridx = 0;
        gbcLblDir2DatDescription.gridy = 2;
        panelDir2DatHeaders.add(lblDir2DatDescription, gbcLblDir2DatDescription);

        tfDir2DatDescription = new JTextField();
        tfDir2DatDescription.setText(""); //$NON-NLS-1$
        tfDir2DatDescription.setColumns(20);
        GridBagConstraints gbcTFDir2DatDescription = new GridBagConstraints();
        gbcTFDir2DatDescription.insets = new Insets(0, 0, 5, 0);
        gbcTFDir2DatDescription.fill = GridBagConstraints.HORIZONTAL;
        gbcTFDir2DatDescription.gridx = 1;
        gbcTFDir2DatDescription.gridy = 2;
        panelDir2DatHeaders.add(tfDir2DatDescription, gbcTFDir2DatDescription);

        JLabel lblDir2DatVersion = new JLabel(Messages.getString("MainFrame.lblVersion.text")); //$NON-NLS-1$
        GridBagConstraints gbcLblDir2DatVersion = new GridBagConstraints();
        gbcLblDir2DatVersion.anchor = GridBagConstraints.EAST;
        gbcLblDir2DatVersion.insets = new Insets(0, 0, 5, 5);
        gbcLblDir2DatVersion.gridx = 0;
        gbcLblDir2DatVersion.gridy = 3;
        panelDir2DatHeaders.add(lblDir2DatVersion, gbcLblDir2DatVersion);

        tfDir2DatVersion = new JTextField();
        tfDir2DatVersion.setText(""); //$NON-NLS-1$
        tfDir2DatVersion.setColumns(20);
        GridBagConstraints gbcTFDir2DatVersion = new GridBagConstraints();
        gbcTFDir2DatVersion.insets = new Insets(0, 0, 5, 0);
        gbcTFDir2DatVersion.fill = GridBagConstraints.HORIZONTAL;
        gbcTFDir2DatVersion.gridx = 1;
        gbcTFDir2DatVersion.gridy = 3;
        panelDir2DatHeaders.add(tfDir2DatVersion, gbcTFDir2DatVersion);

        JLabel lblDir2DatAuthor = new JLabel(Messages.getString("MainFrame.lblAuthor.text")); //$NON-NLS-1$
        GridBagConstraints gbcLblDir2DatAuthor = new GridBagConstraints();
        gbcLblDir2DatAuthor.anchor = GridBagConstraints.EAST;
        gbcLblDir2DatAuthor.insets = new Insets(0, 0, 5, 5);
        gbcLblDir2DatAuthor.gridx = 0;
        gbcLblDir2DatAuthor.gridy = 4;
        panelDir2DatHeaders.add(lblDir2DatAuthor, gbcLblDir2DatAuthor);

        tfDir2DatAuthor = new JTextField();
        tfDir2DatAuthor.setText(""); //$NON-NLS-1$
        tfDir2DatAuthor.setColumns(20);
        GridBagConstraints gbcTFDir2DatAuthor = new GridBagConstraints();
        gbcTFDir2DatAuthor.insets = new Insets(0, 0, 5, 0);
        gbcTFDir2DatAuthor.fill = GridBagConstraints.HORIZONTAL;
        gbcTFDir2DatAuthor.gridx = 1;
        gbcTFDir2DatAuthor.gridy = 4;
        panelDir2DatHeaders.add(tfDir2DatAuthor, gbcTFDir2DatAuthor);

        JLabel lblDir2DatComment = new JLabel(Messages.getString("MainFrame.lblComment.text")); //$NON-NLS-1$
        GridBagConstraints gbcLblDir2DatComment = new GridBagConstraints();
        gbcLblDir2DatComment.insets = new Insets(0, 0, 5, 5);
        gbcLblDir2DatComment.anchor = GridBagConstraints.EAST;
        gbcLblDir2DatComment.gridx = 0;
        gbcLblDir2DatComment.gridy = 5;
        panelDir2DatHeaders.add(lblDir2DatComment, gbcLblDir2DatComment);

        tfDir2DatComment = new JTextField();
        tfDir2DatComment.setText(""); //$NON-NLS-1$
        tfDir2DatComment.setColumns(20);
        GridBagConstraints gbcTFDir2DatComment = new GridBagConstraints();
        gbcTFDir2DatComment.insets = new Insets(0, 0, 5, 0);
        gbcTFDir2DatComment.fill = GridBagConstraints.HORIZONTAL;
        gbcTFDir2DatComment.gridx = 1;
        gbcTFDir2DatComment.gridy = 5;
        panelDir2DatHeaders.add(tfDir2DatComment, gbcTFDir2DatComment);

        JLabel lblDir2DatCategory = new JLabel(Messages.getString("MainFrame.lblCategory.text")); //$NON-NLS-1$
        GridBagConstraints gbcLblDir2DatCategory = new GridBagConstraints();
        gbcLblDir2DatCategory.anchor = GridBagConstraints.EAST;
        gbcLblDir2DatCategory.insets = new Insets(0, 0, 5, 5);
        gbcLblDir2DatCategory.gridx = 0;
        gbcLblDir2DatCategory.gridy = 6;
        panelDir2DatHeaders.add(lblDir2DatCategory, gbcLblDir2DatCategory);

        tfDir2DatCategory = new JTextField();
        tfDir2DatCategory.setText(""); //$NON-NLS-1$
        tfDir2DatCategory.setColumns(20);
        GridBagConstraints gbcTFDir2DatCategory = new GridBagConstraints();
        gbcTFDir2DatCategory.insets = new Insets(0, 0, 5, 0);
        gbcTFDir2DatCategory.fill = GridBagConstraints.HORIZONTAL;
        gbcTFDir2DatCategory.gridx = 1;
        gbcTFDir2DatCategory.gridy = 6;
        panelDir2DatHeaders.add(tfDir2DatCategory, gbcTFDir2DatCategory);

        JLabel lblDir2DatDate = new JLabel(Messages.getString("MainFrame.lblDate.text")); //$NON-NLS-1$
        GridBagConstraints gbcLblDir2DatDate = new GridBagConstraints();
        gbcLblDir2DatDate.anchor = GridBagConstraints.EAST;
        gbcLblDir2DatDate.insets = new Insets(0, 0, 5, 5);
        gbcLblDir2DatDate.gridx = 0;
        gbcLblDir2DatDate.gridy = 7;
        panelDir2DatHeaders.add(lblDir2DatDate, gbcLblDir2DatDate);

        tfDir2DatDate = new JTextField();
        tfDir2DatDate.setText(""); //$NON-NLS-1$
        tfDir2DatDate.setColumns(20);
        GridBagConstraints gbcTFDir2DatDate = new GridBagConstraints();
        gbcTFDir2DatDate.insets = new Insets(0, 0, 5, 0);
        gbcTFDir2DatDate.fill = GridBagConstraints.HORIZONTAL;
        gbcTFDir2DatDate.gridx = 1;
        gbcTFDir2DatDate.gridy = 7;
        panelDir2DatHeaders.add(tfDir2DatDate, gbcTFDir2DatDate);

        JLabel lblDir2DatEmail = new JLabel(Messages.getString("MainFrame.lblEmail.text")); //$NON-NLS-1$
        GridBagConstraints gbcLblDir2DatEmail = new GridBagConstraints();
        gbcLblDir2DatEmail.anchor = GridBagConstraints.EAST;
        gbcLblDir2DatEmail.insets = new Insets(0, 0, 5, 5);
        gbcLblDir2DatEmail.gridx = 0;
        gbcLblDir2DatEmail.gridy = 8;
        panelDir2DatHeaders.add(lblDir2DatEmail, gbcLblDir2DatEmail);

        tfDir2DatEMail = new JTextField();
        tfDir2DatEMail.setText(""); //$NON-NLS-1$
        tfDir2DatEMail.setColumns(20);
        GridBagConstraints gbcTFDir2DatEMail = new GridBagConstraints();
        gbcTFDir2DatEMail.insets = new Insets(0, 0, 5, 0);
        gbcTFDir2DatEMail.fill = GridBagConstraints.HORIZONTAL;
        gbcTFDir2DatEMail.gridx = 1;
        gbcTFDir2DatEMail.gridy = 8;
        panelDir2DatHeaders.add(tfDir2DatEMail, gbcTFDir2DatEMail);

        JLabel lblDir2DatHomepage = new JLabel(Messages.getString("MainFrame.lblHomepage.text")); //$NON-NLS-1$
        GridBagConstraints gbcLblDir2DatHomepage = new GridBagConstraints();
        gbcLblDir2DatHomepage.anchor = GridBagConstraints.EAST;
        gbcLblDir2DatHomepage.insets = new Insets(0, 0, 5, 5);
        gbcLblDir2DatHomepage.gridx = 0;
        gbcLblDir2DatHomepage.gridy = 9;
        panelDir2DatHeaders.add(lblDir2DatHomepage, gbcLblDir2DatHomepage);

        tfDir2DatHomepage = new JTextField();
        tfDir2DatHomepage.setText(""); //$NON-NLS-1$
        tfDir2DatHomepage.setColumns(20);
        GridBagConstraints gbcTFDir2DatHomepage = new GridBagConstraints();
        gbcTFDir2DatHomepage.insets = new Insets(0, 0, 5, 0);
        gbcTFDir2DatHomepage.fill = GridBagConstraints.HORIZONTAL;
        gbcTFDir2DatHomepage.gridx = 1;
        gbcTFDir2DatHomepage.gridy = 9;
        panelDir2DatHeaders.add(tfDir2DatHomepage, gbcTFDir2DatHomepage);

        JLabel lblDir2DatUrl = new JLabel(Messages.getString("MainFrame.lblUrl.text")); //$NON-NLS-1$
        GridBagConstraints gbcLblDir2DatUrl = new GridBagConstraints();
        gbcLblDir2DatUrl.insets = new Insets(0, 0, 5, 5);
        gbcLblDir2DatUrl.anchor = GridBagConstraints.EAST;
        gbcLblDir2DatUrl.gridx = 0;
        gbcLblDir2DatUrl.gridy = 10;
        panelDir2DatHeaders.add(lblDir2DatUrl, gbcLblDir2DatUrl);

        tfDir2DatURL = new JTextField();
        tfDir2DatURL.setText(""); //$NON-NLS-1$
        tfDir2DatURL.setColumns(20);
        GridBagConstraints gbcTFDir2DatURL = new GridBagConstraints();
        gbcTFDir2DatURL.insets = new Insets(0, 0, 5, 0);
        gbcTFDir2DatURL.fill = GridBagConstraints.HORIZONTAL;
        gbcTFDir2DatURL.gridx = 1;
        gbcTFDir2DatURL.gridy = 10;
        panelDir2DatHeaders.add(tfDir2DatURL, gbcTFDir2DatURL);

        JPanel panelDir2DatIO = new JPanel();
        panelDir2DatIO.setBorder(
                new TitledBorder(UIManager.getBorder("TitledBorder.border"), Messages.getString("MainFrame.IO"), TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0))); //$NON-NLS-1$ //$NON-NLS-2$
        GridBagConstraints gbcPanelDir2DatIO = new GridBagConstraints();
        gbcPanelDir2DatIO.insets = new Insets(0, 5, 5, 5);
        gbcPanelDir2DatIO.gridwidth = 2;
        gbcPanelDir2DatIO.fill = GridBagConstraints.BOTH;
        gbcPanelDir2DatIO.gridx = 0;
        gbcPanelDir2DatIO.gridy = 1;
        this.add(panelDir2DatIO, gbcPanelDir2DatIO);
        GridBagLayout gblPanelDir2DatIO = new GridBagLayout();
        gblPanelDir2DatIO.columnWidths = new int[] { 0, 0, 0, 0, 0, 0, 0 };
        gblPanelDir2DatIO.rowHeights = new int[] { 0, 0, 0, 0 };
        gblPanelDir2DatIO.columnWeights = new double[] { 0.0, 1.0, 1.0, 1.0, 0.0, 0.0, Double.MIN_VALUE };
        gblPanelDir2DatIO.rowWeights = new double[] { 0.0, 0.0, 0.0, Double.MIN_VALUE };
        panelDir2DatIO.setLayout(gblPanelDir2DatIO);

        JLabel lblDir2DatSrc = new JLabel(Messages.getString("MainFrame.lblSrcDir_1.text")); //$NON-NLS-1$
        GridBagConstraints gbcLblDir2DatSrc = new GridBagConstraints();
        gbcLblDir2DatSrc.fill = GridBagConstraints.HORIZONTAL;
        gbcLblDir2DatSrc.insets = new Insets(0, 5, 5, 5);
        gbcLblDir2DatSrc.gridx = 0;
        gbcLblDir2DatSrc.gridy = 0;
        panelDir2DatIO.add(lblDir2DatSrc, gbcLblDir2DatSrc);

        tfDir2DatSrc = new JFileDropTextField(txt -> session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_src_dir, txt)); // $NON-NLS-1$
        tfDir2DatSrc.setText(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_src_dir)); // $NON-NLS-1$
                                                                                                                  // //$NON-NLS-2$
        tfDir2DatSrc.setMode(JFileDropMode.DIRECTORY);
        tfDir2DatSrc.setUI(new JTextFieldHintUI(Messages.getString("MainFrame.DropDirHint"), Color.gray)); //$NON-NLS-1$
        tfDir2DatSrc.setColumns(10);
        GridBagConstraints gbcTFDir2DatSrc = new GridBagConstraints();
        gbcTFDir2DatSrc.gridwidth = 3;
        gbcTFDir2DatSrc.insets = new Insets(0, 0, 5, 0);
        gbcTFDir2DatSrc.fill = GridBagConstraints.BOTH;
        gbcTFDir2DatSrc.gridx = 1;
        gbcTFDir2DatSrc.gridy = 0;
        panelDir2DatIO.add(tfDir2DatSrc, gbcTFDir2DatSrc);

        JButton btnDir2DatSrc = new JButton(""); //$NON-NLS-1$
        btnDir2DatSrc.setIcon(MainFrame.getIcon("/jrm/resicons/icons/disk.png")); //$NON-NLS-1$
        btnDir2DatSrc.addActionListener(_ -> {
            final File workdir = session.getUser().getSettings().getWorkPath().toFile(); // $NON-NLS-1$
            new JRMFileChooser<Void>(JFileChooser.OPEN_DIALOG, JFileChooser.DIRECTORIES_ONLY,
                    new File(session.getUser().getSettings().getProperty(MAIN_FRAME_CHOOSE_DAT_SRC, workdir.getAbsolutePath())), new File(tfDir2DatSrc.getText()), null,
                    Messages.getString(MAIN_FRAME_CHOOSE_DAT_SRC), false).show(SwingUtilities.getWindowAncestor(this), chooser -> { // $NON-NLS-1$
                                                                                                                                    // //$NON-NLS-2$
                        session.getUser().getSettings().setProperty(MAIN_FRAME_CHOOSE_DAT_SRC, chooser.getCurrentDirectory().getAbsolutePath()); // $NON-NLS-1$
                        tfDir2DatSrc.setText(chooser.getSelectedFile().getAbsolutePath());
                        session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_src_dir, tfDir2DatSrc.getText()); // $NON-NLS-1$
                        return null;
                    });
        });
        GridBagConstraints gbcBtnDir2DatSrc = new GridBagConstraints();
        gbcBtnDir2DatSrc.insets = new Insets(0, 0, 5, 5);
        gbcBtnDir2DatSrc.gridx = 4;
        gbcBtnDir2DatSrc.gridy = 0;
        panelDir2DatIO.add(btnDir2DatSrc, gbcBtnDir2DatSrc);

        JButton btnDir2DatGenerate = new JButton(Messages.getString("MainFrame.btnGenerate.text")); //$NON-NLS-1$
        btnDir2DatGenerate.addActionListener(_ -> dir2dat(session));
        GridBagConstraints gbcBtnDir2DatGenerate = new GridBagConstraints();
        gbcBtnDir2DatGenerate.fill = GridBagConstraints.BOTH;
        gbcBtnDir2DatGenerate.gridheight = 3;
        gbcBtnDir2DatGenerate.gridx = 5;
        gbcBtnDir2DatGenerate.gridy = 0;
        panelDir2DatIO.add(btnDir2DatGenerate, gbcBtnDir2DatGenerate);

        JLabel lblDir2DatDst = new JLabel(Messages.getString("MainFrame.lblDstDat.text")); //$NON-NLS-1$
        GridBagConstraints gbcLblDir2DatDst = new GridBagConstraints();
        gbcLblDir2DatDst.fill = GridBagConstraints.HORIZONTAL;
        gbcLblDir2DatDst.insets = new Insets(0, 5, 5, 5);
        gbcLblDir2DatDst.gridx = 0;
        gbcLblDir2DatDst.gridy = 1;
        panelDir2DatIO.add(lblDir2DatDst, gbcLblDir2DatDst);

        tfDir2DatDst = new JFileDropTextField(txt -> session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_dst_file, txt)); // $NON-NLS-1$
        tfDir2DatDst.setText(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_dst_file)); // $NON-NLS-1$
                                                                                                                   // //$NON-NLS-2$
        tfDir2DatDst.setMode(JFileDropMode.FILE);
        tfDir2DatDst.setUI(new JTextFieldHintUI(Messages.getString("MainFrame.DropFileHint"), Color.gray)); //$NON-NLS-1$
        tfDir2DatDst.setColumns(10);
        GridBagConstraints gbcTFDir2DatDst = new GridBagConstraints();
        gbcTFDir2DatDst.gridwidth = 3;
        gbcTFDir2DatDst.insets = new Insets(0, 0, 5, 0);
        gbcTFDir2DatDst.fill = GridBagConstraints.BOTH;
        gbcTFDir2DatDst.gridx = 1;
        gbcTFDir2DatDst.gridy = 1;
        panelDir2DatIO.add(tfDir2DatDst, gbcTFDir2DatDst);

        JButton btnDir2DatDst = new JButton(""); //$NON-NLS-1$
        btnDir2DatDst.setIcon(MainFrame.getIcon("/jrm/resicons/icons/disk.png")); //$NON-NLS-1$
        btnDir2DatDst.addActionListener(_ -> {
            final File workdir = session.getUser().getSettings().getWorkPath().toFile(); // $NON-NLS-1$
            new JRMFileChooser<Void>(JFileChooser.SAVE_DIALOG, JFileChooser.FILES_ONLY,
                    new File(session.getUser().getSettings().getProperty(MAIN_FRAME_CHOOSE_DAT_DST, workdir.getAbsolutePath())), new File(tfDir2DatDst.getText()), null,
                    Messages.getString(MAIN_FRAME_CHOOSE_DAT_DST), false).show(SwingUtilities.getWindowAncestor(this), chooser -> { // $NON-NLS-1$
                                                                                                                                    // //$NON-NLS-2$
                        session.getUser().getSettings().setProperty(MAIN_FRAME_CHOOSE_DAT_DST, chooser.getCurrentDirectory().getAbsolutePath()); // $NON-NLS-1$
                        tfDir2DatDst.setText(chooser.getSelectedFile().getAbsolutePath());
                        session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_dst_file, tfDir2DatDst.getText()); // $NON-NLS-1$
                        return null;
                    });
        });
        GridBagConstraints gbcBtnDir2DatDst = new GridBagConstraints();
        gbcBtnDir2DatDst.insets = new Insets(0, 0, 5, 5);
        gbcBtnDir2DatDst.gridx = 4;
        gbcBtnDir2DatDst.gridy = 1;
        panelDir2DatIO.add(btnDir2DatDst, gbcBtnDir2DatDst);

        JLabel lblDir2DatFormat = new JLabel(Messages.getString("MainFrame.lblFormat.text")); //$NON-NLS-1$
        lblDir2DatFormat.setHorizontalAlignment(SwingConstants.TRAILING);
        GridBagConstraints gbcLblDir2DatFormat = new GridBagConstraints();
        gbcLblDir2DatFormat.fill = GridBagConstraints.HORIZONTAL;
        gbcLblDir2DatFormat.insets = new Insets(0, 0, 0, 5);
        gbcLblDir2DatFormat.gridx = 0;
        gbcLblDir2DatFormat.gridy = 2;
        panelDir2DatIO.add(lblDir2DatFormat, gbcLblDir2DatFormat);

        JRadioButton rdbtnDir2DatMame = new JRadioButton(Messages.getString("MainFrame.rdbtnMame.text")); //$NON-NLS-1$
        rdbtnDir2DatMame.setSelected(ExportType.valueOf(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_format)) == ExportType.MAME); // $NON-NLS-1$
        rdbtnDir2DatMame.addActionListener(_ -> session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_format, ExportType.MAME.toString())); // $NON-NLS-1$
        btngrpDir2DatFormat.add(rdbtnDir2DatMame);
        GridBagConstraints gbcRdBtnDir2DatMame = new GridBagConstraints();
        gbcRdBtnDir2DatMame.insets = new Insets(0, 0, 0, 5);
        gbcRdBtnDir2DatMame.gridx = 1;
        gbcRdBtnDir2DatMame.gridy = 2;
        panelDir2DatIO.add(rdbtnDir2DatMame, gbcRdBtnDir2DatMame);

        JRadioButton rdbtnDir2DatLogiqxDat = new JRadioButton(Messages.getString("MainFrame.rdbtnLogiqxDat.text")); //$NON-NLS-1$
        rdbtnDir2DatLogiqxDat.setSelected(ExportType.valueOf(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_format)) == ExportType.DATAFILE); // $NON-NLS-1$
        rdbtnDir2DatLogiqxDat.addActionListener(_ -> session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_format, ExportType.DATAFILE.toString())); // $NON-NLS-1$
        btngrpDir2DatFormat.add(rdbtnDir2DatLogiqxDat);
        GridBagConstraints gbcRdBtnDir2DatLogiqxDat = new GridBagConstraints();
        gbcRdBtnDir2DatLogiqxDat.insets = new Insets(0, 0, 0, 5);
        gbcRdBtnDir2DatLogiqxDat.gridx = 2;
        gbcRdBtnDir2DatLogiqxDat.gridy = 2;
        panelDir2DatIO.add(rdbtnDir2DatLogiqxDat, gbcRdBtnDir2DatLogiqxDat);

        JRadioButton rdbtnDir2DatSwList = new JRadioButton(Messages.getString("MainFrame.rdbtnSwList.text")); //$NON-NLS-1$
        rdbtnDir2DatSwList.setSelected(ExportType.valueOf(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_format)) == ExportType.SOFTWARELIST); // $NON-NLS-1$
        rdbtnDir2DatSwList.addActionListener(_ -> session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.dir2dat_format, ExportType.SOFTWARELIST.toString())); // $NON-NLS-1$
        btngrpDir2DatFormat.add(rdbtnDir2DatSwList);
        GridBagConstraints gbcRdBtnDir2DatSwList = new GridBagConstraints();
        gbcRdBtnDir2DatSwList.gridwidth = 2;
        gbcRdBtnDir2DatSwList.insets = new Insets(0, 0, 0, 5);
        gbcRdBtnDir2DatSwList.gridx = 3;
        gbcRdBtnDir2DatSwList.gridy = 2;
        panelDir2DatIO.add(rdbtnDir2DatSwList, gbcRdBtnDir2DatSwList);
    }

    /**
     * Dir2Dat
     */
    private void dir2dat(final Session session) {
        new SwingWorkerProgress<Void, Void>(SwingUtilities.getWindowAncestor(this)) {
            @Override
            protected Void doInBackground() throws Exception {
                final String src = tfDir2DatSrc.getText();
                final String dst = tfDir2DatDst.getText();
                if (src != null && !src.isEmpty() && dst != null && !dst.isEmpty()) {
                    final File srcdir = new File(src);
                    if (srcdir.isDirectory()) {
                        final File dstdat = new File(dst);
                        if (dstdat.getParentFile().isDirectory() && (dstdat.exists() || dstdat.createNewFile())) {
                            final var options = initOptions(session);
                            final var type = ExportType.valueOf(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_format)); // $NON-NLS-1$
                            final var headers = initHeaders();
                            new Dir2Dat(session, srcdir, dstdat, this, options, type, headers);
                        }
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                close();
            }

        }.execute();
    }

    /**
     * @param session
     * 
     * @return
     */
    private EnumSet<DirScan.Options> initOptions(final Session session) {
        EnumSet<DirScan.Options> options = EnumSet.of(Options.USE_PARALLELISM, Options.MD5_DISKS, Options.SHA1_DISKS);
        if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_scan_subfolders, Boolean.class))) // $NON-NLS-1$
            options.add(Options.RECURSE);
        if (Boolean.FALSE.equals(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_deep_scan, Boolean.class))) // $NON-NLS-1$
            options.add(Options.IS_DEST);
        if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_add_md5, Boolean.class))) // $NON-NLS-1$
            options.add(Options.NEED_MD5);
        if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_add_sha1, Boolean.class))) // $NON-NLS-1$
            options.add(Options.NEED_SHA1);
        if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_junk_folders, Boolean.class))) // $NON-NLS-1$
            options.add(Options.JUNK_SUBFOLDERS);
        if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_do_not_scan_archives, Boolean.class))) // $NON-NLS-1$
            options.add(Options.ARCHIVES_AND_CHD_AS_ROMS);
        if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_match_profile, Boolean.class))) // $NON-NLS-1$
            options.add(Options.MATCH_PROFILE);
        if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.dir2dat_include_empty_dirs, Boolean.class))) // $NON-NLS-1$
            options.add(Options.EMPTY_DIRS);
        return options;
    }

    /**
     * @return
     */
    private HashMap<String, String> initHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("name", tfDir2DatName.getText()); //$NON-NLS-1$
        headers.put("description", tfDir2DatDescription.getText()); //$NON-NLS-1$
        headers.put("version", tfDir2DatVersion.getText()); //$NON-NLS-1$
        headers.put("author", tfDir2DatAuthor.getText()); //$NON-NLS-1$
        headers.put("comment", tfDir2DatComment.getText()); //$NON-NLS-1$
        headers.put("category", tfDir2DatCategory.getText()); //$NON-NLS-1$
        headers.put("date", tfDir2DatDate.getText()); //$NON-NLS-1$
        headers.put("email", tfDir2DatEMail.getText()); //$NON-NLS-1$
        headers.put("homepage", tfDir2DatHomepage.getText()); //$NON-NLS-1$
        headers.put("url", tfDir2DatURL.getText()); //$NON-NLS-1$
        return headers;
    }
}
