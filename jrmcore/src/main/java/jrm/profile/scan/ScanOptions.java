/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.scan;

import java.util.Set;

import jrm.misc.SettingsEnum;
import jtrrntzip.DummyLogCallback;
import jtrrntzip.SimpleTorrentZipOptions;
import jtrrntzip.TorrentZip;
import jrm.security.Session;

/**
 * Helper structure aggregating scanning variables and options constraints.
 * Package-private; used internally by DirScan and its collaborators.
 */
class ScanOptions {
	/**
	 * Whether SHA-1 or MD5 calculation is explicitly required.
	 */
	final boolean needSha1OrMd5;

	/**
	 * Whether MD5 checks are requested for disk containers in the profile.
	 */
	final boolean md5Disks;

	/**
	 * Whether MD5 checks are requested for ROMs in the profile.
	 */
	final boolean md5Roms;

	/**
	 * Whether SHA-1 checks are requested for disk containers in the profile.
	 */
	final boolean sha1Disks;

	/**
	 * Whether SHA-1 checks are requested for ROMs in the profile.
	 */
	final boolean sha1Roms;

	/**
	 * Indicates if the target directory is a destination folder.
	 */
	final boolean isDest;
	/**
	 * Indicates whether folder walking should be recursive.
	 */
	final boolean recurse;
	/**
	 * Indicates if multi-threading is enabled.
	 */
	final boolean useParallelism;
	/**
	 * Indicates whether to format zip archives using TorrentZip standards.
	 */
	final boolean formatTZip;
	/**
	 * Indicates if empty folders should be added to the output.
	 */
	final boolean includeEmptyDirs;
	/**
	 * Indicates whether to treat archives and CHD disk containers as single ROMs.
	 */
	final boolean archivesAndChdAsRoms;

	/**
	 * Parallel thread count.
	 */
	final int nThreads;

	/**
	 * TorrentZip verification and formatting engine.
	 */
	final TorrentZip torrentzip;

	/**
	 * Instantiates a new options configuration container.
	 * 
	 * @param session the active session (needed for thread count and temp paths for tzip)
	 * @param options the scan options enum list
	 */
	ScanOptions(Session session, Set<DirScan.Options> options) {
		needSha1OrMd5 = options.contains(DirScan.Options.NEED_SHA1_OR_MD5) || options.contains(DirScan.Options.NEED_SHA1) || options.contains(DirScan.Options.NEED_MD5);
		md5Disks = options.contains(DirScan.Options.MD5_DISKS) || options.contains(DirScan.Options.NEED_MD5);
		md5Roms = options.contains(DirScan.Options.MD5_ROMS) || options.contains(DirScan.Options.NEED_MD5);
		sha1Disks = options.contains(DirScan.Options.SHA1_DISKS) || options.contains(DirScan.Options.NEED_SHA1);
		sha1Roms = options.contains(DirScan.Options.SHA1_ROMS) || options.contains(DirScan.Options.NEED_SHA1);
		isDest = options.contains(DirScan.Options.IS_DEST);
		recurse = options.contains(DirScan.Options.RECURSE);
		useParallelism = options.contains(DirScan.Options.USE_PARALLELISM);
		formatTZip = options.contains(DirScan.Options.FORMAT_TZIP);
		includeEmptyDirs = options.contains(DirScan.Options.EMPTY_DIRS);
		archivesAndChdAsRoms = options.contains(DirScan.Options.ARCHIVES_AND_CHD_AS_ROMS);
		nThreads = useParallelism ? session.getUser().getSettings().getProperty(SettingsEnum.thread_count, Integer.class) : 1;
		torrentzip = (isDest && formatTZip) ? new TorrentZip(new DummyLogCallback(), new SimpleTorrentZipOptions(false, true)) : null;
	}
}
