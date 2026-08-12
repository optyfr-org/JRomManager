/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.fix.actions;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import jrm.aui.progress.ProgressHandler;
import jrm.compressors.Archive;
import jrm.compressors.ZipTools;
import jrm.locale.Messages;
import jrm.misc.IOUtils;
import jrm.misc.Log;
import jrm.profile.data.Entry;
import jrm.security.Session;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;

/**
 * Duplicate an entry inside the *same* container
 * 
 * @author optyfr
 */
public class DuplicateEntry extends EntryAction {
    private static final String DUPLICATE_S_AT_S_TO_S_AT_S_FAILED = "duplicate %s@%s to %s@%s failed";
    private static final String DUPLICATE_ENTRY_DUPLICATING = "DuplicateEntry.Duplicating";
    /**
     * the new name of the entry
     */
    final String newname;

    /**
     * the constructor
     * 
     * @param newname the new name io the entry
     * @param entry the {@link Entry} to duplicated
     */
    public DuplicateEntry(final String newname, final Entry entry) {
        super(entry);
        this.newname = newname;
    }

    @Override
    public boolean doAction(final Session session, final FileSystem fs, final ProgressHandler handler, int i, int max) {
        final var dstpath = fs.getPath(newname);
        try {
            handler.setProgress(null, null, null, progress(i, max, String.format(session.getMsgs().getString(DUPLICATE_ENTRY_DUPLICATING), entry.getRelFile(), newname))); // $NON-NLS-1$
            final var srcpath = fs.getPath(entry.getFile());
            final var parent2 = dstpath.getParent();
            if (parent2 != null)
                Files.createDirectories(parent2);
            Files.copy(srcpath, dstpath, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
            Log.err(String.format(DUPLICATE_S_AT_S_TO_S_AT_S_FAILED, parent.container.getFile().getName(), entry.getRelFile(), parent.container.getFile().getName(), newname));
        }
        return false;
    }

    @Override
    public boolean doAction(Session session, ZipFile zipf, ZipParameters zipp, ProgressHandler handler, int i, int max) {
        Path tmpdir = null;
        try {
            tmpdir = IOUtils.createTempFile("JRM", null);
            handler.setProgress(null, null, null, progress(i, max, String.format(session.getMsgs().getString(DUPLICATE_ENTRY_DUPLICATING), entry.getRelFile(), newname))); // $NON-NLS-1$
            zipf.extractFile(ZipTools.toZipEntry(entry.getFile()), tmpdir.toString(), "file");
            zipp.setFileNameInZip(newname);
            zipf.addFile(tmpdir.resolve("file").toFile(), zipp);
            return true;
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
            Log.err(String.format(DUPLICATE_S_AT_S_TO_S_AT_S_FAILED, parent.container.getFile().getName(), entry.getRelFile(), parent.container.getFile().getName(), newname));
        } finally {
            try {
                if (tmpdir != null) {
                    Files.deleteIfExists(tmpdir.resolve("file"));
                    Files.delete(tmpdir);
                }
            } catch (IOException e) {
                Log.err(e.getMessage(), e);
            }
        }
        return false;
    }

    @Override
    public boolean doAction(final Session session, final Path target, final ProgressHandler handler, int i, int max) {
        Path dstpath = null;
        try {
            dstpath = IOUtils.resolveContainedPath(target, newname);
            handler.setProgress(null, null, null, progress(i, max, String.format(session.getMsgs().getString(DUPLICATE_ENTRY_DUPLICATING), entry.getRelFile(), newname))); // $NON-NLS-1$
            final var srcpath = IOUtils.resolveContainedPath(target, entry.getFile());
            final var parent2 = dstpath.getParent();
            if (parent2 != null)
                Files.createDirectories(parent2);
            Files.copy(srcpath, dstpath);
            return true;
        } catch (final Exception _) {
            Log.err(String.format(DUPLICATE_S_AT_S_TO_S_AT_S_FAILED, parent.container.getFile().getName(), entry.getRelFile(), parent.container.getFile().getName(), newname));
        }
        return false;
    }

    @Override
    public boolean doAction(final Session session, final Archive archive, final ProgressHandler handler, int i, int max) {
        try {
            handler.setProgress(null, null, null, progress(i, max, String.format(session.getMsgs().getString(DUPLICATE_ENTRY_DUPLICATING), entry.getRelFile(), newname))); // $NON-NLS-1$
            return archive.duplicate(entry.getFile(), newname) == 0;
        } catch (final Exception _) {
            Log.err(String.format(DUPLICATE_S_AT_S_TO_S_AT_S_FAILED, parent.container.getFile().getName(), entry.getRelFile(), parent.container.getFile().getName(), newname));
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format(Messages.getString("DuplicateEntry.Duplicate"), entry, newname); //$NON-NLS-1$
    }
}
