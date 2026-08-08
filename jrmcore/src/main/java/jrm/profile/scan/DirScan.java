/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.scan;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.CRC32;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import jrm.aui.progress.ProgressHandler;
import jrm.compressors.SevenZipArchive;
import jrm.compressors.ZipTools;
import jrm.digest.MDigest;
import jrm.digest.MDigest.Algo;
import jrm.io.chd.CHDInfoReader;
import jrm.locale.Messages;
import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.misc.MultiThreadingVirtual;
import jrm.misc.ProfileSettingsEnum;
import jrm.misc.SettingsEnum;
import jrm.profile.Profile;
import jrm.profile.data.Archive;
import jrm.profile.data.Container;
import jrm.profile.data.Container.Type;
import jrm.profile.data.Directory;
import jrm.profile.data.Disk;
import jrm.profile.data.Entry;
import jrm.profile.data.FakeDirectory;
import jrm.profile.data.Rom;
import jrm.profile.scan.options.FormatOptions;
import jrm.profile.scan.options.FormatOptions.Ext;
import jrm.security.PathAbstractor;
import jrm.security.Session;
import jtrrntzip.DummyLogCallback;
import jtrrntzip.SimpleTorrentZipOptions;
import jtrrntzip.TorrentZip;
import lombok.val;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
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
 * Parallel file, archive, and directory scanner. This class implements the core parallel scanning and checksum evaluation strategy.
 * It checks physical files against previous cached runs (loaded from standard cache serialization structures) and recalculates
 * checksums (CRC32, MD5, SHA-1) only when modification timestamps or sizes differ.
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public final class DirScan extends PathAbstractor {
    /**
     * Default string prefix indicating glob path matching.
     */
    private static final String GLOB = "glob:";
    /**
     * List of found {@link Container}s.
     */
    private final List<Container> containers = Collections.synchronizedList(new ArrayList<>());
    /**
     * Map of {@link Container}s by name {@link String}. Will be serialized for disk caching.
     */
    private final Map<String, Container> containersByName;
    /**
     * Map of {@link Entry} elements by CRC values.
     */
    private final Map<String, Entry> entriesByCrc = Collections.synchronizedMap(new HashMap<>());
    /**
     * Map of {@link Entry} elements by SHA-1 hash strings.
     */
    private final Map<String, Entry> entriesBySha1 = Collections.synchronizedMap(new HashMap<>());
    /**
     * Map of {@link Entry} elements by MD5 hash strings.
     */
    private final Map<String, Entry> entriesByMd5 = Collections.synchronizedMap(new HashMap<>());

    /**
     * Contains the detected suspicious CRCs from the current profile.
     */
    private Set<String> suspiciousCrc = null;

    /**
     * The current execution session.
     */
    private final Session session;

    /**
     * The root directory entry point.
     */
    private final File dir;
    /**
     * Progress tracking monitor showing completion percentage in the UI.
     */
    private final ProgressHandler handler;

    /**
     * List of file pattern path matchers representing target folder exclusions.
     */
    private List<Map.Entry<String, PathMatcher>> exclusions = Collections.emptyList();

    /**
     * Private helper structure aggregating scanning variables and options constraints.
     */
    private class ScanOptions {
        /**
         * Whether SHA-1 or MD5 calculation is explicitly required.
         */
        final boolean needSha1OrMd5;

        /**
         * Whether MD5 checks are requested for disk containers in the profile.
         */
        final boolean md5Disks;

        /**
         * Whether MD5 checks are requested for ROMs in the profile.
         */
        final boolean md5Roms;

        /**
         * Whether SHA-1 checks are requested for disk containers in the profile.
         */
        final boolean sha1Disks;

        /**
         * Whether SHA-1 checks are requested for ROMs in the profile.
         */
        final boolean sha1Roms;

        /**
         * Indicates if the target directory is a destination folder.
         */
        final boolean isDest;
        /**
         * Indicates whether folder walking should be recursive.
         */
        final boolean recurse;
        /**
         * Indicates if multi-threading is enabled.
         */
        final boolean useParallelism;
        /**
         * Indicates whether to format zip archives using TorrentZip standards.
         */
        final boolean formatTZip;
        /**
         * Indicates if empty folders should be added to the output.
         */
        final boolean includeEmptyDirs;
        /**
         * Indicates whether to treat archives and CHD disk containers as single ROMs.
         */
        final boolean archivesAndChdAsRoms;

        /**
         * Parallel thread count.
         */
        final int nThreads;

        /**
         * TorrentZip verification and formatting engine.
         */
        final TorrentZip torrentzip;

        /**
         * Instantiates a new options configuration container.
         * 
         * @param options the scan options enum list
         */
        public ScanOptions(Set<Options> options) {
            needSha1OrMd5 = options.contains(Options.NEED_SHA1_OR_MD5) || options.contains(Options.NEED_SHA1) || options.contains(Options.NEED_MD5);
            md5Disks = options.contains(Options.MD5_DISKS) || options.contains(Options.NEED_MD5);
            md5Roms = options.contains(Options.MD5_ROMS) || options.contains(Options.NEED_MD5);
            sha1Disks = options.contains(Options.SHA1_DISKS) || options.contains(Options.NEED_SHA1);
            sha1Roms = options.contains(Options.SHA1_ROMS) || options.contains(Options.NEED_SHA1);
            isDest = options.contains(Options.IS_DEST);
            recurse = options.contains(Options.RECURSE);
            useParallelism = options.contains(Options.USE_PARALLELISM);
            formatTZip = options.contains(Options.FORMAT_TZIP);
            includeEmptyDirs = options.contains(Options.EMPTY_DIRS);
            archivesAndChdAsRoms = options.contains(Options.ARCHIVES_AND_CHD_AS_ROMS);
            nThreads = useParallelism ? session.getUser().getSettings().getProperty(SettingsEnum.thread_count, Integer.class) : 1;
            torrentzip = (isDest && formatTZip) ? new TorrentZip(new DummyLogCallback(), new SimpleTorrentZipOptions(false, true)) : null;
        }
    }

    /**
     * Triggers platform initialization for the native SevenZip JBinding library.
     */
    private void init7zJBinding() {
        if (!SevenZip.isInitializedSuccessfully()) {
            try {
                SevenZip.initSevenZipFromPlatformJAR(session.getUser().getSettings().getTmpPath(true).toFile());
            } catch (final Exception e) {
                Log.err(e.getMessage(), e);
            }
        }
    }

    /**
     * Options enumeration for custom directory scanning configurations.
     */
    public enum Options {
        /**
         * Indicates the directory is scanned as a destination folder.
         */
        IS_DEST,
        /**
         * Recurse through subdirectories during folder walking.
         */
        RECURSE,
        /**
         * Specifies that either SHA-1 or MD5 calculations are required.
         */
        NEED_SHA1_OR_MD5,
        /**
         * Specifies that SHA-1 verification is explicitly required.
         */
        NEED_SHA1,
        /**
         * Specifies that MD5 verification is explicitly required.
         */
        NEED_MD5,
        /**
         * Utilize multi-threading to parallelize folder analysis.
         */
        USE_PARALLELISM,
        /**
         * Format ZIP containers in accordance with TorrentZip standards.
         */
        FORMAT_TZIP,
        /**
         * MD5 hash calculations are required for romsets.
         */
        MD5_ROMS,
        /**
         * MD5 hash calculations are required for CHD disk files.
         */
        MD5_DISKS,
        /**
         * SHA-1 hash calculations are required for romsets.
         */
        SHA1_ROMS,
        /**
         * SHA-1 hash calculations are required for CHD disk files.
         */
        SHA1_DISKS,
        /**
         * Include empty folders during the physical scan.
         */
        EMPTY_DIRS,
        /**
         * Treat archives and CHD folders as standalone single ROMs.
         */
        ARCHIVES_AND_CHD_AS_ROMS,
        /**
         * Flatten paths by removing internal subdirectories.
         */
        JUNK_SUBFOLDERS,
        /**
         * Align scanned element designations with current active profile structures.
         */
        MATCH_PROFILE
    }

    /**
     * Converts profile options into an active scanning configurations EnumSet.
     * 
     * @param profile the active profile configuration
     * @param is_dest whether the target directory represents a destination path
     * 
     * @return the configured scan options list
     */
    static EnumSet<Options> getOptions(Profile profile, final boolean is_dest) {
        EnumSet<Options> options = EnumSet.noneOf(Options.class);
        if (is_dest)
            options.add(Options.IS_DEST);
        if (profile == null)
            return options;
        if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.need_sha1_or_md5, Boolean.class))) // $NON-NLS-1$
            options.add(Options.NEED_SHA1_OR_MD5);
        if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.use_parallelism, Boolean.class))) // $NON-NLS-1$
            options.add(Options.USE_PARALLELISM);
        if (Boolean.TRUE.equals(profile.getProperty(ProfileSettingsEnum.archives_and_chd_as_roms, Boolean.class))) // $NON-NLS-1$
            options.add(Options.ARCHIVES_AND_CHD_AS_ROMS);
        final var format = FormatOptions.valueOf(profile.getProperty(ProfileSettingsEnum.format, String.class)); // $NON-NLS-1$
        if (FormatOptions.TZIP == format)
            options.add(Options.FORMAT_TZIP);
        else if (FormatOptions.DIR == format)
            options.add(Options.RECURSE);
        if (profile.isMd5Roms())
            options.add(Options.MD5_ROMS);
        if (profile.isMd5Disks())
            options.add(Options.MD5_DISKS);
        if (profile.isSha1Roms())
            options.add(Options.SHA1_ROMS);
        if (profile.isSha1Disks())
            options.add(Options.SHA1_DISKS);
        return options;
    }

    /**
     * Prepares list of exclusion path matchers based on configuration strings in the profile.
     * 
     * @param profile the current profile
     * @param is_dest whether exclusions apply to a destination folder
     * 
     * @return a {@link List} containing exclusion pattern matches
     */
    static List<Map.Entry<String, PathMatcher>> initExclusions(Profile profile, final boolean is_dest) {
        if (is_dest) {
            final var fs = FileSystems.getDefault();
            return Stream.of(StringUtils
                    .split(profile.getProperty(ProfileSettingsEnum.exclusion_glob_list.toString(), "|"), "|"))
                    .filter(s -> !s.isEmpty()).map(s -> {
                        if (!s.startsWith(GLOB) && !s.startsWith("regex:"))
                            s = GLOB + s;
                        if (s.startsWith(GLOB) && !s.contains("**/"))
                            s = GLOB + "**/" + s.substring(5);
                        return Map.entry(s, fs.getPathMatcher(s));
                    }).toList();
        }
        return List.of();
    }

    /**
     * Verifies whether a given checksum hash resides in the profile's list of suspicious CRCs.
     * 
     * @param crc the target checksum to analyze
     * 
     * @return {@code true} if the checksum represents a suspicious CRC, {@code false} otherwise
     */
    private boolean isSuspiciousCRC(String crc) {
        return suspiciousCrc != null && suspiciousCrc.contains(crc);
    }

    /**
     * Constructs a new DirScan instance aligned with profile properties.
     * 
     * @param profile the configuration profile context
     * @param dir the physical folder to walk
     * @param handler the progress reporting channel
     * @param is_dest whether the directory is a destination path
     * 
     * @throws BreakException if execution is stopped by the user
     */
    DirScan(final Profile profile, final File dir, final ProgressHandler handler, final boolean is_dest) throws BreakException {
        this(profile.getSession(), dir, handler, profile.getSuspiciousCRC(), getOptions(profile, is_dest), initExclusions(profile, is_dest));
    }

    /**
     * Constructs a standalone DirScan instance without an active profile context.
     * 
     * @param session the active workspace session
     * @param dir the physical folder to walk
     * @param handler the progress reporting channel
     * @param options the filter options constraints
     * 
     * @throws BreakException if execution is stopped by the user
     */
    DirScan(final Session session, final File dir, final ProgressHandler handler, Set<Options> options) throws BreakException {
        this(session, dir, handler, null, options, List.of());
    }

    /**
     * Private internal constructor carrying out physical scanning and cache retrieval.
     * 
     * @param session the workspace session
     * @param dir the physical folder to walk
     * @param handler the progress monitoring channel
     * @param suspiciousCrc list of suspicious CRC hashes
     * @param soptions options configurations
     * @param exclusions exclusion patterns list
     * 
     * @throws BreakException if scanning is aborted
     */
    private DirScan(final Session session, final File dir, final ProgressHandler handler, final Set<String> suspiciousCrc, Set<Options> soptions,
            List<Map.Entry<String, PathMatcher>> exclusions) throws BreakException {
        super(session);
        this.session = session;

        init7zJBinding();

        this.dir = dir;
        this.handler = handler;
        this.suspiciousCrc = suspiciousCrc;
        this.exclusions = exclusions;

        final var options = new ScanOptions(soptions);
        final var path = Paths.get(dir.getAbsolutePath());

        if (Boolean.FALSE.equals(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.debug_nocache, Boolean.class))) // $NON-NLS-1$
            containersByName = load(dir, soptions);
        else
            containersByName = Collections.synchronizedMap(new HashMap<>());

        if (!Files.isDirectory(path))
            return;

        handler.clearInfos();
        handler.setInfos(options.nThreads, null);

        listFiles(dir, handler, path, options);

        final var i = new AtomicInteger(0);
        final var j = new AtomicInteger(0);
        final var max = new AtomicInteger(0);
        max.addAndGet(containers.size());
        containers.forEach(c -> max.addAndGet((int) (c.getSize() >> 20)));
        handler.clearInfos();
        handler.setInfos(options.nThreads, true);
        handler.setProgress(String.format(Messages.getString("DirScan.ScanningFiles"), getRelativePath(dir.toPath())), -1); //$NON-NLS-1$
        handler.setProgress2("", j.get(), max.get()); //$NON-NLS-1$
        try (final var mt = new MultiThreadingVirtual<Container>("dirscan", handler, options.nThreads, c -> {
            if (handler.isCancel())
                return;
            try {
                scanContainer(c, handler, options);
                handler.setProgress(String.format(Messages.getString("DirScan.Scanned"), c.getFile().getName())); //$NON-NLS-1$
                handler.setProgress2(String.format("%d/%d (%d%%)", i.incrementAndGet(), containers.size(), //$NON-NLS-1$
                        (int) (j.addAndGet(1 + (int) (c.getSize() >> 20)) * 100.0 / max.get())), j.get());
            } catch (final IOException e) {
                c.setLoaded(0);
                Log.err("IOException when scanning", e); //$NON-NLS-1$
            } catch (final BreakException _) {
                c.setLoaded(0);
                handler.doCancel();
            } catch (final Exception e) {
                c.setLoaded(0);
                Log.err("Other Exception when listing", e); //$NON-NLS-1$
            }
            return;
        })) {
            mt.start(containers.stream().sorted(Container.rcomparator()));
        }

        if (!handler.isCancel())
            save(dir, soptions);

    }

    /**
     * Inspects and updates file lists inside a specific container depending on its type.
     * 
     * @param container the target container representation
     * @param progress the progress handler channel
     * @param options options configurations
     * 
     * @throws IOException if folder reading operations fail
     * @throws NoSuchAlgorithmException if hashing algorithms are unavailable
     */
    private void scanContainer(Container container, final ProgressHandler progress, ScanOptions options) throws IOException, NoSuchAlgorithmException {
        switch (container.getType()) {
            case ZIP: {
                scanZip(container, options);
                break;
            }
            case RAR, SEVENZIP: {
                try (final var entries = new SevenZUpdateEntries(container, options)) {
                    entries.updateEntries();
                }
                break;
            }
            case DIR: {
                scanDir(progress, container, options);
                break;
            }
            case FAKE: {
                scanFake(progress, container, options);
                break;
            }
            default:
                break;
        }
    }

    /**
     * Lists and filters all physical files on the filesystem prior to performing full verification.
     * 
     * @param dir the root physical source folder
     * @param handler the progress monitoring channel
     * @param path the path representation of the folder
     * @param options options configurations
     */
    private void listFiles(final File dir, final ProgressHandler handler, final Path path, final ScanOptions options) {
        handler.setProgress(String.format(Messages.getString("DirScan.ListingFiles"), getRelativePath(dir.toPath())));

        try {
            final var i = new AtomicInteger();

            Files.walkFileTree(path, Collections.singleton(FileVisitOption.FOLLOW_LINKS), options.isDest ? 1 : 100, listFilesVisitor(dir, handler, path, options, i));
            containersByName.entrySet().removeIf(entry -> !entry.getValue().isUp2date());
        } catch (IOException e) {
            Log.err("IOException when listing", e); //$NON-NLS-1$
        } catch (final Exception e) {
            Log.err("Other Exception when listing", e); //$NON-NLS-1$
        }

    }

    /**
     * Creates a file visitor that walks files and registers containers into the scanner context.
     * 
     * @param dir the root file directory
     * @param handler the progress handler monitor
     * @param rootPath the starting walk path
     * @param options the scanning configuration metrics
     * @param i progress incremental counter
     * 
     * @return the simple file visitor implementation
     */
    private SimpleFileVisitor<Path> listFilesVisitor(final File dir, final ProgressHandler handler, final Path rootPath,
            final ScanOptions options, final AtomicInteger i) {
        return new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path entryPath, BasicFileAttributes entryAttrs) throws IOException {
                return doVisitFile(entryPath, entryAttrs, dir, handler, rootPath, options, i);
            }
        };
    }

    /**
     * Handles the visit of a single file during directory traversal, applying exclusion filters and dispatching to source or
     * destination listing.
     * 
     * @param dir the root file directory
     * @param handler the progress handler monitor
     * @param rootPath the starting walk path
     * @param options the scanning configuration metrics
     * @param i progress incremental counter
     * 
     * @return the simple file visitor implementation
     */
    private FileVisitResult doVisitFile(Path entryPath, BasicFileAttributes entryAttrs, final File dir,
            final ProgressHandler handler, final Path rootPath, final ScanOptions options, final AtomicInteger i) {
        if (handler.isCancel())
            return FileVisitResult.TERMINATE;
        if (rootPath.equals(entryPath))
            return FileVisitResult.CONTINUE;
        final var entryFile = entryPath.toFile();
        try {
            if (options.isDest) {
                if (isExcluded(entryPath))
                    return FileVisitResult.CONTINUE;
                listFilesDest(entryFile, entryAttrs);
            } else
                listFilesSrc(rootPath, entryPath, entryFile, entryAttrs, options);
            updateVisitProgress(entryPath, rootPath, dir, i);
        } catch (final IOException e) {
            Log.err(e.getMessage(), e);
        } catch (final BreakException _) {
            handler.doCancel();
        }

        updateVisitProgress(entryPath, rootPath, dir, i);
        return FileVisitResult.CONTINUE;
    }

    /**
     * Checks whether the given path matches any configured exclusion pattern.
     * 
     * @param entryPath the path to check against exclusion patterns
     * 
     * @return {@code true} if the path is excluded, {@code false} otherwise
     */
    private boolean isExcluded(Path entryPath) {
        return exclusions.stream().anyMatch(pm -> {
            if (pm.getValue().matches(entryPath)) {
                Log.info(() -> "match for exclusion %s on %s, will skip...".formatted(pm.getKey(), entryPath.toString()));
                return true;
            }
            return false;
        });
    }

    /**
     * Updates the progress handler with current file visit information.
     * 
     * @param entryPath the current entry path
     * @param rootPath the root scan path
     * @param dir the root directory file
     * @param i progress incremental counter
     */
    private void updateVisitProgress(Path entryPath, final Path rootPath, final File dir, final AtomicInteger i) {
        handler.setProgress(rootPath.relativize(entryPath).toString(), -1); // $NON-NLS-1$
        handler.setProgress2(String.format(Messages.getString("DirScan.ListingFiles2"), //$NON-NLS-1$
                getRelativePath(dir.toPath()), i.incrementAndGet()), 0);
    }

    /**
     * Registers files discovered during source directory scans.
     * 
     * @param rootPath the starting walker path
     * @param entryPath the entry path sequence
     * @param entryFile the entry file descriptor
     * @param entryAttr the entry attributes metadata
     * @param options options configurations
     * 
     * @throws IOException if folder descriptors cannot be resolved
     */
    private void listFilesSrc(final Path rootPath, Path entryPath, final File entryFile, final BasicFileAttributes entryAttr, ScanOptions options) throws IOException {
        if (entryAttr.isRegularFile()) {
            val entryType = Container.getType(entryFile);
            if (entryType == Type.UNK || options.archivesAndChdAsRoms) {
                if (rootPath.equals(entryFile.getParentFile().toPath())) {
                    listFilesSrcUnknown(entryFile, entryAttr, entryType);
                } else {
                    listFilesSrcParentDir(rootPath, entryPath, entryFile, entryAttr);
                }
            } else {
                listFilesSrcArchive(rootPath, entryPath, entryFile, entryAttr);
            }
        } else if (options.includeEmptyDirs) {
            listFilesSrcEmptyDir(rootPath, entryPath, entryFile, entryAttr);
        }
    }

    /**
     * Registers discovered empty directories.
     * 
     * @param rootPath the scan starting path
     * @param entryPath the empty folder sequence path
     * @param entryFile the empty folder file handle
     * @param entryAttrs the directory attributes
     * 
     * @throws IOException if directory streams cannot be opened
     */
    private void listFilesSrcEmptyDir(final Path rootPath, Path entryPath, final File entryFile, final BasicFileAttributes entryAttrs) throws IOException {
        try (DirectoryStream<Path> dirstream = Files.newDirectoryStream(entryPath)) {
            if (!dirstream.iterator().hasNext()) {
                final Container existingContainer;
                final var relativePath = rootPath.relativize(entryPath);
                if (null == (existingContainer = containersByName.get(relativePath.toString()))
                        || (existingContainer.getModified() != entryAttrs.lastModifiedTime().toMillis() && !existingContainer.isUp2date())) {
                    final var newContainer = new Directory(entryFile, getRelativePath(entryFile), entryAttrs);
                    newContainer.setUp2date(true);
                    containers.add(newContainer);
                    containersByName.put(relativePath.toString(), newContainer);
                    if (relativePath.getNameCount() > 1)
                        containersByName.put(relativePath.getFileName().toString(), newContainer);
                } else if (!existingContainer.isUp2date()) {
                    existingContainer.setUp2date(true);
                    containers.add(existingContainer);
                    if (relativePath.getNameCount() > 1)
                        containersByName.putIfAbsent(relativePath.getFileName().toString(), existingContainer);
                }
            }
        }
    }

    /**
     * Registers the parent directory of regular files with unknown extensions.
     * 
     * @param rootPath the scan starting path
     * @param entryPath the entry sequence path
     * @param entryFile the entry file handle
     * @param entryAttrs the entry attributes
     * 
     * @throws IOException if directory attributes cannot be read
     */
    private void listFilesSrcParentDir(final Path rootPath, Path entryPath, final File entryFile, final BasicFileAttributes entryAttrs) throws IOException {
        final Container existingContainer;
        final var parentDir = entryFile.getParentFile();
        final var parentAttr = Files.readAttributes(entryPath.getParent(), BasicFileAttributes.class);
        final var relativePath = rootPath.relativize(entryPath.getParent());
        if (null == (existingContainer = containersByName.get(relativePath.toString()))
                || (existingContainer.getModified() != parentAttr.lastModifiedTime().toMillis() && !existingContainer.isUp2date())) {
            final var newContainer = new Directory(parentDir, getRelativePath(parentDir), entryAttrs);
            newContainer.setUp2date(true);
            containers.add(newContainer);
            containersByName.put(relativePath.toString(), newContainer);
            if (relativePath.getNameCount() > 1)
                containersByName.put(relativePath.getFileName().toString(), newContainer);
        } else if (!existingContainer.isUp2date()) {
            existingContainer.setUp2date(true);
            containers.add(existingContainer);
            if (relativePath.getNameCount() > 1)
                containersByName.putIfAbsent(relativePath.getFileName().toString(), existingContainer);
        }
    }

    /**
     * Registers an individual file of unknown extension as a fake directory.
     * 
     * @param file the target physical file handle
     * @param attr the entry attributes
     * @param type the container type
     */
    private void listFilesSrcUnknown(final File file, final BasicFileAttributes attr, final jrm.profile.data.Container.Type type) {
        final Container existingContainer;
        val fname = type == Type.UNK ? (FilenameUtils.getBaseName(file.getName()) + Ext.FAKE) : file.getName();
        if (null == (existingContainer = containersByName.get(fname))
                || (existingContainer.getModified() != attr.lastModifiedTime().toMillis() && !existingContainer.isUp2date())) {
            final var newContainer = new FakeDirectory(file, getRelativePath(file), attr);
            newContainer.setUp2date(true);
            containers.add(newContainer);
            containersByName.put(fname, newContainer);
        } else if (!existingContainer.isUp2date()) {
            existingContainer.setUp2date(true);
            containers.add(existingContainer);
        }
    }

    /**
     * Registers an archive package found during the source folder walk.
     * 
     * @param rootPath the scan starting path
     * @param entryPath the entry sequence path
     * @param file the physical file handle
     * @param attr the entry attributes
     */
    private void listFilesSrcArchive(final Path rootPath, Path entryPath, final File file, final BasicFileAttributes attr) {
        final Container existingContainer;
        final var relativePath = rootPath.relativize(entryPath);
        if (null == (existingContainer = containersByName.get(relativePath.toString()))
                || ((existingContainer.getModified() != attr.lastModifiedTime().toMillis() || existingContainer.getSize() != attr.size()) && !existingContainer.isUp2date())) {
            final var newContainer = new Archive(file, getRelativePath(file), attr);
            newContainer.setUp2date(true);
            containers.add(newContainer);
            containersByName.put(relativePath.toString(), newContainer);
            if (relativePath.getNameCount() > 1)
                containersByName.put(relativePath.getFileName().toString(), newContainer);
        } else if (!existingContainer.isUp2date()) {
            existingContainer.setUp2date(true);
            containers.add(existingContainer);
            if (relativePath.getNameCount() > 1)
                containersByName.putIfAbsent(relativePath.getFileName().toString(), existingContainer);
        }
    }

    /**
     * Registers a container discovered inside the destination directory.
     * 
     * @param file the physical file handle
     * @param attr the entry attributes
     */
    private void listFilesDest(final File file, final BasicFileAttributes attr) {
        final var type = attr.isRegularFile() ? Container.getType(file) : Type.DIR;
        final var fname = type == Type.UNK ? (FilenameUtils.getBaseName(file.getName()) + Ext.FAKE) : file.getName();
        var c = containersByName.get(fname);
        if (null == c || ((c.getModified() != attr.lastModifiedTime().toMillis() || (c instanceof Archive && c.getSize() != attr.size())) && !c.isUp2date())) {
            if (attr.isRegularFile()) {
                if (type != Container.Type.UNK)
                    c = new Archive(file, getRelativePath(file), attr);
                else
                    c = new FakeDirectory(file, getRelativePath(file), attr);
            } else
                c = new Directory(file, getRelativePath(file), attr);
            c.setUp2date(true);
            containers.add(c);
            containersByName.put(fname, c);
        } else if (!c.isUp2date()) {
            c.setUp2date(true);
            containers.add(c);
        }
    }

    /**
     * Evaluates and populates entries inside a fake single file directory container.
     * 
     * @param handler the progress handler monitor
     * @param c the fake container instance
     * @param options options configurations
     * 
     * @throws IOException if file reading fails
     */
    private void scanFake(final ProgressHandler handler, Container c, ScanOptions options) throws IOException {
        if (c.getLoaded() < 1 || (options.needSha1OrMd5 && c.getLoaded() < 2)) {
            final var entry = new Entry(c.getFile().getName(), c.getRelFile().getName(), c.getSize(), c.getModified());
            if (options.archivesAndChdAsRoms)
                entry.setType(Entry.Type.UNK);
            handler.setProgress(FilenameUtils.getBaseName(c.getFile().getName()), -1, null, c.getFile().getName()); // $NON-NLS-1$
                                                                                                                    // //$NON-NLS-2$
            updateEntry(c.add(entry), c.getFile().toPath(), options);
            c.setLoaded(options.needSha1OrMd5 ? 2 : 1);
        } else {
            for (final Entry entry : c.getEntries())
                updateEntry(entry, options);
        }
    }

    /**
     * Evaluates and populates entries inside standard physical directories.
     * 
     * @param handler the progress handler monitor
     * @param c the directory container
     * @param options options configurations
     * 
     * @throws IOException if files cannot be read
     */
    private void scanDir(final ProgressHandler handler, Container c, ScanOptions options) throws IOException {
        if (c.getLoaded() < 1 || (options.needSha1OrMd5 && c.getLoaded() < 2)) {
            scanDirNoCache(handler, c, options);
        } else {
            for (final Entry entry : c.getEntries())
                updateEntry(entry, options);
        }
    }

    /**
     * Evaluates physical files in standard folders without utilizing cached data.
     * 
     * @param handler the progress handler monitor
     * @param c the directory container
     * @param options options configurations
     * 
     * @throws IOException if file attributes cannot be read
     */
    private void scanDirNoCache(final ProgressHandler handler, Container c, ScanOptions options) throws IOException {
        try {
            Files.walkFileTree(c.getFile().toPath(), EnumSet.noneOf(FileVisitOption.class), (options.isDest && options.recurse) ? Integer.MAX_VALUE : 1,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(final Path entryPath, final BasicFileAttributes attrs) throws IOException {
                            if (attrs.isRegularFile()) {
                                final var entry = new Entry(entryPath.toString(), getRelativePath(entryPath).toString(), attrs);
                                if (options.archivesAndChdAsRoms)
                                    entry.setType(Entry.Type.UNK);
                                handler.setProgress(c.getFile().getName(), -1, null, File.separator + c.getFile().toPath().relativize(entryPath).toString()); // $NON-NLS-1$
                                                                                                                                                              // //$NON-NLS-2$
                                updateEntry(c.add(entry), entryPath, options);
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
                            return FileVisitResult.CONTINUE;
                        }
                    });
            c.setLoaded(options.needSha1OrMd5 ? 2 : 1);
        } catch (AccessDeniedException _) {
            // access denied
        }
    }

    /**
     * Scans and updates file list structures nested inside physical ZIP packages.
     * 
     * @param c the target ZIP container
     * @param options options configurations
     * 
     * @throws IOException if the file stream cannot be opened
     */
    private void scanZip(Container c, ScanOptions options) throws IOException {
        try (final var zipf = new ZipFile(c.getFile())) {
            if (c.getLoaded() < 1 || (options.needSha1OrMd5 && c.getLoaded() < 2)) {
                for (final var hdr : zipf.getFileHeaders()) {
                    if (!hdr.isDirectory()) {
                        final var entry = c.add(new Entry(ZipTools.toEntry(hdr.getFileName()), ZipTools.toEntry(hdr.getFileName())));
                        updateEntry(entry, zipf, hdr, options);
                    }
                }
                c.setLoaded(options.needSha1OrMd5 ? 2 : 1);

            } else {
                for (final Entry entry : c.getEntries())
                    updateEntry(entry, zipf, null, options);
            }
        } catch (Exception e) {
            Log.err(() -> c.getRelFile() + " : " + e.getMessage());
        }
        checkTorrentZip(c, options);
    }

    /**
     * Checks and updates TorrentZip compliance for a destination container if needed.
     * 
     * <p>
     * This method checks if the container was modified since last TorrentZip compliance check and if so, it will attempt to
     * update the container properties to match the TorrentZip specification.
     * </p>
     * 
     * @param c the target container
     * @param options options configurations
     * @throws IOException 
     */
    private void checkTorrentZip(Container c, ScanOptions options) throws IOException {
        if (options.isDest && options.formatTZip && c.getLastTZipCheck() < c.getModified()) {
            c.setLastTZipStatus(options.torrentzip.process(c.getFile()));
            c.setLastTZipCheck(System.currentTimeMillis());
        }
    }

    /**
     * Updates properties of a single file entry in a ZIP package.
     * 
     * @param entry the target entry details
     * @param zipf the parent zip package
     * @param hdr the file header descriptor
     * @param options options configurations
     */
    private void updateEntry(Entry entry, ZipFile zipf, FileHeader hdr, ScanOptions options) {
        if (entry.getSize() == 0 && entry.getCrc() == null) {
            entry.setSize(hdr.getUncompressedSize()); // $NON-NLS-1$
            entry.setCrc(String.format("%08x", hdr.getCrc())); //$NON-NLS-1$ //$NON-NLS-2$
        }
        entriesByCrc.put(entry.getCrc() + "." + entry.getSize(), entry); //$NON-NLS-1$
        if (options.needSha1OrMd5 || entry.getCrc() == null || isSuspiciousCRC(entry.getCrc())) {
            List<Algo> algorithms = getAlgorithms(entry, options);
            if (!algorithms.isEmpty())
                try {
                    if (hdr == null)
                        hdr = zipf.getFileHeader(ZipTools.toZipEntry(entry.getFile()));
                    MDigest[] digests = computeHash(zipf.getInputStream(hdr), algorithms);
                    updateEntryFromHashes(entry, digests);
                } catch (IOException | NoSuchAlgorithmException e) {
                    Log.err(e.getMessage(), e);
                }
        }
        updateHashesFromEntry(entry);
    }

    /**
     * Populates hash properties on a file entry with digested hash results.
     * 
     * @param entry the target entry details
     * @param digests array of processed message digests
     */
    private void updateEntryFromHashes(Entry entry, MDigest[] digests) {
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

    /**
     * Helper class wrapping SevenZip JBinding extraction callbacks to calculate hashes for 7-zip or RAR archive formats in
     * parallel.
     */
    private class SevenZUpdateEntries implements Closeable {
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
                            entriesBySha1.put(entry.getSha1(), entry);
                        }
                        if (d.getAlgorithm() == Algo.MD5) // $NON-NLS-1$
                        {
                            entry.setMd5(d.toString());
                            entriesByMd5.put(entry.getMd5(), entry);
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
         * @param container the parent file container
         * @param options options configurations
         * 
         * @throws NoSuchAlgorithmException if digest libraries are missing
         */
        private SevenZUpdateEntries(final Container container, ScanOptions options) throws NoSuchAlgorithmException {
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
                archive = new SevenZipArchive(session, container.getFile());
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
        private void updateEntries() throws IOException {
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
                for (final Entry entry : container.getEntries())
                    updateEntry(entry, entries, null);
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
            entriesByCrc.put(entry.getCrc() + "." + entry.getSize(), entry); //$NON-NLS-1$
            if (entry.getSha1() == null && entry.getMd5() == null && (options.needSha1OrMd5 || entry.getCrc() == null || isSuspiciousCRC(entry.getCrc()))) {
                updateEntryExt(entry, entries, item);
            } else {
                if (entry.getSha1() != null)
                    entriesBySha1.put(entry.getSha1(), entry);
                if (entry.getMd5() != null)
                    entriesByMd5.put(entry.getMd5(), entry);
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
            entriesByCrc.put(entry.getCrc() + "." + entry.getSize(), entry); //$NON-NLS-1$
            if (entry.getSha1() == null && entry.getMd5() == null && (options.needSha1OrMd5 || entry.getCrc() == null || isSuspiciousCRC(entry.getCrc()))) {
                entries.put(entry.getFile(), entry);
            } else {
                if (entry.getSha1() != null)
                    entriesBySha1.put(entry.getSha1(), entry);
                if (entry.getMd5() != null)
                    entriesByMd5.put(entry.getMd5(), entry);
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
                            entriesBySha1.put(entry.getSha1(), entry);
                        }
                        if (d.getAlgorithm() == Algo.MD5) // $NON-NLS-1$
                        {
                            entry.setMd5(d.toString());
                            entriesByMd5.put(entry.getMd5(), entry);
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

    /**
     * Dispatches file entry property updates.
     * 
     * @param entry the target entry details
     * @param options options configurations
     * 
     * @throws IOException if stream errors occur
     */
    private void updateEntry(final Entry entry, ScanOptions options) throws IOException {
        updateEntry(entry, (Path) null, options);
    }

    /**
     * Updates properties of a standalone folder or archive entry.
     * 
     * @param entry the target entry details
     * @param entryPath the path sequence mapping of the file (can be null)
     * @param options options configurations
     * 
     * @throws IOException if stream errors occur
     */
    private void updateEntry(final Entry entry, final Path entryPath, ScanOptions options) throws IOException {
        if (entry.getParent().getType() == Type.ZIP) {
            updatEntryZip(entry, entryPath);
        }
        if (entry.getType() == Entry.Type.CHD && entry.getSha1() == null && entry.getMd5() == null) {
            updateEntryCHD(entry, entryPath, options);
        } else if (entry.getType() != Entry.Type.CHD && (options.needSha1OrMd5 || entry.getCrc() == null || isSuspiciousCRC(entry.getCrc()))) {
            updateEntryExt(entry, entryPath, options);
        } else {
            updateHashesFromEntry(entry);
        }
    }

    /**
     * Performs external hash calculation updates.
     * 
     * @param entry the target entry details
     * @param entryPath the path sequence mapping of the file (can be null)
     * @param options options configurations
     * 
     * @throws IOException if stream errors occur
     */
    private void updateEntryExt(final Entry entry, final Path entryPath, ScanOptions options) throws IOException {
        List<Algo> algorithms = getAlgorithms(entry, options);
        updateEntryExt(entry, entryPath, algorithms);
        updateHashesFromEntry(entry);
    }

    /**
     * Registers an entry's existing hashes in the global scanner indexes.
     * 
     * @param entry the target entry details
     */
    private void updateHashesFromEntry(final Entry entry) {
        if (entry.getCrc() != null)
            entriesByCrc.put(entry.getCrc() + "." + entry.getSize(), entry); //$NON-NLS-1$
        if (entry.getSha1() != null)
            entriesBySha1.put(entry.getSha1(), entry);
        if (entry.getMd5() != null)
            entriesByMd5.put(entry.getMd5(), entry);
    }

    /**
     * Resolves which algorithms should be executed on an entry based on current configurations.
     * 
     * @param entry the target entry
     * @param options scanning option metrics
     * 
     * @return a {@link List} of algorithms to run
     */
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

    /**
     * Executes digests and updates entry values for missing hashes.
     * 
     * @param entry the target entry
     * @param entryPath the physical path representation of the file (can be null)
     * @param algorithms the algorithms to run
     * 
     * @throws IOException if stream reading fails
     */
    private void updateEntryExt(final Entry entry, final Path entryPath, List<Algo> algorithms) throws IOException {
        if (!algorithms.isEmpty())
            try {
                var path = entryPath;
                if (entryPath == null)
                    path = getPath(entry);
                MDigest[] digests = computeHash(path, algorithms);
                updateEntryFromHashes(entry, digests);
                if (entryPath == null)
                    path.getFileSystem().close();
            } catch (NoSuchAlgorithmException e) {
                Log.err(e.getMessage(), e);
            }
    }

    /**
     * Extracts and updates disk package hashes (CHDs) utilizing native header parsing.
     * 
     * @param entry the target entry
     * @param entryPath the path sequence mapping of the file (can be null)
     * @param options options configurations
     * 
     * @throws IOException if stream reading fails
     */
    private void updateEntryCHD(final Entry entry, final Path entryPath, ScanOptions options) throws IOException {
        var path = entryPath;
        if (entryPath == null)
            path = getPath(entry);
        final var chdInfo = new CHDInfoReader(path.toFile());
        if (options.sha1Disks) {
            entry.setSha1(chdInfo.getSHA1());
            if (null != entry.getSha1())
                entriesBySha1.put(entry.getSha1(), entry);
        }
        if (options.md5Disks) {
            entry.setMd5(chdInfo.getMD5());
            if (null != entry.getMd5())
                entriesByMd5.put(entry.getMd5(), entry);
        }
        if (entryPath == null)
            path.getFileSystem().close();
    }

    /**
     * Extracts and populates size and CRC values from zip filesystems.
     * 
     * @param entry the target entry
     * @param entryPath the path sequence mapping of the file (can be null)
     * 
     * @throws IOException if zip filesystem access fails
     */
    private void updatEntryZip(final Entry entry, final Path entryPath) throws IOException {
        if (entry.getSize() == 0 && entry.getCrc() == null) {
            var path = entryPath;
            if (entryPath == null)
                path = getPath(entry);
            final Map<String, Object> entryZipAttrs = Files.readAttributes(path, "zip:*"); //$NON-NLS-1$
            entry.setSize((Long) entryZipAttrs.get("size")); //$NON-NLS-1$
            entry.setCrc(String.format("%08x", entryZipAttrs.get("crc"))); //$NON-NLS-1$ //$NON-NLS-2$
            if (entryPath == null)
                path.getFileSystem().close();
        }
        entriesByCrc.put(entry.getCrc() + "." + entry.getSize(), entry); //$NON-NLS-1$
    }

    /**
     * Calculates message digests on physical paths.
     * 
     * @param entryPath the target file path
     * @param algorithm list of algorithms to run
     * 
     * @return array of updated digests
     * 
     * @throws NoSuchAlgorithmException if hashing libraries are missing
     */
    private MDigest[] computeHash(final Path entryPath, final List<Algo> algorithm) throws NoSuchAlgorithmException {
        return computeHash(entryPath, algorithm.toArray(new Algo[0]));
    }

    /**
     * Calculates message digests on physical paths using native array mappings.
     * 
     * @param entryPath the target file path
     * @param algorithm array of algorithms to run
     * 
     * @return array of updated digests
     * 
     * @throws NoSuchAlgorithmException if hashing libraries are missing
     */
    private MDigest[] computeHash(final Path entryPath, final Algo[] algorithm) throws NoSuchAlgorithmException {
        var md = getMDigest(algorithm);
        try {
            MDigest.computeHash(Files.newInputStream(entryPath), md);
        } catch (final IOException e) {
            Log.err(e.getMessage(), e);
        }
        return md;
    }

    /**
     * Obtains hashing helpers matching specified algorithms.
     * 
     * @param algorithm algorithms enum array
     * 
     * @return array of custom message digests
     * 
     * @throws NoSuchAlgorithmException if hashing libraries are missing
     */
    private MDigest[] getMDigest(final Algo[] algorithm) throws NoSuchAlgorithmException {
        var md = new MDigest[algorithm.length];
        for (var i = 0; i < algorithm.length; i++)
            md[i] = MDigest.getAlgorithm(algorithm[i]);
        return md;
    }

    /**
     * Computes message digests from generic streams.
     * 
     * @param is the target input stream
     * @param algorithm list of algorithms to run
     * 
     * @return array of updated digests
     * 
     * @throws IOException if stream reading fails
     * @throws NoSuchAlgorithmException if hashing libraries are missing
     */
    private MDigest[] computeHash(final InputStream is, final List<Algo> algorithm) throws IOException, NoSuchAlgorithmException {
        return computeHash(is, algorithm.toArray(new Algo[0]));
    }

    /**
     * Computes message digests from generic streams using native array mappings.
     * 
     * @param is the target input stream
     * @param algorithm array of algorithms to run
     * 
     * @return array of updated digests
     * 
     * @throws IOException if stream reading fails
     * @throws NoSuchAlgorithmException if hashing libraries are missing
     */
    private MDigest[] computeHash(final InputStream is, final Algo[] algorithm) throws IOException, NoSuchAlgorithmException {
        var md = getMDigest(algorithm);
        MDigest.computeHash(is, md);
        return md;
    }

    /**
     * Retrieves an isolated zip virtual path representing the target entry.
     * 
     * @param entry the target entry details
     * 
     * @return a virtual file path sequence
     * 
     * @throws IOException if the target archive zip filesystem cannot be mapped
     */
    private Path getPath(final Entry entry) throws IOException {
        try (final var srcfs = FileSystems.newFileSystem(entry.getParent().getFile().toPath(), (ClassLoader) null)) {
            return srcfs.getPath(entry.getFile());
        }
    }

    /**
     * Resolves and returns a matching entry for a specific profile ROM using checksum indexes.
     * 
     * @param r the profile ROM metadata
     * 
     * @return the discovered physical file entry, or {@code null} if unmatched
     */
    Entry findByHash(final Rom r) {
        Entry entry = null;
        if (r.getSha1() != null) {
            if (null != (entry = entriesBySha1.get(r.getSha1())))
                return entry;
            if (isSuspiciousCRC(r.getCrc()))
                return null;
        }
        if (r.getMd5() != null) {
            if (null != (entry = entriesByMd5.get(r.getMd5())))
                return entry;
            if (isSuspiciousCRC(r.getCrc()))
                return null;
        }
        return entriesByCrc.get(r.getCrc() + "." + r.getSize()); //$NON-NLS-1$
    }

    /**
     * Resolves and returns a matching entry for a specific profile hard disk CHD using checksum indexes.
     * 
     * @param d the profile disk metadata
     * 
     * @return the discovered physical disk file entry, or {@code null} if unmatched
     */
    Entry findByHash(final Disk d) {
        Entry entry = null;
        if (d.getSha1() != null && null != (entry = entriesBySha1.get(d.getSha1())))
            return entry;
        return entriesByMd5.get(d.getMd5());
    }

    /**
     * Selects appropriate file caching extension based on configured scanning metrics.
     * 
     * @param options options configurations
     * 
     * @return extension suffix string (.scache, .dcache, etc.)
     */
    private static String getCacheExt(Set<Options> options) {
        if (options.contains(Options.IS_DEST)) {
            return getCacheExtDest(options);
        } else {
            if (options.contains(Options.ARCHIVES_AND_CHD_AS_ROMS)) {
                if (options.contains(Options.RECURSE))
                    return ".rascache"; //$NON-NLS-1$
                return ".ascache"; //$NON-NLS-1$
            }
            if (options.contains(Options.RECURSE))
                return ".rscache"; //$NON-NLS-1$
            return ".scache"; //$NON-NLS-1$
        }
    }

    /**
     * Resolves appropriate file caching extension for destination directories.
     * 
     * @param options options configurations
     * 
     * @return extension suffix string (.dcache, etc.)
     */
    private static String getCacheExtDest(Set<Options> options) {
        if (options.contains(Options.ARCHIVES_AND_CHD_AS_ROMS)) {
            if (options.contains(Options.RECURSE))
                return ".radcache"; //$NON-NLS-1$
            return ".adcache"; //$NON-NLS-1$
        }
        if (options.contains(Options.RECURSE))
            return ".rdcache"; //$NON-NLS-1$
        return ".dcache"; //$NON-NLS-1$
    }

    /**
     * Computes the cache file matching a directory run.
     * 
     * @param session current workspace session
     * @param file root directory
     * @param options options configurations
     * 
     * @return physical cache file location
     */
    public static File getCacheFile(final Session session, final File file, Set<Options> options) {
        final var workdir = session.getUser().getSettings().getWorkPath().toFile(); // $NON-NLS-1$
        final var cachedir = new File(workdir, "cache"); //$NON-NLS-1$
        cachedir.mkdirs();
        final var crc = new CRC32();
        crc.update(file.getAbsolutePath().getBytes());
        return new File(cachedir, String.format("%08x", crc.getValue()) + getCacheExt(options)); //$NON-NLS-1$ //$NON-NLS-2$
                                                                                                 // //$NON-NLS-3$
    }

    /**
     * Serializes current scans properties to the computed cache file.
     * 
     * @param file root folder file
     * @param options options configurations
     */
    private void save(final File file, Set<Options> options) {
        try (final var oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(getCacheFile(session, file, options))))) {
            oos.writeObject(containersByName);
        } catch (final Exception _) {
            // ignore
        }
    }

    /**
     * Deserializes previous runs properties from disk.
     * 
     * @param file root directory file
     * @param options options configurations
     * 
     * @return containers mapping retrieved from caching
     */
    @SuppressWarnings("unchecked")
    private Map<String, Container> load(final File file, Set<Options> options) {
        final var cachefile = getCacheFile(session, file, options);
        try (final var ois = new ValidatingObjectInputStream(new BufferedInputStream(new FileInputStream(cachefile)))) {
            handler.clearInfos();
            handler.setProgress(String.format(Messages.getString("DirScan.LoadingScanCache"), getRelativePath(file.toPath())), 0); //$NON-NLS-1$
            return (Map<String, Container>) ois.readObject();
        } catch (final Exception e) {
            Log.err(() -> "Failed to load cache file: " + cachefile.getAbsolutePath(), e);
        }
        return Collections.synchronizedMap(new HashMap<>());
    }
    
    /**
     * A validating ObjectInputStream that only allows deserialization of specific safe classes.
     * This prevents arbitrary deserialization attacks by implementing an allowlist of permitted classes.
     */
    private static final class ValidatingObjectInputStream extends ObjectInputStream {
        
        /**
         * Set of allowed class name prefixes for deserialization.
         */
        private static final Set<String> ALLOWED_CLASSES = Set.of(
            // Java standard library classes
            "java.lang.String",
            "java.lang.Long",
            "java.lang.Integer",
            "java.lang.Boolean",
            "java.lang.Number",
            "java.lang.Enum",
            "java.io.File",
            "java.util.HashMap",
            "java.util.LinkedHashMap",
            "java.util.ArrayList",
            "java.util.Collections$SynchronizedMap",
            "java.util.Collections$UnmodifiableMap",
            "java.util.EnumSet",
            "java.util.RegularEnumSet",
            "java.util.JumboEnumSet",
            // Application domain classes
            "jrm.profile.data.Container",
            "jrm.profile.data.Archive",
            "jrm.profile.data.Directory",
            "jrm.profile.data.FakeDirectory",
            "jrm.profile.data.Entry",
            "jrm.profile.data.Rom",
            "jrm.profile.data.Disk",
            "jrm.profile.data.Entity",
            "jrm.profile.data.EntityBase",
            "jrm.profile.data.NameBase",
            "jrm.profile.data.Container$Type",
            "jrm.profile.data.Entry$Type",
            "jrm.profile.data.Entity$Status",
            // External library enums
            "jtrrntzip.TrrntZipStatus"
        );
        
        /**
         * Constructs a ValidatingObjectInputStream that reads from the specified InputStream.
         * 
         * @param in the input stream to read from
         * @throws IOException if an I/O error occurs while reading stream header
         */
        ValidatingObjectInputStream(final InputStream in) throws IOException {
            super(in);
        }
        
        @Override
        protected Class<?> resolveClass(final ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            final String className = desc.getName();
            
            // Allow primitive types and arrays
            if (className.startsWith("[")) {
                // Array type - validate component type
                return super.resolveClass(desc);
            }
            
            // Check if the class is in the allowlist
            if (!ALLOWED_CLASSES.contains(className)) {
                Log.err(() -> "Blocked deserialization of unauthorized class: " + className);
                throw new InvalidClassException("Unauthorized deserialization attempt", className);
            }
            
            return super.resolveClass(desc);
        }
    }

    /**
     * Provides a collection iterator over all discovered container systems.
     * 
     * @return container iterator collection
     */
    Iterable<Container> getContainersIterable() {
        return containers;
    }

    /**
     * Resolves a container reference by name.
     * 
     * @param name the container name
     * 
     * @return discovered container, or {@code null} if unmatched
     */
    Container getContainerByName(String name) {
        return containersByName.get(name);
    }

    /**
     * Obtains the root scan folder file.
     * 
     * @return directory root file
     */
    File getDir() {
        return dir;
    }

}
