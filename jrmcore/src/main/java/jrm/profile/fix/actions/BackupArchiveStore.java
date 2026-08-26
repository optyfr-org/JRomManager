package jrm.profile.fix.actions;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;

import jrm.misc.Log;
import jrm.misc.ProfileSettingsEnum;
import jrm.profile.data.Container;
import jrm.security.PathAbstractor;
import jrm.security.Session;
import net.lingala.zip4j.ZipFile;

final class BackupArchiveStore {
	private static final Map<String, ZipFile> zipfiles = new HashMap<>();

	static synchronized ZipFile getZipFile(final Session session, Container container, EntryAction action) {
		Log.info(action.entry.getFile());
		final var crc2 = action.entry.getCrc().substring(0, 2);
		if (!zipfiles.containsKey(crc2)) {
			final String workdir;
			if (Boolean.TRUE.equals(session.getCurrProfile().getSettings().getProperty(ProfileSettingsEnum.backup_dest_dir_enabled, Boolean.class)))
				workdir = session.getCurrProfile().getSettings().getProperty(ProfileSettingsEnum.backup_dest_dir);
			else if (Boolean.TRUE.equals(session.getUser().getSettings().getProperty(ProfileSettingsEnum.backup_dest_dir_enabled, Boolean.class)))
				workdir = session.getUser().getSettings().getProperty(ProfileSettingsEnum.backup_dest_dir);
			else
				workdir = "%work/backup";
			final var backupdir = PathAbstractor.getAbsolutePath(session, workdir).toFile();
			final var crc = new CRC32();
			crc.update(container.getFile().getAbsoluteFile().getParent().getBytes());
			final var backupfile = new File(new File(backupdir, String.format("%08x", crc.getValue())), crc2 + ".zip");
			backupfile.getParentFile().mkdirs();
			zipfiles.put(crc2, new ZipFile(backupfile));
		}
		return zipfiles.get(crc2);
	}

	static synchronized void closeAllFS() {
		for (final var zipfile : zipfiles.values()) {
			try {
				synchronized (zipfile) {
					zipfile.close();
				}
			} catch (IOException e) {
				Log.err(e.getMessage(), e);
			}
		}
		zipfiles.clear();
	}
}
