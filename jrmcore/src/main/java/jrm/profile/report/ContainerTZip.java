package jrm.profile.report;

import java.util.Set;
import java.util.stream.Stream;

import jrm.locale.Messages;
import jrm.profile.data.Container;

/**
 * Subject indicating that a physical container needs to be converted or processed as a TorrentZip file.
 * <p>
 * TorrentZip format ensures standardized layout and compression properties within ZIP files to facilitate exact hash matches.
 *
 * @author optyfr
 * 
 * @since 1.0
 */
public class ContainerTZip extends ContainerSubject {
    private static final long serialVersionUID = 2L;

    /**
     * Constructs a new ContainerTZip subject for the specified physical container.
     *
     * @param c the physical storage container
     */
    public ContainerTZip(final Container c) {
        super(c);
    }

    /**
     * Returns a localized string summarizing that the container needs TorrentZip conversion.
     *
     * @return the localized message string
     */
    @Override
    public String toString() {
        return String.format(Messages.getString("ContainerTZip.NeedTZip"), container.getRelFile()); //$NON-NLS-1$
    }

    /**
     * Clones this subject without modifying notes.
     *
     * @param filterOptions the active filtering options
     * 
     * @return a cloned ContainerTZip instance
     */
    @Override
    public Subject clone(final Set<FilterOptions> filterOptions) {
        return new ContainerTZip(container);
    }

    /**
     * Streams the collection of status notes associated with this container subject.
     *
     * @param filterOptions the active filtering options to evaluate
     * 
     * @return a stream of status Note instances
     */
    @Override
    public Stream<Note> stream(Set<FilterOptions> filterOptions) {
        return getNotes().stream();
    }
}
