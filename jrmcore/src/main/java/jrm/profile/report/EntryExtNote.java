package jrm.profile.report;

import jrm.profile.data.EntityBase;
import jrm.profile.data.Entry;
import lombok.Getter;

/**
 * Abstract base class for report notes that correlate an expected metadata entity with a physical filesystem file entry.
 * <p>
 * This class provides standard accessors and formatting helpers to output details, name resolutions, and expected checksum matches
 * for specific entry types.
 *
 * @author optyfr
 * 
 * @since 1.0
 */
abstract class EntryExtNote extends EntryNote {
    private static final long serialVersionUID = 1L;

    /**
     * The physical file entry referenced by this note.
     *
     * @return the physical entry
     */
    final @Getter Entry entry;

    /**
     * Constructs a new EntryExtNote mapping an expected entity to a physical file entry.
     *
     * @param entity the expected entity base metadata
     * @param entry the physical file entry
     */
    protected EntryExtNote(EntityBase entity, Entry entry) {
        super(entity);
        this.entry = entry;
    }

    /**
     * Gets a detailed diagnostic report combining expected metadata and physical entry attributes.
     *
     * @return the detailed diagnostic text
     */
    @Override
    public String getDetail() {
        return getExpectedEntity(entity) + getCurrentEntry(entry);
    }

    /**
     * Gets the name of the file or rom entity referenced by this note.
     *
     * @return the name string
     */
    @Override
    public String getName() {
        if (entity != null)
            return super.getName();
        return entry.getName();
    }

    /**
     * Gets the CRC32 checksum string value.
     *
     * @return the CRC32 hexadecimal string
     */
    @Override
    public String getCrc() {
        if (entity != null)
            return super.getCrc();
        return entry.getCrc();
    }

    /**
     * Gets the MD5 hash string value.
     *
     * @return the MD5 hexadecimal string
     */
    @Override
    public String getMd5() {
        if (entity != null)
            return super.getMd5();
        return entry.getMd5();
    }

    /**
     * Gets the SHA-1 hash string value.
     *
     * @return the SHA-1 hexadecimal string
     */
    @Override
    public String getSha1() {
        if (entity != null)
            return super.getSha1();
        return entry.getSha1();
    }
}
