package jrm.profile.report;

import jrm.locale.Messages;
import jrm.profile.data.Entry;

/**
 * Report note indicating that a physical entry found in the container is not needed by the active database.
 * <p>
 * This status describes files present inside romset containers or folders that do not match any defined ROM, disk, or sample
 * metadata.
 *
 * @author optyfr
 * 
 * @since 1.0
 */
public class EntryUnneeded extends EntryExtNote {
    /**
     * Constructs a new EntryUnneeded note for the specified physical entry.
     *
     * @param entry the physical entry that is not needed
     */
    public EntryUnneeded(final Entry entry) {
        super(null, entry);
    }

    /**
     * Returns a localized string summarizing that the entry is unneeded.
     *
     * @return the localized message string
     */
    @Override
    public String toString() {
        final String hash;
        if (entry.getSha1() != null)
            hash = entry.getSha1();
        else if (entry.getMd5() != null)
            hash = entry.getMd5();
        else
            hash = entry.getCrc();
        return String.format(Messages.getString("EntryUnneeded.Unneeded"), parent.ware.getFullName(), entry.getRelFile(), hash); //$NON-NLS-1$
    }

    /**
     * Renders an HTML-styled diagnostic document summarizing this note.
     *
     * @return the styled HTML diagnostic document string
     */
    @Override
    public String getDocument() {
        final String hash;
        if (entry.getSha1() != null)
            hash = entry.getSha1();
        else if (entry.getMd5() != null)
            hash = entry.getMd5();
        else
            hash = entry.getCrc();
        return toDocument(String.format(escape(Messages.getString("EntryUnneeded.Unneeded")), toBoldBlack(parent.ware.getFullName()), toBoldBlack(entry.getRelFile()), hash)); //$NON-NLS-1$
    }

    /**
     * Gets the abbreviation code representing this note type.
     *
     * @return the abbreviation "UNNEED"
     */
    @Override
    public String getAbbrv() {
        return "UNNEED";
    }

}
