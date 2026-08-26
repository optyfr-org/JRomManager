package jrm.server.shared.actions;

import java.util.EnumSet;

import jrm.aui.basic.SDRList;
import jrm.aui.basic.SrcDstResult;
import jrm.batch.TorrentChecker;
import jrm.io.torrent.options.TrntChkMode;
import jrm.misc.SettingsEnum;
import jrm.server.shared.WebSession;

final class TrntChkSessionBridge {
	private final WebSession session;

	TrntChkSessionBridge(WebSession session) {
		this.session = session;
	}

	TrntChkMode getMode() {
		return TrntChkMode.valueOf(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_mode));
	}

	EnumSet<TorrentChecker.Options> getOpts() {
		final var opts = EnumSet.noneOf(TorrentChecker.Options.class);
		if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_remove_unknown_files, Boolean.class)))
			opts.add(TorrentChecker.Options.REMOVEUNKNOWNFILES);
		if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_remove_wrong_sized_files, Boolean.class)))
			opts.add(TorrentChecker.Options.REMOVEWRONGSIZEDFILES);
		if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_detect_archived_folders, Boolean.class)))
			opts.add(TorrentChecker.Options.DETECTARCHIVEDFOLDERS);
		return opts;
	}

	SDRList<SrcDstResult> loadSDR() {
		return SrcDstResult.fromJSON(session.getUser().getSettings().getProperty(SettingsEnum.trntchk_sdr));
	}

	void saveSDR(SDRList<SrcDstResult> sdrl) {
		session.getUser().getSettings().setProperty(SettingsEnum.trntchk_sdr, jrm.aui.basic.AbstractSrcDstResult.toJSON(sdrl));
		session.getUser().getSettings().saveSettings();
	}
}
