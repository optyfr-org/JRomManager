package jrm.server.shared.ws;

import jrm.server.shared.WebSession;
import jrm.server.shared.actions.ActionsMgr;
import jrm.server.shared.actions.CatVerActions;
import jrm.server.shared.actions.NPlayersActions;
import jrm.server.shared.actions.ProfileActions;

/**
 * Shared session-seeding helper used by both transports: {@code ActionServlet.doInit} (LPR {@code GET /actions/init})
 * and {@code ActionSocket.onOpen} (WS open) delegate here so WS-open init emits the same messages as LPR init.
 */
public final class ActionInit {

    private ActionInit() {
    }

    /**
     * Seeds the session with profile actions and live worker progress.
     *
     * @param mgr the manager to push through (LPR or WS)
     * @param sess the web session to initialize
     */
    public static void init(final ActionsMgr mgr, final WebSession sess) {
        if (sess.getCurrProfile() != null) {
            new ProfileActions(mgr).loaded(sess.getCurrProfile());
            new CatVerActions(mgr).loaded(sess.getCurrProfile());
            new NPlayersActions(mgr).loaded(sess.getCurrProfile());
        }
        if (sess.getWorker() != null && sess.getWorker().isAlive() && sess.getWorker().getProgress() != null)
            sess.getWorker().getProgress().reload(mgr);
    }
}
