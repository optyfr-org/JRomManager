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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import jrm.aui.progress.ProgressHandler;
import jrm.misc.BreakException;
import jrm.misc.ProfileSettings;
import jrm.misc.ProfileSettingsEnum;
import jrm.profile.Profile;
import jrm.profile.manager.ProfileNFO;
import jrm.profile.scan.options.FormatOptions;
import jrm.profile.scan.options.MergeOptions;
import jrm.security.Session;

/**
 * Layered tests for the {@link Scan} orchestration class.
 *
 * <p>The suite is split into three layers exercising progressively more of the class:</p>
 * <ul>
 *   <li><b>Static helpers</b> &mdash; pure unit tests of {@code getBaseName(File)} and {@code not(Predicate)} via reflection,
 *       with no I/O.</li>
 *   <li><b>Destination validation</b> &mdash; {@link ScanException} paths of the private {@code init*DstDir} methods, which run
 *       before the constructor's heavy {@code try/finally} block and therefore stay fast and hermetic.</li>
 *   <li><b>End-to-end integration</b> &mdash; a real {@code new Scan(profile, handler)} run against the real
 *       {@code MAME 0.288 ROMs (merged).xml} fixture with an empty destination directory, asserting the deterministic
 *       "everything missing" outcome. Uses a real {@link Session} (server mode) so the real {@code Report}, settings and
 *       work-path machinery are exercised; only {@link ProgressHandler} is mocked.</li>
 * </ul>
 *
 * @author optyfr
 * @see Scan
 * @see ScanException
 */
@DisplayName("Scan Tests")
class ScanTest {

    /** System property used by {@code GlobalSettings} to locate the work directory in server mode. */
    private static final String JRM_DIR_PROP = "jrommanager.dir";
    /** Path of the real MAME 0.288 ROMs DAT fixture relative to the module root. */
    private static final String MAME_DAT_PATH = "src/test/resources/dats/MAME 0.288 ROMs (merged).xml";

    /**
     * Reflectively invokes the private static {@code getBaseName(File)} helper.
     *
     * @param file the file whose name must be stripped of its extension
     * @return the stripped base name
     * @throws ReflectiveOperationException if reflection fails
     */
    private static String invokeGetBaseName(final File file) throws ReflectiveOperationException {
        final Method method = Scan.class.getDeclaredMethod("getBaseName", File.class);
        method.setAccessible(true);
        return (String) method.invoke(null, file);
    }

    /**
     * Reflectively invokes the private static {@code not(Predicate)} helper.
     *
     * @param <T> the predicate argument type
     * @param predicate the predicate to negate
     * @return the negated predicate
     * @throws ReflectiveOperationException if reflection fails
     */
    @SuppressWarnings("unchecked")
    private static <T> Predicate<T> invokeNot(final Predicate<T> predicate) throws ReflectiveOperationException {
        final Method method = Scan.class.getDeclaredMethod("not", Predicate.class);
        method.setAccessible(true);
        return (Predicate<T>) method.invoke(null, predicate);
    }

    /**
     * Builds an empty {@link Profile} via its private no-argument constructor.
     *
     * @return a fresh empty profile
     * @throws ReflectiveOperationException if reflection fails
     */
    private static Profile newProfile() throws ReflectiveOperationException {
        final Constructor<Profile> constructor = Profile.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    /**
     * Injects a value into a private field of the given profile.
     *
     * @param profile the target profile
     * @param fieldName the field name to write
     * @param value the value to inject
     * @throws ReflectiveOperationException if reflection fails
     */
    private static void setField(final Profile profile, final String fieldName, final Object value) throws ReflectiveOperationException {
        final Field field = Profile.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(profile, value);
    }

    /**
     * Builds a real {@link ProfileNFO} via its private {@code ProfileNFO(File)} constructor.
     *
     * @param file the associated database file (must not be a {@code .jrm} file)
     * @return a fresh profile NFO
     * @throws ReflectiveOperationException if reflection fails
     */
    private static ProfileNFO newProfileNFO(final File file) throws ReflectiveOperationException {
        final Constructor<ProfileNFO> constructor = ProfileNFO.class.getDeclaredConstructor(File.class);
        constructor.setAccessible(true);
        return constructor.newInstance(file);
    }

    /**
     * Creates a {@link ProfileSettings} pre-populated with a valid scan configuration, optionally overriding the ROMs
     * destination directory.
     *
     * @param romsDstDir the ROMs destination directory path, or {@code null} to leave it empty
     * @return a scan-ready profile settings instance
     */
    private static ProfileSettings scanReadySettings(final String romsDstDir) {
        final var settings = new ProfileSettings();
        settings.setProperty(ProfileSettingsEnum.format, FormatOptions.DIR.toString());
        settings.setProperty(ProfileSettingsEnum.merge_mode, MergeOptions.NOMERGE.toString());
        // Use the String overloads for ProfileSettingsEnum keys that have field-propagation side effects
        // (implicit_merge, hash_collision_mode): the boolean/int overloads route through SettingsEnum.from(String)
        // which does not recover the ProfileSettingsEnum, so propagate(...) would not match and the field stays null.
        settings.setProperty(ProfileSettingsEnum.implicit_merge, Boolean.FALSE.toString());
        settings.setProperty(ProfileSettingsEnum.create_mode, false);
        settings.setProperty(ProfileSettingsEnum.createfull_mode, false);
        settings.setProperty(ProfileSettingsEnum.use_parallelism, false);
        settings.setProperty(ProfileSettingsEnum.backup, false);
        settings.setProperty(ProfileSettingsEnum.ignore_unneeded_containers, false);
        settings.setProperty(ProfileSettingsEnum.ignore_unneeded_entries, false);
        settings.setProperty(ProfileSettingsEnum.ignore_unknown_containers, true);
        settings.setProperty(ProfileSettingsEnum.roms_dest_dir, romsDstDir == null ? "" : romsDstDir);
        settings.setProperty(ProfileSettingsEnum.src_dir, "");
        return settings;
    }

    /**
     * Tests for the private static {@code getBaseName(File)} helper which strips the final extension from a filename using the
     * {@code ^(\d*?)(\.\w{1,5})?$} style regex.
     */
    @Nested
    @DisplayName("getBaseName")
    class GetBaseNameTests {

        /**
         * Verifies that a filename without an extension is returned unchanged.
         *
         * @throws ReflectiveOperationException if reflection fails
         */
        @Test
        @DisplayName("should return name unchanged when there is no extension")
        void shouldReturnNameUnchangedWhenNoExtension() throws ReflectiveOperationException {
            assertThat(invokeGetBaseName(new File("gamename"))).isEqualTo("gamename");
        }

        /**
         * Verifies that a single short extension is stripped.
         *
         * @throws ReflectiveOperationException if reflection fails
         */
        @Test
        @DisplayName("should strip single short extension")
        void shouldStripSingleShortExtension() throws ReflectiveOperationException {
            assertThat(invokeGetBaseName(new File("pacman.zip"))).isEqualTo("pacman");
        }

        /**
         * Verifies that only the final extension is stripped from a multi-part extension (the group is non-greedy).
         *
         * @throws ReflectiveOperationException if reflection fails
         */
        @Test
        @DisplayName("should strip only final extension from multi-part extension")
        void shouldStripOnlyFinalExtensionFromMultiPart() throws ReflectiveOperationException {
            assertThat(invokeGetBaseName(new File("archive.tar.gz"))).isEqualTo("archive.tar");
        }

        /**
         * Verifies that an extension longer than five characters is not stripped (the regex caps the extension at one to five
         * word characters).
         *
         * @throws ReflectiveOperationException if reflection fails
         */
        @Test
        @DisplayName("should not strip extension longer than five characters")
        void shouldNotStripExtensionLongerThanFiveChars() throws ReflectiveOperationException {
            assertThat(invokeGetBaseName(new File("data.sample"))).isEqualTo("data.sample");
        }

        /**
         * Verifies that a leading-dot (dotfile) name is handled by the regex.
         *
         * @throws ReflectiveOperationException if reflection fails
         */
        @Test
        @DisplayName("should handle dotfile name")
        void shouldHandleDotfileName() throws ReflectiveOperationException {
            assertThat(invokeGetBaseName(new File(".hidden"))).isEqualTo(".hidden");
        }
    }

    /**
     * Tests for the private static {@code not(Predicate)} helper which negates a predicate.
     */
    @Nested
    @DisplayName("not predicate")
    class NotPredicateTests {

        /**
         * Verifies that negating an always-true predicate yields {@code false}.
         *
         * @throws ReflectiveOperationException if reflection fails
         */
        @Test
        @DisplayName("should negate always-true predicate to false")
        void shouldNegateAlwaysTruePredicate() throws ReflectiveOperationException {
            assertThat(invokeNot((Predicate<Object>) _ -> true).test(new Object())).isFalse();
        }

        /**
         * Verifies that negating an always-false predicate yields {@code true}.
         *
         * @throws ReflectiveOperationException if reflection fails
         */
        @Test
        @DisplayName("should negate always-false predicate to true")
        void shouldNegateAlwaysFalsePredicate() throws ReflectiveOperationException {
            assertThat(invokeNot((Predicate<Object>) _ -> false).test(new Object())).isTrue();
        }

        /**
         * Verifies that the returned predicate is a true logical negation across multiple inputs.
         *
         * @throws ReflectiveOperationException if reflection fails
         */
        @Test
        @DisplayName("should be a true logical negation")
        void shouldBeTrueLogicalNegation() throws ReflectiveOperationException {
            final Predicate<String> isEmpty = String::isEmpty;
            final Predicate<String> notIsEmpty = invokeNot(isEmpty);
            assertThat(notIsEmpty.test("")).isFalse();
            assertThat(notIsEmpty.test("x")).isTrue();
            assertThat(notIsEmpty.test("hello")).isTrue();
        }
    }

    /**
     * Tests for the {@link ScanException} validation paths of the private {@code init*DstDir} methods.
     *
     * <p>These methods run before the constructor's {@code try/finally} block, so a validation failure propagates without
     * triggering report writing or profile saving &mdash; keeping the tests fast and free of side effects.</p>
     */
    @Nested
    @DisplayName("Destination directory validation")
    class DestinationValidationTests {

        /** Temporary directory used for the work path and destination folders. */
        @TempDir
        Path tempDir;

        /** The real server-mode session whose work path is redirected to {@link #tempDir}. */
        private Session session;
        /** Mocked progress handler that never cancels. */
        private ProgressHandler handler;

        /**
         * Initializes a real server-mode session with the work path pointing at the temp directory, before each test.
         */
        @BeforeEach
        void setUp() {
            System.setProperty(JRM_DIR_PROP, tempDir.toString());
            session = new Session("scan-validation", "JRomManager", new String[] { "admin" });
            handler = mock(ProgressHandler.class);
            when(handler.isCancel()).thenReturn(false);
        }

        /**
         * Clears the work-directory system property after each test to avoid leaking state.
         */
        @AfterEach
        void tearDown() {
            System.clearProperty(JRM_DIR_PROP);
        }

        /**
         * Builds a profile wired with a real session, a scan-ready settings instance and a real {@link ProfileNFO}, so that
         * invoking {@code new Scan(profile, handler)} reaches the destination validation methods.
         *
         * @param settings the profile settings to inject
         * @return the wired profile
         * @throws ReflectiveOperationException if reflection fails
         */
        private Profile buildProfile(final ProfileSettings settings) throws ReflectiveOperationException {
            final Profile profile = newProfile();
            setField(profile, "session", session);
            setField(profile, "settings", settings);
            setField(profile, "nfo", newProfileNFO(tempDir.resolve("test.xml").toFile()));
            return profile;
        }

        /**
         * Creates and returns an existing ROMs destination directory so that {@code initRomsDstDir} passes and execution reaches the
         * downstream destination validation methods under test.
         *
         * @return the absolute path of a real temporary ROMs directory
         * @throws java.io.IOException if the directory cannot be created
         */
        private String validRomsDir() throws java.io.IOException {
            return Files.createDirectories(tempDir.resolve("roms")).toString();
        }

        /**
         * Verifies that an empty ROMs destination directory throws {@link ScanException} with the expected message.
         *
         * @throws ReflectiveOperationException if reflection fails
         */
        @Test
        @DisplayName("should throw ScanException when ROMs destination dir is empty")
        void shouldThrowWhenRomsDstDirEmpty() throws ReflectiveOperationException {
            final Profile profile = buildProfile(scanReadySettings(null));
            assertThatThrownBy(() -> new Scan(profile, handler))
                .isInstanceOf(ScanException.class)
                .hasMessageContaining("dst dir is empty");
        }

        /**
         * Verifies that a non-existent ROMs destination directory throws {@link ScanException} with the expected message.
         *
         * @throws ReflectiveOperationException if reflection fails
         */
        @Test
        @DisplayName("should throw ScanException when ROMs destination dir is not a directory")
        void shouldThrowWhenRomsDstDirNotADirectory() throws ReflectiveOperationException {
            final Profile profile = buildProfile(scanReadySettings(tempDir.resolve("missing").toString()));
            assertThatThrownBy(() -> new Scan(profile, handler))
                .isInstanceOf(ScanException.class)
                .hasMessageContaining("dst dir is not a directory");
        }

        /**
         * Verifies that an enabled-but-empty samples destination directory throws {@link ScanException}.
         *
         * @throws ReflectiveOperationException if reflection fails
         */
        @Test
        @DisplayName("should throw ScanException when samples destination dir is empty")
        void shouldThrowWhenSamplesDstDirEmpty() throws ReflectiveOperationException, java.io.IOException {
            final var settings = scanReadySettings(validRomsDir());
            settings.setProperty(ProfileSettingsEnum.samples_dest_dir_enabled, true);
            settings.setProperty(ProfileSettingsEnum.samples_dest_dir, "");
            final Profile profile = buildProfile(settings);
            assertThatThrownBy(() -> new Scan(profile, handler))
                .isInstanceOf(ScanException.class)
                .hasMessageContaining("Samples dst dir is empty");
        }

        /**
         * Verifies that an enabled-but-non-existent samples destination directory throws {@link ScanException}.
         *
         * @throws ReflectiveOperationException if reflection fails
         */
        @Test
        @DisplayName("should throw ScanException when samples destination dir is not a directory")
        void shouldThrowWhenSamplesDstDirNotADirectory() throws ReflectiveOperationException, java.io.IOException {
            final var settings = scanReadySettings(validRomsDir());
            settings.setProperty(ProfileSettingsEnum.samples_dest_dir_enabled, true);
            settings.setProperty(ProfileSettingsEnum.samples_dest_dir, tempDir.resolve("no-samples").toString());
            final Profile profile = buildProfile(settings);
            assertThatThrownBy(() -> new Scan(profile, handler))
                .isInstanceOf(ScanException.class)
                .hasMessageContaining("Samples dst dir is not a directory");
        }

        /**
         * Verifies that an enabled-but-empty disks destination directory throws {@link ScanException}.
         *
         * @throws ReflectiveOperationException if reflection fails
         */
        @Test
        @DisplayName("should throw ScanException when disks destination dir is empty")
        void shouldThrowWhenDisksDstDirEmpty() throws ReflectiveOperationException, java.io.IOException {
            final var settings = scanReadySettings(validRomsDir());
            settings.setProperty(ProfileSettingsEnum.disks_dest_dir_enabled, true);
            settings.setProperty(ProfileSettingsEnum.disks_dest_dir, "");
            final Profile profile = buildProfile(settings);
            assertThatThrownBy(() -> new Scan(profile, handler))
                .isInstanceOf(ScanException.class)
                .hasMessageContaining("Disks dst dir is empty");
        }

        /**
         * Verifies that an enabled-but-empty software ROMs destination directory throws {@link ScanException}.
         *
         * @throws ReflectiveOperationException if reflection fails
         */
        @Test
        @DisplayName("should throw ScanException when software ROMs destination dir is empty")
        void shouldThrowWhenSwRomsDstDirEmpty() throws ReflectiveOperationException, java.io.IOException {
            final var settings = scanReadySettings(validRomsDir());
            settings.setProperty(ProfileSettingsEnum.swroms_dest_dir_enabled, true);
            settings.setProperty(ProfileSettingsEnum.swroms_dest_dir, "");
            final Profile profile = buildProfile(settings);
            assertThatThrownBy(() -> new Scan(profile, handler))
                .isInstanceOf(ScanException.class)
                .hasMessageContaining("Software roms dst dir is empty");
        }

        /**
         * Verifies that an enabled-but-empty software disks destination directory throws {@link ScanException}.
         *
         * @throws ReflectiveOperationException if reflection fails
         */
        @Test
        @DisplayName("should throw ScanException when software disks destination dir is empty")
        void shouldThrowWhenSwDisksDstDirEmpty() throws ReflectiveOperationException, java.io.IOException {
            final var settings = scanReadySettings(validRomsDir());
            settings.setProperty(ProfileSettingsEnum.swdisks_dest_dir_enabled, true);
            settings.setProperty(ProfileSettingsEnum.swdisks_dest_dir, "");
            final Profile profile = buildProfile(settings);
            assertThatThrownBy(() -> new Scan(profile, handler))
                .isInstanceOf(ScanException.class)
                .hasMessageContaining("Software Disks dst dir is empty");
        }
    }

    /**
     * End-to-end integration tests running {@code new Scan(profile, handler)} against the real MAME 0.288 ROMs DAT fixture with
     * an empty destination directory.
     *
     * <p>Because the destination is empty, the outcome is deterministic: every set with ROMs/disks is reported missing, every ROM
     * is missing, and all fixing-action phases remain empty (no ROMs are ever found in the empty source directories, so even with
     * {@code create_mode} enabled nothing is queued for creation).</p>
     */
    @Nested
    @DisplayName("End-to-end scan against MAME 0.288 DAT")
    class EndToEndScanTests {

        /** Temporary directory used as the work path and destination root. */
        @TempDir
        Path tempDir;

        /** The real server-mode session whose work path is redirected to {@link #tempDir}. */
        private Session session;
        /** Mocked progress handler that never cancels and passes input streams through unchanged. */
        private ProgressHandler handler;
        /** The real MAME 0.288 ROMs DAT file fixture. */
        private File datFile;

        /**
         * Initializes the real session, the mocked progress handler and locates the DAT fixture before each test. The always-scanned
         * backup source directory is pre-created so source scanning completes quickly.
         */
        @BeforeEach
        void setUp() throws java.io.IOException {
            System.setProperty(JRM_DIR_PROP, tempDir.toString());
            // The backup source directory is always added by Scan.initSrcDirs; pre-create it so the DirScan over it is cheap.
            Files.createDirectories(tempDir.resolve("users").resolve("JRomManager").resolve("backup"));
            session = new Session("scan-e2e", "JRomManager", new String[] { "admin" });
            handler = mock(ProgressHandler.class, withSettings().stubOnly());
            when(handler.isCancel()).thenReturn(false);
            when(handler.getInputStream(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
            datFile = Path.of(MAME_DAT_PATH).toFile();
            assertThat(datFile).exists();
        }

        /**
         * Clears the work-directory system property after each test.
         */
        @AfterEach
        void tearDown() {
            System.clearProperty(JRM_DIR_PROP);
        }

        /**
         * Loads the real MAME 0.288 ROMs DAT into a {@link Profile} using the reflection-based pattern from
         * {@code ProfileParserTest}, then injects a real session, a scan-ready settings instance and a real {@link ProfileNFO}.
         *
         * @param settings the scan-ready profile settings to inject after loading
         * @return the loaded and wired profile
         * @throws ReflectiveOperationException if reflection fails
         */
        private Profile loadAndWireProfile(final ProfileSettings settings) throws ReflectiveOperationException {
            // Use the public Profile.load entry point so the full post-parse pipeline runs: buildParentClonesRelations,
            // loadSettings, loadSystems/Years/CatVer/NPlayers, and initialization of filterEntities/filterList/filterListLists
            // (all required by Scan.scanWare's filterRoms/filterDisks/getDest).
            final ProfileNFO nfo = newProfileNFO(datFile);
            final Profile profile = Profile.load(session, nfo, handler);
            assertThat(profile).as("Profile.load should return a non-null profile").isNotNull();
            assertThat(profile.getMachinesCnt()).as("Profile should contain machines").isPositive();
            // Profile.load populated settings from defaults via loadSettings(); override with our scan-ready settings.
            setField(profile, "settings", settings);
            return profile;
        }

        /**
         * Builds scan-ready settings pointing the ROMs destination at an empty temp directory.
         *
         * @param backup whether the backup phase should be enabled
         * @return the scan-ready profile settings
         * @throws java.io.IOException if the destination directory cannot be created
         */
        private ProfileSettings e2eSettings(final boolean backup) throws java.io.IOException {
            final var romsDst = Files.createDirectories(tempDir.resolve("roms")).toString();
            final var settings = scanReadySettings(romsDst);
            // Enable create mode so missing ROM/disk counters are incremented in scanRomsForMissingContainer; with empty source
            // directories nothing is ever found, so no creation actions are queued.
            settings.setProperty(ProfileSettingsEnum.create_mode, true);
            settings.setProperty(ProfileSettingsEnum.backup, backup);
            return settings;
        }

        /**
         * Verifies that scanning an empty destination against the real MAME 0.288 DAT reports every set with ROMs/disks as missing,
         * every ROM as missing, and produces only empty fixing-action phases.
         *
         * @throws ReflectiveOperationException if reflection fails
         * @throws java.io.IOException if a temp directory cannot be created
         */
        @Test
        @Timeout(180)
        @DisplayName("should report all sets missing when destination is empty")
        void shouldReportAllSetsMissingWhenDestinationEmpty() throws ReflectiveOperationException, java.io.IOException, ScanException {
            final Profile profile = loadAndWireProfile(e2eSettings(false));
            final Scan scan = new Scan(profile, handler);

            assertThat(scan.report).isNotNull();
            final var stats = scan.report.getStats();
            // scanWare increments missingSetCnt (not the rendered setMissing counter) for each missing set.
            assertThat(stats.getMissingSetCnt()).isPositive();
            // With an empty destination every ROM is reported missing by scanRomsForMissingContainer.
            assertThat(stats.getMissingRomsCnt()).isEqualTo(profile.getRomsCnt());
            assertThat(scan.actions).hasSize(7).allSatisfy(phase -> assertThat(phase).isEmpty());
        }

        /**
         * Verifies that enabling the backup option adds an (empty) backup phase, yielding eight action phases instead of seven.
         *
         * @throws ReflectiveOperationException if reflection fails
         * @throws java.io.IOException if a temp directory cannot be created
         */
        @Test
        @Timeout(180)
        @DisplayName("should add empty backup phase when backup is enabled")
        void shouldAddEmptyBackupPhaseWhenBackupEnabled() throws ReflectiveOperationException, java.io.IOException, ScanException {
            final Profile profile = loadAndWireProfile(e2eSettings(true));
            final Scan scan = new Scan(profile, handler);

            assertThat(scan.actions).hasSize(8).allSatisfy(phase -> assertThat(phase).isEmpty());
        }

        /**
         * Verifies that a cancelling progress handler makes the scan abort early by throwing {@link BreakException}.
         *
         * <p>The cancelling handler is a stub-only mock so every method returns a safe default. {@code isCancel()} is stubbed to
         * {@code true}, and {@code getInputStream} is stubbed to pass the input through unchanged so that any early I/O inside the
         * constructor does not trip on a {@code null} return before reaching a cancellation check.</p>
         *
         * @throws ReflectiveOperationException if reflection fails
         * @throws java.io.IOException if a temp directory cannot be created
         */
        @Test
        @Timeout(60)
        @DisplayName("should propagate BreakException when progress handler cancels")
        void shouldPropagateBreakExceptionWhenCancelled() throws ReflectiveOperationException, java.io.IOException {
            final Profile profile = loadAndWireProfile(e2eSettings(false));
            final ProgressHandler cancellingHandler = mock(ProgressHandler.class, withSettings().stubOnly());
            when(cancellingHandler.isCancel()).thenReturn(true);
            when(cancellingHandler.getInputStream(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
            assertThatThrownBy(() -> new Scan(profile, cancellingHandler))
                .isInstanceOf(BreakException.class);
        }
    }
}
