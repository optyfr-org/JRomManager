package jrm.ui.profile.data;

import javax.swing.AbstractListModel;

import jrm.profile.data.Systm;
import jrm.profile.data.Systms;

/**
 * List model providing the collection of systems for list-based UI components.
 * <p>
 * Wraps a {@link Systms} instance to expose individual {@link Systm} entries.
 */
public class SystmsModel extends AbstractListModel<Systm> {

    /** The underlying systems collection. */
    private final Systms systms;

    /**
     * Constructs a new systems model.
     *
     * @param systms the systems collection to display
     */
    public SystmsModel(final Systms systms) {
        this.systms = systms;
    }

    @Override
    public int getSize() {
        return systms.getSystems().size();
    }

    @Override
    public Systm getElementAt(int index) {
        return systms.getSystems().get(index);
    }

}
