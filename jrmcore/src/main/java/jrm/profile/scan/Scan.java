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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;

import jrm.aui.progress.ProgressHandler;
import jrm.locale.Messages;
import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.misc.MultiThreadingVirtual;
import jrm.misc.ProfileSettingsEnum;
import jrm.misc.SettingsEnum;
import jrm.profile.Profile;
import jrm.profile.data.Anyware;
import jrm.profile.data.AnywareList;
import jrm.profile.data.Archive;
import jrm.profile.data.ByName;
import jrm.profile.data.Container;
import jrm.profile.data.Container.Type;
import jrm.profile.data.Directory;
import jrm.profile.data.Disk;
import jrm.profile.data.Entity;
import jrm.profile.data.EntityStatus;
import jrm.profile.data.Entry;
import jrm.profile.data.FakeDirectory;
import jrm.profile.data.Machine;
import jrm.profile.data.Rom;
import jrm.profile.data.Sample;
import jrm.profile.data.Samples;
import jrm.profile.data.Software;
import jrm.profile.data.SoftwareList;
import jrm.profile.fix.actions.AddEntry;
import jrm.profile.fix.actions.BackupContainer;
import jrm.profile.fix.actions.BackupEntry;
import jrm.profile.fix.actions.ContainerAction;
import jrm.profile.fix.actions.CreateContainer;
import jrm.profile.fix.actions.DeleteContainer;
import jrm.profile.fix.actions.DeleteEntry;
import jrm.profile.fix.actions.DuplicateEntry;
import jrm.profile.fix.actions.OpenContainer;
import jrm.profile.fix.actions.RenameEntry;
import jrm.profile.fix.actions.TZipContainer;
import jrm.profile.report.ContainerTZip;
import jrm.profile.report.ContainerUnknown;
import jrm.profile.report.ContainerUnneeded;
import jrm.profile.report.EntryAdd;
import jrm.profile.report.EntryMissing;
import jrm.profile.report.EntryMissingDuplicate;
import jrm.profile.report.EntryOK;
import jrm.profile.report.EntryUnneeded;
import jrm.profile.report.EntryWrongHash;
import jrm.profile.report.EntryWrongName;
import jrm.profile.report.Report;
import jrm.profile.report.RomSuspiciousCRC;
import jrm.profile.report.SubjectSet;
import jrm.profile.report.SubjectSet.Status;
import jrm.profile.scan.options.FormatOptions;
import jrm.profile.scan.options.MergeOptions;
import jrm.security.PathAbstractor;
import jtrrntzip.TrrntZipStatus;

/**
 * The main Scan orchestration manager. Walks through source and destination directory scanners, correlates scanned physical rom and
 * CHD contents against parsed metadata profiles, resolves gaps and wrong hashes/names, and builds a comprehensive list of
 * corrective fixing actions to repair the files.
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public class Scan extends PathAbstractor {
    /**
     * Default system variable mapping for workspace backups.
     */
    private static final String WORK_BACKUP = "%work/backup";
    /**
     * Translation string resource bundle key showing active fixes searching progress.
     */
    private static final String MSG_SCAN_SEARCHING_FOR_FIXES = "Scan.SearchingForFixes";
    /**
     * The attached active auditing {@link Report} instance containing detected problems.
     */
    public final Report report;
    /**
     * All corrective repairing actions grouped in execution phases to execute after completing scans.
     */
    public final List<Collection<jrm.profile.fix.actions.ContainerAction>> actions = new ArrayList<>();

    /**
     * The current audited profiles metadata configurations.
     */
    private final Profile profile;

    /**
     * Progress tracker updating percentages and logs to the active user interface.
     */
    private final ProgressHandler handler;

    /**
     * Active merge ruleset configuration.
     */
    private final MergeOptions mergeMode;
    /**
     * Active format output selection.
     */
    private final FormatOptions format;
    /**
     * Indicates whether the scanner should suggest container creation for missing items.
     */
    private final boolean createMode;
    /**
     * Suggest rebuilding packages even when romsets are only partially complete.
     */
    private final boolean createFullMode;
    /**
     * Ignore unneeded containers that do not correspond to any active game/romset in the DAT profile.
     */
    private final boolean ignoreUnneededContainers;
    /**
     * Ignore extra files inside containers that do not belong to that romset definition.
     */
    private final boolean ignoreUnneededEntries;
    /**
     * Ignore totally unrecognized files/folders discovered in the scanned destinations.
     */
    private final boolean ignoreUnknownContainers;
    /**
     * Active backup status configuration.
     */
    private final boolean backup;
    /**
     * Active multi-threading configuration.
     */
    private final boolean useParallelism;
    /**
     * active thread counts.
     */
    private final int nThreads;

    /**
     * Scanned folders result mapping for game/machine ROM paths.
     */
    private DirScan romsDstScan = null;
    /**
     * Scanned folders result mapping for disk images paths.
     */
    private DirScan disksDstScan = null;
    /**
     * Scanned folders result mapping for sample audio assets paths.
     */
    private DirScan samplesDstScan = null;
    /**
     * Software lists roms destination scanners indexed by list code name.
     */
    private final Map<String, DirScan> swromsDstScans = new HashMap<>();
    /**
     * Software lists disk images destination scanners indexed by list code name.
     */
    private Map<String, DirScan> swdisksDstScans = new HashMap<>();
    /**
     * Unified list gathering all physical scanners executed during the active run.
     */
    private final List<DirScan> allScans = new ArrayList<>();

    /**
     * Backups collection group executed first to preserve original content prior to applying repairs.
     */
    private final List<jrm.profile.fix.actions.ContainerAction> backupActions = Collections.synchronizedList(new ArrayList<>());
    /**
     * Creation actions suggestion collection.
     */
    private final List<jrm.profile.fix.actions.ContainerAction> createActions = Collections.synchronizedList(new ArrayList<>());
    /**
     * renaming actions applied prior to deletions or imports.
     */
    private final List<jrm.profile.fix.actions.ContainerAction> renameBeforeActions = Collections.synchronizedList(new ArrayList<>());
    /**
     * Standard folder and package entry addition actions.
     */
    private final List<jrm.profile.fix.actions.ContainerAction> addActions = Collections.synchronizedList(new ArrayList<>());
    /**
     * Standard deletion actions.
     */
    private final List<jrm.profile.fix.actions.ContainerAction> deleteActions = Collections.synchronizedList(new ArrayList<>());
    /**
     * Renaming actions executed after standard import additions complete.
     */
    private final List<jrm.profile.fix.actions.ContainerAction> renameAfterActions = Collections.synchronizedList(new ArrayList<>());
    /**
     * duplicate file mapping actions.
     */
    private final List<jrm.profile.fix.actions.ContainerAction> duplicateActions = Collections.synchronizedList(new ArrayList<>());
    /**
     * TorrentZip processing final step actions.
     */
    private final Map<String, jrm.profile.fix.actions.ContainerAction> tzipActions = Collections.synchronizedMap(new HashMap<>());

    /**
     * Creates a negated predicate.
     * 
     * @param predicate the predicate to negate
     * @param <T> the predicate argument type
     * 
     * @return the negated predicate instance
     */
    private static <T> Predicate<T> not(final Predicate<T> predicate) {
        return predicate.negate();
    }

    /**
     * Constructs a new Scan orchestrator.
     * 
     * @param profile the profile configuration
     * @param handler progress reporting UI channel
     * 
     * @throws BreakException if scans are aborted
     * @throws ScanException if paths configuration contains errors
     */
    public Scan(final Profile profile, final ProgressHandler handler) throws BreakException, ScanException {
        this(profile, handler, null);
    }

    /**
     * Constructs a new Scan orchestrator using cache systems.
     * 
     * @param profile the profile configuration
     * @param handler progress reporting UI channel
     * @param scancache the directories cache manager
     * 
     * @throws BreakException if scans are aborted
     * @throws ScanException if paths configuration contains errors
     */
    public Scan(final Profile profile, final ProgressHandler handler, Map<String, DirScan> scancache) throws BreakException, ScanException {
        super(profile.getSession());
        this.profile = profile;
        this.handler = handler;
        this.report = profile.getSession().getReport();
        profile.setPropsCheckPoint();
        report.reset();
        report.setProfile(profile);

        format = FormatOptions.valueOf(profile.getProperty(ProfileSettingsEnum.format)); // $NON-NLS-1$
        mergeMode = MergeOptions.valueOf(profile.getProperty(ProfileSettingsEnum.merge_mode)); // $NON-NLS-1$
        createMode = profile.getProperty(ProfileSettingsEnum.create_mode, Boolean.class); // $NON-NLS-1$
        createFullMode = profile.getProperty(ProfileSettingsEnum.createfull_mode, Boolean.class); // $NON-NLS-1$
        ignoreUnneededContainers = profile.getProperty(ProfileSettingsEnum.ignore_unneeded_containers, Boolean.class); // $NON-NLS-1$
        ignoreUnneededEntries = profile.getProperty(ProfileSettingsEnum.ignore_unneeded_entries, Boolean.class); // $NON-NLS-1$
        ignoreUnknownContainers = profile.getProperty(ProfileSettingsEnum.ignore_unknown_containers, Boolean.class); // $NON-NLS-1$
        backup = profile.getProperty(ProfileSettingsEnum.backup, Boolean.class); // $NON-NLS-1$
        useParallelism = profile.getProperty(ProfileSettingsEnum.use_parallelism, Boolean.class);
        nThreads = useParallelism ? profile.getSession().getUser().getSettings().getProperty(SettingsEnum.thread_count, Integer.class) : 1;

        final File romsDstDir = initRomsDstDir(profile);
        final File disksDstDir = initDisksDstDir(profile, romsDstDir);
        final File swromsDstDir = initSwRomsDstDir(profile, romsDstDir);
        final File swdisksDstDir = initSwDisksDstDir(profile, swromsDstDir);
        final File samplesDstDir = initSamplesDstDir(profile);
        final var srcdirs = initSrcDirs(profile);

        scanSrcDirs(profile, handler, scancache, srcdirs);

        try {
            final ArrayList<Container> unknown = new ArrayList<>();
            final ArrayList<Container> unneeded = new ArrayList<>();
            final ArrayList<Container> samplesUnknown = new ArrayList<>();
            final ArrayList<Container> samplesUnneeded = new ArrayList<>();
            scanDstDirs(romsDstDir, disksDstDir, samplesDstDir, unknown, unneeded, samplesUnknown, samplesUnneeded);
            scanSWDstDirs(romsDstDir, swromsDstDir, swdisksDstDir, unknown, unneeded);

            handler.setInfos(nThreads, null);

            processAndReportUnknownActions(romsDstDir, disksDstDir, swromsDstDir, swdisksDstDir, samplesDstDir, unknown);
            processAndReportUnneededActions(unneeded);
            reportSuspiciousCrc();
            searchFixes();
        } catch (final BreakException e) {
            throw e;
        } catch (final Exception e) {
            Log.err("Other Exception when listing", e); //$NON-NLS-1$
        } finally {
            handler.setInfos(1, null);
            handler.setProgress(Messages.getString("Profile.SavingCache"), 0); //$NON-NLS-1$
            if (!profile.getSession().isServer())
                report.write(profile.getSession());
            report.flush();
            final var nfo = profile.getNfo();
            nfo.getStats().setScanned(Instant.now());
            nfo.getStats().setHaveSets(
                    Stream.concat(profile.getMachineListList().stream(), profile.getMachineListList().getSoftwareListList().stream()).mapToLong(AnywareList::countHave).sum());
            nfo.getStats().setHaveRoms(Stream.concat(profile.getMachineListList().stream(), profile.getMachineListList().getSoftwareListList().stream())
                    .flatMap(AnywareList::stream).mapToLong(Anyware::countHaveRoms).sum());
            nfo.getStats().setHaveDisks(Stream.concat(profile.getMachineListList().stream(), profile.getMachineListList().getSoftwareListList().stream())
                    .flatMap(AnywareList::stream).mapToLong(Anyware::countHaveDisks).sum());
            nfo.save(profile.getSession());
            //profile.save();
        }

        if (backup)
            actions.add(backupActions);
        actions.add(createActions);
        actions.add(renameBeforeActions);
        actions.add(duplicateActions);
        actions.add(addActions);
        actions.add(deleteActions);
        actions.add(renameAfterActions);
        actions.add(new ArrayList<>(tzipActions.values()));

    }

    /**
     * Prepares source paths collection folders from profile settings.
     * 
     * @param profile the active configurations profile
     * 
     * @return paths collection of physical folders
     * 
     * @throws SecurityException if file read/writes are prohibited
     */
    private ArrayList<File> initSrcDirs(final Profile profile) throws SecurityException {
        final var srcdirs = new ArrayList<File>();
        for (final var s : StringUtils.split(profile.getProperty(ProfileSettingsEnum.src_dir), '|')) // $NON-NLS-1$ //$NON-NLS-2$
        {
            if (!s.isEmpty()) {
                final var f = getAbsolutePath(s).toFile();
                if (f.isDirectory())
                    srcdirs.add(f);
            }
        }
        final String workdir;
        if (Boolean.TRUE.equals(profile.getSettings().getProperty(ProfileSettingsEnum.backup_dest_dir_enabled, Boolean.class)))
            workdir = profile.getSettings().getProperty(ProfileSettingsEnum.backup_dest_dir);
        else
            workdir = WORK_BACKUP;
        if (!workdir.equals(WORK_BACKUP))
            srcdirs.add(PathAbstractor.getWritableAbsolutePath(profile.getSession(), workdir).toFile()); // $NON-NLS-1$
        final String gworkdir;
        if (Boolean.TRUE.equals(profile.getSession().getUser().getSettings().getProperty(ProfileSettingsEnum.backup_dest_dir_enabled, Boolean.class)))
            gworkdir = profile.getSession().getUser().getSettings().getProperty(ProfileSettingsEnum.backup_dest_dir);
        else
            gworkdir = WORK_BACKUP;
        if (!gworkdir.equals(WORK_BACKUP) && !gworkdir.equals(workdir))
            srcdirs.add(PathAbstractor.getWritableAbsolutePath(profile.getSession(), gworkdir).toFile()); // $NON-NLS-1$
        srcdirs.add(new File(profile.getSession().getUser().getSettings().getWorkPath().toFile(), "backup")); //$NON-NLS-1$
        return srcdirs;
    }

    /**
     * Prepares audio samples destination path folder.
     * 
     * @param profile profile details
     * 
     * @return directory folder handle, or {@code null} if disabled
     * 
     * @throws ScanException if paths are invalid
     */
    private File initSamplesDstDir(final Profile profile) throws ScanException {
        if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.samples_dest_dir_enabled, Boolean.class))) {
            final String samplesDstDirTxt = profile.getProperty(ProfileSettingsEnum.samples_dest_dir); // $NON-NLS-1$ //$NON-NLS-2$
            if (samplesDstDirTxt.isEmpty())
                throw new ScanException("Samples dst dir is empty");
            try {
                final var samplesDstDir = getWritableAbsolutePath(samplesDstDirTxt).toFile();
                if (!samplesDstDir.isDirectory())
                    throw new ScanException("Samples dst dir is not a directory");
                return samplesDstDir;
            } catch (SecurityException e) {
                throw new ScanException(e.getMessage());
            }
        }
        return null;
    }

    /**
     * Prepares SoftwareList CHDs disk destination folder.
     * 
     * @param profile profile details
     * @param swromsDstDir software list ROMs destination
     * 
     * @return sw CHD disk destination folder
     * 
     * @throws ScanException if paths are invalid
     */
    private File initSwDisksDstDir(final Profile profile, final File swromsDstDir) throws ScanException {
        final File swdisksDstDir;
        if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.swdisks_dest_dir_enabled, Boolean.class))) // $NON-NLS-1$
        {
            final String swdisksDstDirTxt = profile.getProperty(ProfileSettingsEnum.swdisks_dest_dir); // $NON-NLS-1$ //$NON-NLS-2$
            if (swdisksDstDirTxt.isEmpty())
                throw new ScanException("Software Disks dst dir is empty");
            try {
                swdisksDstDir = getWritableAbsolutePath(swdisksDstDirTxt).toFile();
            } catch (SecurityException e) {
                throw new ScanException(e.getMessage());
            }
        } else
            swdisksDstDir = new File(swromsDstDir.getAbsolutePath());
        return swdisksDstDir;
    }

    /**
     * Prepares SoftwareList ROMs destination folder.
     * 
     * @param profile profile details
     * @param romsDstDir general ROMs destination
     * 
     * @return sw ROMs destination folder
     * 
     * @throws ScanException if paths are invalid
     */
    private File initSwRomsDstDir(final Profile profile, final File romsDstDir) throws ScanException {
        final File swromsDstDir;
        if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.swroms_dest_dir_enabled, Boolean.class))) // $NON-NLS-1$
        {
            final String swromsDstDirTxt = profile.getProperty(ProfileSettingsEnum.swroms_dest_dir); // $NON-NLS-1$ //$NON-NLS-2$
            if (swromsDstDirTxt.isEmpty())
                throw new ScanException("Software roms dst dir is empty");
            try {
                swromsDstDir = getWritableAbsolutePath(swromsDstDirTxt).toFile();
            } catch (SecurityException e) {
                throw new ScanException(e.getMessage());
            }
        } else
            swromsDstDir = new File(romsDstDir.getAbsolutePath());
        return swromsDstDir;
    }

    /**
     * Prepares CHD disk images destination folder.
     * 
     * @param profile profile details
     * @param romsDstDir general ROMs destination
     * 
     * @return CHD images destination folder
     * 
     * @throws ScanException if paths are invalid
     */
    private File initDisksDstDir(final Profile profile, final File romsDstDir) throws ScanException {
        final File disksDstDir;
        if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.disks_dest_dir_enabled, Boolean.class))) // $NON-NLS-1$
        {
            final String disksDstDirTxt = profile.getProperty(ProfileSettingsEnum.disks_dest_dir); // $NON-NLS-1$ //$NON-NLS-2$
            if (disksDstDirTxt.isEmpty())
                throw new ScanException("Disks dst dir is empty");
            try {
                disksDstDir = getWritableAbsolutePath(disksDstDirTxt).toFile();
            } catch (SecurityException e) {
                throw new ScanException(e.getMessage());
            }
        } else
            disksDstDir = new File(romsDstDir.getAbsolutePath());
        return disksDstDir;
    }

    /**
     * Prepares general ROMs destination folder.
     * 
     * @param profile profile details
     * 
     * @return ROMs destination folder handle
     * 
     * @throws ScanException if paths are invalid
     */
    private File initRomsDstDir(final Profile profile) throws ScanException {
        final String dstDirTxt = profile.getProperty(ProfileSettingsEnum.roms_dest_dir); // $NON-NLS-1$ //$NON-NLS-2$
        if (dstDirTxt.isEmpty())
            throw new ScanException("dst dir is empty");
        try {
            final File romsDstDir = getWritableAbsolutePath(dstDirTxt).toFile();
            if (!romsDstDir.isDirectory())
                throw new ScanException("dst dir is not a directory");
            return romsDstDir;
        } catch (SecurityException e) {
            throw new ScanException(e.getMessage());
        }
    }

    /**
     * Performs parallel scans over all parsed source paths.
     * 
     * @param profile active configurations profile
     * @param handler active progress monitor channel
     * @param scancache scans directory caching
     * @param srcdirs source folders to scan
     * 
     * @throws BreakException if process is aborted
     */
    private void scanSrcDirs(final Profile profile, final ProgressHandler handler, Map<String, DirScan> scancache, final ArrayList<File> srcdirs) throws BreakException {
        for (final var dir : srcdirs) {
            if (scancache != null) {
                final var cachefile = DirScan.getCacheFile(profile.getSession(), dir, DirScan.getOptions(profile, false)).getAbsolutePath();
                allScans.add(scancache.computeIfAbsent(cachefile, _ -> new DirScan(profile, dir, handler, false)));
            } else
                allScans.add(new DirScan(profile, dir, handler, false));
            if (handler.isCancel())
                throw new BreakException();
        }
    }

    /**
     * Performs scans over target destinations files and folders.
     * 
     * @param romsDstDir ROMs destination folder
     * @param disksDstDir CHDs destination folder
     * @param samplesDstDir Audio samples destination folder
     * @param unknown receives unknown containers discovered
     * @param unneeded receives unneeded containers discovered
     * @param samplesUnknown receives unknown samples discovered
     * @param samplesUnneeded receives unneeded samples discovered
     * 
     * @throws BreakException if process is aborted
     */
    private void scanDstDirs(final File romsDstDir, final File disksDstDir, final File samplesDstDir, final ArrayList<Container> unknown, final ArrayList<Container> unneeded,
            final ArrayList<Container> samplesUnknown, final ArrayList<Container> samplesUnneeded) throws BreakException {
        if (!profile.getMachineListList().get(0).isEmpty()) {
            profile.getMachineListList().get(0).resetFilteredName();
            romsDstScan = dirscan(profile.getMachineListList().get(0), romsDstDir, unknown, unneeded, handler);
            if (romsDstDir.equals(disksDstDir))
                disksDstScan = romsDstScan;
            else
                disksDstScan = dirscan(profile.getMachineListList().get(0), disksDstDir, unknown, unneeded, handler);
            if (samplesDstDir != null && samplesDstDir.isDirectory())
                samplesDstScan = dirscan(profile.getMachineListList().get(0).samplesets, samplesDstDir, samplesUnknown, samplesUnneeded, handler);
            if (handler.isCancel())
                throw new BreakException();
        }
    }

    /**
     * Performs scans over software lists folders.
     * 
     * @param romsDstDir ROMs destination folder
     * @param swromsDstDir sw ROMs destination folder
     * @param swdisksDstDir sw CHD destination folder
     * @param unknown receives unknown containers discovered
     * @param unneeded receives unneeded containers discovered
     * 
     * @throws BreakException if process is aborted
     */
    private void scanSWDstDirs(final File romsDstDir, final File swromsDstDir, final File swdisksDstDir, final ArrayList<Container> unknown, final ArrayList<Container> unneeded)
            throws BreakException {
        if (profile.getMachineListList().getSoftwareListList().isEmpty())
            return;
        final AtomicInteger j = new AtomicInteger();
        handler.setProgress3(String.format("%d/%d", j.get(), profile.getMachineListList().getSoftwareListList().size()), j.get(), //$NON-NLS-1$
                profile.getMachineListList().getSoftwareListList().size());
        for (final SoftwareList sl : profile.getMachineListList().getSoftwareListList().getFilteredStream().toList()) {
            sl.resetFilteredName();
            File sldir = new File(swromsDstDir, sl.getName());
            swromsDstScans.put(sl.getName(), dirscan(sl, sldir, unknown, unneeded, handler));
            if (swromsDstDir.equals(swdisksDstDir))
                swdisksDstScans = swromsDstScans;
            else {
                sldir = new File(swdisksDstDir, sl.getName());
                swdisksDstScans.put(sl.getName(), dirscan(sl, sldir, unknown, unneeded, handler));
            }
            handler.setProgress3(String.format("%d/%d (%s)", j.incrementAndGet(), profile.getMachineListList().getSoftwareListList().size(), sl.getName()), j.get(), //$NON-NLS-1$
                    profile.getMachineListList().getSoftwareListList().size());
            if (handler.isCancel())
                throw new BreakException();
        }
        handler.setProgress3(null, null);
        searchUnknownDirs(romsDstDir, swromsDstDir, swdisksDstDir, unknown);
    }

    /**
     * Scans and detects completely unrecognized subfolders residing inside sw lists destinations.
     * 
     * @param romsDstDir general ROMs destination folder
     * @param swromsDstDir sw ROMs destination folder
     * @param swdisksDstDir sw CHD destination folder
     * @param unknown receives unknown containers discovered
     * 
     * @throws BreakException if process is aborted
     */
    private void searchUnknownDirs(final File romsDstDir, final File swromsDstDir, final File swdisksDstDir, final ArrayList<Container> unknown) throws BreakException {
        if (!swromsDstDir.equals(romsDstDir) && swromsDstDir.isDirectory())
            Optional.ofNullable(swromsDstDir.listFiles()).ifPresent(files -> deduceUnknownFilesFromScan(swromsDstScans, unknown, files));
        if (!swromsDstDir.equals(swdisksDstDir) && swdisksDstDir.isDirectory())
            Optional.ofNullable(swdisksDstDir.listFiles()).ifPresent(files -> deduceUnknownFilesFromScan(swdisksDstScans, unknown, files));
    }

    /**
     * Inspects directory listing structures and collects unmatched packages as completely unknown.
     * 
     * @param scanMap completed scanners map
     * @param unknown receives unknown containers discovered
     * @param files physical list of files to check
     * 
     * @throws BreakException if process is aborted
     */
    private void deduceUnknownFilesFromScan(final Map<String, DirScan> scanMap, final ArrayList<Container> unknown, final File[] files) throws BreakException {
        for (final File f : files) {
            if (!scanMap.containsKey(f.getName()))
                unknown.add(f.isDirectory() ? new Directory(f, getRelativePath(f), (Machine) null) : new Archive(f, getRelativePath(f), (Machine) null));
            if (handler.isCancel())
                throw new BreakException();
        }
    }

    /**
     * Collects and serializes suspicious CRC listings to the report.
     */
    private void reportSuspiciousCrc() {
        profile.getSuspiciousCRC().forEach(crc -> report.add(new RomSuspiciousCRC(crc)));
    }

    /**
     * Processes unneeded containers to generate reporting and queue deletion fixes.
     * 
     * @param unneeded physical unneeded containers listing
     */
    private void processAndReportUnneededActions(final ArrayList<Container> unneeded) {
        if (!ignoreUnneededContainers) {
            unneeded.forEach(c -> {
                report.add(new ContainerUnneeded(c));
                backupActions.add(new BackupContainer(c));
                deleteActions.add(new DeleteContainer(c, format));
            });
        }
    }

    /**
     * Processes unknown packages to generate reporting and queue deletion fixes.
     * 
     * @param romsDstDir general ROMs folder
     * @param disksDstDir general CHD folder
     * @param swromsDstDir sw ROMs folder
     * @param swdisksDstDir sw CHD folder
     * @param samplesDstDir general audio samples folder
     * @param unknown unknown physical containers listing
     */
    private void processAndReportUnknownActions(final File romsDstDir, final File disksDstDir, final File swromsDstDir, final File swdisksDstDir, final File samplesDstDir,
            final ArrayList<Container> unknown) {
        if (!ignoreUnknownContainers) {
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
                report.add(new ContainerUnknown(c));
                deleteActions.add(new DeleteContainer(c, format));
            });
        }
    }

    /**
     * Walks through retro platforms, BIOS listings, games, software collections, and audio assets, performing comparative
     * verification to identify missing elements and suggest fixes.
     */
    private void searchFixes() {
        final AtomicInteger i = new AtomicInteger();
        final AtomicInteger j = new AtomicInteger();
        handler.setProgress(null, i.get(), profile.filteredSubsize()); // $NON-NLS-1$
        handler.setProgress2(String.format("%s %d/%d", Messages.getString(MSG_SCAN_SEARCHING_FOR_FIXES), j.get(), profile.size()), j.get(), profile.size()); //$NON-NLS-1$
        if (!profile.getMachineListList().get(0).isEmpty()) {

            handler.setProgress2(String.format("%s %d/%d", Messages.getString(MSG_SCAN_SEARCHING_FOR_FIXES), j.get(), profile.size()), j.getAndIncrement(), profile.size()); //$NON-NLS-1$
            try (final var mt = new MultiThreadingVirtual<Samples>("scan-samples", handler, nThreads, set -> {
                if (handler.isCancel())
                    return;
                handler.setProgress(set.getName(), i.getAndIncrement());
                if (samplesDstScan != null)
                    scanSamples(set);
            })) {
                mt.start(StreamSupport.stream(profile.getMachineListList().get(0).samplesets.spliterator(), false));
            }
            profile.getMachineListList().get(0).forEach(m -> {
                m.resetCollisionMode();
                m.resetClonesRomsStatus();
            });
            try (final var mt = new MultiThreadingVirtual<Machine>("scan-machines", handler, nThreads, m -> {
                if (handler.isCancel())
                    return;
                handler.setProgress(m.getFullName(), i.getAndIncrement());
                scanWare(m);
            })) {
                mt.start(profile.getMachineListList().get(0).getFilteredStream());
            }
        }
        if (!profile.getMachineListList().getSoftwareListList().isEmpty()) {
            profile.getMachineListList().getSoftwareListList().getFilteredStream().takeWhile(_ -> !handler.isCancel()).forEach(sl -> {
                handler.setProgress2(String.format("%s %d/%d (%s)", Messages.getString(MSG_SCAN_SEARCHING_FOR_FIXES), j.get(), profile.size(), sl.getName()), j.getAndIncrement(), //$NON-NLS-1$
                        profile.size());
                romsDstScan = swromsDstScans.get(sl.getName());
                disksDstScan = swdisksDstScans.get(sl.getName());
                sl.forEach(Software::resetCollisionMode);
                try (final var mt = new MultiThreadingVirtual<Software>("scan-soft-" + sl.getName().toLowerCase(), handler, nThreads, s -> {
                    if (handler.isCancel())
                        return;
                    handler.setProgress(s.getFullName(), i.getAndIncrement());
                    scanWare(s);
                })) {
                    mt.start(sl.getFilteredStream());
                }
            });
        }
        handler.setProgress(null, i.get());
        handler.setProgress2(null, j.get());
    }

    /**
     * Scans a folder matching standard profile designations and registers matching, unknown, or unneeded content properties.
     * 
     * @param byname profile names categorizer
     * @param dstdir destination path folder
     * @param unknown unknown containers collection
     * @param unneeded unneeded containers collection
     * @param handler active progress tracker
     * 
     * @return directory scanner instance
     */
    private DirScan dirscan(final ByName<?> byname, final File dstdir, final List<Container> unknown, final List<Container> unneeded, final ProgressHandler handler) {
        final DirScan dstScan;
        dstScan = new DirScan(profile, dstdir, handler, true);
        allScans.add(dstScan);
        for (final Container c : dstScan.getContainersIterable()) {
            if (c.getType() == Type.UNK)
                unknown.add(c);
            else if (c.getType() == Type.DIR && format == FormatOptions.FAKE)
                unknown.add(c);
            else if (!byname.containsFilteredName(getBaseName(c.getFile()))) {
                if (byname.containsName(getBaseName(c.getFile())))
                    unneeded.add(c);
                else
                    unknown.add(c);
            }
        }
        return dstScan;
    }

    /**
     * File extension pattern parser helper.
     */
    private static final Pattern baseNameMatch = Pattern.compile("^(.*?)(\\.\\w{1,5})?$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.UNICODE_CASE);

    /**
     * Extracts and strips extensions from filenames.
     * 
     * @param file file handle target
     * 
     * @return stripped raw filename string
     */
    private static String getBaseName(File file) {
        String name = file.getName();
        final var matcher = baseNameMatch.matcher(name);
        if (matcher.find() && matcher.groupCount() > 0)
            return matcher.group(1);
        return name;
    }

    /**
     * Checks and updates TorrentZip formatting configurations final step repair operations.
     * 
     * @param reportSubject active audited subject report
     * @param archive the target zipped archive container
     * @param ware the related software list or arcade machine definition
     * @param roms profile filtered ROMs list
     */
    private void prepTZip(final SubjectSet reportSubject, final Container archive, final Anyware ware, final List<Rom> roms) {
        if (format != FormatOptions.TZIP || (mergeMode.isMerge() && ware.isClone()) || reportSubject.isMissing() || reportSubject.isUnneeded() || roms.isEmpty())
            return;
        Optional<Container> tzipcontainer = Optional.empty();
        final Container container = romsDstScan.getContainerByName(ware.getDest().getName() + format.getExt());
        if (container != null) {
            if (container.getLastTZipCheck() < container.getModified() || !container.getLastTZipStatus().contains(TrrntZipStatus.VALIDTRRNTZIP) || reportSubject.hasFix())
                tzipcontainer = Optional.of(container);
        } else if (createMode) {
            if (createFullMode) {
                if (reportSubject.isFixable())
                    tzipcontainer = Optional.of(archive);
            } else if (reportSubject.hasFix())
                tzipcontainer = Optional.of(archive);
        }
        tzipcontainer.ifPresent(c -> {
            final long estimatedRomsSize = roms.stream().mapToLong(Rom::getSize).sum();
            c.setRelAW(ware);
            tzipActions.put(c.getFile().getAbsolutePath(), new TZipContainer(c, format, estimatedRomsSize));
            report.add(new ContainerTZip(c));
        });
    }

    /**
     * Checks and updates TorrentZip formatting configurations for audio samples archives.
     * 
     * @param reportSubject audited subject report
     * @param archive the target container
     * @param set samples tracking group definition
     */
    private void prepTZip(final SubjectSet reportSubject, final Container archive, final Samples set) {
        if (format == FormatOptions.TZIP && !reportSubject.isMissing() && !reportSubject.isUnneeded() && set.getSamplesMap().size() > 0) {
            Container tzipcontainer = null;
            final Container container = samplesDstScan.getContainerByName(archive.getFile().getName());
            if (container != null) {
                if (container.getLastTZipCheck() < container.getModified() || !container.getLastTZipStatus().contains(TrrntZipStatus.VALIDTRRNTZIP) || reportSubject.hasFix())
                    tzipcontainer = container;
            } else if (createMode && reportSubject.hasFix())
                tzipcontainer = archive;
            if (tzipcontainer != null) {
                tzipcontainer.setRelAW(set);
                tzipActions.put(tzipcontainer.getFile().getAbsolutePath(), new TZipContainer(tzipcontainer, format, Long.MAX_VALUE));
                report.add(new ContainerTZip(tzipcontainer));
            }
        }
    }

    /**
     * Cleans up mismatched target format files on disk if configurations change.
     * 
     * @param ware active target software/machine representation
     */
    private void removeOtherFormats(final Anyware ware) {
        format.getExt().allExcept().forEach(e -> {
            final Container c = romsDstScan.getContainerByName(ware.getName() + e);
            if (c != null) {
                report.add(new ContainerUnneeded(c));
                backupActions.add(new BackupContainer(c));
                deleteActions.add(new DeleteContainer(c, format));
            }
        });
    }

    /**
     * Cleans up separate clone containers when rebuild properties migrate to merged models.
     * 
     * @param ware the active software/machine
     * @param disks the CHD images definition
     * @param roms the ROMs definition
     */
    private void removeUnneededClone(final Anyware ware, final List<Disk> disks, final List<Rom> roms) {
        if (mergeMode.isMerge() && ware.isClone()) {
            if (format == FormatOptions.DIR && disks.isEmpty() && roms.isEmpty()) {
                Arrays.asList(romsDstScan.getContainerByName(ware.getName()), disksDstScan.getContainerByName(ware.getName())).forEach(c -> {
                    if (c != null) {
                        report.add(new ContainerUnneeded(c));
                        backupActions.add(new BackupContainer(c));
                        deleteActions.add(new DeleteContainer(c, format));
                    }
                });
            } else if (disks.isEmpty()) {
                Optional.ofNullable(disksDstScan.getContainerByName(ware.getName())).ifPresent(c -> {
                    report.add(new ContainerUnneeded(c));
                    backupActions.add(new BackupContainer(c));
                    deleteActions.add(new DeleteContainer(c, format));
                });
            }
            if (format != FormatOptions.DIR && roms.isEmpty()) {
                Optional.ofNullable(romsDstScan.getContainerByName(ware.getName() + format.getExt())).ifPresent(c -> {
                    report.add(new ContainerUnneeded(c));
                    backupActions.add(new BackupContainer(c));
                    deleteActions.add(new DeleteContainer(c, format));
                });
            }
        }
    }

    /**
     * Inspects and audits disk images inside target folders.
     * 
     * @param ware active machine software definition
     * @param disks active disks CHD listing
     * @param directory parent container folder target
     * @param reportSubject audited subject report
     * 
     * @return {@code true} if the disk image is missing, otherwise {@code false}
     */
    private boolean scanDisks(final Anyware ware, final List<Disk> disks, final Directory directory, final SubjectSet reportSubject) {
        final Container container = disksDstScan.getContainerByName(ware.getDest().getNormalizedName());
        if (null != container) {
            scanDisksForFoundContainer(disks, directory, reportSubject, container);
            return false;
        } else {
            scanDisksForMissingContainer(disks, directory, reportSubject);
            return true;
        }
    }

    /**
     * Handles audit checks when disk image containers are completely missing from destination paths.
     * 
     * @param disks active disk definition list
     * @param directory parent folder target
     * @param reportSubject audited subject report
     */
    private void scanDisksForMissingContainer(final List<Disk> disks, final Directory directory, final SubjectSet reportSubject) {
        for (final Disk disk : disks)
            disk.setStatus(EntityStatus.KO);
        if (!createMode || disks.isEmpty())
            return;
        int disksFound = 0;
        boolean partialSet = false;
        final var createSet = new AtomicReference<CreateContainer>();
        for (final Disk disk : disks) {
            report.getStats().incMissingDisksCnt();
            Entry foundEntry = searchDiskInAllScans(disk);
            if (foundEntry != null) {
                report.getStats().incFixableDisksCnt();
                reportSubject.add(new EntryAdd(disk, foundEntry));
                CreateContainer.getInstance(createSet, directory, format, 0L).addAction(new AddEntry(disk, foundEntry));
                disksFound++;
            } else {
                reportSubject.add(new EntryMissing(disk));
                partialSet = true;
            }
        }
        if (disksFound > 0 && (!createFullMode || !partialSet)) {
            reportSubject.setCreateFull();
            if (partialSet)
                reportSubject.setCreate();
            ContainerAction.addToList(createActions, createSet.get());
        }
    }

    /**
     * Searches directories map indexes to resolve matching entries for a disk.
     * 
     * @param disk disk properties details
     * 
     * @return physical file entry, or {@code null} if unmatched
     */
    private Entry searchDiskInAllScans(final Disk disk) {
        for (final DirScan scan : allScans) {
            final Entry foundEntry = scan.findByHash(disk);
            if (null != foundEntry)
                return foundEntry;
        }
        return null;
    }

    /**
     * Evaluates and maps entry gaps inside a discovered disk destination container.
     * 
     * @param disks active disk definition list
     * @param directory parent folder target
     * @param reportSubject audited subject report
     * @param container discovered destination container
     */
    private void scanDisksForFoundContainer(final List<Disk> disks, final Directory directory, final SubjectSet reportSubject, final Container container) {
        if (disks.isEmpty())
            return;
        reportSubject.setFound();

        final var scanData = new ScanDisksData(disks, container);

        for (final Disk disk : disks) {
            disk.setStatus(EntityStatus.KO);
            Entry foundEntry = Optional.ofNullable(findEntriesByHash(scanData, disk))
                    .map(entries -> scanDisksEntries(directory, reportSubject, scanData, disk, entries))
                    .orElse(null);

            final Entry wrongHash = foundEntry == null ? checkWrongHash(scanData, disk) : null;

            if (foundEntry == null) {
                report.getStats().incMissingDisksCnt();

                foundEntry = searchDiskInAllScans(disk);
                if (foundEntry != null) {
                    report.getStats().incFixableDisksCnt();
                    reportSubject.add(new EntryAdd(disk, foundEntry));
                    OpenContainer.getInstance(scanData.addSet, directory, format, 0L).addAction(new AddEntry(disk, foundEntry));
                } else
                    reportSubject.add(wrongHash != null ? new EntryWrongHash(disk, wrongHash) : new EntryMissing(disk));
            } else {
                disk.setStatus(EntityStatus.OK);
                reportSubject.add(new EntryOK(disk));
                scanData.found.add(foundEntry);
            }
        }
        removeUnneededEntries(directory, reportSubject, container, scanData);
        ContainerAction.addToList(renameBeforeActions, scanData.renameBeforeSet.get());
        ContainerAction.addToList(duplicateActions, scanData.duplicateSet.get());
        ContainerAction.addToList(addActions, scanData.addSet.get());
        ContainerAction.addToList(deleteActions, scanData.deleteSet.get());
        ContainerAction.addToList(renameAfterActions, scanData.renameAfterSet.get());
    }

    /**
     * Iterates over potential matching entries to verify filenames.
     * 
     * @param directory parent directory folder
     * @param reportSubject subject report
     * @param scanData hashing cache structures
     * @param disk disk specifications
     * @param entries resolved entries list
     * 
     * @return matching entry details
     */
    private Entry scanDisksEntries(final Directory directory, final SubjectSet reportSubject, final ScanDisksData scanData, final Disk disk, final List<Entry> entries) {
        for (final var candidate_entry : entries) {
            Log.debug(() -> "The entry " + candidate_entry.getName() + " match hash from disk " + disk.getNormalizedName());
            if (!disk.getNormalizedName().equals(candidate_entry.getName())) {
                if (scanDisksEntriesNameMismatch(directory, reportSubject, scanData, disk, candidate_entry))
                    return candidate_entry;
            } else {
                Log.debug(() -> "\tThe entry " + candidate_entry.getName() + " match hash and name for disk " + disk.getNormalizedName());
                return candidate_entry;
            }
        }
        return null;
    }

    /**
     * Queues deletion for extra unneeded entries residing in audited folders.
     * 
     * @param directory parent directory folder
     * @param reportSubject subject report
     * @param container audited container
     * @param data tracking structures
     */
    private void removeUnneededEntries(final Directory directory, final SubjectSet reportSubject, final Container container, final ScanDisksData data) {
        if (!ignoreUnneededEntries) {
            final List<Entry> unneeded = container.getEntries().stream().filter(Scan.not(new HashSet<>(data.found)::contains)).toList();
            for (final Entry unneeded_entry : unneeded) {
                reportSubject.add(new EntryUnneeded(unneeded_entry));
                OpenContainer.getInstance(data.renameBeforeSet, directory, format, 0L).addAction(new RenameEntry(unneeded_entry));
                OpenContainer.getInstance(data.deleteSet, directory, format, 0L).addAction(new DeleteEntry(unneeded_entry));
            }
        }
    }

    /**
     * Handles disk filename discrepancies when checksums match.
     * 
     * @param directory parent folder
     * @param reportSubject subject report
     * @param data tracking structures
     * @param disk disk details
     * @param candidateEntry matched checksum file entry
     * 
     * @return {@code true} if discrepancy resolved, otherwise {@code false}
     */
    @SuppressWarnings("unlikely-arg-type")
    private boolean scanDisksEntriesNameMismatch(final Directory directory, final SubjectSet reportSubject, final ScanDisksData data, final Disk disk, final Entry candidateEntry) {
        Log.debug(() -> "\tbut this disk name does not match the disk name");
        final Disk anotherDisk = data.disksByName.get(candidateEntry.getName());
        if (null != anotherDisk && candidateEntry.equals(anotherDisk)) // NOSONAR
        {
            if (scanDisksEntriesNameRetrieved(directory, reportSubject, data, disk, candidateEntry))
                return true;
        } else {
            if (anotherDisk == null)
                Log.debug(() -> "\t" + candidateEntry.getName() + " in disksByName not found (" + data.disksByName.keySet().stream().collect(Collectors.joining(", ")) + ")");
            else
                Log.debug(() -> "\t" + candidateEntry.getName() + " in disksByName found but does not match hash");
            if (!data.entriesByName.containsKey(disk.getNormalizedName())) {
                Log.debug(() -> "\t\tand disk " + disk.getNormalizedName() + " is NOT in the entriesByName ("
                        + data.entriesByName.keySet().stream().collect(Collectors.joining(", ")) + ")");
                if (!data.markedForRename.contains(candidateEntry))
                    scanRename(directory, reportSubject, 0L, data, disk, candidateEntry);
                else
                    scanDuplicate(directory, reportSubject, 0L, data, disk, candidateEntry);
                return true;
            }
        }
        return false;
    }

    /**
     * Suggests dynamic file duplication if a file serves multiple purposes.
     * 
     * @param directory parent folder
     * @param reportSubject subject report
     * @param data tracking structures
     * @param disk disk details
     * @param candidateEntry file entry
     * 
     * @return {@code true} if suggestion queued, otherwise {@code false}
     */
    private boolean scanDisksEntriesNameRetrieved(final Directory directory, final SubjectSet reportSubject, final ScanDisksData data, final Disk disk,
            final Entry candidateEntry) {
        Log.debug(() -> "\t\t\tand the entry " + candidateEntry.getName() + " is ANOTHER disk");
        if (data.entriesByName.containsKey(disk.getNormalizedName())) {
            Log.debug(() -> String.format("\t\t\t\tand disk %s is in the entriesByName", disk.getNormalizedName()));
        } else {
            Log.debug(() -> "\\t\\t\\t\\twe must duplicate disk " + disk.getNormalizedName() + " to ");
            scanDuplicate(directory, reportSubject, 0L, data, disk, candidateEntry);
            return true;
        }
        return false;
    }

    /**
     * Evaluates and audits ROMs inside destination packages.
     * 
     * @param ware audited software/arcade machine
     * @param roms filtered ROMs listing
     * @param archive audited container properties
     * @param reportSubject subject report
     * 
     * @return {@code true} if container is missing, otherwise {@code false}
     */
    private boolean scanRoms(final Anyware ware, final List<Rom> roms, final Container archive, final SubjectSet reportSubject) {
        final long estimatedRomsSize = roms.stream().mapToLong(Rom::getSize).sum();

        final Container container = romsDstScan.getContainerByName(ware.getDest().getNormalizedName() + format.getExt());
        if (null != container) {
            scanRomsForFoundContainer(roms, archive, reportSubject, container, estimatedRomsSize);
            return false;
        } else {
            scanRomsForMissingContainer(roms, archive, reportSubject, estimatedRomsSize);
            return true;
        }
    }

    /**
     * Core scanner tracking state cache.
     */
    private abstract class ScanData {
        /** Constructs a set of entries to be added. */
        protected final AtomicReference<OpenContainer> addSet = new AtomicReference<>();
        /** Constructs a set of entries to be removed. */
        protected final AtomicReference<OpenContainer> deleteSet = new AtomicReference<>();
        /** Constructs a set of entries to be renamed. */
        protected final AtomicReference<OpenContainer> renameBeforeSet = new AtomicReference<>();
        /** Constructs a set of entries to be renamed. */
        protected final AtomicReference<OpenContainer> renameAfterSet = new AtomicReference<>();
        /** Constructs a set of entries to be duplicated. */
        protected final AtomicReference<OpenContainer> duplicateSet = new AtomicReference<>();

        /** Constructs a set of entries found. */
        protected final List<Entry> found = new ArrayList<>();
        /** Constructs a set of entries by name. */
        protected final Map<String, Entry> entriesByName;
        /** Constructs a set of entries for renaming. */
        protected final Set<Entry> markedForRename = new HashSet<>();

        /** Constructs a new ScanData instance.
         * 
         * @param container the audited container
         */
        protected ScanData(final Container container) {
            entriesByName = container.getEntriesByName();
        }
    }

    /**
     * ROM scanning tracking state cache.
     */
    private final class ScanRomsData extends ScanHashData {
        /*** Constructs a backup container. */
        protected final AtomicReference<BackupContainer> backupSet = new AtomicReference<>();

        /*** Constructs a ROMs list by name. */
        protected final Map<String, Rom> romsByName;

        /** Constructs a new ScanRomsData instance.
         * 
         * @param roms the ROMs list to index by name
         * @param container the audited container
         */
        public ScanRomsData(final List<Rom> roms, final Container container) {
            super(container);
            romsByName = Rom.getRomsByName(roms);
        }
    }

    /**
     * Hashing details parsing cache helper.
     */
    private abstract class ScanHashData extends ScanData {
        protected final HashMap<String, List<Entry>> entriesBySha1 = new HashMap<>();
        protected final HashMap<String, List<Entry>> entriesByMd5 = new HashMap<>();
        protected final HashMap<String, List<Entry>> entriesByCrc = new HashMap<>();

        /**
         * Constructs a new ScanHashData instance.
         * 
         * @param container the audited container
         */
        protected ScanHashData(final Container container) {
            super(container);
            initHashesFromContainerEntries(container);
        }

        /**
         * Populates hash lookup maps from container entries.
         * 
         * @param container the container to index by hash values
         */
        private void initHashesFromContainerEntries(final Container container) {
            container.getEntries().forEach(e -> {
                if (e.getSha1() != null)
                    entriesBySha1.computeIfAbsent(e.getSha1(), _ -> new ArrayList<>()).add(e);
                if (e.getMd5() != null)
                    entriesByMd5.computeIfAbsent(e.getMd5(), _ -> new ArrayList<>()).add(e);
                if (e.getCrc() != null)
                    entriesByCrc.computeIfAbsent(e.getCrc() + '.' + e.getSize(), _ -> new ArrayList<>()).add(e);
            });
        }
    }

    /**
     * Disk CHD scanning tracking state cache.
     */
    private final class ScanDisksData extends ScanHashData {
        /*** Disk definitions indexed by name.  */
        final Map<String, Disk> disksByName;

        /**
         * Constructs a new ScanDisksData instance.
         * 
         * @param disks the CHD disk definitions
         * @param container the audited container
         */
        public ScanDisksData(final List<Disk> disks, final Container container) {
            super(container);
            disksByName = Disk.getDisksByName(disks);
        }

    }

    /**
     * Audio samples scanning tracking state cache.
     */
    private final class ScanSamplesData extends ScanData {

        /** Constructs a new ScanSamplesData instance.
         * 
         * @param container the audited container
         */
        public ScanSamplesData(Container container) {
            super(container);
        }

    }

    /**
     * Scans and correlates ROMs inside a found destination file container.
     * 
     * @param roms active ROM definitions
     * @param archive audited container
     * @param reportSubject subject report
     * @param container destination audited container properties
     * @param estimatedRomsSize size metrics
     */
    private void scanRomsForFoundContainer(final List<Rom> roms, final Container archive, final SubjectSet reportSubject, final Container container, final long estimatedRomsSize) {
        if (roms.isEmpty())
            return;
        reportSubject.setFound();

        final var scanData = new ScanRomsData(roms, container);

        for (final Rom rom : roms) {
            rom.setStatus(EntityStatus.KO);

            Entry foundEntry = Optional.ofNullable(findEntriesByHash(scanData, rom))
                    .map(entries -> scanRomsEntries(archive, reportSubject, estimatedRomsSize, scanData, rom, entries))
                    .orElse(null);

            final Entry wrongHash = foundEntry == null ? checkWrongHash(scanData, rom) : null;

            if (foundEntry == null) {
                report.getStats().incMissingRomsCnt();

                foundEntry = searchRomInAllScans(rom);
                if (foundEntry != null) {
                    report.getStats().incFixableRomsCnt();
                    reportSubject.add(new EntryAdd(rom, foundEntry));
                    OpenContainer.getInstance(scanData.addSet, archive, format, estimatedRomsSize).addAction(new AddEntry(rom, foundEntry));
                } else
                    reportSubject.add(wrongHash != null ? new EntryWrongHash(rom, wrongHash) : new EntryMissing(rom));
            } else {
                rom.setStatus(EntityStatus.OK);
                reportSubject.add(new EntryOK(rom));
                scanData.found.add(foundEntry);
            }
        }

        removeUnneededEntries(archive, reportSubject, container, estimatedRomsSize, scanData);

        ContainerAction.addToList(backupActions, scanData.backupSet.get());
        ContainerAction.addToList(renameBeforeActions, scanData.renameBeforeSet.get());
        ContainerAction.addToList(duplicateActions, scanData.duplicateSet.get());
        ContainerAction.addToList(addActions, scanData.addSet.get());
        ContainerAction.addToList(deleteActions, scanData.deleteSet.get());
        ContainerAction.addToList(renameAfterActions, scanData.renameAfterSet.get());
    }

    /**
     * Detects wrong checksum errors when filenames are correct.
     * 
     * @param scanData scan cache
     * @param rom ROM properties
     * 
     * @return physical file entry, or {@code null}
     */
    private Entry checkWrongHash(final ScanRomsData scanData, final Rom rom) {
        final var candidateEntry = scanData.entriesByName.get(rom.getNormalizedName());
        if (candidateEntry != null) {
            Log.debug(() -> "\tOups! we got wrong hash in " + candidateEntry.getName() + " for " + rom.getNormalizedName());
            return candidateEntry;
        }
        return null;
    }

    /**
     * Detects wrong checksum errors when filenames are correct for CHD disks.
     * 
     * @param scanData scan cache
     * @param disk disk properties
     * 
     * @return physical disk file entry, or {@code null}
     */
    private Entry checkWrongHash(final ScanDisksData scanData, final Disk disk) {
        final var candidateEntry = scanData.entriesByName.get(disk.getNormalizedName());
        if (candidateEntry != null) {
            Log.debug(() -> "\tOups! we got wrong hash in " + candidateEntry.getName() + " for " + disk.getNormalizedName());
            return candidateEntry;
        }
        return null;
    }

    /**
     * Deletes extra files discovered in a target ZIP or folder package.
     * 
     * @param archive audited container
     * @param reportSubject subject report
     * @param container container properties
     * @param estimatedRomsSize size metrics
     * @param scanData scan cache
     */
    private void removeUnneededEntries(final Container archive, final SubjectSet reportSubject, final Container container, final long estimatedRomsSize,
            final ScanRomsData scanData) {
        if (!ignoreUnneededEntries) {
            final List<Entry> unneeded = container.getEntries().stream().filter(Scan.not(new HashSet<>(scanData.found)::contains)).toList();
            for (final Entry unneeded_entry : unneeded) {
                reportSubject.add(new EntryUnneeded(unneeded_entry));
                BackupContainer.getInstance(scanData.backupSet, archive).addAction(new BackupEntry(unneeded_entry));
                OpenContainer.getInstance(scanData.renameBeforeSet, archive, format, estimatedRomsSize).addAction(new RenameEntry(unneeded_entry));
                OpenContainer.getInstance(scanData.deleteSet, archive, format, estimatedRomsSize).addAction(new DeleteEntry(unneeded_entry));
            }
        }
    }

    /**
     * Verifies matching entries lists for filename alignment.
     * 
     * @param archive audited container
     * @param reportSubject subject report
     * @param estimatedRomsSize size metrics
     * @param scanData scan cache
     * @param rom ROM definitions
     * @param entries entries list
     * 
     * @return matching entry details
     */
    private Entry scanRomsEntries(final Container archive, final SubjectSet reportSubject, final long estimatedRomsSize, final ScanRomsData scanData, final Rom rom,
            final List<Entry> entries) {
        for (final var candidate_entry : entries) {
            Log.debug(() -> "The entry " + candidate_entry.getName() + " match hash from rom " + rom.getNormalizedName());
            if (!rom.getNormalizedName().equals(candidate_entry.getName())) {
                if (scanRomsEntriesNameMismatch(archive, reportSubject, estimatedRomsSize, scanData, rom, candidate_entry))
                    return candidate_entry;
            } else {
                Log.debug(() -> "\tThe entry " + candidate_entry.getName() + " match hash and name for rom " + rom.getNormalizedName());
                return candidate_entry;
            }
        }
        return null;
    }

    /**
     * Handles filename mismatch when ROM checksum matches.
     * 
     * @param archive audited container
     * @param reportSubject subject report
     * @param estimatedRomsSize size metrics
     * @param scanData scan cache
     * @param rom ROM definitions
     * @param candidateEntry matched checksum file entry
     * 
     * @return {@code true} if resolved, otherwise {@code false}
     */
    @SuppressWarnings("unlikely-arg-type")
    private boolean scanRomsEntriesNameMismatch(final Container archive, final SubjectSet reportSubject, final long estimatedRomsSize, final ScanRomsData scanData, final Rom rom,
            final Entry candidateEntry) {
        Log.debug(() -> "\tbut this entry name does not match the rom name");
        final Rom anotherRom = scanData.romsByName.get(candidateEntry.getName());
        if (null != anotherRom && candidateEntry.equals(anotherRom)) // NOSONAR
        {
            if (scanRomsEntriesNameRetrieved(archive, reportSubject, estimatedRomsSize, scanData, rom, candidateEntry))
                return true;
        } else {
            if (anotherRom == null)
                Log.debug(() -> "\t" + candidateEntry.getName() + " in romsByName not found (" + scanData.romsByName.keySet().stream().collect(Collectors.joining(", ")) + ")");
            else
                Log.debug(() -> "\t" + candidateEntry.getName() + " in romsByName found but does not match hash");

            if (!scanData.entriesByName.containsKey(rom.getNormalizedName())) {
                Log.debug(() -> "\t\tand rom " + rom.getNormalizedName() + " is NOT in the entriesByName ("
                        + scanData.entriesByName.keySet().stream().collect(Collectors.joining(", ")) + ")");

                if (!scanData.markedForRename.contains(candidateEntry))
                    scanRename(archive, reportSubject, estimatedRomsSize, scanData, rom, candidateEntry);
                else
                    scanDuplicate(archive, reportSubject, estimatedRomsSize, scanData, rom, candidateEntry);
                return true;
            } else
                Log.debug(() -> "\t\tand rom " + rom.getNormalizedName() + " is in the entriesByName");
        }
        return false;
    }

    /**
     * Suggests filename duplication when files are shared across parent/clones.
     * 
     * @param archive audited container
     * @param reportSubject subject report
     * @param estimatedRomsSize size metrics
     * @param scanData scan cache
     * @param rom ROM properties
     * @param candidateEntry matched checksum file entry
     * 
     * @return {@code true} if suggestions queued, otherwise {@code false}
     */
    private boolean scanRomsEntriesNameRetrieved(final Container archive, final SubjectSet reportSubject, final long estimatedRomsSize, final ScanRomsData scanData, final Rom rom,
            final Entry candidateEntry) {
        Log.debug(() -> "\t\t\tand the entry " + candidateEntry.getName() + " is ANOTHER rom");
        if (scanData.entriesByName.containsKey(rom.getNormalizedName()))
            Log.debug(() -> String.format("\t\t\t\tand rom %s is in the entriesByName", rom.getNormalizedName()));
        else {
            Log.debug(() -> "\\t\\t\\t\\twe must duplicate rom " + rom.getNormalizedName() + " to ");
            scanDuplicate(archive, reportSubject, estimatedRomsSize, scanData, rom, candidateEntry);
            return true;
        }
        return false;
    }

    /**
     * Queues duplication actions.
     * 
     * @param container parent package
     * @param reportSubject subject report
     * @param estimatedRomsSize size metrics
     * @param scanData scan cache
     * @param entity target ROM or disk image metadata reference
     * @param entry matching source file entry
     */
    private void scanDuplicate(final Container container, final SubjectSet reportSubject, final long estimatedRomsSize, final ScanData scanData, final Entity entity,
            final Entry entry) {
        reportSubject.add(new EntryMissingDuplicate(entity, entry));
        OpenContainer.getInstance(scanData.duplicateSet, container, format, estimatedRomsSize).addAction(new DuplicateEntry(entity.getName(), entry));
    }

    /**
     * Queues file renaming actions to fix incorrect spelling.
     * 
     * @param container parent package
     * @param reportSubject subject report
     * @param estimatedRomsSize size metrics
     * @param data scan cache
     * @param entity target ROM or disk image reference
     * @param entry misspelled file entry
     */
    private void scanRename(final Container container, final SubjectSet reportSubject, final long estimatedRomsSize, final ScanData data, final Entity entity, final Entry entry) {
        reportSubject.add(new EntryWrongName(entity, entry));
        OpenContainer.getInstance(data.renameBeforeSet, container, format, estimatedRomsSize).addAction(new RenameEntry(entry));
        OpenContainer.getInstance(data.renameAfterSet, container, format, estimatedRomsSize).addAction(new RenameEntry(entity.getName(), entry));
        data.markedForRename.add(entry);
    }

    /**
     * Suggestions auditing ROM containers when packages are completely missing from destination paths.
     * 
     * @param roms filtered ROMs list
     * @param archive mismatched container
     * @param reportSubject subject report
     * @param estimatedRomsSize size metrics
     */
    private void scanRomsForMissingContainer(final List<Rom> roms, final Container archive, final SubjectSet reportSubject, final long estimatedRomsSize) {
        for (final Rom rom : roms)
            rom.setStatus(EntityStatus.KO);
        if (!createMode || roms.isEmpty())
            return;
        int romsFound = 0;
        boolean partialSet = false;
        final var createSet = new AtomicReference<CreateContainer>();
        for (final Rom rom : roms) {
            report.getStats().incMissingRomsCnt();
            final Entry entryFound = searchRomInAllScans(rom);
            if (null != entryFound) {
                report.getStats().incFixableRomsCnt();
                reportSubject.add(new EntryAdd(rom, entryFound));
                CreateContainer.getInstance(createSet, archive, format, estimatedRomsSize).addAction(new AddEntry(rom, entryFound));
                romsFound++;
            } else {
                reportSubject.add(new EntryMissing(rom));
                partialSet = true;
            }
        }
        if (romsFound > 0 && (!createFullMode || !partialSet)) {
            reportSubject.setCreateFull();
            if (partialSet)
                reportSubject.setCreate();
            ContainerAction.addToList(createActions, createSet.get());
        }
    }

    /**
     * Searches directories scan listings to match a single ROM properties.
     * 
     * @param rom target ROM definitions
     * 
     * @return physical file entry, or {@code null}
     */
    private Entry searchRomInAllScans(final Rom rom) {
        for (final DirScan scan : allScans) {
            final var foundEntry = scan.findByHash(rom);
            if (null != foundEntry)
                return foundEntry;
        }
        return null;
    }

    /**
     * Selects matching entry properties using hash metrics from ROM.
     * 
     * @param scanData scan cache
     * @param rom ROM definitions
     * 
     * @return potential matching entries list
     */
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

    /**
     * Selects matching entry properties using hash metrics from CHD disk.
     * 
     * @param scanData scan cache
     * @param disk disk properties
     * 
     * @return potential matching entries list
     */
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

    /**
     * Performs audit checks over audio samples sets.
     * 
     * @param set active samples group details
     */
    private void scanSamples(final Samples set) {
        boolean missingSet = true;
        final Container archive;
        if (format.getExt().isDir()) {
            final var f = new File(samplesDstScan.getDir(), set.getName());
            archive = new Directory(f, getRelativePath(f), set);
        } else {
            final var f = new File(samplesDstScan.getDir(), set.getName() + format.getExt());
            archive = new Archive(f, getRelativePath(f), set);
        }
        final SubjectSet reportSubject = new SubjectSet(set);
        if (!scanSamples(set, archive, reportSubject))
            missingSet = false;
        if (createMode && reportSubject.getStatus() == Status.UNKNOWN)
            reportSubject.setMissing();
        if (missingSet)
            report.getStats().incMissingSetCnt();
        if (reportSubject.getStatus() != Status.UNKNOWN)
            report.add(reportSubject);
        prepTZip(reportSubject, archive, set);
    }

    /**
     * Audits audio sample sets inside scanned target folders.
     * 
     * @param set active samples group details
     * @param archive audited container properties
     * @param reportSubject subject report
     * 
     * @return {@code true} if audio set container is missing, otherwise {@code false}
     */
    private boolean scanSamples(final Samples set, final Container archive, final SubjectSet reportSubject) {
        final Container container = samplesDstScan.getContainerByName(archive.getFile().getName());
        if (null != container) {
            scanSamplesForFoundContainer(set, archive, reportSubject, container);
            return false;
        } else {
            scanSamplesForMissingContainer(set, archive, reportSubject);
            return true;
        }
    }

    /**
     * Handles audit checks when audio sample containers are found.
     * 
     * @param set active samples definitions
     * @param archive audited container
     * @param reportSubject subject report
     * @param container discovered container
     */
    private void scanSamplesForFoundContainer(final Samples set, final Container archive, final SubjectSet reportSubject, final Container container) {
        reportSubject.setFound();

        final var data = new ScanSamplesData(container);

        for (final Sample sample : set) {
            sample.setStatus(EntityStatus.KO);
            Entry foundEntry = scanSamplesEntries(container, sample);
            if (foundEntry == null) {
                report.getStats().incMissingSamplesCnt();
                foundEntry = searchSampleInAllScans(set, sample);
                if (foundEntry != null) {
                    reportSubject.add(new EntryAdd(sample, foundEntry));
                    OpenContainer.getInstance(data.addSet, archive, format, Long.MAX_VALUE).addAction(new AddEntry(sample, foundEntry));
                } else
                    reportSubject.add(new EntryMissing(sample));
            } else {
                sample.setStatus(EntityStatus.OK);
                reportSubject.add(new EntryOK(sample));
                data.found.add(foundEntry);
            }
        }
        removeUnneededEntries(archive, reportSubject, container, data);

        ContainerAction.addToList(renameBeforeActions, data.renameBeforeSet.get());
        ContainerAction.addToList(duplicateActions, data.duplicateSet.get());
        ContainerAction.addToList(addActions, data.addSet.get());
        ContainerAction.addToList(deleteActions, data.deleteSet.get());
        ContainerAction.addToList(renameAfterActions, data.renameAfterSet.get());
    }

    /**
     * Deletes extra unneeded elements residing in audio sample folders.
     * 
     * @param archive audited container
     * @param reportSubject subject report
     * @param container container properties
     * @param data tracking structures
     */
    private void removeUnneededEntries(final Container archive, final SubjectSet reportSubject, final Container container, final ScanSamplesData data) {
        if (!ignoreUnneededEntries) {
            final List<Entry> unneeded = container.getEntries().stream().filter(Scan.not(new HashSet<>(data.found)::contains)).toList();
            for (final Entry unneededEntry : unneeded) {
                reportSubject.add(new EntryUnneeded(unneededEntry));
                OpenContainer.getInstance(data.renameBeforeSet, archive, format, Long.MAX_VALUE).addAction(new RenameEntry(unneededEntry));
                OpenContainer.getInstance(data.deleteSet, archive, format, Long.MAX_VALUE).addAction(new DeleteEntry(unneededEntry));
            }
        }
    }

    /**
     * Resolves matching audio entries using case-insensitive filename comparison.
     * 
     * @param container audited container
     * @param sample audio sample specifications
     * 
     * @return physical file entry, or {@code null}
     */
    @SuppressWarnings("unlikely-arg-type")
    private Entry scanSamplesEntries(final Container container, final Sample sample) {
        for (final Entry candidate_entry : container.getEntries()) {
            if (candidate_entry.equals(sample)) // NOSONAR
                return candidate_entry;
        }
        return null;
    }

    /**
     * Handles audit checks when audio sample containers are completely missing from folders.
     * 
     * @param set active samples definitions
     * @param archive mismatched container
     * @param reportSubject subject report
     */
    private void scanSamplesForMissingContainer(final Samples set, final Container archive, final SubjectSet reportSubject) {
        for (final Sample sample : set)
            sample.setStatus(EntityStatus.KO);
        if (!createMode)
            return;
        int samplesFound = 0;
        boolean partialSet = false;
        final var createSet = new AtomicReference<CreateContainer>();
        for (final Sample sample : set) {
            report.getStats().incMissingSamplesCnt();
            Entry entryFound = searchSampleInAllScans(set, sample);
            if (null != entryFound) {
                reportSubject.add(new EntryAdd(sample, entryFound));
                CreateContainer.getInstance(createSet, archive, format, Long.MAX_VALUE).addAction(new AddEntry(sample, entryFound));
                samplesFound++;
            } else {
                reportSubject.add(new EntryMissing(sample));
                partialSet = true;
            }
        }
        if (samplesFound > 0 && (!createFullMode || !partialSet)) {
            reportSubject.setCreateFull();
            if (partialSet)
                reportSubject.setCreate();
            ContainerAction.addToList(createActions, createSet.get());
        }
    }

    /**
     * Searches directories scan listings to match a single audio Sample.
     * 
     * @param set active audio samples definitions
     * @param sample audio sample details
     * 
     * @return physical file entry, or {@code null}
     */
    private Entry searchSampleInAllScans(final Samples set, final Sample sample) {
        for (final DirScan scan : allScans) {
            for (final FormatOptions.Ext ext : EnumSet.allOf(FormatOptions.Ext.class)) {
                final Container foundContainer = scan.getContainerByName(set.getName() + ext);
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

    /**
     * High-level orchestration verifying a specific game/machine software, launching ROM verification and CHD disk image audits
     * sequentially, compiling report metrics.
     * 
     * @param ware the software/machine definitions to audit
     */
    private void scanWare(final Anyware ware) // NOSONAR
    {
        var missingSet = true;

        final var reportSubject = new SubjectSet(ware);
        final var dd = new File(disksDstScan.getDir(), ware.getDest().getName());
        final var directory = new Directory(dd, getRelativePath(dd), ware);
        final var archive = getArchive(ware);
        final var roms = ware.filterRoms();
        final var disks = ware.filterDisks();
        if (!scanRoms(ware, roms, archive, reportSubject))
            missingSet = false;
        if (!scanDisks(ware, disks, directory, reportSubject))
            missingSet = false;
        if (roms.isEmpty() && disks.isEmpty()) {
            if (!(mergeMode.isMerge() && ware.isClone())) {
                if (!missingSet)
                    reportSubject.setUnneeded();
                else
                    reportSubject.setFound();
            }
            missingSet = false;
        } else if (createMode && reportSubject.getStatus() == Status.UNKNOWN)
            reportSubject.setMissing();
        prepTZip(reportSubject, archive, ware, roms);
        if (!ignoreUnneededContainers) {
            removeUnneededClone(ware, disks, roms);
            removeOtherFormats(ware);
            if (reportSubject.isUnneeded()) {
                backupActions.add(new BackupContainer(archive));
                deleteActions.add(new DeleteContainer(archive, format));
            }
        }
        if (missingSet)
            report.getStats().incMissingSetCnt();
        Optional.of(reportSubject).filter(s -> s.getStatus() != Status.UNKNOWN).ifPresent(report::add);
    }

    /**
     * Prepares a new file container representation adjusted for format settings.
     * 
     * @param ware machine/software specs
     * 
     * @return clean new container representation
     */
    private Container getArchive(final Anyware ware) {
        final Container archive;
        switch (format) {
            case FormatOptions.DIR -> {
                final var d = new File(romsDstScan.getDir(), ware.getDest().getName());
                archive = new Directory(d, getRelativePath(d), ware);
            }
            case FormatOptions.FAKE -> {
                final var fd = new File(romsDstScan.getDir(), ware.getDest().getName());
                archive = new FakeDirectory(fd, getRelativePath(fd), ware);
            }
            default -> {
                final var af = new File(romsDstScan.getDir(), ware.getDest().getName() + format.getExt());
                archive = new Archive(af, getRelativePath(af), ware);
            }
        }
        return archive;
    }

}
