/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.fix.actions;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import jrm.aui.progress.ProgressHandler;
import jrm.compressors.SevenZipArchive;
import jrm.compressors.ZipArchive;
import jrm.locale.Messages;
import jrm.misc.IOUtils;
import jrm.misc.Log;
import jrm.profile.data.Container;
import jrm.profile.scan.options.FormatOptions;
import jrm.security.Session;
import net.lingala.zip4j.ZipFile;

/**
 * specialized class when container need to be created before doing actions on entries (which should be only {@link AddEntry})
 * 
 * @author optyfr
 */
public class CreateContainer extends ContainerAction {
    /**
     * the uncompressed datasize of all entries to add (for temp file threshold purpose)
     */
    private final long dataSize;

    /**
     * constructor
     * 
     * @param container the container to create
     * @param format the desired format
     * @param dataSize the uncompressed data size supposed to be added
     */
    public CreateContainer(final Container container, final FormatOptions format, final long dataSize) {
        super(container, format);
        this.dataSize = dataSize;
    }

    /**
     * shortcut static method to get an instance of {@link CreateContainer}
     * 
     * @param action the potentially already existing {@link CreateContainer}
     * @param container the container to create
     * @param format the desired format
     * @param dataSize the uncompressed data size supposed to be added
     * 
     * @return a {@link CreateContainer}
     */
    public static CreateContainer getInstance(CreateContainer action, final Container container, final FormatOptions format, final long dataSize) {
        if (action == null)
            action = new CreateContainer(container, format, dataSize);
        return action;
    }

    /**
     * shortcut static method to get an instance of {@link CreateContainer}
     * 
     * @param action the potentially already existing {@link CreateContainer}
     * @param container the container to create
     * @param format the desired format
     * @param dataSize the uncompressed data size supposed to be added
     * 
     * @return a {@link CreateContainer}
     */
    public static CreateContainer getInstance(AtomicReference<CreateContainer> action, final Container container, final FormatOptions format, final long dataSize) {
        if (action.get() == null)
            action.set(new CreateContainer(container, format, dataSize));
        return action.get();
    }

    @Override
    public boolean doAction(final Session session, final ProgressHandler handler) {
        handler.setProgress(toDocument(toNoBR(String.format(escape(session.getMsgs().getString("CreateContainer.Creating")), //$NON-NLS-1$
                toBlue(container.getRelAW().getFullName(container.getFile().getName())), toPurple(container.getRelAW().getDescription())))));
        if (container.getType() == Container.Type.ZIP) {
            if (format == FormatOptions.ZIP || format == FormatOptions.TZIP) {
                return createZip(session, handler);
            } else if (format == FormatOptions.ZIPE) {
                return createZipE(session, handler);
            }
            return false;
        } else if (container.getType() == Container.Type.SEVENZIP) {
            return createSevenZip(session, handler);
        } else if (container.getType() == Container.Type.DIR || container.getType() == Container.Type.FAKE) {
            return createDirOrFake(session, handler);
        }
        return false;
    }

    /**
     * @param session
     * @param handler
     * 
     * @return
     */
    private boolean createDirOrFake(final Session session, final ProgressHandler handler) {
        try {
            final Path target;
            if (container.getType() == Container.Type.DIR) {
                target = container.getFile().toPath();
                IOUtils.createDirectories(target);
            } else
                target = container.getFile().getParentFile().toPath();
            return pathAction(session, handler, target);
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
    private boolean createSevenZip(final Session session, final ProgressHandler handler) {
        container.getFile().getParentFile().mkdirs();
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
    private boolean createZipE(final Session session, final ProgressHandler handler) {
        container.getFile().getParentFile().mkdirs();
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
    private boolean createZip(final Session session, final ProgressHandler handler) {
        try (final var zipf = new ZipFile(container.getFile())) {
            return zosAction(session, handler, zipf);
        } catch (Exception e) {
            Log.err(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public String toString() {
        final var str = new StringBuilder(Messages.getString("CreateContainer.Create")).append(container); //$NON-NLS-1$
        for (final EntryAction action : entryActions)
            str.append("\n\t").append(action); //$NON-NLS-1$
        return str.toString();
    }

    @Override
    public long estimatedSize() {
        return dataSize < Long.MAX_VALUE ? dataSize : 0;
    }

    @Override
    public int count() {
        return entryActions.size();
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
