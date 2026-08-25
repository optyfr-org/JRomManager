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
import java.util.concurrent.atomic.AtomicReference;

import jrm.profile.data.Anyware;
import jrm.profile.data.Container;
import jrm.profile.data.Entry;
import jrm.profile.data.Rom;
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
 * Handles auditing of ROM sets for a ware.
 * Extracted from Scan to reduce size and separate asset-specific logic.
 */
final class RomsScan {

	private final Scan scan;

	RomsScan(final Scan scan) {
		this.scan = scan;
	}

	boolean scan(final Anyware ware, final List<Rom> roms, final Container archive, final SubjectSet reportSubject) {
		final long estimatedRomsSize = roms.stream().mapToLong(Rom::getSize).sum();

		final Container container = scan.romsDstScan.getContainerByName(ware.getDest().getNormalizedName() + scan.format.getExt());
		if (null != container) {
			scanRomsForFoundContainer(roms, archive, reportSubject, container, estimatedRomsSize);
			return false;
		} else {
			scanRomsForMissingContainer(roms, archive, reportSubject, estimatedRomsSize);
			return true;
		}
	}

	private void scanRomsForFoundContainer(final List<Rom> roms, final Container archive, final SubjectSet reportSubject, final Container container, final long estimatedRomsSize) {
		if (roms.isEmpty())
			return;
		reportSubject.setFound();

		final var scanData = new ScanRomsData(roms, container);

		for (final Rom rom : roms) {
			rom.setStatus(jrm.profile.data.EntityStatus.KO);

			final var entries = findEntriesByHash(scanData, rom);
			Entry foundEntry = entries != null ? scanRomsEntries(archive, reportSubject, estimatedRomsSize, scanData, rom, entries) : null;

			final Entry wrongHash = foundEntry == null ? checkWrongHash(scanData, rom) : null;

			if (foundEntry == null) {
				scan.report.getStats().incMissingRomsCnt();

				foundEntry = searchRomInAllScans(rom);
				if (foundEntry != null) {
					scan.report.getStats().incFixableRomsCnt();
					reportSubject.add(new EntryAdd(rom, foundEntry));
					OpenContainer.getInstance(scanData.addSet, archive, scan.format, estimatedRomsSize).addAction(new AddEntry(rom, foundEntry));
				} else
					reportSubject.add(wrongHash != null ? new EntryWrongHash(rom, wrongHash) : new EntryMissing(rom));
			} else {
				rom.setStatus(jrm.profile.data.EntityStatus.OK);
				reportSubject.add(new EntryOK(rom));
				scanData.found.add(foundEntry);
			}
		}

		removeUnneededEntries(archive, reportSubject, container, estimatedRomsSize, scanData);

		ContainerAction.addToList(scan.backupActions, scanData.backupSet.get());
		ContainerAction.addToList(scan.renameBeforeActions, scanData.renameBeforeSet.get());
		ContainerAction.addToList(scan.duplicateActions, scanData.duplicateSet.get());
		ContainerAction.addToList(scan.addActions, scanData.addSet.get());
		ContainerAction.addToList(scan.deleteActions, scanData.deleteSet.get());
		ContainerAction.addToList(scan.renameAfterActions, scanData.renameAfterSet.get());
	}

	private Entry checkWrongHash(final ScanRomsData scanData, final Rom rom) {
		final var candidateEntry = scanData.entriesByName.get(rom.getNormalizedName());
		if (candidateEntry != null) {
			jrm.misc.Log.debug(() -> "\tOups! we got wrong hash in " + candidateEntry.getName() + " for " + rom.getNormalizedName());
			return candidateEntry;
		}
		return null;
	}

	private void removeUnneededEntries(final Container archive, final SubjectSet reportSubject, final Container container, final long estimatedRomsSize,
			final ScanRomsData scanData) {
		if (!scan.ignoreUnneededEntries) {
			final List<Entry> unneeded = container.getEntries().stream().filter(Scan.not(new HashSet<>(scanData.found)::contains)).toList();
			for (final Entry unneeded_entry : unneeded) {
				reportSubject.add(new jrm.profile.report.EntryUnneeded(unneeded_entry));
				jrm.profile.fix.actions.BackupContainer.getInstance(scanData.backupSet, archive).addAction(new jrm.profile.fix.actions.BackupEntry(unneeded_entry));
				OpenContainer.getInstance(scanData.renameBeforeSet, archive, scan.format, estimatedRomsSize).addAction(new jrm.profile.fix.actions.RenameEntry(unneeded_entry));
				OpenContainer.getInstance(scanData.deleteSet, archive, scan.format, estimatedRomsSize).addAction(new jrm.profile.fix.actions.DeleteEntry(unneeded_entry));
			}
		}
	}

	private Entry scanRomsEntries(final Container archive, final SubjectSet reportSubject, final long estimatedRomsSize, final ScanRomsData scanData, final Rom rom,
			final List<Entry> entries) {
		for (final var candidate_entry : entries) {
			jrm.misc.Log.debug(() -> "The entry " + candidate_entry.getName() + " match hash from rom " + rom.getNormalizedName());
			if (!rom.getNormalizedName().equals(candidate_entry.getName())) {
				if (scanRomsEntriesNameMismatch(archive, reportSubject, estimatedRomsSize, scanData, rom, candidate_entry))
					return candidate_entry;
			} else {
				jrm.misc.Log.debug(() -> "\tThe entry " + candidate_entry.getName() + " match hash and name for rom " + rom.getNormalizedName());
				return candidate_entry;
			}
		}
		return null;
	}

	@SuppressWarnings("unlikely-arg-type")
	private boolean scanRomsEntriesNameMismatch(final Container archive, final SubjectSet reportSubject, final long estimatedRomsSize, final ScanRomsData scanData, final Rom rom,
			final Entry candidateEntry) {
		jrm.misc.Log.debug(() -> "\tbut this entry name does not match the rom name");
		final Rom anotherRom = scanData.romsByName.get(candidateEntry.getName());
		if (null != anotherRom && candidateEntry.equals(anotherRom)) // NOSONAR
		{
			if (scanRomsEntriesNameRetrieved(archive, reportSubject, estimatedRomsSize, scanData, rom, candidateEntry))
				return true;
		} else {
			if (anotherRom == null)
				jrm.misc.Log.debug(() -> "\t" + candidateEntry.getName() + " in romsByName not found (" + scanData.romsByName.keySet().stream().collect(java.util.stream.Collectors.joining(", ")) + ")");
			else
				jrm.misc.Log.debug(() -> "\t" + candidateEntry.getName() + " in romsByName found but does not match hash");

			if (!scanData.entriesByName.containsKey(rom.getNormalizedName())) {
				jrm.misc.Log.debug(() -> "\t\tand rom " + rom.getNormalizedName() + " is NOT in the entriesByName ("
						+ scanData.entriesByName.keySet().stream().collect(java.util.stream.Collectors.joining(", ")) + ")");

				if (!scanData.markedForRename.contains(candidateEntry))
					scan.scanRename(archive, reportSubject, estimatedRomsSize, scanData, rom, candidateEntry);
				else
					scan.scanDuplicate(archive, reportSubject, estimatedRomsSize, scanData, rom, candidateEntry);
				return true;
			} else
				jrm.misc.Log.debug(() -> "\t\tand rom " + rom.getNormalizedName() + " is in the entriesByName");
		}
		return false;
	}

	private boolean scanRomsEntriesNameRetrieved(final Container archive, final SubjectSet reportSubject, final long estimatedRomsSize, final ScanRomsData scanData, final Rom rom,
			final Entry candidateEntry) {
		jrm.misc.Log.debug(() -> "\t\t\tand the entry " + candidateEntry.getName() + " is ANOTHER rom");
		if (scanData.entriesByName.containsKey(rom.getNormalizedName()))
			jrm.misc.Log.debug(() -> String.format("\t\t\t\tand rom %s is in the entriesByName", rom.getNormalizedName()));
		else {
			jrm.misc.Log.debug(() -> "\\t\\t\\t\\twe must duplicate rom " + rom.getNormalizedName() + " to ");
			scan.scanDuplicate(archive, reportSubject, estimatedRomsSize, scanData, rom, candidateEntry);
			return true;
		}
		return false;
	}

	private void scanRomsForMissingContainer(final List<Rom> roms, final Container archive, final SubjectSet reportSubject, final long estimatedRomsSize) {
		for (final Rom rom : roms)
			rom.setStatus(jrm.profile.data.EntityStatus.KO);
		if (!scan.createMode || roms.isEmpty())
			return;
		int romsFound = 0;
		boolean partialSet = false;
		final var createSet = new AtomicReference<CreateContainer>();
		for (final Rom rom : roms) {
			scan.report.getStats().incMissingRomsCnt();
			final Entry entryFound = searchRomInAllScans(rom);
			if (null != entryFound) {
				scan.report.getStats().incFixableRomsCnt();
				reportSubject.add(new EntryAdd(rom, entryFound));
				CreateContainer.getInstance(createSet, archive, scan.format, estimatedRomsSize).addAction(new AddEntry(rom, entryFound));
				romsFound++;
			} else {
				reportSubject.add(new EntryMissing(rom));
				partialSet = true;
			}
		}
		if (romsFound > 0 && (!scan.createFullMode || !partialSet)) {
			reportSubject.setCreateFull();
			if (partialSet)
				reportSubject.setCreate();
			ContainerAction.addToList(scan.createActions, createSet.get());
		}
	}

	private Entry searchRomInAllScans(final Rom rom) {
		for (final DirScan dscan : scan.allScans) {
			final var foundEntry = dscan.findByHash(rom);
			if (null != foundEntry)
				return foundEntry;
		}
		return null;
	}

	private List<Entry> findEntriesByHash(final ScanRomsData scanData, final Rom rom) {
		List<Entry> entries = null;
		if (rom.getSha1() != null)
			entries = scanData.entriesBySha1.get(rom.getSha1());
		if (entries == null && rom.getMd5() != null)
			entries = scanData.entriesByMd5.get(rom.getMd5());
		if (entries == null && rom.getCrc() != null)
			entries = scanData.entriesByCrc.get(rom.getCrc() + '.' + rom.getSize());
		return entries;
	}
}
