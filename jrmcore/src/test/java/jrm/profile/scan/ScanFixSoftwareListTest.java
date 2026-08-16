/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.ResourceBundle;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import jrm.aui.progress.ProgressHandler;
import jrm.misc.ProfileSettings;
import jrm.misc.ProfileSettingsEnum;
import jrm.profile.Profile;
import jrm.profile.data.EntityStatus;
import jrm.profile.data.Software;
import jrm.profile.data.SoftwareList;
import jrm.profile.fix.Fix;
import jrm.profile.manager.ProfileNFO;
import jrm.profile.scan.options.FormatOptions;
import jrm.profile.scan.options.MergeOptions;
import jrm.security.Session;

/**
 * Composite integration tests for the scan &rarr; fix pipeline covering software lists.
 * <p>
 * This test validates the complete workflow:
 * </p>
 * <ul>
 * <li>Load a JRM profile from {@code jrm.zip} containing both machines and software lists</li>
 * <li>Scan from {@code bios} and {@code swroms} directories as sources</li>
 * <li>Fix missing machines and software into a temporary destination directory</li>
 * <li>Verify all machines and software are fully present (all ROMs {@link EntityStatus#OK})</li>
 * </ul>
 * <p>
 * The test uses real fixtures:
 * </p>
 * <ul>
 * <li>{@code src/test/resources/dats/jrm.zip} - Complete JRM profile with machines and software lists</li>
 * <li>{@code src/test/resources/bios} - BIOS ROM files</li>
 * <li>{@code src/test/resources/swroms} - Software list ROM files organized by software list name</li>
 * </ul>
 *
 * @author optyfr
 * 
 * @see Scan
 * @see Fix
 * @see SoftwareList
 */
@DisplayName("Scan + Fix Software List integration tests")
class ScanFixSoftwareListTest {

    /** Path of the jrm.zip fixture relative to the module root. */
    private static final String JRM_ZIP_PATH = "src/test/resources/dats/jrm.zip";
    /** Path of the bios directory relative to the module root. */
    private static final String BIOS_DIR_PATH = "src/test/resources/bios";
    /** Path of the swroms directory relative to the module root. */
    private static final String SWROMS_DIR_PATH = "src/test/resources/swroms";

    /**
     * Builds a Mockito-mocked {@link ProgressHandler} that never cancels and passes input streams through unchanged.
     *
     * @return the mocked progress handler
     */
    static ProgressHandler nonCancellingHandler() {
        final ProgressHandler handler = mock(ProgressHandler.class, withSettings().stubOnly());
        when(handler.isCancel()).thenReturn(false);
        when(handler.getInputStream(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return handler;
    }

    /**
     * Builds a mock {@link ResourceBundle} that returns the requested key (wrapped) for any {@code getString} call.
     *
     * @return a permissive mock resource bundle
     */
    static ResourceBundle fullBundle() {
        final ResourceBundle bundle = mock(ResourceBundle.class, withSettings().stubOnly());
        when(bundle.getString(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> "!" + invocation.getArgument(0) + "!");
        when(bundle.containsKey(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        return bundle;
    }

    /**
     * Extracts the jrm.zip archive to a temporary directory.
     *
     * @param zipPath the path to the jrm.zip file
     * @param targetDir the target directory for extraction
     * 
     * @return the path to the extracted JRM configuration file
     * 
     * @throws IOException if extraction fails
     */
    private File extractJrmZip(Path zipPath, Path targetDir) throws IOException {
        final Path normalizedTargetDir = targetDir.toAbsolutePath().normalize();
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path targetPath = normalizedTargetDir.resolve(entry.getName()).normalize();
                if (!targetPath.startsWith(normalizedTargetDir)) {
                    throw new IOException("Bad zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        // Return the .jrm configuration file
        try (var stream = Files.list(targetDir)) {
            return stream.filter(p -> p.toString().endsWith(".jrm"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElseThrow(() -> new IOException("No .jrm file found in archive"));
        }
    }

    /**
     * Loads a profile from a JRM configuration file using the public load entry point.
     *
     * @param session the session to use
     * @param jrmFile the JRM configuration file
     * @param handler the progress handler
     * 
     * @return the loaded profile
     * 
     * @throws ReflectiveOperationException if reflection fails
     */
    private Profile loadProfile(Session session, File jrmFile, ProgressHandler handler) throws ReflectiveOperationException {
        final ProfileNFO nfo = ScanTestSupport.newProfileNFO(jrmFile);
        return Profile.load(session, nfo, handler);
    }

    /**
     * Asserts that every ROM of the named software has the given status after a scan.
     *
     * @param profile the scanned profile
     * @param softwareListName the software list name
     * @param softwareName the software name to look up
     * @param expected the expected {@link EntityStatus} for all of the software's ROMs
     */
    @SuppressWarnings("unused")
    private static void assertSoftwareRomsStatus(final Profile profile, final String softwareListName, final String softwareName, final EntityStatus expected) /* NOSONAR */ {
        final SoftwareList softwareList = profile.getMachineListList().getSoftwareListList().getByName(softwareListName);
        assertThat((Object) softwareList).as("software list %s should exist", softwareListName).isNotNull();

        final Software software = softwareList.getByName(softwareName);
        assertThat((Object) software).as("software %s should exist in list %s", softwareName, softwareListName).isNotNull();
        assertThat(software.getRoms()).as("software %s should have ROMs", softwareName).isNotEmpty();
        assertThat(software.getRoms()).as("software %s ROMs should all be %s", softwareName, expected)
                .allSatisfy(rom -> assertThat(rom.getStatus()).isEqualTo(expected));
    }

    /**
     * Composite scan &rarr; fix &rarr; rescan tests for software lists.
     */
    @Nested
    @DisplayName("Composite scan -> fix -> rescan for software lists")
    class CompositeSoftwareListTests {

        /** Temporary directory used as the work path, source and destination root. */
        @TempDir
        Path tempDir;
        /** The real server-mode session whose work path is redirected to {@link #tempDir}. */
        private Session session;
        /** Mocked progress handler that never cancels. */
        private ProgressHandler handler;
        /** Path to the extracted JRM configuration file. */
        private File jrmFile;
        /** Path to the bios source directory. */
        private Path biosDir;
        /** Path to the swroms source directory. */
        private Path swromsDir;

        /**
         * Initializes the session, extracts jrm.zip, and locates source directories before each test.
         *
         * @throws IOException if extraction or directory creation fails
         */
        @BeforeEach
        void setUp() throws IOException {
            System.setProperty(ScanTestSupport.JRM_DIR_PROP, tempDir.toString());
            Files.createDirectories(tempDir.resolve("users").resolve("JRomManager").resolve("backup"));

            session = new Session("scanfix-swlist", "JRomManager", new String[] { "admin" });
            session.setMsgs(fullBundle());
            handler = nonCancellingHandler();

            // Extract jrm.zip
            Path zipPath = Path.of(JRM_ZIP_PATH);
            assumeTrue(Files.exists(zipPath), "jrm.zip not found - skipping test");

            Path extractDir = tempDir.resolve("jrm-extract");
            Files.createDirectories(extractDir);
            jrmFile = extractJrmZip(zipPath, extractDir);
            assertThat(jrmFile).exists();

            // Locate source directories
            biosDir = Path.of(BIOS_DIR_PATH);
            swromsDir = Path.of(SWROMS_DIR_PATH);
            assumeTrue(Files.isDirectory(biosDir), "bios directory not found - skipping test");
            assumeTrue(Files.isDirectory(swromsDir), "swroms directory not found - skipping test");
        }

        /**
         * Clears the work-directory system property and releases large object references after each test.
         */
        @AfterEach
        void tearDown() {
            System.clearProperty(ScanTestSupport.JRM_DIR_PROP);
        }

        /**
         * Provides format and merge mode combinations for parameterized tests.
         *
         * @return stream of arguments with format and merge mode pairs
         */
        static Stream<Arguments> formatAndMergeModeProvider() {
            return Stream.of(
                    Arguments.of(FormatOptions.ZIP, MergeOptions.NOMERGE),
                    Arguments.of(FormatOptions.ZIP, MergeOptions.MERGE),
                    Arguments.of(FormatOptions.ZIP, MergeOptions.FULLMERGE),
                    Arguments.of(FormatOptions.ZIP, MergeOptions.SPLIT),
                    Arguments.of(FormatOptions.TZIP, MergeOptions.NOMERGE),
                    Arguments.of(FormatOptions.SEVENZIP, MergeOptions.NOMERGE),
                    Arguments.of(FormatOptions.DIR, MergeOptions.NOMERGE));
        }

        /**
         * Verifies that the complete scan &rarr; fix &rarr; rescan pipeline works for software lists across different format and
         * merge mode combinations: loads the profile from jrm.zip, scans with bios and swroms as sources, fixes into an empty
         * destination, and verifies all software ROMs are present after the fix.
         *
         * @param format the output format to test
         * @param mergeMode the merge mode to test
         * 
         * @throws ReflectiveOperationException if reflection fails
         * @throws IOException if directories cannot be created
         * @throws ScanException if scan fails
         */
        @ParameterizedTest(name = "format={0}, merge={1}")
        @MethodSource("formatAndMergeModeProvider")
        @Timeout(600)
        @DisplayName("should scan, fix, and rescan software lists with different formats and merge modes")
        void shouldScanFixAndRescanSoftwareLists(FormatOptions format, MergeOptions mergeMode) throws ReflectiveOperationException, IOException, ScanException {
            // Arrange: Create empty destination directory
            final var dstDir = Files.createDirectories(tempDir.resolve("dst-" + format + "-" + mergeMode));

            // Build source path: bios|swroms
            final String srcDir = biosDir.toAbsolutePath() + "|" + swromsDir.toAbsolutePath();

            // Create settings with software list destination enabled
            final var settings = new ProfileSettings();
            settings.setProperty(ProfileSettingsEnum.format, format.toString());
            settings.setProperty(ProfileSettingsEnum.merge_mode, mergeMode.toString());
            settings.setProperty(ProfileSettingsEnum.implicit_merge, Boolean.FALSE.toString());
            settings.setProperty(ProfileSettingsEnum.create_mode, true);
            settings.setProperty(ProfileSettingsEnum.createfull_mode, false);
            settings.setProperty(ProfileSettingsEnum.use_parallelism, false);
            settings.setProperty(ProfileSettingsEnum.backup, false);
            settings.setProperty(ProfileSettingsEnum.ignore_unneeded_containers, true);
            settings.setProperty(ProfileSettingsEnum.ignore_unneeded_entries, true);
            settings.setProperty(ProfileSettingsEnum.ignore_unknown_containers, true);
            settings.setProperty(ProfileSettingsEnum.roms_dest_dir, dstDir.toString());
            settings.setProperty(ProfileSettingsEnum.src_dir, srcDir);
            // Enable separate software list destination
            settings.setProperty(ProfileSettingsEnum.swroms_dest_dir_enabled, true);
            settings.setProperty(ProfileSettingsEnum.swroms_dest_dir, dstDir.toString());

            // Load the profile from jrm.zip
            final Profile profile = loadProfile(session, jrmFile, handler);
            ScanTestSupport.setField(profile, "settings", settings);

            // Verify profile has software lists
            assertThat(profile.getMachineListList().getSoftwareListList())
                    .as("profile should have software lists")
                    .isNotEmpty();

            // Create software list subdirectories in destination before scan/fix
            // This is needed because Fix will try to create zip files in these directories
            for (SoftwareList sl : profile.getMachineListList().getSoftwareListList()) {
                Files.createDirectories(dstDir.resolve(sl.getName()));
            }

            // Count software items before scan
            final long totalSoftware = profile.getMachineListList().getSoftwareListList().stream()
                    .mapToLong(SoftwareList::size)
                    .sum();
            assertThat(totalSoftware).as("profile should have software items").isGreaterThan(0);

            // Act: First scan - should detect missing software
            final Scan scan1 = new Scan(profile, handler);
            assertThat(scan1.actions).as("scan should queue creation actions for missing software")
                    .anySatisfy(phase -> assertThat(phase).isNotEmpty());

            // Apply fixes
            final Fix fix = new Fix(profile, scan1, handler);
            assertThat(fix.getActionsRemain()).as("all fix actions should have succeeded").isZero();

            // Act: Re-scan after fix - should report everything OK
            final Scan scan2 = new Scan(profile, handler);
            assertThat(scan2.actions).as("rescan should have no pending actions")
                    .hasSize(7)
                    .allSatisfy(phase -> assertThat(phase).isEmpty());

            // Assert: Verify some software items are fully present
            // Check at least one software from each software list that has files in swroms
            for (SoftwareList sl : profile.getMachineListList().getSoftwareListList()) {
                if (sl.isEmpty())
                    continue;

                // Check if this software list has corresponding files in swroms
                Path slDir = swromsDir.resolve(sl.getName());
                if (!Files.isDirectory(slDir))
                    continue;

                // Verify at least one software from this list is fully present
                boolean foundOk = false;
                for (Software sw : sl) {
                    if (sw.getRoms().isEmpty())
                        continue;

                    // Check if all ROMs are OK
                    boolean allOk = sw.getRoms().stream()
                            .allMatch(rom -> rom.getStatus() == EntityStatus.OK);

                    if (allOk) {
                        foundOk = true;
                        break;
                    }
                }

                // If we have source files for this software list, we should have fixed at least one
                if (Files.list(slDir).findAny().isPresent()) {
                    assertThat(foundOk)
                            .as("at least one software from list %s should be fully present after fix", sl.getName())
                            .isTrue();
                }
            }
        }

        /**
         * Verifies that the scan &rarr; fix &rarr; rescan pipeline correctly deletes unneeded containers when
         * {@code ignore_unneeded_containers=false} and extra files exist in the destination.
         * <p>
         * This test places extra/unknown zip files in a software list subdirectory, then runs the pipeline with
         * {@code ignore_unneeded_containers=false} and {@code ignore_unknown_containers=false}. The scan should detect the extra
         * containers and queue delete actions. After fix, the extra containers should be removed from the destination.
         * </p>
         *
         * @throws ReflectiveOperationException if reflection fails
         * @throws IOException if directories cannot be created
         * @throws ScanException if scan fails
         */
        @Test
        @Timeout(600)
        @DisplayName("should delete unneeded containers when ignore_unneeded_containers is false")
        void shouldDeleteUnneededContainers() throws ReflectiveOperationException, IOException, ScanException {
            // Arrange: Create destination directory with software list subdirectories
            final var dstDir = Files.createDirectories(tempDir.resolve("dst-delete"));
            // Use a separate ROMs destination so the machine scan does not detect
            // the software-list subdirectories as unknown containers (which would
            // cause cascading delete failures when the directory is removed before
            // the file-level DeleteContainer actions run).
            final var romsDstDir = Files.createDirectories(tempDir.resolve("dst-delete-roms"));

            // Load the profile to get software list names
            final Profile profileForLists = loadProfile(session, jrmFile, handler);
            final var settingsForLists = new ProfileSettings();
            settingsForLists.setProperty(ProfileSettingsEnum.format, FormatOptions.ZIP.toString());
            settingsForLists.setProperty(ProfileSettingsEnum.merge_mode, MergeOptions.NOMERGE.toString());
            settingsForLists.setProperty(ProfileSettingsEnum.implicit_merge, Boolean.FALSE.toString());
            settingsForLists.setProperty(ProfileSettingsEnum.create_mode, false);
            settingsForLists.setProperty(ProfileSettingsEnum.createfull_mode, false);
            settingsForLists.setProperty(ProfileSettingsEnum.use_parallelism, false);
            settingsForLists.setProperty(ProfileSettingsEnum.backup, false);
            settingsForLists.setProperty(ProfileSettingsEnum.ignore_unneeded_containers, true);
            settingsForLists.setProperty(ProfileSettingsEnum.ignore_unneeded_entries, true);
            settingsForLists.setProperty(ProfileSettingsEnum.ignore_unknown_containers, true);
            settingsForLists.setProperty(ProfileSettingsEnum.roms_dest_dir, romsDstDir.toString());
            settingsForLists.setProperty(ProfileSettingsEnum.src_dir, "");
            settingsForLists.setProperty(ProfileSettingsEnum.swroms_dest_dir_enabled, true);
            settingsForLists.setProperty(ProfileSettingsEnum.swroms_dest_dir, dstDir.toString());
            ScanTestSupport.setField(profileForLists, "settings", settingsForLists);

            // Create software list subdirectories and place extra/unknown zip files
            String firstSoftwareListName = null;
            for (SoftwareList sl : profileForLists.getMachineListList().getSoftwareListList()) {
                final Path slDir = Files.createDirectories(dstDir.resolve(sl.getName()));
                if (firstSoftwareListName == null && !sl.isEmpty()) {
                    firstSoftwareListName = sl.getName();
                    // Place an extra/unknown zip file that is NOT in the profile
                    Files.writeString(slDir.resolve("unknown_software.zip"), "fake content");
                }
            }
            assertThat(firstSoftwareListName).as("at least one software list should exist").isNotNull();

            // Build source path: bios|swroms
            final String srcDir = biosDir.toAbsolutePath() + "|" + swromsDir.toAbsolutePath();

            // Create settings with ignore_unneeded_containers=false to trigger delete actions
            final var settings = new ProfileSettings();
            settings.setProperty(ProfileSettingsEnum.format, FormatOptions.ZIP.toString());
            settings.setProperty(ProfileSettingsEnum.merge_mode, MergeOptions.NOMERGE.toString());
            settings.setProperty(ProfileSettingsEnum.implicit_merge, Boolean.FALSE.toString());
            settings.setProperty(ProfileSettingsEnum.create_mode, true);
            settings.setProperty(ProfileSettingsEnum.createfull_mode, false);
            settings.setProperty(ProfileSettingsEnum.use_parallelism, false);
            settings.setProperty(ProfileSettingsEnum.backup, false);
            settings.setProperty(ProfileSettingsEnum.ignore_unneeded_containers, false);
            settings.setProperty(ProfileSettingsEnum.ignore_unneeded_entries, false);
            settings.setProperty(ProfileSettingsEnum.ignore_unknown_containers, false);
            settings.setProperty(ProfileSettingsEnum.roms_dest_dir, romsDstDir.toString());
            settings.setProperty(ProfileSettingsEnum.src_dir, srcDir);
            settings.setProperty(ProfileSettingsEnum.swroms_dest_dir_enabled, true);
            settings.setProperty(ProfileSettingsEnum.swroms_dest_dir, dstDir.toString());

            // Load the profile
            final Profile profile = loadProfile(session, jrmFile, handler);
            ScanTestSupport.setField(profile, "settings", settings);

            // Act: First scan - should detect missing software AND extra containers
            final Scan scan1 = new Scan(profile, handler);

            // Assert: Should have delete actions for the unknown container
            assertThat(scan1.actions).as("scan should queue delete actions for unknown containers")
                    .anySatisfy(phase -> assertThat(phase).isNotEmpty());

            // Apply fixes (should create missing and delete extra)
            final Fix fix = new Fix(profile, scan1, handler);
            assertThat(fix.getActionsRemain()).as("all fix actions should have succeeded").isZero();

            // Assert: The extra/unknown zip should be deleted
            final Path unknownZipPath = dstDir.resolve(firstSoftwareListName).resolve("unknown_software.zip");
            assertThat(unknownZipPath).as("unknown container should be deleted after fix").doesNotExist();

            // Act: Re-scan after fix - should report everything OK (no pending actions)
            final Scan scan2 = new Scan(profile, handler);
            assertThat(scan2.actions).as("rescan should have no pending actions after delete")
                    .hasSize(7)
                    .allSatisfy(phase -> assertThat(phase).isEmpty());
        }

        /**
         * Verifies that scanning with bios and swroms as sources detects all available software when the destination already
         * contains the files.
         *
         * @throws ReflectiveOperationException if reflection fails
         * @throws IOException if directories cannot be created
         * @throws ScanException if scan fails
         */
        @Test
        @Timeout(600)
        @DisplayName("should scan software lists as fully present when destination contains files")
        void shouldScanSoftwareListsAsPresentWhenDestinationContainsFiles() throws ReflectiveOperationException, IOException, ScanException {
            // Arrange: Copy bios and swroms to destination
            final var dstDir = Files.createDirectories(tempDir.resolve("dst"));

            // Copy bios files to dstDir/bios
            final var dstBiosDir = Files.createDirectories(dstDir.resolve("bios"));
            try (var biosStream = Files.list(biosDir)) {
                biosStream.filter(Files::isRegularFile).forEach(src -> {
                    try {
                        Files.copy(src, dstBiosDir.resolve(src.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            // Copy swroms files to dstDir preserving structure
            try (var swromsStream = Files.walk(swromsDir)) {
                swromsStream.filter(Files::isRegularFile).forEach(src -> {
                    try {
                        final var rel = swromsDir.relativize(src);
                        final var target = dstDir.resolve(rel);
                        Files.createDirectories(target.getParent());
                        Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            // Create settings
            final var settings = new ProfileSettings();
            settings.setProperty(ProfileSettingsEnum.format, FormatOptions.ZIP.toString());
            settings.setProperty(ProfileSettingsEnum.merge_mode, MergeOptions.NOMERGE.toString());
            settings.setProperty(ProfileSettingsEnum.implicit_merge, Boolean.FALSE.toString());
            settings.setProperty(ProfileSettingsEnum.create_mode, false);
            settings.setProperty(ProfileSettingsEnum.createfull_mode, false);
            settings.setProperty(ProfileSettingsEnum.use_parallelism, false);
            settings.setProperty(ProfileSettingsEnum.backup, false);
            settings.setProperty(ProfileSettingsEnum.ignore_unneeded_containers, true);
            settings.setProperty(ProfileSettingsEnum.ignore_unneeded_entries, true);
            settings.setProperty(ProfileSettingsEnum.ignore_unknown_containers, true);
            settings.setProperty(ProfileSettingsEnum.roms_dest_dir, dstDir.toString());
            settings.setProperty(ProfileSettingsEnum.src_dir, "");
            settings.setProperty(ProfileSettingsEnum.swroms_dest_dir_enabled, true);
            settings.setProperty(ProfileSettingsEnum.swroms_dest_dir, dstDir.toString());

            // Load the profile
            final Profile profile = loadProfile(session, jrmFile, handler);
            ScanTestSupport.setField(profile, "settings", settings);

            // Act: Scan
            final Scan scan = new Scan(profile, handler);

            // Assert: No creation actions should be queued (files are present)
            assertThat(scan.actions).as("scan should have no creation actions when files are present")
                    .hasSize(7)
                    .allSatisfy(phase -> assertThat(phase).isEmpty());
        }
    }
}
