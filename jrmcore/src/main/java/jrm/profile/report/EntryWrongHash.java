package jrm.profile.report;

import jrm.locale.Messages;
import jrm.profile.data.Entity;
import jrm.profile.data.Entry;

/**
 * Report note indicating that a physical entry is present in the container but has a wrong or mismatched hash code when compared
 * against its expected database ROM or file entity.
 *
 * @author optyfr
 * 
 * @since 1.0
 */
public class EntryWrongHash extends EntryExtNote {
    /**
     * Resource bundle key for wrong hash entries localization messages.
     */
    private static final String ENTRY_WRONG_HASH_WRONG = "EntryWrongHash.Wrong";

    /**
     * Constructs a new EntryWrongHash note mapping the mismatched entity to the physical entry.
     *
     * @param entity the expected entity definition
     * @param entry the physical file entry with mismatched hash
     */
    public EntryWrongHash(final Entity entity, final Entry entry) {
        super(entity, entry);
    }

    /**
     * Returns a localized string summarizing that the entry has a wrong hash.
     *
     * @return the localized message string
     */
    @Override
    public String toString() {
        if (entry.getMd5() == null && entry.getSha1() == null)
            return String.format(Messages.getString(ENTRY_WRONG_HASH_WRONG), parent.ware.getFullName(), entry.getRelFile(), "CRC", entry.getCrc(), getCrc()); //$NON-NLS-1$ //$NON-NLS-2$
        else if (entry.getSha1() == null)
            return String.format(Messages.getString(ENTRY_WRONG_HASH_WRONG), parent.ware.getFullName(), entry.getRelFile(), "MD5", entry.getMd5(), getMd5()); //$NON-NLS-1$ //$NON-NLS-2$
        else
            return String.format(Messages.getString(ENTRY_WRONG_HASH_WRONG), parent.ware.getFullName(), entry.getRelFile(), "SHA-1", entry.getSha1(), getSha1()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Renders an HTML-styled diagnostic document summarizing this note.
     *
     * @return the styled HTML diagnostic document string
     */
    @Override
    public String getDocument() {
        if (entry.getMd5() == null && entry.getSha1() == null)
            return toDocument(String.format(escape(Messages.getString(ENTRY_WRONG_HASH_WRONG)), toBlue(parent.ware.getFullName()), toBoldBlack(entry.getRelFile()), "CRC", //$NON-NLS-1$
                    entry.getCrc(), getCrc())); // $NON-NLS-2$
        else if (entry.getSha1() == null)
            return toDocument(String.format(escape(Messages.getString(ENTRY_WRONG_HASH_WRONG)), toBlue(parent.ware.getFullName()), toBoldBlack(entry.getRelFile()), "MD5", //$NON-NLS-1$
                    entry.getMd5(), getMd5())); // $NON-NLS-2$
        else
            return toDocument(String.format(escape(Messages.getString(ENTRY_WRONG_HASH_WRONG)), toBlue(parent.ware.getFullName()), toBoldBlack(entry.getRelFile()), "SHA-1", //$NON-NLS-1$
                    entry.getSha1(), getSha1())); // $NON-NLS-2$
    }

    /**
     * Gets the abbreviation code representing this note type.
     *
     * @return the abbreviation "WHASH"
     */
    @Override
    public String getAbbrv() {
        return "WHASH";
    }

}
