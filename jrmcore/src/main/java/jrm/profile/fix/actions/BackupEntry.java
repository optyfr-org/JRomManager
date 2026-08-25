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
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import jrm.aui.progress.ProgressHandler;
import jrm.compressors.Archive;
import jrm.compressors.SevenZipArchive;
import jrm.compressors.ZipTools;
import jrm.misc.Log;
import jrm.profile.data.Container.Type;
import jrm.profile.data.Entry;
import jrm.security.Session;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;

/**
 * Describe an entry to backup, will take appropriate actions to extract entry before copying to provided backup {@link FileSystem}
 * 
 * @author optyfr
 */
public class BackupEntry extends EntryAction {
    private static final String BACKUP_OF_S = "Backup of %s";

    /**
     * Constructor
     * 
     * @param entry the entry to backup
     */
    public BackupEntry(final Entry entry) {
        super(entry);
    }

    @Override
    public boolean doAction(final Session session, Archive archive, ProgressHandler handler, int i, int max) {
        throw new UnsupportedOperationException("Backup to Archive is not supported: " + archive);
    }

    @Override
    public boolean doAction(final Session session, Path target, ProgressHandler handler, int i, int max) {
        throw new UnsupportedOperationException("Backup to Path is not supported: " + target);
    }

    @Override
    public String toString() {
        return String.format(BACKUP_OF_S, entry); // $NON-NLS-1$
    }

    @Override
    public boolean doAction(Session session, ZipFile zipf, ZipParameters zipp, ProgressHandler handler, int i, int max) {
        final var dstPathCrc = entry.getCrc() + '_' + entry.getSize();
        final String dstPath;
        if (entry.getSha1() != null)
            dstPath = entry.getSha1();
        else if (entry.getMd5() != null)
            dstPath = entry.getMd5();
        else
            dstPath = entry.getCrc() + '_' + entry.getSize();
        handler.setProgress(null, null, null, progress(i, max, String.format(BACKUP_OF_S, entry.getName()))); // $NON-NLS-1$
        String srcname = null;
        try {
            if (!dstPath.equals(dstPathCrc)) {
                final var dstPathCrcHdr = zipf.getFileHeader(ZipTools.toZipEntry(dstPathCrc));
                if (dstPathCrcHdr != null)
                    zipf.removeFile(dstPathCrcHdr);
            }
            final var dstPathHdr = zipf.getFileHeader(ZipTools.toZipEntry(dstPath));
            if (dstPathHdr != null)
                return true;
            if (entry.getParent().getType() == Type.DIR) {
                final var srcpath = entry.getParent().getFile().toPath().resolve(entry.getFile());
                srcname = srcpath.toString();
                zipp.setFileNameInZip(dstPath);
                zipf.addFile(srcpath.toFile(), zipp);
                return true;
            } else if (entry.getParent().getType() == Type.FAKE) {
                final var srcpath = entry.getParent().getFile().getParentFile().toPath().resolve(entry.getFile());
                srcname = srcpath.toString();
                zipp.setFileNameInZip(dstPath);
                zipf.addFile(srcpath.toFile(), zipp);
                return true;
            } else if (entry.getParent().getType() == Type.ZIP) {
                try (final var srczf = new ZipFile(entry.getParent().getFile())) {
                    final var srchdr = srczf.getFileHeader(ZipTools.toZipEntry(entry.getFile()));
                    srcname = entry.getFile();
                    zipp.setFileNameInZip(dstPath);
                    zipf.addStream(srczf.getInputStream(srchdr), zipp);
                    return true;
                }
            } else if (entry.getParent().getType() == Type.SEVENZIP) {
                try (Archive srcarchive = new SevenZipArchive(session, entry.getParent().getFile())) {
                    if (srcarchive.extract(entry.getFile()) != null) {
                        final var srcpath = new File(srcarchive.getTempDir(), entry.getFile());
                        srcname = srcpath.toString();
                        zipp.setFileNameInZip(dstPath);
                        zipf.addFile(srcpath, zipp);
                        return true;
                    }
                }
            }
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
            Log.err("add from " + entry.getParent().getRelFile() + "@" + srcname + " to " + parent.container.getFile().getName() + "@" + dstPath + " failed"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        }
        return false;
    }

    @Override
    public boolean doAction(final Session session, final FileSystem dstfs, final ProgressHandler handler, int i, int max) {
        final var dstPathCrc = dstfs.getPath(entry.getCrc() + '_' + entry.getSize());
        final Path dstPath;
        if (entry.getSha1() != null)
            dstPath = dstfs.getPath(entry.getSha1());
        else if (entry.getMd5() != null)
            dstPath = dstfs.getPath(entry.getMd5());
        else
            dstPath = dstfs.getPath(entry.getCrc() + '_' + entry.getSize());
        handler.setProgress(null, null, null, progress(i, max, String.format(BACKUP_OF_S, entry.getName()))); // $NON-NLS-1$
        Path srcpath = null;
        try {
            final var parent2 = dstPath.getParent();
            if (parent2 != null)
                Files.createDirectories(parent2);
            if (!dstPath.equals(dstPathCrc) && Files.exists(dstPathCrc))
                Files.delete(dstPathCrc);
            if (Files.exists(dstPath))
                return true;
            if (entry.getParent().getType() == Type.DIR) {
                srcpath = entry.getParent().getFile().toPath().resolve(entry.getFile());
                Files.copy(srcpath, dstPath, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                return true;
            } else if (entry.getParent().getType() == Type.FAKE) {
                srcpath = entry.getParent().getFile().getParentFile().toPath().resolve(entry.getFile());
                Files.copy(srcpath, dstPath, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                return true;
            } else if (entry.getParent().getType() == Type.ZIP) {
                try (final var srcfs = FileSystems.newFileSystem(entry.getParent().getFile().toPath(), (ClassLoader) null);) {
                    srcpath = srcfs.getPath(entry.getFile());
                    Files.copy(srcpath, dstPath, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                    return true;
                }
            } else if (entry.getParent().getType() == Type.SEVENZIP) {
                try (Archive srcarchive = new SevenZipArchive(session, entry.getParent().getFile())) {
                    if (srcarchive.extract(entry.getFile()) != null) {
                        srcpath = new File(srcarchive.getTempDir(), entry.getFile()).toPath();
                        Files.copy(srcpath, dstPath, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                        return true;
                    }
                }
            }
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
            Log.err("add from " + entry.getParent().getRelFile() + "@" + srcpath + " to " + parent.container.getFile().getName() + "@" + dstPath + " failed"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        }
        return false;
    }

}
