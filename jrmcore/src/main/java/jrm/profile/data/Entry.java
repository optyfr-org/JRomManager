/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.data;

import java.io.Serializable;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

import org.apache.commons.io.FilenameUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * Entry is a container item (i.e., a directory file or a compressed archive entry). It represents an individual scanned file with
 * size, last modified timestamp, and checksum hashes.
 *
 * @author optyfr
 */
public class Entry implements Serializable {
    /**
     * The entry name (with relative path).
     */
    private String file;

    /**
     * The relative file name reference to show to the user.
     */
    private String relfile;

    /**
     * The entry size in bytes.
     *
     * @param size the entry size to set
     * 
     * @return the entry size in bytes
     */
    protected @Getter @Setter long size = 0;

    /**
     * The last modified date in Unix time (milliseconds).
     *
     * @param modified the modified timestamp to set
     * 
     * @return the modified timestamp in milliseconds
     */
    protected @Getter @Setter long modified = 0;

    /**
     * The CRC32 checksum value as a lowercase hexadecimal string.
     *
     * @param crc the CRC32 string to set
     * 
     * @return the CRC32 string
     */
    protected @Getter @Setter String crc = null;

    /**
     * The SHA-1 checksum value as a lowercase hexadecimal string.
     *
     * @param sha1 the SHA-1 string to set
     * 
     * @return the SHA-1 string
     */
    protected @Getter @Setter String sha1 = null;

    /**
     * The MD5 checksum value as a lowercase hexadecimal string.
     *
     * @param md5 the MD5 string to set
     * 
     * @return the MD5 string
     */
    protected @Getter @Setter String md5 = null;

    /**
     * The parent {@link Container} holding this entry.
     *
     * @return the parent container
     */
    protected @Getter Container parent = null;

    /**
     * The specific type of the entry (e.g., CHD).
     *
     * @param type the entry type to set
     * 
     * @return the entry type
     */
    protected @Getter @Setter Type type = Type.UNK;

    /**
     * Entry type definition.
     */
    public enum Type {
        /**
         * Unknown entry type (standard rom file).
         */
        UNK,
        /**
         * MAME CHD disk format type.
         */
        CHD
    }

    /**
     * Constructs an entry based on a file path.
     *
     * @param file the physical file path string (with relative path)
     * @param relfile the relative version of the file path to present to the user
     */
    public Entry(final String file, final String relfile) {
        this.file = file;
        this.relfile = relfile;
        final String ext = FilenameUtils.getExtension(file);
        if ("chd".equalsIgnoreCase(ext))
            type = Type.CHD;
    }

    /**
     * Constructs an entry based on a file path and its attributes.
     *
     * @param file the physical file path string (with relative path)
     * @param relfile the relative version of the file path to present to the user
     * @param attr the physical file attributes
     */
    public Entry(final String file, final String relfile, final BasicFileAttributes attr) {
        this(file, relfile);
        size = attr.size();
        modified = attr.lastModifiedTime().toMillis();
    }

    /**
     * Constructs an entry based on a file path, size, and modified date.
     *
     * @param file the physical file path string (with relative path)
     * @param relfile the relative version of the file path to present to the user
     * @param size the entry size in bytes
     * @param modified the last modified timestamp in milliseconds
     */
    public Entry(final String file, final String relfile, final long size, final long modified) {
        this(file, relfile);
        this.size = size;
        this.modified = modified;
    }

    /**
     * Renames the entry path names.
     *
     * @param file the new physical file path string
     * @param relfile the new relative file path string
     */
    public void rename(final String file, final String relfile) {
        this.file = file;
        this.relfile = relfile;
    }

    /**
     * Retrieves the relative version of the file name, or fallback to absolute if not defined.
     *
     * @return the relative file path string
     */
    public String getRelFile() {
        return relfile != null ? relfile : file;
    }

    /**
     * Retrieves the physical file path.
     *
     * @return the physical file path string
     */
    public String getFile() {
        return file;
    }

    /**
     * Cached value of the entry's normalized name.
     */
    private String cachedName = null;

    /**
     * Retrieves the relativized (against its parent) and normalized name (according to parent or file type).
     *
     * @return the normalized name string
     */
    public String getName() {
        if (cachedName == null) {
            final var path = Paths.get(file);
            if (parent.getType() == Container.Type.DIR) {
                cachedName = parent.getFile().toPath().relativize(path).toString().replace('\\', '/');
                return cachedName;
            }
            if (type == Type.CHD) {
                final var fileName = path.getFileName();
                if (fileName == null)
                    return null;
                cachedName = fileName.toString();
                return cachedName;
            }
            cachedName = path.subpath(0, path.getNameCount()).toString().replace('\\', '/');
            return cachedName;
        }
        return cachedName;
    }

    /**
     * Indicates whether some other object is "equal to" this entry. Supports comparison against other {@link Entry} objects, or
     * domain {@link Rom} / {@link Disk} / {@link Sample} entities.
     *
     * @param obj the reference object to compare with
     * 
     * @return {@code true} if this entry matches the properties of the compared object, {@code false} otherwise
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof Entry)
            return equalsEntry(obj);
        if (obj instanceof Rom)
            return equalsRom(obj);
        if (obj instanceof Disk)
            return equalsDisk(obj);
        if (obj instanceof Sample)
            return equalsSample(obj);
        return super.equals(obj);
    }

    /**
     * Compares this entry with a sample audio entity based on name comparison.
     *
     * @param obj the reference sample object
     * 
     * @return {@code true} if names match, {@code false} otherwise
     */
    protected boolean equalsSample(final Object obj) {
        return ((Sample) obj).getNormalizedName().equals(this.getName());
    }

    /**
     * Compares this entry with a disk entity based on SHA-1 or MD5 hashes.
     *
     * @param obj the reference disk object
     * 
     * @return {@code true} if hashes match, {@code false} otherwise
     */
    protected boolean equalsDisk(final Object obj) {
        if (((Disk) obj).sha1 != null && sha1 != null)
            return ((Disk) obj).sha1.equals(sha1);
        if (((Disk) obj).md5 != null && md5 != null)
            return ((Disk) obj).md5.equals(md5);
        return super.equals(obj);
    }

    /**
     * Compares this entry with a rom entity based on SHA-1, MD5, or CRC32 and size.
     *
     * @param obj the reference rom object
     * 
     * @return {@code true} if hashes match, {@code false} otherwise
     */
    protected boolean equalsRom(final Object obj) {
        if (((Rom) obj).sha1 != null && sha1 != null)
            return ((Rom) obj).sha1.equals(sha1);
        if (((Rom) obj).md5 != null && md5 != null)
            return ((Rom) obj).md5.equals(md5);
        if (((Rom) obj).crc != null && crc != null)
            return ((Rom) obj).crc.equals(crc) && ((Rom) obj).size == size;
        return super.equals(obj);
    }

    /**
     * Compares this entry with another entry based on SHA-1, MD5, CRC32 and size, or modified timestamp and size.
     *
     * @param obj the reference entry object
     * 
     * @return {@code true} if entries represent the same physical entity, {@code false} otherwise
     */
    protected boolean equalsEntry(final Object obj) {
        if (((Entry) obj).sha1 != null && sha1 != null)
            return ((Entry) obj).sha1.equals(sha1);
        if (((Entry) obj).md5 != null && md5 != null)
            return ((Entry) obj).md5.equals(md5);
        if (((Entry) obj).crc != null && crc != null)
            return ((Entry) obj).crc.equals(crc) && ((Entry) obj).size == size;
        if (((Entry) obj).modified != 0 && modified != 0)
            return ((Entry) obj).modified == modified && ((Entry) obj).size == size;
        return super.equals(obj);
    }

    /**
     * Returns a hash code value for the entry.
     *
     * @return a hash code value for this entry
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * Returns a string representation of this entry.
     *
     * @return a formatted string in the format "parent::relfile"
     */
    @Override
    public String toString() {
        return parent.getRelFile() + "::" + getRelFile(); //$NON-NLS-1$
    }
}
