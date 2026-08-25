package jrm.profile.report;

import java.util.Optional;

import jrm.profile.data.Entity;
import jrm.profile.data.EntityBase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Abstract base class for report notes that describe specific states or discrepancies of an expected ROM metadata entity.
 * <p>
 * This class maps expected database components to physical scan findings, resolving names, sizes, and expected checksum hashes.
 *
 * @author optyfr
 * 
 * @since 1.0
 */
@RequiredArgsConstructor
abstract class EntryNote extends Note {
    private static final long serialVersionUID = 1L;

    /**
     * The expected metadata entity defined in the configuration profile.
     *
     * @return the expected entity base metadata
     */
    protected final @Getter EntityBase entity;

    /**
     * Gets a detailed diagnostic report mapping expected metadata properties.
     *
     * @return the detailed diagnostic text block
     */
    @Override
    public String getDetail() {
        return getExpectedEntity(entity);
    }

    /**
     * Gets the name of the expected file or rom entity referenced by this note.
     *
     * @return the name string
     */
    @Override
    public String getName() {
        return entity.getBaseName();
    }

    /**
     * Gets the expected CRC32 checksum string value of the entity.
     *
     * @return the expected CRC32 hexadecimal string, or {@code null} if not applicable
     */
    @Override
    public String getCrc() {
        if (entity instanceof Entity e)
            return e.getCrc();
        return null;
    }

    /**
     * Gets the expected MD5 hash string value of the entity.
     *
     * @return the expected MD5 hexadecimal string, or {@code null} if not applicable
     */
    @Override
    public String getMd5() {
        if (entity instanceof Entity e)
            return e.getMd5();
        return null;
    }

    /**
     * Gets the expected SHA-1 hash string value of the entity.
     *
     * @return the expected SHA-1 hexadecimal string, or {@code null} if not applicable
     */
    @Override
    public String getSha1() {
        if (entity instanceof Entity e)
            return e.getSha1();
        return null;
    }

    /**
     * Gets the expected primary hash identifying the entity, selected in priority order: SHA-1, MD5, CRC32.
     *
     * @return the expected hexadecimal hash string, or {@code null} if none is defined
     */
    @Override
    public String getHash() {
        if (entity instanceof Entity e) {
            return Optional.ofNullable(e.getSha1()).or(() -> Optional.ofNullable(e.getMd5())).or(() -> Optional.ofNullable(e.getCrc())).orElse(null);
        }
        return null;
    }
}
