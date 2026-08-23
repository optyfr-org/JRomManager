/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.scan;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

import jrm.aui.progress.ProgressHandler;
import jrm.locale.Messages;
import jrm.misc.Log;
import jrm.profile.data.Container;
import jrm.security.PathAbstractor;
import jrm.security.Session;
import jrm.security.SignedObjectStore;

/**
 * Cache file naming and (de)serialization helper for DirScan results.
 */
final class ScanCache {

	private final Session session;
	private final ProgressHandler handler;

	ScanCache(Session session, ProgressHandler handler) {
		this.session = session;
		this.handler = handler;
	}

	private static String getCacheExt(Set<DirScan.Options> options) {
		if (options.contains(DirScan.Options.IS_DEST)) {
			return getCacheExtDest(options);
		} else {
			if (options.contains(DirScan.Options.ARCHIVES_AND_CHD_AS_ROMS)) {
				if (options.contains(DirScan.Options.RECURSE))
					return ".rascache"; //$NON-NLS-1$
				return ".ascache"; //$NON-NLS-1$
			}
			if (options.contains(DirScan.Options.RECURSE))
				return ".rscache"; //$NON-NLS-1$
			return ".scache"; //$NON-NLS-1$
		}
	}

	private static String getCacheExtDest(Set<DirScan.Options> options) {
		if (options.contains(DirScan.Options.ARCHIVES_AND_CHD_AS_ROMS)) {
			if (options.contains(DirScan.Options.RECURSE))
				return ".radcache"; //$NON-NLS-1$
			return ".adcache"; //$NON-NLS-1$
		}
		if (options.contains(DirScan.Options.RECURSE))
			return ".rdcache"; //$NON-NLS-1$
		return ".dcache"; //$NON-NLS-1$
	}

	/**
	 * Computes the cache file matching a directory run.
	 */
	static File getCacheFile(final Session session, final File file, Set<DirScan.Options> options) {
		final var workdir = session.getUser().getSettings().getWorkPath().toFile(); // $NON-NLS-1$
		final var cachedir = new File(workdir, "cache"); //$NON-NLS-1$
		cachedir.mkdirs();
		final var crc = new CRC32();
		crc.update(file.getAbsolutePath().getBytes());
		return new File(cachedir, String.format("%08x", crc.getValue()) + getCacheExt(options)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Serializes current scans properties to the computed cache file with integrity protection.
	 */
	void save(final File file, Set<DirScan.Options> options, Map<String, Container> containersByName) {
		try {
			SignedObjectStore.write(session, getCacheFile(session, file, options), containersByName, SignedObjectStore.Codec.CACHE);
		} catch (final Exception _) {
			// ignore
		}
	}

	/**
	 * Deserializes previous runs properties from disk with integrity verification.
	 */
	@SuppressWarnings("unchecked")
	Map<String, Container> load(final File file, Set<DirScan.Options> options) {
		final var cachefile = getCacheFile(session, file, options);
		try {
			handler.clearInfos();
			handler.setProgress(String.format(Messages.getString("DirScan.LoadingScanCache"), PathAbstractor.getRelativePath(session, file.toPath())), 0); //$NON-NLS-1$
			return (Map<String, Container>) SignedObjectStore.read(session, cachefile, SignedObjectStore.Codec.CACHE);
		} catch (final Exception e) {
			Log.info(() -> "Failed to load cache file: " + cachefile.getAbsolutePath() + " (" + e.getMessage() + ")");
		}
		return Collections.synchronizedMap(new HashMap<>());
	}
}
