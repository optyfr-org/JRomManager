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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

import jrm.aui.progress.ProgressHandler;
import jrm.compressors.Archive;
import jrm.compressors.SevenZipArchive;
import jrm.compressors.ZipTools;
import jrm.locale.Messages;
import jrm.misc.IOUtils;
import jrm.misc.Log;
import jrm.profile.data.Entity;
import jrm.profile.data.EntityBase;
import jrm.profile.data.Entry;
import jrm.profile.data.Rom;
import jrm.security.Session;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.InternalZipConstants;

/**
 * Concrete action for copying and adding a files/ROM entry to a targeted game package container.
 * <p>
 * This supports multi-formatted sources and destinations such as standard directories, standalone ZIP/7Z archives, and virtual zip
 * file systems.
 * </p>
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public class AddEntry extends EntryAction {
    /**
     * Log error template when adding from archive to another archive fails.
     */
    private static final String ADD_FROM_S_AT_S_TO_S_AT_S_FAILED = "add from %s@%s to %s@%s failed";

    /**
     * Log error template when adding from file system to archive fails.
     */
    private static final String ADD_FROM_S_TO_S_AT_S_FAILED = "add from %s to %s@%s failed";

    /**
     * Localized progress message key indicating that an entry is being added.
     */
    private static final String ADD_ENTRY_ADDING = "AddEntry.Adding";

    /**
     * The related entity (e.g., a {@link Rom}) being targeted.
     */
    private final EntityBase entity;

    /**
     * Constructs a new {@code AddEntry} action.
     * 
     * @param entity the related {@link EntityBase} target
     * @param entry the source {@link Entry} details to add
     */
    public AddEntry(final EntityBase entity, final Entry entry) {
        super(entry);
        this.entity = entity;
    }

    /**
     * Performs the add entry operation inside a target file system directory on disk.
     * 
     * @param session the current active user session
     * @param target the target parent folder path on disk
     * @param handler the visual UI progress bar status handler
     * @param i the current progression index
     * @param max the total progression maximum
     * 
     * @return {@code true} if the add action succeeded, otherwise {@code false}
     */
    @Override
    public boolean doAction(final Session session, final Path target, final ProgressHandler handler, int i, int max) {
        final Path dstpath;
        try {
            dstpath = IOUtils.resolveContainedPath(target, entity.getName());
        } catch (final IOException e) {
            Log.err(String.format(ADD_FROM_S_TO_S_AT_S_FAILED, entry.getRelFile(), parent != null ? parent.container.getFile().getName() : target, entity.getName()), e);
            return false;
        }
        handler.setProgress(null, null, null, progress(i, max, String.format(session.getMsgs().getString(ADD_ENTRY_ADDING), entity.getName()))); // $NON-NLS-1$
        switch (entry.getParent().getType()) {
            case DIR:
                return dir2Path(dstpath);
            case FAKE:
                return fake2Path(dstpath);
            case ZIP:
                return zip2Path(dstpath);
            default:
                return default2Path(session, dstpath);
        }
    }

    /**
     * Performs the add entry operation inside a target standalone Zip file using ZipFile.
     * 
     * @param session the current active user session
     * @param zipf the target ZipFile package
     * @param zipp the zip file parameters
     * @param handler the visual UI progress bar status handler
     * @param i the current progression index
     * @param max the total progression maximum
     * 
     * @return {@code true} if the add action succeeded, otherwise {@code false}
     */
    @Override
    public boolean doAction(Session session, ZipFile zipf, ZipParameters zipp, ProgressHandler handler, int i, int max) {
        handler.setProgress(null, null, null, progress(i, max, String.format(session.getMsgs().getString(ADD_ENTRY_ADDING), entity.getName()))); // $NON-NLS-1$
        switch (entry.getParent().getType()) {
            case DIR:
                return dir2zos(zipf, zipp, entity.getName());
            case FAKE:
                return fake2zos(zipf, zipp, entity.getName());
            case ZIP:
                return zip2zos(zipf, zipp, entity.getName());
            default:
                return default2zos(session, zipf, zipp, entity.getName());
        }
    }

    /**
     * Extracts the entry from a default 7z/RAR compressed archive source and saves it to a path.
     * 
     * @param session the current active session
     * @param dstpath the destination path on disk
     * 
     * @return {@code true} if the operation succeeded, otherwise {@code false}
     */
    private boolean default2Path(final Session session, final Path dstpath) {
        Path srcpath = null;
        try (Archive srcarchive = new SevenZipArchive(session, entry.getParent().getFile())) {
            final File srcfile;
            if ((srcfile = srcarchive.extract(entry.getFile())) != null) {
                srcpath = srcfile.toPath();
                final var parent = dstpath.getParent();
                if (parent != null)
                    Files.createDirectories(parent);
                Files.copy(srcpath, dstpath, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (final IOException e) {
            Log.err(String.format(ADD_FROM_S_AT_S_TO_S_AT_S_FAILED, entry.getParent().getFile().getName(), entry.getRelFile(), parent.container.getFile().getName(),
                    entity.getName()), e);
        }
        return false;
    }

    /**
     * Extracts the entry from a default 7z/RAR compressed archive source and adds it to a zip file.
     * 
     * @param session the current active session
     * @param zipf the target ZipFile
     * @param zipp the configuration zip parameters
     * @param zentry the zip entry name
     * 
     * @return {@code true} if the operation succeeded, otherwise {@code false}
     */
    private boolean default2zos(final Session session, final ZipFile zipf, final ZipParameters zipp, String zentry) {
        try (Archive srcarchive = new SevenZipArchive(session, entry.getParent().getFile())) {
            final File srcfile;
            if ((srcfile = srcarchive.extract(entry.getFile())) != null) {
                zipp.setFileNameInZip(zentry);
                zipf.addFile(srcfile, zipp);
            }
            return true;
        } catch (final IOException e) {
            Log.err(String.format(ADD_FROM_S_AT_S_TO_S_AT_S_FAILED, entry.getParent().getFile().getName(), entry.getRelFile(), parent.container.getFile().getName(),
                    entity.getName()), e);
        }
        return false;
    }

    /**
     * Extracts the entry from a zip source and copies it directly to a path.
     * 
     * @param dstpath the destination path
     * 
     * @return {@code true} if the copy succeeded, otherwise {@code false}
     */
    private boolean zip2Path(final Path dstpath) {
        try (final var srczf = new ZipFile(entry.getParent().getFile())) {
            srczf.setBufferSize(Math.clamp(entry.getSize(), InternalZipConstants.MIN_BUFF_SIZE, 65536));
            final var srcheader = srczf.getFileHeader(ZipTools.toZipEntry(entry.getFile()));
            final var srcstream = srczf.getInputStream(srcheader);
            Files.copy(srcstream, dstpath, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (final Exception e) {
            Log.err(String.format(ADD_FROM_S_AT_S_TO_S_AT_S_FAILED, entry.getParent().getFile().getName(), entry.getRelFile(), parent.container.getFile().getName(),
                    entity.getName()), e);
        }
        return false;
    }

    /**
     * Extracts the entry from a zip source and copies it to a target zip archive.
     * 
     * @param zipf the target ZipFile
     * @param zipp the zip parameters
     * @param zentry the name of the entry inside the zip
     * 
     * @return {@code true} if the add succeeded, otherwise {@code false}
     */
    private boolean zip2zos(final ZipFile zipf, final ZipParameters zipp, String zentry) {
        try (final var srczf = new ZipFile(entry.getParent().getFile())) {
            srczf.setBufferSize(Math.clamp(entry.getSize(), InternalZipConstants.MIN_BUFF_SIZE, 65536));
            final var srcheader = srczf.getFileHeader(ZipTools.toZipEntry(entry.getFile()));
            final var srcstream = srczf.getInputStream(srcheader);
            zipp.setFileNameInZip(zentry);
            zipf.addStream(srcstream, zipp);
            return true;
        } catch (final Exception e) {
            Log.err(String.format(ADD_FROM_S_AT_S_TO_S_AT_S_FAILED, entry.getParent().getFile().getName(), entry.getRelFile(), parent.container.getFile().getName(),
                    entity.getName()), e);
        }
        return false;
    }

    /**
     * Resolves fake uncompressed sources and copies them to a path.
     * 
     * @param dstpath the target path
     * 
     * @return {@code true} if copy succeeded, otherwise {@code false}
     */
    private boolean fake2Path(final Path dstpath) {
        Path srcpath = null;
        try {
            srcpath = entry.getParent().getFile().getParentFile().toPath().resolve(entry.getFile());
            final var parent = dstpath.getParent();
            if (parent != null)
                Files.createDirectories(parent);
            Files.copy(srcpath, dstpath, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            Log.err(String.format(ADD_FROM_S_TO_S_AT_S_FAILED, srcpath, parent.container.getFile().getName(), dstpath), e);
        }
        return false;
    }

    /**
     * Resolves fake uncompressed sources and copies them to a target zip.
     * 
     * @param zipf the target ZipFile
     * @param zipp the zip parameters
     * @param zentry the name of the entry in the zip
     * 
     * @return {@code true} if addition succeeded, otherwise {@code false}
     */
    private boolean fake2zos(final ZipFile zipf, final ZipParameters zipp, String zentry) {
        Path srcpath = null;
        try {
            srcpath = entry.getParent().getFile().getParentFile().toPath().resolve(entry.getFile());
            zipp.setFileNameInZip(zentry);
            zipf.addFile(srcpath.toFile(), zipp);
            return true;
        } catch (IOException e) {
            Log.err(String.format(ADD_FROM_S_AT_S_TO_S_AT_S_FAILED, entry.getParent().getFile().getName(), entry.getRelFile(), parent.container.getFile().getName(),
                    entity.getName()), e);
        }
        return false;
    }

    /**
     * Resolves uncompressed directory sources and copies them to a path.
     * 
     * @param dstpath the destination path
     * 
     * @return {@code true} if copy succeeded, otherwise {@code false}
     */
    private boolean dir2Path(final Path dstpath) {
        Path srcpath = null;
        try {
            srcpath = entry.getParent().getFile().toPath().resolve(entry.getFile());
            final var parent = dstpath.getParent();
            if (parent != null)
                Files.createDirectories(parent);
            Files.copy(srcpath, dstpath, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            Log.err(String.format(ADD_FROM_S_TO_S_AT_S_FAILED, srcpath, parent.container.getFile().getName(), dstpath), e);
        }
        return false;
    }

    /**
     * Resolves uncompressed directory sources and adds them to a target zip file.
     * 
     * @param zipf the target ZipFile
     * @param zipp the zip parameters
     * @param zentry the entry name
     * 
     * @return {@code true} if addition succeeded, otherwise {@code false}
     */
    private boolean dir2zos(final ZipFile zipf, final ZipParameters zipp, String zentry) {
        Path srcpath = null;
        try {
            srcpath = entry.getParent().getFile().toPath().resolve(entry.getFile());
            zipp.setFileNameInZip(zentry);
            zipf.addFile(srcpath.toFile(), zipp);
            return true;
        } catch (IOException e) {
            Log.err(String.format(ADD_FROM_S_AT_S_TO_S_AT_S_FAILED, entry.getParent().getFile().getName(), entry.getRelFile(), parent.container.getFile().getName(),
                    entity.getName()), e);
        }
        return false;
    }

    /**
     * Performs the add entry operation inside a target compressed general archive wrapper.
     * 
     * @param session the current active session
     * @param dstarchive the destination archive wrapper
     * @param handler the progress visual status tracker
     * @param i the progression index
     * @param max the progression maximum limits
     * 
     * @return {@code true} if the add operation succeeded, otherwise {@code false}
     */
    @Override
    public boolean doAction(final Session session, final Archive dstarchive, final ProgressHandler handler, int i, int max) {
        handler.setProgress(null, null, null, progress(i, max, String.format(session.getMsgs().getString(ADD_ENTRY_ADDING), entity.getName()))); // $NON-NLS-1$
        switch (entry.getParent().getType()) {
            case DIR:
                return dir2Archive(dstarchive);
            case FAKE:
                return fake2Archive(dstarchive);
            case ZIP:
                return zip2Archive(dstarchive);
            default:
                return default2Archive(session, dstarchive);
        }
    }

    /**
     * Copies default compressed source entries into a general archive wrapper.
     * 
     * @param session the current active session
     * @param dstarchive the destination archive
     * 
     * @return {@code true} if addition succeeded, otherwise {@code false}
     */
    private boolean default2Archive(final Session session, final Archive dstarchive) {
        try (Archive srcarchive = new SevenZipArchive(session, entry.getParent().getFile())) {
            final File file;
            if ((file = srcarchive.extract(entry.getFile())) != null)
                return dstarchive.add(file, entity.getName()) == 0;
        } catch (final IOException e) {
            Log.err(String.format(ADD_FROM_S_AT_S_TO_S_AT_S_FAILED, entry.getParent().getFile().getName(), entry.getRelFile(), parent.container.getFile().getName(),
                    entity.getName()), e);
        }
        return false;
    }

    /**
     * Copies zip source entries into a general archive wrapper.
     * 
     * @param dstarchive the destination archive wrapper
     * 
     * @return {@code true} if addition succeeded, otherwise {@code false}
     */
    private boolean zip2Archive(final Archive dstarchive) {
        try (final var srczf = new ZipFile(entry.getParent().getFile())) {
            srczf.setBufferSize( Math.clamp(entry.getSize(), InternalZipConstants.MIN_BUFF_SIZE, 65536));
            final var srcheader = srczf.getFileHeader(ZipTools.toZipEntry(entry.getFile()));
            final var srcstream = srczf.getInputStream(srcheader);
            return dstarchive.addStdIn(srcstream, entity.getName()) == 0;
        } catch (final Exception e) {
            Log.err(String.format(ADD_FROM_S_AT_S_TO_S_AT_S_FAILED, entry.getParent().getFile().getName(), entry.getRelFile(), parent.container.getFile().getName(),
                    entity.getName()), e);
        }
        return false;
    }

    /**
     * Copies fake source files into a general archive wrapper.
     * 
     * @param dstarchive the destination archive wrapper
     * 
     * @return {@code true} if addition succeeded, otherwise {@code false}
     */
    private boolean fake2Archive(final Archive dstarchive) {
        try {
            Path srcpath = entry.getParent().getFile().getParentFile().toPath().resolve(entry.getFile());
            return dstarchive.add(srcpath.toFile(), entity.getName()) == 0;
        } catch (IOException e) {
            Log.err(String.format(ADD_FROM_S_TO_S_AT_S_FAILED, entry.getRelFile(), parent.container.getFile().getName(), entity.getName()), e);
        }
        return false;
    }

    /**
     * Copies uncompressed folder source files into a general archive wrapper.
     * 
     * @param dstarchive the destination archive wrapper
     * 
     * @return {@code true} if addition succeeded, otherwise {@code false}
     */
    private boolean dir2Archive(final Archive dstarchive) {
        try {
            Path srcpath = entry.getParent().getFile().toPath().resolve(entry.getFile());
            return dstarchive.add(srcpath.toFile(), entity.getName()) == 0;
        } catch (IOException e) {
            Log.err(String.format(ADD_FROM_S_TO_S_AT_S_FAILED, entry.getRelFile(), parent.container.getFile().getName(), entity.getName()), e);
        }
        return false;
    }

    /**
     * Performs the add entry operation inside a virtual zip FileSystem.
     * 
     * @param session the current active user session
     * @param dstfs the target virtual zip FileSystem
     * @param handler the progress visual status tracker
     * @param i the progression index
     * @param max the progression maximum limits
     * 
     * @return {@code true} if addition succeeded, otherwise {@code false}
     */
    @Override
    public boolean doAction(final Session session, final FileSystem dstfs, final ProgressHandler handler, int i, int max) {
        final var dstpath = dstfs.getPath(entity.getName());
        handler.setProgress(null, null, null, progress(i, max, String.format(session.getMsgs().getString(ADD_ENTRY_ADDING), entity.getName()))); // $NON-NLS-1$
        switch (entry.getParent().getType()) {
            case DIR:
                return dir2FS(dstpath);
            case FAKE:
                return fake2FS(dstpath);
            case ZIP:
                return zip2FS(dstpath);
            default:
                return default2FS(session, dstpath);
        }
    }

    /**
     * Copies default compressed archive entries into a virtual zip FileSystem.
     * 
     * @param session the current active session
     * @param dstpath the destination path inside the FileSystem
     * 
     * @return {@code true} if addition succeeded, otherwise {@code false}
     */
    private boolean default2FS(final Session session, final Path dstpath) {
        Path srcpath = null;
        try (Archive srcarchive = new SevenZipArchive(session, entry.getParent().getFile())) {
            final File srcfile;
            if ((srcfile = srcarchive.extract(entry.getFile())) != null) {
                final var parentDstPath = dstpath.getParent();
                if (parentDstPath != null)
                    Files.createDirectories(parentDstPath);
                srcpath = srcfile.toPath();
                Files.copy(srcpath, dstpath, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
        } catch (IOException e) {
            Log.err(String.format(ADD_FROM_S_AT_S_TO_S_AT_S_FAILED, entry.getParent().getRelFile(), srcpath, parent.container.getFile().getName(), dstpath), e);
        }
        return false;
    }

    /**
     * Copies zip source entries into a virtual zip FileSystem.
     * 
     * @param dstpath the destination path inside the FileSystem
     * 
     * @return {@code true} if addition succeeded, otherwise {@code false}
     */
    private boolean zip2FS(final Path dstpath) {
        Path srcpath = null;
        try (final var srcfs = FileSystems.newFileSystem(entry.getParent().getFile().toPath(), Collections.singletonMap("readOnly", true));) {
            final var parentDstPath = dstpath.getParent();
            if (parentDstPath != null)
                Files.createDirectories(parentDstPath);
            srcpath = srcfs.getPath(entry.getFile());
            Files.copy(srcpath, dstpath, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            Log.err(String.format(ADD_FROM_S_AT_S_TO_S_AT_S_FAILED, entry.getParent().getRelFile(), srcpath, parent.container.getFile().getName(), dstpath), e);
        }
        return false;
    }

    /**
     * Copies fake source files into a virtual zip FileSystem.
     * 
     * @param dstpath the destination path inside the FileSystem
     * 
     * @return {@code true} if addition succeeded, otherwise {@code false}
     */
    private boolean fake2FS(final Path dstpath) {
        Path srcpath = null;
        try {
            final var parentDstPath = dstpath.getParent();
            if (parentDstPath != null)
                Files.createDirectories(parentDstPath);
            srcpath = entry.getParent().getFile().getParentFile().toPath().resolve(entry.getFile());
            Files.copy(srcpath, dstpath, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            Log.err(String.format(ADD_FROM_S_TO_S_AT_S_FAILED, srcpath, parent.container.getFile().getName(), dstpath), e);
        }
        return false;
    }

    /**
     * Copies uncompressed directory source files into a virtual zip FileSystem.
     * 
     * @param dstpath the destination path inside the FileSystem
     * 
     * @return {@code true} if addition succeeded, otherwise {@code false}
     */
    private boolean dir2FS(final Path dstpath) {
        Path srcpath = null;
        try {
            var parentDstPath = dstpath.getParent();
            if (parentDstPath != null)
                Files.createDirectories(parentDstPath);
            srcpath = entry.getParent().getFile().toPath().resolve(entry.getFile());
            Files.copy(srcpath, dstpath, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            Log.err(String.format(ADD_FROM_S_TO_S_AT_S_FAILED, srcpath, parent.container.getFile().getName(), dstpath), e);
        }
        return false;
    }

    /**
     * Returns a localized string representation describing the addition operation.
     * 
     * @return standard descriptive text
     */
    @Override
    public String toString() {
        return String.format(Messages.getString("AddEntry.Add"), entry, entity); //$NON-NLS-1$
    }

    /**
     * Estimates the uncompressed size of the entity being added.
     * 
     * @return estimated size in bytes
     */
    @Override
    public long estimatedSize() {
        if (entity instanceof Entity e)
            return e.getSize();
        return 0L;
    }

}
