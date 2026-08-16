/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui.profile.manager;

import java.io.File;
import java.util.Enumeration;

import javax.swing.tree.DefaultMutableTreeNode;

import jrm.profile.manager.Dir;

/**
 * Tree node representing a directory in the profile manager's directory tree.
 * <p>
 * Wraps a {@link Dir} object and recursively builds a tree structure
 * from the filesystem. Supports reloading the directory structure
 * when the filesystem changes.
 *
 * @author optyfr
 */
public class DirNode extends DefaultMutableTreeNode {

    /** The directory data associated with this node. */
    private transient Dir dir;

    /**
     * Instantiates a new dir node.
     *
     * @param root the root
     */
    public DirNode(final File root) {
        super(new Dir(root, "/"), true); //$NON-NLS-1$
        buildDirTree(setDir((Dir) getUserObject()), this);
    }

    /**
     * Instantiates a new dir node.
     *
     * @param dir the dir
     */
    public DirNode(final Dir dir) {
        super(dir);
        this.setDir(dir);
    }

    /**
     * Builds the dir tree.
     *
     * @param dir the dir
     * @param node the node
     */
    private void buildDirTree(final Dir dir, final DefaultMutableTreeNode node) {
        if (dir == null)
            return;
        File dirfile = dir.getFile();
        if (dirfile != null && dirfile.isDirectory()) {
            File[] listFiles = dirfile.listFiles();
            if (listFiles != null) {
                for (final File file : listFiles) {
                    if (file != null && file.isDirectory()) {
                        final DefaultMutableTreeNode newdir = new DirNode(new Dir(file));
                        node.add(newdir);
                        buildDirTree(new Dir(file), newdir);
                    }

                }
            }
        }
    }

    /**
     * Reload.
     */
    public void reload() {
        removeAllChildren();
        buildDirTree(getDir(), this);
    }

    /**
     * Find.
     *
     * @param file the file
     * 
     * @return the dir node
     */
    public DirNode find(final File file) {
        return DirNode.find(this, file);
    }

    /**
     * Find.
     *
     * @param root the root
     * @param file the file
     * 
     * @return the dir node
     */
    public static DirNode find(final DirNode root, final File file) {
        final File parent = file.isFile() ? file.getParentFile() : file;
        if (parent != null) {
            for (final Enumeration<?> e = root.depthFirstEnumeration(); e.hasMoreElements();) {
                final DirNode node = (DirNode) e.nextElement();
                if (((Dir) node.getUserObject()).getFile().equals(parent))
                    return node;
            }
            return DirNode.find(root, parent.getParentFile());
        }
        return null;
    }

    /**
     * Gets the dir.
     *
     * @return the dir
     */
    public Dir getDir() {
        return dir;
    }

    /**
     * Sets the dir.
     *
     * @param dir the dir to set
     * 
     * @return the dir
     */
    public Dir setDir(Dir dir) {
        this.dir = dir;
        return dir;
    }
}
