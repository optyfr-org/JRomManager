/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.scan;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import jrm.profile.data.Anyware;
import jrm.profile.data.Container;
import jrm.profile.data.Disk;
import jrm.profile.data.Rom;
import jrm.profile.fix.actions.BackupContainer;
import jrm.profile.fix.actions.DeleteContainer;
import jrm.profile.report.ContainerUnneeded;
import jrm.profile.scan.options.FormatOptions;

/**
 * Handles post-audit cleanup actions: removing unneeded clones and format-mismatched containers.
 * Extracted from Scan to reduce main orchestration class size.
 */
final class ScanCleanup {

	private final Scan scan;

	ScanCleanup(final Scan scan) {
		this.scan = scan;
	}

	void removeOtherFormats(final Anyware ware) {
		scan.format.getExt().allExcept().forEach(e -> {
			final Container c = scan.romsDstScan.getContainerByName(ware.getName() + e);
			if (c != null) {
				scan.report.add(new ContainerUnneeded(c));
				scan.backupActions.add(new BackupContainer(c));
				scan.deleteActions.add(new DeleteContainer(c, scan.format));
			}
		});
	}

	void removeUnneededClone(final Anyware ware, final List<Disk> disks, final List<Rom> roms) {
		if (scan.mergeMode.isMerge() && ware.isClone()) {
			if (scan.format == FormatOptions.DIR && disks.isEmpty() && roms.isEmpty()) {
				Arrays.asList(scan.romsDstScan.getContainerByName(ware.getName()), scan.disksDstScan.getContainerByName(ware.getName())).forEach(c -> {
					if (c != null) {
						scan.report.add(new ContainerUnneeded(c));
						scan.backupActions.add(new BackupContainer(c));
						scan.deleteActions.add(new DeleteContainer(c, scan.format));
					}
				});
			} else if (disks.isEmpty()) {
				Optional.ofNullable(scan.disksDstScan.getContainerByName(ware.getName())).ifPresent(c -> {
					scan.report.add(new ContainerUnneeded(c));
					scan.backupActions.add(new BackupContainer(c));
					scan.deleteActions.add(new DeleteContainer(c, scan.format));
				});
			}
			if (scan.format != FormatOptions.DIR && roms.isEmpty()) {
				Optional.ofNullable(scan.romsDstScan.getContainerByName(ware.getName() + scan.format.getExt())).ifPresent(c -> {
					scan.report.add(new ContainerUnneeded(c));
					scan.backupActions.add(new BackupContainer(c));
					scan.deleteActions.add(new DeleteContainer(c, scan.format));
				});
			}
		}
	}
}
