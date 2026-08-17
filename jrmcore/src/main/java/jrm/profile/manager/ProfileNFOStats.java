/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.manager;

import java.io.Serializable;
import java.time.Instant;

import lombok.Data;

/**
 * Contains comprehensive statistics and audit tracking metadata for a ROM profile. Stores information regarding owned versus total
 * counts of sets, ROMs, and disks, as well as timestamps of profile lifecycle events like creation, scanning, and fixing. Fully
 * supports compliant and custom manual serialization.
 * 
 * @author optyfr
 */
public final @Data class ProfileNFOStats implements Serializable {
    /**
     * Serial version UID for maintaining serialization compatibility across releases.
     */
    private static final long serialVersionUID = 3L;

    /**
     * The MAME or metadata catalog database version string.
     * 
     * @param version the catalog database version to set
     * 
     * @return the catalog database version
     */
    private String version = null;

    /**
     * The total count of game sets owned in the user's collection.
     * 
     * @param haveSets the count of owned game sets to set
     * 
     * @return the count of owned game sets
     */
    private Long haveSets = null;

    /**
     * The total count of game sets defined in the metadata profile.
     * 
     * @param totalSets the total count of defined game sets to set
     * 
     * @return the total count of defined game sets
     */
    private Long totalSets = null;

    /**
     * The total count of ROM files owned in the user's collection.
     * 
     * @param haveRoms the count of owned ROMs to set
     * 
     * @return the count of owned ROMs
     */
    private Long haveRoms = null;

    /**
     * The total count of ROM files defined in the metadata profile.
     * 
     * @param totalRoms the total count of defined ROMs to set
     * 
     * @return the total count of defined ROMs
     */
    private Long totalRoms = null;

    /**
     * The total count of CHD or disk files owned in the user's collection.
     * 
     * @param haveDisks the count of owned disks to set
     * 
     * @return the count of owned disks
     */
    private Long haveDisks = null;

    /**
     * The total count of CHD or disk files defined in the metadata profile.
     * 
     * @param totalDisks the total count of defined disks to set
     * 
     * @return the total count of defined disks
     */
    private Long totalDisks = null;

    /**
     * The timestamp of when this profile NFO metadata was originally created.
     * 
     * @param created the creation instant to set
     * 
     * @return the creation instant
     */
    private Instant created = null;

    /**
     * The timestamp of the last complete directory or filesystem scan.
     * 
     * @param scanned the last scan instant to set
     * 
     * @return the last scan instant
     */
    private Instant scanned = null;

    /**
     * The timestamp of when the last repair or repair-fix operation occurred.
     * 
     * @param fixed the last fix instant to set
     * 
     * @return the last fix instant
     */
    private Instant fixed = null;

    /**
     * Default zero-argument constructor initializing an empty profile statistics container.
     */
    public ProfileNFOStats() {
        // Default constructor
    }

    /**
     * Resets all statistics values, clearing counts and timestamps and setting the profile creation timestamp to the current system
     * time.
     */
    public void reset() {
        version = null;
        haveSets = null;
        totalSets = null;
        haveRoms = null;
        totalRoms = null;
        haveDisks = null;
        totalDisks = null;
        created = Instant.now();
        scanned = null;
        fixed = null;
    }

    /**
     * Nested immutable record-like structure pairing the number of owned items ("have") with the total expected items ("total").
     * 
     * @author optyfr
     */
    public static @Data class HaveNTotal {
        /**
         * The count of successfully acquired/owned physical elements.
         * 
         * @param have the count of owned items
         * 
         * @return the count of owned items
         */
        private final Long have;

        /**
         * The total target count of expected elements in the profile.
         * 
         * @param total the total count of items
         * 
         * @return the total count of items
         */
        private final Long total;
    }

    /**
     * Returns the game sets completion statistics container.
     * 
     * @return a {@link HaveNTotal} instance representing owned sets vs total sets
     */
    public HaveNTotal getSets() {
        return new HaveNTotal(haveSets, totalSets);
    }

    /**
     * Returns the ROM files completion statistics container.
     * 
     * @return a {@link HaveNTotal} instance representing owned ROMs vs total ROMs
     */
    public HaveNTotal getRoms() {
        return new HaveNTotal(haveRoms, totalRoms);
    }

    /**
     * Returns the CHD/disk files completion statistics container.
     * 
     * @return a {@link HaveNTotal} instance representing owned disks vs total disks
     */
    public HaveNTotal getDisks() {
        return new HaveNTotal(haveDisks, totalDisks);
    }

}
