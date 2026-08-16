package jrm.ui.batch;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellRenderer;

import org.apache.commons.io.FilenameUtils;

import jrm.aui.basic.SrcDstResult;
import jrm.aui.status.StatusRendererFactory;
import jrm.batch.Compressor;
import jrm.batch.Compressor.FileResult;
import jrm.batch.CompressorFormat;
import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.misc.MultiThreadingVirtual;
import jrm.misc.SettingsEnum;
import jrm.security.Session;
import jrm.ui.MainFrame;
import jrm.ui.basic.AbstractEnhTableModel;
import jrm.ui.basic.JRMFileChooser;
import jrm.ui.progress.SwingWorkerProgress;

/**
 * Panel for batch compression operations.
 * <p>
 * Provides controls for compressing ROM sets using various compression formats.
 */
public class BatchCompressorPanel extends JPanel implements StatusRendererFactory {
    private BatchCompressorTable table;
    private JCheckBox chckbxForce;
    private JComboBox<CompressorFormat> comboBox;
    private JButton btnClear;
    private JPopupMenu popupMenu;
    private JMenuItem mntmAddArchive;
    private JMenuItem mntmRemoveSelectedArchives;

    static class BatchCompressorTableModel extends AbstractEnhTableModel {

        private List<FileResult> data = new ArrayList<>();
        private final String[] columnNames = new String[] { Messages.getString("BatchCompressorPanel.File"), Messages.getString("BatchCompressorPanel.Status") }; //$NON-NLS-1$ //$NON-NLS-2$
        private final Class<?>[] columnTypes = new Class<?>[] { Object.class, String.class };
        private final TableCellRenderer[] cellRenderers = new TableCellRenderer[] { new FileCellRenderer(), new StatusCellRenderer() };
        private final int[] widths = { 0, 0 };
        private final String[] headersTT = columnNames;

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public int getColumnCount() {
            return columnTypes.length;
        }

        public List<FileResult> getData() {
            return data;
        }

        /**
         * @param data initialize data
         */
        public void setData(List<FileResult> data) {
            this.data = data;
            fireTableChanged(new TableModelEvent(this));
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex == 0)
                return getData().get(rowIndex).getFile();
            if (columnIndex == 1)
                return getData().get(rowIndex).getResult();
            return null;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnTypes[columnIndex];
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        public TableCellRenderer getCellRenderer(int column) {
            return cellRenderers[column];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0)
                getData().get(rowIndex).setFile(((File) aValue).toPath());
            if (columnIndex == 1)
                getData().get(rowIndex).setResult((String) aValue);
            fireTableChanged(new TableModelEvent(this, rowIndex, rowIndex, columnIndex, TableModelEvent.UPDATE));
        }

        @Override
        public TableCellRenderer[] getCellRenderers() {
            return cellRenderers;
        }

        @Override
        public int getColumnWidth(int columnIndex) {
            return widths[columnIndex];
        }

        @Override
        public String getColumnTT(int columnIndex) {
            return headersTT[columnIndex];
        }
    }

    static class BatchCompressorTable extends JTable implements DropTargetListener {
        private transient BatchCompressorTableModel model;
        private transient Color color;

        /** The add call back. */
        private final transient AddCallBack callback;

        /**
         * The Interface AddDelCallBack.
         */
        @FunctionalInterface
        public interface AddCallBack {
            public void call(List<FileResult> files);
        }

        public BatchCompressorTable(BatchCompressorTableModel model, AddCallBack callback) {
            super(model);
            this.model = model;
            this.callback = callback;
            for (int i = 0; i < getColumnModel().getColumnCount(); i++)
                getColumnModel().getColumn(i).setCellRenderer(model.getCellRenderer(i));
            color = getBackground();
            new DropTarget(this, this);
            this.model.addTableModelListener(e -> {
                if (e.getColumn() >= 0 && model.getColumnClass(e.getColumn()).equals(Boolean.class) && e.getType() == TableModelEvent.UPDATE)
                    callback.call(model.getData());
            });
            setFillsViewportHeight(true);
        }

        @Override
        public void dragEnter(DropTargetDragEvent dtde) {
            // do nothing
        }

        @Override
        public void dragOver(DropTargetDragEvent dtde) {
            final Transferable transferable = dtde.getTransferable();
            if (isEnabled() && transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                setBackground(Color.decode("#DDFFDD")); //$NON-NLS-1$
                model.fireTableChanged(new TableModelEvent(model));
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
            } else {
                setBackground(Color.decode("#FFDDDD")); //$NON-NLS-1$
                model.fireTableChanged(new TableModelEvent(model));
                dtde.rejectDrag();
            }
        }

        @Override
        public void dropActionChanged(DropTargetDragEvent dtde) {
            // do nothing
        }

        @Override
        public void dragExit(DropTargetEvent dte) {
            setBackground(color);
            model.fireTableChanged(new TableModelEvent(model));
        }

        @Override
        public void drop(DropTargetDropEvent dtde) {
            setBackground(color);
            model.fireTableChanged(new TableModelEvent(model));
            try {
                final Transferable transferable = dtde.getTransferable();

                if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);

                    final String[] extensions = new String[] { "zip", "7z", "rar", "arj", "tar", "lzh", "lha", "tgz", "tbz", "tbz2", "rpm", "iso", "deb", "cab" };

                    FileFilter filter = pathname -> pathname.isDirectory() || FilenameUtils.isExtension(pathname.getName(), extensions);

                    @SuppressWarnings("unchecked")
                    final List<File> files = ((List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor)).stream().filter(filter::accept).toList();
                    if (!files.isEmpty()) {
                        addFiles(files, extensions);
                        dtde.getDropTargetContext().dropComplete(true);
                    } else
                        dtde.getDropTargetContext().dropComplete(false);
                } else
                    dtde.rejectDrop();
            } catch (final UnsupportedFlavorException _) {
                dtde.rejectDrop();
            } catch (final Exception e) {
                Log.err(e.getMessage(), e);
                dtde.rejectDrop();
            }
        }

        /**
         * @param files
         * @param extensions
         * 
         * @throws IOException
         */
        private void addFiles(final List<File> files, final String[] extensions) throws IOException {
            int startSize = model.getData().size();
            for (File f : files) {
                if (f.isDirectory()) {
                    try (final var stream = Files.walk(f.toPath())) {
                        stream.filter(p -> Files.isRegularFile(p) && FilenameUtils.isExtension(p.getFileName().toString(), extensions))
                                .forEachOrdered(p -> model.getData().add(new FileResult(p)));
                    }
                } else
                    model.getData().add(new FileResult(f.toPath()));
            }
            if (startSize != model.getData().size())
                model.fireTableChanged(new TableModelEvent(model, startSize, model.getData().size() - 1, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT));
            callback.call(model.getData());
        }

        /**
         * Del.
         *
         * @param sdrl the data to delete
         */
        public void del(final List<FileResult> sdrl) {
            for (final FileResult sdr : sdrl)
                model.getData().remove(sdr);
            model.fireTableChanged(new TableModelEvent(model));
            callback.call(model.getData());
        }

        /**
         * get selected values as a {@link List} of {@link SrcDstResult}
         * 
         * @return the {@link List} of {@link FileResult} corresponding to selected values
         */
        public List<FileResult> getSelectedValuesList() {
            int[] rows = getSelectedRows();
            List<FileResult> list = new ArrayList<>();
            for (int row : rows)
                list.add(model.getData().get(row));
            return list;
        }
    }

    public BatchCompressorPanel(@Nonnull Session session) {
        GridBagLayout gridBagLayout = new GridBagLayout();
        gridBagLayout.columnWidths = new int[] { 450, 0, 0, 0, 0, 0 };
        gridBagLayout.rowHeights = new int[] { 300, 0, 0 };
        gridBagLayout.columnWeights = new double[] { 1.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
        gridBagLayout.rowWeights = new double[] { 1.0, 0.0, Double.MIN_VALUE };
        setLayout(gridBagLayout);

        JScrollPane scrollPane = new JScrollPane();
        GridBagConstraints gbcScrollPane = new GridBagConstraints();
        gbcScrollPane.gridwidth = 5;
        gbcScrollPane.insets = new Insets(0, 0, 5, 0);
        gbcScrollPane.fill = GridBagConstraints.BOTH;
        gbcScrollPane.gridx = 0;
        gbcScrollPane.gridy = 0;
        add(scrollPane, gbcScrollPane);

        table = new BatchCompressorTable(new BatchCompressorTableModel(), _ -> {
        });
        scrollPane.setViewportView(table);

        popupMenu = new JPopupMenu();
        addPopup(table, popupMenu);

        mntmAddArchive = new JMenuItem(Messages.getString("BatchCompressorPanel.mntmAddArchive.text")); //$NON-NLS-1$
        mntmAddArchive.addActionListener(_ -> {
            final String[] extensions = new String[] { "zip", "7z", "rar", "arj", "tar", "lzh", "lha", "tgz", "tbz", "tbz2", "rpm", "iso", "deb", "cab" };
            new JRMFileChooser<Void>(JFileChooser.OPEN_DIALOG, JFileChooser.FILES_AND_DIRECTORIES, null, null, Collections.singletonList(getFileFilter(extensions)),
                    Messages.getString("BatchCompressorPanel.mntmAddArchive.text"), true).showOpen(SwingUtilities.windowForComponent(BatchCompressorPanel.this), chooser -> {
                        File[] files = chooser.getSelectedFiles();
                        addArchiveChooser(extensions, files);
                        return null;
                    });
        });
        popupMenu.add(mntmAddArchive);

        mntmRemoveSelectedArchives = new JMenuItem(Messages.getString("BatchCompressorPanel.mntmRemoveSelectedArchives.text")); //$NON-NLS-1$
        mntmRemoveSelectedArchives.addActionListener(_ -> table.del(table.getSelectedValuesList()));
        popupMenu.add(mntmRemoveSelectedArchives);

        comboBox = new JComboBox<>();
        comboBox.setModel(new DefaultComboBoxModel<>(CompressorFormat.values()));
        comboBox.setSelectedIndex(1);
        comboBox.setSelectedItem(CompressorFormat.valueOf(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.compressor_format))); // $NON-NLS-1$
        comboBox.addActionListener(_ -> session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.compressor_format, comboBox.getSelectedItem().toString()));
        GridBagConstraints gbcComboBox = new GridBagConstraints();
        gbcComboBox.insets = new Insets(0, 0, 0, 5);
        gbcComboBox.fill = GridBagConstraints.HORIZONTAL;
        gbcComboBox.gridx = 1;
        gbcComboBox.gridy = 1;
        add(comboBox, gbcComboBox);

        chckbxForce = new JCheckBox(Messages.getString("BatchCompressorPanel.Force")); //$NON-NLS-1$
        chckbxForce.addActionListener(_ -> session.getUser().getSettings().setProperty(jrm.misc.SettingsEnum.compressor_force, chckbxForce.isSelected()));
        chckbxForce.setSelected(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.compressor_force, Boolean.class)); // $NON-NLS-1$
        GridBagConstraints gbcChckbxForce = new GridBagConstraints();
        gbcChckbxForce.insets = new Insets(0, 0, 0, 5);
        gbcChckbxForce.gridx = 2;
        gbcChckbxForce.gridy = 1;
        add(chckbxForce, gbcChckbxForce);

        JButton btnStart = new JButton(Messages.getString("BatchCompressorPanel.Start")); //$NON-NLS-1$
        btnStart.setIcon(MainFrame.getIcon("/jrm/resicons/icons/bullet_go.png"));
        btnStart.addActionListener(_ -> start(session));

        btnClear = new JButton(Messages.getString("BatchCompressorPanel.btnClear.text")); //$NON-NLS-1$
        btnClear.setIcon(MainFrame.getIcon("/jrm/resicons/icons/bin.png"));
        btnClear.addActionListener(_ -> table.model.setData(new ArrayList<>()));
        GridBagConstraints gbcBtnClear = new GridBagConstraints();
        gbcBtnClear.insets = new Insets(0, 0, 0, 5);
        gbcBtnClear.gridx = 3;
        gbcBtnClear.gridy = 1;
        add(btnClear, gbcBtnClear);
        GridBagConstraints gbcBtnStart = new GridBagConstraints();
        gbcBtnStart.gridx = 4;
        gbcBtnStart.gridy = 1;
        add(btnStart, gbcBtnStart);
    }

    /**
     * @param session
     */
    private void start(Session session) {
        new SwingWorkerProgress<Void, Void>(SwingUtilities.getWindowAncestor(this)) {
            @Override
            protected Void doInBackground() throws Exception {
                setInfos(Runtime.getRuntime().availableProcessors(), true);
                for (int i = 0; i < table.getRowCount(); i++)
                    table.setValueAt("", i, 1);
                final var cnt = new AtomicInteger();
                final var compressor = new Compressor(session, cnt, table.getRowCount(), this);
                final var useParallelism = session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.compressor_parallelism, Boolean.class);
                final var nThreads = Boolean.TRUE.equals(useParallelism) ? session.getUser().getSettings().getProperty(SettingsEnum.thread_count, Integer.class) : 1;
                try (final var mt = new MultiThreadingVirtual<FileResult>("compressor", this, nThreads, fr -> {
                    if (isCancel())
                        return;
                    compress(cnt, compressor, fr);
                })) {
                    mt.start(table.model.getData().stream());
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
     * @param extensions
     * @param files
     */
    private void addArchiveChooser(final String[] extensions, File[] files) {
        BatchCompressorTableModel model = (BatchCompressorTableModel) table.getModel();
        if (files.length > 0) {
            int startSize = model.getData().size();
            for (File f : files) {
                if (f.isDirectory()) {
                    try (final var stream = Files.walk(f.toPath())) {
                        stream.filter(p -> Files.isRegularFile(p) && FilenameUtils.isExtension(p.getFileName().toString(), extensions))
                                .forEachOrdered(p -> model.getData().add(new FileResult(p)));
                    } catch (IOException ex) {
                        Log.err(ex.getMessage(), ex);
                    }
                } else
                    model.getData().add(new FileResult(f.toPath()));
            }
            if (startSize != model.getData().size())
                model.fireTableChanged(new TableModelEvent(model, startSize, model.getData().size() - 1, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT));
        }
    }

    /**
     * @param extensions
     * 
     * @return
     */
    private javax.swing.filechooser.FileFilter getFileFilter(final String[] extensions) {
        return new javax.swing.filechooser.FileFilter() {
            @Override
            public String getDescription() {
                return "Archive files";
            }

            @Override
            public boolean accept(File f) {
                return f.isDirectory() || FilenameUtils.isExtension(f.getName(), extensions);
            }
        };
    }

    /**
     * @param cnt
     * @param compressor
     * @param fr
     * 
     * @throws IllegalArgumentException
     */
    private void compress(final AtomicInteger cnt, final Compressor compressor, FileResult fr) throws IllegalArgumentException {
        final var i = table.model.getData().indexOf(fr);
        var file = fr.getFile().toFile();
        cnt.incrementAndGet();
        Compressor.UpdResultCallBack cb = txt -> table.setValueAt(txt, i, 1);
        Compressor.UpdSrcCallBack scb = src -> table.setValueAt(src, i, 0);
        switch ((CompressorFormat) comboBox.getSelectedItem()) {
            case SEVENZIP: {
                toSevenZip(compressor, file, cb, scb);
                break;
            }
            case ZIP: {
                toZip(compressor, file, cb, scb);
                break;
            }
            case TZIP: {
                toTZip(compressor, file, cb, scb);
                break;
            }
        }
    }

    /**
     * @param compressor
     * @param file
     * @param cb
     * @param scb
     * 
     * @throws IllegalArgumentException
     */
    private void toSevenZip(final Compressor compressor, File file, Compressor.UpdResultCallBack cb, Compressor.UpdSrcCallBack scb) throws IllegalArgumentException {
        switch (FilenameUtils.getExtension(file.getName())) {
            case "zip":
                compressor.zip2SevenZip(file, cb, scb);
                break;
            case "7z":
                if (chckbxForce.isSelected())
                    compressor.sevenZip2SevenZip(file, cb, scb);
                else
                    cb.apply("Skipped");
                break;
            default:
                compressor.sevenZip2SevenZip(file, cb, scb);
                break;
        }
    }

    /**
     * @param compressor
     * @param file
     * @param cb
     * @param scb
     * 
     * @throws IllegalArgumentException
     */
    private void toZip(final Compressor compressor, File file, Compressor.UpdResultCallBack cb, Compressor.UpdSrcCallBack scb) throws IllegalArgumentException {
        if ("zip".equals(FilenameUtils.getExtension(file.getName()))) {
            if (chckbxForce.isSelected())
                compressor.zip2Zip(file, cb, scb);
            else
                cb.apply("Skipped");
        } else
            compressor.sevenZip2Zip(file, false, cb, scb);
    }

    /**
     * @param compressor
     * @param file
     * @param cb
     * @param scb
     * 
     * @throws IllegalArgumentException
     */
    private void toTZip(final Compressor compressor, File file, Compressor.UpdResultCallBack cb, Compressor.UpdSrcCallBack scb) throws IllegalArgumentException {
        if ("zip".equals(FilenameUtils.getExtension(file.getName())))
            compressor.zip2TZip(file, chckbxForce.isSelected(), cb);
        else {
            file = compressor.sevenZip2Zip(file, true, cb, scb);
            if (file != null && file.exists())
                compressor.zip2TZip(file, chckbxForce.isSelected(), cb);
        }
    }

    private static void addPopup(Component component, final JPopupMenu popup) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showMenu(e);
                }
            }

            private void showMenu(MouseEvent e) {
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }
}
