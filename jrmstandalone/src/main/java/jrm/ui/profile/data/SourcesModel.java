package jrm.ui.profile.data;

import javax.swing.AbstractListModel;

import jrm.profile.data.Source;
import jrm.profile.data.Sources;

/**
 * List model providing the collection of sources for list-based UI components.
 * <p>
 * Wraps a {@link Sources} instance to expose individual {@link Source} entries.
 */
public class SourcesModel extends AbstractListModel<Source> {

    /** The underlying sources collection. */
    private final Sources sources;

    /**
     * Constructs a new sources model.
     *
     * @param sources the sources collection to display
     */
    public SourcesModel(final Sources sources) {
        this.sources = sources;
    }

    @Override
    public int getSize() {
        return sources.getSrces().size();
    }

    @Override
    public Source getElementAt(int index) {
        return sources.getSrces().get(index);
    }

}
