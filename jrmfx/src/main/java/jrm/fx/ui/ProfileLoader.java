package jrm.fx.ui;

import jrm.profile.manager.ProfileNFO;
import jrm.security.Session;

/**
 * Callback interface for loading a selected profile.
 * <p>
 * Implemented by the scanner panel controller so that the profile panel can
 * trigger a profile load when the user selects one.
 *
 * @since 2.5
 */
public interface ProfileLoader {
    /**
     * Loads the given profile.
     *
     * @param session the current user session
     * @param profile the profile to load
     */
    void loadProfile(final Session session, final ProfileNFO profile);
}
