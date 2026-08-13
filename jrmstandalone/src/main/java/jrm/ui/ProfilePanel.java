package jrm.ui;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableColumn;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.profile.manager.Dir;
import jrm.profile.manager.Import;
import jrm.profile.manager.MameExecutable;
import jrm.profile.manager.ProfileNFO;
import jrm.profile.manager.ProfileNFOMame.MameStatus;
import jrm.security.Session;
import jrm.ui.basic.JRMFileChooser;
import jrm.ui.basic.JRMFileChooser.OneRootFileSystemView;
import jrm.ui.basic.Popup;
import jrm.ui.profile.manager.DirNode;
import jrm.ui.profile.manager.DirTreeCellEditor;
import jrm.ui.profile.manager.DirTreeCellRenderer;
import jrm.ui.profile.manager.DirTreeModel;
import jrm.ui.profile.manager.DirTreeSelectionListener;
import jrm.ui.profile.manager.FileTableCellRenderer;
import jrm.ui.profile.manager.FileTableModel;
import jrm.ui.progress.SwingWorkerProgress;
import lombok.val;

/**
 * Panel for managing profiles in the profile manager.
 * <p>
 * Provides a split-pane interface with a directory tree on the left and a profile
 * file table on the right. Supports creating/deleting directories, importing profiles,
 * renaming profiles, updating from MAME, and managing profile caches.
 */
@SuppressWarnings("serial")
public class ProfilePanel extends JPanel {
    /** Message key for the file chooser dialog when importing profiles. */
    private static final String MAIN_FRAME_CHOOSE_EXE_OR_DAT_TO_IMPORT = "MainFrame.ChooseExeOrDatToImport";

    /** Message key for the "Update from MAME" menu item text. */
    private static final String MAIN_FRAME_MNTM_UPDATE_FROM_MAME_TEXT = "MainFrame.mntmUpdateFromMame.text";

    /** Menu item for creating a new folder in the directory tree. */
    private JMenuItem mntmCreateFolder;

    /** Menu item for deleting the selected folder from the directory tree. */
    private JMenuItem mntmDeleteFolder;

    /** Menu item for deleting the selected profile. */
    private JMenuItem mntmDeleteProfile;

    /** Menu item for dropping the profile cache file. */
    private JMenuItem mntmDropCache;

    /** Menu item for renaming the selected profile. */
    private JMenuItem mntmRenameProfile;

    /** Menu item for updating the profile from MAME. */
    private JMenuItem mntmUpdateFromMame;

    /** Table displaying profile files in the selected directory. */
    private JTable profilesList;

    /** Scroll pane containing the directory tree. */
    private JScrollPane scrollPaneTree;

    /** Tree displaying the directory structure for profile files. */
    private JTree profilesTree;

    /** Callback interface for loading profiles into the UI. */
    private transient ProfileLoader profileLoader;

    /**
     * Constructs a new profile panel.
     *
     * @param session the security session for accessing profile data
     */
    public ProfilePanel(final Session session) {
        final var gblProfilesTab = new GridBagLayout();
        gblProfilesTab.columnWidths = new int[] { 0, 0 };
        gblProfilesTab.rowHeights = new int[] { 0, 0, 0 };
        gblProfilesTab.columnWeights = new double[] { 1.0, Double.MIN_VALUE };
        gblProfilesTab.rowWeights = new double[] { 1.0, 0.0, Double.MIN_VALUE };
        this.setLayout(gblProfilesTab);

        final var profilesPanel = new JSplitPane();
        profilesPanel.setContinuousLayout(true);
        profilesPanel.setResizeWeight(0.2);
        profilesPanel.setOneTouchExpandable(true);
        final var gbcProfilesPanel = new GridBagConstraints();
        gbcProfilesPanel.insets = new Insets(0, 0, 5, 0);
        gbcProfilesPanel.fill = GridBagConstraints.BOTH;
        gbcProfilesPanel.gridx = 0;
        gbcProfilesPanel.gridy = 0;
        this.add(profilesPanel, gbcProfilesPanel);

        final var scrollPaneList = new JScrollPane();
        profilesPanel.setRightComponent(scrollPaneList);

        profilesList = new JTable();
        final DefaultCellEditor editor = (DefaultCellEditor) profilesList.getDefaultEditor(Object.class);
        editor.setClickCountToStart(3);
        profilesList.setShowVerticalLines(false);
        profilesList.setShowHorizontalLines(false);
        profilesList.setShowGrid(false);
        profilesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profilesList.setPreferredScrollableViewportSize(new Dimension(400, 300));
        profilesList.setFillsViewportHeight(true);
        scrollPaneList.setViewportView(profilesList);
        final var filemodel = new FileTableModel();
        profilesList.setModel(filemodel);
        for (var i = 0; i < profilesList.getColumnCount(); i++) {
            final TableColumn column = profilesList.getColumnModel().getColumn(i);
            column.setCellRenderer(new FileTableCellRenderer());
            if (FileTableModel.getColumnsWidths()[i] >= 0) {
                column.setPreferredWidth(FileTableModel.getColumnsWidths()[i]);
            } else {
                final var format = "%0" + (-FileTableModel.getColumnsWidths()[i]) + "d";
                final int width = profilesList.getFontMetrics(profilesList.getFont()).stringWidth(String.format(format, 0)); // $NON-NLS-1$
                                                                                                                             // //$NON-NLS-2$
                column.setMinWidth(width);
                column.setMaxWidth(width);
            }
        }
        profilesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                if (e.getClickCount() == 2) {
                    loadProfile(session, filemodel);
                }
            }

        });

        scrollPaneTree = new JScrollPane();
        scrollPaneTree.setMinimumSize(new Dimension(80, 22));
        profilesPanel.setLeftComponent(scrollPaneTree);

        profilesTree = new JTree();
        scrollPaneTree.setViewportView(profilesTree);

        final var profilesTreeModel = new DirTreeModel(new DirNode(session.getUser().getSettings().getWorkPath().resolve("xmlfiles").toAbsolutePath().normalize().toFile())); //$NON-NLS-1$
        profilesTree.setModel(profilesTreeModel);
        profilesTree.setRootVisible(true);
        profilesTree.setShowsRootHandles(true);
        profilesTree.setEditable(true);
        final var profilesTreeRenderer = new DirTreeCellRenderer();
        profilesTree.setCellRenderer(profilesTreeRenderer);
        profilesTree.setCellEditor(new DirTreeCellEditor(profilesTree, profilesTreeRenderer));
        profilesTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        profilesTree.addTreeSelectionListener(new DirTreeSelectionListener(session, profilesList));

        final var popupMenuList = new JPopupMenu();
        popupMenuList.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuCanceled(final PopupMenuEvent e) {
                // not used
            }

            @Override
            public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
                // not used
            }

            @Override
            public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
                mntmDeleteProfile.setEnabled(profilesList.getSelectedRowCount() > 0);
                mntmRenameProfile.setEnabled(profilesList.getSelectedRowCount() > 0);
                mntmDropCache.setEnabled(profilesList.getSelectedRowCount() > 0);
                mntmUpdateFromMame.setEnabled(profilesList.getSelectedRowCount() > 0
                        && EnumSet.of(MameStatus.NEEDUPDATE, MameStatus.NOTFOUND).contains(filemodel.getNfoAt(profilesList.getSelectedRow()).getMame().getStatus()));
                if (profilesList.getSelectedRowCount() > 0)
                    mntmUpdateFromMame.setText(Messages.getString(MAIN_FRAME_MNTM_UPDATE_FROM_MAME_TEXT) + " (" //$NON-NLS-1$
                            + filemodel.getNfoAt(profilesList.getSelectedRow()).getMame().getStatus().getMsg() + ")"); //$NON-NLS-1$ //$NON-NLS-3$
                else
                    mntmUpdateFromMame.setText(Messages.getString(MAIN_FRAME_MNTM_UPDATE_FROM_MAME_TEXT)); // $NON-NLS-1$
            }
        });
        Popup.addPopup(profilesList, popupMenuList);

        mntmDeleteProfile = new JMenuItem(Messages.getString("MainFrame.mntmDeleteProfile.text")); //$NON-NLS-1$
        mntmDeleteProfile.addActionListener(_ -> deleteProfile(session, filemodel));
        mntmDeleteProfile.setIcon(MainFrame.getIcon("/jrm/resicons/icons/script_delete.png")); //$NON-NLS-1$
        popupMenuList.add(mntmDeleteProfile);

        mntmRenameProfile = new JMenuItem(Messages.getString("MainFrame.mntmRenameProfile.text")); //$NON-NLS-1$
        mntmRenameProfile.addActionListener(_ -> renameProfile());
        mntmRenameProfile.setIcon(MainFrame.getIcon("/jrm/resicons/icons/script_edit.png")); //$NON-NLS-1$
        popupMenuList.add(mntmRenameProfile);

        mntmDropCache = new JMenuItem(Messages.getString("MainFrame.mntmDropCache.text")); //$NON-NLS-1$
        mntmDropCache.addActionListener(_ -> dropCache(filemodel));
        mntmDropCache.setIcon(MainFrame.getIcon("/jrm/resicons/icons/bin.png")); //$NON-NLS-1$
        popupMenuList.add(mntmDropCache);

        final var separator = new JSeparator();
        popupMenuList.add(separator);

        mntmUpdateFromMame = new JMenuItem(Messages.getString(MAIN_FRAME_MNTM_UPDATE_FROM_MAME_TEXT)); // $NON-NLS-1$
        mntmUpdateFromMame.addActionListener(_ -> updateFromMame(session, filemodel));
        popupMenuList.add(mntmUpdateFromMame);
        profilesTree.setSelectionRow(0);

        final var popupMenuTree = new JPopupMenu();
        popupMenuTree.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuCanceled(final PopupMenuEvent e) {
                // not used
            }

            @Override
            public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
                // not used
            }

            @Override
            public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
                mntmCreateFolder.setEnabled(profilesTree.getSelectionCount() > 0);
                mntmDeleteFolder.setEnabled(profilesTree.getSelectionCount() > 0 && !((DirNode) profilesTree.getLastSelectedPathComponent()).isRoot());
            }
        });
        Popup.addPopup(profilesTree, popupMenuTree);

        mntmCreateFolder = new JMenuItem(Messages.getString("MainFrame.mntmCreateFolder.text")); //$NON-NLS-1$
        mntmCreateFolder.addActionListener(_ -> createFolder(profilesTreeModel));
        mntmCreateFolder.setIcon(MainFrame.getIcon("/jrm/resicons/icons/folder_add.png")); //$NON-NLS-1$
        popupMenuTree.add(mntmCreateFolder);

        mntmDeleteFolder = new JMenuItem(Messages.getString("MainFrame.mntmDeleteFolder.text")); //$NON-NLS-1$
        mntmDeleteFolder.addActionListener(_ -> deleteFolder(profilesTreeModel));
        mntmDeleteFolder.setIcon(MainFrame.getIcon("/jrm/resicons/icons/folder_delete.png")); //$NON-NLS-1$
        popupMenuTree.add(mntmDeleteFolder);

        final var profilesBtnPanel = new JPanel();
        final var gbcProfilesBtnPanel = new GridBagConstraints();
        gbcProfilesBtnPanel.fill = GridBagConstraints.HORIZONTAL;
        gbcProfilesBtnPanel.gridx = 0;
        gbcProfilesBtnPanel.gridy = 1;
        this.add(profilesBtnPanel, gbcProfilesBtnPanel);

        final var btnLoadProfile = new JButton(Messages.getString("MainFrame.btnLoadProfile.text")); //$NON-NLS-1$
        btnLoadProfile.setIcon(MainFrame.getIcon("/jrm/resicons/icons/add.png")); //$NON-NLS-1$
        btnLoadProfile.setEnabled(false);
        btnLoadProfile.addActionListener(_ -> loadProfile(session, (FileTableModel) profilesList.getModel()));
        profilesBtnPanel.add(btnLoadProfile);

        final var btnImportDat = new JButton(Messages.getString("MainFrame.btnImportDat.text")); //$NON-NLS-1$
        btnImportDat.setIcon(MainFrame.getIcon("/jrm/resicons/icons/script_go.png")); //$NON-NLS-1$
        btnImportDat.addActionListener(_ -> importDat(session, false));
        profilesBtnPanel.add(btnImportDat);

        final var btnImportSL = new JButton(Messages.getString("MainFrame.btnImportSL.text")); //$NON-NLS-1$
        btnImportSL.addActionListener(_ -> importDat(session, true));
        btnImportSL.setIcon(MainFrame.getIcon("/jrm/resicons/icons/application_go.png")); //$NON-NLS-1$
        profilesBtnPanel.add(btnImportSL);

        profilesList.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting())
                btnLoadProfile.setEnabled(profilesList.getSelectedColumnCount() != 0);
        });
    }

    /**
     * Deletes the selected folder from the directory tree.
     *
     * @param profilesTreeModel the tree model containing the directory structure
     */
    private void deleteFolder(final DirTreeModel profilesTreeModel) {
        final DirNode selectedNode = (DirNode) profilesTree.getLastSelectedPathComponent();
        if (selectedNode != null) {
            final DirNode parent = (DirNode) selectedNode.getParent();
            profilesTreeModel.removeNodeFromParent(selectedNode);
            final var path = new TreePath(parent.getPath());
            profilesTree.setSelectionPath(path);
        }
    }

    /**
     * Creates a new folder under the selected directory tree node.
     *
     * @param profilesTreeModel the tree model containing the directory structure
     */
    private void createFolder(final DirTreeModel profilesTreeModel) {
        final DirNode selectedNode = (DirNode) profilesTree.getLastSelectedPathComponent();
        if (selectedNode != null) {
            final var newnode = new DirNode(new Dir(new File(selectedNode.getDir().getFile(), Messages.getString("MainFrame.NewFolder")))); //$NON-NLS-1$
            selectedNode.add(newnode);
            profilesTreeModel.reload(selectedNode);
            final var path = new TreePath(newnode.getPath());
            profilesTree.setSelectionPath(path);
            profilesTree.startEditingAtPath(path);
        }
    }

    /**
     * Deletes the cache file associated with the selected profile.
     *
     * @param filemodel the table model containing profile file information
     */
    private void dropCache(final FileTableModel filemodel) {
        final int row = profilesList.getSelectedRow();
        if (row >= 0)
            try {
                Files.deleteIfExists(Paths.get(filemodel.getFileAt(row).getAbsolutePath() + ".cache"));
            } catch (IOException e1) {
                Log.err(e1.getMessage(), e1);
            }
    }

    /**
     * Initiates in-place renaming of the selected profile by entering edit mode.
     */
    private void renameProfile() {
        final int row = profilesList.getSelectedRow();
        if (row >= 0)
            profilesList.editCellAt(row, 0);
    }

    /**
     * Deletes the selected profile from the file system.
     *
     * @param session the current session
     * @param filemodel the table model containing profile file information
     */
    private void deleteProfile(final Session session, final FileTableModel filemodel) {
        final int row = profilesList.getSelectedRow();
        if (row >= 0) {
            final ProfileNFO nfo = filemodel.getNfoAt(row);
            if ((session.getCurrProfile() == null || !session.getCurrProfile().getNfo().equals(nfo)) && nfo.delete())
                filemodel.populate(session);
        }
    }

    /**
     * Updates the selected profile's MAME data, prompting for relocation if needed.
     *
     * @param session the current session
     * @param filemodel the table model containing profile file information
     */
    private void updateFromMame(final Session session, final FileTableModel filemodel) {
        final int row = profilesList.getSelectedRow();
        if (row >= 0) {
            try {
                final ProfileNFO nfo = filemodel.getNfoAt(row);
                if (nfo.getMame().getStatus() == MameStatus.NEEDUPDATE
                        || (nfo.getMame().getStatus() == MameStatus.NOTFOUND && new JRMFileChooser<MameStatus>(JFileChooser.OPEN_DIALOG, JFileChooser.FILES_ONLY, null,
                                nfo.getMame().getFile(), null, Messages.getString("MainFrame.ChooseMameNewLocation"), false)
                                .show(SwingUtilities.getWindowAncestor(this), chooser -> updateFromMameRelocate(nfo, chooser)) == MameStatus.NEEDUPDATE)) {
                    new SwingWorkerProgress<Import, Void>(SwingUtilities.getWindowAncestor(this)) {

                        @Override
                        protected Import doInBackground() throws Exception {
                            return new Import(session, nfo.getMame().getFile(), nfo.getMame().isSL(), this);
                        }

                        @Override
                        protected void done() {
                            try {
                                updateFromMame(session, nfo, get());
                                close();
                            } catch (InterruptedException e) {
                                Log.err(e.getMessage(), e);
                                Thread.currentThread().interrupt();
                            } catch (ExecutionException | IOException e) {
                                Log.err(e.getMessage(), e);
                            }
                        }
                    }.execute();
                }
            } catch (final Exception e1) {
                Log.err(e1.getMessage(), e1);
            }
        }
    }

    /**
     * Handles relocation of the MAME executable when the original location is not found.
     *
     * @param nfo the profile NFO containing MAME configuration
     * @param chooser the file chooser for selecting the new MAME location
     * @return the updated MAME status after relocation attempt
     */
    private MameStatus updateFromMameRelocate(final ProfileNFO nfo, JRMFileChooser<MameStatus> chooser) {
        if (chooser.getSelectedFile().exists())
            return nfo.getMame().relocate(chooser.getSelectedFile());
        return MameStatus.NOTFOUND;
    }

    /**
     * Imports a DAT file into the profile system.
     *
     * @param session the current session
     * @param sl true if importing a software list DAT, false for regular DAT
     */
    private void importDat(final Session session, final boolean sl) {
        final List<FileFilter> filters = Arrays.asList(new ImportDatFileFilter(), new FileNameExtensionFilter(Messages.getString("MainFrame.DatFile"), "dat", "xml"));
        new JRMFileChooser<Void>(JFileChooser.OPEN_DIALOG, JFileChooser.FILES_ONLY,
                Optional.ofNullable(session.getUser().getSettings().getProperty(MAIN_FRAME_CHOOSE_EXE_OR_DAT_TO_IMPORT, (String) null)).map(File::new).orElse(null), null, filters,
                Messages.getString(MAIN_FRAME_CHOOSE_EXE_OR_DAT_TO_IMPORT), true).show(SwingUtilities.getWindowAncestor(this), chooser -> {
                    importDat(session, sl, chooser);
                    return null;
                });
    }

    /**
     * Processes the DAT file import using the provided file chooser.
     *
     * @param session the current session
     * @param sl true if importing a software list DAT, false for regular DAT
     * @param chooser the file chooser containing the selected DAT file
     */
    private void importDat(final Session session, final boolean sl, JRMFileChooser<Void> chooser) {
        new SwingWorkerProgress<Void, Import>(SwingUtilities.getWindowAncestor(this)) {
            @Override
            protected Void doInBackground() throws Exception {
                canCancel(false);
                session.getUser().getSettings().setProperty(MAIN_FRAME_CHOOSE_EXE_OR_DAT_TO_IMPORT, chooser.getCurrentDirectory().getAbsolutePath()); // $NON-NLS-1$
                for (final File selectedfile : chooser.getSelectedFiles()) {
                    setProgress(Messages.getString("MainFrame.ImportingFromMame"), -1); //$NON-NLS-1$
                    publish(new Import(session, selectedfile, sl, this));
                }
                return null;
            }

            @Override
            protected void process(List<Import> imprts) {
                importDat(session, sl, imprts);
            }

            @Override
            protected void done() {
                close();
            }
        }.execute();
    }

    /**
     * Gets the profile loader callback.
     *
     * @return the profile loader callback
     */
    public ProfileLoader getProfileLoader() {
        return profileLoader;
    }

    /**
     * Sets the profile loader callback.
     *
     * @param profileLoader the profile loader callback to set
     */
    public void setProfileLoader(ProfileLoader profileLoader) {
        this.profileLoader = profileLoader;
    }

    /**
     * Loads the selected profile into the application.
     *
     * @param session the current session
     * @param filemodel the table model containing profile file information
     */
    private void loadProfile(final Session session, final FileTableModel filemodel) {
        final int row = profilesList.getSelectedRow();
        if (row >= 0) {
            getProfileLoader().loadProfile(session, filemodel.getNfoAt(row));
        }
    }

    /**
     * Generates a unique filename by appending a numeric suffix.
     *
     * @param file the original file to rename
     * @return a new file with a unique name
     * @throws IllegalArgumentException if unable to generate a unique name
     */
    private File autoRenameFile(File file) throws IllegalArgumentException {
        for (var i = 1;; i++) {
            final var testFile = new File(file.getParentFile(), FilenameUtils.getBaseName(file.getName()) + '_' + i + '.' + FilenameUtils.getExtension(file.getName()));
            if (!testFile.exists())
                return testFile;
        }
    }

    /**
     * Prompts the user when a DAT file already exists at the target location.
     *
     * @param file reference to the file that may need renaming
     * @return the user's choice: 0=Overwrite, 1=Auto Rename, 2=File Chooser, 3=Cancel
     * @throws HeadlessException if the operation requires a display that is not available
     * @throws IllegalArgumentException if unable to generate a unique filename
     */
    private int importDatExistsChoose(AtomicReference<File> file) throws HeadlessException, IllegalArgumentException {
        int mode = -1;
        if (file.get().exists()) {
            final var options = new String[] { "Overwrite", "Auto Rename", "File Chooser", "Cancel" };
            mode = JOptionPane.showOptionDialog(ProfilePanel.this, "File already exists, choose what to do", "File already exists", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
            if (mode == 1)
                file.set(autoRenameFile(file.get()));
        }
        return mode;
    }

    /**
     * Processes the imported DAT file and updates the profile tree.
     *
     * @param session the current session
     * @param sl true if importing a software list DAT, false for regular DAT
     * @param imprt the import operation containing the DAT file
     * @param file the target file location
     * @return null (placeholder for SwingWorker compatibility)
     */
    private Void importDat(final Session session, final boolean sl, final jrm.profile.manager.Import imprt, final File file) {
        try {
            final var parent = file.getParentFile();
            FileUtils.copyFile(imprt.getFile(), file);
            if (imprt.isMame()) {
                final var pnfo = ProfileNFO.load(session, file);
                pnfo.getMame().set(imprt.getOrgFile(), sl);
                if (imprt.getRomsFile() != null) {
                    FileUtils.copyFileToDirectory(imprt.getRomsFile(), parent);
                    pnfo.getMame().setFileroms(new File(parent, imprt.getRomsFile().getName()));
                    if (imprt.getSlFile() != null) {
                        FileUtils.copyFileToDirectory(imprt.getSlFile(), parent);
                        pnfo.getMame().setFilesl(new File(parent, imprt.getSlFile().getName()));
                    }
                }
                pnfo.save(session);
            }
            final var model = (DirTreeModel) profilesTree.getModel();
            final var root = (DirNode) model.getRoot();
            DirNode theNode = root.find(parent);
            if (theNode != null) {
                theNode.reload();
                model.reload(theNode);
                if ((theNode = root.find(parent)) != null) {
                    profilesTree.clearSelection();
                    profilesTree.setSelectionPath(new TreePath(model.getPathToRoot(theNode)));
                } else
                    Log.err(Messages.getString("MainFrame.FinalNodeNotFound")); //$NON-NLS-1$
            } else
                Log.err(Messages.getString("MainFrame.NodeNotFound")); //$NON-NLS-1$
        } catch (final IOException e) {
            Log.err(e.getMessage(), e);
        }
        return null;
    }

    /**
     * @param session
     * @param nfo
     * @param imprt
     * 
     * @throws IOException
     */
    private void updateFromMame(final Session session, final ProfileNFO nfo, Import imprt) throws IOException {
        nfo.getMame().deleteAlongside(nfo.getFile());
        nfo.getMame().setFileroms(new File(nfo.getFile().getParentFile(), imprt.getRomsFile().getName()));
        Files.copy(imprt.getRomsFile().toPath(), nfo.getMame().getFileroms().toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
        if (nfo.getMame().isSL()) {
            nfo.getMame().setFilesl(new File(nfo.getFile().getParentFile(), imprt.getSlFile().getName()));
            Files.copy(imprt.getSlFile().toPath(), nfo.getMame().getFilesl().toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
        }
        nfo.getMame().setUpdated();
        nfo.getStats().reset();
        nfo.save(session);
    }

    /**
     * @param session
     * @param sl
     * @param imprts
     * 
     * @throws HeadlessException
     * @throws IllegalArgumentException
     */
    private void importDat(final Session session, final boolean sl, List<Import> imprts) throws HeadlessException, IllegalArgumentException {
        for (val imprt : imprts) {
            if (imprt.getFile() == null)
                continue;
            if (!imprt.isMame()) {
                final var currDir = ((FileTableModel) profilesList.getModel()).getCurrDir().getFile();
                var fileRef = new AtomicReference<File>(new File(currDir, imprt.getFile().getName()));
                int mode = importDatExistsChoose(fileRef);
                if (mode == 3)
                    continue;
                if (!fileRef.get().exists() || mode == 0) {
                    try {
                        FileUtils.copyFile(imprt.getFile(), fileRef.get());
                        ((FileTableModel) profilesList.getModel()).populate(session);
                    } catch (IOException e) {
                        Log.err(e.getMessage(), e);
                    }
                }
            }
            final var workdir = session.getUser().getSettings().getWorkPath().toFile(); // $NON-NLS-1$
            final var xmldir = new File(workdir, "xmlfiles"); //$NON-NLS-1$
            new JRMFileChooser<Void>(new OneRootFileSystemView(xmldir))
                    .setup(JFileChooser.SAVE_DIALOG, JFileChooser.FILES_ONLY, null, new File(xmldir, imprt.getFile().getName()),
                            Collections.singletonList(new FileNameExtensionFilter(Messages.getString("MainFrame.DatFile"), "dat", "xml", "jrm")),
                            Messages.getString("MainFrame.ChooseFileName"), false)
                    .show(SwingUtilities.getWindowAncestor(ProfilePanel.this), chooser1 -> importDat(session, sl, imprt, chooser1.getSelectedFile()));
        }
    }

    private static final class ImportDatFileFilter extends FileFilter {
        @Override
        public boolean accept(final File f) {
            return f.isDirectory() || MameExecutable.isLaunchable(f);
        }

        @Override
        public String getDescription() {
            return Messages.getString("MainFrame.MameExecutable"); //$NON-NLS-1$
        }
    }

}
