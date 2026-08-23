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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;

import jrm.aui.progress.ProgressHandler;
import jrm.misc.BreakException;
import jrm.misc.ProfileSettingsEnum;
import jrm.profile.Profile;
import jrm.profile.data.Archive;
import jrm.profile.data.ByName;
import jrm.profile.data.Container;
import jrm.profile.data.Container.Type;
import jrm.profile.data.Directory;
import jrm.profile.data.Machine;
import jrm.profile.data.SoftwareList;
import jrm.profile.scan.options.FormatOptions;
import jrm.security.PathAbstractor;

/**
 * Handles source/destination directory initialization and initial DirScan execution.
 * Extracted to keep Scan.java smaller.
 */
final class ScanDirSetup {

	private final Scan scan;

	ScanDirSetup(final Scan scan) {
		this.scan = scan;
	}

	ArrayList<File> initSrcDirs(final Profile profile) throws SecurityException {
		final var srcdirs = new ArrayList<File>();
		for (final var s : StringUtils.split(profile.getProperty(ProfileSettingsEnum.src_dir), '|')) // $NON-NLS-1$ //$NON-NLS-2$
		{
			if (!s.isEmpty()) {
				final var f = scan.getAbsolutePath(s).toFile();
				if (f.isDirectory())
					srcdirs.add(f);
			}
		}
		final String workdir;
		if (Boolean.TRUE.equals(profile.getSettings().getProperty(ProfileSettingsEnum.backup_dest_dir_enabled, Boolean.class)))
			workdir = profile.getSettings().getProperty(ProfileSettingsEnum.backup_dest_dir);
		else
			workdir = Scan.WORK_BACKUP;
		if (!workdir.equals(Scan.WORK_BACKUP))
			srcdirs.add(PathAbstractor.getWritableAbsolutePath(profile.getSession(), workdir).toFile()); // $NON-NLS-1$
		final String gworkdir;
		if (Boolean.TRUE.equals(profile.getSession().getUser().getSettings().getProperty(ProfileSettingsEnum.backup_dest_dir_enabled, Boolean.class)))
			gworkdir = profile.getSession().getUser().getSettings().getProperty(ProfileSettingsEnum.backup_dest_dir);
		else
			gworkdir = Scan.WORK_BACKUP;
		if (!gworkdir.equals(Scan.WORK_BACKUP) && !gworkdir.equals(workdir))
			srcdirs.add(PathAbstractor.getWritableAbsolutePath(profile.getSession(), gworkdir).toFile()); // $NON-NLS-1$
		srcdirs.add(new File(profile.getSession().getUser().getSettings().getWorkPath().toFile(), "backup")); //$NON-NLS-1$
		return srcdirs;
	}

	File initSamplesDstDir(final Profile profile) throws ScanException {
		if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.samples_dest_dir_enabled, Boolean.class))) {
			final String samplesDstDirTxt = profile.getProperty(ProfileSettingsEnum.samples_dest_dir); // $NON-NLS-1$ //$NON-NLS-2$
			if (samplesDstDirTxt.isEmpty())
				throw new ScanException("Samples dst dir is empty");
			try {
				final var samplesDstDir = scan.getWritableAbsolutePath(samplesDstDirTxt).toFile();
				if (!samplesDstDir.isDirectory())
					throw new ScanException("Samples dst dir is not a directory");
				return samplesDstDir;
			} catch (SecurityException e) {
				throw new ScanException(e.getMessage());
			}
		}
		return null;
	}

	File initSwDisksDstDir(final Profile profile, final File swromsDstDir) throws ScanException {
		final File swdisksDstDir;
		if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.swdisks_dest_dir_enabled, Boolean.class))) // $NON-NLS-1$
		{
			final String swdisksDstDirTxt = profile.getProperty(ProfileSettingsEnum.swdisks_dest_dir); // $NON-NLS-1$ //$NON-NLS-2$
			if (swdisksDstDirTxt.isEmpty())
				throw new ScanException("Software Disks dst dir is empty");
			try {
				swdisksDstDir = scan.getWritableAbsolutePath(swdisksDstDirTxt).toFile();
			} catch (SecurityException e) {
				throw new ScanException(e.getMessage());
			}
		} else
			swdisksDstDir = new File(swromsDstDir.getAbsolutePath());
		return swdisksDstDir;
	}

	File initSwRomsDstDir(final Profile profile, final File romsDstDir) throws ScanException {
		final File swromsDstDir;
		if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.swroms_dest_dir_enabled, Boolean.class))) // $NON-NLS-1$
		{
			final String swromsDstDirTxt = profile.getProperty(ProfileSettingsEnum.swroms_dest_dir); // $NON-NLS-1$ //$NON-NLS-2$
			if (swromsDstDirTxt.isEmpty())
				throw new ScanException("Software roms dst dir is empty");
			try {
				swromsDstDir = scan.getWritableAbsolutePath(swromsDstDirTxt).toFile();
			} catch (SecurityException e) {
				throw new ScanException(e.getMessage());
			}
		} else
			swromsDstDir = new File(romsDstDir.getAbsolutePath());
		return swromsDstDir;
	}

	File initDisksDstDir(final Profile profile, final File romsDstDir) throws ScanException {
		final File disksDstDir;
		if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.disks_dest_dir_enabled, Boolean.class))) // $NON-NLS-1$
		{
			final String disksDstDirTxt = profile.getProperty(ProfileSettingsEnum.disks_dest_dir); // $NON-NLS-1$ //$NON-NLS-2$
			if (disksDstDirTxt.isEmpty())
				throw new ScanException("Disks dst dir is empty");
			try {
				disksDstDir = scan.getWritableAbsolutePath(disksDstDirTxt).toFile();
			} catch (SecurityException e) {
				throw new ScanException(e.getMessage());
			}
		} else
			disksDstDir = new File(romsDstDir.getAbsolutePath());
		return disksDstDir;
	}

	File initRomsDstDir(final Profile profile) throws ScanException {
		final String dstDirTxt = profile.getProperty(ProfileSettingsEnum.roms_dest_dir); // $NON-NLS-1$ //$NON-NLS-2$
		if (dstDirTxt.isEmpty())
			throw new ScanException("dst dir is empty");
		try {
			final File romsDstDir = scan.getWritableAbsolutePath(dstDirTxt).toFile();
			if (!romsDstDir.isDirectory())
				throw new ScanException("dst dir is not a directory");
			return romsDstDir;
		} catch (SecurityException e) {
			throw new ScanException(e.getMessage());
		}
	}

	void scanSrcDirs(final Profile profile, final ProgressHandler handler, Map<String, DirScan> scancache, final ArrayList<File> srcdirs) throws BreakException {
		for (final var dir : srcdirs) {
			if (scancache != null) {
				final var cachefile = DirScan.getCacheFile(profile.getSession(), dir, DirScan.getOptions(profile, false)).getAbsolutePath();
				scan.allScans.add(scancache.computeIfAbsent(cachefile, _ -> new DirScan(profile, dir, handler, false)));
			} else
				scan.allScans.add(new DirScan(profile, dir, handler, false));
			if (handler.isCancel())
				throw new BreakException();
		}
	}

	void scanDstDirs(final File romsDstDir, final File disksDstDir, final File samplesDstDir, final ArrayList<Container> unknown, final ArrayList<Container> unneeded,
			final ArrayList<Container> samplesUnknown, final ArrayList<Container> samplesUnneeded) throws BreakException {
		if (!scan.profile.getMachineListList().get(0).isEmpty()) {
			scan.profile.getMachineListList().get(0).resetFilteredName();
			scan.romsDstScan = dirscan(scan.profile.getMachineListList().get(0), romsDstDir, unknown, unneeded, scan.handler);
			if (romsDstDir.equals(disksDstDir))
				scan.disksDstScan = scan.romsDstScan;
			else
				scan.disksDstScan = dirscan(scan.profile.getMachineListList().get(0), disksDstDir, unknown, unneeded, scan.handler);
			if (samplesDstDir != null && samplesDstDir.isDirectory())
				scan.samplesDstScan = dirscan(scan.profile.getMachineListList().get(0).samplesets, samplesDstDir, samplesUnknown, samplesUnneeded, scan.handler);
			if (scan.handler.isCancel())
				throw new BreakException();
		}
	}

	void scanSWDstDirs(final File romsDstDir, final File swromsDstDir, final File swdisksDstDir, final ArrayList<Container> unknown, final ArrayList<Container> unneeded)
			throws BreakException {
		if (scan.profile.getMachineListList().getSoftwareListList().isEmpty())
			return;
		final AtomicInteger j = new AtomicInteger();
		scan.handler.setProgress3(String.format("%d/%d", j.get(), scan.profile.getMachineListList().getSoftwareListList().size()), j.get(), //$NON-NLS-1$
				scan.profile.getMachineListList().getSoftwareListList().size());
		for (final SoftwareList sl : scan.profile.getMachineListList().getSoftwareListList().getFilteredStream().toList()) {
			sl.resetFilteredName();
			File sldir = new File(swromsDstDir, sl.getName());
			scan.swromsDstScans.put(sl.getName(), dirscan(sl, sldir, unknown, unneeded, scan.handler));
			if (swromsDstDir.equals(swdisksDstDir))
				scan.swdisksDstScans = scan.swromsDstScans;
			else {
				sldir = new File(swdisksDstDir, sl.getName());
				scan.swdisksDstScans.put(sl.getName(), dirscan(sl, sldir, unknown, unneeded, scan.handler));
			}
			scan.handler.setProgress3(String.format("%d/%d (%s)", j.incrementAndGet(), scan.profile.getMachineListList().getSoftwareListList().size(), sl.getName()), j.get(), //$NON-NLS-1$
					scan.profile.getMachineListList().getSoftwareListList().size());
			if (scan.handler.isCancel())
				throw new BreakException();
		}
		scan.handler.setProgress3(null, null);
		searchUnknownDirs(romsDstDir, swromsDstDir, swdisksDstDir, unknown);
	}

	private void searchUnknownDirs(final File romsDstDir, final File swromsDstDir, final File swdisksDstDir, final ArrayList<Container> unknown) throws BreakException {
		if (!swromsDstDir.equals(romsDstDir) && swromsDstDir.isDirectory())
			Optional.ofNullable(swromsDstDir.listFiles()).ifPresent(files -> deduceUnknownFilesFromScan(scan.swromsDstScans, unknown, files));
		if (!swromsDstDir.equals(swdisksDstDir) && swdisksDstDir.isDirectory())
			Optional.ofNullable(swdisksDstDir.listFiles()).ifPresent(files -> deduceUnknownFilesFromScan(scan.swdisksDstScans, unknown, files));
	}

	private void deduceUnknownFilesFromScan(final Map<String, DirScan> scanMap, final ArrayList<Container> unknown, final File[] files) throws BreakException {
		for (final File f : files) {
			if (!scanMap.containsKey(f.getName()))
				unknown.add(f.isDirectory() ? new Directory(f, scan.getRelativePath(f), (Machine) null) : new Archive(f, scan.getRelativePath(f), (Machine) null));
			if (scan.handler.isCancel())
				throw new BreakException();
		}
	}

	DirScan dirscan(final ByName<?> byname, final File dstdir, final List<Container> unknown, final List<Container> unneeded, final ProgressHandler handler) {
		final DirScan dstScan;
		dstScan = new DirScan(scan.profile, dstdir, handler, true);
		scan.allScans.add(dstScan);
		for (final Container c : dstScan.getContainersIterable()) {
			if (c.getType() == Type.UNK)
				unknown.add(c);
			else if (c.getType() == Type.DIR && scan.format == FormatOptions.FAKE)
				unknown.add(c);
			else if (!byname.containsFilteredName(Scan.getBaseName(c.getFile()))) {
				if (byname.containsName(Scan.getBaseName(c.getFile())))
					unneeded.add(c);
				else
					unknown.add(c);
			}
		}
		return dstScan;
	}

	void reportSuspiciousCrc() {
		scan.profile.getSuspiciousCRC().forEach(crc -> scan.report.add(new jrm.profile.report.RomSuspiciousCRC(crc)));
	}

	void processAndReportUnneededActions(final ArrayList<Container> unneeded) {
		if (!scan.ignoreUnneededContainers) {
			unneeded.forEach(c -> {
				scan.report.add(new jrm.profile.report.ContainerUnneeded(c));
				scan.backupActions.add(new jrm.profile.fix.actions.BackupContainer(c));
				scan.deleteActions.add(new jrm.profile.fix.actions.DeleteContainer(c, scan.format));
			});
		}
	}

	void processAndReportUnknownActions(final File romsDstDir, final File disksDstDir, final File swromsDstDir, final File swdisksDstDir, final File samplesDstDir,
			final ArrayList<Container> unknown) {
		if (!scan.ignoreUnknownContainers) {
			unknown.stream().filter(c -> {
				if (samplesDstDir != null && c.getRelFile().equals(samplesDstDir))
					return false;
				else if (disksDstDir != romsDstDir && c.getRelFile().equals(disksDstDir))
					return false;
				else if (swromsDstDir != romsDstDir && c.getRelFile().equals(swromsDstDir))
					return false;
				else
					return !(swdisksDstDir != swromsDstDir && c.getRelFile().equals(swdisksDstDir));
			}).forEach(c -> {
				scan.report.add(new jrm.profile.report.ContainerUnknown(c));
				scan.deleteActions.add(new jrm.profile.fix.actions.DeleteContainer(c, scan.format));
			});
		}
	}
}
