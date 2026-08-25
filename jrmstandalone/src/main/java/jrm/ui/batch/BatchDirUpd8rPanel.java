package jrm.ui.batch;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nonnull;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TableModelEvent;
import javax.swing.filechooser.FileFilter;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import jrm.JRomManager;
import jrm.aui.basic.AbstractSrcDstResult;
import jrm.aui.basic.SDRList;
import jrm.aui.basic.SrcDstResult;
import jrm.batch.DirUpdater;
import jrm.batch.DirUpdaterResults;
import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.misc.ProfileSettings;
import jrm.misc.SettingsEnum;
import jrm.security.PathAbstractor;
import jrm.security.Session;
import jrm.ui.MainFrame;
import jrm.ui.basic.JFileDropList;
import jrm.ui.basic.JFileDropMode;
import jrm.ui.basic.JListHintUI;
import jrm.ui.basic.JRMFileChooser;
import jrm.ui.basic.JSDRDropTable;
import jrm.ui.basic.Popup;
import jrm.ui.basic.SDRTableModel;
import jrm.ui.progress.SwingWorkerProgress;
import one.util.streamex.StreamEx;

/**
 * Panel for batch directory update operations.
 * <p>
 * Provides controls for updating directories from DAT/XML files.
 */
public class BatchDirUpd8rPanel extends JPanel {
    /** List of source DAT/XML files. */
    private JFileDropList listBatchToolsDat2DirSrc;
    /** Table for batch directory update results. */
    private JSDRDropTable tableBatchToolsDat2Dir;
    /** Menu for DAT-to-directory presets. */
    private JMenu mnDat2DirPresets;
    /** Position for popup menu. */
    private Point popupPoint;

    /**
     * Constructs the batch directory update panel.
     *
     * @param session the user session for accessing settings
     */
    public BatchDirUpd8rPanel(final @Nonnull Session session) {
        final GridBagLayout gblPanelBatchToolsDat2Dir = new GridBagLayout();
        gblPanelBatchToolsDat2Dir.columnWidths = new int[] { 0, 0, 0, 0 };
        gblPanelBatchToolsDat2Dir.rowHeights = new int[] { 0, 0, 0 };
        gblPanelBatchToolsDat2Dir.columnWeights = new double[] { 1.0, 0.0, 0.0, Double.MIN_VALUE };
        gblPanelBatchToolsDat2Dir.rowWeights = new double[] { 1.0, 0.0, Double.MIN_VALUE };
        this.setLayout(gblPanelBatchToolsDat2Dir);

        final JSplitPane splitPane = new JSplitPane();
        splitPane.setOneTouchExpandable(true);
        splitPane.setResizeWeight(0.3);
        splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
        final GridBagConstraints gbcSplitPane = new GridBagConstraints();
        gbcSplitPane.gridwidth = 3;
        gbcSplitPane.insets = new Insets(0, 0, 5, 0);
        gbcSplitPane.fill = GridBagConstraints.BOTH;
        gbcSplitPane.gridx = 0;
        gbcSplitPane.gridy = 0;
        this.add(splitPane, gbcSplitPane);

        final JScrollPane scrollPaneLeft = new JScrollPane();
        splitPane.setLeftComponent(scrollPaneLeft);
        scrollPaneLeft.setBorder(new TitledBorder(null, Messages.getString("MainFrame.SrcDirs"), TitledBorder.LEADING, TitledBorder.TOP, null, null));

        listBatchToolsDat2DirSrc = new JFileDropList(
                files -> session.getUser().getSettings().setProperty(SettingsEnum.dat2dir_srcdirs, String.join("|", files.stream().map(File::getAbsolutePath).toList())));
        listBatchToolsDat2DirSrc.setMode(JFileDropMode.DIRECTORY);
        listBatchToolsDat2DirSrc.setUI(new JListHintUI(Messages.getString("MainFrame.DropDirHint"), Color.gray));
        listBatchToolsDat2DirSrc.setToolTipText(Messages.getString("MainFrame.listBatchToolsDat2DirSrc.toolTipText"));
        scrollPaneLeft.setViewportView(listBatchToolsDat2DirSrc);

        final JPopupMenu popupMenuSrc = new JPopupMenu();
        Popup.addPopup(listBatchToolsDat2DirSrc, popupMenuSrc);

        JMenuItem mnDat2DirAddSrcDir = new JMenuItem(Messages.getString("MainFrame.AddSrcDir"));
        mnDat2DirAddSrcDir.addActionListener(_ -> addSrcDir());
        popupMenuSrc.add(mnDat2DirAddSrcDir);

        JMenuItem mnDat2DirDelSrcDir = new JMenuItem(Messages.getString("MainFrame.DelSrcDir"));
        mnDat2DirDelSrcDir.addActionListener(_ -> listBatchToolsDat2DirSrc.del(listBatchToolsDat2DirSrc.getSelectedValuesList()));
        popupMenuSrc.add(mnDat2DirDelSrcDir);

        JScrollPane scrollPaneRight = new JScrollPane();
        splitPane.setRightComponent(scrollPaneRight);

        BatchTableModel model = new BatchTableModel();
        tableBatchToolsDat2Dir = new JSDRDropTable(model, files -> session.getUser().getSettings().setProperty(SettingsEnum.dat2dir_sdr, AbstractSrcDstResult.toJSON(files))); // $NON-NLS-1$
        model.setButtonHandler((row, _) -> showResult(session, model, row));
        tableBatchToolsDat2Dir.getSDRModel().setData(SrcDstResult.fromJSON(session.getUser().getSettings().getProperty(SettingsEnum.dat2dir_sdr)));
        tableBatchToolsDat2Dir.setCellSelectionEnabled(false);
        tableBatchToolsDat2Dir.setRowSelectionAllowed(true);
        tableBatchToolsDat2Dir.getSDRModel().setSrcFilter(this::srcFilter);
        tableBatchToolsDat2Dir.getSDRModel().setDstFilter(File::isDirectory);
        tableBatchToolsDat2Dir.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        tableBatchToolsDat2Dir.setFillsViewportHeight(true);
        ((BatchTableModel) tableBatchToolsDat2Dir.getModel()).applyColumnsWidths(tableBatchToolsDat2Dir);
        scrollPaneRight.setViewportView(tableBatchToolsDat2Dir);

        tableBatchToolsDat2Dir.addMouseListener(new Dir2DatMouseAdapter(session));

        JPopupMenu popupMenu = new JPopupMenu();
        Popup.addPopup(tableBatchToolsDat2Dir, popupMenu);

        JMenuItem mnDat2DirAddDat = new JMenuItem(Messages.getString("MainFrame.AddDat"));
        mnDat2DirAddDat.addActionListener(_ -> addDat());
        popupMenu.add(mnDat2DirAddDat);

        popupMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                // do nothing
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                // do nothing
            }

            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                mnDat2DirPresets.setEnabled(tableBatchToolsDat2Dir.getSelectedRowCount() > 0);
                mnDat2DirAddDat.setEnabled(tableBatchToolsDat2Dir.columnAtPoint(popupPoint) <= 1);
            }
        });

        JMenuItem mnDat2DirDelDat = new JMenuItem(Messages.getString("MainFrame.DelDat"));
        mnDat2DirDelDat.addActionListener(_ -> tableBatchToolsDat2Dir.del(tableBatchToolsDat2Dir.getSelectedValuesList()));
        popupMenu.add(mnDat2DirDelDat);

        mnDat2DirPresets = new JMenu(Messages.getString("MainFrame.Presets"));
        popupMenu.add(mnDat2DirPresets);

        JMenu mnDat2DirD2D = new JMenu(Messages.getString("MainFrame.Dir2DatMenu"));
        mnDat2DirPresets.add(mnDat2DirD2D);

        JMenuItem mntmDat2DirD2DTzip = new JMenuItem(Messages.getString("MainFrame.TZIP"));
        mntmDat2DirD2DTzip.addActionListener(_ -> {
            for (AbstractSrcDstResult sdr : tableBatchToolsDat2Dir.getSelectedValuesList())
                ProfileSettings.TZIP(session, PathAbstractor.getAbsolutePath(session, sdr.getSrc()).toFile());
        });
        mnDat2DirD2D.add(mntmDat2DirD2DTzip);

        JMenuItem mntmDat2DirD2DDir = new JMenuItem(Messages.getString("MainFrame.DIR"));
        mntmDat2DirD2DDir.addActionListener(_ -> {
            for (AbstractSrcDstResult sdr : tableBatchToolsDat2Dir.getSelectedValuesList())
                ProfileSettings.DIR(session, PathAbstractor.getAbsolutePath(session, sdr.getSrc()).toFile());
        });
        mnDat2DirD2D.add(mntmDat2DirD2DDir);

        JMenuItem mntmCustom = new JMenuItem(Messages.getString("BatchToolsDirUpd8rPanel.mntmCustom.text"));
        mntmCustom.addActionListener(_ -> customPreset(session));
        mnDat2DirPresets.add(mntmCustom);
        for (final String s : StringUtils.split(session.getUser().getSettings().getProperty(SettingsEnum.dat2dir_srcdirs), '|'))
            if (!s.isEmpty())
                listBatchToolsDat2DirSrc.getModel().addElement(new File(s));

        JCheckBox cbBatchToolsDat2DirDryRun = new JCheckBox(Messages.getString("MainFrame.cbBatchToolsDat2DirDryRun.text"));
        cbBatchToolsDat2DirDryRun.setSelected(session.getUser().getSettings().getProperty(SettingsEnum.dat2dir_dry_run, Boolean.class)); // $NON-NLS-1$
        cbBatchToolsDat2DirDryRun.addItemListener(e -> session.getUser().getSettings().setProperty(SettingsEnum.dat2dir_dry_run, e.getStateChange() == ItemEvent.SELECTED)); // $NON-NLS-1$

        JButton btnBatchToolsDir2DatStart = new JButton(Messages.getString("MainFrame.btnStart.text"));
        btnBatchToolsDir2DatStart.setIcon(MainFrame.getIcon("/jrm/resicons/icons/bullet_go.png"));
        btnBatchToolsDir2DatStart.addActionListener(_ -> dat2dir(session, cbBatchToolsDat2DirDryRun.isSelected()));

        GridBagConstraints gbcCBBatchToolsDat2DirDryRun = new GridBagConstraints();
        gbcCBBatchToolsDat2DirDryRun.insets = new Insets(0, 0, 0, 5);
        gbcCBBatchToolsDat2DirDryRun.gridx = 1;
        gbcCBBatchToolsDat2DirDryRun.gridy = 1;
        this.add(cbBatchToolsDat2DirDryRun, gbcCBBatchToolsDat2DirDryRun);
        GridBagConstraints gbcBtnBatchToolsDir2DatStart = new GridBagConstraints();
        gbcBtnBatchToolsDir2DatStart.fill = GridBagConstraints.HORIZONTAL;
        gbcBtnBatchToolsDir2DatStart.gridx = 2;
        gbcBtnBatchToolsDir2DatStart.gridy = 1;
        this.add(btnBatchToolsDir2DatStart, gbcBtnBatchToolsDir2DatStart);

    }

    /**
     * @param session
     * 
     * @throws SecurityException
     */
    private void customPreset(final Session session) throws SecurityException {
        SDRList<SrcDstResult> list = tableBatchToolsDat2Dir.getSelectedValuesList();
        if (!list.isEmpty()) {
            BatchDirUpd8rSettingsDialog dialog = new BatchDirUpd8rSettingsDialog(SwingUtilities.getWindowAncestor(BatchDirUpd8rPanel.this));
            AbstractSrcDstResult entry = list.get(0);
            dialog.settingsPanel.initProfileSettings(session.getUser().getSettings().loadProfileSettings(PathAbstractor.getAbsolutePath(session, entry.getSrc()).toFile(), null));
            dialog.setVisible(true);
            if (dialog.isSuccess()) {
                for (AbstractSrcDstResult sdr : list) {
                    session.getUser().getSettings().saveProfileSettings(PathAbstractor.getAbsolutePath(session, sdr.getSrc()).toFile(), dialog.settingsPanel.getSettings());
                }
            }
        }
    }

    /**
     * 
     */
    private void addDat() {
        final int col = tableBatchToolsDat2Dir.columnAtPoint(popupPoint);
        final int row = tableBatchToolsDat2Dir.rowAtPoint(popupPoint);
        SDRList<SrcDstResult> list = tableBatchToolsDat2Dir.getSelectedValuesList();
        final File currdir;
        final int type = col == 0 ? JFileChooser.OPEN_DIALOG : JFileChooser.SAVE_DIALOG;
        final int mode = col == 0 ? JFileChooser.FILES_AND_DIRECTORIES : JFileChooser.DIRECTORIES_ONLY;
        if (!list.isEmpty())
            currdir = Optional.ofNullable(col == 0 ? list.get(0).getSrc() : list.get(0).getDst()).map(f -> new File(f).getParentFile()).orElse(null);
        else
            currdir = null;
        final List<FileFilter> filters = Collections.singletonList(new Dir2DatFileFilter(col));
        final var title = col == 0 ? "Choose XML/DAT files or the parent directory in case of software lists" : "Choose destination directories";
        final var fchooser = new JRMFileChooser<Void>(type, mode, currdir, null /* selected */, filters, title, true);
        fchooser.show(SwingUtilities.windowForComponent(BatchDirUpd8rPanel.this), chooser -> chosen(col, row, chooser));
    }

    /**
     * @param col
     * @param row
     * @param chooser
     * 
     * @return
     */
    private Void chosen(final int col, final int row, JRMFileChooser<Void> chooser) {
        final File[] files = chooser.getSelectedFiles();
        if (files.length <= 0)
            return null;
        final SDRTableModel sdrmodel = tableBatchToolsDat2Dir.getSDRModel();
        final int startSize = sdrmodel.getData().size();
        final var filter = col == 0 ? sdrmodel.getSrcFilter() : sdrmodel.getDstFilter();
        for (int i = 0; i < files.length; i++) {
            final File file = files[i];
            if (filter.accept(file)) {
                final SrcDstResult line;
                if (row == -1 || row + i >= sdrmodel.getData().size()) {
                    line = new SrcDstResult();
                    sdrmodel.getData().add(line);
                } else
                    line = sdrmodel.getData().get(row + i);
                if (col == 1)
                    line.setDst(file.getPath());
                else
                    line.setSrc(file.getPath());
            }
        }
        chosenTablechanged(col, row, sdrmodel, startSize);
        tableBatchToolsDat2Dir.call();
        return null;
    }

    /**
     * @param col
     * @param row
     * @param sdrmodel
     * @param startSize
     */
    private void chosenTablechanged(final int col, final int row, final SDRTableModel sdrmodel, final int startSize) {
        if (row != -1)
            sdrmodel.fireTableChanged(new TableModelEvent(sdrmodel, row, startSize - 1, col));
        if (startSize != sdrmodel.getData().size())
            sdrmodel.fireTableChanged(new TableModelEvent(sdrmodel, startSize, sdrmodel.getData().size() - 1, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT));
    }

    /**
     * @param file
     * 
     * @return
     * 
     * @throws IllegalArgumentException
     */
    private boolean srcFilter(File file) throws IllegalArgumentException {
        final List<String> exts = Arrays.asList("xml", "dat");
        if (file.isFile())
            return exts.contains(FilenameUtils.getExtension(file.getName()));
        else if (file.isDirectory())
            return file.listFiles(f -> f.isFile() && exts.contains(FilenameUtils.getExtension(f.getName()))).length > 0;
        return false;
    }

    /**
     * @param session
     * @param model
     * @param row
     */
    private void showResult(final Session session, SDRTableModel model, int row) {
        final AbstractSrcDstResult sdr = model.getData().get(row);
        new SwingWorkerProgress<DirUpdaterResults, Void>(SwingUtilities.getWindowAncestor(BatchDirUpd8rPanel.this)) {

            @Override
            protected DirUpdaterResults doInBackground() throws Exception {
                setProgress("Loading...");
                return DirUpdaterResults.load(session, new File(sdr.getSrc()), this);
            }

            @Override
            protected void done() {
                close();
                try {
                    new BatchDirUpd8rResultsDialog(session, SwingUtilities.getWindowAncestor(BatchDirUpd8rPanel.this), get());
                } catch (InterruptedException e) {
                    Log.err(e.getMessage(), e);
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Log.err(e.getMessage(), e);
                }
            }
        }.execute();
    }

    /**
     * 
     */
    private void addSrcDir() {
        List<File> list = listBatchToolsDat2DirSrc.getSelectedValuesList();
        new JRMFileChooser<Void>(JFileChooser.OPEN_DIALOG, JFileChooser.DIRECTORIES_ONLY, !list.isEmpty() ? list.get(0).getParentFile() : null, // currdir
                null, // selected
                null, // filters
                "Choose source directories", true).show(SwingUtilities.windowForComponent(BatchDirUpd8rPanel.this), chooser -> {
                    File[] files = chooser.getSelectedFiles();
                    if (files.length > 0)
                        listBatchToolsDat2DirSrc.add(files);
                    return null;
                });
    }

    private void dat2dir(final Session session, boolean dryrun) {
        if (listBatchToolsDat2DirSrc.getModel().getSize() > 0) {
            // Capture immutable snapshots on the EDT before handing them to the background worker.
            final SDRList<SrcDstResult> sdrl = new SDRList<>(((SDRTableModel) tableBatchToolsDat2Dir.getModel()).getData());
            final List<File> srcDirs = StreamEx.of(listBatchToolsDat2DirSrc.getModel().elements()).map(f -> PathAbstractor.getAbsolutePath(session, f.toString()).toFile()).toList();
            if (sdrl.stream().filter(sdr -> !session.getUser().getSettings().getProfileSettingsFile(PathAbstractor.getAbsolutePath(session, sdr.getSrc()).toFile()).exists())
                    .count() > 0)
                JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this), Messages.getString("MainFrame.AllDatsMustHavePreset"));
            else {
                new SwingWorkerProgress<DirUpdater, Void>(SwingUtilities.getWindowAncestor(this)) {
                    @Override
                    protected DirUpdater doInBackground() throws Exception {
                        return new DirUpdater(session, sdrl, this, srcDirs, tableBatchToolsDat2Dir, dryrun);
                    }

                    @Override
                    protected void done() {
                        close();
                        session.setCurrProfile(null);
                        session.setCurrScan(null);
                        session.getReport().setProfile(session.getCurrProfile());
                        if (MainFrame.getProfileViewer() != null) {
                            MainFrame.getProfileViewer().dispose();
                            MainFrame.setProfileViewer(null);
                        }
                        if (MainFrame.getReportFrame() != null)
                            MainFrame.getReportFrame().setVisible(false);
                        JRomManager.getMainFrame().getMainPane().setEnabledAt(1, false);
                    }
                }.execute();
            }
        } else
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this), Messages.getString("MainFrame.AtLeastOneSrcDir"));
    }

    private final class Dir2DatMouseAdapter extends MouseAdapter {
        private final Session session;

        private Dir2DatMouseAdapter(Session session) {
            this.session = session;
        }

        @Override
        public void mouseClicked(final MouseEvent e) {
            if (e.getClickCount() == 2) {
                final JTable target = (JTable) e.getSource();
                int row = target.getSelectedRow();
                if (row >= 0) {
                    showResult(session, tableBatchToolsDat2Dir.getSDRModel(), row);
                }
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (e.isPopupTrigger())
                popupPoint = e.getPoint();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger())
                popupPoint = e.getPoint();
        }
    }

    private final class Dir2DatFileFilter extends FileFilter {
        private final int col;

        private Dir2DatFileFilter(int col) {
            this.col = col;
        }

        @Override
        public boolean accept(File f) {
            java.io.FileFilter filter = null;
            if (col == 1)
                filter = tableBatchToolsDat2Dir.getSDRModel().getDstFilter();
            else if (col == 0)
                filter = file -> {
                    final List<String> exts = Arrays.asList("xml", "dat");
                    if (file.isFile())
                        return exts.contains(FilenameUtils.getExtension(file.getName()));
                    return true;
                };
            if (filter != null)
                return filter.accept(f);
            return true;
        }

        @Override
        public String getDescription() {
            return col == 0 ? "Dat/XML files or directories of Dat/XML files" : "Destination directories";
        }
    }

}
