package jrm.profile.report;

import jrm.locale.Messages;
import jrm.profile.data.Entity;
import jrm.profile.data.Entry;

/**
 * Report note indicating that a physical entry is found for an expected ROM or file entity, but has a wrong or mismatched filename.
 * <p>
 * This status represents entries that can be repaired in-place by simply renaming the file to the expected name.
 *
 * @author optyfr
 * 
 * @since 1.0
 */
public class EntryWrongName extends EntryExtNote {
    /**
     * Constructs a new EntryWrongName note mapping an expected entity to a wrongly-named physical entry.
     *
     * @param entity the expected entity definition
     * @param entry the wrongly-named physical entry
     */
    public EntryWrongName(final Entity entity, final Entry entry) {
        super(entity, entry);
    }

    /**
     * Returns a localized string summarizing that the entry is wrongly named.
     *
     * @return the localized message string
     */
    @Override
    public String toString() {
        return String.format(Messages.getString("EntryWrongName.Wrong"), parent.ware.getFullName(), entry.getName(), entity.getNormalizedName()); //$NON-NLS-1$
    }

    /**
     * Renders an HTML-styled diagnostic document summarizing this note.
     *
     * @return the styled HTML diagnostic document string
     */
    @Override
    public String getDocument() {
        return toDocument(String.format(escape(Messages.getString("EntryWrongName.Wrong")), toBlue(parent.ware.getFullName()), toBoldBlack(entry.getName()), //$NON-NLS-1$
                toBoldBlack(entity.getNormalizedName())));
    }

    /**
     * Gets the abbreviation code representing this note type.
     *
     * @return the abbreviation "WNAME"
     */
    @Override
    public String getAbbrv() {
        return "WNAME";
    }

}
