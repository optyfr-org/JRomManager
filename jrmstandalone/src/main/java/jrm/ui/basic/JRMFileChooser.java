/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.ui.basic;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileSystemView;

import jrm.locale.Messages;
import jrm.misc.Log;

/**
 * Enhanced file chooser dialog with callback-based result handling and configurable filters.
 * <p>
 * This component extends {@link JFileChooser} to provide a fluent API for configuring file selection
 * dialogs with custom filters, titles, and selection modes. Supports callback-based result processing
 * and includes a custom file system view that restricts navigation to a single root directory.
 * </p>
 *
 * @param <V> the type of value returned by the callback
 * @see JFileChooser
 */
public class JRMFileChooser<V> extends JFileChooser {

    /**
     * A custom file system view that restricts navigation to a single root directory.
     * <p>
     * This view limits the user to browsing within a specified root directory and its subdirectories,
     * preventing navigation to parent directories or other file system roots.
     * </p>
     */
    public static class OneRootFileSystemView extends FileSystemView {

        /** The root directory for this file system view. */
        File root;

        /** The array containing the single root directory. */
        File[] roots = new File[1];

        /**
         * Constructs a new one-root file system view.
         *
         * @param root the root directory to restrict navigation to
         */
        public OneRootFileSystemView(final File root) {
            try {
                this.root = root.getCanonicalFile();
                roots[0] = this.root;
            } catch (final IOException e1) {
                JOptionPane.showMessageDialog(null, e1, "Exception", JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
                Log.err(e1.getMessage(), e1);
            }
        }

        /**
         * {@inheritDoc}
         * <p>
         * Creates a new folder with a localized name in the specified directory.
         * </p>
         *
         * @param containingDir the directory in which to create the new folder
         * @return the newly created folder
         * @throws IOException if an I/O error occurs
         */
        @Override
        public File createNewFolder(final File containingDir) throws IOException {
            final File folder = new File(containingDir, Messages.getString("JRMFileChooser.NewFolder")); //$NON-NLS-1$
            folder.mkdir();
            return folder;
        }

        /**
         * {@inheritDoc}
         *
         * @return the root directory
         */
        @Override
        public File getDefaultDirectory() {
            return root;
        }

        /**
         * {@inheritDoc}
         *
         * @return the root directory
         */
        @Override
        public File getHomeDirectory() {
            return root;
        }

        /**
         * {@inheritDoc}
         *
         * @return an array containing the single root directory
         */
        @Override
        public File[] getRoots() {
            return roots;
        }
    }

    /**
     * Callback interface for processing the result of a file chooser dialog.
     *
     * @param <V> the type of value returned by the callback
     */
    public interface CallBack<V> {

        /**
         * Called when the user approves the file selection.
         *
         * @param chooser the {@link JRMFileChooser} containing the selected file(s)
         * @return the processed result
         */
        public V call(JRMFileChooser<V> chooser);
    }

    /**
     * Constructs a new file chooser with default settings.
     */
    public JRMFileChooser() {
        this(null, null, null, null, null, null, false);
    }

    /**
     * Constructs a new file chooser with the specified dialog type and file selection mode.
     *
     * @param type the dialog type (e.g., {@link JFileChooser#OPEN_DIALOG})
     * @param mode the file selection mode (e.g., {@link JFileChooser#FILES_ONLY})
     */
    public JRMFileChooser(final int type, final int mode) {
        this(type, mode, null, null, null, null, false);
    }

    /**
     * Constructs a new file chooser with the specified dialog type, mode, and current directory.
     *
     * @param type the dialog type (e.g., {@link JFileChooser#OPEN_DIALOG})
     * @param mode the file selection mode (e.g., {@link JFileChooser#FILES_ONLY})
     * @param currdir the initial directory to display, or {@code null} for default
     */
    public JRMFileChooser(final int type, final int mode, final File currdir) {
        this(type, mode, currdir, null, null, null, false);
    }

    /**
     * Constructs a new file chooser with full configuration.
     *
     * @param type the dialog type, or {@code null} for default
     * @param mode the file selection mode, or {@code null} for default
     * @param currdir the initial directory, or {@code null} for default
     * @param selected the initially selected file, or {@code null} for none
     * @param filters the list of file filters to add, or {@code null} for none
     * @param title the dialog title, or {@code null} for default
     * @param multi {@code true} to enable multiple file selection
     */
    public JRMFileChooser(final Integer type, final Integer mode, final File currdir, final File selected, final List<FileFilter> filters, final String title,
            final boolean multi) {
        super();
        setup(type, mode, currdir, selected, filters, title, multi);
    }

    /**
     * Configures the file chooser with the specified settings.
     * <p>
     * Sets the dialog type, file selection mode, current directory, selected file, filters, title,
     * and multi-selection mode. If the current directory is a file, it is set as the selected file instead.
     * </p>
     *
     * @param type the dialog type, or {@code null} to leave unchanged
     * @param mode the file selection mode, or {@code null} to leave unchanged
     * @param currdir the initial directory, or {@code null} to leave unchanged
     * @param selected the initially selected file, or {@code null} for none
     * @param filters the list of file filters to add, or {@code null} for none
     * @param title the dialog title, or {@code null} to leave unchanged
     * @param multi {@code true} to enable multiple file selection
     * @return this file chooser instance for method chaining
     */
    public JRMFileChooser<V> setup(final Integer type, final Integer mode, final File currdir, final File selected, final List<FileFilter> filters, final String title,
            final boolean multi) {
        Optional.ofNullable(type).ifPresent(this::setDialogType);
        Optional.ofNullable(mode).ifPresent(this::setFileSelectionMode);
        Optional.ofNullable(selected).ifPresent(this::setSelectedFile);
        if (currdir != null && currdir.exists()) {
            if (currdir.isFile())
                setSelectedFile(currdir);
            else
                setCurrentDirectory(currdir);
        }
        if (filters != null) {
            if (filters.size() == 1) {
                setFileFilter(filters.get(0));
                setAcceptAllFileFilterUsed(false);
            } else
                for (final FileFilter filter : filters) {
                    addChoosableFileFilter(filter);
                    setAcceptAllFileFilterUsed(false);
                }
        }
        Optional.ofNullable(title).ifPresent(this::setDialogTitle);
        if (multi)
            setMultiSelectionEnabled(multi);
        return this;
    }

    /**
     * Constructs a new file chooser with the specified file system view.
     *
     * @param fsv the {@link FileSystemView} to use
     */
    public JRMFileChooser(final FileSystemView fsv) {
        super(fsv);
    }

    /**
     * Displays the file chooser dialog and invokes the callback if the user approves the selection.
     *
     * @param parent the parent component for the dialog
     * @param callback the {@link CallBack} to invoke with the result
     * @return the result from the callback, or {@code null} if the user cancelled
     */
    public V show(final Component parent, final CallBack<V> callback) {
        if (showOpenDialog(parent) == JFileChooser.APPROVE_OPTION)
            return callback.call(this);
        return null;
    }

    /**
     * Displays an open file dialog and invokes the callback if the user approves the selection.
     *
     * @param parent the parent component for the dialog
     * @param callback the {@link CallBack} to invoke with the result
     * @return the result from the callback, or {@code null} if the user cancelled
     */
    public V showOpen(final Component parent, final CallBack<V> callback) {
        setDialogType(JFileChooser.OPEN_DIALOG);
        return show(parent, callback);
    }

    /**
     * Displays a save file dialog and invokes the callback if the user approves the selection.
     *
     * @param parent the parent component for the dialog
     * @param callback the {@link CallBack} to invoke with the result
     * @return the result from the callback, or {@code null} if the user cancelled
     */
    public V showSave(final Component parent, final CallBack<V> callback) {
        setDialogType(JFileChooser.SAVE_DIALOG);
        return show(parent, callback);
    }
}
