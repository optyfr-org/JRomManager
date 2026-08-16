/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui.basic;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EventListener;
import java.util.EventObject;

import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * A tree component that renders nodes with checkboxes supporting tristate selection.
 * <p>
 * This component extends {@link JTree} to provide checkbox-based selection for tree nodes.
 * Nodes can be individually selected, and parent nodes automatically reflect the selection
 * state of their children (selected, unselected, or partially selected). Supports bulk
 * operations and notifies listeners of selection changes.
 * </p>
 *
 * @see NGTreeNode
 * @see JTristateCheckBox
 * @see JTree
 */
public class JCheckBoxTree extends JTree {
    /**
     * Event fired when the selection state of tree nodes changes.
     */
    public class CheckChangeEvent extends EventObject {

        /**
         * Constructs a new check change event.
         *
         * @param source the object that triggered the event
         */
        public CheckChangeEvent(final Object source) {
            super(source);
        }
    }

    /**
     * Listener interface for receiving check change events from the tree.
     */
    public interface CheckChangeEventListener extends EventListener {

        /**
         * Called when the selection state of tree nodes changes.
         *
         * @param event the {@link CheckChangeEvent} containing details about the change
         */
        public void checkStateChanged(CheckChangeEvent event);
    }

    /**
     * Adds a listener to receive check change events.
     *
     * @param listener the {@link CheckChangeEventListener} to add
     */
    public void addCheckChangeEventListener(final CheckChangeEventListener listener) {
        listenerList.add(CheckChangeEventListener.class, listener);
    }

    /**
     * Removes a previously registered check change event listener.
     *
     * @param listener the {@link CheckChangeEventListener} to remove
     */
    public void removeCheckChangeEventListener(final CheckChangeEventListener listener) {
        listenerList.remove(CheckChangeEventListener.class, listener);
    }

    /**
     * Notifies all registered listeners of a check change event.
     *
     * @param evt the {@link CheckChangeEvent} to fire
     */
    void fireCheckChangeEvent(final CheckChangeEvent evt) {
        final Object[] listeners = listenerList.getListenerList();
        for (int i = 0; i < listeners.length; i++) {
            if (listeners[i] == CheckChangeEventListener.class) {
                ((CheckChangeEventListener) listeners[i + 1]).checkStateChanged(evt);
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Also enables or disables the tree based on whether the model has children.
     * </p>
     *
     * @param newModel the {@link TreeModel} to set
     */
    @Override
    public void setModel(final TreeModel newModel) {
        super.setModel(newModel);
        setEnabled(newModel.getChildCount(newModel.getRoot()) > 0);
    }

    /**
     * Checks whether the node at the given path is partially selected.
     * <p>
     * A node is partially selected if it is selected but not all of its children are selected.
     * </p>
     *
     * @param path the {@link TreePath} to check
     * @return {@code true} if the node is partially selected, {@code false} otherwise
     */
    public boolean isSelectedPartially(final TreePath path) {
        final NGTreeNode cn = (NGTreeNode) path.getLastPathComponent();
        return cn.isSelected() && cn.getChildCount() > 0 && !cn.allChildrenSelected();
    }

    /**
     * Cell renderer that displays tree nodes with tristate checkboxes.
     */
    private class CheckBoxCellRenderer extends JPanel implements TreeCellRenderer {

        /** The checkbox used to render each tree node. */
        JTristateCheckBox checkBox;

        /**
         * Constructs a new checkbox cell renderer.
         */
        public CheckBoxCellRenderer() {
            super();
            setLayout(new BorderLayout());
            checkBox = new JTristateCheckBox();
            checkBox.setOpaque(false);
            checkBox.setFont(getFont());
            add(checkBox, BorderLayout.CENTER);
            setOpaque(false);
        }

        /**
         * {@inheritDoc}
         * <p>
         * Configures the checkbox to reflect the node's selection state, text, and partial selection state.
         * </p>
         *
         * @param tree the enclosing {@link JTree}
         * @param value the value of the current node
         * @param selected whether the node is selected
         * @param expanded whether the node is expanded
         * @param leaf whether the node is a leaf
         * @param row the row index
         * @param hasFocus whether the node has focus
         * @return the configured {@link Component} for rendering
         */
        @Override
        public Component getTreeCellRendererComponent(final JTree tree, final Object value, final boolean selected, final boolean expanded, final boolean leaf, final int row,
                final boolean hasFocus) {
            final NGTreeNode node = (NGTreeNode) value;
            if (node == null)
                return this;
            final Object obj = node.getUserObject();
            checkBox.setEnabled(tree.isEnabled());
            checkBox.setSelected(node.isSelected());
            checkBox.setText(obj != null ? obj.toString() : null);
            checkBox.setHalfSelected(node.isSelected() && node.getChildCount() > 0 && !node.allChildrenSelected());
            setPreferredSize(new Dimension(Math.max(getPreferredSize().width, checkBox.getFontMetrics(getFont()).stringWidth(checkBox.getText()) + 100), 20));
            return this;
        }
    }

    /**
     * Constructs a new checkbox tree with the given model.
     * <p>
     * Sets up the cell renderer, disables toggle on double-click, and registers mouse listeners
     * for checkbox toggling.
     * </p>
     *
     * @param model the {@link TreeModel} to display
     */
    public JCheckBoxTree(final TreeModel model) {
        super(model);
        setToggleClickCount(0);
        setCellRenderer(new CheckBoxCellRenderer());

        final DefaultTreeSelectionModel dtsm = new DefaultTreeSelectionModel() {
            @Override
            public void setSelectionPath(final TreePath path) {
                // do nothing
            }

            @Override
            public void addSelectionPath(final TreePath path) {
                // do nothing
            }

            @Override
            public void removeSelectionPath(final TreePath path) {
                // do nothing
            }

            @Override
            public void setSelectionPaths(final TreePath[] pPaths) {
                // do nothing
            }
        };
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    if (!JCheckBoxTree.this.isEnabled())
                        return;
                    final TreePath tp = JCheckBoxTree.this.getPathForLocation(e.getX(), e.getY());
                    if (tp == null)
                        return;
                    final boolean checkMode = !((NGTreeNode) tp.getLastPathComponent()).isSelected();
                    checkSubTree(tp, checkMode);
                    updatePredecessorsWithCheckMode(tp);
                    fireCheckChangeEvent(new CheckChangeEvent(new Object()));
                    JCheckBoxTree.this.repaint();
                } else
                    super.mouseClicked(e);
            }
        });
        setSelectionModel(dtsm);
    }

    /**
     * Sets the selection state of the specified nodes and their descendants.
     * <p>
     * Updates the selection state and propagates changes to parent nodes, then fires a check change event.
     * </p>
     *
     * @param selected {@code true} to select the nodes, {@code false} to deselect them
     * @param nodes the {@link NGTreeNode} objects to update
     */
    public void setSelected(final boolean selected, final NGTreeNode... nodes) {
        for (final NGTreeNode node : nodes) {
            final TreePath tp = new TreePath(node.getPath());
            checkSubTree(tp, selected);
            updatePredecessorsWithCheckMode(tp);
        }
        fireCheckChangeEvent(new CheckChangeEvent(new Object()));
        JCheckBoxTree.this.repaint();
    }

    /**
     * Selects the specified nodes and their descendants.
     *
     * @param nodes the {@link NGTreeNode} objects to select
     */
    public void select(final NGTreeNode... nodes) {
        setSelected(true, nodes);
    }

    /**
     * Deselects the specified nodes and their descendants.
     *
     * @param nodes the {@link NGTreeNode} objects to deselect
     */
    public void unselect(final NGTreeNode... nodes) {
        setSelected(false, nodes);
    }

    /**
     * Selects all nodes in the tree.
     */
    public void selectAll() {
        select((NGTreeNode) getModel().getRoot());
    }

    /**
     * Deselects all nodes in the tree.
     */
    public void selectNone() {
        unselect((NGTreeNode) getModel().getRoot());
    }

    /**
     * Updates the selection state of all ancestor nodes based on their children's selection.
     * <p>
     * Recursively walks up the tree from the given path, setting each parent node's selection
     * state based on whether any of its children are selected.
     * </p>
     *
     * @param tp the {@link TreePath} to start updating from
     */
    protected void updatePredecessorsWithCheckMode(final TreePath tp) {
        final TreePath parentPath = tp.getParentPath();
        if (parentPath == null)
            return;
        final NGTreeNode parentNode = (NGTreeNode) parentPath.getLastPathComponent();
        parentNode.setSelected(false);
        for (int i = 0; i < parentNode.getChildCount(); i++) {
            if (((NGTreeNode) parentNode.getChildAt(i)).isSelected())
                parentNode.setSelected(true);
        }
        updatePredecessorsWithCheckMode(parentPath);
    }

    /**
     * Recursively sets the selection state of all nodes in the subtree rooted at the given path.
     *
     * @param tp the {@link TreePath} to the root of the subtree
     * @param check {@code true} to select all nodes, {@code false} to deselect all nodes
     */
    protected void checkSubTree(final TreePath tp, final boolean check) {
        final NGTreeNode node = (NGTreeNode) tp.getLastPathComponent();
        node.setSelected(check);
        for (int i = 0; i < node.getChildCount(); i++)
            checkSubTree(tp.pathByAddingChild(node.getChildAt(i)), check);
    }

}
