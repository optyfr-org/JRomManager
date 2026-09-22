package jrm.server.shared.actions;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import jrm.misc.ProfileSettingsEnum;
import jrm.profile.fix.Fix;
import jrm.profile.scan.ScanException;
import jrm.profile.scan.options.ScanAutomation;
import jrm.server.shared.WebSession;
import jrm.server.shared.Worker;

final class ProfileFixOps {
	private final ProfileActions parent;
	private final ActionsMgr ws;

	ProfileFixOps(ProfileActions parent) {
		this.parent = parent;
		this.ws = parent.getWs();
	}

	void performFix(Worker worker) {
		final var session = ws.getSession();
		worker.setProgress(new ProgressActions(ws));
		try {
			if (session.getCurrProfile().hasPropsChanged()) {
				session.setCurrScan(new jrm.profile.scan.Scan(session.getCurrProfile(), worker.getProgress()));
				if (!hasPendingScanActions(session))
					return;
			}
			final var fix = new Fix(session.getCurrProfile(), session.getCurrScan(), worker.getProgress());
			parent.fixed(fix);
		} catch (ScanException ex) {
			worker.getProgress().addError(ex.getMessage());
		} finally {
			final var automation = currentScanAutomation(session);
			worker.getProgress().close();
			worker.setProgress(null);
			session.setLastAction(Instant.now());
			if (automation.hasScanAgain()) {
				final var workerRef = new AtomicReference<Worker>();
				final var scanWorker = new Worker(() -> parent.runScanAndNotifyForFix(workerRef.get()));
				workerRef.set(scanWorker);
				session.setWorker(scanWorker).start();
			}
		}
	}

	private static ScanAutomation currentScanAutomation(final WebSession session) {
		return ScanAutomation.valueOf(session.getCurrProfile().getSettings().getProperty(ProfileSettingsEnum.automation_scan));
	}

	private static boolean hasPendingScanActions(final WebSession session) {
		return session.getCurrScan() != null && session.getCurrScan().actions.stream().mapToInt(java.util.Collection::size).sum() > 0;
	}
}
