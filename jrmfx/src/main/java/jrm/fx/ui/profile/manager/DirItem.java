package jrm.fx.ui.profile.manager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;

import javafx.scene.control.TreeItem;
import javafx.scene.image.ImageView;
import jrm.fx.ui.MainFrame;
import jrm.profile.manager.Dir;

/**
 * A tree item representing a directory in the profile manager.
 * <p>
 * Displays a folder icon and recursively builds the directory tree structure.
 * Supports reloading the tree when the directory contents change.
 *
 * @since 2.5
 */
public class DirItem extends TreeItem<Dir> {

    /**
     * Maximum directory nesting scanned from the root. Deeper folders are omitted.
     */
    static final int MAX_DIR_DEPTH = 100;

    /**
     * Constructs a directory item from a file.
     *
     * @param file the directory file
     */
    public DirItem(File file) {
        super(new Dir(file, "/"));
        setExpanded(true);
        ImageView i = new ImageView((MainFrame.getIcon("/jrm/resicons/folder_open.png")));
        i.setPreserveRatio(true);
        i.getStyleClass().add("icon");
        setGraphic(i);
        buildDirTree(getValue(), this);
    }

    /**
     * Constructs a directory item from a Dir.
     *
     * @param dir the directory
     */
    private DirItem(Dir dir) {
        super(dir);
        ImageView i = new ImageView((MainFrame.getIcon("/jrm/resicons/folder_open.png")));
        i.setPreserveRatio(true);
        i.getStyleClass().add("icon");
        setGraphic(i);
    }

    /**
     * Builds the directory tree by iterating over the given directory's
     * subdirectories and adding child {@link DirItem} nodes.
     * Walks iteratively with a depth cap and canonical-path cycle detection.
     *
     * @param dir  the current directory node to explore
     * @param node the parent tree node to which children are added
     */
    private void buildDirTree(final Dir dir, final DirItem node) {
        if (dir == null || node == null)
            return;
        final var pending = new ArrayDeque<Pending>();
        pending.add(new Pending(dir, node, 0));
        final var visited = new HashSet<String>();
        while (!pending.isEmpty())
            visitDirEntry(pending.removeFirst(), pending, visited);
    }

    private static void visitDirEntry(final Pending current, final ArrayDeque<Pending> pending, final HashSet<String> visited) {
        if (current.depth() >= MAX_DIR_DEPTH)
            return;
        final var currentDir = current.dir();
        if (currentDir == null)
            return;
        final var dirfile = currentDir.getFile();
        if (dirfile == null || !dirfile.isDirectory())
            return;
        final String canonical = canonicalPath(dirfile);
        if (canonical == null || !visited.add(canonical))
            return;
        enqueueSubDirs(current, pending, dirfile);
    }

    private static String canonicalPath(final File dirfile) {
        try {
            return dirfile.getCanonicalPath();
        } catch (final IOException _) {
            return null;
        }
    }

    private static void enqueueSubDirs(final Pending current, final ArrayDeque<Pending> pending, final File dirfile) {
        final File[] listFiles = dirfile.listFiles();
        if (listFiles == null)
            return;
        for (final File file : listFiles) {
            if (file != null && file.isDirectory()) {
                final var childDir = new Dir(file);
                final var child = new DirItem(childDir);
                current.node().getChildren().add(child);
                pending.add(new Pending(childDir, child, current.depth() + 1));
            }
        }
    }

    private record Pending(Dir dir, DirItem node, int depth) {
    }

    /**
     * Clears all children and rebuilds the directory tree from the current node,
     * expanding the node if it is not a leaf.
     */
    public void reload() {
        getChildren().clear();
        buildDirTree(getValue(), this);
        if (!isLeaf())
            setExpanded(true);
    }

}
