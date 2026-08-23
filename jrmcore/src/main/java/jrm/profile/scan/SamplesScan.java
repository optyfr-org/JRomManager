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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jrm.profile.data.Archive;
import jrm.profile.data.Container;
import jrm.profile.data.Directory;
import jrm.profile.data.Entry;
import jrm.profile.data.Sample;
import jrm.profile.data.Samples;
import jrm.profile.fix.actions.AddEntry;
import jrm.profile.fix.actions.ContainerAction;
import jrm.profile.fix.actions.CreateContainer;
import jrm.profile.fix.actions.DeleteEntry;
import jrm.profile.fix.actions.OpenContainer;
import jrm.profile.fix.actions.RenameEntry;
import jrm.profile.report.EntryAdd;
import jrm.profile.report.EntryMissing;
import jrm.profile.report.EntryOK;
import jrm.profile.report.EntryUnneeded;
import jrm.profile.report.SubjectSet;
import jrm.profile.report.SubjectSet.Status;
import jrm.profile.scan.options.FormatOptions;

/**
 * Handles auditing of Samples (audio) sets.
 * Extracted from Scan to reduce size and separate asset-specific logic.
 */
final class SamplesScan {

	private final Scan scan;

	SamplesScan(final Scan scan) {
		this.scan = scan;
	}

	void scan(final Samples set) {
		boolean missingSet = true;
		final Container archive;
		if (scan.format.getExt().isDir()) {
			final var f = new File(scan.samplesDstScan.getDir(), set.getName());
			archive = new Directory(f, scan.getRelativePath(f), set);
		} else {
			final var f = new File(scan.samplesDstScan.getDir(), set.getName() + scan.format.getExt());
			archive = new Archive(f, scan.getRelativePath(f), set);
		}
		final SubjectSet reportSubject = new SubjectSet(set);

		if (!scanSamples(set, archive, reportSubject))
			missingSet = false;
		if (scan.createMode && reportSubject.getStatus() == Status.UNKNOWN)
			reportSubject.setMissing();
		if (missingSet)
			scan.report.getStats().incMissingSetCnt();
		if (reportSubject.getStatus() != Status.UNKNOWN)
			scan.report.add(reportSubject);
		scan.prepTZip(reportSubject, archive, set);
	}

	private boolean scanSamples(final Samples set, final Container archive, final SubjectSet reportSubject) {
		final Container container = scan.samplesDstScan.getContainerByName(archive.getFile().getName());
		if (null != container) {
			scanSamplesForFoundContainer(set, archive, reportSubject, container);
			return false;
		} else {
			scanSamplesForMissingContainer(set, archive, reportSubject);
			return true;
		}
	}

	private void scanSamplesForFoundContainer(final Samples set, final Container archive, final SubjectSet reportSubject, final Container container) {
		reportSubject.setFound();

		final var data = new ScanSamplesData(container);

		for (final Sample sample : set) {
			sample.setStatus(jrm.profile.data.EntityStatus.KO);
			Entry foundEntry = scanSamplesEntries(container, sample);
			if (foundEntry == null) {
				scan.report.getStats().incMissingSamplesCnt();
				foundEntry = searchSampleInAllScans(set, sample);
				if (foundEntry != null) {
					reportSubject.add(new EntryAdd(sample, foundEntry));
					OpenContainer.getInstance(data.addSet, archive, scan.format, Long.MAX_VALUE).addAction(new AddEntry(sample, foundEntry));
				} else
					reportSubject.add(new EntryMissing(sample));
			} else {
				sample.setStatus(jrm.profile.data.EntityStatus.OK);
				reportSubject.add(new EntryOK(sample));
				data.found.add(foundEntry);
			}
		}
		removeUnneededEntries(archive, reportSubject, container, data);

		ContainerAction.addToList(scan.renameBeforeActions, data.renameBeforeSet.get());
		ContainerAction.addToList(scan.duplicateActions, data.duplicateSet.get());
		ContainerAction.addToList(scan.addActions, data.addSet.get());
		ContainerAction.addToList(scan.deleteActions, data.deleteSet.get());
		ContainerAction.addToList(scan.renameAfterActions, data.renameAfterSet.get());
	}

	private void removeUnneededEntries(final Container archive, final SubjectSet reportSubject, final Container container, final ScanSamplesData data) {
		if (!scan.ignoreUnneededEntries) {
			final List<Entry> unneeded = container.getEntries().stream().filter(Scan.not(new HashSet<>(data.found)::contains)).toList();
			for (final Entry unneededEntry : unneeded) {
				reportSubject.add(new EntryUnneeded(unneededEntry));
				OpenContainer.getInstance(data.renameBeforeSet, archive, scan.format, Long.MAX_VALUE).addAction(new RenameEntry(unneededEntry));
				OpenContainer.getInstance(data.deleteSet, archive, scan.format, Long.MAX_VALUE).addAction(new DeleteEntry(unneededEntry));
			}
		}
	}

	@SuppressWarnings("unlikely-arg-type")
	private Entry scanSamplesEntries(final Container container, final Sample sample) {
		for (final Entry candidate_entry : container.getEntries()) {
			if (candidate_entry.equals(sample)) // NOSONAR
				return candidate_entry;
		}
		return null;
	}

	private void scanSamplesForMissingContainer(final Samples set, final Container archive, final SubjectSet reportSubject) {
		for (final Sample sample : set)
			sample.setStatus(jrm.profile.data.EntityStatus.KO);
		if (!scan.createMode)
			return;
		int samplesFound = 0;
		boolean partialSet = false;
		final var createSet = new AtomicReference<CreateContainer>();
		for (final Sample sample : set) {
			scan.report.getStats().incMissingSamplesCnt();
			Entry entryFound = searchSampleInAllScans(set, sample);
			if (null != entryFound) {
				reportSubject.add(new EntryAdd(sample, entryFound));
				CreateContainer.getInstance(createSet, archive, scan.format, Long.MAX_VALUE).addAction(new AddEntry(sample, entryFound));
				samplesFound++;
			} else {
				reportSubject.add(new EntryMissing(sample));
				partialSet = true;
			}
		}
		if (samplesFound > 0 && (!scan.createFullMode || !partialSet)) {
			reportSubject.setCreateFull();
			if (partialSet)
				reportSubject.setCreate();
			ContainerAction.addToList(scan.createActions, createSet.get());
		}
	}

	private Entry searchSampleInAllScans(final Samples set, final Sample sample) {
		for (final DirScan dscan : scan.allScans) {
			for (final FormatOptions.Ext ext : EnumSet.allOf(FormatOptions.Ext.class)) {
				final Container foundContainer = dscan.getContainerByName(set.getName() + ext);
				if (null != foundContainer) {
					for (final Entry entry : foundContainer.getEntriesByFName().values()) {
						if (entry.getName().equals(sample.getNormalizedName()))
							return entry;
					}
				}
			}
		}
		return null;
	}
}
