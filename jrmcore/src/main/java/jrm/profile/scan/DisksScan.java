/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.scan;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import jrm.profile.data.Anyware;
import jrm.profile.data.Container;
import jrm.profile.data.Directory;
import jrm.profile.data.Disk;
import jrm.profile.data.Entry;
import jrm.profile.fix.actions.AddEntry;
import jrm.profile.fix.actions.ContainerAction;
import jrm.profile.fix.actions.CreateContainer;
import jrm.profile.fix.actions.OpenContainer;
import jrm.profile.report.EntryAdd;
import jrm.profile.report.EntryMissing;
import jrm.profile.report.EntryOK;
import jrm.profile.report.EntryWrongHash;
import jrm.profile.report.SubjectSet;

/**
 * Handles auditing of Disk (CHD) images for a ware.
 * Extracted from Scan to reduce size and separate asset-specific logic.
 */
final class DisksScan {

	private final Scan scan;

	DisksScan(final Scan scan) {
		this.scan = scan;
	}

	boolean scan(final Anyware ware, final List<Disk> disks, final Directory directory, final SubjectSet reportSubject) {
		final Container container = scan.disksDstScan.getContainerByName(ware.getDest().getNormalizedName());
		if (null != container) {
			scanDisksForFoundContainer(disks, directory, reportSubject, container);
			return false;
		} else {
			scanDisksForMissingContainer(disks, directory, reportSubject);
			return true;
		}
	}

	private void scanDisksForMissingContainer(final List<Disk> disks, final Directory directory, final SubjectSet reportSubject) {
		for (final Disk disk : disks)
			disk.setStatus(jrm.profile.data.EntityStatus.KO);
		if (!scan.createMode || disks.isEmpty())
			return;
		int disksFound = 0;
		boolean partialSet = false;
		final var createSet = new AtomicReference<CreateContainer>();
		for (final Disk disk : disks) {
			scan.report.getStats().incMissingDisksCnt();
			Entry foundEntry = searchDiskInAllScans(disk);
			if (foundEntry != null) {
				scan.report.getStats().incFixableDisksCnt();
				reportSubject.add(new EntryAdd(disk, foundEntry));
				CreateContainer.getInstance(createSet, directory, scan.format, 0L).addAction(new AddEntry(disk, foundEntry));
				disksFound++;
			} else {
				reportSubject.add(new EntryMissing(disk));
				partialSet = true;
			}
		}
		if (disksFound > 0 && (!scan.createFullMode || !partialSet)) {
			reportSubject.setCreateFull();
			if (partialSet)
				reportSubject.setCreate();
			ContainerAction.addToList(scan.createActions, createSet.get());
		}
	}

	private Entry searchDiskInAllScans(final Disk disk) {
		for (final DirScan dscan : scan.allScans) {
			final Entry foundEntry = dscan.findByHash(disk);
			if (null != foundEntry)
				return foundEntry;
		}
		return null;
	}

	private void scanDisksForFoundContainer(final List<Disk> disks, final Directory directory, final SubjectSet reportSubject, final Container container) {
		if (disks.isEmpty())
			return;
		reportSubject.setFound();

		final var scanData = new ScanDisksData(disks, container);

		for (final Disk disk : disks) {
			disk.setStatus(jrm.profile.data.EntityStatus.KO);
			Entry foundEntry = Optional.ofNullable(findEntriesByHash(scanData, disk))
					.map(entries -> scanDisksEntries(directory, reportSubject, scanData, disk, entries))
					.orElse(null);

			final Entry wrongHash = foundEntry == null ? checkWrongHash(scanData, disk) : null;

			if (foundEntry == null) {
				scan.report.getStats().incMissingDisksCnt();

				foundEntry = searchDiskInAllScans(disk);
				if (foundEntry != null) {
					scan.report.getStats().incFixableDisksCnt();
					reportSubject.add(new EntryAdd(disk, foundEntry));
					OpenContainer.getInstance(scanData.addSet, directory, scan.format, 0L).addAction(new AddEntry(disk, foundEntry));
				} else
					reportSubject.add(wrongHash != null ? new EntryWrongHash(disk, wrongHash) : new EntryMissing(disk));
			} else {
				disk.setStatus(jrm.profile.data.EntityStatus.OK);
				reportSubject.add(new EntryOK(disk));
				scanData.found.add(foundEntry);
			}
		}

		removeUnneededEntries(directory, reportSubject, container, scanData);
		ContainerAction.addToList(scan.renameBeforeActions, scanData.renameBeforeSet.get());
		ContainerAction.addToList(scan.duplicateActions, scanData.duplicateSet.get());
		ContainerAction.addToList(scan.addActions, scanData.addSet.get());
		ContainerAction.addToList(scan.deleteActions, scanData.deleteSet.get());
		ContainerAction.addToList(scan.renameAfterActions, scanData.renameAfterSet.get());
	}

	private Entry scanDisksEntries(final Directory directory, final SubjectSet reportSubject, final ScanDisksData scanData, final Disk disk, final List<Entry> entries) {
		for (final var candidate_entry : entries) {
			jrm.misc.Log.debug(() -> "The entry " + candidate_entry.getName() + " match hash from disk " + disk.getNormalizedName());
			if (!disk.getNormalizedName().equals(candidate_entry.getName())) {
				if (scanDisksEntriesNameMismatch(directory, reportSubject, scanData, disk, candidate_entry))
					return candidate_entry;
			} else {
				jrm.misc.Log.debug(() -> "\tThe entry " + candidate_entry.getName() + " match hash and name for disk " + disk.getNormalizedName());
				return candidate_entry;
			}
		}
		return null;
	}

	private void removeUnneededEntries(final Directory directory, final SubjectSet reportSubject, final Container container, final ScanDisksData data) {
		if (!scan.ignoreUnneededEntries) {
			final List<Entry> unneeded = container.getEntries().stream().filter(Scan.not(new HashSet<>(data.found)::contains)).toList();
			for (final Entry unneeded_entry : unneeded) {
				reportSubject.add(new jrm.profile.report.EntryUnneeded(unneeded_entry));
				OpenContainer.getInstance(data.renameBeforeSet, directory, scan.format, 0L).addAction(new jrm.profile.fix.actions.RenameEntry(unneeded_entry));
				OpenContainer.getInstance(data.deleteSet, directory, scan.format, 0L).addAction(new jrm.profile.fix.actions.DeleteEntry(unneeded_entry));
			}
		}
	}

	@SuppressWarnings("unlikely-arg-type")
	private boolean scanDisksEntriesNameMismatch(final Directory directory, final SubjectSet reportSubject, final ScanDisksData data, final Disk disk, final Entry candidateEntry) {
		jrm.misc.Log.debug(() -> "\tbut this disk name does not match the disk name");
		final Disk anotherDisk = data.disksByName.get(candidateEntry.getName());
		if (null != anotherDisk && candidateEntry.equals(anotherDisk)) // NOSONAR
		{
			if (scanDisksEntriesNameRetrieved(directory, reportSubject, data, disk, candidateEntry))
				return true;
		} else {
			if (anotherDisk == null)
				jrm.misc.Log.debug(() -> "\t" + candidateEntry.getName() + " in disksByName not found (" + data.disksByName.keySet().stream().collect(java.util.stream.Collectors.joining(", ")) + ")");
			else
				jrm.misc.Log.debug(() -> "\t" + candidateEntry.getName() + " in disksByName found but does not match hash");
			if (!data.entriesByName.containsKey(disk.getNormalizedName())) {
				jrm.misc.Log.debug(() -> "\t\tand disk " + disk.getNormalizedName() + " is NOT in the entriesByName ("
						+ data.entriesByName.keySet().stream().collect(java.util.stream.Collectors.joining(", ")) + ")");
				if (!data.markedForRename.contains(candidateEntry))
					scan.scanRename(directory, reportSubject, 0L, data, disk, candidateEntry);
				else
					scan.scanDuplicate(directory, reportSubject, 0L, data, disk, candidateEntry);
				return true;
			}
		}
		return false;
	}

	private boolean scanDisksEntriesNameRetrieved(final Directory directory, final SubjectSet reportSubject, final ScanDisksData data, final Disk disk,
			final Entry candidateEntry) {
		jrm.misc.Log.debug(() -> "\t\t\tand the entry " + candidateEntry.getName() + " is ANOTHER disk");
		if (data.entriesByName.containsKey(disk.getNormalizedName())) {
			jrm.misc.Log.debug(() -> String.format("\t\t\t\tand disk %s is in the entriesByName", disk.getNormalizedName()));
		} else {
			jrm.misc.Log.debug(() -> "\\t\\t\\t\\twe must duplicate disk " + disk.getNormalizedName() + " to ");
			scan.scanDuplicate(directory, reportSubject, 0L, data, disk, candidateEntry);
			return true;
		}
		return false;
	}

	private Entry checkWrongHash(final ScanDisksData scanData, final Disk disk) {
		final var candidateEntry = scanData.entriesByName.get(disk.getNormalizedName());
		if (candidateEntry != null) {
			jrm.misc.Log.debug(() -> "\tOups! we got wrong hash in " + candidateEntry.getName() + " for " + disk.getNormalizedName());
			return candidateEntry;
		}
		return null;
	}

	private List<Entry> findEntriesByHash(final ScanDisksData scanData, final Disk disk) {
		List<Entry> entries = null;
		if (disk.getSha1() != null)
			entries = scanData.entriesBySha1.get(disk.getSha1());
		if (entries == null && disk.getMd5() != null)
			entries = scanData.entriesByMd5.get(disk.getMd5());
		if (entries == null && disk.getCrc() != null)
			entries = scanData.entriesByCrc.get(disk.getCrc() + '.' + disk.getSize());
		return entries;
	}
}
