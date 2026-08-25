package jrm.ui.batch;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TableModelEvent;
import javax.swing.filechooser.FileFilter;

import org.apache.commons.io.FilenameUtils;

import jrm.aui.basic.AbstractSrcDstResult;
import jrm.aui.basic.ResultColUpdater;
import jrm.aui.basic.SDRList;
import jrm.aui.basic.SrcDstResult;
import jrm.batch.TorrentChecker;
import jrm.batch.TrntChkReport;
import jrm.io.torrent.options.TrntChkMode;
import jrm.locale.Messages;
import jrm.misc.SettingsEnum;
import jrm.security.PathAbstractor;
import jrm.security.Session;
import jrm.ui.MainFrame;
import jrm.ui.basic.JRMFileChooser;
import jrm.ui.basic.JSDRDropTable;
import jrm.ui.basic.Popup;
import jrm.ui.basic.SDRTableModel;
import jrm.ui.progress.SwingWorkerProgress;

/**
 * Panel for batch torrent check operations.
 * <p>
 * Provides controls for checking and fixing torrent-structured directories.
 */
public class BatchTrrntChkPanel extends JPanel {
    /** Table for torrent check source/destination entries. */
    private JSDRDropTable tableTrntChk;
    /** Combo box for selecting the torrent check mode. */
    private JComboBox<TrntChkMode> cbbxTrntChk;
    /** Checkbox to remove unknown files during torrent check. */
    private JCheckBox cbRemoveUnknownFiles;
    /** Checkbox to remove wrong-sized files during torrent check. */
    private JCheckBox cbRemoveWrongSizedFiles;
    /** Position for popup menu. */
    private Point popupPoint;
    /** Checkbox to detect archived folders. */
    private JCheckBox chckbxDetectArchivedFolder;

    /**
     * Constructs the batch torrent check panel.
     *
     * @param session the user session for accessing settings
     */
    public BatchTrrntChkPanel(final @Nonnull Session session) {
        final GridBagLayout gblPanelBatchToolsDir2Torrent = new GridBagLayout();
        gblPanelBatchToolsDir2Torrent.columnWidths = new int[] { 0, 0, 0, 0, 0, 0, 0 };
        gblPanelBatchToolsDir2Torrent.rowHeights = new int[] { 0, 0, 0 };
        gblPanelBatchToolsDir2Torrent.columnWeights = new double[] { 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, Double.MIN_VALUE };
        gblPanelBatchToolsDir2Torrent.rowWeights = new double[] { 1.0, 0.0, Double.MIN_VALUE };
        setLayout(gblPanelBatchToolsDir2Torrent);

        final JScrollPane scrollPane = new JScrollPane();
        final GridBagConstraints gbcScrollPane = new GridBagConstraints();
        gbcScrollPane.gridwidth = 6;
        gbcScrollPane.insets = new Insets(0, 0, 5, 0);
        gbcScrollPane.fill = GridBagConstraints.BOTH;
        gbcScrollPane.gridx = 0;
        gbcScrollPane.gridy = 0;
        this.add(scrollPane, gbcScrollPane);

        initTable(session, scrollPane);
        initPopupMenu();
        initControls(session);
    }

    private void initTable(final Session session, final JScrollPane scrollPane) {
        final BatchTableModel model = new BatchTableModel(new String[] { Messages.getString("MainFrame.TorrentFiles"), Messages.getString("MainFrame.DstDirs"),
                Messages.getString("MainFrame.Result"), "Details", "Selected" });
        tableTrntChk = new JSDRDropTable(model, files -> session.getUser().getSettings().setProperty(SettingsEnum.trntchk_sdr, AbstractSrcDstResult.toJSON(files)));
        model.setButtonHandler((row, _) -> new BatchTrrntChkResultsDialog(SwingUtilities.getWindowAncestor(BatchTrrntChkPanel.this),
                TrntChkReport.load(session, PathAbstractor.getAbsolutePath(session, model.getData().get(row).getSrc()).toFile())));
        tableTrntChk.addMouseListener(getTableTrntChkMouseListener());
        ((BatchTableModel) tableTrntChk.getModel()).applyColumnsWidths(tableTrntChk);
        tableTrntChk.getSDRModel().setData(SrcDstResult.fromJSON(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_sdr)));
        tableTrntChk.setCellSelectionEnabled(false);
        tableTrntChk.setRowSelectionAllowed(true);
        tableTrntChk.getSDRModel().setSrcFilter(this::isTorrentFile);
        tableTrntChk.getSDRModel().setDstFilter(File::isDirectory);
        tableTrntChk.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        tableTrntChk.setFillsViewportHeight(true);
        scrollPane.setViewportView(tableTrntChk);
    }

    private boolean isTorrentFile(final File file) {
        return file.isFile() && Arrays.asList("torrent").contains(FilenameUtils.getExtension(file.getName()));
    }

    private void initPopupMenu() {
        final JPopupMenu pmTrntChk = new JPopupMenu();
        Popup.addPopup(tableTrntChk, pmTrntChk);

        final JMenuItem mntmAddTorrent = new JMenuItem(Messages.getString("BatchToolsTrrntChkPanel.mntmAddTorrent.text"));
        mntmAddTorrent.addActionListener(_ -> addTorrent());
        pmTrntChk.add(mntmAddTorrent);
        pmTrntChk.addPopupMenuListener(addTorrentPopupListener(mntmAddTorrent));

        final JMenuItem mntmDelTorrent = new JMenuItem(Messages.getString("BatchToolsTrrntChkPanel.mntmDelTorrent.text"));
        mntmDelTorrent.addActionListener(_ -> tableTrntChk.del(tableTrntChk.getSelectedValuesList()));
        pmTrntChk.add(mntmDelTorrent);
    }

    private PopupMenuListener addTorrentPopupListener(final JMenuItem mntmAddTorrent) {
        return new PopupMenuListener() {
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
                mntmAddTorrent.setEnabled(tableTrntChk.columnAtPoint(popupPoint) <= 1);
            }
        };
    }

    private void initControls(final Session session) {
        final JLabel lblCheckMode = new JLabel(Messages.getString("BatchToolsTrrntChkPanel.lblCheckMode.text"));
        this.add(lblCheckMode, gbc(0, 1, GridBagConstraints.EAST, new Insets(0, 0, 0, 5)));

        cbbxTrntChk = new JComboBox<>();
        cbbxTrntChk.setModel(new DefaultComboBoxModel<>(TrntChkMode.values()));
        cbbxTrntChk.setSelectedItem(TrntChkMode.valueOf(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_mode)));
        cbbxTrntChk.addActionListener(_ -> {
            session.getUser().getSettings().setProperty(SettingsEnum.trntchk_mode, cbbxTrntChk.getSelectedItem().toString());
            cbRemoveWrongSizedFiles.setEnabled(cbbxTrntChk.getSelectedItem() != TrntChkMode.FILENAME);
        });
        this.add(cbbxTrntChk, gbc(1, 1, GridBagConstraints.EAST, new Insets(0, 0, 0, 5)));

        chckbxDetectArchivedFolder = settingCheckBox(session, "BatchTrrntChkPanel.chckbxDetectArchivedFolder.text", SettingsEnum.trntchk_detect_archived_folders);
        add(chckbxDetectArchivedFolder, gbc(2, 1, GridBagConstraints.CENTER, new Insets(0, 0, 0, 5)));

        cbRemoveUnknownFiles = settingCheckBox(session, "BatchToolsTrrntChkPanel.chckbxRemoveUnknownFiles.text", SettingsEnum.trntchk_remove_unknown_files);
        add(cbRemoveUnknownFiles, gbc(3, 1, GridBagConstraints.CENTER, new Insets(0, 0, 0, 5)));

        cbRemoveWrongSizedFiles = settingCheckBox(session, "BatchToolsTrrntChkPanel.chckbxRemoveWrongSized.text", SettingsEnum.trntchk_remove_wrong_sized_files);
        cbRemoveWrongSizedFiles.setEnabled(cbbxTrntChk.getSelectedItem() != TrntChkMode.FILENAME);
        add(cbRemoveWrongSizedFiles, gbc(4, 1, GridBagConstraints.WEST, new Insets(0, 0, 0, 5)));

        final JButton btnBatchToolsTrntChkStart = new JButton(Messages.getString("BatchToolsTrrntChkPanel.TrntCheckStart.text"));
        btnBatchToolsTrntChkStart.setIcon(MainFrame.getIcon("/jrm/resicons/icons/bullet_go.png"));
        btnBatchToolsTrntChkStart.addActionListener(_ -> trrntChk(session));
        this.add(btnBatchToolsTrntChkStart, gbc(5, 1, GridBagConstraints.EAST, new Insets(0, 0, 0, 0)));
    }

    private static JCheckBox settingCheckBox(final Session session, final String messageKey, final SettingsEnum setting) {
        final var box = new JCheckBox(Messages.getString(messageKey));
        box.addActionListener(_ -> session.getUser().getSettings().setProperty(setting, box.isSelected()));
        box.setSelected(session.getUser().getSettings().getProperty(setting, Boolean.class));
        return box;
    }

    private static GridBagConstraints gbc(final int x, final int y, final int anchor, final Insets insets) {
        final var constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.anchor = anchor;
        constraints.insets = insets;
        return constraints;
    }

    /**
     * 
     */
    private void addTorrent() {
        final int col = tableTrntChk.columnAtPoint(popupPoint);
        final int row = tableTrntChk.rowAtPoint(popupPoint);
        final var list = tableTrntChk.getSelectedValuesList();
        final var type = col == 0 ? JFileChooser.OPEN_DIALOG : JFileChooser.SAVE_DIALOG;
        final var mode = col == 0 ? JFileChooser.FILES_AND_DIRECTORIES : JFileChooser.DIRECTORIES_ONLY;
        final File currdir;
        if (!list.isEmpty())
            currdir = Optional.ofNullable(col == 0 ? list.get(0).getSrc() : list.get(0).getDst()).map(File::new).map(File::getParentFile).orElse(null);
        else
            currdir = null;
        new JRMFileChooser<Void>(type, mode, currdir, null /* selected */, Collections.singletonList(getAddTorrentFileFilter(col)),
                col == 0 ? "Choose torrent files" : "Choose destination directories", true).show(SwingUtilities.windowForComponent(BatchTrrntChkPanel.this), chooser -> {
                    File[] files = chooser.getSelectedFiles();
                    addTorrent(col, row, files);
                    return null;
                });
    }

    /**
     * @param col
     * @param row
     * @param files
     */
    private void addTorrent(final int col, final int row, File[] files) {
        SDRTableModel model = tableTrntChk.getSDRModel();
        if (files.length <= 0)
            return;
        final int startSize = model.getData().size();
        final var filter = col == 0 ? model.getSrcFilter() : model.getDstFilter();
        for (int i = 0; i < files.length; i++) {
            final File file = files[i];
            if (!filter.accept(file))
                continue;
            model.addFile(file, row, col, i);
        }
        if (row != -1)
            model.fireTableChanged(new TableModelEvent(model, row, startSize - 1, col));
        if (startSize != model.getData().size())
            model.fireTableChanged(new TableModelEvent(model, startSize, model.getData().size() - 1, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT));
        tableTrntChk.call();
    }

    /**
     * @param col
     * 
     * @return
     */
    private FileFilter getAddTorrentFileFilter(final int col) {
        return new FileFilter() {
            @Override
            public boolean accept(File f) {
                java.io.FileFilter filter = null;
                if (col == 1)
                    filter = tableTrntChk.getSDRModel().getDstFilter();
                else if (col == 0)
                    filter = file -> {
                        final List<String> exts = Arrays.asList("torrent"); //$NON-NLS-1$ //$NON-NLS-2$
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
                return col == 0 ? "Torrent files" : "Destination directories";
            }
        };
    }

    /**
     * @return
     */
    private MouseAdapter getTableTrntChkMouseListener() {
        return new MouseAdapter() {
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
        };
    }

    private void trrntChk(final Session session) {
        // Deep-copy rows on the EDT so the worker does not share mutable table state.
        final SDRList<SrcDstResult> sdrl = snapshotRows(((SDRTableModel) tableTrntChk.getModel()).getData());
        final TrntChkMode mode = (TrntChkMode) cbbxTrntChk.getSelectedItem();
        final ResultColUpdater updater = edtUpdater(tableTrntChk);
        final var opts = EnumSet.noneOf(TorrentChecker.Options.class);
        if (cbRemoveUnknownFiles.isSelected())
            opts.add(TorrentChecker.Options.REMOVEUNKNOWNFILES);
        if (cbRemoveWrongSizedFiles.isSelected())
            opts.add(TorrentChecker.Options.REMOVEWRONGSIZEDFILES);
        if (chckbxDetectArchivedFolder.isSelected())
            opts.add(TorrentChecker.Options.DETECTARCHIVEDFOLDERS);

        new SwingWorkerProgress<TorrentChecker<SrcDstResult>, Void>(SwingUtilities.getWindowAncestor(this)) {
            @Override
            protected TorrentChecker<SrcDstResult> doInBackground() throws Exception {
                return new TorrentChecker<>(session, this, sdrl, mode, updater, opts);
            }

            @Override
            protected void done() {
                close();
            }
        }.execute();
    }

    private static SDRList<SrcDstResult> snapshotRows(List<SrcDstResult> data) {
        final SDRList<SrcDstResult> copy = new SDRList<>();
        for (final SrcDstResult row : data) {
            final SrcDstResult snap = new SrcDstResult(row.getSrc(), row.getDst());
            snap.setResult(row.getResult());
            snap.setSelected(row.isSelected());
            copy.add(snap);
        }
        return copy;
    }

    private static ResultColUpdater edtUpdater(ResultColUpdater delegate) {
        return new ResultColUpdater() {
            @Override
            public void updateResult(int row, String result) {
                runOnEdt(() -> delegate.updateResult(row, result));
            }

            @Override
            public void clearResults() {
                runOnEdt(delegate::clearResults);
            }
        };
    }

    private static void runOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException e) {
            final var cause = e.getCause();
            if (cause instanceof RuntimeException re)
                throw re;
            if (cause instanceof Error err)
                throw err;
            throw new IllegalStateException(cause);
        }
    }

}
