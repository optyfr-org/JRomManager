package jrm.ui;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.apache.commons.lang3.StringUtils;

import jrm.locale.Messages;
import jrm.misc.ProfileSettingsEnum;
import jrm.security.Session;
import jrm.ui.basic.JFileDropList;
import jrm.ui.basic.JFileDropMode;
import jrm.ui.basic.JFileDropTextField;
import jrm.ui.basic.JListHintUI;
import jrm.ui.basic.JRMFileChooser;
import jrm.ui.basic.JTextFieldHintUI;
import jrm.ui.basic.Popup;

/**
 * Panel for configuring scanner source and destination directories.
 * <p>
 * Provides controls for selecting ROM/disk source directories, destination paths for
 * ROMs, disks, samples, backups, and software list entries. Supports drag-and-drop
 * for directory selection and context menus for managing entries.
 */
public class ScannerDirPanel extends JPanel {
    /** Preference key for the samples destination directory. */
    private static final String MAIN_FRAME_CHOOSE_SAMPLES_DESTINATION = "MainFrame.ChooseSamplesDestination";
    /** Preference key for the backup destination directory. */
    private static final String MAIN_FRAME_CHOOSE_BACKUP_DESTINATION = "MainFrame.ChooseBackupDestination";
    /** Preference key for the software list disks destination directory. */
    private static final String MAIN_FRAME_CHOOSE_SW_DISKS_DESTINATION = "MainFrame.ChooseSWDisksDestination";
    /** Preference key for the software list ROMs destination directory. */
    private static final String MAIN_FRAME_CHOOSE_SW_ROMS_DESTINATION = "MainFrame.ChooseSWRomsDestination";
    /** Preference key for the disks destination directory. */
    private static final String MAIN_FRAME_CHOOSE_DISKS_DESTINATION = "MainFrame.ChooseDisksDestination";
    /** Preference key for the ROMs destination directory. */
    private static final String MAIN_FRAME_CHOOSE_ROMS_DESTINATION = "MainFrame.ChooseRomsDestination";
    /** Preference key for the drop directory hint text. */
    private static final String MAIN_FRAME_DROP_DIR_HINT = "MainFrame.DropDirHint";
    /** Resource path for the disk icon. */
    private static final String ICONS_DISK = "/jrm/resicons/icons/disk.png";

    /** Checkbox to enable disk destination processing. */
    private JCheckBox lblDisksDest;
    /** Checkbox to enable samples destination processing. */
    private JCheckBox lblSamplesDest;
    /** Checkbox to enable backup destination processing. */
    private JCheckBox lblBackupDest;
    /** Checkbox to enable software list ROM destination processing. */
    private JCheckBox lblSWDest;
    /** Checkbox to enable software list disk destination processing. */
    private JCheckBox lblSWDisksDest;
    /** List of source directories to scan. */
    private JFileDropList listSrcDir;
    /** Context menu item to delete selected source directory entries. */
    private JMenuItem mntmDeleteSelected;
    /** Text field for the disk destination directory. */
    private JFileDropTextField tfDisksDest;
    /** Text field for the samples destination directory. */
    private JFileDropTextField tfSamplesDest;

    /** The tf samples dest. */
    private JFileDropTextField tfBackupDest;

    /** The tf SW dest. */
    private JFileDropTextField tfSWDest;

    /** The tf SW disks dest. */
    private JFileDropTextField tfSWDisksDest;

    /** The txt roms dest. */
    JFileDropTextField txtRomsDest;

    /** The btn roms dest. */
    JButton btnRomsDest;

    /**
     * Create the panel.
     */
    public ScannerDirPanel(final Session session) {
        final GridBagLayout gblScannerDirectories = new GridBagLayout();
        gblScannerDirectories.columnWidths = new int[] { 109, 65, 0, 0 };
        gblScannerDirectories.rowHeights = new int[] { 26, 0, 0, 0, 0, 0, 0 };
        gblScannerDirectories.columnWeights = new double[] { 0.0, 1.0, 0.0, Double.MIN_VALUE };
        gblScannerDirectories.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, Double.MIN_VALUE };
        this.setLayout(gblScannerDirectories);

        buildRomsDestDir(session);

        buildDisksDestDir(session);

        buildSWDestDir(session);

        buildSWDisksDestDir(session);

        buildSamplesDestDir(session);

        buildBackupDestDir(session);

        buildSrcDirs(session);

        JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuCanceled(final PopupMenuEvent e) {
                // do nothing
            }

            @Override
            public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
                // do nothing
            }

            @Override
            public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
                mntmDeleteSelected.setEnabled(!listSrcDir.getSelectedValuesList().isEmpty());
            }
        });
        Popup.addPopup(listSrcDir, popupMenu);

        mntmDeleteSelected = new JMenuItem(Messages.getString("MainFrame.mntmDeleteSelected.text")); //$NON-NLS-1$
        mntmDeleteSelected.addActionListener(_ -> listSrcDir.del(listSrcDir.getSelectedValuesList()));
        mntmDeleteSelected.setIcon(MainFrame.getIcon("/jrm/resicons/icons/folder_delete.png")); //$NON-NLS-1$
        popupMenu.add(mntmDeleteSelected);

        JMenuItem mntmAddDirectory = new JMenuItem(Messages.getString("MainFrame.mntmAddDirectory.text")); //$NON-NLS-1$
        mntmAddDirectory.addActionListener(_ -> {
            final File workdir = session.getUser().getSettings().getWorkPath().toFile(); // $NON-NLS-1$
            new JRMFileChooser<Boolean>(JFileChooser.OPEN_DIALOG, JFileChooser.DIRECTORIES_ONLY,
                    new File(session.getCurrProfile().getProperty("MainFrame.ChooseRomsSource", workdir.getAbsolutePath())), null, null, null, true) //$NON-NLS-1$
                    .show(SwingUtilities.getWindowAncestor(this), chooser -> {
                        session.getCurrProfile().setProperty("MainFrame.ChooseRomsSource", chooser.getCurrentDirectory().getAbsolutePath()); //$NON-NLS-1$
                        listSrcDir.add(chooser.getSelectedFiles());
                        return true;
                    });
        });
        mntmAddDirectory.setIcon(MainFrame.getIcon("/jrm/resicons/icons/folder_add.png")); //$NON-NLS-1$
        popupMenu.add(mntmAddDirectory);

    }

    /**
     * @param session
     */
    private void buildSrcDirs(final Session session) {
        JLabel lblSrcDir = new JLabel(Messages.getString("MainFrame.lblSrcDir.text")); //$NON-NLS-1$
        lblSrcDir.setHorizontalAlignment(SwingConstants.TRAILING);
        final GridBagConstraints gbcLblSrcDir = new GridBagConstraints();
        gbcLblSrcDir.fill = GridBagConstraints.HORIZONTAL;
        gbcLblSrcDir.anchor = GridBagConstraints.NORTH;
        gbcLblSrcDir.insets = new Insets(0, 0, 0, 5);
        gbcLblSrcDir.gridx = 0;
        gbcLblSrcDir.gridy = 6;
        this.add(lblSrcDir, gbcLblSrcDir);

        listSrcDir = new JFileDropList(
                files -> session.getCurrProfile().setProperty(ProfileSettingsEnum.src_dir, String.join("|", files.stream().map(File::getAbsolutePath).toList()))); // $NON-NLS-1$ //$NON-NLS-1$
                                                                                                                                                                   // //$NON-NLS-2$
                                                                                                                                                                   // //
                                                                                                                                                                   // $NON-NLS-2$
        listSrcDir.setMode(JFileDropMode.DIRECTORY);
        listSrcDir.setUI(new JListHintUI(Messages.getString(MAIN_FRAME_DROP_DIR_HINT), Color.gray)); // $NON-NLS-1$
        final GridBagConstraints gbcListSrcDir = new GridBagConstraints();
        gbcListSrcDir.insets = new Insets(0, 0, 5, 5);
        gbcListSrcDir.gridwidth = 2;
        gbcListSrcDir.fill = GridBagConstraints.BOTH;
        gbcListSrcDir.gridx = 1;
        gbcListSrcDir.gridy = 6;
        this.add(listSrcDir, gbcListSrcDir);
        listSrcDir.setBorder(new BevelBorder(BevelBorder.LOWERED, null, null, null, null));
    }

    /**
     * @param session
     * 
     * @throws HeadlessException
     */
    private void buildSWDisksDestDir(final Session session) throws HeadlessException {
        final var btnSWDisksDest = new JButton(""); //$NON-NLS-1$
        btnSWDisksDest.addActionListener(_ -> {
            final File workdir = session.getUser().getSettings().getWorkPath().toFile(); // $NON-NLS-1$
            new JRMFileChooser<Boolean>(JFileChooser.OPEN_DIALOG, JFileChooser.DIRECTORIES_ONLY,
                    new File(session.getCurrProfile().getProperty(MAIN_FRAME_CHOOSE_SW_DISKS_DESTINATION, workdir.getAbsolutePath())), new File(tfSWDisksDest.getText()), null,
                    Messages.getString(MAIN_FRAME_CHOOSE_SW_DISKS_DESTINATION), false).show(SwingUtilities.getWindowAncestor(this), chooser -> { // $NON-NLS-1$//$NON-NLS-2$
                        session.getCurrProfile().setProperty(MAIN_FRAME_CHOOSE_SW_DISKS_DESTINATION, chooser.getCurrentDirectory().getAbsolutePath()); // $NON-NLS-1$
                        tfSWDisksDest.setText(chooser.getSelectedFile().getAbsolutePath());
                        session.getCurrProfile().setProperty(ProfileSettingsEnum.swdisks_dest_dir, tfSWDisksDest.getText()); // $NON-NLS-1$
                        return true;
                    });
        });
        btnSWDisksDest.setEnabled(false);
        btnSWDisksDest.setIcon(MainFrame.getIcon(ICONS_DISK)); // $NON-NLS-1$

        lblSWDisksDest = new JCheckBox(Messages.getString("MainFrame.chckbxSwdisksdest.text")); //$NON-NLS-1$
        lblSWDisksDest.addItemListener(e -> {
            tfSWDisksDest.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
            btnSWDisksDest.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
            session.getCurrProfile().setProperty(ProfileSettingsEnum.swdisks_dest_dir_enabled, e.getStateChange() == ItemEvent.SELECTED); // $NON-NLS-1$
        });
        final GridBagConstraints gbcLblSWDisksDest = new GridBagConstraints();
        gbcLblSWDisksDest.insets = new Insets(0, 0, 5, 5);
        gbcLblSWDisksDest.gridx = 0;
        gbcLblSWDisksDest.gridy = 3;
        this.add(lblSWDisksDest, gbcLblSWDisksDest);

        tfSWDisksDest = new JFileDropTextField(txt -> session.getCurrProfile().setProperty(ProfileSettingsEnum.swdisks_dest_dir, txt)); // $NON-NLS-1$
        tfSWDisksDest.setMode(JFileDropMode.DIRECTORY);
        tfSWDisksDest.setEnabled(false);
        tfSWDisksDest.setUI(new JTextFieldHintUI(Messages.getString(MAIN_FRAME_DROP_DIR_HINT), Color.gray)); // $NON-NLS-1$
        tfSWDisksDest.setText(""); //$NON-NLS-1$
        final GridBagConstraints gbcTFSWDisksDest = new GridBagConstraints();
        gbcTFSWDisksDest.insets = new Insets(0, 0, 5, 0);
        gbcTFSWDisksDest.fill = GridBagConstraints.BOTH;
        gbcTFSWDisksDest.gridx = 1;
        gbcTFSWDisksDest.gridy = 3;
        this.add(tfSWDisksDest, gbcTFSWDisksDest);
        tfSWDisksDest.setColumns(10);

        final GridBagConstraints gbcBtnSWDisksDest = new GridBagConstraints();
        gbcBtnSWDisksDest.insets = new Insets(0, 0, 5, 5);
        gbcBtnSWDisksDest.gridx = 2;
        gbcBtnSWDisksDest.gridy = 3;
        this.add(btnSWDisksDest, gbcBtnSWDisksDest);
    }

    /**
     * @param session
     * 
     * @throws HeadlessException
     */
    private void buildSWDestDir(final Session session) throws HeadlessException {
        final var btnSWDest = new JButton(""); //$NON-NLS-1$
        btnSWDest.addActionListener(_ -> {
            final File workdir = session.getUser().getSettings().getWorkPath().toFile(); // $NON-NLS-1$
            new JRMFileChooser<Void>(JFileChooser.OPEN_DIALOG, JFileChooser.DIRECTORIES_ONLY,
                    new File(session.getCurrProfile().getProperty(MAIN_FRAME_CHOOSE_SW_ROMS_DESTINATION, workdir.getAbsolutePath())), new File(tfSWDest.getText()), null,
                    Messages.getString(MAIN_FRAME_CHOOSE_SW_ROMS_DESTINATION), false).show(SwingUtilities.getWindowAncestor(this), chooser -> { // $NON-NLS-1$//$NON-NLS-2$
                        session.getCurrProfile().setProperty(MAIN_FRAME_CHOOSE_SW_ROMS_DESTINATION, chooser.getCurrentDirectory().getAbsolutePath()); // $NON-NLS-1$
                        tfSWDest.setText(chooser.getSelectedFile().getAbsolutePath());
                        session.getCurrProfile().setProperty(ProfileSettingsEnum.swroms_dest_dir, tfSWDest.getText()); // $NON-NLS-1$
                        return null;
                    });
        });
        btnSWDest.setEnabled(false);
        btnSWDest.setIcon(MainFrame.getIcon(ICONS_DISK)); // $NON-NLS-1$

        lblSWDest = new JCheckBox(Messages.getString("MainFrame.chckbxSoftwareDest.text")); //$NON-NLS-1$
        lblSWDest.addItemListener(e -> {
            tfSWDest.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
            btnSWDest.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
            session.getCurrProfile().setProperty(ProfileSettingsEnum.swroms_dest_dir_enabled, e.getStateChange() == ItemEvent.SELECTED); // $NON-NLS-1$
        });
        lblSWDest.setHorizontalAlignment(SwingConstants.TRAILING);
        final GridBagConstraints gbcLblSWDest = new GridBagConstraints();
        gbcLblSWDest.fill = GridBagConstraints.HORIZONTAL;
        gbcLblSWDest.insets = new Insets(0, 0, 5, 5);
        gbcLblSWDest.gridx = 0;
        gbcLblSWDest.gridy = 2;
        this.add(lblSWDest, gbcLblSWDest);

        tfSWDest = new JFileDropTextField(txt -> session.getCurrProfile().setProperty(ProfileSettingsEnum.swroms_dest_dir, txt)); // $NON-NLS-1$
        tfSWDest.setMode(JFileDropMode.DIRECTORY);
        tfSWDest.setEnabled(false);
        tfSWDest.setUI(new JTextFieldHintUI(Messages.getString(MAIN_FRAME_DROP_DIR_HINT), Color.gray)); // $NON-NLS-1$
        tfSWDest.setText(""); //$NON-NLS-1$
        final GridBagConstraints gbcTFSWDest = new GridBagConstraints();
        gbcTFSWDest.insets = new Insets(0, 0, 5, 0);
        gbcTFSWDest.fill = GridBagConstraints.BOTH;
        gbcTFSWDest.gridx = 1;
        gbcTFSWDest.gridy = 2;
        this.add(tfSWDest, gbcTFSWDest);
        tfSWDest.setColumns(10);

        final GridBagConstraints gbcBtnSWDest = new GridBagConstraints();
        gbcBtnSWDest.insets = new Insets(0, 0, 5, 5);
        gbcBtnSWDest.gridx = 2;
        gbcBtnSWDest.gridy = 2;
        this.add(btnSWDest, gbcBtnSWDest);
    }

    /**
     * @param session
     * 
     * @throws HeadlessException
     */
    private void buildDisksDestDir(final Session session) throws HeadlessException {
        final var btnDisksDest = new JButton(""); //$NON-NLS-1$
        btnDisksDest.setEnabled(false);
        btnDisksDest.setIcon(MainFrame.getIcon(ICONS_DISK)); // $NON-NLS-1$
        btnDisksDest.addActionListener(_ -> {
            final File workdir = session.getUser().getSettings().getWorkPath().toFile(); // $NON-NLS-1$
            new JRMFileChooser<Void>(JFileChooser.OPEN_DIALOG, JFileChooser.DIRECTORIES_ONLY,
                    new File(session.getCurrProfile().getProperty(MAIN_FRAME_CHOOSE_DISKS_DESTINATION, workdir.getAbsolutePath())), new File(tfDisksDest.getText()), null,
                    Messages.getString(MAIN_FRAME_CHOOSE_DISKS_DESTINATION), false).show(SwingUtilities.getWindowAncestor(this), chooser -> { // $NON-NLS-1$//$NON-NLS-2$
                        session.getCurrProfile().setProperty(MAIN_FRAME_CHOOSE_DISKS_DESTINATION, chooser.getCurrentDirectory().getAbsolutePath()); // $NON-NLS-1$
                        tfDisksDest.setText(chooser.getSelectedFile().getAbsolutePath());
                        session.getCurrProfile().setProperty(ProfileSettingsEnum.disks_dest_dir, tfDisksDest.getText()); // $NON-NLS-1$
                        return null;
                    });
        });

        lblDisksDest = new JCheckBox(Messages.getString("MainFrame.lblDisksDest.text")); //$NON-NLS-1$
        lblDisksDest.addItemListener(e -> {
            tfDisksDest.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
            btnDisksDest.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
            session.getCurrProfile().setProperty(ProfileSettingsEnum.disks_dest_dir_enabled, e.getStateChange() == ItemEvent.SELECTED); // $NON-NLS-1$
        });
        lblDisksDest.setHorizontalAlignment(SwingConstants.TRAILING);
        final GridBagConstraints gbcLblDisksDest = new GridBagConstraints();
        gbcLblDisksDest.fill = GridBagConstraints.HORIZONTAL;
        gbcLblDisksDest.insets = new Insets(0, 0, 5, 5);
        gbcLblDisksDest.gridx = 0;
        gbcLblDisksDest.gridy = 1;
        this.add(lblDisksDest, gbcLblDisksDest);

        tfDisksDest = new JFileDropTextField(txt -> session.getCurrProfile().setProperty(ProfileSettingsEnum.disks_dest_dir, txt)); // $NON-NLS-1$
        tfDisksDest.setMode(JFileDropMode.DIRECTORY);
        tfDisksDest.setEnabled(false);
        tfDisksDest.setUI(new JTextFieldHintUI(Messages.getString(MAIN_FRAME_DROP_DIR_HINT), Color.gray)); // $NON-NLS-1$
        tfDisksDest.setText(""); //$NON-NLS-1$
        final GridBagConstraints gbcTFDisksDest = new GridBagConstraints();
        gbcTFDisksDest.insets = new Insets(0, 0, 5, 0);
        gbcTFDisksDest.fill = GridBagConstraints.BOTH;
        gbcTFDisksDest.gridx = 1;
        gbcTFDisksDest.gridy = 1;
        this.add(tfDisksDest, gbcTFDisksDest);
        tfDisksDest.setColumns(10);

        final GridBagConstraints gbcBtnDisksDest = new GridBagConstraints();
        gbcBtnDisksDest.insets = new Insets(0, 0, 5, 5);
        gbcBtnDisksDest.gridx = 2;
        gbcBtnDisksDest.gridy = 1;
        this.add(btnDisksDest, gbcBtnDisksDest);
    }

    /**
     * @param session
     * 
     * @throws HeadlessException
     */
    private void buildRomsDestDir(final Session session) throws HeadlessException {
        JLabel lblRomsDest = new JLabel(Messages.getString("MainFrame.lblRomsDest.text")); //$NON-NLS-1$
        lblRomsDest.setHorizontalAlignment(SwingConstants.TRAILING);
        final GridBagConstraints gbcLblRomsDest = new GridBagConstraints();
        gbcLblRomsDest.fill = GridBagConstraints.HORIZONTAL;
        gbcLblRomsDest.insets = new Insets(5, 0, 5, 5);
        gbcLblRomsDest.gridx = 0;
        gbcLblRomsDest.gridy = 0;
        this.add(lblRomsDest, gbcLblRomsDest);

        txtRomsDest = new JFileDropTextField(txt -> session.getCurrProfile().setProperty(ProfileSettingsEnum.roms_dest_dir, txt)); // $NON-NLS-1$
        txtRomsDest.setMode(JFileDropMode.DIRECTORY);
        txtRomsDest.setUI(new JTextFieldHintUI(Messages.getString(MAIN_FRAME_DROP_DIR_HINT), Color.gray)); // $NON-NLS-1$
        txtRomsDest.setColumns(10);
        final GridBagConstraints gbcTxtRomsDest = new GridBagConstraints();
        gbcTxtRomsDest.fill = GridBagConstraints.BOTH;
        gbcTxtRomsDest.insets = new Insets(5, 0, 5, 0);
        gbcTxtRomsDest.gridx = 1;
        gbcTxtRomsDest.gridy = 0;
        this.add(txtRomsDest, gbcTxtRomsDest);

        btnRomsDest = new JButton(""); //$NON-NLS-1$
        btnRomsDest.setIcon(MainFrame.getIcon(ICONS_DISK)); // $NON-NLS-1$
        btnRomsDest.addActionListener(_ -> {
            final File workdir = session.getUser().getSettings().getWorkPath().toFile(); // $NON-NLS-1$
            new JRMFileChooser<Void>(JFileChooser.OPEN_DIALOG, JFileChooser.DIRECTORIES_ONLY,
                    new File(session.getCurrProfile().getProperty(MAIN_FRAME_CHOOSE_ROMS_DESTINATION, workdir.getAbsolutePath())), new File(txtRomsDest.getText()), null,
                    Messages.getString(MAIN_FRAME_CHOOSE_ROMS_DESTINATION), false).show(SwingUtilities.getWindowAncestor(this), chooser -> { // $NON-NLS-1$
                                                                                                                                             // //$NON-NLS-2$
                        session.getCurrProfile().setProperty(MAIN_FRAME_CHOOSE_ROMS_DESTINATION, chooser.getCurrentDirectory().getAbsolutePath()); // $NON-NLS-1$
                        txtRomsDest.setText(chooser.getSelectedFile().getAbsolutePath());
                        session.getCurrProfile().setProperty(ProfileSettingsEnum.roms_dest_dir, txtRomsDest.getText()); // $NON-NLS-1$
                        return null;
                    });
        });
        final GridBagConstraints gbcBtnRomsDest = new GridBagConstraints();
        gbcBtnRomsDest.anchor = GridBagConstraints.NORTHWEST;
        gbcBtnRomsDest.insets = new Insets(5, 0, 5, 5);
        gbcBtnRomsDest.gridx = 2;
        gbcBtnRomsDest.gridy = 0;
        this.add(btnRomsDest, gbcBtnRomsDest);
    }

    /**
     * @param session
     * 
     * @throws HeadlessException
     */
    private void buildSamplesDestDir(final Session session) throws HeadlessException {
        final var btnSamplesDest = new JButton(""); //$NON-NLS-1$
        btnSamplesDest.addActionListener(_ -> {
            final File workdir = session.getUser().getSettings().getWorkPath().toFile(); // $NON-NLS-1$
            new JRMFileChooser<Boolean>(JFileChooser.OPEN_DIALOG, JFileChooser.DIRECTORIES_ONLY,
                    new File(session.getCurrProfile().getProperty(MAIN_FRAME_CHOOSE_SAMPLES_DESTINATION, workdir.getAbsolutePath())), new File(tfSamplesDest.getText()), null,
                    Messages.getString(MAIN_FRAME_CHOOSE_SAMPLES_DESTINATION), false).show(SwingUtilities.getWindowAncestor(this), chooser -> { // $NON-NLS-1$//$NON-NLS-2$
                        session.getCurrProfile().setProperty(MAIN_FRAME_CHOOSE_SAMPLES_DESTINATION, chooser.getCurrentDirectory().getAbsolutePath()); // $NON-NLS-1$
                        tfSamplesDest.setText(chooser.getSelectedFile().getAbsolutePath());
                        session.getCurrProfile().setProperty(ProfileSettingsEnum.samples_dest_dir, tfSamplesDest.getText()); // $NON-NLS-1$
                        return true;
                    });
        });
        btnSamplesDest.setEnabled(false);
        btnSamplesDest.setIcon(MainFrame.getIcon(ICONS_DISK)); // $NON-NLS-1$

        lblSamplesDest = new JCheckBox(Messages.getString("MainFrame.lblSamplesDest.text")); //$NON-NLS-1$
        lblSamplesDest.addItemListener(e -> {
            tfSamplesDest.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
            btnSamplesDest.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
            session.getCurrProfile().setProperty(ProfileSettingsEnum.samples_dest_dir_enabled, e.getStateChange() == ItemEvent.SELECTED); // $NON-NLS-1$
        });
        lblSamplesDest.setHorizontalAlignment(SwingConstants.TRAILING);
        final GridBagConstraints gbcLblSamplesDest = new GridBagConstraints();
        gbcLblSamplesDest.fill = GridBagConstraints.HORIZONTAL;
        gbcLblSamplesDest.insets = new Insets(0, 0, 5, 5);
        gbcLblSamplesDest.gridx = 0;
        gbcLblSamplesDest.gridy = 4;
        this.add(lblSamplesDest, gbcLblSamplesDest);

        tfSamplesDest = new JFileDropTextField(txt -> session.getCurrProfile().setProperty(ProfileSettingsEnum.samples_dest_dir, txt)); // $NON-NLS-1$
        tfSamplesDest.setMode(JFileDropMode.DIRECTORY);
        tfSamplesDest.setEnabled(false);
        tfSamplesDest.setUI(new JTextFieldHintUI(Messages.getString(MAIN_FRAME_DROP_DIR_HINT), Color.gray)); // $NON-NLS-1$
        tfSamplesDest.setText(""); //$NON-NLS-1$
        final GridBagConstraints gbcTFSamplesDest = new GridBagConstraints();
        gbcTFSamplesDest.insets = new Insets(0, 0, 5, 0);
        gbcTFSamplesDest.fill = GridBagConstraints.BOTH;
        gbcTFSamplesDest.gridx = 1;
        gbcTFSamplesDest.gridy = 4;
        this.add(tfSamplesDest, gbcTFSamplesDest);
        tfSamplesDest.setColumns(10);

        final GridBagConstraints gbcBtnSamplesDest = new GridBagConstraints();
        gbcBtnSamplesDest.insets = new Insets(0, 0, 5, 5);
        gbcBtnSamplesDest.gridx = 2;
        gbcBtnSamplesDest.gridy = 4;
        this.add(btnSamplesDest, gbcBtnSamplesDest);
    }

    /**
     * @param session
     * 
     * @throws HeadlessException
     */
    private void buildBackupDestDir(final Session session) throws HeadlessException {
        final var btnBackupDest = new JButton(""); //$NON-NLS-1$
        btnBackupDest.addActionListener(_ -> {
            final File workdir = session.getUser().getSettings().getWorkPath().toFile(); // $NON-NLS-1$
            new JRMFileChooser<Boolean>(JFileChooser.OPEN_DIALOG, JFileChooser.DIRECTORIES_ONLY,
                    new File(session.getCurrProfile().getProperty(MAIN_FRAME_CHOOSE_BACKUP_DESTINATION, workdir.getAbsolutePath())), new File(tfBackupDest.getText()), null,
                    Messages.getString(MAIN_FRAME_CHOOSE_BACKUP_DESTINATION), false).show(SwingUtilities.getWindowAncestor(this), chooser -> { // $NON-NLS-1$//$NON-NLS-2$
                        session.getCurrProfile().setProperty(MAIN_FRAME_CHOOSE_BACKUP_DESTINATION, chooser.getCurrentDirectory().getAbsolutePath()); // $NON-NLS-1$
                        tfBackupDest.setText(chooser.getSelectedFile().getAbsolutePath());
                        session.getCurrProfile().setProperty(ProfileSettingsEnum.backup_dest_dir, tfBackupDest.getText()); // $NON-NLS-1$
                        return true;
                    });
        });
        btnBackupDest.setEnabled(false);
        btnBackupDest.setIcon(MainFrame.getIcon(ICONS_DISK)); // $NON-NLS-1$

        lblBackupDest = new JCheckBox(Messages.getString("MainFrame.lblBackupDest.text")); //$NON-NLS-1$
        lblBackupDest.addItemListener(e -> {
            tfBackupDest.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
            btnBackupDest.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
            session.getCurrProfile().setProperty(ProfileSettingsEnum.backup_dest_dir_enabled, e.getStateChange() == ItemEvent.SELECTED); // $NON-NLS-1$
        });
        lblBackupDest.setHorizontalAlignment(SwingConstants.TRAILING);
        final GridBagConstraints gbcLblBackupDest = new GridBagConstraints();
        gbcLblBackupDest.fill = GridBagConstraints.HORIZONTAL;
        gbcLblBackupDest.insets = new Insets(0, 0, 5, 5);
        gbcLblBackupDest.gridx = 0;
        gbcLblBackupDest.gridy = 5;
        this.add(lblBackupDest, gbcLblBackupDest);

        tfBackupDest = new JFileDropTextField(txt -> session.getCurrProfile().setProperty(ProfileSettingsEnum.backup_dest_dir, txt)); // $NON-NLS-1$
        tfBackupDest.setMode(JFileDropMode.DIRECTORY);
        tfBackupDest.setEnabled(false);
        tfBackupDest.setUI(new JTextFieldHintUI(Messages.getString(MAIN_FRAME_DROP_DIR_HINT), Color.gray)); // $NON-NLS-1$
        tfBackupDest.setText(""); //$NON-NLS-1$
        final GridBagConstraints gbcTFBackupDest = new GridBagConstraints();
        gbcTFBackupDest.insets = new Insets(0, 0, 5, 0);
        gbcTFBackupDest.fill = GridBagConstraints.BOTH;
        gbcTFBackupDest.gridx = 1;
        gbcTFBackupDest.gridy = 5;
        this.add(tfBackupDest, gbcTFBackupDest);
        tfBackupDest.setColumns(10);

        final GridBagConstraints gbcBtnBackupDest = new GridBagConstraints();
        gbcBtnBackupDest.insets = new Insets(0, 0, 5, 5);
        gbcBtnBackupDest.gridx = 2;
        gbcBtnBackupDest.gridy = 5;
        this.add(btnBackupDest, gbcBtnBackupDest);
    }

    public void initProfileSettings(final Session session) {
        txtRomsDest.setText(session.getCurrProfile().getProperty(ProfileSettingsEnum.roms_dest_dir)); // $NON-NLS-1$ //$NON-NLS-2$
        lblDisksDest.setSelected(session.getCurrProfile().getProperty(ProfileSettingsEnum.disks_dest_dir_enabled, Boolean.class)); // $NON-NLS-1$
        tfDisksDest.setText(session.getCurrProfile().getProperty(ProfileSettingsEnum.disks_dest_dir)); // $NON-NLS-1$ //$NON-NLS-2$
        lblSWDest.setSelected(session.getCurrProfile().getProperty(ProfileSettingsEnum.swroms_dest_dir_enabled, Boolean.class)); // $NON-NLS-1$
        tfSWDest.setText(session.getCurrProfile().getProperty(ProfileSettingsEnum.swroms_dest_dir)); // $NON-NLS-1$ //$NON-NLS-2$
        lblSWDisksDest.setSelected(session.getCurrProfile().getProperty(ProfileSettingsEnum.swdisks_dest_dir_enabled, Boolean.class)); // $NON-NLS-1$
        tfSWDisksDest.setText(session.getCurrProfile().getProperty(ProfileSettingsEnum.swdisks_dest_dir)); // $NON-NLS-1$
                                                                                                           // //$NON-NLS-2$
        lblSamplesDest.setSelected(session.getCurrProfile().getProperty(ProfileSettingsEnum.samples_dest_dir_enabled, Boolean.class)); // $NON-NLS-1$
        tfSamplesDest.setText(session.getCurrProfile().getProperty(ProfileSettingsEnum.samples_dest_dir)); // $NON-NLS-1$
                                                                                                           // //$NON-NLS-2$
        lblBackupDest.setSelected(session.getCurrProfile().getProperty(ProfileSettingsEnum.backup_dest_dir_enabled, Boolean.class)); // $NON-NLS-1$
        tfBackupDest.setText(session.getCurrProfile().getProperty(ProfileSettingsEnum.backup_dest_dir)); // $NON-NLS-1$
                                                                                                         // //$NON-NLS-2$
        listSrcDir.getModel().removeAllElements();
        for (final String s : StringUtils.split(session.getCurrProfile().getProperty(ProfileSettingsEnum.src_dir), '|')) // $NON-NLS-1$
                                                                                                                         // //$NON-NLS-2$
                                                                                                                         // //$NON-NLS-3$
            if (!s.isEmpty())
                listSrcDir.getModel().addElement(new File(s));

    }

}
