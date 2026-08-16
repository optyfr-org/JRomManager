package jrm.profile.report;

import jrm.locale.Messages;
import jrm.profile.data.Entity;
import jrm.profile.data.Entry;

/**
 * Report note indicating that a required expected metadata entity is missing, but can be duplicated from another matching physical
 * entry located inside the same container.
 * <p>
 * This status allows local performance optimization by copying an existing file within an archive without external fetches.
 *
 * @author optyfr
 * 
 * @since 1.0
 */
public class EntryMissingDuplicate extends EntryExtNote {
    /**
     * Constructs a new EntryMissingDuplicate note mapping a missing entity to an existing candidate file entry.
     *
     * @param entity the missing expected metadata entity
     * @param entry the existing physical entry that can be duplicated
     */
    public EntryMissingDuplicate(final Entity entity, final Entry entry) {
        super(entity, entry);
    }

    /**
     * Returns a localized string summarizing that the entry can be duplicated.
     *
     * @return the localized message string
     */
    @Override
    public String toString() {
        return String.format(Messages.getString("EntryMissingDuplicate.MissingDuplicate"), parent.ware.getFullName(), entry.getRelFile(), entity.getName()); //$NON-NLS-1$
    }

    /**
     * Renders an HTML-styled diagnostic document summarizing this note.
     *
     * @return the styled HTML diagnostic document string
     */
    @Override
    public String getDocument() {
        return toDocument(String.format(escape(Messages.getString("EntryMissingDuplicate.MissingDuplicate")), toBlue(parent.ware.getFullName()), toBoldBlack(entry.getRelFile()), //$NON-NLS-1$
                toBoldBlack(entity.getName())));
    }

    /**
     * Gets the abbreviation code representing this note type.
     *
     * @return the abbreviation "DUP"
     */
    @Override
    public String getAbbrv() {
        return "DUP";
    }
}
