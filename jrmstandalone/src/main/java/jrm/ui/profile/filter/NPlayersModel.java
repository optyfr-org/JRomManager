package jrm.ui.profile.filter;

import javax.swing.AbstractListModel;

import jrm.profile.filter.NPlayer;
import jrm.profile.filter.NPlayers;

/**
 * List model providing the collection of player count filters.
 * <p>
 * Wraps an {@link NPlayers} instance to expose individual {@link NPlayer} entries
 * for use in combo boxes or lists.
 */
public class NPlayersModel extends AbstractListModel<NPlayer> {
    /** The underlying player count collection. */
    private final transient NPlayers nplayers;

    /**
     * Constructs a new player count model.
     *
     * @param nplayers the player count collection to display
     */
    public NPlayersModel(final NPlayers nplayers) {
        this.nplayers = nplayers;
    }

    @Override
    public int getSize() {
        if (nplayers != null)
            return nplayers.getListNPlayers().size();
        return 0;
    }

    @Override
    public NPlayer getElementAt(int index) {
        if (nplayers != null)
            return nplayers.getListNPlayers().get(index);
        return null; // NOSONAR
    }

}
