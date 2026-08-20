/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.fix.actions;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;

import jrm.aui.progress.ProgressHandler;
import jrm.misc.Log;
import jrm.misc.ProfileSettingsEnum;
import jrm.profile.data.Container;
import jrm.profile.data.Entry;
import jrm.profile.data.Entry.Type;
import jrm.profile.scan.options.FormatOptions;
import jrm.security.PathAbstractor;
import jrm.security.Session;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;

/**
 * Special container action aimed at backing up some or all file entries from a target container.
 * <p>
 * This class coordinates the creation and caching of backup archive targets, ensuring that original content is preserved before any
 * modifications or deletes are applied.
 * </p>
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public class BackupContainer extends ContainerAction {
    /**
     * Constructs a new {@code BackupContainer} for the specified container.
     * 
     * @param container the container to backup
     */
    public BackupContainer(Container container) {
        super(container, FormatOptions.ZIP);
    }

    /**
     * Factory method to obtain or initialize a {@code BackupContainer} instance.
     * 
     * @param action the existing backup container action reference (can be null)
     * @param container the target container to backup
     * 
     * @return the initialized backup container action
     */
    public static BackupContainer getInstance(BackupContainer action, final Container container) {
        if (action == null)
            action = new BackupContainer(container);
        return action;
    }

    /**
     * Factory method to obtain or initialize a {@code BackupContainer} instance stored within an {@link AtomicReference}.
     * 
     * @param action the atomic reference enclosing the backup container action
     * @param container the target container to backup
     * 
     * @return the initialized backup container action
     */
    public static BackupContainer getInstance(AtomicReference<BackupContainer> action, final Container container) {
        if (action.get() == null)
            action.set(new BackupContainer(container));
        return action.get();
    }

    /**
     * A thread-safe registry of currently opened backup Zip file instances.
     */
    private static final Map<String, ZipFile> zipfiles = new HashMap<>();

    /**
     * Retrieves or creates a Zip backup file destination corresponding to the entry actions.
     * 
     * @param session the current active session
     * @param container the originating entry's container
     * @param action the entry action being backed up
     * 
     * @return the cached or newly instantiated {@link ZipFile} target
     */
    public static synchronized ZipFile getZipFile(final Session session, Container container, EntryAction action) {
        Log.info(action.entry.getFile());
        final var crc2 = action.entry.getCrc().substring(0, 2);
        if (!zipfiles.containsKey(crc2)) {
            final String workdir;
            if (Boolean.TRUE.equals(session.getCurrProfile().getSettings().getProperty(ProfileSettingsEnum.backup_dest_dir_enabled, Boolean.class)))
                workdir = session.getCurrProfile().getSettings().getProperty(ProfileSettingsEnum.backup_dest_dir);
            else if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(ProfileSettingsEnum.backup_dest_dir_enabled, Boolean.class)))
                workdir = session.getUser().getSettings().getProperty(ProfileSettingsEnum.backup_dest_dir);
            else
                workdir = "%work/backup"; //$NON-NLS-1$
            final var backupdir = PathAbstractor.getAbsolutePath(session, workdir).toFile();
            final var crc = new CRC32();
            crc.update(container.getFile().getAbsoluteFile().getParent().getBytes());
            final var backupfile = new File(new File(backupdir, String.format("%08x", crc.getValue())), crc2 + ".zip"); //$NON-NLS-1$ //$NON-NLS-2$
            backupfile.getParentFile().mkdirs();
            zipfiles.put(crc2, new ZipFile(backupfile)); // $NON-NLS-1$
        }
        return zipfiles.get(crc2);
    }

    /**
     * Closes all opened backup zip archive {@link FileSystem}s when all backup tasks are completed.
     */
    public static synchronized void closeAllFS() {
        for (final var zipfile : zipfiles.values()) {
            try {
                synchronized (zipfile) {
                    zipfile.close();
                }
            } catch (IOException e) {
                Log.err(e.getMessage(), e);
            }
        }
        zipfiles.clear();
    }

    /**
     * Executes the backup action on the registered entries.
     * 
     * @param session the active session
     * @param handler the visual progress handler
     * 
     * @return {@code true} if all entries were successfully backed up, otherwise {@code false}
     */
    @Override
    public boolean doAction(final Session session, ProgressHandler handler) {
        try {
            var i = 0;
            if (entryActions.isEmpty())
                for (Entry entry : container.getEntries())
                    if (entry.getType() != Type.CHD)
                        addAction(new BackupEntry(entry));
            for (final EntryAction action : entryActions) {
                i++;
                final var zipf = getZipFile(session, container, action);
                synchronized (zipf) {
                    final var zipp = new ZipParameters();
                    zipp.setCompressionLevel(CompressionLevel.FASTEST);
                    if (!action.doAction(session, zipf, zipp, handler, i, entryActions.size())) {
                        Log.err("action to " + container.getFile().getName() + "@" + action.entry.getRelFile() + " failed"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        return false;
                    }
                }
            }
            return true;
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
        }
        return false;
    }

}
