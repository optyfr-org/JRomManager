package jrm.fx.ui;

import java.net.URL;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import jrm.batch.TrntChkReport;
import jrm.batch.TrntChkReport.Child;
import jrm.batch.TrntChkReport.Status;
import jrm.profile.report.FilterOptions;
import lombok.Getter;

/**
 * FXML controller for the batch torrent check results dialog.
 * <p>
 * Displays a tree view of torrent check results with color-coded status indicators
 * and context menu options for expanding/collapsing nodes and filtering by status.
 *
 * @since 2.5
 */
public class BatchTorrentResultsController implements Initializable {
    /**
     * The tree view displaying the results.
     * @return the tree view
     */
    @FXML
    @Getter
    private TreeView<Child> treeview;
    /** The context menu. */
    @FXML
    private ContextMenu menu;
    /** Menu item to expand all nodes. */
    @FXML
    private MenuItem openAllNodes;
    /** Menu item to collapse all nodes. */
    @FXML
    private MenuItem closeAllNodes;
    /** Menu item to show OK entries. */
    @FXML
    private CheckMenuItem showok;
    /** Menu item to hide missing entries. */
    @FXML
    private CheckMenuItem hidemissing;

    /** The active filter options. */
    private static final EnumSet<FilterOptions> filterOptions = EnumSet.noneOf(FilterOptions.class);

    /** The torrent check report. */
    private TrntChkReport report;

    /**
     * Maximum report-tree nesting built or expanded. Deeper nodes are omitted.
     */
    static final int MAX_TREE_DEPTH = 100;

    /**
     * Initializes the controller.
     *
     * @param location the location of the FXML file
     * @param resources the resources for the FXML file
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        treeview.setCellFactory(_ -> new TreeCell<>() {
            @Override
            protected void updateItem(Child item, boolean empty) {
                super.updateItem(item, empty);
                if (empty)
                    setText("");
                else {
                    final var str = new StringBuilder(item.getData().getTitle());
                    Optional.ofNullable(item.getData().getLength()).ifPresent(l -> str.append(" (" + l + ")"));
                    Optional.ofNullable(item.getData().getStatus()).ifPresent(s -> str.append(" [" + s + "]"));
                    setText(str.toString());
                    final Image icon;
                    if (!getTreeItem().isLeaf())
                        icon = MainFrame.getIcon("/jrm/resicons/folder" + (getTreeItem().isExpanded() ? "_open" : "_closed") + statusColor(item.getData().getStatus()) + ".png");
                    else
                        icon = MainFrame.getIcon("/jrm/resicons/icons/bullet" + statusColor(item.getData().getStatus()) + ".png");
                    setGraphic(new ImageView(icon));
                }

            }
        });
    }

    /**
     * Returns the color suffix for the given status.
     *
     * @param status the status to map
     * @return the color suffix string (e.g., "_green", "_red")
     */
    protected String statusColor(final Status status) {
        return switch (status) {
            case OK -> "_green";
            case MISSING -> "_red";
            case SHA1 -> "_purple";
            case SIZE -> "_blue";
            case SKIPPED -> "_orange";
            case UNKNOWN -> "_gray";
            default -> "";
        };
    }

    /**
     * Handles the "OK" button action.
     *
     * @param e the action event
     */
    @FXML
    private void onOK(ActionEvent e) {
        treeview.getScene().getWindow().hide();
    }

    /**
     * Sets the torrent check report to display.
     *
     * @param report the report to display
     */
    public void setResult(TrntChkReport report) {
        this.report = report;
        build();
    }

    /**
     * Builds the tree structure by applying the current filter and setting the root.
     */
    private void build() {
        treeview.setRoot(buildTree(null, report.filter(filterOptions)));
    }

    /**
     * Builds a tree structure from the given children list.
     * Walks iteratively with a depth cap and identity cycle detection.
     *
     * @param parent the parent tree item, or {@code null} to create a new root
     * @param children the list of child items
     * @return the built tree item
     */
    private TreeItem<Child> buildTree(TreeItem<Child> parent, List<Child> children) {
        final var root = parent == null ? new TreeItem<Child>() : parent;
        if (children == null || children.isEmpty())
            return root;
        final var pending = new ArrayDeque<BuildPending>();
        pending.add(new BuildPending(root, children, 0));
        final var seen = new IdentityHashMap<Child, Boolean>();
        while (!pending.isEmpty()) {
            final var current = pending.removeFirst();
            if (current.depth() >= MAX_TREE_DEPTH)
                continue;
            for (final var child : current.children()) {
                if (child == null || seen.put(child, Boolean.TRUE) != null)
                    continue;
                final var item = new TreeItem<>(child);
                current.parent().getChildren().add(item);
                final var next = child.getChildren();
                if (next != null && !next.isEmpty())
                    pending.add(new BuildPending(item, next, current.depth() + 1));
            }
        }
        return root;
    }

    /**
     * Handles the "Show OK" checkbox action.
     *
     * @param e the action event
     */
    @FXML
    private void showok(javafx.event.ActionEvent e) {
        if (showok.isSelected())
            filterOptions.add(FilterOptions.SHOWOK);
        else
            filterOptions.remove(FilterOptions.SHOWOK);
        build();
    }

    /**
     * Handles the "Hide Missing" checkbox action.
     *
     * @param e the action event
     */
    @FXML
    private void hidemissing(javafx.event.ActionEvent e) {
        if (hidemissing.isSelected())
            filterOptions.add(FilterOptions.HIDEMISSING);
        else
            filterOptions.remove(FilterOptions.HIDEMISSING);
        build();
    }

    /**
     * Opens all non-leaf nodes in the tree view.
     *
     * @param e the action event
     */
    @FXML
    private void openAllNodes(javafx.event.ActionEvent e) {
        final var root = treeview.getRoot();
        treeview.setRoot(null);
        setExpandedAll(root, true);
        treeview.setRoot(root);
    }

    /**
     * Closes all non-leaf nodes in the tree view.
     *
     * @param e the action event
     */
    @FXML
    private void closeAllNodes(javafx.event.ActionEvent e) {
        final var root = treeview.getRoot();
        treeview.setRoot(null);
        setExpandedAll(root, false);
        treeview.setRoot(root);
    }

    /**
     * Sets the expanded state of all non-leaf descendants.
     * Walks iteratively with a depth cap and identity cycle detection.
     *
     * @param item the tree item to start from
     * @param expanded the expanded state to apply
     */
    private static void setExpandedAll(TreeItem<?> item, boolean expanded) {
        if (item == null)
            return;
        final var pending = new ArrayDeque<ExpandPending>();
        pending.add(new ExpandPending(item, 0));
        final var seen = new IdentityHashMap<TreeItem<?>, Boolean>();
        while (!pending.isEmpty()) {
            final var current = pending.removeFirst();
            if (current.depth() >= MAX_TREE_DEPTH)
                continue;
            final var node = current.item();
            if (node == null || seen.put(node, Boolean.TRUE) != null || node.isLeaf())
                continue;
            node.setExpanded(expanded);
            for (final var child : node.getChildren())
                pending.add(new ExpandPending(child, current.depth() + 1));
        }
    }

    private record BuildPending(TreeItem<Child> parent, List<Child> children, int depth) {
    }

    private record ExpandPending(TreeItem<?> item, int depth) {
    }

}
