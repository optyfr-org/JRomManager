package jrm.ui.basic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JCheckBoxTree tree walks")
class JCheckBoxTreeWalkTest {

    @Test
    @DisplayName("checkSubTree selects the subtree")
    void checkSubTreeSelectsDescendants() {
        final var root = new StubNode(null, "root");
        final var child = root.addChild("child");
        final var leaf = child.addChild("leaf");
        final var tree = tree(root);

        tree.checkSubTree(new TreePath(child.getPath()), true);

        assertFalse(root.selected);
        assertTrue(child.selected);
        assertTrue(leaf.selected);
    }

    @Test
    @DisplayName("checkSubTree stops on a cyclic child graph")
    void checkSubTreeStopsOnCycle() {
        final var root = new StubNode(null, "root");
        final var a = root.addChild("a");
        final var b = a.addChild("b");
        b.children.add(a);
        final var tree = tree(root);

        assertDoesNotThrow(() -> tree.checkSubTree(new TreePath(root.getPath()), true));
        assertTrue(root.selected);
        assertTrue(a.selected);
        assertTrue(b.selected);
    }

    @Test
    @DisplayName("checkSubTree does not exceed the depth cap")
    void checkSubTreeCapsDepth() {
        final var nodes = new ArrayList<StubNode>();
        StubNode current = new StubNode(null, "n0");
        nodes.add(current);
        for (int i = 1; i < JCheckBoxTree.MAX_TREE_DEPTH + 20; i++)
            nodes.add(current = current.addChild("n" + i));
        final var tree = tree(nodes.get(0));

        tree.checkSubTree(new TreePath(nodes.get(0).getPath()), true);

        for (int i = 0; i < JCheckBoxTree.MAX_TREE_DEPTH; i++)
            assertTrue(nodes.get(i).selected);
        for (int i = JCheckBoxTree.MAX_TREE_DEPTH; i < nodes.size(); i++)
            assertFalse(nodes.get(i).selected);
    }

    @Test
    @DisplayName("updatePredecessors reflects child selection")
    void updatePredecessorsSelectsAncestors() {
        final var root = new StubNode(null, "root");
        final var child = root.addChild("child");
        final var leaf = child.addChild("leaf");
        leaf.selected = true;
        final var tree = tree(root);

        tree.updatePredecessorsWithCheckMode(new TreePath(leaf.getPath()));

        assertTrue(child.selected);
        assertTrue(root.selected);
    }

    @Test
    @DisplayName("updatePredecessors stops on a cyclic parent chain")
    void updatePredecessorsStopsOnCycle() {
        final var a = new StubNode(null, "a");
        final var b = a.addChild("b");
        a.parent = b;
        b.selected = true;
        final var tree = tree(a);

        assertDoesNotThrow(() -> tree.updatePredecessorsWithCheckMode(new TreePath(new TreeNode[] { b, a, b })));
        assertTrue(a.selected);
    }

    @Test
    @DisplayName("updatePredecessors does not exceed the depth cap")
    void updatePredecessorsCapsDepth() {
        final var nodes = new ArrayList<StubNode>();
        StubNode current = new StubNode(null, "n0");
        nodes.add(current);
        for (int i = 1; i < JCheckBoxTree.MAX_TREE_DEPTH + 20; i++)
            nodes.add(current = current.addChild("n" + i));
        nodes.get(nodes.size() - 1).selected = true;
        final var tree = tree(nodes.get(0));
        final var path = new TreePath(nodes.toArray(TreeNode[]::new));

        tree.updatePredecessorsWithCheckMode(path);

        assertFalse(nodes.get(0).selected);
        assertTrue(nodes.get(nodes.size() - 1 - JCheckBoxTree.MAX_TREE_DEPTH).selected);
        assertFalse(nodes.get(nodes.size() - 2 - JCheckBoxTree.MAX_TREE_DEPTH).selected);
    }

    private static JCheckBoxTree tree(final StubNode root) {
        return new JCheckBoxTree(new DefaultTreeModel(root));
    }

    private static final class StubNode extends AbstractNGTreeNode {
        private TreeNode parent;
        private final String name;
        private final List<StubNode> children = new ArrayList<>();
        private boolean selected;

        private StubNode(final TreeNode parent, final String name) {
            this.parent = parent;
            this.name = name;
        }

        private StubNode addChild(final String childName) {
            final var child = new StubNode(this, childName);
            children.add(child);
            return child;
        }

        @Override
        public TreeNode getParent() {
            return parent;
        }

        @Override
        public TreeNode getChildAt(final int childIndex) {
            return children.get(childIndex);
        }

        @Override
        public int getChildCount() {
            return children.size();
        }

        @Override
        public int getIndex(final TreeNode node) {
            return children.indexOf(node);
        }

        @Override
        public boolean getAllowsChildren() {
            return true;
        }

        @Override
        public boolean isLeaf() {
            return children.isEmpty();
        }

        @Override
        public Enumeration<? extends TreeNode> children() {
            return Collections.enumeration(children);
        }

        @Override
        public Object getUserObject() {
            return name;
        }

        @Override
        public void setSelected(final boolean selected) {
            this.selected = selected;
        }

        @Override
        public boolean isSelected() {
            return selected;
        }
    }
}
