/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.scan;

import java.io.Closeable;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import jrm.compressors.SevenZipArchive;
import jrm.digest.MDigest;
import jrm.digest.MDigest.Algo;
import jrm.profile.data.Container;
import jrm.profile.data.Entry;
import net.sf.sevenzipjbinding.ExtractAskMode;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IArchiveExtractCallback;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.simple.ISimpleInArchive;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;
import one.util.streamex.IntStreamEx;

/**
 * Helper class wrapping SevenZip JBinding extraction callbacks to calculate hashes for 7-zip or RAR archive formats in
 * parallel.
 */
class SevenZUpdateEntries implements Closeable {
	/**
	 * Callback implementing extraction operations of the native 7-zip binding library.
	 */
	private final class ComputeHashes7ZipCallback implements IArchiveExtractCallback {
		/**
		 * Map containing target entries indexed by integer sequential ID.
		 */
		private final Map<Integer, Entry> entries;
		/**
		 * Currently handled file entry reference.
		 */
		Entry entry;

		/**
		 * Instantiates a new callback listener.
		 * 
		 * @param entries map of registered entries
		 */
		private ComputeHashes7ZipCallback(Map<Integer, Entry> entries) {
			this.entries = entries;
		}

		@Override
		public void setTotal(final long total) throws SevenZipException {
			// unused
		}

		@Override
		public void setCompleted(final long complete) throws SevenZipException {
			// unused
		}

		@Override
		public void setOperationResult(final ExtractOperationResult extractOperationResult) throws SevenZipException {
			if (extractOperationResult == ExtractOperationResult.OK) {
				for (final MDigest d : digest) {
					if (d.getAlgorithm() == Algo.SHA1) // $NON-NLS-1$
					{
						entry.setSha1(d.toString());
						dirScan.entriesBySha1.put(entry.getSha1(), entry);
					}
					if (d.getAlgorithm() == Algo.MD5) // $NON-NLS-1$
					{
						entry.setMd5(d.toString());
						dirScan.entriesByMd5.put(entry.getMd5(), entry);
					}
					d.reset();
				}
			}
		}

		@Override
		public void prepareOperation(final ExtractAskMode extractAskMode) throws SevenZipException {
			// unused
		}

		@Override
		public ISequentialOutStream getStream(final int index, final ExtractAskMode extractAskMode) throws SevenZipException {
			entry = entries.get(index);
			if (extractAskMode != ExtractAskMode.EXTRACT)
				return null;
			return data -> {
				for (final MDigest d : digest)
					d.update(data);
				return data.length;
			};
		}
	}

	/** Back-reference to owning DirScan for shared indexes, session and suspicious checks. */
	private final DirScan dirScan;

	/**
	 * The container to read.
	 */
	private final Container container;
	/**
	 * Hashing algorithms requested for digest calculations.
	 */
	private final ArrayList<Algo> algorithms;
	/**
	 * Message digest structures.
	 */
	private final MDigest[] digest;
	/**
	 * SevenZFile instance utilizing apache commons compress routines.
	 */
	private SevenZFile cArchive = null;
	/**
	 * SevenZipArchive instance representing standard command executors.
	 */
	private SevenZipArchive archive = null;

	/**
	 * Scanning option configuration metrics.
	 */
	private final ScanOptions options;

	/**
	 * Instantiates a new multi-format archive worker.
	 * 
	 * @param dirScan the parent scanner (for shared state)
	 * @param container the parent file container
	 * @param options options configurations
	 * 
	 * @throws NoSuchAlgorithmException if digest libraries are missing
	 */
	SevenZUpdateEntries(final DirScan dirScan, final Container container, ScanOptions options) throws NoSuchAlgorithmException {
		this.dirScan = dirScan;
		this.container = container;
		this.options = options;
		algorithms = new ArrayList<>();
		if (options.sha1Roms)
			algorithms.add(Algo.SHA1); // $NON-NLS-1$
		if (options.md5Roms)
			algorithms.add(Algo.MD5); // $NON-NLS-1$
		digest = new MDigest[algorithms.size()];
		for (var i = 0; i < algorithms.size(); i++)
			digest[i] = MDigest.getAlgorithm(algorithms.get(i));
	}

	@Override
	public void close() throws IOException {
		if (archive != null)
			archive.close();
		if (cArchive != null)
			cArchive.close();
	}

	/**
	 * Obtains the apache SevenZFile stream helper.
	 * 
	 * @return the commons compress reader instance
	 * 
	 * @throws IOException if files cannot be opened
	 */
	@SuppressWarnings("deprecation")
	private SevenZFile getCArchive() throws IOException {
		if (cArchive == null)
			cArchive = new SevenZFile(container.getFile());
		return cArchive;
	}

	/**
	 * Obtains the JBinding native archive stream helper.
	 * 
	 * @return the archive abstraction wrapper
	 * 
	 * @throws IOException if native libraries cannot load files
	 */
	private SevenZipArchive getArchive() throws IOException {
		if (archive == null)
			archive = new SevenZipArchive(dirScan.session, container.getFile());
		return archive;
	}

	/**
	 * Gets a simple native JBinding interface.
	 * 
	 * @return the JBinding simpler operations interface
	 * 
	 * @throws IOException if native stream mapping fails
	 */
	private ISimpleInArchive getNInterface() throws IOException {
		return getArchive().getNative7Zip().getIInArchive().getSimpleInterface();
	}

	/**
	 * Analyzes and registers all entries inside a 7-zip or RAR format archive container.
	 * 
	 * @throws IOException if reading operations fail
	 */
	void updateEntries() throws IOException {
		if (SevenZip.isInitializedSuccessfully()) {
			updateEntries7ZipJBindingMethod();
		} else {
			updateEntriesFallbackMethod();
		}
	}

	/**
	 * Employs native sevenzipjbinding calls to scan files and compute checksums.
	 * 
	 * @throws IOException if streams fail
	 */
	private void updateEntries7ZipJBindingMethod() throws IOException {
		final Map<Integer, Entry> entries = new HashMap<>();
		if (container.getLoaded() < 1 || (options.needSha1OrMd5 && container.getLoaded() < 2)) {
			for (final ISimpleInArchiveItem item : getNInterface().getArchiveItems()) {
				if (item.isFolder())
					continue;
				updateEntry(container.add(new Entry(item.getPath(), null)), entries, item);

			}
			container.setLoaded(options.needSha1OrMd5 ? 2 : 1);
		} else {
			final Map<String, ISimpleInArchiveItem> pathToItem = new HashMap<>();
			for (final ISimpleInArchiveItem itm : getNInterface().getArchiveItems())
				if (!itm.isFolder())
					pathToItem.put(itm.getPath(), itm);
			for (final Entry entry : container.getEntries())
				updateEntry(entry, entries, pathToItem.get(entry.getFile()));
		}
		computeHashes(entries);
	}

	/**
	 * Employs standard apache commons libraries to walk archives and compute hashes.
	 * 
	 * @throws IOException if file access fails
	 */
	private void updateEntriesFallbackMethod() throws IOException {
		final HashMap<String, Entry> entries = new HashMap<>();
		if (container.getLoaded() < 1 || (options.needSha1OrMd5 && container.getLoaded() < 2)) {
			for (final SevenZArchiveEntry archive_entry : getCArchive().getEntries()) {
				if (archive_entry.isDirectory())
					continue;
				updateEntry(container.add(new Entry(archive_entry.getName(), null)), entries, archive_entry);
			}
			container.setLoaded(options.needSha1OrMd5 ? 2 : 1);
		} else {
			for (final Entry entry : container.getEntries())
				updateEntry(entry, entries, (SevenZArchiveEntry) null);
		}
		computeHashes(entries);
	}

	/**
	 * Updates an entry structure from native JBinding item descriptors.
	 * 
	 * @param entry the target entry details
	 * @param entries map tracking entries by their integer index
	 * @param item the native file reference
	 * 
	 * @throws IOException if streams fail
	 */
	private void updateEntry(final Entry entry, final Map<Integer, Entry> entries, ISimpleInArchiveItem item) throws IOException {
		if (entry.getSize() == 0 && entry.getCrc() == null && item != null) {
			entry.setSize(item.getSize());
			entry.setCrc(String.format("%08x", item.getCRC())); //$NON-NLS-1$
		}
		dirScan.entriesByCrc.put(entry.getCrc() + "." + entry.getSize(), entry); //$NON-NLS-1$
		if (entry.getSha1() == null && entry.getMd5() == null && (options.needSha1OrMd5 || entry.getCrc() == null || dirScan.isSuspiciousCRC(entry.getCrc()))) {
			updateEntryExt(entry, entries, item);
		} else {
			if (entry.getSha1() != null)
				dirScan.entriesBySha1.put(entry.getSha1(), entry);
			if (entry.getMd5() != null)
				dirScan.entriesByMd5.put(entry.getMd5(), entry);
		}
	}

	/**
	 * Handles extended property updating operations.
	 * 
	 * @param entry the target entry details
	 * @param entries map tracking entries by their integer index
	 * @param item the native file reference
	 * 
	 * @throws IOException if streams fail
	 */
	private void updateEntryExt(final Entry entry, final Map<Integer, Entry> entries, ISimpleInArchiveItem item) throws IOException {
		if (item == null) {
			for (final ISimpleInArchiveItem itm : getNInterface().getArchiveItems()) {
				if (entry.getFile().equals(itm.getPath())) {
					item = itm;
					break;
				}
			}

		}
		if (item != null)
			entries.put(item.getItemIndex(), entry);
	}

	/**
	 * Updates an entry structure using commons compress descriptors.
	 * 
	 * @param entry the target entry details
	 * @param entries map containing entries indexed by filename
	 * @param archiveEntry the commons compress descriptor
	 */
	private void updateEntry(final Entry entry, final Map<String, Entry> entries, final SevenZArchiveEntry archiveEntry) {
		if (entry.getSize() == 0 && entry.getCrc() == null && archiveEntry != null) {
			entry.setSize(archiveEntry.getSize());
			entry.setCrc(String.format("%08x", archiveEntry.getCrcValue())); //$NON-NLS-1$
		}
		dirScan.entriesByCrc.put(entry.getCrc() + "." + entry.getSize(), entry); //$NON-NLS-1$
		if (entry.getSha1() == null && entry.getMd5() == null && (options.needSha1OrMd5 || entry.getCrc() == null || dirScan.isSuspiciousCRC(entry.getCrc()))) {
			entries.put(entry.getFile(), entry);
		} else {
			if (entry.getSha1() != null)
				dirScan.entriesBySha1.put(entry.getSha1(), entry);
			if (entry.getMd5() != null)
				dirScan.entriesByMd5.put(entry.getMd5(), entry);
		}

	}

	/**
	 * Performs native extract commands to parallel-process and update missing hashes.
	 * 
	 * @param entries mapped index registers of items to update
	 * 
	 * @throws IOException if reading operations fail
	 */
	private void computeHashes(final Map<Integer, Entry> entries) throws IOException {
		if (entries.size() > 0) {
			getArchive().getNative7Zip().getIInArchive().extract(IntStreamEx.of(entries.keySet()).toArray(), false, new ComputeHashes7ZipCallback(entries));
		}
	}

	/**
	 * Walks commons compress zip elements sequentially to compute missing hashes.
	 * 
	 * @param entries registered files to hash
	 * 
	 * @throws IOException if reading operations fail
	 */
	private void computeHashes(final HashMap<String, Entry> entries) throws IOException {
		SevenZArchiveEntry entry7z;
		Entry entry;
		while (null != (entry7z = getCArchive().getNextEntry())) {
			if (null != (entry = entries.get(entry7z.getName()))) {
				computeHashes(entry7z.getSize());
				for (MDigest d : digest) {
					if (d.getAlgorithm() == Algo.SHA1) // $NON-NLS-1$
					{
						entry.setSha1(d.toString());
						dirScan.entriesBySha1.put(entry.getSha1(), entry);
					}
					if (d.getAlgorithm() == Algo.MD5) // $NON-NLS-1$
					{
						entry.setMd5(d.toString());
						dirScan.entriesByMd5.put(entry.getMd5(), entry);
					}
					d.reset();
				}
			}
		}
	}

	/**
	 * Pulls data chunks from SevenZFile streams to update digests.
	 * 
	 * @param size the size of the entry data
	 * 
	 * @throws IOException if streams fail
	 */
	private void computeHashes(long size) throws IOException {
		final var buffer = new byte[8192];
		while (size > 0) {
			int read = getCArchive().read(buffer, 0, (int) Math.min(buffer.length, size));
			if (read == -1)
				break;
			for (MDigest d : digest)
				d.update(buffer, 0, read);
			size -= read;
		}
	}
}
