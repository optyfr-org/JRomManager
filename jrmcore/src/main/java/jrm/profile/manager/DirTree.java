package jrm.profile.manager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;

import jrm.misc.Tree;

/**
 * Models a tree structure of filesystem directories. Extends the generic {@link Tree} container with {@link Dir} elements to allow
 * hierarchical traversal and rendering of physical or virtual folder structures.
 * 
 * @author optyfr
 */
public class DirTree extends Tree<Dir> {
    /**
     * Maximum directory nesting scanned from the root. Deeper folders are omitted.
     */
    static final int MAX_DIR_DEPTH = 100;

    /**
     * Constructs a new directory tree with a specified root directory node without performing recursive scanning.
     * 
     * @param rootData the directory instance serving as the root node
     */
    public DirTree(Dir rootData) {
        super(rootData);
    }

    /**
     * Constructs a new directory tree starting from a physical filesystem root folder. This triggers a filesystem
     * discovery to build the complete node hierarchy.
     * 
     * @param root the physical root folder on disk
     */
    public DirTree(final File root) {
        super(new Dir(root, "/")); //$NON-NLS-1$
        buildDirTree(getRoot());
    }

    /**
     * Scans the filesystem for directories starting from the given node, attaching any discovered subdirectories as
     * child nodes in the tree structure. Walks iteratively with a depth cap and canonical-path cycle detection.
     * 
     * @param node the tree node containing the directory to explore
     */
    private void buildDirTree(Node<Dir> node) {
        final var pending = new ArrayDeque<Pending>();
        pending.add(new Pending(node, 0));
        final var visited = new HashSet<String>();
        while (!pending.isEmpty()) {
            final var current = pending.removeFirst();
            if (current.depth() >= MAX_DIR_DEPTH)
                continue;
            final var data = current.node().getData();
            if (data == null)
                continue;
            final var dirfile = data.getFile();
            if (dirfile == null || !dirfile.isDirectory())
                continue;
            final String canonical;
            try {
                canonical = dirfile.getCanonicalPath();
            } catch (final IOException _) {
                continue;
            }
            if (!visited.add(canonical))
                continue;
            final File[] listFiles = dirfile.listFiles();
            if (listFiles == null)
                continue;
            for (final File file : listFiles) {
                if (file != null && file.isDirectory())
                    pending.add(new Pending(current.node().addChild(new Dir(file)), current.depth() + 1));
            }
        }
    }

    private record Pending(Node<Dir> node, int depth) {
    }
}
