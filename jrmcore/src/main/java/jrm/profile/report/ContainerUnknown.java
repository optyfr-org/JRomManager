package jrm.profile.report;

import java.util.Set;
import java.util.stream.Stream;

import jrm.locale.Messages;
import jrm.profile.data.Container;

/**
 * Subject representing an unknown container discovered on disk during scanning.
 * <p>
 * This status usually indicates a file or directory that does not correspond to any known game romset in the active database.
 *
 * @author optyfr
 * 
 * @since 1.0
 */
public class ContainerUnknown extends ContainerSubject {
    /**
     * Constructs a new ContainerUnknown subject for the specified physical container.
     *
     * @param c the physical storage container
     */
    public ContainerUnknown(final Container c) {
        super(c);
    }

    /**
     * Returns a localized string summarizing that the container is unknown.
     *
     * @return the localized message string
     */
    @Override
    public String toString() {
        return String.format(Messages.getString("ContainerUnknown.Unknown"), //$NON-NLS-1$
                container.getType() == Container.Type.DIR ? Messages.getString("ContainerUnknown.Directory") : Messages.getString("ContainerUnknown.File"), container.getRelFile()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Clones this subject without modifying notes.
     *
     * @param filterOptions the active filtering options
     * 
     * @return a cloned ContainerUnknown instance
     */
    @Override
    public Subject clone(final Set<FilterOptions> filterOptions) {
        return new ContainerUnknown(container);
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
        return notes.stream();
    }
}
