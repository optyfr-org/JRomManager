package jrm.ui.basic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import javax.swing.tree.TreeNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AbstractNGTreeNode.getPathToRoot")
class AbstractNGTreeNodePathTest {

    @Test
    @DisplayName("returns root-to-node path")
    void returnsRootToNodePath() {
        final var root = new StubNode(null);
        final var child = new StubNode(root);
        final var leaf = new StubNode(child);

        assertArrayEquals(new TreeNode[] { root, child, leaf }, leaf.getPath());
        assertArrayEquals(new TreeNode[] { root }, root.getPath());
    }

    @Test
    @DisplayName("stops on a cyclic parent chain")
    void stopsOnCyclicParents() {
        final var a = new StubNode(null);
        final var b = new StubNode(a);
        a.parent = b;

        assertDoesNotThrow(a::getPath);
        assertArrayEquals(new TreeNode[] { b, a }, a.getPath());
    }

    @Test
    @DisplayName("does not exceed the path depth cap")
    void capsDeepParentChain() {
        StubNode current = new StubNode(null);
        final var leaf = current;
        for (int i = 0; i < AbstractNGTreeNode.MAX_PATH_DEPTH + 20; i++) {
            final var parent = new StubNode(null);
            current.parent = parent;
            current = parent;
        }

        final TreeNode[] path = leaf.getPath();
        assertEquals(AbstractNGTreeNode.MAX_PATH_DEPTH, path.length);
        assertSame(leaf, path[path.length - 1]);
    }

    @Test
    @DisplayName("returns an empty path for a null node")
    void nullNodeReturnsEmpty() {
        final var node = new StubNode(null);
        assertEquals(0, node.getPathToRoot(null, 0).length);
    }

    private static final class StubNode extends AbstractNGTreeNode {
        private TreeNode parent;

        private StubNode(final TreeNode parent) {
            this.parent = parent;
        }

        @Override
        public TreeNode getParent() {
            return parent;
        }
    }
}
