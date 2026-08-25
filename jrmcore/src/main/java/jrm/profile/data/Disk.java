/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.data;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import jrm.misc.ProfileSettingsEnum;
import jrm.xml.EnhancedXMLStreamWriter;
import jrm.xml.SimpleAttribute;
import lombok.Getter;
import lombok.Setter;

/**
 * Describes a disk entity (such as a MAME CHD) associated with a machine or software. Tracks storage flags, placement, optional
 * flags, and status resolutions.
 *
 * @author optyfr
 */
public class Disk extends Entity {
    private static final String MERGE_STR = "merge";
    private static final String WRITEABLE_STR = "writeable";
    private static final String STATUS_STR = "status";
    private static final String SHA1_STR = "sha1";
    private static final String NAME_STR = "name";
    private static final String DISK_STR = "disk";

    /**
     * Is the disk writable? Defaults to {@code false}.
     *
     * @param writeable {@code true} if the disk is writable, {@code false} otherwise
     * 
     * @return {@code true} if the disk is writable, {@code false} otherwise
     */
    protected @Getter @Setter boolean writeable = false;

    /**
     * What's the disk index? Defaults to {@code null}.
     *
     * @param index the disk index integer
     * 
     * @return the disk index integer
     */
    protected @Getter @Setter Integer index = null;

    /**
     * Is the disk optional? Defaults to {@code false}.
     *
     * @param optional {@code true} if the disk is optional, {@code false} otherwise
     * 
     * @return {@code true} if the disk is optional, {@code false} otherwise
     */
    protected @Getter @Setter boolean optional = false;

    /**
     * What's the disk region?
     *
     * @param region the disk region string
     * 
     * @return the disk region string
     */
    protected @Getter @Setter String region = null;

    /**
     * Constructor for Disk.
     *
     * @param parent the {@link Anyware} parent containing the disk
     */
    public Disk(final Anyware parent) {
        super(parent);
    }

    /**
     * Retrieves the forged disk file name, applying merge mode naming conventions if applicable.
     *
     * @return the disk file name with ".chd" extension
     */
    @Override
    public String getName() {
        if (getParent().profile.getSettings() != null && getParent().profile.getSettings().getMergeMode().isMerge()) {
            if (merge == null) {
                if (isCollisionMode(true) && getParent().isClone()) {
                    return parent.name + "/" + name + ".chd";
                }
            } else if (Boolean.FALSE.equals(parent.getProfile().getProperty(ProfileSettingsEnum.ignore_merge_name_disks, Boolean.class)))
                return merge + ".chd";
        }
        return name + ".chd";
    }

    /**
     * Indicates whether some other object is "equal to" this one. Disks are compared first using their SHA-1 checksums, then MD5
     * checksums, and falling back to super equality.
     *
     * @param obj the reference object with which to compare
     * 
     * @return {@code true} if this disk is the same as the obj argument; {@code false} otherwise
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof Disk dsk) {
            if (dsk.sha1 != null && sha1 != null)
                return dsk.sha1.equals(sha1);
            if (dsk.md5 != null && md5 != null)
                return dsk.md5.equals(md5);
        }
        return super.equals(obj);
    }

    /**
     * Returns a hash code value for the disk.
     *
     * @return a hash code value for this disk
     */
    @Override
    public int hashCode() {
        if (sha1 != null)
            return sha1.hashCode();
        if (md5 != null)
            return md5.hashCode();
        return super.hashCode();
    }

    /**
     * Retrieves the disk hash value (SHA-1 or MD5) as a string, or the forged disk name if no hash is available.
     *
     * @return the hash value as a string, or name as a fallback
     */
    public String hashString() {
        if (sha1 != null)
            return sha1;
        if (md5 != null)
            return md5;
        return getName();
    }

    /**
     * Converts a list of disks into a map of disks indexed by their normalized names.
     *
     * @param disks the list of disks to convert
     * 
     * @return a map of disks with normalized names as keys
     */
    public static Map<String, Disk> getDisksByName(final List<Disk> disks) {
        return disks.stream().collect(Collectors.toMap(Disk::getNormalizedName, Function.identity(), (n, _) -> n));
    }

    /**
     * Tries to find the disk status across parent clones and systems (valid in merged mode).
     * Walks the parent chain iteratively with a depth cap and cycle detection.
     *
     * @param parent the parent machine or software
     * @param disk the disk to search for
     * 
     * @return the matched {@link EntityStatus} or {@code null} if not found
     */
    private static EntityStatus findDiskStatus(final Anyware parent, final Disk disk) {
        Anyware current = parent;
        final var seen = new IdentityHashMap<Anyware, Boolean>();
        for (var depth = 0; depth < Anyware.MAX_PARENT_DEPTH; depth++) {
            if (seen.put(current, Boolean.TRUE) != null)
                return null;
            if (current.parent == null) {
                if (current.isRomOf() && disk.merge != null)
                    return EntityStatus.OK;
                return null;
            }
            if (current.profile.getSettings().getMergeMode().isMerge()) {
                final var status = findDiskStatusInClones(current, disk);
                if (status != null)
                    return status;
            }
            for (final Disk d : current.getParent().getDisks()) {
                if (d != disk && disk.equals(d))
                    return d.getStatus();
            }
            if (current.parent.parent == null)
                return null;
            current = current.getParent();
        }
        return null;
    }

    /**
     * Tries to find the disk status specifically inside sibling clone sets.
     *
     * @param parent the parent machine or software
     * @param disk the disk to search for
     * 
     * @return the matched {@link EntityStatus} or {@code null} if not found
     */
    private static EntityStatus findDiskStatusInClones(final Anyware parent, final Disk disk) {
        for (final Anyware clone : parent.getParent().clones.values()) {
            if (clone != parent) {
                for (final Disk d : clone.getDisks()) {
                    if (d.ownStatus != EntityStatus.UNKNOWN && disk.equals(d))
                        return d.ownStatus;
                }
            }
        }
        return null;
    }

    /**
     * Retrieves the status of this disk, falling back to parent clone resolution if unknown.
     *
     * @return the status of the disk
     */
    @Override
    public EntityStatus getStatus() {
        if (dumpStatus == Status.nodump)
            return EntityStatus.OK;
        if (ownStatus == EntityStatus.UNKNOWN) {
            final EntityStatus status = findDiskStatus(getParent(), this);
            if (status != null)
                return status;
        }
        return ownStatus;
    }

    /**
     * Exports the disk metadata into an XML format.
     *
     * @param writer the enhanced XML stream writer to output to
     * @param is_mame {@code true} if exporting in MAME format, {@code false} for logiqx format
     * 
     * @throws XMLStreamException if an error occurs during XML writing
     */
    public void export(final EnhancedXMLStreamWriter writer, final boolean is_mame) throws XMLStreamException {
        if (parent instanceof Software) {
            writer.writeElement(DISK_STR,
                    new SimpleAttribute(NAME_STR, name),
                    new SimpleAttribute(SHA1_STR, sha1),
                    new SimpleAttribute(STATUS_STR, dumpStatus.getXML(is_mame)),
                    new SimpleAttribute(WRITEABLE_STR, writeable ? "yes" : null)
            );
        } else if (is_mame) {
            writer.writeElement(DISK_STR,
                    new SimpleAttribute(NAME_STR, name),
                    new SimpleAttribute(SHA1_STR, sha1),
                    new SimpleAttribute(MERGE_STR, merge),
                    new SimpleAttribute(STATUS_STR, dumpStatus.getXML(is_mame)),
                    new SimpleAttribute("optional", optional),
                    new SimpleAttribute("region", region),
                    new SimpleAttribute("writable", writeable ? "yes" : null),
                    new SimpleAttribute("index", index)
            );
        } else {
            writer.writeElement(DISK_STR,
                    new SimpleAttribute(NAME_STR, name),
                    new SimpleAttribute(SHA1_STR, sha1),
                    new SimpleAttribute("md5", md5),
                    new SimpleAttribute(MERGE_STR, merge),
                    new SimpleAttribute(STATUS_STR, dumpStatus.getXML(is_mame))
            );
        }
    }
}
