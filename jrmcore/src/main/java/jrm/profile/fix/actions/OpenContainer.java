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
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import jrm.aui.progress.ProgressHandler;
import jrm.compressors.SevenZipArchive;
import jrm.compressors.ZipArchive;
import jrm.compressors.ZipLevel;
import jrm.compressors.ZipTempThreshold;
import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.profile.data.Container;
import jrm.profile.scan.options.FormatOptions;
import jrm.security.Session;
import net.lingala.zip4j.ZipFile;

/**
 * specialized class when an already existing container have to be opened before doing actions on entries (which should be only
 * {@link AddEntry})
 * 
 * @author optyfr
 */
public class OpenContainer extends ContainerAction {
    /**
     * the uncompressed datasize of all entries to add (for temp file threshold purpose)
     */
    private final long dataSize;

    /**
     * constructor
     * 
     * @param container the container to open
     * @param format the desired format
     * @param dataSize the uncompressed data size supposed to be added
     */
    public OpenContainer(final Container container, final FormatOptions format, final long dataSize) {
        super(container, format);
        this.dataSize = dataSize;
    }

    /**
     * shortcut static method to get an instance of {@link OpenContainer}
     * 
     * @param action the potentially already existing {@link OpenContainer}
     * @param container the container to open
     * @param format the desired format
     * @param dataSize the uncompressed data size supposed to be added
     * 
     * @return a {@link OpenContainer}
     */
    public static OpenContainer getInstance(OpenContainer action, final Container container, final FormatOptions format, final long dataSize) {
        if (action == null)
            action = new OpenContainer(container, format, dataSize);
        return action;
    }

    /**
     * shortcut static method to get an instance of {@link OpenContainer}
     * 
     * @param action the potentially {@link AtomicReference} to already existing {@link OpenContainer}
     * @param container the container to open
     * @param format the desired format
     * @param dataSize the uncompressed data size supposed to be added
     * 
     * @return a {@link OpenContainer}
     */
    public static OpenContainer getInstance(final AtomicReference<OpenContainer> action, final Container container, final FormatOptions format, final long dataSize) {
        if (action.get() == null)
            action.set(new OpenContainer(container, format, dataSize));
        return action.get();
    }

    @Override
    public boolean doAction(final Session session, final ProgressHandler handler) {
        handler.setProgress(toDocument(toNoBR(String.format(escape(session.getMsgs().getString("OpenContainer.Fixing")), //$NON-NLS-1$
                toBlue(container.getRelAW().getFullName(container.getFile().getName())), toPurple(container.getRelAW().getDescription())))));
        if (container.getType() == Container.Type.ZIP) {
            if (format == FormatOptions.ZIP || format == FormatOptions.TZIP) {
                return doActionZip(session, handler);
            } else if (format == FormatOptions.ZIPE) {
                return doActionZipE(session, handler);
            }
        } else if (container.getType() == Container.Type.SEVENZIP) {
            return doAction7z(session, handler);
        } else if (container.getType() == Container.Type.DIR || container.getType() == Container.Type.FAKE) {
            return doActionDir(session, handler);
        }
        return false;
    }

    /**
     * @param session
     * @param handler
     * 
     * @return
     */
    private boolean doActionDir(final Session session, final ProgressHandler handler) {
        final Path target = container.getType() == Container.Type.DIR ? container.getFile().toPath() : container.getFile().getParentFile().toPath();
        if (!pathAction(session, handler, target))
            return false;
        if (container.getType() == Container.Type.DIR)
            deleteEmptyFolders(container.getFile());
        return true;
    }

    /**
     * @param session
     * @param handler
     * 
     * @return
     */
    private boolean doAction7z(final Session session, final ProgressHandler handler) {
        try {
            return archiveAction(session, handler, new SevenZipArchive(session, container.getFile()));
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
        }
        return false;
    }

    /**
     * @param session
     * @param handler
     * 
     * @return
     */
    private boolean doActionZipE(final Session session, final ProgressHandler handler) {
        try {
            return archiveAction(session, handler, new ZipArchive(session, container.getFile()));
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
        }
        return false;
    }

    /**
     * @param session
     * @param handler
     * 
     * @return
     */
    private boolean doActionZip(final Session session, final ProgressHandler handler) {
        if (entryActions.isEmpty())
            return true;
        if (entryActions.get(0) instanceof RenameEntry) {
            final Map<String, Object> env = new HashMap<>();
            env.put("useTempFile", dataSize > ZipTempThreshold.valueOf(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.zip_temp_threshold)).getThreshold()); //$NON-NLS-1$ //$NON-NLS-2$
            env.put("compressionLevel", //$NON-NLS-1$
                    format == FormatOptions.TZIP ? 1 : ZipLevel.valueOf(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.zip_compression_level)).getLevel()); // $NON-NLS-2$
            try (final var fs = FileSystems.newFileSystem(URI.create("jar:" + container.getFile().toURI()), env);) //$NON-NLS-1$
            {
                if (!fsAction(session, handler, fs))
                    return false;
                deleteEmptyFolders(fs.getPath("/")); //$NON-NLS-1$
                return true;
            } catch (final Exception e) {
                Log.err(e.getMessage(), e);
            }

        } else
            try (final var zif = new ZipFile(container.getFile())) {
                return zosAction(session, handler, zif);
            } catch (final Exception e) {
                Log.err(e.getMessage(), e);
            }
        return false;
    }

    /**
     * Maximum directory nesting inspected from the starting folder. Deeper folders are left in place.
     */
    static final int MAX_FOLDER_DEPTH = 100;

    /**
     * Delete empty folders under {@code baseFolder}.
     * Walks iteratively with a depth cap and canonical-path cycle detection.
     *
     * @param baseFolder the base folder as a {@link File} (may also be deleted if nothing left)
     *
     * @return the number of bytes left in folders, 0 mean all folders were deleted
     */
    public long deleteEmptyFolders(final File baseFolder) {
        return baseFolder == null ? 0L : deleteEmptyFolders(baseFolder.toPath());
    }

    /**
     * Delete empty folders under {@code baseFolder}.
     * Walks iteratively with a depth cap and canonical-path cycle detection.
     *
     * @param baseFolder the base folder as a {@link Path} (may also be deleted if nothing left)
     *
     * @return the number of bytes left in folders, 0 mean all folders were deleted
     */
    public long deleteEmptyFolders(final Path baseFolder) {
        if (baseFolder == null)
            return 0L;
        final var stack = new ArrayDeque<FolderFrame>();
        final var visited = new HashSet<String>();
        stack.push(new FolderFrame(baseFolder, 0));
        long leftover = 0L;
        while (!stack.isEmpty()) {
            final var frame = stack.peek();
            if (!frame.entered) {
                enterFolder(frame, stack, visited);
                continue;
            }
            stack.pop();
            if (frame.size == 0L) {
                try {
                    Files.deleteIfExists(frame.dir);
                } catch (final Exception e) {
                    Log.err(e.getMessage(), e);
                }
            }
            if (stack.isEmpty())
                leftover = frame.size;
            else
                stack.peek().size += frame.size;
        }
        return leftover;
    }

    private static void enterFolder(final FolderFrame frame, final ArrayDeque<FolderFrame> stack, final Set<String> visited) {
        frame.entered = true;
        try {
            if (!visited.add(folderKey(frame.dir))) {
                frame.size = 1L;
                return;
            }
            try (final var stream = Files.list(frame.dir)) {
                for (final Path child : stream.toList()) {
                    if (Files.isDirectory(child)) {
                        if (frame.depth + 1 >= MAX_FOLDER_DEPTH)
                            frame.size += 1L;
                        else
                            stack.push(new FolderFrame(child, frame.depth + 1));
                    } else
                        frame.size += Files.size(child);
                }
            }
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
            if (frame.size == 0L)
                frame.size = 1L;
        }
    }

    private static String folderKey(final Path dir) {
        try {
            return dir.toRealPath().toString();
        } catch (final IOException _) {
            return dir.toAbsolutePath().normalize().toString();
        }
    }

    private static final class FolderFrame {
        private final Path dir;
        private final int depth;
        private long size;
        private boolean entered;

        private FolderFrame(final Path dir, final int depth) {
            this.dir = dir;
            this.depth = depth;
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder(Messages.getString("OpenContainer.Open")).append(container); //$NON-NLS-1$
        for (final EntryAction action : entryActions)
            str.append("\n\t").append(action); //$NON-NLS-1$
        return str.toString();
    }

    @Override
    public long estimatedSize() {
        long size = 0;
        for (final EntryAction action : entryActions)
            size += action.estimatedSize();
        return size;
    }

    @Override
    public int count() {
        return entryActions.size();
    }
}
