package jrm.profile.report;

import jrm.locale.Messages;
import jrm.profile.data.EntityBase;

/**
 * Report note indicating that a physical entry is successfully matched (found and correct) or explicitly configured as not needed.
 *
 * @author optyfr
 * 
 * @since 1.0
 */
public class EntryOK extends EntryNote {
    /**
     * Constructs a new EntryOK note for the specified metadata entity.
     *
     * @param entity the expected entity base metadata
     */
    public EntryOK(final EntityBase entity) {
        super(entity);
    }

    /**
     * Returns a localized string summarizing that the entry is OK.
     *
     * @return the localized message string
     */
    @Override
    public String toString() {
        return String.format(Messages.getString("EntryOK.OK"), parent.ware.getFullName(), entity.getNormalizedName()); //$NON-NLS-1$
    }

    /**
     * Renders an HTML-styled diagnostic document summarizing this note.
     *
     * @return the styled HTML diagnostic document string
     */
    @Override
    public String getDocument() {
        return toDocument(String.format(escape(Messages.getString("EntryOK.OK")), toBlue(parent.ware.getFullName()), toBoldBlack(entity.getNormalizedName()))); //$NON-NLS-1$
    }

    /**
     * Gets the abbreviation code representing this note type.
     *
     * @return the abbreviation "OK"
     */
    @Override
    public String getAbbrv() {
        return "OK";
    }

}
