/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui.basic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.IdentityHashMap;

import javax.swing.tree.TreeNode;

/**
 * Abstract base implementation of {@link NGTreeNode} providing common tree node operations.
 * <p>
 * This class provides path computation, default leaf-node behavior, and recursive
 * selection-state checking for tree nodes used in the JRomManager UI.
 * </p>
 *
 * @see NGTreeNode
 * @see javax.swing.tree.TreeNode
 */
public abstract class AbstractNGTreeNode implements NGTreeNode {
    /**
     * {@inheritDoc}
     *
     * @return {@code true} if this node and all its descendants are selected
     */
    @Override
    public boolean allChildrenSelected() {
        for (int i = 0; i < getChildCount(); i++) {
            if (!((NGTreeNode) getChildAt(i)).allChildrenSelected())
                return false;
        }
        return isSelected();
    }

    /**
     * Returns the path from the root, to get to this node. The last element in the path is this node.
     *
     * @return an array of TreeNode objects giving the path, where the first element in the path is the root and the last element is
     *         this node.
     */
    @Override
    public TreeNode[] getPath() {
        return getPathToRoot(this, 0);
    }

    /**
     * Maximum parent hops collected from the starting node. Deeper ancestors are omitted.
     */
    static final int MAX_PATH_DEPTH = 100;

    /**
     * Builds the parents of node up to and including the root node, where the original node is the last element in the returned
     * array. The length of the returned array gives the node's depth in the tree.
     * Walks iteratively with a depth cap and identity cycle detection.
     *
     * @param aNode the TreeNode to get the path for
     * @param depth unused; kept for signature compatibility
     *
     * @return an array of TreeNodes giving the path from the root to the specified node
     */
    protected TreeNode[] getPathToRoot(final TreeNode aNode, int depth) {
        if (aNode == null)
            return new TreeNode[0];
        final var chain = new ArrayList<TreeNode>();
        final var seen = Collections.newSetFromMap(new IdentityHashMap<TreeNode, Boolean>());
        TreeNode current = aNode;
        while (current != null && chain.size() < MAX_PATH_DEPTH && seen.add(current)) {
            chain.add(current);
            current = current.getParent();
        }
        Collections.reverse(chain);
        return chain.toArray(TreeNode[]::new);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation returns {@code null}, indicating no children.
     * </p>
     *
     * @param childIndex the index of the child node
     * @return {@code null} in this default implementation
     */
    @Override
    public TreeNode getChildAt(int childIndex) {
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation returns {@code 0}, indicating no children.
     * </p>
     *
     * @return {@code 0} in this default implementation
     */
    @Override
    public int getChildCount() {
        return 0;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation returns {@code -1}, indicating the node is not a child.
     * </p>
     *
     * @param node the child node to find the index of
     * @return {@code -1} in this default implementation
     */
    @Override
    public int getIndex(TreeNode node) {
        return -1;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation returns {@code false}.
     * </p>
     *
     * @return {@code false} in this default implementation
     */
    @Override
    public boolean getAllowsChildren() {
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation returns {@code true}.
     * </p>
     *
     * @return {@code true} in this default implementation
     */
    @Override
    public boolean isLeaf() {
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation returns {@code null}.
     * </p>
     *
     * @return {@code null} in this default implementation
     */
    @Override
    public Enumeration<? extends TreeNode> children() {
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation returns {@code null}.
     * </p>
     *
     * @return {@code null} in this default implementation
     */
    @Override
    public Object getUserObject() {
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation is a no-op.
     * </p>
     *
     * @param selected the selection state to set
     */
    @Override
    public void setSelected(boolean selected) {
        // do nothing
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation returns {@code false}.
     * </p>
     *
     * @return {@code false} in this default implementation
     */
    @Override
    public boolean isSelected() {
        return false;
    }
}
