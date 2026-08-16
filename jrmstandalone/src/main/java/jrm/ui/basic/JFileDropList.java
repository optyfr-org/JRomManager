/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui.basic;

import java.awt.Color;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Collections;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JList;

/**
 * A list component that accepts file drops via drag-and-drop.
 * <p>
 * This component extends {@link JList} to display dropped files and implements {@link JFileDrop}
 * for shared drop handling logic. The background color changes to indicate valid or invalid drop targets.
 * A callback is invoked whenever files are added or removed from the list.
 * </p>
 *
 * @see JFileDrop
 * @see JFileDropMode
 */
public class JFileDropList extends JList<File> implements JFileDrop {

    /** The original background color, restored after drag operations. */
    private final Color color;

    /** The callback invoked when files are added or removed. */
    private final transient AddDelCallBack addCallBack;

    /** The current drop mode controlling which file types are accepted. */
    private JFileDropMode mode = JFileDropMode.FILE;

    /** The optional filename filter applied to dropped files. */
    private transient FilenameFilter filter = null;

    /**
     * Callback interface invoked when the file list changes.
     */
    @FunctionalInterface
    public interface AddDelCallBack {

        /**
         * Called when files are added to or removed from the list.
         *
         * @param files the current list of {@link File} objects in the list
         */
        public void call(List<File> files);
    }

    /**
     * Constructs a new file drop list.
     *
     * @param addCallBack the {@link AddDelCallBack} invoked when files are added or removed
     */
    public JFileDropList(final AddDelCallBack addCallBack) {
        super(new DefaultListModel<>());
        color = getBackground();
        this.addCallBack = addCallBack;
        new DropTarget(this, this);
    }

    /**
     * Sets the drop mode controlling which file types are accepted.
     *
     * @param mode the {@link JFileDropMode} to set
     */
    public void setMode(JFileDropMode mode) {
        this.mode = mode;
    }

    /**
     * Sets the filename filter applied to dropped files.
     *
     * @param filter the {@link FilenameFilter} to set, or {@code null} to accept all files
     */
    public void setFilter(FilenameFilter filter) {
        this.filter = filter;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Changes the background color to green for valid drops or red for invalid drops.
     * </p>
     *
     * @param dtde the {@link DropTargetDragEvent}
     */
    @Override
    public void dragEnter(final DropTargetDragEvent dtde) {
        final Transferable transferable = dtde.getTransferable();
        if (isEnabled() && transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            setBackground(Color.decode("#DDFFDD")); //$NON-NLS-1$
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
        } else {
            setBackground(Color.decode("#FFDDDD")); //$NON-NLS-1$
            dtde.rejectDrag();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Restores the original background color.
     * </p>
     *
     * @param dte the {@link DropTargetEvent}
     */
    @Override
    public void dragExit(final DropTargetEvent dte) {
        setBackground(color);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Restores the background color and delegates to the shared drop handler.
     * </p>
     *
     * @param dtde the {@link DropTargetDropEvent}
     */
    @Override
    public void drop(final DropTargetDropEvent dtde) {
        setBackground(color);
        drop(dtde, this::add);
    }

    /**
     * {@inheritDoc}
     *
     * @param transferable the {@link Transferable} to check
     * @return {@code true} if the transferable supports the Java file list data flavor
     */
    @Override
    public boolean isFlavorSupported(Transferable transferable) {
        return transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    /**
     * {@inheritDoc}
     *
     * @param files the list of {@link File} objects to validate
     * @return {@code true} if the list is not empty
     */
    @Override
    public boolean checkValid(List<File> files) {
        return !files.isEmpty();
    }

    /**
     * Adds the specified files to the list model and invokes the callback.
     *
     * @param files the list of {@link File} objects to add
     */
    public void add(final List<File> files) {
        for (final File file : files)
            getModel().addElement(file);
        addCallBack.call(Collections.list(getModel().elements()));
    }

    /**
     * Adds the specified files to the list model and invokes the callback.
     *
     * @param files the array of {@link File} objects to add
     */
    public void add(final File[] files) {
        for (final File file : files)
            getModel().addElement(file);
        addCallBack.call(Collections.list(getModel().elements()));
    }

    /**
     * Removes the specified files from the list model and invokes the callback.
     *
     * @param files the list of {@link File} objects to remove
     */
    public void del(final List<File> files) {
        for (final File file : files)
            getModel().removeElement(file);
        addCallBack.call(Collections.list(getModel().elements()));
    }

    /**
     * Removes the specified files from the list model and invokes the callback.
     *
     * @param files the array of {@link File} objects to remove
     */
    public void del(final File[] files) {
        for (final File file : files)
            getModel().removeElement(file);
        addCallBack.call(Collections.list(getModel().elements()));
    }

    /**
     * {@inheritDoc}
     *
     * @return the underlying {@link DefaultListModel} cast to the appropriate type
     */
    @Override
    public DefaultListModel<File> getModel() {
        return (DefaultListModel<File>) super.getModel();
    }

    /**
     * {@inheritDoc}
     *
     * @return the current {@link JFileDropMode}
     */
    @Override
    public JFileDropMode getMode() {
        return mode;
    }

    /**
     * {@inheritDoc}
     *
     * @return the current {@link FilenameFilter}, or {@code null} if none is set
     */
    @Override
    public FilenameFilter getFilter() {
        return filter;
    }
}
