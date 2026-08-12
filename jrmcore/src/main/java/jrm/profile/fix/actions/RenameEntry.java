/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.fix.actions;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

import jrm.aui.progress.ProgressHandler;
import jrm.compressors.Archive;
import jrm.compressors.ZipTools;
import jrm.locale.Messages;
import jrm.misc.IOUtils;
import jrm.misc.Log;
import jrm.profile.data.Entry;
import jrm.security.PathAbstractor;
import jrm.security.Session;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;

/**
 * Rename an entry inside its container
 * 
 * @author optyfr
 */
public class RenameEntry extends EntryAction {
    private static final String RENAME_S_AT_S_TO_S_AT_S = "rename %s@%s to %s@%s";
    private static final String RENAME_ENTRY_RENAMING = "RenameEntry.Renaming";
    /**
     * the desired new name of the entry to rename
     */
    final String newname;

    /**
     * constructor that will rename to a temporary name
     * 
     * @param entry the {@link Entry} to rename
     */
    public RenameEntry(final Entry entry) {
        super(entry);
        newname = UUID.randomUUID() + "_" + entry.getSize() + ".tmp"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * constructor
     * 
     * @param newname the desired new name for the entry
     * @param entry the {@link Entry} to rename
     */
    public RenameEntry(final String newname, final Entry entry) {
        super(entry);
        this.newname = newname;
    }

    @Override
    public boolean doAction(Session session, ZipFile zipf, ZipParameters zipp, ProgressHandler handler, int i, int max) {
        Path dstpath = null;
        try {
            handler.setProgress(null, null, null, progress(i, max, String.format(session.getMsgs().getString(RENAME_ENTRY_RENAMING), entry.getRelFile(), newname))); // $NON-NLS-1$

            final var fh = Optional
                    .ofNullable(zipf.getFileHeader(ZipTools.toZipEntry(entry.getFile())))
                    .orElse(zipf.getFileHeader(entry.getFile()));
            if (fh != null) {
                if (!fh.getFileName().equals(ZipTools.toZipEntry(entry.getFile())))
                    Log.err(() -> "%s : selected the wrong FileHeader, should be %s, got %s".formatted(parent.container.getFile().getName(),
                            ZipTools.toZipEntry(entry.getFile()), fh.getFileName()));
                if (zipf.getFileHeader(newname) != null)
                    Log.err(() -> "%s : can't rename %s to %s because destination already exists".formatted(parent.container.getFile().getName(), entry.getFile(), newname));
                zipf.renameFile(fh, newname);
                if (!fh.getFileName().equals(newname))
                    Log.err(() -> "%s : %s has been renamed to %s instead of %s".formatted(parent.container.getFile().getName(), entry.getFile(), fh.getFileName(), newname));
                dstpath = Path.of(newname);
                final var srcpath = entry.getFile();
                entry.rename(newname, PathAbstractor.getRelativePath(session, dstpath).toString());
                Log.debug(String.format(RENAME_S_AT_S_TO_S_AT_S, parent.container.getFile().getName(), srcpath, parent.container.getFile().getName(), dstpath));
                return true;
            } else {
                Log.err(String.format(RENAME_S_AT_S_TO_S_AT_S, parent.container.getFile().getName(), entry.getRelFile(), parent.container.getFile().getName(), newname));
            }
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
            Log.err(String.format(RENAME_S_AT_S_TO_S_AT_S, parent.container.getFile().getName(), entry.getRelFile(), parent.container.getFile().getName(), newname));
        }
        return false;
    }

    @Override
    public boolean doAction(final Session session, final Path target, final ProgressHandler handler, int i, int max) {
        Path dstpath = null;
        try {
            dstpath = IOUtils.resolveContainedPath(target, newname);
            handler.setProgress(null, null, null, progress(i, max, String.format(session.getMsgs().getString(RENAME_ENTRY_RENAMING), entry.getRelFile(), newname))); // $NON-NLS-1$
            final var srcpath = IOUtils.resolveContainedPath(target, entry.getFile());
            final var parent = dstpath.getParent();
            if (parent != null)
                Files.createDirectories(parent);
            Files.move(srcpath, dstpath, StandardCopyOption.REPLACE_EXISTING);
            entry.rename(dstpath.toString(), PathAbstractor.getRelativePath(session, dstpath).toString());
            Log.debug(String.format(RENAME_S_AT_S_TO_S_AT_S, this.parent.container.getFile().getName(), srcpath, this.parent.container.getFile().getName(), dstpath));
            return true;
        } catch (final Exception _) {
            Log.err(String.format(RENAME_S_AT_S_TO_S_AT_S, parent.container.getFile().getName(), entry.getRelFile(), parent.container.getFile().getName(), newname));
        }
        return false;
    }

    @Override
    public boolean doAction(final Session session, final Archive archive, final ProgressHandler handler, int i, int max) {
        try {
            handler.setProgress(null, null, null, progress(i, max, String.format(session.getMsgs().getString(RENAME_ENTRY_RENAMING), entry.getRelFile(), newname))); // $NON-NLS-1$
            if (archive.rename(entry.getFile(), newname) == 0) {
                entry.rename(newname, null);
                return true;
            }
        } catch (final Exception _) {
            Log.err("rename " + parent.container.getFile().getName() + "@" + entry.getRelFile() + " to " + parent.container.getFile().getName() + "@" + newname + " failed"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        }
        return false;
    }

    @Override
    public boolean doAction(final Session session, final FileSystem fs, final ProgressHandler handler, int i, int max) {
        Path dstpath = null;
        try {
            handler.setProgress(null, null, null, progress(i, max, String.format(session.getMsgs().getString(RENAME_ENTRY_RENAMING), entry.getRelFile(), newname))); // $NON-NLS-1$
            final var srcpath = fs.getPath(entry.getFile());
            dstpath = fs.getPath(newname);
            if (dstpath != null) {
                final var parent = dstpath.getParent();
                if (parent != null)
                    Files.createDirectories(parent);
                Files.move(srcpath, dstpath, StandardCopyOption.REPLACE_EXISTING);
                entry.rename(dstpath.toString(), PathAbstractor.getRelativePath(session, dstpath).toString());
            }
            Log.debug(String.format(RENAME_S_AT_S_TO_S_AT_S, parent.container.getFile().getName(), srcpath, parent.container.getFile().getName(), dstpath));
            return true;
        } catch (final Exception _) {
            Log.err(String.format(RENAME_S_AT_S_TO_S_AT_S, parent.container.getFile().getName(), entry.getRelFile(), parent.container.getFile().getName(), newname));
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format(Messages.getString("RenameEntry.Rename"), entry, newname); //$NON-NLS-1$
    }
}
