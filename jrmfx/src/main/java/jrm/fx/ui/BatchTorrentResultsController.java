package jrm.fx.ui;

import java.net.URL;
import java.util.EnumSet;
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
     * Recursively builds a tree structure from the given children list.
     *
     * @param parent the parent tree item, or {@code null} to create a new root
     * @param children the list of child items
     * @return the built tree item
     */
    private TreeItem<Child> buildTree(TreeItem<Child> parent, List<Child> children) {
        final var p = parent == null ? new TreeItem<Child>() : parent;
        if (children != null)
            for (final var c : children)
                p.getChildren().add(buildTree(new TreeItem<>(c), c.getChildren()));
        return p;
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
     * Recursively sets the expanded state of all non-leaf descendants.
     *
     * @param item the tree item to start from
     * @param expanded the expanded state to apply
     */
    private static void setExpandedAll(TreeItem<?> item, boolean expanded) {
        if (item.isLeaf())
            return;
        item.setExpanded(expanded);
        for (TreeItem<?> child : item.getChildren())
            setExpandedAll(child, expanded);
    }

}
