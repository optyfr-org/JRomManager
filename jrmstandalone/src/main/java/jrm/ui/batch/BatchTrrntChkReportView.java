package jrm.ui.batch;

import java.awt.event.ItemEvent;
import java.util.Set;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;

import jrm.batch.TrntChkReport;
import jrm.locale.Messages;
import jrm.profile.report.FilterOptions;
import jrm.ui.MainFrame;
import jrm.ui.basic.Popup;

/**
 * Scrollable view displaying torrent check report as a tree.
 */
public class BatchTrrntChkReportView extends JScrollPane {
    public BatchTrrntChkReportView(TrntChkReport report) {
        final JTree tree = new JTree();
        tree.setShowsRootHandles(true);
        tree.setRootVisible(false);
        tree.setModel(new BatchTrrntChkReportTreeModel(report));
        tree.setCellRenderer(new BatchTrrntChkReportTreeCellRenderer());
        this.setViewportView(tree);

        final JPopupMenu popupMenu = new JPopupMenu();
        Popup.addPopup(tree, popupMenu);

        final JMenuItem mntmOpenAllNodes = new JMenuItem(Messages.getString("ReportFrame.mntmOpenAllNodes.text")); //$NON-NLS-1$
        mntmOpenAllNodes.setIcon(MainFrame.getIcon("/jrm/resicons/folder_open.png")); //$NON-NLS-1$
        mntmOpenAllNodes.addActionListener(_ -> {
            tree.invalidate();
            int j = tree.getRowCount();
            int i = 0;
            while (i < j) {
                tree.expandRow(i);
                i += 1;
                j = tree.getRowCount();
            }
            tree.validate();
        });
        popupMenu.add(mntmOpenAllNodes);

        final JMenuItem mntmCloseAllNodes = new JMenuItem(Messages.getString("ReportFrame.mntmCloseAllNodes.text")); //$NON-NLS-1$
        mntmCloseAllNodes.setIcon(MainFrame.getIcon("/jrm/resicons/folder_closed.png")); //$NON-NLS-1$
        mntmCloseAllNodes.addActionListener(_ -> {
            tree.invalidate();
            int j = tree.getRowCount();
            int i = 0;
            while (i < j) {
                tree.collapseRow(i);
                i += 1;
                j = tree.getRowCount();
            }
            tree.validate();
        });
        popupMenu.add(mntmCloseAllNodes);

        final JCheckBoxMenuItem chckbxmntmShowOkEntries = new JCheckBoxMenuItem(Messages.getString("ReportFrame.chckbxmntmShowOkEntries.text")); //$NON-NLS-1$
        chckbxmntmShowOkEntries.setIcon(MainFrame.getIcon("/jrm/resicons/folder_closed_green.png")); //$NON-NLS-1$
        chckbxmntmShowOkEntries.addItemListener(e -> {
            final Set<FilterOptions> options = report.getHandler().getFilterOptions();
            if (e.getStateChange() == ItemEvent.SELECTED)
                options.add(FilterOptions.SHOWOK);
            else
                options.remove(FilterOptions.SHOWOK);
            report.getHandler().filter(options.toArray(new FilterOptions[0]));
        });
        popupMenu.add(chckbxmntmShowOkEntries);

        final JCheckBoxMenuItem chckbxmntmHideFullyMissing = new JCheckBoxMenuItem(Messages.getString("ReportFrame.chckbxmntmHideFullyMissing.text")); //$NON-NLS-1$
        chckbxmntmHideFullyMissing.setIcon(MainFrame.getIcon("/jrm/resicons/folder_closed_red.png")); //$NON-NLS-1$
        chckbxmntmHideFullyMissing.addItemListener(e -> {
            final Set<FilterOptions> options = report.getHandler().getFilterOptions();
            if (e.getStateChange() == ItemEvent.SELECTED)
                options.add(FilterOptions.HIDEMISSING);
            else
                options.remove(FilterOptions.HIDEMISSING);
            report.getHandler().filter(options.toArray(new FilterOptions[0]));
        });
        popupMenu.add(chckbxmntmHideFullyMissing);
    }
}
