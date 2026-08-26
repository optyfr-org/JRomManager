package jrm.fx.ui;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;

import jrm.fx.ui.progress.ProgressTask;
import jrm.misc.SettingsEnum;
import jrm.profile.manager.Export.ExportType;
import jrm.profile.scan.Dir2Dat;
import jrm.profile.scan.DirScan;
import jrm.profile.scan.DirScan.Options;
import jrm.security.Session;

final class Dir2DatRun {
	static void execute(final Session session, final String src, final String dst, final HashMap<String, String> headers, final ProgressTask<Void> task) throws IOException {
		if (src == null || src.isEmpty() || dst == null || dst.isEmpty())
			return;
		final File srcdir = new File(src);
		if (!srcdir.isDirectory())
			return;
		final File dstdat = new File(dst);
		final File dstdir = dstdat.getParentFile();
		if ((dstdir == null || dstdir.isDirectory()) && (dstdat.exists() || dstdat.createNewFile())) {
			final var options = initOptions(session);
			final var type = ExportType.valueOf(session.getUser().getSettings().getProperty(SettingsEnum.dir2dat_format));
			new Dir2Dat(session, srcdir, dstdat, task, options, type, headers);
		}
	}

	private static EnumSet<DirScan.Options> initOptions(final Session session) {
		EnumSet<DirScan.Options> options = EnumSet.of(Options.USE_PARALLELISM, Options.MD5_DISKS, Options.SHA1_DISKS);
		if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(SettingsEnum.dir2dat_scan_subfolders, Boolean.class)))
			options.add(Options.RECURSE);
		if (Boolean.FALSE.equals(session.getUser().getSettings().getProperty(SettingsEnum.dir2dat_deep_scan, Boolean.class)))
			options.add(Options.IS_DEST);
		if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(SettingsEnum.dir2dat_add_md5, Boolean.class)))
			options.add(Options.NEED_MD5);
		if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(SettingsEnum.dir2dat_add_sha1, Boolean.class)))
			options.add(Options.NEED_SHA1);
		if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(SettingsEnum.dir2dat_junk_folders, Boolean.class)))
			options.add(Options.JUNK_SUBFOLDERS);
		if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(SettingsEnum.dir2dat_do_not_scan_archives, Boolean.class)))
			options.add(Options.ARCHIVES_AND_CHD_AS_ROMS);
		if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(SettingsEnum.dir2dat_match_profile, Boolean.class)))
			options.add(Options.MATCH_PROFILE);
		if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(SettingsEnum.dir2dat_include_empty_dirs, Boolean.class)))
			options.add(Options.EMPTY_DIRS);
		return options;
	}
}
