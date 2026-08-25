/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.data;

import jrm.misc.ProfileSettings;
import jrm.profile.scan.options.HashCollisionOptions;
import lombok.Getter;
import lombok.Setter;

/**
 * The abstract common base class for {@link Rom} and {@link Disk}. This class extends {@link EntityBase} and manages standard retro
 * game files properties, such as file sizes, CRC, MD5, and SHA-1 checksums, dump status, and merge behaviors.
 *
 * @author optyfr
 */
public abstract class Entity extends EntityBase {
    /** XML attribute name for dump status. */
    protected static final String STATUS_STR = "status";
    /** XML attribute name for merge target. */
    protected static final String MERGE_STR = "merge";
    /** XML attribute name for MD5 checksum. */
    protected static final String MD5_STR = "md5";
    /** XML attribute name for SHA-1 checksum. */
    protected static final String SHA1_STR = "sha1";
    /** XML attribute name for CRC32 checksum. */
    protected static final String CRC_STR = "crc";
    /** XML attribute name for file size. */
    protected static final String SIZE_STR = "size";

    /** Serial version UID for serialization compatibility. */
    private static final long serialVersionUID = 1L;

    /**
     * The file size in bytes, defaults to 0 (always 0 for disks).
     *
     * @param size the file size in bytes to set
     * 
     * @return the file size in bytes
     */
    protected @Getter @Setter long size = 0;

    /**
     * The CRC32 value as a lowercase hexadecimal {@link String}, or null if none is defined (e.g. for disks).
     *
     * @param crc the CRC32 hexadecimal string to set
     * 
     * @return the CRC32 hexadecimal string
     */
    protected @Getter @Setter String crc = null;

    /**
     * The SHA-1 checksum value as a lowercase hexadecimal {@link String}, or null if none is defined.
     *
     * @param sha1 the SHA-1 hexadecimal string to set
     * 
     * @return the SHA-1 hexadecimal string
     */
    protected @Getter @Setter String sha1 = null;

    /**
     * The MD5 checksum value as a lowercase hexadecimal {@link String}, or null if none is defined.
     *
     * @param md5 the MD5 hexadecimal string to set
     * 
     * @return the MD5 hexadecimal string
     */
    protected @Getter @Setter String md5 = null;

    /**
     * The merge name target of this entity, or null if no explicit merge is defined.
     *
     * @param merge the merge target name to set
     * 
     * @return the merge target name
     */
    protected @Getter @Setter String merge = null;

    /**
     * The dump status of this entity, defaulting to {@link Status#good} when not defined.
     *
     * @param dumpStatus the dump status to set
     * 
     * @return the dump status of the entity
     */
    protected @Getter @Setter Status dumpStatus = Status.good;

    /**
     * Enumeration defining the dump status of a game rom or disk.
     */
    public enum Status {
        /**
         * Bad dump (faulty, incomplete, or corrupted dump).
         */
        baddump, // NOSONAR
        /**
         * No dump is known to exist yet.
         */
        nodump, // NOSONAR
        /**
         * The dump is verified as good.
         */
        good, // NOSONAR
        /**
         * The dump is good and has been verified (applicable only in logiqx formats).
         */
        verified; // NOSONAR

        /**
         * Maps the status value for XML output according to the requested export format.
         *
         * @param is_mame {@code true} if the export format is MAME XML, {@code false} for other formats
         * 
         * @return the mapped {@link Status}, or {@code null} if good / default status should be skipped in the XML output
         */
        public Status getXML(final boolean is_mame) {
            return (Status.good == this || (is_mame && Status.verified == this)) ? null : this;
        }
    }

    /**
     * Collision mode status.
     */
    private transient boolean collision = false;

    /**
     * Single supported constructor for Entity subclasses.
     *
     * @param parent the required {@link Anyware} parent set (Machine or Software)
     */
    protected Entity(final Anyware parent) {
        super(parent);
    }

    /**
     * Enables collision mode for this entity and propagates it to parents or clone families depending on the configured
     * {@link ProfileSettings#getHashCollisionMode()}.
     */
    public void setCollisionMode() {
        if (getParent().profile.getSettings().getHashCollisionMode() == HashCollisionOptions.SINGLECLONE)
            getParent().setCollisionMode(false);
        else if (getParent().profile.getSettings().getHashCollisionMode() == HashCollisionOptions.ALLCLONES)
            getParent().setCollisionMode(true);
        collision = true;
    }

    /**
     * Checks if collision mode is currently active for this entity.
     *
     * @param dumber {@code true} to assume collision mode for very dumb strategies, {@code false} otherwise
     * 
     * @return {@code true} if collision mode is active, {@code false} otherwise
     */
    public boolean isCollisionMode(boolean dumber) {
        if (getParent().profile.getSettings().getHashCollisionMode() == HashCollisionOptions.SINGLECLONE)
            return getParent().isCollisionMode();
        else if (getParent().profile.getSettings().getHashCollisionMode() == HashCollisionOptions.ALLCLONES)
            return getParent().isCollisionMode();
        else if (dumber) {
            if (getParent().profile.getSettings().getHashCollisionMode() == HashCollisionOptions.DUMBER)
                return true;
        } else {
            if (getParent().profile.getSettings().getHashCollisionMode() == HashCollisionOptions.HALFDUMB
                    || getParent().profile.getSettings().getHashCollisionMode() == HashCollisionOptions.DUMB
                    || getParent().profile.getSettings().getHashCollisionMode() == HashCollisionOptions.DUMBER)
                return true;
        }
        return collision;
    }

    /**
     * Resets both the {@link #collision} status flag and the internal {@link EntityBase#ownStatus}.
     */
    void resetCollisionMode() {
        collision = false;
        ownStatus = EntityStatus.UNKNOWN;
    }

    /**
     * Retrieves the Anyware parent of this entity.
     *
     * @return the parent {@link Anyware}
     */
    @Override
    public Anyware getParent() {
        return getParent(Anyware.class);
    }

}
