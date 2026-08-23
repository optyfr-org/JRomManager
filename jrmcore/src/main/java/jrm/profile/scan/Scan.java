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
import java.util.Collection;
import java.util.Collections;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
import jrm.profile.data.Container;
import jrm.profile.data.Directory;
import jrm.profile.data.Disk;
import jrm.profile.data.Entity;
import jrm.profile.data.Entry;
import jrm.profile.data.FakeDirectory;
import jrm.profile.data.Machine;
import jrm.profile.data.Rom;
import jrm.profile.data.Samples;
import jrm.profile.data.Software;
import jrm.profile.fix.actions.BackupContainer;
import jrm.profile.fix.actions.DeleteContainer;
import jrm.profile.fix.actions.DuplicateEntry;
import jrm.profile.fix.actions.OpenContainer;
import jrm.profile.fix.actions.RenameEntry;
import jrm.profile.report.EntryMissingDuplicate;
import jrm.profile.report.EntryWrongName;
import jrm.profile.report.Report;
import jrm.profile.report.SubjectSet;
import jrm.profile.report.SubjectSet.Status;
import jrm.profile.scan.options.FormatOptions;
import jrm.profile.scan.options.MergeOptions;
import jrm.security.PathAbstractor;

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
	/*package*/ static final String WORK_BACKUP = "%work/backup";
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
	/*package*/ final Profile profile;

    /**
     * Progress tracker updating percentages and logs to the active user interface.
     */
	/*package*/ final ProgressHandler handler;

    /**
     * Active merge ruleset configuration.
     */
	/*package*/ final MergeOptions mergeMode;
    /**
     * Active format output selection.
     */
	/*package*/ final FormatOptions format;
    /**
     * Indicates whether the scanner should suggest container creation for missing items.
     */
	/*package*/ final boolean createMode;
    /**
     * Suggest rebuilding packages even when romsets are only partially complete.
     */
	/*package*/ final boolean createFullMode;
    /**
     * Ignore unneeded containers that do not correspond to any active game/romset in the DAT profile.
     */
	/*package*/ final boolean ignoreUnneededContainers;
    /**
     * Ignore extra files inside containers that do not belong to that romset definition.
     */
	/*package*/ final boolean ignoreUnneededEntries;
    /**
     * Ignore totally unrecognized files/folders discovered in the scanned destinations.
     */
	/*package*/ final boolean ignoreUnknownContainers;
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
	/*package*/ DirScan romsDstScan = null;
    /**
     * Scanned folders result mapping for disk images paths.
     */
	/*package*/ DirScan disksDstScan = null;
    /**
     * Scanned folders result mapping for sample audio assets paths.
     */
	/*package*/ DirScan samplesDstScan = null;
    /**
     * Software lists roms destination scanners indexed by list code name.
     */
	/*package*/ final Map<String, DirScan> swromsDstScans = new HashMap<>();
    /**
     * Software lists disk images destination scanners indexed by list code name.
     */
	/*package*/ Map<String, DirScan> swdisksDstScans = new HashMap<>();
    /**
     * Unified list gathering all physical scanners executed during the active run.
     */
	/*package*/ final List<DirScan> allScans = new ArrayList<>();

    /**
     * Backups collection group executed first to preserve original content prior to applying repairs.
     */
	/*package*/ final List<jrm.profile.fix.actions.ContainerAction> backupActions = Collections.synchronizedList(new ArrayList<>());
    /**
     * Creation actions suggestion collection.
     */
	/*package*/ final List<jrm.profile.fix.actions.ContainerAction> createActions = Collections.synchronizedList(new ArrayList<>());
    /**
     * renaming actions applied prior to deletions or imports.
     */
	/*package*/ final List<jrm.profile.fix.actions.ContainerAction> renameBeforeActions = Collections.synchronizedList(new ArrayList<>());
    /**
     * Standard folder and package entry addition actions.
     */
	/*package*/ final List<jrm.profile.fix.actions.ContainerAction> addActions = Collections.synchronizedList(new ArrayList<>());
    /**
     * Standard deletion actions.
     */
	/*package*/ final List<jrm.profile.fix.actions.ContainerAction> deleteActions = Collections.synchronizedList(new ArrayList<>());
    /**
     * Renaming actions executed after standard import additions complete.
     */
	/*package*/ final List<jrm.profile.fix.actions.ContainerAction> renameAfterActions = Collections.synchronizedList(new ArrayList<>());
    /**
     * duplicate file mapping actions.
     */
	/*package*/ final List<jrm.profile.fix.actions.ContainerAction> duplicateActions = Collections.synchronizedList(new ArrayList<>());
    /**
     * TorrentZip processing final step actions.
     */
	/*package*/ final Map<String, jrm.profile.fix.actions.ContainerAction> tzipActions = Collections.synchronizedMap(new HashMap<>());

    /**
     * Creates a negated predicate.
     * 
     * @param predicate the predicate to negate
     * @param <T> the predicate argument type
     * 
     * @return the negated predicate instance
     */
	static <T> Predicate<T> not(final Predicate<T> predicate) {
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

        final var setup = new ScanDirSetup(this);
        final File romsDstDir = setup.initRomsDstDir(profile);
        final File disksDstDir = setup.initDisksDstDir(profile, romsDstDir);
        final File swromsDstDir = setup.initSwRomsDstDir(profile, romsDstDir);
        final File swdisksDstDir = setup.initSwDisksDstDir(profile, swromsDstDir);
        final File samplesDstDir = setup.initSamplesDstDir(profile);
        final var srcdirs = setup.initSrcDirs(profile);

        setup.scanSrcDirs(profile, handler, scancache, srcdirs);

        try {
            final ArrayList<Container> unknown = new ArrayList<>();
            final ArrayList<Container> unneeded = new ArrayList<>();
            final ArrayList<Container> samplesUnknown = new ArrayList<>();
            final ArrayList<Container> samplesUnneeded = new ArrayList<>();
            setup.scanDstDirs(romsDstDir, disksDstDir, samplesDstDir, unknown, unneeded, samplesUnknown, samplesUnneeded);
            setup.scanSWDstDirs(romsDstDir, swromsDstDir, swdisksDstDir, unknown, unneeded);

            handler.setInfos(nThreads, null);

            setup.processAndReportUnknownActions(romsDstDir, disksDstDir, swromsDstDir, swdisksDstDir, samplesDstDir, unknown);
            setup.processAndReportUnneededActions(unneeded);
            setup.reportSuspiciousCrc();
            searchFixes();
        } catch (final BreakException e) {
            throw e;
        } catch (final Exception e) {
            Log.err("Other Exception when listing", e); //$NON-NLS-1$
        } finally {
            handler.setInfos(1, null);
            handler.setProgress(Messages.getString("Profile.SavingCache"), 0); //$NON-NLS-1$
            saveStats();
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
     * File extension pattern parser helper.
     */
	/*package*/ static final Pattern baseNameMatch = Pattern.compile("^(.*?)(\\.\\w{1,5})?$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.UNICODE_CASE);

    /**
     * Extracts and strips extensions from filenames.
     * 
     * @param file file handle target
     * 
     * @return stripped raw filename string
     */
	static String getBaseName(File file) {
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
	void prepTZip(final SubjectSet reportSubject, final Container archive, final Anyware ware, final List<Rom> roms) {
        new TZipPrep(this).prepTZip(reportSubject, archive, ware, roms);
    }

    /**
     * Checks and updates TorrentZip formatting configurations for audio samples archives.
     * 
     * @param reportSubject audited subject report
     * @param archive the target container
     * @param set samples tracking group definition
     */
	void prepTZip(final SubjectSet reportSubject, final Container archive, final Samples set) {
        new TZipPrep(this).prepTZip(reportSubject, archive, set);
    }

    /**
     * Cleans up mismatched target format files on disk if configurations change.
     * 
     * @param ware active target software/machine representation
     */
    private void removeOtherFormats(final Anyware ware) {
        new ScanCleanup(this).removeOtherFormats(ware);
    }

    /**
     * Cleans up separate clone containers when rebuild properties migrate to merged models.
     * 
     * @param ware the active software/machine
     * @param disks the CHD images definition
     * @param roms the ROMs definition
     */
    private void removeUnneededClone(final Anyware ware, final List<Disk> disks, final List<Rom> roms) {
        new ScanCleanup(this).removeUnneededClone(ware, disks, roms);
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
        return new DisksScan(this).scan(ware, disks, directory, reportSubject);
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
        return new RomsScan(this).scan(ware, roms, archive, reportSubject);
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
	void scanDuplicate(final Container container, final SubjectSet reportSubject, final long estimatedRomsSize, final ScanData scanData, final Entity entity,
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
	void scanRename(final Container container, final SubjectSet reportSubject, final long estimatedRomsSize, final ScanData data, final Entity entity, final Entry entry) {
        reportSubject.add(new EntryWrongName(entity, entry));
        OpenContainer.getInstance(data.renameBeforeSet, container, format, estimatedRomsSize).addAction(new RenameEntry(entry));
        OpenContainer.getInstance(data.renameAfterSet, container, format, estimatedRomsSize).addAction(new RenameEntry(entity.getName(), entry));
        data.markedForRename.add(entry);
    }

    /**
     * Performs audit checks over audio samples sets. Delegates to extracted SamplesScan.
     * 
     * @param set active samples group details
     */
    private void scanSamples(final Samples set) {
        new SamplesScan(this).scan(set);
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

    private void saveStats() {
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

}
