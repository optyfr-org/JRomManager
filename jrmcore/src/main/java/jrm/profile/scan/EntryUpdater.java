/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.scan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jrm.compressors.ZipTools;
import jrm.digest.MDigest;
import jrm.digest.MDigest.Algo;
import jrm.io.chd.CHDInfoReader;
import jrm.misc.Log;
import jrm.profile.data.Container.Type;
import jrm.profile.data.Entry;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

/**
 * Handles updating Entry metadata (size/crc + optional hashes) and indexing into the scanner's lookup maps.
 * Extracted to keep DirScan smaller.
 */
final class EntryUpdater {

	private final DirScan ds;

	EntryUpdater(DirScan ds) {
		this.ds = ds;
	}

	void updateEntry(Entry entry, ZipFile zipf, FileHeader hdr, ScanOptions options) {
		if (entry.getSize() == 0 && entry.getCrc() == null) {
			entry.setSize(hdr.getUncompressedSize()); // $NON-NLS-1$
			entry.setCrc(String.format("%08x", hdr.getCrc())); //$NON-NLS-1$ //$NON-NLS-2$
		}
		ds.entriesByCrc.put(entry.getCrc() + "." + entry.getSize(), entry); //$NON-NLS-1$
		if (options.needSha1OrMd5 || entry.getCrc() == null || ds.isSuspiciousCRC(entry.getCrc())) {
			List<Algo> algorithms = getAlgorithms(entry, options);
			if (!algorithms.isEmpty())
				try {
					if (hdr == null)
						hdr = zipf.getFileHeader(ZipTools.toZipEntry(entry.getFile()));
					MDigest[] digests = HashComputer.computeHash(zipf.getInputStream(hdr), algorithms);
					updateEntryFromHashes(entry, digests);
				} catch (IOException | NoSuchAlgorithmException e) {
					Log.err(e.getMessage(), e);
				}
		}
		updateHashesFromEntry(entry);
	}

	void updateEntryFromHashes(Entry entry, MDigest[] digests) {
		for (MDigest md : digests) {
			switch (md.getAlgorithm()) {
				case CRC32: // $NON-NLS-1$
					entry.setCrc(md.toString());
					break;
				case MD5: // $NON-NLS-1$
					entry.setMd5(md.toString());
					break;
				case SHA1: // $NON-NLS-1$
					entry.setSha1(md.toString());
					break;
			}
		}
	}

	void updateEntry(final Entry entry, ScanOptions options) throws IOException {
		updateEntry(entry, (Path) null, options);
	}

	void updateEntry(final Entry entry, final Path entryPath, ScanOptions options) throws IOException {
		if (entry.getParent().getType() == Type.ZIP) {
			updatEntryZip(entry, entryPath);
		}
		if (entry.getType() == Entry.Type.CHD && entry.getSha1() == null && entry.getMd5() == null) {
			updateEntryCHD(entry, entryPath, options);
		} else if (entry.getType() != Entry.Type.CHD && (options.needSha1OrMd5 || entry.getCrc() == null || ds.isSuspiciousCRC(entry.getCrc()))) {
			updateEntryExt(entry, entryPath, options);
		} else {
			updateHashesFromEntry(entry);
		}
	}

	void updateEntryExt(final Entry entry, final Path entryPath, ScanOptions options) throws IOException {
		List<Algo> algorithms = getAlgorithms(entry, options);
		updateEntryExt(entry, entryPath, algorithms);
		updateHashesFromEntry(entry);
	}

	void updateHashesFromEntry(final Entry entry) {
		if (entry.getCrc() != null)
			ds.entriesByCrc.put(entry.getCrc() + "." + entry.getSize(), entry); //$NON-NLS-1$
		if (entry.getSha1() != null)
			ds.entriesBySha1.put(entry.getSha1(), entry);
		if (entry.getMd5() != null)
			ds.entriesByMd5.put(entry.getMd5(), entry);
	}

	private List<Algo> getAlgorithms(final Entry entry, ScanOptions options) {
		List<Algo> algorithms = new ArrayList<>();
		if (entry.getCrc() == null)
			algorithms.add(Algo.CRC32); // $NON-NLS-1$
		if (entry.getMd5() == null && (options.md5Roms || options.needSha1OrMd5))
			algorithms.add(Algo.MD5); // $NON-NLS-1$
		if (entry.getSha1() == null && (options.sha1Roms || options.needSha1OrMd5))
			algorithms.add(Algo.SHA1); // $NON-NLS-1$
		return algorithms;
	}

	void updateEntryExt(final Entry entry, final Path entryPath, List<Algo> algorithms) throws IOException {
		if (!algorithms.isEmpty())
			try (var owned = OwnedPath.of(entry, entryPath)) {
				MDigest[] digests = HashComputer.computeHash(owned.path(), algorithms);
				updateEntryFromHashes(entry, digests);
			} catch (NoSuchAlgorithmException e) {
				Log.err(e.getMessage(), e);
			}
	}

	void updateEntryCHD(final Entry entry, final Path entryPath, ScanOptions options) throws IOException {
		try (var owned = OwnedPath.of(entry, entryPath)) {
			final var chdInfo = new CHDInfoReader(owned.path().toFile());
			if (options.sha1Disks) {
				entry.setSha1(chdInfo.getSHA1());
				if (null != entry.getSha1())
					ds.entriesBySha1.put(entry.getSha1(), entry);
			}
			if (options.md5Disks) {
				entry.setMd5(chdInfo.getMD5());
				if (null != entry.getMd5())
					ds.entriesByMd5.put(entry.getMd5(), entry);
			}
		}
	}

	void updatEntryZip(final Entry entry, final Path entryPath) throws IOException {
		if (entry.getSize() == 0 && entry.getCrc() == null) {
			try (var owned = OwnedPath.of(entry, entryPath)) {
				final Map<String, Object> entryZipAttrs = Files.readAttributes(owned.path(), "zip:*"); //$NON-NLS-1$
				entry.setSize((Long) entryZipAttrs.get("size")); //$NON-NLS-1$
				entry.setCrc(String.format("%08x", entryZipAttrs.get("crc"))); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		ds.entriesByCrc.put(entry.getCrc() + "." + entry.getSize(), entry); //$NON-NLS-1$
	}
}
