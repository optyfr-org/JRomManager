package jrm.server.shared.actions;

import java.time.Instant;

import jrm.misc.ProfileSettingsEnum;
import jrm.profile.fix.Fix;
import jrm.profile.scan.ScanException;
import jrm.profile.scan.options.ScanAutomation;
import jrm.server.shared.WebSession;

final class ProfileFixOps {
	private final ProfileActions parent;
	private final ActionsMgr ws;

	ProfileFixOps(ProfileActions parent) {
		this.parent = parent;
		this.ws = parent.getWs();
	}

	void performFix() {
		final var session = ws.getSession();
		session.getWorker().setProgress(new ProgressActions(ws));
		try {
			if (session.getCurrProfile().hasPropsChanged()) {
				session.setCurrScan(new jrm.profile.scan.Scan(session.getCurrProfile(), session.getWorker().getProgress()));
				if (!hasPendingScanActions(session))
					return;
			}
			final var fix = new Fix(session.getCurrProfile(), session.getCurrScan(), session.getWorker().getProgress());
			parent.fixed(fix);
		} catch (ScanException ex) {
			session.getWorker().getProgress().addError(ex.getMessage());
		} finally {
			final var automation = currentScanAutomation(session);
			session.getWorker().getProgress().close();
			session.getWorker().setProgress(null);
			session.setLastAction(Instant.now());
			if (automation.hasScanAgain())
				session.setWorker(new jrm.server.shared.Worker(parent::runScanAndNotifyForFix)).start();
		}
	}

	private static ScanAutomation currentScanAutomation(final WebSession session) {
		return ScanAutomation.valueOf(session.getCurrProfile().getSettings().getProperty(ProfileSettingsEnum.automation_scan));
	}

	private static boolean hasPendingScanActions(final WebSession session) {
		return session.getCurrScan() != null && session.getCurrScan().actions.stream().mapToInt(java.util.Collection::size).sum() > 0;
	}
}
