/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui.profile.filter;

import java.util.Enumeration;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import jrm.locale.Messages;
import jrm.ui.basic.AbstractNGTreeNode;
import jrm.ui.basic.NGTreeNode;

/**
 * Tree model for displaying category/version (CatVer) filter hierarchy.
 * <p>
 * Provides a tree structure of machine categories and subcategories
 * for filtering purposes.
 */
public class CatVerModel extends DefaultTreeModel {

    /**
     * Constructs a new CatVer tree model with the specified root node.
     *
     * @param root the root tree node
     */
    public CatVerModel(final NGTreeNode root) {
        super(root);
    }

    @Override
    public NGTreeNode getRoot() {
        return (NGTreeNode) super.getRoot();
    }

    /**
     * Instantiates a new cat ver model.
     */
    public CatVerModel() {
        super(new AbstractNGTreeNode() {

            @Override
            public boolean isLeaf() {
                return true;
            }

            @Override
            public TreeNode getParent() {
                return null;
            }

            @Override
            public int getIndex(final TreeNode node) {
                return 0;
            }

            @Override
            public int getChildCount() {
                return 0;
            }

            @Override
            public TreeNode getChildAt(final int childIndex) {
                return null;
            }

            @Override
            public boolean getAllowsChildren() {
                return false;
            }

            @Override
            public Enumeration<? extends TreeNode> children() {
                return null;
            }

            @Override
            public Object getUserObject() {
                return Messages.getString("CatVerModel.AddCatVerIniFirst"); //$NON-NLS-1$
            }

            @Override
            public boolean isSelected() {
                return false;
            }

            @Override
            public void setSelected(final boolean selected) {
                // not selectable
            }
        });
    }

}
