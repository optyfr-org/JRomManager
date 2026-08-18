package jrm.ui.batch;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
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

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

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

        BatchTableModel model = new BatchTableModel(new String[] { Messages.getString("MainFrame.TorrentFiles"), Messages.getString("MainFrame.DstDirs"),
                Messages.getString("MainFrame.Result"), "Details", "Selected" });
        tableTrntChk = new JSDRDropTable(model, files -> session.getUser().getSettings().setProperty(SettingsEnum.trntchk_sdr, AbstractSrcDstResult.toJSON(files)));
        model.setButtonHandler((row, _) -> new BatchTrrntChkResultsDialog(SwingUtilities.getWindowAncestor(BatchTrrntChkPanel.this),
                TrntChkReport.load(session, PathAbstractor.getAbsolutePath(session, model.getData().get(row).getSrc()).toFile())));
        tableTrntChk.addMouseListener(getTableTrntChkMouseListener());
        ((BatchTableModel) tableTrntChk.getModel()).applyColumnsWidths(tableTrntChk);
        final SDRList<SrcDstResult> sdrl2 = new SDRList<>();
        if (session != null) {
            for (final JsonValue arrv : Json.parse(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_sdr)).asArray()) // $NON-NLS-1$
                                                                                                                                     // //$NON-NLS-2$
            {
                final SrcDstResult sdr = new SrcDstResult();
                final JsonObject jso = arrv.asObject();
                final JsonValue src = jso.get("src"); //$NON-NLS-1$
                if (src != null && src != Json.NULL)
                    sdr.setSrc(src.asString());
                final JsonValue dst = jso.get("dst"); //$NON-NLS-1$
                if (dst != null && dst != Json.NULL)
                    sdr.setDst(dst.asString());
                final JsonValue result = jso.get("result"); //$NON-NLS-1$
                if (result != null && result != Json.NULL)
                    sdr.setResult(result.asString());
                sdr.setSelected(jso.getBoolean("selected", true)); //$NON-NLS-1$
                sdrl2.add(sdr);
            }
        }
        tableTrntChk.getSDRModel().setData(sdrl2);
        tableTrntChk.setCellSelectionEnabled(false);
        tableTrntChk.setRowSelectionAllowed(true);
        tableTrntChk.getSDRModel().setSrcFilter(file -> {
            final List<String> exts = Arrays.asList("torrent"); //$NON-NLS-1$
            if (file.isFile())
                return exts.contains(FilenameUtils.getExtension(file.getName()));
            return false;
        });
        tableTrntChk.getSDRModel().setDstFilter(File::isDirectory);
        tableTrntChk.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        tableTrntChk.setFillsViewportHeight(true);
        scrollPane.setViewportView(tableTrntChk);

        final JPopupMenu pmTrntChk = new JPopupMenu();
        Popup.addPopup(tableTrntChk, pmTrntChk);

        final JMenuItem mntmAddTorrent = new JMenuItem(Messages.getString("BatchToolsTrrntChkPanel.mntmAddTorrent.text"));
        mntmAddTorrent.addActionListener(_ -> addTorrent());
        pmTrntChk.add(mntmAddTorrent);

        pmTrntChk.addPopupMenuListener(new PopupMenuListener() {
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
        });

        final JMenuItem mntmDelTorrent = new JMenuItem(Messages.getString("BatchToolsTrrntChkPanel.mntmDelTorrent.text")); //$NON-NLS-1$
        mntmDelTorrent.addActionListener(_ -> tableTrntChk.del(tableTrntChk.getSelectedValuesList()));
        pmTrntChk.add(mntmDelTorrent);

        final JLabel lblCheckMode = new JLabel(Messages.getString("BatchToolsTrrntChkPanel.lblCheckMode.text")); //$NON-NLS-1$
        final GridBagConstraints gbcLblCheckMode = new GridBagConstraints();
        gbcLblCheckMode.insets = new Insets(0, 0, 0, 5);
        gbcLblCheckMode.anchor = GridBagConstraints.EAST;
        gbcLblCheckMode.gridx = 0;
        gbcLblCheckMode.gridy = 1;
        this.add(lblCheckMode, gbcLblCheckMode);

        cbbxTrntChk = new JComboBox<>();
        cbbxTrntChk.setModel(new DefaultComboBoxModel<>(TrntChkMode.values()));
        if (session != null)
            cbbxTrntChk.setSelectedItem(TrntChkMode.valueOf(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_mode))); // $NON-NLS-1$
        cbbxTrntChk.addActionListener(_ -> {
            session.getUser().getSettings().setProperty(SettingsEnum.trntchk_mode, cbbxTrntChk.getSelectedItem().toString());
            cbRemoveWrongSizedFiles.setEnabled(cbbxTrntChk.getSelectedItem() != TrntChkMode.FILENAME);
        });
        final GridBagConstraints gbcCbbxTrntChk = new GridBagConstraints();
        gbcCbbxTrntChk.anchor = GridBagConstraints.EAST;
        gbcCbbxTrntChk.insets = new Insets(0, 0, 0, 5);
        gbcCbbxTrntChk.gridx = 1;
        gbcCbbxTrntChk.gridy = 1;
        this.add(cbbxTrntChk, gbcCbbxTrntChk);

        final JButton btnBatchToolsTrntChkStart = new JButton(Messages.getString("BatchToolsTrrntChkPanel.TrntCheckStart.text")); //$NON-NLS-1$
        btnBatchToolsTrntChkStart.setIcon(MainFrame.getIcon("/jrm/resicons/icons/bullet_go.png"));
        btnBatchToolsTrntChkStart.addActionListener(_ -> trrntChk(session));

        chckbxDetectArchivedFolder = new JCheckBox(Messages.getString("BatchTrrntChkPanel.chckbxDetectArchivedFolder.text")); //$NON-NLS-1$
        chckbxDetectArchivedFolder
                .addActionListener(_ -> session.getUser().getSettings().setProperty(SettingsEnum.trntchk_detect_archived_folders, chckbxDetectArchivedFolder.isSelected()));
        if (session != null)
            chckbxDetectArchivedFolder.setSelected(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_detect_archived_folders, Boolean.class)); // $NON-NLS-1$
        GridBagConstraints gbcChckbxDetectArchivedFolder = new GridBagConstraints();
        gbcChckbxDetectArchivedFolder.insets = new Insets(0, 0, 0, 5);
        gbcChckbxDetectArchivedFolder.gridx = 2;
        gbcChckbxDetectArchivedFolder.gridy = 1;
        add(chckbxDetectArchivedFolder, gbcChckbxDetectArchivedFolder);

        cbRemoveUnknownFiles = new JCheckBox(Messages.getString("BatchToolsTrrntChkPanel.chckbxRemoveUnknownFiles.text")); //$NON-NLS-1$
        cbRemoveUnknownFiles.addActionListener(_ -> session.getUser().getSettings().setProperty(SettingsEnum.trntchk_remove_unknown_files, cbRemoveUnknownFiles.isSelected()));
        if (session != null)
            cbRemoveUnknownFiles.setSelected(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_remove_unknown_files, Boolean.class)); // $NON-NLS-1$
        GridBagConstraints gbcCbRemoveUnknownFiles = new GridBagConstraints();
        gbcCbRemoveUnknownFiles.insets = new Insets(0, 0, 0, 5);
        gbcCbRemoveUnknownFiles.gridx = 3;
        gbcCbRemoveUnknownFiles.gridy = 1;
        add(cbRemoveUnknownFiles, gbcCbRemoveUnknownFiles);

        cbRemoveWrongSizedFiles = new JCheckBox(Messages.getString("BatchToolsTrrntChkPanel.chckbxRemoveWrongSized.text")); //$NON-NLS-1$
        cbRemoveWrongSizedFiles
                .addActionListener(_ -> session.getUser().getSettings().setProperty(SettingsEnum.trntchk_remove_wrong_sized_files, cbRemoveWrongSizedFiles.isSelected()));
        if (session != null)
            cbRemoveWrongSizedFiles.setSelected(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_remove_wrong_sized_files, Boolean.class)); // $NON-NLS-1$
        cbRemoveWrongSizedFiles.setEnabled(cbbxTrntChk.getSelectedItem() != TrntChkMode.FILENAME);
        GridBagConstraints gbcCbRemoveWrongSizedFiles = new GridBagConstraints();
        gbcCbRemoveWrongSizedFiles.anchor = GridBagConstraints.WEST;
        gbcCbRemoveWrongSizedFiles.insets = new Insets(0, 0, 0, 5);
        gbcCbRemoveWrongSizedFiles.gridx = 4;
        gbcCbRemoveWrongSizedFiles.gridy = 1;
        add(cbRemoveWrongSizedFiles, gbcCbRemoveWrongSizedFiles);
        final GridBagConstraints gbcBtnBatchToolsTrntChkStart = new GridBagConstraints();
        gbcBtnBatchToolsTrntChkStart.anchor = GridBagConstraints.EAST;
        gbcBtnBatchToolsTrntChkStart.gridx = 5;
        gbcBtnBatchToolsTrntChkStart.gridy = 1;
        this.add(btnBatchToolsTrntChkStart, gbcBtnBatchToolsTrntChkStart);

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
        final var sdrl = ((SDRTableModel) tableTrntChk.getModel()).getData();
        final TrntChkMode mode = (TrntChkMode) cbbxTrntChk.getSelectedItem();
        final ResultColUpdater updater = tableTrntChk;
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

}
