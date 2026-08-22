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
import java.awt.HeadlessException;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.io.FilenameFilter;
import java.util.List;

import javax.swing.JTextField;
import javax.swing.text.Document;

/**
 * A text field component that accepts file drops via drag-and-drop.
 * <p>
 * This component extends {@link JTextField} to display the absolute path of a dropped file.
 * Implements {@link JFileDrop} for shared drop handling logic and {@link FocusListener}
 * to invoke the callback when focus is lost. The background color changes to indicate valid
 * or invalid drop targets.
 * </p>
 *
 * @see JFileDrop
 * @see JFileDropMode
 */
public class JFileDropTextField extends JTextField implements FocusListener, JFileDrop {

    /** The original background color, restored after drag operations. */
    private final Color color;

    /** The callback invoked when the text field value changes. */
    private final transient SetCallBack callback;

    /** The current drop mode controlling which file types are accepted. */
    private JFileDropMode mode = JFileDropMode.FILE;

    /** The optional filename filter applied to dropped files. */
    private transient FilenameFilter filter = null;

    /**
     * Callback interface invoked when the text field value changes.
     */
    @FunctionalInterface
    public interface SetCallBack {

        /**
         * Called when the text field value changes.
         *
         * @param txt the new text value (typically a file path)
         */
        public void call(String txt);
    }

    /**
     * Constructs a new file drop text field with default settings.
     *
     * @param callback the {@link SetCallBack} invoked when the value changes
     * @throws HeadlessException if the component is created in a headless environment
     */
    public JFileDropTextField(final SetCallBack callback) throws HeadlessException {
        this(null, "", 0, callback); //$NON-NLS-1$
    }

    /**
     * Constructs a new file drop text field with the specified initial text.
     *
     * @param text the initial text to display
     * @param callback the {@link SetCallBack} invoked when the value changes
     * @throws HeadlessException if the component is created in a headless environment
     */
    public JFileDropTextField(final String text, final SetCallBack callback) throws HeadlessException {
        this(null, text, 0, callback);
    }

    /**
     * Constructs a new file drop text field with the specified number of columns.
     *
     * @param columns the number of columns for the text field
     * @param callback the {@link SetCallBack} invoked when the value changes
     * @throws HeadlessException if the component is created in a headless environment
     */
    public JFileDropTextField(final int columns, final SetCallBack callback) throws HeadlessException {
        this(null, "", columns, callback); //$NON-NLS-1$
    }

    /**
     * Constructs a new file drop text field with the specified text and number of columns.
     *
     * @param text the initial text to display
     * @param columns the number of columns for the text field
     * @param callback the {@link SetCallBack} invoked when the value changes
     * @throws HeadlessException if the component is created in a headless environment
     */
    public JFileDropTextField(final String text, final int columns, final SetCallBack callback) throws HeadlessException {
        this(null, text, columns, callback);
    }

    /**
     * Constructs a new file drop text field with full configuration.
     *
     * @param doc the {@link Document} model for the text field, or {@code null}
     * @param text the initial text to display
     * @param columns the number of columns for the text field
     * @param callback the {@link SetCallBack} invoked when the value changes
     */
    public JFileDropTextField(final Document doc, final String text, final int columns, final SetCallBack callback) {
        super(doc, text, columns);
        this.callback = callback;
        color = JFileDropTextField.this.getBackground();
        addFocusListener(this);
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
     * No action is taken when focus is gained.
     * </p>
     *
     * @param e the {@link FocusEvent}
     */
    @Override
    public void focusGained(final FocusEvent e) {
        // do nothing
    }

    /**
     * {@inheritDoc}
     * <p>
     * Invokes the callback with the current text when focus is lost.
     * </p>
     *
     * @param e the {@link FocusEvent}
     */
    @Override
    public void focusLost(final FocusEvent e) {
        if (callback != null)
            callback.call(JFileDropTextField.this.getText());
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
        if (isFlavorSupported(transferable)) {
            JFileDropTextField.this.setBackground(Color.decode("#DDFFDD")); //$NON-NLS-1$
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
        } else {
            JFileDropTextField.this.setBackground(Color.decode("#FFDDDD")); //$NON-NLS-1$
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
        JFileDropTextField.this.setBackground(color);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Restores the background color, sets the text field to the first dropped file's absolute path,
     * and invokes the callback.
     * </p>
     *
     * @param dtde the {@link DropTargetDropEvent}
     */
    @Override
    public void drop(final DropTargetDropEvent dtde) {
        JFileDropTextField.this.setBackground(color);
        drop(dtde, files -> {
            JFileDropTextField.this.setText(files.get(0).getAbsolutePath());
            callback.call(JFileDropTextField.this.getText());
        });
    }

    /**
     * {@inheritDoc}
     * <p>
     * Validates that exactly one file was dropped.
     * </p>
     *
     * @param files the list of {@link File} objects to validate
     * @return {@code true} if exactly one file was dropped, {@code false} otherwise
     */
    @Override
    public boolean checkValid(final List<File> files) {
        return files.size() == 1;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Checks that the component is enabled and the transferable supports the Java file list data flavor.
     * </p>
     *
     * @param transferable the {@link Transferable} to check
     * @return {@code true} if the data flavor is supported and the component is enabled
     */
    @Override
    public boolean isFlavorSupported(final Transferable transferable) {
        return JFileDropTextField.this.isEnabled() && transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
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
