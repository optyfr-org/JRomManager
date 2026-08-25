package jrm.profile.report;

import jrm.profile.data.Container;
import lombok.Getter;

/**
 * Abstract base class for report subjects associated with physical storage containers (e.g., zip files, directories).
 * <p>
 * This class maps report subjects that are not directly retro-gaming machine definitions, but are rather filesystem physical
 * containers.
 *
 * @author optyfr
 * 
 * @since 1.0
 */
abstract class ContainerSubject extends Subject {
    private static final long serialVersionUID = 1L;

    /**
     * The physical storage container associated with this subject.
     *
     * @return the physical container
     */
    protected final @Getter Container container;

    /**
     * Constructs a new ContainerSubject wrapping the specified physical container.
     *
     * @param container the filesystem storage container to wrap
     */
    protected ContainerSubject(Container container) {
        super(null);
        this.container = container;
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
     * Updates parent statistics. Physical filesystem containers do not increment database romset counts.
     */
    @Override
    public void updateStats() {
        // do nothing
    }

}
