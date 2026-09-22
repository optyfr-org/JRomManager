package jrm.server.shared.actions;

import java.time.Instant;
import java.util.Collection;

import com.eclipsesource.json.JsonObject;

import jrm.misc.BreakException;
import jrm.misc.ProfileSettingsEnum;
import jrm.profile.scan.Scan;
import jrm.profile.scan.ScanException;
import jrm.profile.scan.options.ScanAutomation;
import jrm.server.shared.WebSession;
import jrm.server.shared.Worker;

final class ProfileScanOps {
	private final ProfileActions parent;
	private final ActionsMgr ws;

	ProfileScanOps(ProfileActions parent) {
		this.parent = parent;
		this.ws = parent.getWs();
	}

	void performScan(JsonObject jso, final boolean automate, Worker worker) {
		final var session = runScanAndNotify(worker);
		final var automation = currentScanAutomation(session);
		if (automate && session.getCurrScan() != null && hasPendingScanActions(session) && automation.hasFix())
			parent.fix(jso);
	}

	WebSession runScanAndNotify(Worker worker) {
		final var session = ws.getSession();
		worker.setProgress(new ProgressActions(ws));
		try {
			session.setCurrScan(new Scan(session.getCurrProfile(), worker.getProgress()));
		} catch (BreakException _) {
		} catch (ScanException ex) {
			worker.getProgress().addError(ex.getMessage());
		}
		worker.getProgress().close();
		worker.setProgress(null);
		session.setLastAction(Instant.now());
		parent.scanned(session.getCurrScan(), currentScanAutomation(session).hasReport());
		return session;
	}

	private static ScanAutomation currentScanAutomation(final WebSession session) {
		return ScanAutomation.valueOf(session.getCurrProfile().getSettings().getProperty(ProfileSettingsEnum.automation_scan));
	}

	private static boolean hasPendingScanActions(final WebSession session) {
		return session.getCurrScan().actions.stream().mapToInt(Collection::size).sum() > 0;
	}
}
