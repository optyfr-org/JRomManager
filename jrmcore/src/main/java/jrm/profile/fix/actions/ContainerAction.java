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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jrm.aui.progress.ProgressHandler;
import jrm.aui.status.StatusRendererFactory;
import jrm.compressors.Archive;
import jrm.compressors.ZipLevel;
import jrm.misc.Log;
import jrm.profile.data.Container;
import jrm.profile.scan.options.FormatOptions;
import jrm.security.Session;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

/**
 * Base abstract class representing a corrective action applied to a game/ROM container (e.g., creating, opening, deleting, or
 * torrent-zipping a ZIP/7Z archive or a folder).
 * <p>
 * A {@code ContainerAction} maintains a list of individual {@link EntryAction} operations that are executed on files nested inside
 * the targeted container.
 * </p>
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public abstract class ContainerAction implements StatusRendererFactory {
    /**
     * Error log message template for entry action failures.
     */
    private static final String ACTION_TO_S_AT_S_FAILED = "action to %s@%s failed";

    /**
     * Default fallback action count.
     */
    private static final int COUNT = 0;

    /**
     * Default fallback estimated action size in bytes.
     */
    private static final long ESTIMATED_SIZE = 0L;

    /**
     * The target container on which corrective actions are to be performed.
     */
    public final Container container;

    /**
     * The desired output format profile configuration for this container.
     */
    public final FormatOptions format;

    /**
     * The collection of nested structural entry actions to run inside this container.
     */
    public final List<EntryAction> entryActions = new ArrayList<>();

    /**
     * Constructs a new {@code ContainerAction} for the specified container and target format.
     * 
     * @param container the target game/ROM container
     * @param format the target format options
     */
    protected ContainerAction(final Container container, final FormatOptions format) {
        this.container = container;
        this.format = format;
    }

    /**
     * Associates a child {@link EntryAction} with this container action.
     * 
     * @param entryAction the entry action to add
     */
    public void addAction(final EntryAction entryAction) {
        entryActions.add(entryAction);
        entryAction.parent = this;
    }

    /**
     * Helper method to append a valid, non-empty container action to a list.
     * 
     * @param list the list of container actions
     * @param action the container action to potentially append
     */
    public static void addToList(final List<ContainerAction> list, final ContainerAction action) {
        if (action != null && !action.entryActions.isEmpty())
            list.add(action);
    }

    /**
     * Performs the specific structural container action, using the given execution session context and progress indicators.
     * 
     * @param session the current active user session
     * @param handler the UI progress status indicator
     * 
     * @return {@code true} if the action succeeded, otherwise {@code false}
     */
    public abstract boolean doAction(final Session session, ProgressHandler handler);

    /**
     * Returns the estimated size of uncompressed bytes to process.
     * 
     * @return the estimated size in bytes
     */
    public long estimatedSize() {
        return ESTIMATED_SIZE;
    }

    /**
     * Returns the number of sub-actions registered inside this container task.
     * 
     * @return the entry actions count
     */
    public int count() {
        return COUNT;
    }

    /**
     * Returns a standard ascending priority sorting comparator, prioritizing smaller sizes and lower counts, tie-broken by
     * container file for a deterministic order.
     * <p>
     * This ordering is a scheduling priority and is intentionally not consistent with equals: it must only be used for sorting,
     * never in sorted sets or maps.
     * 
     * @return a {@link Comparator} for sorting container actions
     */
    public static Comparator<ContainerAction> comparator() {
        return (o1, o2) -> {
            var cmp = Long.compare(o1.estimatedSize(), o2.estimatedSize());
            if (cmp != 0)
                return cmp;
            cmp = Integer.compare(o1.count(), o2.count());
            if (cmp != 0)
                return cmp;
            return String.valueOf(o1.container.getFile()).compareTo(String.valueOf(o2.container.getFile()));
        };
    }

    /**
     * Returns a reverse descending priority sorting comparator (e.g., executing largest tasks first).
     * <p>
     * Same contract as {@link #comparator()}: sorting only, not consistent with equals.
     * 
     * @return a descending {@link Comparator} for sorting container actions
     */
    public static Comparator<ContainerAction> rcomparator() {
        return comparator().reversed();
    }

    /**
     * Executes registered entry actions inside the provided generic compressed archive.
     * 
     * @param session the current active session
     * @param handler the UI progress indicator
     * @param archive the target compressed archive interface
     * 
     * @return {@code true} if all child actions were executed successfully, otherwise {@code false}
     * 
     * @throws IOException if a file system or archive reading error occurs
     */
    protected boolean archiveAction(final Session session, final ProgressHandler handler, Archive archive) throws IOException {
        try (archive) {
            var i = 0;
            for (final EntryAction action : entryActions) {
                i++;
                if (!action.doAction(session, archive, handler, i, entryActions.size())) {
                    Log.err(() -> String.format(ACTION_TO_S_AT_S_FAILED, container.getFile().getName(), action.entry.getRelFile()));
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Executes registered entry actions inside the targeted directory path.
     * 
     * @param session the current active session
     * @param handler the UI progress indicator
     * @param target the target directory path on disk
     * 
     * @return {@code true} if all child actions were executed successfully, otherwise {@code false}
     */
    protected boolean pathAction(final Session session, final ProgressHandler handler, final Path target) {
        var i = 0;
        for (final EntryAction action : entryActions) {
            i++;
            if (!action.doAction(session, target, handler, i, entryActions.size())) {
                Log.err(() -> String.format(ACTION_TO_S_AT_S_FAILED, container.getFile().getName(), action.entry.getRelFile()));
                return false;
            }
        }
        return true;
    }

    /**
     * Executes registered entry actions inside the targeted Zip file structure.
     * 
     * @param session the current active session
     * @param handler the UI progress indicator
     * @param zipf the target zip file instance
     * 
     * @return {@code true} if all child actions were executed successfully, otherwise {@code false}
     */
    protected boolean zosAction(final Session session, final ProgressHandler handler, final ZipFile zipf) {
        var i = 0;
        final var level = ZipLevel.valueOf(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.zip_compression_level));
        for (final EntryAction action : entryActions) {
            i++;
            final var zipp = new ZipParameters();
            if (format == FormatOptions.TZIP)
                zipp.setCompressionLevel(CompressionLevel.FASTEST);
            else {
                switch (level) {
                    case STORE -> zipp.setCompressionMethod(CompressionMethod.STORE);
                    case FASTEST -> zipp.setCompressionLevel(CompressionLevel.FASTEST);
                    case FAST -> zipp.setCompressionLevel(CompressionLevel.FAST);
                    case NORMAL -> zipp.setCompressionLevel(CompressionLevel.NORMAL);
                    case MAXIMUM -> zipp.setCompressionLevel(CompressionLevel.MAXIMUM);
                    case ULTRA -> zipp.setCompressionLevel(CompressionLevel.ULTRA);
                    default -> zipp.setCompressionLevel(CompressionLevel.NORMAL);
                }
            }

            if (!action.doAction(session, zipf, zipp, handler, i, entryActions.size())) {
                Log.err(() -> String.format(ACTION_TO_S_AT_S_FAILED, container.getFile().getName(), action.entry.getRelFile()));
                return false;
            }
        }
        return true;
    }

    /**
     * Executes registered entry actions inside the targeted zip file system context.
     * 
     * @param session the current active session
     * @param handler the UI progress indicator
     * @param fs the target file system wrapper
     * 
     * @return {@code true} if all child actions were executed successfully, otherwise {@code false}
     */
    protected boolean fsAction(final Session session, final ProgressHandler handler, final FileSystem fs) {
        var i = 0;
        for (final EntryAction action : entryActions) {
            i++;
            if (!action.doAction(session, fs, handler, i, entryActions.size())) {
                Log.err(() -> String.format(ACTION_TO_S_AT_S_FAILED, container.getFile().getName(), action.entry.getRelFile()));
                return false;
            }
        }
        return true;
    }

}
