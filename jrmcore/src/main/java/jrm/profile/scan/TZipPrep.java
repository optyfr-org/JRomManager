/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.scan;

import java.util.Optional;

import jrm.profile.data.Anyware;
import jrm.profile.data.Container;
import jrm.profile.data.Samples;
import jrm.profile.fix.actions.TZipContainer;
import jrm.profile.report.ContainerTZip;
import jrm.profile.report.SubjectSet;
import jrm.profile.scan.options.FormatOptions;
import jtrrntzip.TrrntZipStatus;

/**
 * Handles TorrentZip (TZIP) preparation actions for both ROM sets and Samples.
 * Extracted from Scan to further reduce main class size.
 */
final class TZipPrep {

	private final Scan scan;

	TZipPrep(final Scan scan) {
		this.scan = scan;
	}

	void prepTZip(final SubjectSet reportSubject, final Container archive, final Anyware ware, final java.util.List<jrm.profile.data.Rom> roms) {
		if (scan.format != FormatOptions.TZIP || (scan.mergeMode.isMerge() && ware.isClone()) || reportSubject.isMissing() || reportSubject.isUnneeded() || roms.isEmpty())
			return;
		Optional<Container> tzipcontainer = Optional.empty();
		final Container container = scan.romsDstScan.getContainerByName(ware.getDest().getName() + scan.format.getExt());
		if (container != null) {
			if (container.getLastTZipCheck() < container.getModified() || !container.getLastTZipStatus().contains(TrrntZipStatus.VALIDTRRNTZIP) || reportSubject.hasFix())
				tzipcontainer = Optional.of(container);
		} else if (scan.createMode) {
			if (scan.createFullMode) {
				if (reportSubject.isFixable())
					tzipcontainer = Optional.of(archive);
			} else if (reportSubject.hasFix())
				tzipcontainer = Optional.of(archive);
		}
		tzipcontainer.ifPresent(c -> {
			final long estimatedRomsSize = roms.stream().mapToLong(jrm.profile.data.Rom::getSize).sum();
			c.setRelAW(ware);
			scan.tzipActions.put(c.getFile().getAbsolutePath(), new TZipContainer(c, scan.format, estimatedRomsSize));
			scan.report.add(new ContainerTZip(c));
		});
	}

	void prepTZip(final SubjectSet reportSubject, final Container archive, final Samples set) {
		if (scan.format == FormatOptions.TZIP && !reportSubject.isMissing() && !reportSubject.isUnneeded() && set.getSamplesMap().size() > 0) {
			Container tzipcontainer = null;
			final Container container = scan.samplesDstScan.getContainerByName(archive.getFile().getName());
			if (container != null) {
				if (container.getLastTZipCheck() < container.getModified() || !container.getLastTZipStatus().contains(TrrntZipStatus.VALIDTRRNTZIP) || reportSubject.hasFix())
					tzipcontainer = container;
			} else if (scan.createMode && reportSubject.hasFix())
				tzipcontainer = archive;
			if (tzipcontainer != null) {
				tzipcontainer.setRelAW(set);
				scan.tzipActions.put(tzipcontainer.getFile().getAbsolutePath(), new TZipContainer(tzipcontainer, scan.format, Long.MAX_VALUE));
				scan.report.add(new ContainerTZip(tzipcontainer));
			}
		}
	}
}
