package jrm.ui.profile.report;

import java.awt.Desktop;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeSelectionModel;

import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.profile.report.FilterOptions;
import jrm.profile.report.Report;
import jrm.ui.MainFrame;
import jrm.ui.basic.Popup;
import jrm.ui.profile.report.ReportNode.SubjectNode;
import jrm.ui.profile.report.ReportNode.SubjectNode.NoteNode;

/**
 * Scrollable view displaying scan reports as a tree.
 * <p>
 * Provides filtering options, context menus for node operations, and detail views.
 */
public class ReportView extends JScrollPane implements Popup {
    /** The report data. */
    private final Report report;
    /** Loading indicator label. */
    private final JLabel wait;
    /** The tree component displaying the report hierarchy. */
    private final JTree tree;

    public ReportView(Report report) {
        this.report = report;
        tree = new JTree();
        tree.setShowsRootHandles(true);
        tree.setRootVisible(false);
        TreeSelectionModel selmodel = new DefaultTreeSelectionModel();
        selmodel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setSelectionModel(selmodel);
        wait = new JLabel("Building tree...");
        wait.setFont(getFont().deriveFont(14.0f));
        wait.setHorizontalAlignment(SwingConstants.CENTER);
        wait.setBorder(new EmptyBorder(5, 5, 5, 5));
        setViewportView(wait);

        build(report);

        final JPopupMenu popupMenu = new JPopupMenu();
        Popup.addPopup(tree, popupMenu);

        final JMenuItem mntmOpenAllNodes = new JMenuItem(Messages.getString("ReportFrame.mntmOpenAllNodes.text")); //$NON-NLS-1$
        mntmOpenAllNodes.setIcon(MainFrame.getIcon("/jrm/resicons/folder_open.png")); //$NON-NLS-1$
        mntmOpenAllNodes.addActionListener(_ -> openAllNodes());
        popupMenu.add(mntmOpenAllNodes);

        final JCheckBoxMenuItem chckbxmntmShowOkEntries = new JCheckBoxMenuItem(Messages.getString("ReportFrame.chckbxmntmShowOkEntries.text")); //$NON-NLS-1$
        chckbxmntmShowOkEntries.setIcon(MainFrame.getIcon("/jrm/resicons/folder_closed_green.png")); //$NON-NLS-1$
        chckbxmntmShowOkEntries.addItemListener(e -> showOKEntries(report, e));

        final JMenuItem mntmCloseAllNodes = new JMenuItem(Messages.getString("ReportFrame.mntmCloseAllNodes.text")); //$NON-NLS-1$
        mntmCloseAllNodes.addActionListener(_ -> closeAllNodes());
        mntmCloseAllNodes.setIcon(MainFrame.getIcon("/jrm/resicons/folder_closed.png")); //$NON-NLS-1$
        popupMenu.add(mntmCloseAllNodes);
        popupMenu.add(chckbxmntmShowOkEntries);

        final JCheckBoxMenuItem chckbxmntmHideFullyMissing = new JCheckBoxMenuItem(Messages.getString("ReportFrame.chckbxmntmHideFullyMissing.text")); //$NON-NLS-1$
        chckbxmntmHideFullyMissing.setIcon(MainFrame.getIcon("/jrm/resicons/folder_closed_red.png")); //$NON-NLS-1$
        chckbxmntmHideFullyMissing.addItemListener(e -> hideFullyMissing(report, e));
        popupMenu.add(chckbxmntmHideFullyMissing);

        popupMenu.addSeparator();

        JMenuItem mntmDetail = new JMenuItem(Messages.getString("ReportView.mntmDetail.text")); //$NON-NLS-1$
        mntmDetail.addActionListener(_ -> showDetail());
        mntmDetail.setEnabled(false);
        popupMenu.add(mntmDetail);

        JMenuItem mntmCopyCRC = new JMenuItem("Copy CRC");
        mntmCopyCRC.setEnabled(false);
        mntmCopyCRC.addActionListener(_ -> copyCRC());
        popupMenu.add(mntmCopyCRC);

        JMenuItem mntmCopySHA1 = new JMenuItem("Copy SHA1");
        mntmCopySHA1.setEnabled(false);
        mntmCopySHA1.addActionListener(_ -> copySHA1());
        popupMenu.add(mntmCopySHA1);

        JMenuItem mntmCopyName = new JMenuItem("Copy Name");
        mntmCopyName.setEnabled(false);
        mntmCopyName.addActionListener(_ -> copyName());
        popupMenu.add(mntmCopyName);

        JMenuItem mntmSearchWeb = new JMenuItem("Search on the Web");
        mntmSearchWeb.setEnabled(false);
        mntmSearchWeb.addActionListener(_ -> searchOnTheWeb());
        popupMenu.add(mntmSearchWeb);

        tree.addTreeSelectionListener(e -> {
            final var path = e.getNewLeadSelectionPath();
            if (path != null) {
                final var node = path.getLastPathComponent();
                mntmDetail.setEnabled(node instanceof NoteNode);
                mntmCopyCRC.setEnabled(node instanceof NoteNode);
                mntmCopySHA1.setEnabled(node instanceof NoteNode);
                mntmCopyName.setEnabled(node instanceof NoteNode);
                mntmSearchWeb.setEnabled(node instanceof NoteNode);
            } else {
                mntmDetail.setEnabled(false);
                mntmCopyCRC.setEnabled(false);
                mntmCopySHA1.setEnabled(false);
                mntmCopyName.setEnabled(false);
                mntmSearchWeb.setEnabled(false);
            }
        });
    }

    /**
     * 
     */
    private void searchOnTheWeb() {
        final var path = tree.getSelectionPath();
        if (path != null) {
            Object node = path.getLastPathComponent();
            if (node instanceof NoteNode nn && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                try {
                    final var name = nn.getNote().getName();
                    final var crc = nn.getNote().getCrc();
                    final var sha1 = nn.getNote().getSha1();
                    final var hash = Optional.ofNullable(Optional.ofNullable(crc).orElse(sha1)).map(h -> '+' + h).orElse("");
                    Desktop.getDesktop().browse(new URI("https://www.google.com/search?q=" + URLEncoder.encode('"' + name + '"', "UTF-8") + hash));
                } catch (IOException | URISyntaxException e1) {
                    Log.err(e1.getMessage(), e1);
                }
            }
        }
    }

    /**
     * @throws HeadlessException
     */
    private void copyName() throws HeadlessException {
        final var path = tree.getSelectionPath();
        if (path != null) {
            Object node = path.getLastPathComponent();
            if (node instanceof NoteNode nn) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(nn.getNote().getName()), null);
            }
        }
    }

    /**
     * @throws HeadlessException
     */
    private void copySHA1() throws HeadlessException {
        final var path = tree.getSelectionPath();
        if (path != null) {
            Object node = path.getLastPathComponent();
            if (node instanceof NoteNode nn) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(nn.getNote().getSha1()), null);
            }
        }
    }

    /**
     * @throws HeadlessException
     */
    private void copyCRC() throws HeadlessException {
        final var path = tree.getSelectionPath();
        if (path != null) {
            Object node = path.getLastPathComponent();
            if (node instanceof NoteNode nn) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(nn.getNote().getCrc()), null);
            }
        }
    }

    /**
     * @throws HeadlessException
     */
    private void showDetail() throws HeadlessException {
        final var path = tree.getSelectionPath();
        if (path != null) {
            Object node = path.getLastPathComponent();
            if (node instanceof NoteNode nn) {
                final var msg = nn.getNote().getDetail();
                JOptionPane.showMessageDialog(this, new JTextArea(msg), "Details", JOptionPane.INFORMATION_MESSAGE);
            } else if (node instanceof SubjectNode) {
                // not supported
            }
        }
    }

    /**
     * @param report
     * @param e
     */
    private void hideFullyMissing(Report report, ItemEvent e) {
        final Set<FilterOptions> options = report.getHandler().getFilterOptions();
        if (e.getStateChange() == ItemEvent.SELECTED)
            options.add(FilterOptions.HIDEMISSING);
        else
            options.remove(FilterOptions.HIDEMISSING);
        update(options.toArray(FilterOptions[]::new));
    }

    /**
     * 
     */
    private void closeAllNodes() {
        tree.invalidate();
        int j = tree.getRowCount();
        int i = 0;
        while (i < j) {
            tree.collapseRow(i);
            i += 1;
            j = tree.getRowCount();
        }
        tree.validate();
    }

    /**
     * @param report
     * @param e
     */
    private void showOKEntries(Report report, ItemEvent e) {
        final Set<FilterOptions> options = report.getHandler().getFilterOptions();
        if (e.getStateChange() == ItemEvent.SELECTED)
            options.add(FilterOptions.SHOWOK);
        else
            options.remove(FilterOptions.SHOWOK);
        update(options.toArray(FilterOptions[]::new));
    }

    /**
     * 
     */
    private void openAllNodes() {
        tree.invalidate();
        int j = tree.getRowCount();
        int i = 0;
        while (i < j) {
            tree.expandRow(i);
            i += 1;
            j = tree.getRowCount();
        }
        tree.validate();
    }

    /**
     * @param report
     */
    private void build(Report report) {
        new SwingWorker<TreeModel, Void>() {
            @Override
            protected TreeModel doInBackground() throws Exception {
                return new ReportTreeModel(report.getHandler());
            }

            @Override
            protected void done() {
                try {
                    tree.setModel(get());
                    tree.setCellRenderer(new ReportTreeCellRenderer());
                    ReportView.this.setViewportView(tree);
                } catch (InterruptedException e) {
                    Log.err(e.getMessage(), e);
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Log.err(e.getMessage(), e);
                } finally {
                    setEnabled(true);
                }
            }
        }.execute();
    }

    public void update() {
        update(new FilterOptions[0]);
    }

    private void update(FilterOptions[] options) {
        setViewportView(wait);
        new SwingWorker<FilterOptions[], Void>() {

            @Override
            protected FilterOptions[] doInBackground() throws Exception {
                return options;
            }

            @Override
            protected void done() {
                try {
                    setViewportView(tree);
                    report.getHandler().filter(get());
                } catch (InterruptedException e) {
                    Log.err(e.getMessage(), e);
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Log.err(e.getMessage(), e);
                }
            }
        }.execute();

    }

}
