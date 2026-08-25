/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.compressors;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Archive common interface
 * 
 * @author optyfr
 */
public interface Archive extends Closeable {
    /**
     * get a suitable Temporary directory
     * 
     * @return the Temporary directory as {@link File}
     * 
     * @throws IOException in case of IO error, can happen if no suitable temp directory is found
     */
    File getTempDir() throws IOException;

    /**
     * Extract all files from Archive
     * 
     * @return 0 for success, -1 for error
     * 
     * @throws IOException in case of IO error
     */
    public int extract() throws IOException;

    /**
     * Extract a file entry from Archive
     * 
     * @param entry the file entry name including path as stored in archive
     * 
     * @return the {@link File} extracted
     * 
     * @throws IOException in case of IO error, can happen if entry not found or entry is a directory
     */
    public File extract(String entry) throws IOException;

    /**
     * Extract a file entry from Archive into an {@link InputStream}
     * 
     * @param entry the file entry name including path as stored in archive
     * 
     * @return the resulting {@link InputStream}
     * 
     * @throws IOException in case of IO error, can happen if entry not found or entry is a directory
     */
    public InputStream extractStdOut(String entry) throws IOException;

    /**
     * Add an entry to the archive, The default assume that the {@code baseDir} is from {@link #getTempDir()} then return
     * {@link #add(File baseDir, String entry)}
     * 
     * @param entry the entry to add, can contains sub directories, but always have to be a file
     * 
     * @return 0 for success, anything else for error, -1 is reserved for read only archives
     * 
     * @throws IOException in case of IO error, can happen if entry not found or entry is a directory
     */
    public default int add(String entry) throws IOException {
        return add(getTempDir(), entry);
    }

    /**
     * Add an entry to the archive, baseDir is the base directory of the entry and should not be included in the archive path
     * 
     * @param baseDir the base directory
     * @param entry the entry to add, can contains sub directories, but always have to be a file
     * 
     * @return 0 for success, anything else for error, -1 is reserved for read only archives
     * 
     * @throws IOException in case of IO error, can happen if entry not found or entry is a directory
     */
    public int add(File baseDir, String entry) throws IOException;

    /**
     * Add an entry to the archive, named by entry, src data come from an {@link InputStream}
     * 
     * @param src the src data
     * @param entry the entry name (with path)
     * 
     * @return 0 for success, anything else for error, -1 is reserved for read only archives
     * 
     * @throws IOException in case of IO error, can happen if entry already exists or entry is a directory
     */
    public int addStdIn(InputStream src, String entry) throws IOException;

    /**
     * Delete an entry from archive
     * 
     * @param entry the entry to delete
     * 
     * @return 0 for success, anything else for error, -1 is reserved for read only archives
     * 
     * @throws IOException in case of IO error
     */
    public int delete(String entry) throws IOException;

    /**
     * Rename an entry into an archive. Be warned that, if using some external commands in backend (like zip), renaming may not
     * exists! So that, this function will need to extract, rename, add, then delete!
     * 
     * @param entry the entry to rename
     * @param newname the new name
     * 
     * @return 0 for success, anything else for error, -1 is reserved for read only archives
     * 
     * @throws IOException in case of IO error, can happen if new name already exists
     */
    public int rename(String entry, String newname) throws IOException;

    /**
     * Duplicate an entry. Generally, it is handled by extracting, then adding
     * 
     * @param entry the entry to duplicate (with path)
     * @param newname the new name (with path)
     * 
     * @return 0 for success, anything else for error, -1 is reserved for read only archives
     * 
     * @throws IOException in case of IO error, can happen if new name already exists
     */
    public int duplicate(String entry, String newname) throws IOException;
}
