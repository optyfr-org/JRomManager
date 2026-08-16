/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.data;

import java.io.File;
import java.io.Serializable;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;

import jtrrntzip.TrrntZipStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * The base class representing a group of entities on the filesystem, such as a compressed archive or a directory. It manages
 * entries (individual files) and keeps track of scanning states, torrentzip checks, and metadata.
 *
 * @author optyfr
 */
public class Container implements Serializable, Comparable<Container> {
    /**
     * File representing the physical location of the container.
     */
    private final File file;

    /**
     * Relative file path representing the relative location of the container inside the workspace.
     */
    private final File relfile;

    /**
     * Last modified date of the container in milliseconds since the epoch.
     *
     * @return the last modified time of the container in milliseconds
     */
    private @Getter long modified = 0L;

    /**
     * File size in bytes (or 0 if the container is a folder directory).
     *
     * @return the size of the container in bytes
     */
    private @Getter long size = 0L;

    /**
     * Internal map storing container entry objects indexed by their respective file names.
     *
     * @return the map of entry objects indexed by their file names
     */
    private final @Getter Map<String, Entry> entriesByFName = new HashMap<>();

    /**
     * Flag for scanning removal indicating whether this container is up-to-date with the filesystem.
     *
     * @param up2date {@code true} if up-to-date, {@code false} otherwise
     * 
     * @return {@code true} if up-to-date, {@code false} otherwise
     */
    private transient @Getter @Setter boolean up2date = false;

    /**
     * Scan load status indicating how deep the container scan was performed:
     * <ul>
     * <li>0 - Not scanned</li>
     * <li>1 - Quick scanned (only CRCs are resolved)</li>
     * <li>2 - Deep scanned (SHA-1 and MD5 hashes resolved for all entries)</li>
     * </ul>
     *
     * @param loaded the scan load status level (0, 1, or 2)
     * 
     * @return the scan load status level (0, 1, or 2)
     */
    private @Getter @Setter int loaded = 0;

    /**
     * Last time the archive was torrentzip checked (only valid for zip archives).
     *
     * @param lastTZipCheck the timestamp of the last torrentzip check in milliseconds
     * 
     * @return the timestamp of the last torrentzip check in milliseconds
     */
    private @Getter @Setter long lastTZipCheck = 0L;

    /**
     * Last torrentzip check status flags (only valid for zip archives).
     *
     * @param lastTZipStatus the set of torrentzip status flags
     * 
     * @return the set of torrentzip status flags
     */
    private @Getter @Setter Set<TrrntZipStatus> lastTZipStatus = EnumSet.noneOf(TrrntZipStatus.class);

    /**
     * Related {@link AnywareBase} set that matches this container.
     *
     * @param relAW the related AnywareBase set
     * 
     * @return the related AnywareBase set
     */
    protected transient @Getter @Setter AnywareBase relAW;

    /**
     * Enum representing the type of container.
     */
    public enum Type {
        /**
         * Unknown container type (cannot be determined).
         */
        UNK,
        /**
         * File system folder directory.
         */
        DIR,
        /**
         * ZIP compressed archive.
         */
        ZIP,
        /**
         * SevenZip (7z) compressed archive.
         */
        SEVENZIP,
        /**
         * RAR compressed archive.
         */
        RAR,
        /**
         * Dummy/Fake container used for virtual operations.
         */
        FAKE
    }

    /**
     * The matched type of this container.
     *
     * @return the container type
     */
    private @Getter Type type = Type.UNK;

    /**
     * Constructs a container where the related set is known.
     *
     * @param type the guessed type of the container
     * @param file the container {@link File}
     * @param relfile the relative version of the container {@link File}
     * @param m the corresponding {@link AnywareBase} set
     */
    protected Container(final Type type, final File file, final File relfile, final AnywareBase m) {
        this.type = type;
        this.file = file;
        this.relfile = relfile;
        this.relAW = m;
    }

    /**
     * Constructs a container file with no related set.
     *
     * @param type the guessed type of the container
     * @param file the container {@link File}
     * @param relfile the relative version of the container {@link File}
     * @param attr the file attributes (modified time and size are extracted)
     */
    protected Container(final Type type, final File file, final File relfile, final BasicFileAttributes attr) {
        this(type, file, relfile, (AnywareBase) null);
        modified = attr.lastModifiedTime().toMillis();
        if (type != Type.DIR)
            size = attr.size();
    }

    /**
     * Retrieves the relative file path of the container, falling back to the absolute file path if not defined.
     *
     * @return the relative {@link File}, or fallback to physical {@link File}
     */
    public File getRelFile() {
        return relfile != null ? relfile : file;
    }

    /**
     * Retrieves the physical absolute file path of the container.
     *
     * @return the physical {@link File}
     */
    public File getFile() {
        return file;
    }

    /**
     * Adds a listed entry by its file name to the container mapping. If an identical entry with matching modification date and size
     * is already present, it is returned and no insert takes place.
     *
     * @param e the {@link Entry} to add
     * 
     * @return the active or newly added entry
     */
    public Entry add(final Entry e) {
        Entry oldEntry;
        if (null != (oldEntry = entriesByFName.get(e.getFile())) && oldEntry.modified == e.modified && oldEntry.size == e.size)
            return oldEntry;
        entriesByFName.put(e.getFile(), e);
        e.parent = this;
        return e;
    }

    /**
     * Finds an existing {@link Entry} inside the container using the reference entry's name.
     *
     * @param e the {@link Entry} containing the file name to find
     * 
     * @return the matched {@link Entry} found, or {@code null} if not found
     */
    public Entry find(final Entry e) {
        return entriesByFName.get(e.getFile());
    }

    /**
     * Retrieves all entries currently registered in this container as a collection.
     *
     * @return a {@link Collection} of {@link Entry} objects
     */
    public Collection<Entry> getEntries() {
        return entriesByFName.values();
    }

    /**
     * Retrieves a map of entries indexed by their normalized string names.
     *
     * @return a {@link Map} of entries with their normalized names as keys
     */
    public Map<String, Entry> getEntriesByName() {
        return entriesByFName.values().stream().collect(Collectors.toMap(Entry::getName, Function.identity(), (n, _) -> n));
    }

    /**
     * Statistically evaluates and guesses the {@link Type} of a given file based on its extension.
     *
     * @param file the {@link File} to guess type of
     * 
     * @return the guessed {@link Type}
     */
    public static Type getType(final File file) {
        if (file == null || file.getName() == null)
            return Type.UNK;
        final String ext = FilenameUtils.getExtension(file.getName());
        switch (ext.toLowerCase()) {
            case "zip": //$NON-NLS-1$
                return Type.ZIP;
            case "7z": //$NON-NLS-1$
                return Type.SEVENZIP;
            case "rar": //$NON-NLS-1$
                if (file.getName().contains(".part") && !file.getName().endsWith(".part001.rar"))
                    return Type.UNK;
                return Type.RAR;
            default:
                break;
        }
        if (file.getName().endsWith(".7z.001"))
            return Type.SEVENZIP;
        return Type.UNK;
    }

    /**
     * Returns a string representation of this container.
     *
     * @return a string representing the container
     */
    @Override
    public String toString() {
        return "Container " + file; //$NON-NLS-1$
    }

    /**
     * Compares this container with another container based on their physical sizes.
     *
     * @param o the other container to compare with
     * 
     * @return a negative integer, zero, or a positive integer as this container size is less than, equal to, or greater than the
     *         specified container's size
     */
    @Override
    public int compareTo(Container o) {
        return Long.compare(this.size, o.size);
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     *
     * @param obj the reference object with which to compare
     * 
     * @return {@code true} if this object is the same as the obj argument; {@code false} otherwise
     */
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    /**
     * Returns a hash code value for the container.
     *
     * @return a hash code value for this container
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * Creates and returns a standard comparator comparing containers by size.
     *
     * @return a {@link Comparator} for Container sorting
     */
    public static Comparator<Container> comparator() {
        return Comparable::compareTo;
    }

    /**
     * Creates and returns a reverse comparator comparing containers by size in descending order.
     *
     * @return a reverse {@link Comparator} for Container sorting
     */
    public static Comparator<Container> rcomparator() {
        return (o1, o2) -> o2.compareTo(o1);
    }
}
