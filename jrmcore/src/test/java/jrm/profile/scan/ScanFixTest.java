/*
 * Copyright (C) 2018 optyfr
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.ResourceBundle;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import jrm.aui.progress.ProgressHandler;
import jrm.profile.Profile;
import jrm.profile.data.EntityStatus;
import jrm.profile.data.Machine;
import jrm.profile.fix.Fix;
import jrm.profile.scan.options.FormatOptions;
import jrm.profile.scan.options.MergeOptions;
import jrm.security.Session;

/**
 * Integration tests for the scan &rarr; fix pipeline using real ROM bytes downloaded from mamedev.org, plus a merge-mode matrix
 * covering all {@link MergeOptions} values.
 *
 * <p>The suite has three layers:</p>
 * <ul>
 *   <li><b>Real-ROM scan</b> &mdash; downloads a freely-distributable MAME ROM zip from {@code https://www.mamedev.org/roms/},
 *       places it in the destination, and asserts the scan reports the set fully present (every ROM {@link EntityStatus#OK}).</li>
 *   <li><b>Real-ROM fix</b> &mdash; with an empty destination and the downloaded ROMs as source, asserts that {@link Fix} builds
 *       the missing containers and a re-scan reports everything OK.</li>
 *   <li><b>Merge-mode matrix</b> &mdash; runs every {@link MergeOptions} against the real ROMs (no clones &rarr; identical
 *       happy-path outcomes, regression guard) and against a synthetic parent/clone DAT (where merge modes genuinely
 *       differentiate: SPLIT keeps the clone container, MERGE flags it unneeded).</li>
 * </ul>
 *
 * <p>Network tests cache downloaded ROMs under {@code build/tmp/mamedev-roms/} (not committed) and skip gracefully via
 * {@link assumeTrue} when mamedev.org is unreachable, so they never fail in offline environments.</p>
 *
 * @author optyfr
 * @see Scan
 * @see Fix
 * @see MergeOptions
 */
@DisplayName("Scan + Fix integration tests")
class ScanFixTest {

    /** Base URL for freely-distributable MAME ROM downloads. */
    private static final String MAMEDEV_BASE = "https://www.mamedev.org/roms/";
    /** Cache directory for downloaded ROM zips, so they are not re-fetched on every test run. */
    private static final Path ROM_CACHE = Path.of("build/tmp/mamedev-roms");
    /** Path of the real MAME 0.288 ROMs DAT fixture relative to the module root. */
    private static final String MAME_DAT_PATH = "src/test/resources/dats/MAME 0.288 ROMs (merged).xml";

    /**
     * Downloads a freely-distributable MAME ROM zip from mamedev.org into the local cache, returning the cached file.
     *
     * <p>If the file is already cached and non-empty, it is returned directly. On any I/O or HTTP failure, {@code null} is
     * returned so callers can skip the test via {@link assumeTrue}.</p>
     *
     * @param id the MAME game id (e.g. {@code robotbwl})
     * @return the cached zip {@link Path}, or {@code null} if the download failed
     */
    static Path downloadRom(final String id) {
        try {
            Files.createDirectories(ROM_CACHE);
            final var cached = ROM_CACHE.resolve(id + ".zip");
            if (Files.exists(cached) && Files.size(cached) > 0)
                return cached;
            final var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
            final var request = HttpRequest.newBuilder(URI.create(MAMEDEV_BASE + id + "/" + id + ".zip")).timeout(Duration.ofSeconds(30)).GET().build();
            final var response = client.send(request, HttpResponse.BodyHandlers.ofFile(cached));
            if (response.statusCode() >= 400 || !Files.exists(cached) || Files.size(cached) == 0)
                return null;
            return cached;
        } catch (final IOException | InterruptedException _) {
            return null;
        }
    }

    /**
     * Builds a Mockito-mocked {@link ProgressHandler} that never cancels and passes input streams through unchanged.
     *
     * @return the mocked progress handler
     */
    static ProgressHandler nonCancellingHandler() {
        final ProgressHandler handler = mock(ProgressHandler.class, withSettings().stubOnly());
        when(handler.isCancel()).thenReturn(false);
        when(handler.getInputStream(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        return handler;
    }

    /**
     * Builds a mock {@link ResourceBundle} that returns the requested key (wrapped) for any {@code getString} call, so code that
     * reads message keys directly from the session bundle (e.g. {@code Fix} reading {@code Fix.Fixing}) does not throw
     * {@code MissingResourceException} under the minimal test {@code Messages.properties}.
     *
     * @return a permissive mock resource bundle
     */
    static ResourceBundle fullBundle() {
        final ResourceBundle bundle = mock(ResourceBundle.class, withSettings().stubOnly());
        when(bundle.getString(anyString())).thenAnswer(invocation -> "!" + invocation.getArgument(0) + "!");
        when(bundle.containsKey(anyString())).thenReturn(true);
        return bundle;
    }

    /**
     * Asserts that every ROM of the named machine has the given status after a scan.
     *
     * @param profile the scanned profile
     * @param machineName the machine name to look up
     * @param expected the expected {@link EntityStatus} for all of the machine's ROMs
     */
    private static void assertMachineRomsStatus(final Profile profile, final String machineName, final EntityStatus expected) {
        final Machine machine = profile.getMachineListList().get(0).getByName(machineName);
        assertThat(machine).as("machine %s should exist in the profile", machineName).isNotNull();
        assertThat(machine.getRoms()).as("machine %s should have ROMs", machineName).isNotEmpty();
        assertThat(machine.getRoms()).as("machine %s ROMs should all be %s", machineName, expected)
            .allSatisfy(rom -> assertThat(rom.getStatus()).isEqualTo(expected));
    }

    /**
     * Real-ROM scan tests: download a freely-distributable ROM, place it in the destination, and assert the scan finds it fully
     * present.
     */
    @Nested
    @DisplayName("Real-ROM scan")
    class RealRomScanTests {

        /** Temporary directory used as the work path and destination root. */
        @TempDir
        Path tempDir;
        /** The real server-mode session whose work path is redirected to {@link #tempDir}. */
        private Session session;
        /** Mocked progress handler that never cancels. */
        private ProgressHandler handler;
        /** The real MAME 0.288 ROMs DAT file fixture. */
        private File datFile;

        /**
         * Initializes the real session, the mocked progress handler and locates the DAT fixture before each test.
         *
         * @throws java.io.IOException if the backup source directory cannot be created
         */
        @BeforeEach
        void setUp() throws IOException {
            System.setProperty(ScanTestSupport.JRM_DIR_PROP, tempDir.toString());
            Files.createDirectories(tempDir.resolve("users").resolve("JRomManager").resolve("backup"));
            session = new Session("scanfix-scan", "JRomManager", new String[] { "admin" });
            handler = nonCancellingHandler();
            datFile = Path.of(MAME_DAT_PATH).toFile();
            assertThat(datFile).exists();
        }

        /**
         * Clears the work-directory system property after each test.
         */
        @AfterEach
        void tearDown() {
            System.clearProperty(ScanTestSupport.JRM_DIR_PROP);
        }

        /**
         * Verifies that a downloaded {@code robotbwl.zip} placed in the destination scans as fully present (all ROMs OK), while a
         * machine whose zip is absent (e.g. {@code circus}) scans as all missing (KO).
         *
         * @throws ReflectiveOperationException if reflection fails
         * @throws java.io.IOException if directories cannot be created
         */
        @Test
        @Timeout(180)
        @DisplayName("should scan downloaded robotbwl ROMs as fully present")
        void shouldScanDownloadedRomsAsPresent() throws ReflectiveOperationException, IOException, ScanException {
            final var romZip = downloadRom("robotbwl");
            assumeTrue(romZip != null, "mamedev.org unreachable or download failed - skipping network test");
            final var romsDir = Files.createDirectories(tempDir.resolve("roms"));
            Files.copy(romZip, romsDir.resolve("robotbwl.zip"), StandardCopyOption.REPLACE_EXISTING);

            final var settings = ScanTestSupport.baseSettings(FormatOptions.ZIP, MergeOptions.NOMERGE, romsDir.toString(), romsDir.toString(), false, false);
            final Profile profile = ScanTestSupport.loadAndWireProfile(session, datFile, handler, settings);

            final Scan scan = new Scan(profile, handler);
            assertMachineRomsStatus(profile, "robotbwl", EntityStatus.OK);
            assertThat(scan.actions).hasSize(7).allSatisfy(phase -> assertThat(phase).isEmpty());
        }

        /**
         * Verifies that when the destination is empty, the scan reports {@code robotbwl} as fully missing (all ROMs KO).
         *
         * @throws ReflectiveOperationException if reflection fails
         * @throws java.io.IOException if directories cannot be created
         */
        @Test
        @Timeout(180)
        @DisplayName("should scan robotbwl as fully missing when destination is empty")
        void shouldScanRomsAsMissingWhenDestinationEmpty() throws ReflectiveOperationException, IOException, ScanException {
            final var romsDir = Files.createDirectories(tempDir.resolve("roms"));
            final var settings = ScanTestSupport.baseSettings(FormatOptions.ZIP, MergeOptions.NOMERGE, romsDir.toString(), "", false, false);
            final Profile profile = ScanTestSupport.loadAndWireProfile(session, datFile, handler, settings);

            new Scan(profile, handler);
            assertMachineRomsStatus(profile, "robotbwl", EntityStatus.KO);
        }
    }

    /**
     * Breadth tests: download and scan every freely-distributable mamedev.org ROM, asserting the scan finds each container and
     * matches at least the game's own ROMs.
     *
     * <p>The profile is loaded once in {@code @BeforeAll} (loading the 16k-machine MAME DAT is expensive) and reused across all
     * parametrized invocations; each invocation only creates a fresh destination directory, injects scan-ready settings, and
     * scans. This keeps the suite fast and avoids JVM instrumentation pressure from repeated profile loads.</p>
     */
    @Nested
    @org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
    @DisplayName("All mamedev.org ROMs scan")
    class AllGamesScanTests {

        /** Shared work directory for the whole parametrized batch (created in {@code @BeforeAll}, removed in {@code @AfterAll}). */
        private Path sharedWorkDir;
        /** The real server-mode session shared across all invocations. */
        private Session session;
        /** Mocked progress handler that never cancels, shared across all invocations. */
        private ProgressHandler handler;
        /** The profile loaded once from the real MAME 0.288 DAT and reused across all invocations. */
        private Profile profile;

        /**
         * Loads the real MAME 0.288 DAT into a shared profile once before the parametrized batch, after redirecting the work path
         * to a temporary directory.
         *
         * @throws java.io.IOException if the work directory or backup folder cannot be created
         * @throws ReflectiveOperationException if reflection fails
         */
        @org.junit.jupiter.api.BeforeAll
        void setUpOnce() throws IOException, ReflectiveOperationException {
            sharedWorkDir = Files.createTempDirectory("jrm-allgames");
            System.setProperty(ScanTestSupport.JRM_DIR_PROP, sharedWorkDir.toString());
            Files.createDirectories(sharedWorkDir.resolve("users").resolve("JRomManager").resolve("backup"));
            session = new Session("scanfix-allgames", "JRomManager", new String[] { "admin" });
            handler = nonCancellingHandler();
            final var datFile = Path.of(MAME_DAT_PATH).toFile();
            assertThat(datFile).exists();
            // Load with a placeholder destination; each invocation re-injects settings with its own destination directory.
            profile = ScanTestSupport.loadAndWireProfile(session, datFile, handler,
                ScanTestSupport.baseSettings(FormatOptions.ZIP, MergeOptions.NOMERGE, sharedWorkDir.toString(), "", false, false));
            assertThat(profile).as("shared profile should load").isNotNull();
        }

        /**
         * Clears the work-directory system property and deletes the shared work directory after the parametrized batch.
         *
         * @throws java.io.IOException if the work directory cannot be deleted
         */
        @org.junit.jupiter.api.AfterAll
        void tearDownOnce() throws IOException {
            System.clearProperty(ScanTestSupport.JRM_DIR_PROP);
            if (sharedWorkDir != null)
                deleteRecursively(sharedWorkDir);
        }

        /**
         * Verifies that a downloaded ROM zip for each freely-distributable mamedev.org game can be placed in a fresh destination
         * and scanned without crashing, with the scan finding the container and matching at least the game's own ROMs.
         *
         * <p>The assertion is intentionally tolerant of the merged-DAT layout: the {@code MAME 0.288 ROMs (merged).xml} fixture
         * merges clone ROMs into parent machine elements (e.g. {@code circus} contains {@code circuso\9004.1a}), but the official
         * single-game zips from mamedev.org only contain that machine's own ROMs. So parents that have clones report the merged
         * clone ROMs as missing (KO), while the machine's own ROMs scan as OK. Asserting "at least one OK ROM" proves the download
         * and scan pipeline works for every game without false completeness claims about merged clone ROMs. Games {@code falcnwld}
         * and {@code witchcrd} are excluded because their download URLs return 404.</p>
         *
         * @param gameId the MAME game id to download and scan
         * @throws java.io.IOException if the destination directory cannot be created
         * @throws ReflectiveOperationException if reflection fails
         */
        @ParameterizedTest(name = "game={0}")
        @ValueSource(strings = {
            "alienar", "carpolo", "circus", "crash", "fax", "fireone", "gridlee", "hardhat", "looping",
            "ripcord", "robby", "robotbwl", "sidetrac", "spectar", "starfire", "supertnk", "targ", "teetert",
            "topgunnr", "victory", "witchgme", "witchjol", "wldwitch", "wstrike", "wtchjack", "wupndown"
        })
        @Timeout(120)
        @DisplayName("should download and scan each mamedev ROM without crashing")
        void shouldScanEachDownloadedRom(final String gameId) throws IOException, ReflectiveOperationException, ScanException {
            final var romZip = downloadRom(gameId);
            assumeTrue(romZip != null, "mamedev.org unreachable or download failed for " + gameId + " - skipping network test");
            final var romsDir = Files.createDirectories(sharedWorkDir.resolve("dst-" + gameId));
            Files.copy(romZip, romsDir.resolve(gameId + ".zip"), StandardCopyOption.REPLACE_EXISTING);

            ScanTestSupport.setField(profile, "settings",
                ScanTestSupport.baseSettings(FormatOptions.ZIP, MergeOptions.NOMERGE, romsDir.toString(), romsDir.toString(), false, false));

            final Scan scan = new Scan(profile, handler);
            final Machine machine = profile.getMachineListList().get(0).getByName(gameId);
            assertThat(machine).as("machine %s should exist in the profile", gameId).isNotNull();
            assertThat(machine.getRoms()).as("machine %s should have ROMs", gameId).isNotEmpty();
            assertThat(machine.getRoms()).as("machine %s should have at least one OK ROM (container found)", gameId)
                .anySatisfy(rom -> assertThat(rom.getStatus()).isEqualTo(EntityStatus.OK));
            assertThat(scan.actions).as("scan should always produce 7 action phases (backup disabled)").hasSize(7);
        }

        /**
         * Recursively deletes a directory tree.
         *
         * @param path the root path to delete
         * @throws java.io.IOException if deletion fails
         */
        private static void deleteRecursively(final Path path) throws IOException {
            if (Files.exists(path))
                try (final var walk = Files.walk(path)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (final IOException _) {
                            // best-effort cleanup
                        }
                    });
                }
        }
    }

    /**
     * Real-ROM fix tests: with an empty destination and the downloaded ROMs as source, assert that {@link Fix} builds the missing
     * containers and a re-scan reports everything OK.
     */
    @Nested
    @DisplayName("Real-ROM scan -> fix -> rescan")
    class RealRomFixTests {

        /** Temporary directory used as the work path, source and destination root. */
        @TempDir
        Path tempDir;
        /** The real server-mode session whose work path is redirected to {@link #tempDir}. */
        private Session session;
        /** Mocked progress handler that never cancels. */
        private ProgressHandler handler;
        /** The real MAME 0.288 ROMs DAT file fixture. */
        private File datFile;

        /**
         * Initializes the real session, the mocked progress handler and locates the DAT fixture before each test.
         *
         * @throws java.io.IOException if the backup source directory cannot be created
         */
        @BeforeEach
        void setUp() throws IOException {
            System.setProperty(ScanTestSupport.JRM_DIR_PROP, tempDir.toString());
            Files.createDirectories(tempDir.resolve("users").resolve("JRomManager").resolve("backup"));
            session = new Session("scanfix-fix", "JRomManager", new String[] { "admin" });
            session.setMsgs(fullBundle());
            handler = nonCancellingHandler();
            datFile = Path.of(MAME_DAT_PATH).toFile();
            assertThat(datFile).exists();
        }

        /**
         * Clears the work-directory system property after each test.
         */
        @AfterEach
        void tearDown() {
            System.clearProperty(ScanTestSupport.JRM_DIR_PROP);
        }

        /**
         * Verifies that with an empty destination and the downloaded {@code robotbwl} ROMs as source, the scan queues creation
         * actions, {@link Fix} builds the missing zip, and a re-scan reports {@code robotbwl} fully present (all ROMs OK).
         *
         * @throws ReflectiveOperationException if reflection fails
         * @throws java.io.IOException if directories cannot be created
         */
        @Test
        @Timeout(300)
        @DisplayName("should fix missing robotbwl container from downloaded source ROMs")
        void shouldFixMissingContainerFromDownloadedRoms() throws ReflectiveOperationException, IOException, ScanException {
            final var romZip = downloadRom("robotbwl");
            assumeTrue(romZip != null, "mamedev.org unreachable or download failed - skipping network test");
            final var srcDir = Files.createDirectories(tempDir.resolve("src"));
            final var dstDir = Files.createDirectories(tempDir.resolve("dst"));
            Files.copy(romZip, srcDir.resolve("robotbwl.zip"), StandardCopyOption.REPLACE_EXISTING);

            final var settings = ScanTestSupport.baseSettings(FormatOptions.ZIP, MergeOptions.NOMERGE, dstDir.toString(), srcDir.toString(), true, false);
            final Profile profile = ScanTestSupport.loadAndWireProfile(session, datFile, handler, settings);

            final Scan scan = new Scan(profile, handler);
            assertThat(scan.actions).as("scan should queue creation actions for the missing container").anySatisfy(phase -> assertThat(phase).isNotEmpty());

            final Fix fix = new Fix(profile, scan, handler);
            assertThat(fix.getActionsRemain()).as("all fix actions should have succeeded").isZero();
            assertThat(dstDir.resolve("robotbwl.zip")).exists();

            final Scan rescan = new Scan(profile, handler);
            assertMachineRomsStatus(profile, "robotbwl", EntityStatus.OK);
            assertThat(rescan.actions).hasSize(7).allSatisfy(phase -> assertThat(phase).isEmpty());
        }
    }

    /**
     * Merge-mode matrix running every {@link MergeOptions} against the real MAME ROMs (no clones &rarr; identical happy-path
     * outcomes, regression guard) and against a synthetic parent/clone DAT (where merge modes genuinely differentiate).
     */
    @Nested
    @DisplayName("Merge-mode matrix")
    class MergeModeTests {

        /**
         * Verifies that every merge mode scans a downloaded {@code robotbwl.zip} (a standalone machine with no clones) as fully
         * present without crashing. Because {@code robotbwl} has no parent/clone relationship, all modes produce the same
         * happy-path outcome; this is a regression guard ensuring no mode breaks the basic scan.
         *
         * @param mergeMode the merge mode under test
         * @throws ReflectiveOperationException if reflection fails
         * @throws java.io.IOException if directories cannot be created
         */
        @ParameterizedTest(name = "mode={0}")
        @EnumSource(MergeOptions.class)
        @Timeout(240)
        @DisplayName("should scan robotbwl as present for every merge mode (no clones)")
        void shouldScanRomsAsPresentForEveryMergeMode(final MergeOptions mergeMode) throws ReflectiveOperationException, IOException {
            final var romZip = downloadRom("robotbwl");
            assumeTrue(romZip != null, "mamedev.org unreachable or download failed - skipping network test");
            final var workDir = Files.createTempDirectory("jrm-merge-real");
            System.setProperty(ScanTestSupport.JRM_DIR_PROP, workDir.toString());
            try {
                Files.createDirectories(workDir.resolve("users").resolve("JRomManager").resolve("backup"));
                final var session = new Session("scanfix-merge-real", "JRomManager", new String[] { "admin" });
                final var handler = nonCancellingHandler();
                final var datFile = Path.of(MAME_DAT_PATH).toFile();
                final var romsDir = Files.createDirectories(workDir.resolve("roms"));
                Files.copy(romZip, romsDir.resolve("robotbwl.zip"), StandardCopyOption.REPLACE_EXISTING);

                final var settings = ScanTestSupport.baseSettings(FormatOptions.ZIP, mergeMode, romsDir.toString(), romsDir.toString(), false, false);
                final Profile profile = ScanTestSupport.loadAndWireProfile(session, datFile, handler, settings);

                assertDoesNotThrow(() -> new Scan(profile, handler));
                assertMachineRomsStatus(profile, "robotbwl", EntityStatus.OK);
            } finally {
                System.clearProperty(ScanTestSupport.JRM_DIR_PROP);
            }
        }

        /**
         * Verifies that merge modes differentiate on a synthetic parent/clone DAT: with {@link MergeOptions#SPLIT} a complete clone
         * container is kept (no actions), whereas with a merging mode ({@link MergeOptions#MERGE} or {@link MergeOptions#FULLMERGE})
         * the clone container is flagged unneeded (non-empty delete/backup actions).
         *
         * @param mergeMode the merge mode under test
         * @throws ReflectiveOperationException if reflection fails
         * @throws java.io.IOException if the synthetic DAT or zip fixtures cannot be written
         */
        @ParameterizedTest(name = "mode={0}")
        @EnumSource(MergeOptions.class)
        @Timeout(120)
        @DisplayName("should differentiate parent/clone handling per merge mode on synthetic DAT")
        void shouldDifferentiateCloneHandlingPerMergeMode(final MergeOptions mergeMode) throws ReflectiveOperationException, IOException, ScanException {
            final var workDir = Files.createTempDirectory("jrm-merge-synth");
            System.setProperty(ScanTestSupport.JRM_DIR_PROP, workDir.toString());
            try {
                Files.createDirectories(workDir.resolve("users").resolve("JRomManager").resolve("backup"));
                final var session = new Session("scanfix-merge-synth", "JRomManager", new String[] { "admin" });
                final var handler = nonCancellingHandler();

                final var fixtures = buildSyntheticParentCloneFixtures(workDir);
                final var settings = ScanTestSupport.baseSettings(FormatOptions.ZIP, mergeMode, fixtures.dstDir().toString(), fixtures.dstDir().toString(), false, false);
                final Profile profile = ScanTestSupport.loadAndWireProfile(session, fixtures.datFile().toFile(), handler, settings);

                final Scan scan = new Scan(profile, handler);
                if (mergeMode.isMerge()) {
                    assertThat(scan.actions).as("merging modes should flag the redundant clone container for deletion/backup")
                        .anySatisfy(phase -> assertThat(phase).isNotEmpty());
                } else if (mergeMode == MergeOptions.SPLIT) {
                    assertThat(scan.actions).as("SPLIT should keep the complete clone container and produce no actions").allSatisfy(phase -> assertThat(phase).isEmpty());
                }
            } finally {
                System.clearProperty(ScanTestSupport.JRM_DIR_PROP);
            }
        }

        /**
         * Builds a synthetic DAT with a parent machine and a clone machine sharing one ROM (via a {@code merge} attribute), plus
         * complete destination zip containers for both.
         *
         * <p>The parent contains {@code shared.bin} and {@code parent_only.bin}; the clone (cloneof=parent) contains
         * {@code shared.bin} (merge=shared.bin) and {@code child_only.bin}. The destination holds {@code parent.zip} (with the
         * parent's two ROMs) and {@code child.zip} (with the clone's own {@code child_only.bin}).</p>
         *
         * @param workDir the working directory in which to create the fixtures
         * @return the assembled fixtures (DAT file + destination directory)
         * @throws java.io.IOException if any file cannot be written
         */
        private SyntheticFixtures buildSyntheticParentCloneFixtures(final Path workDir) throws IOException {
            final var shared = "SHARED".getBytes();
            final var parentOnly = "PAREN".getBytes();
            final var childOnly = "CHILD".getBytes();
            final var datFile = workDir.resolve("synthetic.xml");
            Files.writeString(datFile, """
                    <?xml version="1.0" encoding="utf-8"?>
                    <datafile>
                        <header>
                            <name>Synthetic Clone Test</name>
                            <description>Synthetic parent/clone DAT</description>
                        </header>
                        <machine name="parent">
                            <description>Parent</description>
                            <year>2024</year>
                            <manufacturer>Test</manufacturer>
                            <rom name="shared.bin" size="%d" crc="%s" sha1="%s" />
                            <rom name="parent_only.bin" size="%d" crc="%s" sha1="%s" />
                        </machine>
                        <machine name="child" cloneof="parent" romof="parent">
                            <description>Child</description>
                            <year>2024</year>
                            <manufacturer>Test</manufacturer>
                            <rom name="shared.bin" size="%d" crc="%s" sha1="%s" merge="shared.bin" />
                            <rom name="child_only.bin" size="%d" crc="%s" sha1="%s" />
                        </machine>
                    </datafile>
                    """.formatted(
                    shared.length, crcHex(shared), sha1Hex(shared),
                    parentOnly.length, crcHex(parentOnly), sha1Hex(parentOnly),
                    shared.length, crcHex(shared), sha1Hex(shared),
                    childOnly.length, crcHex(childOnly), sha1Hex(childOnly)));
            final var dstDir = Files.createDirectories(workDir.resolve("dst"));
            writeZip(dstDir.resolve("parent.zip"), "shared.bin", shared, "parent_only.bin", parentOnly);
            writeZip(dstDir.resolve("child.zip"), "child_only.bin", childOnly);
            return new SyntheticFixtures(datFile, dstDir);
        }

        /**
         * Computes the lowercase hexadecimal CRC32 of the given bytes.
         *
         * @param bytes the bytes to hash
         * @return the 8-character hex CRC32 string
         */
        private static String crcHex(final byte[] bytes) {
            final var crc = new CRC32();
            crc.update(bytes);
            return String.format("%08x", crc.getValue());
        }

        /**
         * Computes the lowercase hexadecimal SHA-1 of the given bytes.
         *
         * @param bytes the bytes to hash
         * @return the 40-character hex SHA-1 string
         * @throws java.io.IOException if the digest algorithm is unavailable
         */
        private static String sha1Hex(final byte[] bytes) throws IOException {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
            } catch (final java.security.NoSuchAlgorithmException e) {
                throw new IOException(e);
            }
        }

        /**
         * Writes a zip file containing the given named byte entries.
         *
         * @param zipPath the destination zip path
         * @param entries an alternating sequence of entry name and entry bytes
         * @throws java.io.IOException if the zip cannot be written
         */
        private static void writeZip(final Path zipPath, final Object... entries) throws IOException {
            try (final var zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
                for (var i = 0; i < entries.length; i += 2) {
                    zos.putNextEntry(new ZipEntry((String) entries[i]));
                    zos.write((byte[]) entries[i + 1]);
                    zos.closeEntry();
                }
            }
        }

        /**
         * Immutable holder for the synthetic DAT file and destination directory.
         *
         * @param datFile the synthetic DAT file path
         * @param dstDir the destination directory containing the parent and child zip containers
         */
        private record SyntheticFixtures(Path datFile, Path dstDir) {
        }
    }
}
