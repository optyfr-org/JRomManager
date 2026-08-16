package jrm.ui.batch;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

import jrm.batch.DirUpdaterResults;
import jrm.batch.DirUpdaterResults.DirUpdaterResult;
import jrm.security.Session;
import jrm.ui.basic.EnhTableModel;
import jrm.ui.basic.JTableButton;
import jrm.ui.profile.report.ReportLite;

/**
 * Scrollable view displaying batch directory update results.
 * <p>
 * Shows a table with columns for DAT/XML name, have, create, fix, miss, total, and report.
 */
public class BatchDirUpdResultsView extends JScrollPane {
    private static class ColorRenderer extends DefaultTableCellRenderer {
        private Color color;

        public ColorRenderer(Color color) {
            this.color = color;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            this.setForeground(color);
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
    }

    private final class BatchDirUpdResultsViewModel implements EnhTableModel {
        private final DirUpdaterResults results;
        private JTableButton buttons = new JTableButton();
        private final String[] headers = { "DAT/XML", "Have", "Create", "Fix", "Miss", "Total", "Report" };
        private final Class<?>[] types = { String.class, String.class, String.class, String.class, String.class, String.class, String.class };
        private final int[] widths = { 400, 40, 40, 40, 40, 40, -70 };
        final TableCellRenderer[] renderers = { null, new ColorRenderer(new Color(0, 180, 0)), new ColorRenderer(new Color(0, 0, 180)), new ColorRenderer(new Color(127, 0, 180)),
                new ColorRenderer(new Color(180, 0, 0)), null, buttons };
        final TableCellEditor[] editors = { null, null, null, null, null, null, buttons };

        private BatchDirUpdResultsViewModel(DirUpdaterResults results, Session session) {
            this.results = results;
            buttons.addHandler(
                    (int row, int _) -> new ReportLite(session, SwingUtilities.getWindowAncestor(BatchDirUpdResultsView.this), results.getResults().get(row).getDat()));
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            // do nothing
        }

        @Override
        public void removeTableModelListener(TableModelListener l) {
            // do nothing
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 6;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (results != null) {
                DirUpdaterResult result = results.getResults().get(rowIndex);
                switch (columnIndex) {
                    case 0: // dat/xml name
                        return result.getDat().toString();
                    case 1: // have
                        return result.getStats().getSetFoundOk();
                    case 2: // create
                        return result.getStats().getSetCreateComplete();
                    case 3: // fix
                        return result.getStats().getSetFoundFixComplete();
                    case 4: // miss
                        return result.getStats().getSetCreate() + result.getStats().getSetFound() + result.getStats().getSetMissing()
                                - (result.getStats().getSetCreateComplete() + result.getStats().getSetFoundFixComplete() + result.getStats().getSetFoundOk());
                    case 5: // total
                        return result.getStats().getSetCreate() + result.getStats().getSetFound() + result.getStats().getSetMissing();
                    case 6: // report button
                        return "Report";
                    default:
                        return null;
                }
            }
            return null;
        }

        @Override
        public int getRowCount() {
            return results != null ? results.getResults().size() : 0;
        }

        @Override
        public String getColumnName(int columnIndex) {
            return headers[columnIndex];
        }

        @Override
        public int getColumnCount() {
            return headers.length;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return types[columnIndex];
        }

        @Override
        public void addTableModelListener(TableModelListener l) {
            // do nothing
        }

        @Override
        public TableCellRenderer[] getCellRenderers() {
            return renderers;
        }

        @Override
        public TableCellEditor[] getCellEditors() {
            return editors;
        }

        @Override
        public int getColumnWidth(int columnIndex) {
            return widths[columnIndex];
        }

        @Override
        public String getColumnTT(int columnIndex) {
            return headers[columnIndex];
        }
    }

    private JTable table;

    /**
     * Create the panel.
     */
    public BatchDirUpdResultsView(final Session session, DirUpdaterResults results) {
        table = new JTable();
        setViewportView(table);
        setPreferredSize(new Dimension(700, 400));
        EnhTableModel model = new BatchDirUpdResultsViewModel(results, session);
        table.setModel(model);
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++)
            table.getColumnModel().getColumn(i).setCellRenderer(model.getCellRenderers()[i]);
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++)
            table.getColumnModel().getColumn(i).setCellEditor(model.getCellEditors()[i]);
        model.applyColumnsWidths(table);
    }

}
