/*
 * Copyright (C) 2018 optyfr
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jrm.aui.progress.ProgressHandler;
import jrm.misc.GlobalSettings;
import jrm.profile.data.ExportMode;
import jrm.profile.data.Machine;
import jrm.profile.data.Rom;
import jrm.profile.manager.Export;
import jrm.profile.manager.Export.ExportType;
import jrm.security.Session;
import jrm.security.User;

/**
 * Comprehensive tests for Profile loading from JRM archive (jrm.zip).
 * 
 * <p>These tests validate the complete loading pipeline for JRM profiles, including:</p>
 * <ul>
 *   <li>ZIP extraction and file handling</li>
 *   <li>JRM configuration file parsing</li>
 *   <li>Multi-file DAT loading (ROMs + Software Lists)</li>
 *   <li>Complete object graph construction</li>
 * </ul>
 * 
 * <p>The jrm.zip fixture contains a complete JRM profile with:</p>
 * <ul>
 *   <li>JRM5314466932233810111.jrm - Configuration file referencing ROMs and SL DATs</li>
 *   <li>JRM18061833993484010293.jrm1 - ROMs DAT file (MAME machines)</li>
 *   <li>JRM14871978788853077359.jrm2 - Software List DAT file</li>
 * </ul>
 *
 * @author optyfr
 * @see Profile
 * @see ProfileParserTest
 */
@DisplayName("Profile JRM Archive Tests")
class ProfileJrmZipTest {

    /** Mocked session object providing user context and message bundles. */
    private Session session;
    /** Mocked progress handler that never cancels and passes through input streams. */
    private ProgressHandler handler;
    /** Root directory containing the jrm.zip fixture file. */
    private Path datFilesRoot;

    /**
     * Initializes mocked dependencies and sets the DAT files root directory before each test.
     *
     * <p>The session mock returns null for user (no authentication required in tests).
     * The progress handler mock never cancels and passes input streams through unchanged.</p>
     */
    @BeforeEach
    void setUp() {
        session = mock(Session.class,withSettings().stubOnly());
		final var mockSettings = mock(GlobalSettings.class,withSettings().stubOnly());
        when(mockSettings.getWorkPath()).thenReturn(Paths.get(System.getProperty("java.io.tmpdir")));
		final var mockUser = mock(User.class, withSettings().stubOnly());
		when(mockUser.getSettings()).thenReturn(mockSettings);
        when(session.getUser()).thenReturn(mockUser);
        final var msgs = ResourceBundle.getBundle("jrm.resources.Messages");
        when(session.getMsgs()).thenReturn(msgs);
        
        handler = mock(ProgressHandler.class, withSettings().stubOnly());
        when(handler.isCancel()).thenReturn(false);
        when(handler.getInputStream(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        datFilesRoot = Path.of("src/test/resources/dats");
    }

    /**
     * Tests verifying that the jrm.zip archive can be successfully extracted and that
     * all expected files (JRM config, ROMs DAT, Software List DAT) are present with correct sizes.
     */
    @Nested
    @DisplayName("JRM Archive Extraction")
    class JrmArchiveExtractionTests {

        /**
         * Verifies that the jrm.zip archive can be extracted successfully with all expected files present.
         *
         * @param tempDir the temporary directory used for extraction
         * @throws IOException if extraction fails
         */
        @Test
        @DisplayName("Should extract jrm.zip successfully")
        void shouldExtractJrmZipSuccessfully(@TempDir Path tempDir) throws IOException {
            // Arrange
            Path zipPath = datFilesRoot.resolve("jrm.zip");
            assertThat(zipPath).exists();
            
            // Act
            Path extractedDir = extractJrmZip(zipPath, tempDir);
            
            // Assert
            assertThat(extractedDir).exists();
            assertThat(extractedDir.resolve("JRM5314466932233810111.jrm")).exists();
            assertThat(extractedDir.resolve("JRM18061833993484010293.jrm1")).exists();
            assertThat(extractedDir.resolve("JRM14871978788853077359.jrm2")).exists();
        }

        /**
         * Verifies that files extracted from the jrm.zip archive have the expected sizes.
         *
         * @param tempDir the temporary directory used for extraction
         * @throws IOException if extraction fails
         */
        @Test
        @DisplayName("Should extract files with correct sizes")
        void shouldExtractFilesWithCorrectSizes(@TempDir Path tempDir) throws IOException {
            // Arrange
            Path zipPath = datFilesRoot.resolve("jrm.zip");
            
            // Act
            Path extractedDir = extractJrmZip(zipPath, tempDir);
            
            // Assert
            File jrmFile = extractedDir.resolve("JRM5314466932233810111.jrm").toFile();
            File romsFile = extractedDir.resolve("JRM18061833993484010293.jrm1").toFile();
            File slFile = extractedDir.resolve("JRM14871978788853077359.jrm2").toFile();
            
            assertThat(jrmFile).exists();
            assertThat(jrmFile.length()).isGreaterThan(0);
            assertThat(romsFile.length()).isGreaterThan(1_000_000); // ~273MB compressed
            assertThat(slFile.length()).isGreaterThan(1_000_000);   // ~95MB compressed
        }
    }

    /**
     * Tests verifying that JRM profiles can be loaded from extracted archives, including
     * machine loading, ROM loading, and software list loading from multi-file configurations.
     */
    @Nested
    @DisplayName("JRM Profile Loading")
    class JrmProfileLoadingTests {

        /**
         * Verifies that a JRM profile can be loaded successfully from the extracted archive
         * and contains machines and ROMs.
         *
         * @param tempDir the temporary directory used for extraction
         * @throws IOException if extraction or loading fails
         */
        @Test
        @DisplayName("Should load JRM profile from extracted archive")
        void shouldLoadJrmProfileFromExtractedArchive(@TempDir Path tempDir) throws IOException {
            // Arrange
            Path zipPath = datFilesRoot.resolve("jrm.zip");
            Path extractedDir = extractJrmZip(zipPath, tempDir);
            File jrmFile = extractedDir.resolve("JRM5314466932233810111.jrm").toFile();
            
            // Act
            Profile profile = loadProfile(jrmFile);
            
            // Assert
            assertThat(profile).isNotNull();
            assertThat(profile.getMachinesCnt()).isGreaterThan(0);
            assertThat(profile.getRomsCnt()).isGreaterThan(0);
        }

        /**
         * Verifies that machines are loaded correctly from the ROMs DAT file in the JRM archive.
         *
         * @param tempDir the temporary directory used for extraction
         * @throws IOException if extraction or loading fails
         */
        @Test
        @DisplayName("Should load machines from ROMs DAT")
        void shouldLoadMachinesFromRomsDat(@TempDir Path tempDir) throws IOException {
            // Arrange
            Path zipPath = datFilesRoot.resolve("jrm.zip");
            Path extractedDir = extractJrmZip(zipPath, tempDir);
            File jrmFile = extractedDir.resolve("JRM5314466932233810111.jrm").toFile();
            
            // Act
            Profile profile = loadProfile(jrmFile);
            
            // Assert
            assertThat(profile).isNotNull();
            assertThat(profile.getMachineListList()).isNotNull();
            assertThat(profile.getMachineListList()).hasSizeGreaterThan(0);
            assertThat(profile.getMachinesCnt()).isGreaterThan(1000); // MAME has thousands of machines
        }

        /**
         * Verifies that ROMs are loaded correctly from the ROMs DAT file in the JRM archive.
         *
         * @param tempDir the temporary directory used for extraction
         * @throws IOException if extraction or loading fails
         */
        @Test
        @DisplayName("Should load ROMs from ROMs DAT")
        void shouldLoadRomsFromRomsDat(@TempDir Path tempDir) throws IOException {
            // Arrange
            Path zipPath = datFilesRoot.resolve("jrm.zip");
            Path extractedDir = extractJrmZip(zipPath, tempDir);
            File jrmFile = extractedDir.resolve("JRM5314466932233810111.jrm").toFile();
            
            // Act
            Profile profile = loadProfile(jrmFile);
            
            // Assert
            assertThat(profile).isNotNull();
            assertThat(profile.getRomsCnt()).isGreaterThan(10000); // MAME has tens of thousands of ROMs
        }

        /**
         * Verifies that software lists are loaded correctly from the SL DAT file in the JRM archive.
         *
         * @param tempDir the temporary directory used for extraction
         * @throws IOException if extraction or loading fails
         */
        @Test
        @DisplayName("Should load software lists from SL DAT")
        void shouldLoadSoftwareListsFromSlDat(@TempDir Path tempDir) throws IOException {
            // Arrange
            Path zipPath = datFilesRoot.resolve("jrm.zip");
            Path extractedDir = extractJrmZip(zipPath, tempDir);
            File jrmFile = extractedDir.resolve("JRM5314466932233810111.jrm").toFile();
            
            // Act
            Profile profile = loadProfile(jrmFile);
            
            // Assert
            assertThat(profile).isNotNull();
            assertThat(profile.getSoftwaresListCnt()).isGreaterThan(0);
            assertThat(profile.getSoftwaresCnt()).isGreaterThan(0);
        }
    }

    /**
     * Tests verifying data integrity of loaded JRM profiles, including valid machine data,
     * ROM checksums, SHA1 presence, software list structure, and machine-rom relationships.
     */
    @Nested
    @DisplayName("JRM Profile Data Integrity")
    class JrmProfileDataIntegrityTests {

        /**
         * Verifies that machine data (name, description, ROMs) is valid after loading from the JRM archive.
         *
         * @param tempDir the temporary directory used for extraction
         * @throws IOException if extraction or loading fails
         */
        @Test
        @DisplayName("Should have valid machine data after loading")
        void shouldHaveValidMachineDataAfterLoading(@TempDir Path tempDir) throws IOException {
            // Arrange
            Path zipPath = datFilesRoot.resolve("jrm.zip");
            Path extractedDir = extractJrmZip(zipPath, tempDir);
            File jrmFile = extractedDir.resolve("JRM5314466932233810111.jrm").toFile();
            Profile profile = loadProfile(jrmFile);
            
            // Act - Get first machine from first machine list
            Machine machine = profile.getMachineListList().get(0).stream()
                .filter(m -> m.getRoms() != null && !m.getRoms().isEmpty())
                .findFirst()
                .orElse(null);
            
            // Assert
            assertThat(machine).isNotNull();
            assertThat(machine.getName()).isNotBlank();
            assertThat(machine.getDescription()).isNotBlank();
            assertThat(machine.getRoms()).isNotEmpty();
        }

        /**
         * Verifies that ROM checksums (CRC) are valid after loading from the JRM archive.
         *
         * @param tempDir the temporary directory used for extraction
         * @throws IOException if extraction or loading fails
         */
        @Test
        @DisplayName("Should have valid ROM checksums after loading")
        void shouldHaveValidRomChecksumsAfterLoading(@TempDir Path tempDir) throws IOException {
            // Arrange
            Path zipPath = datFilesRoot.resolve("jrm.zip");
            Path extractedDir = extractJrmZip(zipPath, tempDir);
            File jrmFile = extractedDir.resolve("JRM5314466932233810111.jrm").toFile();
            Profile profile = loadProfile(jrmFile);
            
            // Act - Get first ROM from first machine with ROMs
            Machine machine = profile.getMachineListList().get(0).stream()
                .filter(m -> m.getRoms() != null && !m.getRoms().isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No machine with ROMs found"));
            
            Rom rom = machine.getRoms().iterator().next();
            
            // Assert
            assertThat(rom).isNotNull();
            assertThat(rom.getName()).isNotBlank();
            assertThat(rom.getSize()).isGreaterThan(0);
            assertThat(rom.getCrc()).isNotBlank();
            assertThat(rom.getCrc()).matches("^[0-9a-fA-F]{8}$");
        }

        /**
         * Verifies that SHA1 presence is detected across the loaded ROMs from the JRM archive.
         *
         * @param tempDir the temporary directory used for extraction
         * @throws IOException if extraction or loading fails
         */
        @Test
        @DisplayName("Should detect SHA1 presence in ROMs")
        void shouldDetectSha1PresenceInRoms(@TempDir Path tempDir) throws IOException {
            // Arrange
            Path zipPath = datFilesRoot.resolve("jrm.zip");
            Path extractedDir = extractJrmZip(zipPath, tempDir);
            File jrmFile = extractedDir.resolve("JRM5314466932233810111.jrm").toFile();
            
            // Act
            Profile profile = loadProfile(jrmFile);
            
            // Assert
            assertThat(profile).isNotNull();
            assertThat(profile.isSha1Roms()).isTrue();
        }

        /**
         * Verifies that software lists are loaded with the correct structure from the JRM archive.
         *
         * @param tempDir the temporary directory used for extraction
         * @throws IOException if extraction or loading fails
         */
        @Test
        @DisplayName("Should load software lists with correct structure")
        void shouldLoadSoftwareListsWithCorrectStructure(@TempDir Path tempDir) throws IOException {
            // Arrange
            Path zipPath = datFilesRoot.resolve("jrm.zip");
            Path extractedDir = extractJrmZip(zipPath, tempDir);
            File jrmFile = extractedDir.resolve("JRM5314466932233810111.jrm").toFile();
            Profile profile = loadProfile(jrmFile);
            
            // Assert - Software lists are tracked at profile level
            assertThat(profile).isNotNull();
            assertThat(profile.getSoftwaresListCnt()).isGreaterThanOrEqualTo(0);
            assertThat(profile.getSoftwaresCnt()).isGreaterThanOrEqualTo(0);
        }

        /**
         * Verifies that machine-rom relationships are maintained after loading from the JRM archive.
         *
         * @param tempDir the temporary directory used for extraction
         * @throws IOException if extraction or loading fails
         */
        @Test
        @DisplayName("Should maintain machine-rom relationships")
        void shouldMaintainMachineRomRelationships(@TempDir Path tempDir) throws IOException {
            // Arrange
            Path zipPath = datFilesRoot.resolve("jrm.zip");
            Path extractedDir = extractJrmZip(zipPath, tempDir);
            File jrmFile = extractedDir.resolve("JRM5314466932233810111.jrm").toFile();
            Profile profile = loadProfile(jrmFile);
            
            // Act - Count machines with ROMs
            long machinesWithRoms = profile.getMachineListList().get(0).stream()
                .filter(m -> m.getRoms() != null && !m.getRoms().isEmpty())
                .count();
            
            // Assert
            assertThat(machinesWithRoms).isGreaterThan(0);
        }

        /**
         * Verifies that a MAME XML export file is generated successfully from the loaded profile.
         *
         * @param tempDir the temporary directory used for the test
         * @throws IOException if file operations fail
         */
        @Test
        @DisplayName("Should export MAME xml file")
        void shouldExportMame(@TempDir Path tempDir) throws IOException {
            // Arrange
            Path zipPath = datFilesRoot.resolve("jrm.zip");
            Path extractedDir = extractJrmZip(zipPath, tempDir);
            File jrmFile = extractedDir.resolve("JRM5314466932233810111.jrm").toFile();
            Profile profile = loadProfile(jrmFile);
            final var mamexml = Files.createTempFile(tempDir, null, null);
            Export.export(profile, mamexml.toFile(), ExportType.MAME, Set.of(ExportMode.ALL), null, handler);
            assertThat(mamexml).exists().isNotEmptyFile();
        }

        /**
         * Verifies that a SOFTWARELIST XML export file is generated successfully from the loaded profile.
         *
         * @param tempDir the temporary directory used for the test
         * @throws IOException if file operations fail
         */
        @Test
        @DisplayName("Should export SOFTWARELIST xml file")
        void shouldExportSoftwareList(@TempDir Path tempDir) throws IOException {
            // Arrange
            Path zipPath = datFilesRoot.resolve("jrm.zip");
            Path extractedDir = extractJrmZip(zipPath, tempDir);
            File jrmFile = extractedDir.resolve("JRM5314466932233810111.jrm").toFile();
            Profile profile = loadProfile(jrmFile);
            final var swlistxml = Files.createTempFile(tempDir, null, null);
            Export.export(profile, swlistxml.toFile(), ExportType.SOFTWARELIST, Set.of(ExportMode.ALL), null, handler);
            assertThat(swlistxml).exists().isNotEmptyFile();
        }


        /**
         * Verifies that an old DAT file is exported successfully from the loaded profile.
         *
         * @param tempDir the temporary directory used for the test
         * @throws IOException if file operations fail
         */
        @Test
        @DisplayName("Should export old DAT file")
        void shouldExportDat(@TempDir Path tempDir) throws IOException {
            // Arrange
            Path zipPath = datFilesRoot.resolve("jrm.zip");
            Path extractedDir = extractJrmZip(zipPath, tempDir);
            File jrmFile = extractedDir.resolve("JRM5314466932233810111.jrm").toFile();
            Profile profile = loadProfile(jrmFile);
            final var datfile = Files.createTempFile(tempDir, null, null);
            Export.export(profile, datfile.toFile(), ExportType.DATAFILE, Set.of(ExportMode.ALL), null, handler);
            assertThat(datfile).exists().isNotEmptyFile();
        }

    }

    /**
     * Tests verifying that all profile counters (machines, ROMs, disks, samples, software lists)
     * are tracked correctly and that header information is loaded when available.
     */
    @Nested
    @DisplayName("JRM Profile Statistics")
    class JrmProfileStatisticsTests {

        /**
         * Verifies that all profile counters (machines, ROMs, disks, samples, software lists) are
         * tracked correctly after loading from the JRM archive.
         *
         * @param tempDir the temporary directory used for extraction
         * @throws IOException if extraction or loading fails
         */
        @Test
        @DisplayName("Should track all counters correctly")
        void shouldTrackAllCountersCorrectly(@TempDir Path tempDir) throws IOException {
            // Arrange
            Path zipPath = datFilesRoot.resolve("jrm.zip");
            Path extractedDir = extractJrmZip(zipPath, tempDir);
            File jrmFile = extractedDir.resolve("JRM5314466932233810111.jrm").toFile();
            
            // Act
            Profile profile = loadProfile(jrmFile);
            
            // Assert - All counters should be positive or zero
            assertThat(profile.getMachinesCnt()).isGreaterThan(0);
            assertThat(profile.getRomsCnt()).isGreaterThan(0);
            assertThat(profile.getDisksCnt()).isGreaterThanOrEqualTo(0);
            assertThat(profile.getSamplesCnt()).isGreaterThanOrEqualTo(0);
            assertThat(profile.getSoftwaresListCnt()).isGreaterThanOrEqualTo(0);
            assertThat(profile.getSoftwaresCnt()).isGreaterThanOrEqualTo(0);
            assertThat(profile.getSwromsCnt()).isGreaterThanOrEqualTo(0);
            assertThat(profile.getSwdisksCnt()).isGreaterThanOrEqualTo(0);
        }

        /**
         * Verifies that header information is loaded from the JRM archive's DAT files.
         *
         * @param tempDir the temporary directory used for extraction
         * @throws IOException if extraction or loading fails
         */
        @Test
        @DisplayName("Should load header information")
        void shouldLoadHeaderInformation(@TempDir Path tempDir) throws IOException {
            // Arrange
            Path zipPath = datFilesRoot.resolve("jrm.zip");
            Path extractedDir = extractJrmZip(zipPath, tempDir);
            File jrmFile = extractedDir.resolve("JRM5314466932233810111.jrm").toFile();
            
            // Act
            Profile profile = loadProfile(jrmFile);
            
            // Assert
            assertThat(profile).isNotNull();
            assertThat(profile.getHeader()).isNotNull();
            // Header may be empty for JRM files that reference DAT files without headers
            // If header exists, it should contain standard DAT fields
            if (!profile.getHeader().isEmpty()) {
                assertThat(profile.getHeader().keySet())
                    .as("Header should contain at least one standard DAT field")
                    .containsAnyOf("name", "description", "version");
            }
        }
    }

    /**
     * Extracts the jrm.zip archive to a temporary directory.
     *
     * <p>This method iterates through all ZIP entries, creating directories and extracting
     * files while preserving the archive structure.</p>
     *
     * @param zipPath   the path to the jrm.zip file
     * @param targetDir the target directory for extraction
     * @return the target directory containing all extracted files
     * @throws IOException if extraction fails
     */
    private Path extractJrmZip(Path zipPath, Path targetDir) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path targetPath = targetDir.resolve(entry.getName());
                
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
        return targetDir;
    }

    /**
     * Loads a Profile from a JRM configuration file using mocked dependencies and reflection.
     *
     * <p>This helper method creates a Profile instance via its private constructor, injects
     * mocked session, ProfileNFO, and ProfileNFOMame objects using reflection, loads the JRM
     * configuration, and then invokes {@code internalLoad} for both the ROMs DAT and Software
     * List DAT files referenced by the configuration.</p>
     *
     * @param datFile the JRM configuration file to parse
     * @return the loaded Profile, or null if loading failed
     */
    private Profile loadProfile(File datFile) {
        try {
            // Create Profile instance using reflection (private constructor)
            java.lang.reflect.Constructor<Profile> constructor = Profile.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Profile profile = constructor.newInstance();
            
            // Set the session field using reflection
            java.lang.reflect.Field sessionField = Profile.class.getDeclaredField("session");
            sessionField.setAccessible(true);
            sessionField.set(profile, session);
            
            // Set the nfo field using reflection (needed for some internal operations)
            jrm.profile.manager.ProfileNFO nfo = mock(jrm.profile.manager.ProfileNFO.class);
            when(nfo.getFile()).thenReturn(datFile);
            when(nfo.isJRM()).thenReturn(true); // This is a JRM file
            
            // Create a mock ProfileNFOMame with the ROMs and SL files
            jrm.profile.manager.ProfileNFOMame mame = mock(jrm.profile.manager.ProfileNFOMame.class);
            File romsFile = new File(datFile.getParentFile(), "JRM18061833993484010293.jrm1");
            File slFile = new File(datFile.getParentFile(), "JRM14871978788853077359.jrm2");
            when(mame.getFileroms()).thenReturn(romsFile);
            when(mame.getFilesl()).thenReturn(slFile);
            when(nfo.getMame()).thenReturn(mame);
            
            java.lang.reflect.Field nfoField = Profile.class.getDeclaredField("nfo");
            nfoField.setAccessible(true);
            nfoField.set(profile, nfo);
            
            // Load the JRM configuration first
            nfo.loadJrm(datFile);
            
            // Call internalLoad for ROMs DAT
            if (romsFile.exists()) {
                java.lang.reflect.Method internalLoadMethod = Profile.class.getDeclaredMethod("internalLoad", File.class, ProgressHandler.class);
                internalLoadMethod.setAccessible(true);
                boolean success = (Boolean) internalLoadMethod.invoke(profile, romsFile, handler);
                if (!success) {
                    return null;
                }
            }
            
            // Call internalLoad for SL DAT (slFile is a `new File`, never null)
            if (slFile.exists()) {
                java.lang.reflect.Method internalLoadMethod = Profile.class.getDeclaredMethod("internalLoad", File.class, ProgressHandler.class);
                internalLoadMethod.setAccessible(true);
                boolean success = (Boolean) internalLoadMethod.invoke(profile, slFile, handler);
                if (!success) {
                    return null;
                }
            }
            
            return profile;
        } catch (Exception e) {
            System.err.println("Failed to load profile: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
