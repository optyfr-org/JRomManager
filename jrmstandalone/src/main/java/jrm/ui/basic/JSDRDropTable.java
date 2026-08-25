package jrm.ui.basic;

import java.awt.Color;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileFilter;
import java.util.List;
import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.swing.table.JTableHeader;

import jrm.aui.basic.AbstractSrcDstResult;
import jrm.aui.basic.ResultColUpdater;
import jrm.aui.basic.SDRList;
import jrm.aui.basic.SrcDstResult;
import jrm.misc.Log;

/**
 * A table component that accepts file drops and displays source-destination-result data.
 * <p>
 * This component extends {@link JTable} to provide drag-and-drop file handling for SDR data.
 * Files can be dropped onto specific cells to populate source or destination columns. The table
 * supports cell selection, column-specific file filters, and notifies listeners when data changes.
 * </p>
 *
 * @see SDRTableModel
 * @see SrcDstResult
 */
public class JSDRDropTable extends JTable implements DropTargetListener, ResultColUpdater {
    /** The original background color, restored after drag operations. */
    private final Color color;

    /**
     * The model from {@link JTable#getModel()} to avoid cast to {@link SDRTableModel} each time.
     */
    private transient SDRTableModel model;

    /** The callback invoked when data changes. */
    private final transient AddDelCallBack addCallBack;

    /**
     * Callback interface invoked when the table data changes.
     */
    @FunctionalInterface
    public interface AddDelCallBack {
        /**
         * Called when the table data changes.
         *
         * @param files the current {@link SDRList} of {@link SrcDstResult} data
         */
        public void call(SDRList<SrcDstResult> files);
    }

    /**
     * Constructs a new SDR drop table with the specified model and callback.
     *
     * @param model the {@link SDRTableModel} to use for data management
     * @param callback the {@link AddDelCallBack} invoked when data changes
     */
    public JSDRDropTable(SDRTableModel model, AddDelCallBack callback) {
        super(model);
        setCellSelectionEnabled(true);
        this.addCallBack = callback;
        this.model = model;
        for (int i = 0; i < getColumnModel().getColumnCount(); i++) {
            getColumnModel().getColumn(i).setCellRenderer(model.getCellRenderers()[i]);
            getColumnModel().getColumn(i).setCellEditor(model.getCellEditors()[i]);
        }
        color = getBackground();
        new DropTarget(this, this);
        this.model.addTableModelListener(e -> {
            if (e.getColumn() >= 0 && model.getColumnClass(e.getColumn()).equals(Boolean.class) && e.getType() == TableModelEvent.UPDATE)
                addCallBack.call(model.getData());
        });
    }

    /**
     * {@inheritDoc}
     * <p>
     * Creates a table header that displays column-specific tooltip text from the model.
     * </p>
     *
     * @return the configured {@link JTableHeader}
     */
    @Override
    protected JTableHeader createDefaultTableHeader() {
        return new JTableHeader(columnModel) {
            @Override
            public String getToolTipText(MouseEvent e) {
                java.awt.Point p = e.getPoint();
                int index = columnModel.getColumnIndexAtX(p.x);
                int realIndex = columnModel.getColumn(index).getModelIndex();
                return ((SDRTableModel) getModel()).getColumnTT(realIndex);
            }
        };
    }

    /**
     * {@inheritDoc}
     * <p>
     * No action is taken on drag enter.
     * </p>
     *
     * @param dtde the {@link DropTargetDragEvent}
     */
    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
        // do nothing
    }

    /**
     * {@inheritDoc}
     * <p>
     * Updates the current row and column hover state and changes the background color to indicate
     * valid or invalid drop targets.
     * </p>
     *
     * @param dtde the {@link DropTargetDragEvent}
     */
    @Override
    public void dragOver(DropTargetDragEvent dtde) {
        final Transferable transferable = dtde.getTransferable();
        if (isEnabled() && transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            Point point = dtde.getLocation();
            int oldRow = model.getCurrentRow();
            int oldCol = model.getCurrentCol();
            int row = model.setCurrentRow(rowAtPoint(point));
            int col = model.setCurrentCol(columnAtPoint(point));
            if (oldCol != col || oldRow != row) {
                if (row == -1) {
                    model.setCurrentRow(-1);
                    setBackground(Color.decode("#DDFFDD")); //$NON-NLS-1$
                    model.fireTableChanged(new TableModelEvent(model));
                } else {
                    setBackground(Color.white); // $NON-NLS-1$
                    model.fireTableChanged(new TableModelEvent(model, row, row, col));
                    if (oldRow != -1)
                        model.fireTableChanged(new TableModelEvent(model, oldRow, oldRow, oldCol));
                }
            }
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
        } else {
            model.setCurrentRow(-1);
            setBackground(Color.decode("#FFDDDD")); //$NON-NLS-1$
            model.fireTableChanged(new TableModelEvent(model));
            dtde.rejectDrag();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * No action is taken when the drop action changes.
     * </p>
     *
     * @param dtde the {@link DropTargetDragEvent}
     */
    @Override
    public void dropActionChanged(DropTargetDragEvent dtde) {
        // do nothing
    }

    /**
     * {@inheritDoc}
     * <p>
     * Resets the hover state and restores the original background color.
     * </p>
     *
     * @param dte the {@link DropTargetEvent}
     */
    @Override
    public void dragExit(DropTargetEvent dte) {
        model.setCurrentRow(-1);
        setBackground(color);
        model.fireTableChanged(new TableModelEvent(model));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Resets the hover state, extracts files from the transferable, applies column-specific filters,
     * and adds the files to the model at the drop location.
     * </p>
     *
     * @param dtde the {@link DropTargetDropEvent}
     */
    @Override
    public void drop(DropTargetDropEvent dtde) {
        model.setCurrentRow(-1);
        setBackground(color);
        model.fireTableChanged(new TableModelEvent(model));
        try {
            final Transferable transferable = dtde.getTransferable();

            if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                dtde.rejectDrop();
                return;
            }

            dtde.acceptDrop(DnDConstants.ACTION_COPY);

            Point point = dtde.getLocation();
            int row = rowAtPoint(point);
            int col = columnAtPoint(point);

            @SuppressWarnings("unchecked")
            final List<File> files = ((List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor)).stream().filter(f -> dropFilter(col, f))
                    .toList();
            if (!files.isEmpty()) {
                final var startSize = model.getData().size();
                for (int i = 0; i < files.size(); i++)
                    model.addFile(files.get(i), row, col, i);
                if (row != -1)
                    model.fireTableChanged(new TableModelEvent(model, row, startSize - 1, col));
                if (startSize != model.getData().size())
                    model.fireTableChanged(new TableModelEvent(model, startSize, model.getData().size() - 1, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT));
                addCallBack.call(model.getData());
                dtde.getDropTargetContext().dropComplete(true);
            } else
                dtde.getDropTargetContext().dropComplete(false);
        } catch (final UnsupportedFlavorException _) {
            dtde.rejectDrop();
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
            dtde.rejectDrop();
        }
    }

    /**
     * Applies column-specific file filters to the dropped file.
     *
     * @param col the column index where the file was dropped
     * @param f the {@link File} to filter
     * @return {@code true} if the file passes the filter or no filter is set, {@code false} otherwise
     */
    private boolean dropFilter(int col, File f) {
        FileFilter filter = null;
        if (col == 1)
            filter = model.getDstFilter();
        else if (col == 0)
            filter = model.getSrcFilter();
        if (filter != null)
            return filter.accept(f);
        return true;
    }

    /**
     * Returns the underlying SDR table model.
     *
     * @return the {@link SDRTableModel}
     */
    public SDRTableModel getSDRModel() {
        return model;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Updates the result text for the specified row and fires a table change event.
     * </p>
     *
     * @param row the row index to update
     * @param result the result text to set
     */
    @Override
    public void updateResult(int row, String result) {
        model.getData().get(row).setResult(result);
        model.fireTableChanged(new TableModelEvent(model, row, row, 2));
        addCallBack.call(model.getData());
    }

    /**
     * Removes the specified data entries from the table.
     *
     * @param sdrl the {@link SDRList} of {@link SrcDstResult} entries to remove
     */
    public void del(final SDRList<SrcDstResult> sdrl) {
        for (final AbstractSrcDstResult sdr : sdrl)
            model.getData().remove(sdr);
        model.fireTableChanged(new TableModelEvent(model));
        addCallBack.call(model.getData());
    }

    /**
     * Returns the selected rows as a list of source-destination-result entries.
     *
     * @return the {@link SDRList} of {@link SrcDstResult} corresponding to selected rows
     */
    public SDRList<SrcDstResult> getSelectedValuesList() {
        int[] rows = getSelectedRows();
        final var list = new SDRList<SrcDstResult>();
        for (int row : rows)
            list.add(model.getData().get(row));
        return list;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Clears the result text for all rows and fires a table change event.
     * </p>
     */
    @Override
    public void clearResults() {
        model.getData().forEach(r -> r.setResult(""));
        model.fireTableChanged(new TableModelEvent(model, 0, model.getRowCount() - 1, 2));
        addCallBack.call(model.getData());
    }

    /**
     * Invokes the callback with the current data.
     */
    public void call() {
        addCallBack.call(model.getData());
    }
}
