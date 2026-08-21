package jrm.profile.report;

import java.util.Set;
import java.util.stream.Stream;

import jrm.locale.Messages;
import jrm.profile.data.Container;

/**
 * Subject representing an unneeded container discovered on disk during scanning.
 * <p>
 * This status indicates that the physical file or folder matches a known game or romset, but is not selected or required by the
 * active configuration profile settings.
 *
 * @author optyfr
 * 
 * @since 1.0
 */
public class ContainerUnneeded extends ContainerSubject {
    /**
     * Constructs a new ContainerUnneeded subject for the specified physical container.
     *
     * @param c the physical storage container
     */
    public ContainerUnneeded(final Container c) {
        super(c);
    }

    /**
     * Returns a localized string summarizing that the container is unneeded.
     *
     * @return the localized message string
     */
    @Override
    public String toString() {
        return String.format(Messages.getString("ContainerUnneeded.Unneeded"), //$NON-NLS-1$
                container.getType() == Container.Type.DIR ? Messages.getString("ContainerUnneeded.Directory") : Messages.getString("ContainerUnneeded.File"), //$NON-NLS-1$ //$NON-NLS-2$
                container.getRelFile());
    }

    /**
     * Clones this subject without modifying notes.
     *
     * @param filterOptions the active filtering options
     * 
     * @return a cloned ContainerUnneeded instance
     */
    @Override
    public Subject clone(final Set<FilterOptions> filterOptions) {
        return new ContainerUnneeded(container);
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
