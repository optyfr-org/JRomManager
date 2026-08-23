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
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import jrm.aui.progress.ProgressHandler;
import jrm.compressors.ZipTools;
import jrm.locale.Messages;
import jrm.misc.BreakException;
import jrm.misc.Log;
import jrm.misc.MultiThreadingVirtual;
import jrm.misc.ProfileSettingsEnum;
import jrm.profile.Profile;
import jrm.profile.data.Container;
import jrm.profile.data.Disk;
import jrm.profile.data.Entry;
import jrm.profile.data.Rom;
import jrm.profile.scan.options.FormatOptions;
import jrm.security.PathAbstractor;
import jrm.security.Session;
import net.lingala.zip4j.ZipFile;
import net.sf.sevenzipjbinding.SevenZip;

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
    final Map<String, Entry> entriesByCrc = Collections.synchronizedMap(new HashMap<>());
    /**
     * Map of {@link Entry} elements by SHA-1 hash strings.
     */
    final Map<String, Entry> entriesBySha1 = Collections.synchronizedMap(new HashMap<>());
    /**
     * Map of {@link Entry} elements by MD5 hash strings.
     */
    final Map<String, Entry> entriesByMd5 = Collections.synchronizedMap(new HashMap<>());

    /**
     * Contains the detected suspicious CRCs from the current profile.
     */
    Set<String> suspiciousCrc = null;

    /**
     * The current execution session.
     */
    final Session session;

    /**
     * The root directory entry point.
     */
    private final File dir;

    /**
     * List of file pattern path matchers representing target folder exclusions.
     */
    private List<Map.Entry<String, PathMatcher>> exclusions = Collections.emptyList();

    private final EntryUpdater entryUpdater = new EntryUpdater(this);

    /**
     * Triggers platform initialization for the native SevenZip JBinding library.
     */
    private void init7zJBinding() {
        if (!SevenZip.isInitializedSuccessfully()) {
            try {
                SevenZip.initSevenZipFromPlatformJAR(session.getUser().getSettings().getTmpPath(false).toFile());
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
    boolean isSuspiciousCRC(String crc) {
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
        this.suspiciousCrc = suspiciousCrc;
        this.exclusions = exclusions;

        final var options = new ScanOptions(session, soptions);
        final var path = Paths.get(dir.getAbsolutePath());

        final var scanCache = new ScanCache(session, handler);
        if (Boolean.FALSE.equals(session.getUser().getSettings().getProperty(jrm.misc.SettingsEnum.debug_nocache, Boolean.class))) // $NON-NLS-1$
            containersByName = scanCache.load(dir, soptions);
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
                handler.setProgress(String.format(Messages.getString("DirScan.Scanning"), c.getFile().getName())); //$NON-NLS-1$
                scanContainer(c, handler, options);
                handler.setProgress(String.format(Messages.getString("DirScan.Scanned"), c.getFile().getName())); //$NON-NLS-1$
                handler.setProgress2(String.format("%d/%d (%d%%)", i.incrementAndGet(), containers.size(), //$NON-NLS-1$
                        (int) (j.addAndGet(1 + (int) (c.getSize() >> 20)) * 100.0 / max.get())), j.get());
            } catch (final IOException e) {
                c.setLoaded(0);
                Log.err("IOException when scanning", e);
            } catch (final BreakException _) {
                c.setLoaded(0);
                handler.doCancel();
            } catch (final Exception e) {
                c.setLoaded(0);
                Log.err("Other Exception when listing", e);
            }
            return;
        })) {
            mt.start(containers.stream().sorted(Container.rcomparator()));
        }

        if (!handler.isCancel())
            scanCache.save(dir, soptions, containersByName);

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
                try (final var entries = new SevenZUpdateEntries(this, container, options)) {
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
     */
    private void listFiles(final File dir, final ProgressHandler handler, final Path path, final ScanOptions options) {
        new DirScanLister(this, containers, containersByName, exclusions, handler).listFiles(dir, handler, path, options);
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
            entryUpdater.updateEntry(c.add(entry), c.getFile().toPath(), options);
            c.setLoaded(options.needSha1OrMd5 ? 2 : 1);
        } else {
            for (final Entry entry : c.getEntries())
                entryUpdater.updateEntry(entry, options);
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
                entryUpdater.updateEntry(entry, options);
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
                                entryUpdater.updateEntry(c.add(entry), entryPath, options);
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
                        entryUpdater.updateEntry(entry, zipf, hdr, options);
                    }
                }
                c.setLoaded(options.needSha1OrMd5 ? 2 : 1);

            } else {
                for (final Entry entry : c.getEntries())
                    entryUpdater.updateEntry(entry, zipf, null, options);
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
     * Computes the cache file matching a directory run (delegates to ScanCache for implementation).
     */
    public static File getCacheFile(final Session session, final File file, Set<Options> options) {
        return ScanCache.getCacheFile(session, file, options);
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
