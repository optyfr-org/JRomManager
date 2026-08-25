package jrm.ui;

import jrm.profile.manager.ProfileNFO;
import jrm.security.Session;

/**
 * Callback interface for loading a ROM profile into the UI.
 * <p>
 * Implementations handle the actual profile loading logic, typically
 * initializing the scanner and related panels with the loaded profile data.
 */
interface ProfileLoader {
    /**
     * Loads the specified profile and updates the UI accordingly.
     *
     * @param session the current user session
     * @param profile the profile metadata to load
     */
    void loadProfile(final Session session, final ProfileNFO profile);
}
