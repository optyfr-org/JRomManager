package jrm.profile.report;

import java.util.Set;
import java.util.stream.Stream;

import jrm.locale.Messages;
import jrm.profile.data.AnywareBase;

/**
 * Subject indicating that a suspicious CRC32 checksum mismatch was discovered during scan validation.
 * <p>
 * A suspicious CRC checksum occurs when two distinct file entries share an identical CRC32 value but have conflicting high-security
 * cryptographic hashes (MD5 or SHA-1), signaling potential collisions or errors.
 *
 * @author optyfr
 * 
 * @since 1.0
 */
public class RomSuspiciousCRC extends Subject {
    private static final long serialVersionUID = 2L;

    /**
     * The hexadecimal representation of the suspicious CRC32 checksum.
     */
    private String crc;

    /**
     * Constructs a new RomSuspiciousCRC subject with no parent {@link AnywareBase} reference.
     *
     * @param crc the suspicious CRC32 hex value
     */
    public RomSuspiciousCRC(final String crc) {
        super(null);
        this.crc = crc;
    }

    /**
     * Returns a localized string summarizing the suspicious CRC32 value.
     *
     * @return the localized message string
     */
    @Override
    public String toString() {
        return String.format(Messages.getString("RomSuspiciousCRC.SuspiciousCRC"), crc); //$NON-NLS-1$
    }

    /**
     * Returns the localized text document representation of this subject.
     *
     * @return the string document
     */
    @Override
    public String getDocument() {
        return toString();
    }

    /**
     * Clones this subject with the specified filtering options.
     *
     * @param filterOptions the active filtering options
     * 
     * @return a cloned RomSuspiciousCRC instance
     */
    @Override
    public Subject clone(final Set<FilterOptions> filterOptions) {
        return new RomSuspiciousCRC(crc);
    }

    /**
     * Streams the collection of status notes associated with this subject.
     *
     * @param filterOptions the active filtering options to evaluate
     * 
     * @return a stream of status Note instances
     */
    @Override
    public Stream<Note> stream(Set<FilterOptions> filterOptions) {
        return getNotes().stream();
    }

    /**
     * Updates parent statistics. Suspicious CRC instances do not increment database statistics.
     */
    @Override
    public void updateStats() {
        // do nothing
    }
}
