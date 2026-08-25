package jrm.profile.report;

import java.io.Serializable;
import java.util.Comparator;

import jrm.aui.status.StatusRendererFactory;
import jrm.profile.data.Entity;
import jrm.profile.data.EntityBase;
import jrm.profile.data.Entry;
import lombok.Getter;

/**
 * Represents an individual report note (leaf node) within a container subject.
 * <p>
 * A Note describes specific structural states, details, and differences discovered during the ROM scanning process, such as wrong
 * hash values, missing items, unneeded files, or successful matches.
 *
 * @author optyfr
 * 
 * @since 1.0
 */
public abstract class Note implements StatusRendererFactory, Serializable {

    /** Serial version UID for serialization backward compatibility of Note records. */
    private static final long serialVersionUID = 2L;

    /**
     * The parent subject containing this note.
     *
     * @return the parent subject
     */
    transient @Getter Subject parent;

    /**
     * The transient identifier of this note, used to map tree selections in the UI.
     */
    transient int id = -1;

    /**
     * Default constructor for Note. This constructor does not perform any specific initialization and is intended to be used by
     * subclasses that will provide concrete implementations of the abstract methods defined in this class.
     */
    protected Note() {
        /* default constructor */ }

    /**
     * Gets a short abbreviation code representing the type of this note (e.g., "OK", "MISS", "UNNEED", "ADD").
     *
     * @return the abbreviation code string
     */
    public abstract String getAbbrv();

    /**
     * Returns a localized text representation of this note's status message.
     *
     * @return the localized status string
     */
    @Override
    public abstract String toString();

    /**
     * Gets a detailed diagnostic report mapping expected metadata against current physical state attributes.
     *
     * @return the detailed diagnostic text
     */
    public abstract String getDetail();

    /**
     * Gets the name of the file or rom entity referenced by this note.
     *
     * @return the name string
     */
    public abstract String getName();

    /**
     * Gets the expected or current CRC32 checksum string value.
     *
     * @return the CRC32 hexadecimal string, or {@code null} if not applicable or available
     */
    public abstract String getCrc();

    /**
     * Gets the expected or current MD5 hash string value.
     *
     * @return the MD5 hexadecimal string, or {@code null} if not applicable or available
     */
    public abstract String getMd5();

    /**
     * Gets the expected or current SHA-1 hash string value.
     *
     * @return the SHA-1 hexadecimal string, or {@code null} if not applicable or available
     */
    public abstract String getSha1();

    /**
     * Gets the primary checksum or hash identifying this note, selected in priority order: SHA-1, MD5, CRC32.
     *
     * @return the hexadecimal hash string, or {@code null} if none is defined
     */
    public abstract String getHash();

    /**
     * Gets the identifier of this note.
     *
     * @return the integer ID, or {@code -1} if uninitialized
     */
    public int getId() {
        return id;
    }

    /**
     * Computes the identity hash code of this instance.
     *
     * @return the identity hash code
     */
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    /**
     * Formats a diagnostic text block summarizing the expected attributes of the target entity.
     *
     * @param entity the expected entity definition
     * 
     * @return the formatted diagnostic string, or an empty string if entity is {@code null}
     */
    protected String getExpectedEntity(EntityBase entity) {
        if (entity == null)
            return "";
        String msg = "";
        msg += "== Expected == \n";
        msg += "Name : " + entity.getBaseName() + "\n";
        if (entity instanceof Entity e1) {
            if (e1.getSize() >= 0)
                msg += "Size : " + e1.getSize() + "\n";
            if (e1.getCrc() != null)
                msg += "CRC : " + e1.getCrc() + "\n";
            if (e1.getMd5() != null)
                msg += "MD5 : " + e1.getMd5() + "\n";
            if (e1.getSha1() != null)
                msg += "SHA1 : " + e1.getSha1() + "\n";
        }
        return msg;
    }

    /**
     * Formats a diagnostic text block summarizing the actual attributes of the physical file entry.
     *
     * @param entry the actual scanned file entry
     * 
     * @return the formatted diagnostic string, or an empty string if entry is {@code null}
     */
    protected String getCurrentEntry(Entry entry) {
        if (entry == null)
            return "";
        String msg = "";
        msg += "== Current == \n";
        msg += "Name : " + entry.getName() + "\n";
        if (entry.getSize() >= 0)
            msg += "Size : " + entry.getSize() + "\n";
        if (entry.getCrc() != null)
            msg += "CRC : " + entry.getCrc() + "\n";
        if (entry.getMd5() != null)
            msg += "MD5 : " + entry.getMd5() + "\n";
        if (entry.getSha1() != null)
            msg += "SHA1 : " + entry.getSha1() + "\n";
        return msg;
    }

    /**
     * Gets a comparator for sorting notes alphabetically by their name case-insensitively.
     *
     * @return the sorting comparator
     */
    public static Comparator<Note> getComparator() {
        return (n1, n2) -> {
            final var name1 = n1.getName();
            final var name2 = n2.getName();
            if (name1 == null) {
                if (name2 == null)
                    return 0;
                return -1;
            }
            if (name2 == null)
                return 1;
            return name1.compareToIgnoreCase(name2);
        };
    }

}
