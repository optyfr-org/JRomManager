package jrm.profile.report;

import jrm.locale.Messages;
import jrm.profile.data.EntityBase;
import jrm.profile.data.Entry;

/**
 * Report note indicating that a physical file entry can be added to the target container.
 * <p>
 * This status occurs when a matching ROM or file is found in another local location or archive, and can be copied or imported to
 * resolve a missing dependency.
 *
 * @author optyfr
 * 
 * @since 1.0
 */
public class EntryAdd extends EntryExtNote {
    /**
     * Constructs a new EntryAdd note mapping an expected entity to an actual available entry.
     *
     * @param entity the expected rom, disk, or sample metadata
     * @param entry the actual scanned file entry that is available for copy/add
     */
    public EntryAdd(final EntityBase entity, final Entry entry) {
        super(entity, entry);
    }

    /**
     * Returns a localized string summarizing that the entry can be added.
     *
     * @return the localized message string
     */
    @Override
    public String toString() {
        return String.format(Messages.getString("EntryAddAdd"), parent.ware.getFullName(), entity.getNormalizedName(), entry.getParent().getRelFile().getName(), //$NON-NLS-1$
                entry.getRelFile());
    }

    /**
     * Renders an HTML-styled diagnostic document summarizing this note.
     *
     * @return the styled HTML diagnostic document string
     */
    @Override
    public String getDocument() {
        return toDocument(String.format(escape(Messages.getString("EntryAddAdd")), toBlue(parent.ware.getFullName()), toBoldBlack(entity.getNormalizedName()), //$NON-NLS-1$
                toItalicBlack(entry.getParent().getRelFile().getName()), toBoldBlack(entry.getRelFile())));
    }

    /**
     * Gets the abbreviation code representing this note type.
     *
     * @return the abbreviation "ADD"
     */
    @Override
    public String getAbbrv() {
        return "ADD";
    }
}
