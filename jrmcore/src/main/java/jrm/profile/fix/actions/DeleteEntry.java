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
import java.util.stream.Collectors;

import jrm.aui.progress.ProgressHandler;
import jrm.compressors.Archive;
import jrm.compressors.ZipTools;
import jrm.locale.Messages;
import jrm.misc.IOUtils;
import jrm.misc.Log;
import jrm.profile.data.Entry;
import jrm.security.Session;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;

/**
 * Delete an entry from its container
 * 
 * @author optyfr
 */
public class DeleteEntry extends EntryAction {
    private static final String DELETE_S_AT_S_FAILED = "delete %s@%s failed";
    private static final String DELETE_ENTRY_DELETING = "DeleteEntry.Deleting";

    /**
     * constructor
     * 
     * @param entry the entry to delete
     */
    public DeleteEntry(final Entry entry) {
        super(entry);
    }

    @Override
    public boolean doAction(final Session session, final FileSystem dstfs, final ProgressHandler handler, int i, int max) {
        Path path = null;
        try {
            handler.setProgress(null, null, null, progress(i, max, String.format(session.getMsgs().getString(DELETE_ENTRY_DELETING), entry.getRelFile()))); // $NON-NLS-1$
            path = dstfs.getPath(entry.getFile());
            Files.deleteIfExists(path);
            return true;
        } catch (final Exception _) {
            Log.err(String.format(DELETE_S_AT_S_FAILED, parent.container.getFile().getName(), path)); // $NON-NLS-1$ //$NON-NLS-2$
                                                                                                      // //$NON-NLS-3$
        }
        return false;
    }

    @Override
    public boolean doAction(Session session, ZipFile zipf, ZipParameters zipp, ProgressHandler handler, int i, int max) {
        Path path = null;
        try {
            path = Path.of(entry.getFile());
            handler.setProgress(null, null, null, progress(i, max, String.format(session.getMsgs().getString(DELETE_ENTRY_DELETING), entry.getRelFile()))); // $NON-NLS-1$
            Log.info(() -> "remove " + entry.getFile() + " from " + zipf.getFile());
            final var ziphdr = zipf.getFileHeader(ZipTools.toZipEntry(entry.getFile()));
            if (ziphdr != null)
                zipf.removeFile(ziphdr);
            else {
                Log.info(() -> "ziphdr not found");
                Log.info(zipf.getFileHeaders().stream().map(FileHeader::getFileName).collect(Collectors.joining(", ")));
            }
            return true;
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
            Log.err(String.format(DELETE_S_AT_S_FAILED, parent.container.getFile().getName(), path)); // $NON-NLS-1$ //$NON-NLS-2$
                                                                                                      // //$NON-NLS-3$
        }
        return false;
    }

    @Override
    public boolean doAction(final Session session, final Path target, final ProgressHandler handler, int i, int max) {
        Path path = null;
        try {
            handler.setProgress(null, null, null, progress(i, max, String.format(session.getMsgs().getString(DELETE_ENTRY_DELETING), entry.getRelFile()))); // $NON-NLS-1$
            path = IOUtils.resolveContainedPath(target, entry.getFile());
            Files.deleteIfExists(path);
            return true;
        } catch (final Exception _) {
            Log.err(String.format(DELETE_S_AT_S_FAILED, parent.container.getFile().getName(), path)); // $NON-NLS-1$ //$NON-NLS-2$
                                                                                                      // //$NON-NLS-3$
        }
        return false;
    }

    @Override
    public boolean doAction(final Session session, final Archive archive, final ProgressHandler handler, int i, int max) {
        try {
            handler.setProgress(null, null, null, progress(i, max, String.format(session.getMsgs().getString(DELETE_ENTRY_DELETING), entry.getRelFile()))); // $NON-NLS-1$
            return archive.delete(entry.getFile()) == 0;
        } catch (final Exception _) {
            Log.err(String.format(DELETE_S_AT_S_FAILED, parent.container.getFile().getName(), entry.getRelFile())); // $NON-NLS-1$
                                                                                                                    // //$NON-NLS-2$
                                                                                                                    // //$NON-NLS-3$
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format(Messages.getString("DeleteEntry.Delete"), entry); //$NON-NLS-1$
    }

}
