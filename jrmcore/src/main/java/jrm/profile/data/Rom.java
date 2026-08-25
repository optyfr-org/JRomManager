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
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import jrm.misc.ProfileSettingsEnum;
import jrm.xml.EnhancedXMLStreamWriter;
import jrm.xml.SimpleAttribute;
import lombok.Getter;
import lombok.Setter;

/**
 * Rom entity definition representing a read-only memory chip file inside a retro-gaming system or software list. This class extends
 * {@link Entity} and encapsulates ROM-specific attributes like offsets, bios associations, and loading flags.
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public class Rom extends Entity {
    /**
     * XML attribute name for memory offset.
     */
    private static final String OFFSET_STR = "offset";

    /**
     * XML attribute name for ROM name.
     */
    private static final String NAME_STR = "name";

    /**
     * XML element name for ROM entries.
     */
    private static final String ROM_STR = "rom";

    /**
     * The name of the BIOS, otherwise null if the ROM is not associated with a BIOS.
     * 
     * @param bios the BIOS name to set
     * 
     * @return the BIOS name
     */
    protected @Getter @Setter String bios = null;

    /**
     * The in-memory offset, kept for XML export.
     * 
     * @param offset the in-memory offset to set
     * 
     * @return the in-memory offset
     */
    protected @Getter @Setter Long offset = null;

    /**
     * The in-memory load flag, kept for XML export.
     * 
     * @param loadflag the load flag to set
     * 
     * @return the load flag
     */
    protected @Getter @Setter LoadFlag loadflag = null;

    /**
     * The value to fill according to the load flag (software ROM only, kept for XML export).
     * 
     * @param value the fill value to set
     * 
     * @return the fill value
     */
    protected @Getter @Setter String value = null;

    /**
     * Indicates whether this ROM is optional.
     * 
     * @param optional true if the ROM is optional, false otherwise
     * 
     * @return true if the ROM is optional, false otherwise
     */
    protected @Getter @Setter boolean optional = false;

    /**
     * The memory region where this ROM belongs, kept for XML export.
     * 
     * @param region the memory region to set
     * 
     * @return the memory region
     */
    protected @Getter @Setter String region = null;

    /**
     * The dump date of this ROM, kept for XML export.
     * 
     * @param date the dump date to set
     * 
     * @return the dump date
     */
    protected @Getter @Setter String date = null;

    /**
     * Possible Load Flags definitions. Definitions are uppercase to avoid java keyword collisions (e.g., continue), but are
     * serialized as lowercase strings.
     * 
     * @author optyfr
     * 
     * @since 1.0
     */
    public enum LoadFlag {
        /**
         * Load 16-bit byte.
         */
        LOAD16_BYTE,

        /**
         * Load 16-bit word.
         */
        LOAD16_WORD,

        /**
         * Load 16-bit word with swapped bytes.
         */
        LOAD16_WORD_SWAP,

        /**
         * Load 32-bit byte.
         */
        LOAD32_BYTE,

        /**
         * Load 32-bit word.
         */
        LOAD32_WORD,

        /**
         * Load 32-bit word with swapped bytes.
         */
        LOAD32_WORD_SWAP,

        /**
         * Load 32-bit double-word.
         */
        LOAD32_DWORD,

        /**
         * Load 64-bit word.
         */
        LOAD64_WORD,

        /**
         * Load 64-bit word with swapped bytes.
         */
        LOAD64_WORD_SWAP,

        /**
         * Reload action.
         */
        RELOAD,

        /**
         * Fill action.
         */
        FILL,

        /**
         * Continue action.
         */
        CONTINUE,

        /**
         * Reload plain action.
         */
        RELOAD_PLAIN,

        /**
         * Ignore action.
         */
        IGNORE;

        @Override
        public String toString() {
            return name().toLowerCase();
        }

        /**
         * Resolves the given string value into its matching LoadFlag.
         * 
         * @param value the string value to match
         * 
         * @return the matching LoadFlag enum element
         * 
         * @throws IllegalArgumentException if no match is found
         */
        public static LoadFlag getEnum(final String value) {
            for (final LoadFlag v : LoadFlag.values())
                if (v.name().equalsIgnoreCase(value))
                    return v;
            throw new IllegalArgumentException();
        }
    }

    /**
     * Constructor for Rom with its associated parent Anyware.
     * 
     * @param parent the non-null parent system or game
     */
    public Rom(final Anyware parent) {
        super(parent);
    }

    /**
     * Get the ROM name. Includes the parent name if using merge mode and the ROM represents a clone colliding with its parent.
     * 
     * @return the name of the ROM
     */
    @Override
    public String getName() {
        if (parent.getProfile().getSettings() != null && parent.getProfile().getSettings().getMergeMode().isMerge()) {
            if (merge == null) {
                if (isCollisionMode(false) && getParent().isClone()) {
                    return parent.name + "/" + name;
                }
            } else if (Boolean.FALSE.equals(parent.getProfile().getProperty(ProfileSettingsEnum.ignore_merge_name_roms, Boolean.class)))
                return merge;
        }
        return name;
    }

    /**
     * Get the full ROM name. Includes the parent name if merge mode is enabled, regardless of collisions.
     * 
     * @return the full hierarchical path/name of the ROM
     */
    public String getFullName() {
        if (getParent().profile.getSettings() != null && getParent().profile.getSettings().getMergeMode().isMerge()) {
            if (merge != null && !parent.getProfile().getProperty(ProfileSettingsEnum.ignore_merge_name_roms, Boolean.class))
                return parent.name + "/" + merge;
            return parent.name + "/" + name;
        }
        return name;
    }

    /**
     * Evaluates whether this ROM is equivalent to another object based on checksums (SHA1, MD5, or CRC and size). Falls back to
     * reference equality checks.
     * 
     * @param obj the reference object to compare with
     * 
     * @return true if equivalent, false otherwise
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof Rom rom) {
            if (rom.sha1 != null && sha1 != null)
                return rom.sha1.equals(sha1);
            if (rom.md5 != null && md5 != null)
                return rom.md5.equals(md5);
            if (rom.crc != null && crc != null)
                return rom.crc.equals(crc) && rom.size == size;
        }
        return super.equals(obj);
    }

    /**
     * Returns the hash code based on the best available checksum.
     * 
     * @return the hash code
     */
    @Override
    public int hashCode() {
        if (sha1 != null)
            return sha1.hashCode();
        if (md5 != null)
            return md5.hashCode();
        if (crc != null)
            return crc.hashCode();
        return super.hashCode();
    }

    /**
     * Get the latest available hash string value for this ROM.
     * 
     * @return the hash string (SHA1, MD5, or CRC) or name if no checksums are available
     */
    public String hashString() {
        if (sha1 != null)
            return sha1;
        if (md5 != null)
            return md5;
        if (crc != null)
            return crc;
        return getName();
    }

    /**
     * Converts a list of ROMs into a map indexed by their normalized names.
     * 
     * @param roms the list of ROMs to map
     * 
     * @return a map of ROMs by name
     */
    public static Map<String, Rom> getRomsByName(final List<Rom> roms) {
        return roms.stream().collect(Collectors.toMap(Rom::getNormalizedName, r -> r, (n, _) -> n));
    }

    /**
     * Searches for a matching ROM in the parent's own ROM list and returns its status if found and known.
     * 
     * @param parent the Anyware parent of the ROM
     * @param rom the Rom whose status is to be resolved
     * 
     * @return the resolved EntityStatus, or null if no known-status match is found
     */
    private EntityStatus findMatchingRomStatus(final Anyware parent, final Rom rom) {
        for (final Rom r : parent.getRoms()) {
            if (rom != r && rom.equals(r)) {
                if (r.ownStatus != EntityStatus.UNKNOWN)
                    return r.ownStatus;
                break;
            }
        }
        return null;
    }

    /**
     * Tries to find the ROM status across parents and also in clones (if in merged mode).
     * Walks the parent chain iteratively with a depth cap and cycle detection.
     * 
     * @param parent the Anyware parent of the ROM
     * @param rom the Rom whose status is to be resolved
     * 
     * @return the resolved EntityStatus, or null if not found
     */
    private EntityStatus findRomStatus(final Anyware parent, final Rom rom) {
        Anyware current = parent;
        final var seen = new IdentityHashMap<Anyware, Boolean>();
        for (var depth = 0; depth < Anyware.MAX_PARENT_DEPTH; depth++) {
            if (seen.put(current, Boolean.TRUE) != null)
                return null;
            final EntityStatus matchStatus = findMatchingRomStatus(current, rom);
            if (matchStatus != null)
                return matchStatus;
            if (current.parent == null) {
                if (current.isRomOf() && rom.merge != null)
                    return EntityStatus.OK;
                return null;
            }
            final var status = findRomStatusMerge(current, rom);
            if (status != null)
                return status;
            for (final Rom r : current.getParent().getRoms()) {
                if (r != rom && rom.equals(r))
                    return r.getStatus();
            }
            if (current.parent.parent == null)
                return null;
            current = current.getParent();
        }
        return null;
    }

    /**
     * Resolves ROM status across clones in merged mode.
     * 
     * @param parent the parent Anyware system
     * @param rom the Rom whose status is to be resolved
     * 
     * @return the clone status or null if not in merge mode or not resolved
     */
    private EntityStatus findRomStatusMerge(final Anyware parent, final Rom rom) {
        if (!getParent().profile.getSettings().getMergeMode().isMerge())
            return null;
        return parent.getParent().getCloneRomStatus(rom);
    }

    /**
     * Returns the final status of this ROM, resolving it dynamically from parent/clones if necessary.
     * 
     * @return the resolved EntityStatus
     */
    @Override
    public EntityStatus getStatus() {
        if (name.isEmpty())
            return EntityStatus.OK;
        if (dumpStatus == Status.nodump)
            return EntityStatus.OK;
        if (ownStatus == EntityStatus.UNKNOWN) {
            final EntityStatus status = findRomStatus(getParent(), this);
            if (status != null)
                return status;
        }
        return ownStatus;
    }

    /**
     * Export the ROM definition as a XML element inside a DAT file structure.
     * 
     * @param writer the EnhancedXMLStreamWriter used to write the XML output
     * @param is_mame true if writing MAME-compatible XML format, false for Logiqx format
     * 
     * @throws XMLStreamException if an XML stream error occurs
     */
    public void export(final EnhancedXMLStreamWriter writer, final boolean is_mame) throws XMLStreamException {
        if (parent instanceof Software) {
            writer.writeElement(ROM_STR,
                    new SimpleAttribute(NAME_STR, name),
                    new SimpleAttribute(SIZE_STR, size),
                    new SimpleAttribute(CRC_STR, crc),
                    new SimpleAttribute(SHA1_STR, sha1),
                    new SimpleAttribute(MERGE_STR, merge),
                    new SimpleAttribute(STATUS_STR, dumpStatus.getXML(is_mame)),
                    new SimpleAttribute("value", value),
                    new SimpleAttribute("loadflag", loadflag),
                    new SimpleAttribute(OFFSET_STR, offset == null ? null : ("0x" + Long.toHexString(offset)))
            );
        } else if (is_mame) {
            writer.writeElement(ROM_STR,
                    new SimpleAttribute(NAME_STR, name),
                    new SimpleAttribute("bios", bios),
                    new SimpleAttribute(SIZE_STR, size),
                    new SimpleAttribute(CRC_STR, crc),
                    new SimpleAttribute(SHA1_STR, sha1),
                    new SimpleAttribute(MERGE_STR, merge),
                    new SimpleAttribute(STATUS_STR, dumpStatus.getXML(is_mame)),
                    new SimpleAttribute("optional", optional ? "yes" : null),
                    new SimpleAttribute("region", region),
                    new SimpleAttribute(OFFSET_STR, offset == null ? null : ("0x" + Long.toHexString(offset)))
            );
        } else {
            writer.writeElement(ROM_STR,
                    new SimpleAttribute(NAME_STR, name),
                    new SimpleAttribute(SIZE_STR, size),
                    new SimpleAttribute(CRC_STR, crc),
                    new SimpleAttribute(SHA1_STR, sha1),
                    new SimpleAttribute(MD5_STR, md5),
                    new SimpleAttribute(MERGE_STR, merge),
                    new SimpleAttribute(STATUS_STR, dumpStatus.getXML(is_mame)),
                    new SimpleAttribute("date", date)
            );
        }
    }

}
