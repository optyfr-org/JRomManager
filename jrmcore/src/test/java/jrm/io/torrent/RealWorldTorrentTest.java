package jrm.io.torrent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests using real-world torrent files from various Linux distributions.
 * These tests validate parsing of actual production torrent files with diverse
 * configurations (different piece sizes, creators, trackers, and file sizes).
 *
 * <p>Tested torrents:</p>
 * <ul>
 *   <li>Ubuntu 24.04.3 Desktop ISO (Ubuntu official, 256KB pieces, mktorrent)</li>
 *   <li>Ubuntu 24.04.3 Live Server ISO (Ubuntu official, 256KB pieces, mktorrent)</li>
 *   <li>Debian 12.12.0 Live Standard ISO (Debian official, 256KB pieces, mktorrent)</li>
 *   <li>Kubuntu 24.04.3 Desktop ISO (Ubuntu flavor, 256KB pieces, mktorrent)</li>
 *   <li>Lubuntu 24.04.3 Desktop ISO (Ubuntu flavor, 256KB pieces, mktorrent)</li>
 *   <li>openSUSE Leap 15.6 DVD ISO (openSUSE official, 4MB pieces, MirrorCache)</li>
 *   <li>Arch Linux 2026.04/05/06 (Arch Linux official, 512KB pieces, mktorrent, no announce)</li>
 *   <li>Linux Mint 22/22.1/22.2/22.3 Cinnamon (Linux Mint official, 2MB pieces, Transmission)</li>
 *   <li>Raspberry Pi OS arm64 (Raspberry Pi official, 1MB pieces, Transmission)</li>
 * </ul>
 *
 * @author optyfr
 * @see TorrentParser
 * @see TorrentParserTest
 */
class RealWorldTorrentTest {

    /** Base path to the directory containing real-world torrent fixture files. */
    private static final String TORRENTS_DIR = "src/test/resources/torrents/";

    private static final Pattern ANNOUNCE_URL = Pattern.compile("^(https?|udp)://.*");

    /** All available torrent filenames for parametrized-style tests. */
    private static final String[] ALL_TORRENTS = {
        "ubuntu-24.04.3-desktop-amd64.iso.torrent",
        "ubuntu-24.04.3-live-server-amd64.iso.torrent",
        "debian-live-12.12.0-amd64-standard.iso.torrent",
        "kubuntu-24.04.3.torrent",
        "lubuntu-24.04.3.torrent",
        "opensuse-leap-15.6.torrent",
        "archlinux-2026.04.torrent",
        "archlinux-2026.05.torrent",
        "archlinux-2026.06.torrent",
        "linuxmint-22-cinnamon.torrent",
        "linuxmint-22.1-cinnamon.torrent",
        "linuxmint-22.2-cinnamon.torrent",
        "linuxmint-22.3-cinnamon.torrent",
        "raspios-arm64-latest.torrent"
    };

    /**
     * Verifies that all required torrent fixture files exist before running tests.
     * This ensures that test failures are due to parsing issues, not missing files.
     */
    @BeforeAll
    static void verifyTorrentFilesExist() {
        for (String filename : ALL_TORRENTS) {
            File file = new File(TORRENTS_DIR + filename);
            assertThat(file)
                .as("Torrent file must exist: " + file.getAbsolutePath())
                .exists();
        }
    }

    /**
     * Loads and parses a torrent file from the fixtures directory.
     *
     * @param filename the torrent filename (relative to {@link #TORRENTS_DIR})
     * @return the parsed {@link Torrent} object
     * @throws TorrentException if torrent parsing fails
     * @throws IOException      if the file cannot be read
     */
    private Torrent loadTorrent(String filename) throws TorrentException, IOException {
        File file = new File(TORRENTS_DIR + filename);
        return TorrentParser.parseTorrent(file);
    }

    // ========================================================================
    // Individual torrent tests
    // ========================================================================

    /**
     * Tests for Ubuntu 24.04.3 Desktop ISO torrent, verifying all metadata fields
     * including announce URLs, announce-list with IPv6 tracker, piece count, and info hash.
     */
    @Nested
    @DisplayName("Ubuntu Desktop 24.04.3")
    class UbuntuDesktopTests {

        /**
         * Verifies that Ubuntu Desktop 24.04.3 torrent metadata is parsed correctly, including
         * announce URLs, announce-list with IPv6 tracker, piece count, and info hash.
         *
         * @throws TorrentException if torrent parsing fails
         * @throws IOException      if the file cannot be read
         */
        @Test
        @DisplayName("Should parse all metadata correctly")
        void parseAllMetadata() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("ubuntu-24.04.3-desktop-amd64.iso.torrent");

            assertThat(torrent.getName()).isEqualTo("ubuntu-24.04.3-desktop-amd64.iso");
            assertThat(torrent.getAnnounce()).isEqualTo("https://torrent.ubuntu.com/announce");
            assertThat(torrent.getComment()).isEqualTo("Ubuntu CD releases.ubuntu.com");
            assertThat(torrent.getCreatedBy()).isEqualTo("mktorrent 1.1");
            assertThat(torrent.getCreationDate()).isNotNull();

            assertThat(torrent.getPieceLength()).isEqualTo(262144L);
            assertThat(torrent.getTotalSize()).isEqualTo(6345887744L);
            assertThat(torrent.isSingleFileTorrent()).isTrue();

            assertThat(torrent.getInfoHash()).isEqualTo("d160b8d8ea35a5b4e52837468fc8f03d55cef1f7");
            assertThat(torrent.getPieces()).hasSize(24208);

            assertThat(torrent.getAnnounceList()).hasSize(2);
            assertThat(torrent.getAnnounceList()).containsExactly(
                "https://torrent.ubuntu.com/announce",
                "https://ipv6.torrent.ubuntu.com/announce"
            );
        }
    }

    /**
     * Tests for Ubuntu 24.04.3 Live Server ISO torrent, verifying server-specific
     * metadata and smaller file size compared to Desktop ISO.
     */
    @Nested
    @DisplayName("Ubuntu Server 24.04.3")
    class UbuntuServerTests {

        /**
         * Verifies that Ubuntu Server 24.04.3 torrent metadata is parsed correctly, including
         * server-specific metadata and smaller file size compared to Desktop ISO.
         *
         * @throws TorrentException if torrent parsing fails
         * @throws IOException      if the file cannot be read
         */
        @Test
        @DisplayName("Should parse all metadata correctly")
        void parseAllMetadata() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("ubuntu-24.04.3-live-server-amd64.iso.torrent");

            assertThat(torrent.getName()).isEqualTo("ubuntu-24.04.3-live-server-amd64.iso");
            assertThat(torrent.getAnnounce()).isEqualTo("https://torrent.ubuntu.com/announce");
            assertThat(torrent.getComment()).isEqualTo("Ubuntu CD releases.ubuntu.com");
            assertThat(torrent.getCreatedBy()).isEqualTo("mktorrent 1.1");
            assertThat(torrent.getCreationDate()).isNotNull();

            assertThat(torrent.getPieceLength()).isEqualTo(262144L);
            assertThat(torrent.getTotalSize()).isEqualTo(3303444480L);
            assertThat(torrent.isSingleFileTorrent()).isTrue();

            assertThat(torrent.getInfoHash()).isEqualTo("a1dfefec1a9dd7fa8a041ebeeea271db55126d2f");
            assertThat(torrent.getPieces()).hasSize(12602);

            assertThat(torrent.getAnnounceList()).hasSize(2);
            assertThat(torrent.getAnnounceList()).containsExactly(
                "https://torrent.ubuntu.com/announce",
                "https://ipv6.torrent.ubuntu.com/announce"
            );
        }
    }

    /**
     * Tests for Debian 12.12.0 Live Standard ISO torrent, verifying Debian-specific
     * tracker configuration and absence of announce-list.
     */
    @Nested
    @DisplayName("Debian 12.12.0 Live Standard")
    class DebianStandardTests {

        /**
         * Verifies that Debian 12.12.0 Live Standard torrent metadata is parsed correctly, including
         * Debian-specific tracker configuration and absence of announce-list.
         *
         * @throws TorrentException if torrent parsing fails
         * @throws IOException      if the file cannot be read
         */
        @Test
        @DisplayName("Should parse all metadata correctly")
        void parseAllMetadata() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("debian-live-12.12.0-amd64-standard.iso.torrent");

            assertThat(torrent.getName()).isEqualTo("debian-live-12.12.0-amd64-standard.iso");
            assertThat(torrent.getAnnounce()).isEqualTo("http://bttracker.debian.org:6969/announce");
            assertThat(torrent.getComment()).isEqualTo("Debian CD from cdimage.debian.org");
            assertThat(torrent.getCreatedBy()).isEqualTo("mktorrent 1.1");
            assertThat(torrent.getCreationDate()).isNotNull();

            assertThat(torrent.getPieceLength()).isEqualTo(262144L);
            assertThat(torrent.getTotalSize()).isEqualTo(1522253824L);
            assertThat(torrent.isSingleFileTorrent()).isTrue();

            assertThat(torrent.getInfoHash()).isEqualTo("1ddafb048094eb9d993d6a368b3653e963fec641");
            assertThat(torrent.getPieces()).hasSize(5807);

            // Debian torrent has no announce-list
            assertThat(torrent.getAnnounceList()).isEmpty();
        }
    }

    /**
     * Tests for Kubuntu 24.04.3 Desktop ISO torrent, verifying Ubuntu flavor-specific
     * metadata and absence of announce-list (unlike main Ubuntu).
     */
    @Nested
    @DisplayName("Kubuntu 24.04.3 Desktop")
    class KubuntuTests {

        /**
         * Verifies that Kubuntu 24.04.3 torrent metadata is parsed correctly, including
         * Ubuntu flavor-specific metadata and absence of announce-list.
         *
         * @throws TorrentException if torrent parsing fails
         * @throws IOException      if the file cannot be read
         */
        @Test
        @DisplayName("Should parse all metadata correctly")
        void parseAllMetadata() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("kubuntu-24.04.3.torrent");

            assertThat(torrent.getName()).isEqualTo("kubuntu-24.04.3-desktop-amd64.iso");
            assertThat(torrent.getAnnounce()).isEqualTo("https://torrent.ubuntu.com/announce");
            assertThat(torrent.getComment()).isEqualTo("Kubuntu CD cdimage.ubuntu.com");
            assertThat(torrent.getCreatedBy()).isEqualTo("mktorrent 1.1");
            assertThat(torrent.getCreationDate()).isNotNull();

            assertThat(torrent.getPieceLength()).isEqualTo(262144L);
            assertThat(torrent.getTotalSize()).isEqualTo(4560015360L);
            assertThat(torrent.isSingleFileTorrent()).isTrue();

            assertThat(torrent.getInfoHash()).isEqualTo("51fe0a3e0f53991d5e38aa4571368797f45e0e9e");
            assertThat(torrent.getPieces()).hasSize(17396);

            // Kubuntu has no announce-list (unlike main Ubuntu)
            assertThat(torrent.getAnnounceList()).isEmpty();
        }
    }

    /**
     * Tests for Lubuntu 24.04.3 Desktop ISO torrent, verifying another Ubuntu flavor
     * with lightweight desktop environment.
     */
    @Nested
    @DisplayName("Lubuntu 24.04.3 Desktop")
    class LubuntuTests {

        /**
         * Verifies that Lubuntu 24.04.3 torrent metadata is parsed correctly, including
         * another Ubuntu flavor with lightweight desktop environment.
         *
         * @throws TorrentException if torrent parsing fails
         * @throws IOException      if the file cannot be read
         */
        @Test
        @DisplayName("Should parse all metadata correctly")
        void parseAllMetadata() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("lubuntu-24.04.3.torrent");

            assertThat(torrent.getName()).isEqualTo("lubuntu-24.04.3-desktop-amd64.iso");
            assertThat(torrent.getAnnounce()).isEqualTo("https://torrent.ubuntu.com/announce");
            assertThat(torrent.getComment()).isEqualTo("Lubuntu CD cdimage.ubuntu.com");
            assertThat(torrent.getCreatedBy()).isEqualTo("mktorrent 1.1");
            assertThat(torrent.getCreationDate()).isNotNull();

            assertThat(torrent.getPieceLength()).isEqualTo(262144L);
            assertThat(torrent.getTotalSize()).isEqualTo(3388037120L);
            assertThat(torrent.isSingleFileTorrent()).isTrue();

            assertThat(torrent.getInfoHash()).isEqualTo("8fee4f29c900f23bb79beae5dbb1327ab0a13f4e");
            assertThat(torrent.getPieces()).hasSize(12925);

            // Lubuntu has no announce-list
            assertThat(torrent.getAnnounceList()).isEmpty();
        }
    }

    /**
     * Tests for openSUSE Leap 15.6 DVD ISO torrent, verifying use of large 4MB pieces
     * (unlike most distros using 256KB-2MB) and MirrorCache creator.
     */
    @Nested
    @DisplayName("openSUSE Leap 15.6 DVD")
    class OpenSUSETests {

        /**
         * Verifies that openSUSE Leap 15.6 torrent metadata is parsed correctly, including
         * large 4MB pieces and MirrorCache creator.
         *
         * @throws TorrentException if torrent parsing fails
         * @throws IOException      if the file cannot be read
         */
        @Test
        @DisplayName("Should parse all metadata correctly")
        void parseAllMetadata() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("opensuse-leap-15.6.torrent");

            assertThat(torrent.getName()).isEqualTo("openSUSE-Leap-15.6-DVD-x86_64-Build710.3-Media.iso");
            assertThat(torrent.getAnnounce()).isEqualTo("http://tracker.opensuse.org:6969/announce");
            assertThat(torrent.getComment()).isEqualTo("openSUSE-Leap-15.6-DVD-x86_64-Build710.3-Media.iso");
            assertThat(torrent.getCreatedBy()).isEqualTo("MirrorCache");
            assertThat(torrent.getCreationDate()).isNotNull();

            assertThat(torrent.getPieceLength()).isEqualTo(4194304L); // 4 MB pieces
            assertThat(torrent.getTotalSize()).isEqualTo(4631560192L);
            assertThat(torrent.isSingleFileTorrent()).isTrue();

            assertThat(torrent.getInfoHash()).isEqualTo("039983ade894b8f86d037f46da3c726d2c43a7e7");
            assertThat(torrent.getPieces()).hasSize(1105);

            // openSUSE has a single announce-list entry matching the announce URL
            assertThat(torrent.getAnnounceList()).hasSize(1);
            assertThat(torrent.getAnnounceList()).containsExactly("http://tracker.opensuse.org:6969/announce");
        }

        /**
         * Verifies that openSUSE Leap 15.6 uses 4MB piece size, which is larger than most other distributions.
         *
         * @throws TorrentException if torrent parsing fails
         * @throws IOException      if the file cannot be read
         */
        @Test
        @DisplayName("Should use 4MB piece size (different from other distros)")
        void usesLargePieceSize() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("opensuse-leap-15.6.torrent");
            assertThat(torrent.getPieceLength()).isEqualTo(4194304L);
            assertThat(torrent.getPieceLength()).isGreaterThan(1048576L); // larger than 1 MB
        }

        /**
         * Verifies that openSUSE Leap 15.6 has a comment equal to the filename.
         *
         * @throws TorrentException if torrent parsing fails
         * @throws IOException      if the file cannot be read
         */
        @Test
        @DisplayName("Should have comment equal to the filename")
        void commentMatchesName() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("opensuse-leap-15.6.torrent");
            assertThat(torrent.getComment()).isEqualTo(torrent.getName());
        }
    }

    /**
     * Tests for Arch Linux 2026.06 ISO torrent, verifying Arch-specific characteristics
     * including null announce URL (webseed-only distribution) and 512KB pieces.
     */
    @Nested
    @DisplayName("Arch Linux 2026.06")
    class ArchLinux202606Tests {

        /**
         * Verifies that Arch Linux 2026.06 torrent metadata is parsed correctly, including
         * null announce URL (webseed-only) and 512KB pieces.
         *
         * @throws TorrentException if torrent parsing fails
         * @throws IOException      if the file cannot be read
         */
        @Test
        @DisplayName("Should parse all metadata correctly")
        void parseAllMetadata() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("archlinux-2026.06.torrent");

            assertThat(torrent.getName()).isEqualTo("archlinux-2026.06.01-x86_64.iso");
            assertThat(torrent.getAnnounce()).isNull();
            assertThat(torrent.getComment()).isEqualTo("Arch Linux 2026.06.01 <https://archlinux.org>");
            assertThat(torrent.getCreatedBy()).isEqualTo("mktorrent 1.1");
            assertThat(torrent.getCreationDate()).isNotNull();

            assertThat(torrent.getPieceLength()).isEqualTo(524288L);
            assertThat(torrent.getTotalSize()).isEqualTo(1566638080L);
            assertThat(torrent.isSingleFileTorrent()).isTrue();

            assertThat(torrent.getInfoHash()).isEqualTo("777695049623a1cd052bd6b175b40e6540ce74ca");
            assertThat(torrent.getPieces()).hasSize(2989);

            assertThat(torrent.getAnnounceList()).isEmpty();
        }
    }

    /**
     * Tests for Arch Linux 2026.05 ISO torrent, verifying monthly release metadata
     * and consistency with other Arch releases.
     */
    @Nested
    @DisplayName("Arch Linux 2026.05")
    class ArchLinux202605Tests {

        /**
         * Verifies that Arch Linux 2026.05 torrent metadata is parsed correctly, including
         * monthly release metadata and consistency with other Arch releases.
         *
         * @throws TorrentException if torrent parsing fails
         * @throws IOException      if the file cannot be read
         */
        @Test
        @DisplayName("Should parse all metadata correctly")
        void parseAllMetadata() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("archlinux-2026.05.torrent");

            assertThat(torrent.getName()).isEqualTo("archlinux-2026.05.01-x86_64.iso");
            assertThat(torrent.getAnnounce()).isNull();
            assertThat(torrent.getComment()).isEqualTo("Arch Linux 2026.05.01 <https://archlinux.org>");
            assertThat(torrent.getCreatedBy()).isEqualTo("mktorrent 1.1");
            assertThat(torrent.getCreationDate()).isNotNull();

            assertThat(torrent.getPieceLength()).isEqualTo(524288L);
            assertThat(torrent.getTotalSize()).isEqualTo(1545814016L);
            assertThat(torrent.isSingleFileTorrent()).isTrue();

            assertThat(torrent.getInfoHash()).isEqualTo("e337a880c4d0f552bab5b437fe1208d26130ccc5");
            assertThat(torrent.getPieces()).hasSize(2949);

            assertThat(torrent.getAnnounceList()).isEmpty();
        }
    }

    /**
     * Tests for Arch Linux 2026.04 ISO torrent, verifying earlier monthly release
     * metadata and piece count differences.
     */
    @Nested
    @DisplayName("Arch Linux 2026.04")
    class ArchLinux202604Tests {

        /**
         * Verifies that Arch Linux 2026.04 torrent metadata is parsed correctly, including
         * earlier monthly release metadata and piece count differences.
         *
         * @throws TorrentException if torrent parsing fails
         * @throws IOException      if the file cannot be read
         */
        @Test
        @DisplayName("Should parse all metadata correctly")
        void parseAllMetadata() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("archlinux-2026.04.torrent");

            assertThat(torrent.getName()).isEqualTo("archlinux-2026.04.01-x86_64.iso");
            assertThat(torrent.getAnnounce()).isNull();
            assertThat(torrent.getComment()).isEqualTo("Arch Linux 2026.04.01 <https://archlinux.org>");
            assertThat(torrent.getCreatedBy()).isEqualTo("mktorrent 1.1");
            assertThat(torrent.getCreationDate()).isNotNull();

            assertThat(torrent.getPieceLength()).isEqualTo(524288L);
            assertThat(torrent.getTotalSize()).isEqualTo(1536851968L);
            assertThat(torrent.isSingleFileTorrent()).isTrue();

            assertThat(torrent.getInfoHash()).isEqualTo("157e0a57e1af0e1cfd46258ba6c62938c21b6ee8");
            assertThat(torrent.getPieces()).hasSize(2932);

            assertThat(torrent.getAnnounceList()).isEmpty();
        }
    }

    /**
     * Tests for Linux Mint 22.3 Cinnamon ISO torrent, verifying Transmission-created
     * torrents with 2MB pieces and opentrackr tracker.
     */
    @Nested
    @DisplayName("Linux Mint 22.3 Cinnamon")
    class LinuxMint223Tests {

        /**
         * Verifies that Linux Mint 22.3 Cinnamon torrent metadata is parsed correctly, including
         * Transmission-created format with 2MB pieces and opentrackr tracker.
         *
         * @throws TorrentException if torrent parsing fails
         * @throws IOException      if the file cannot be read
         */
        @Test
        @DisplayName("Should parse all metadata correctly")
        void parseAllMetadata() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("linuxmint-22.3-cinnamon.torrent");

            assertThat(torrent.getName()).isEqualTo("linuxmint-22.3-cinnamon-64bit.iso");
            assertThat(torrent.getAnnounce()).isEqualTo("udp://tracker.opentrackr.org:1337/announce");
            assertThat(torrent.getComment()).isNull();
            assertThat(torrent.getCreatedBy()).startsWith("Transmission/");
            assertThat(torrent.getCreationDate()).isNotNull();

            assertThat(torrent.getPieceLength()).isEqualTo(2097152L);
            assertThat(torrent.getTotalSize()).isEqualTo(3091660800L);
            assertThat(torrent.isSingleFileTorrent()).isTrue();

            assertThat(torrent.getInfoHash()).isEqualTo("2fee37827d0b6dc81372a7affcd1e3dcb4110e64");
            assertThat(torrent.getPieces()).hasSize(1475);

            assertThat(torrent.getAnnounceList()).isEmpty();
        }
    }

    /**
     * Tests for Linux Mint 22.2 Cinnamon ISO torrent, verifying point release
     * metadata and file size progression.
     */
    @Nested
    @DisplayName("Linux Mint 22.2 Cinnamon")
    class LinuxMint222Tests {

        /**
         * Verifies that Linux Mint 22.2 Cinnamon torrent metadata is parsed correctly, including
         * point release metadata and file size progression.
         *
         * @throws TorrentException if torrent parsing fails
         * @throws IOException      if the file cannot be read
         */
        @Test
        @DisplayName("Should parse all metadata correctly")
        void parseAllMetadata() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("linuxmint-22.2-cinnamon.torrent");

            assertThat(torrent.getName()).isEqualTo("linuxmint-22.2-cinnamon-64bit.iso");
            assertThat(torrent.getAnnounce()).isEqualTo("udp://tracker.opentrackr.org:1337/announce");
            assertThat(torrent.getComment()).isNull();
            assertThat(torrent.getCreatedBy()).startsWith("Transmission/");
            assertThat(torrent.getCreationDate()).isNotNull();

            assertThat(torrent.getPieceLength()).isEqualTo(2097152L);
            assertThat(torrent.getTotalSize()).isEqualTo(3055239168L);
            assertThat(torrent.isSingleFileTorrent()).isTrue();

            assertThat(torrent.getInfoHash()).isEqualTo("4a984ee35f1f09e95e50c224ab70b775a43e8e06");
            assertThat(torrent.getPieces()).hasSize(1457);

            assertThat(torrent.getAnnounceList()).isEmpty();
        }
    }

    /**
     * Tests for Linux Mint 22.1 Cinnamon ISO torrent, verifying earlier point release
     * metadata and smaller file size.
     */
    @Nested
    @DisplayName("Linux Mint 22.1 Cinnamon")
    class LinuxMint221Tests {

        /**
         * Verifies that Linux Mint 22.1 Cinnamon torrent metadata is parsed correctly, including
         * earlier point release metadata and smaller file size.
         *
         * @throws TorrentException if torrent parsing fails
         * @throws IOException      if the file cannot be read
         */
        @Test
        @DisplayName("Should parse all metadata correctly")
        void parseAllMetadata() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("linuxmint-22.1-cinnamon.torrent");

            assertThat(torrent.getName()).isEqualTo("linuxmint-22.1-cinnamon-64bit.iso");
            assertThat(torrent.getAnnounce()).isEqualTo("udp://tracker.opentrackr.org:1337/announce");
            assertThat(torrent.getComment()).isNull();
            assertThat(torrent.getCreatedBy()).startsWith("Transmission/");
            assertThat(torrent.getCreationDate()).isNotNull();

            assertThat(torrent.getPieceLength()).isEqualTo(2097152L);
            assertThat(torrent.getTotalSize()).isEqualTo(2980511744L);
            assertThat(torrent.isSingleFileTorrent()).isTrue();

            assertThat(torrent.getInfoHash()).isEqualTo("5d4d25e0e66647c7e289202d788075d6884ec002");
            assertThat(torrent.getPieces()).hasSize(1422);

            assertThat(torrent.getAnnounceList()).isEmpty();
        }
    }

    /**
     * Tests for Linux Mint 22 Cinnamon ISO torrent (initial major release),
     * verifying base release metadata and smallest file size in the 22.x series.
     */
    @Nested
    @DisplayName("Linux Mint 22 Cinnamon")
    class LinuxMint22Tests {

        /**
         * Verifies that Linux Mint 22 Cinnamon torrent metadata is parsed correctly, including
         * base release metadata and smallest file size in the 22.x series.
         *
         * @throws TorrentException if torrent parsing fails
         * @throws IOException      if the file cannot be read
         */
        @Test
        @DisplayName("Should parse all metadata correctly")
        void parseAllMetadata() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("linuxmint-22-cinnamon.torrent");

            assertThat(torrent.getName()).isEqualTo("linuxmint-22-cinnamon-64bit.iso");
            assertThat(torrent.getAnnounce()).isEqualTo("udp://tracker.opentrackr.org:1337/announce");
            assertThat(torrent.getComment()).isNull();
            assertThat(torrent.getCreatedBy()).startsWith("Transmission/");
            assertThat(torrent.getCreationDate()).isNotNull();

            assertThat(torrent.getPieceLength()).isEqualTo(2097152L);
            assertThat(torrent.getTotalSize()).isEqualTo(2907832320L);
            assertThat(torrent.isSingleFileTorrent()).isTrue();

            assertThat(torrent.getInfoHash()).isEqualTo("a9ae5333b345d9c66ed09e2f72eef639dec5ad1d");
            assertThat(torrent.getPieces()).hasSize(1387);

            assertThat(torrent.getAnnounceList()).isEmpty();
        }
    }

    /**
     * Tests for Raspberry Pi OS arm64 torrent, verifying unique characteristics including
     * compressed .img.xz file format (not ISO), 1MB pieces, null comment, and Transmission creator.
     */
    @Nested
    @DisplayName("Raspberry Pi OS arm64")
    class RaspberryPiOSTests {

        /**
         * Verifies that Raspberry Pi OS arm64 torrent metadata is parsed correctly, including
         * compressed .img.xz file format, 1MB pieces, null comment, and Transmission creator.
         *
         * @throws TorrentException if torrent parsing fails
         * @throws IOException      if the file cannot be read
         */
        @Test
        @DisplayName("Should parse all metadata correctly")
        void parseAllMetadata() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("raspios-arm64-latest.torrent");

            assertThat(torrent.getName()).isEqualTo("2026-06-18-raspios-trixie-arm64.img.xz");
            assertThat(torrent.getAnnounce()).isEqualTo("http://tracker.raspberrypi.org:6969/announce");
            assertThat(torrent.getComment()).isNull();
            assertThat(torrent.getCreatedBy()).startsWith("Transmission/");
            assertThat(torrent.getCreationDate()).isNotNull();

            assertThat(torrent.getPieceLength()).isEqualTo(1048576L); // 1 MB pieces
            assertThat(torrent.getTotalSize()).isEqualTo(1344722248L);
            assertThat(torrent.isSingleFileTorrent()).isTrue();

            assertThat(torrent.getInfoHash()).isEqualTo("36b6e370e7b6f545157be1b8d38050c983481f22");
            assertThat(torrent.getPieces()).hasSize(1283);

            // Raspberry Pi OS has no announce-list
            assertThat(torrent.getAnnounceList()).isEmpty();
        }

        /**
         * Verifies that Raspberry Pi OS torrent has null comment, unlike most other distros.
         *
         * @throws TorrentException if torrent parsing fails
         * @throws IOException      if the file cannot be read
         */
        @Test
        @DisplayName("Should have null comment (unlike other distros)")
        void hasNoComment() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("raspios-arm64-latest.torrent");
            assertThat(torrent.getComment()).isNull();
        }

        /**
         * Verifies that Raspberry Pi OS torrent was created by Transmission, not mktorrent.
         *
         * @throws TorrentException if torrent parsing fails
         * @throws IOException      if the file cannot be read
         */
        @Test
        @DisplayName("Should be created by Transmission (different from mktorrent)")
        void createdByTransmission() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("raspios-arm64-latest.torrent");
            assertThat(torrent.getCreatedBy()).startsWith("Transmission/");
        }

        /**
         * Verifies that Raspberry Pi OS torrent contains a compressed .img.xz file rather than an ISO.
         *
         * @throws TorrentException if torrent parsing fails
         * @throws IOException      if the file cannot be read
         */
        @Test
        @DisplayName("Should have a compressed image file (not an ISO)")
        void compressedImageFile() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("raspios-arm64-latest.torrent");
            assertThat(torrent.getName()).endsWith(".img.xz");
        }
    }

    // ========================================================================
    // Cross-torrent validation tests
    // ========================================================================

    /**
     * Cross-validation tests that verify consistency and correctness across all torrent
     * fixtures, ensuring info hashes, piece counts, sizes, and metadata are valid for all files.
     */
    @Nested
    @DisplayName("Cross-torrent validation")
    class CrossTorrentTests {

        /**
         * Verifies that all torrent fixtures have valid info hashes (40-character lowercase hex strings).
         *
         * @throws TorrentException if any torrent parsing fails
         * @throws IOException      if any file cannot be read
         */
        @Test
        @DisplayName("All torrents should have valid 40-char hex info hashes")
        void allTorrentsHaveValidInfoHashes() throws TorrentException, IOException {
            for (String filename : ALL_TORRENTS) {
                Torrent torrent = loadTorrent(filename);
                assertThat(torrent.getInfoHash())
                    .as("Info hash for " + filename)
                    .isNotNull()
                    .hasSize(40)
                    .matches("[0-9a-f]+");
            }
        }

        /**
         * Verifies that all torrents have piece counts consistent with the formula
         * {@code ceil(totalSize / pieceLength)}, and that all piece hashes are 40 hex characters.
         *
         * @throws TorrentException if any torrent parsing fails
         * @throws IOException      if any file cannot be read
         */
        @Test
        @DisplayName("All torrents should have consistent piece counts matching totalSize/pieceLength")
        void allTorrentsHaveConsistentPieceCounts() throws TorrentException, IOException {
            for (String filename : ALL_TORRENTS) {
                Torrent torrent = loadTorrent(filename);

                long expectedPieces = (long) Math.ceil((double) torrent.getTotalSize() / torrent.getPieceLength());

                assertThat(torrent.getPieces())
                    .as("Piece count for " + filename)
                    .hasSize((int) expectedPieces);

                assertThat(torrent.getPieces())
                    .as("Piece hashes for " + filename)
                    .allMatch(hash -> hash.length() == 40 && hash.matches("[0-9a-fA-F]+"));
            }
        }

        /**
         * Verifies that all torrent fixtures have non-null, non-empty name fields.
         *
         * @throws TorrentException if any torrent parsing fails
         * @throws IOException      if any file cannot be read
         */
        @Test
        @DisplayName("All torrents should have non-null names")
        void allTorrentsHaveNames() throws TorrentException, IOException {
            for (String filename : ALL_TORRENTS) {
                Torrent torrent = loadTorrent(filename);
                assertThat(torrent.getName())
                    .as("Name for " + filename)
                    .isNotNull()
                    .isNotEmpty();
            }
        }

        /**
         * Verifies that all torrents with trackers have non-null, non-empty announce URLs.
         * Torrents without trackers (e.g., Arch Linux with webseeds only) may have null announce.
         *
         * @throws TorrentException if any torrent parsing fails
         * @throws IOException      if any file cannot be read
         */
        @Test
        @DisplayName("All torrents with a tracker should have non-null announce URLs")
        void allTorrentsHaveAnnounce() throws TorrentException, IOException {
            for (String filename : ALL_TORRENTS) {
                Torrent torrent = loadTorrent(filename);
                if (torrent.getAnnounce() != null) {
                    assertThat(torrent.getAnnounce())
                        .as("Announce for " + filename)
                        .isNotEmpty();
                }
            }
        }

        /**
         * Verifies that all torrent fixtures have non-null creation date fields.
         *
         * @throws TorrentException if any torrent parsing fails
         * @throws IOException      if any file cannot be read
         */
        @Test
        @DisplayName("All torrents should have non-null creation dates")
        void allTorrentsHaveCreationDates() throws TorrentException, IOException {
            for (String filename : ALL_TORRENTS) {
                Torrent torrent = loadTorrent(filename);
                assertThat(torrent.getCreationDate())
                    .as("Creation date for " + filename)
                    .isNotNull();
            }
        }

        /**
         * Verifies that all torrent fixtures have positive total size values.
         *
         * @throws TorrentException if any torrent parsing fails
         * @throws IOException      if any file cannot be read
         */
        @Test
        @DisplayName("All torrents should have positive total sizes")
        void allTorrentsHavePositiveSizes() throws TorrentException, IOException {
            for (String filename : ALL_TORRENTS) {
                Torrent torrent = loadTorrent(filename);
                assertThat(torrent.getTotalSize())
                    .as("Total size for " + filename)
                    .isGreaterThan(0);
            }
        }

        /**
         * Verifies that all torrent fixtures have positive piece length values.
         *
         * @throws TorrentException if any torrent parsing fails
         * @throws IOException      if any file cannot be read
         */
        @Test
        @DisplayName("All torrents should have positive piece lengths")
        void allTorrentsHavePositivePieceLengths() throws TorrentException, IOException {
            for (String filename : ALL_TORRENTS) {
                Torrent torrent = loadTorrent(filename);
                assertThat(torrent.getPieceLength())
                    .as("Piece length for " + filename)
                    .isGreaterThan(0);
            }
        }

        /**
         * Verifies that all torrent fixtures have non-null, non-empty piece hash lists.
         *
         * @throws TorrentException if any torrent parsing fails
         * @throws IOException      if any file cannot be read
         */
        @Test
        @DisplayName("All torrents should have non-null piece hash lists")
        void allTorrentsHavePieceLists() throws TorrentException, IOException {
            for (String filename : ALL_TORRENTS) {
                Torrent torrent = loadTorrent(filename);
                assertThat(torrent.getPieces())
                    .as("Pieces list for " + filename)
                    .isNotNull()
                    .isNotEmpty();
            }
        }

        /**
         * Verifies that all torrent fixtures have non-null announce lists (which may be empty
         * for torrents without announce-list fields).
         *
         * @throws TorrentException if any torrent parsing fails
         * @throws IOException      if any file cannot be read
         */
        @Test
        @DisplayName("All torrents should have non-null announce list (possibly empty)")
        void allTorrentsHaveAnnounceList() throws TorrentException, IOException {
            for (String filename : ALL_TORRENTS) {
                Torrent torrent = loadTorrent(filename);
                assertThat(torrent.getAnnounceList())
                    .as("Announce list for " + filename)
                    .isNotNull();
            }
        }

        /**
         * Verifies that all ISO distribution torrents are correctly identified as single-file torrents.
         *
         * @throws TorrentException if any torrent parsing fails
         * @throws IOException      if any file cannot be read
         */
        @Test
        @DisplayName("All real-world ISO torrents should be single-file torrents")
        void allIsoTorrentsAreSingleFile() throws TorrentException, IOException {
            String[] singleFileTorrents = {
                "ubuntu-24.04.3-desktop-amd64.iso.torrent",
                "ubuntu-24.04.3-live-server-amd64.iso.torrent",
                "debian-live-12.12.0-amd64-standard.iso.torrent",
                "kubuntu-24.04.3.torrent",
                "lubuntu-24.04.3.torrent",
                "opensuse-leap-15.6.torrent",
                "raspios-arm64-latest.torrent"
            };
            for (String filename : singleFileTorrents) {
                Torrent torrent = loadTorrent(filename);
                assertThat(torrent.isSingleFileTorrent())
                    .as("Single file flag for " + filename)
                    .isTrue();
            }
        }

        /**
         * Verifies that all piece hashes across all torrents are exactly 40 hex characters (SHA-1).
         *
         * @throws TorrentException if any torrent parsing fails
         * @throws IOException      if any file cannot be read
         */
        @Test
        @DisplayName("All piece hashes should be exactly 40 hex characters")
        void allPieceHashesAreValidFormat() throws TorrentException, IOException {
            for (String filename : ALL_TORRENTS) {
                Torrent torrent = loadTorrent(filename);
                assertThat(torrent.getPieces())
                    .as("Piece hashes format for " + filename)
                    .isNotEmpty()
                    .allSatisfy(hash -> {
                        assertThat(hash).hasSize(40);
                        assertThat(hash).matches("[0-9a-fA-F]+");
                    });
            }
        }

        /**
         * Verifies that info hashes are unique across all torrent fixtures, ensuring no duplicates.
         *
         * @throws TorrentException if any torrent parsing fails
         * @throws IOException      if any file cannot be read
         */
        @Test
        @DisplayName("Info hashes should be unique across all torrents")
        void infoHashesAreUnique() throws TorrentException, IOException {
            var infoHashes = new java.util.HashSet<String>();
            for (String filename : ALL_TORRENTS) {
                Torrent torrent = loadTorrent(filename);
                assertThat(infoHashes.add(torrent.getInfoHash()))
                    .as("Info hash should be unique for " + filename)
                    .isTrue();
            }
        }

        /**
         * Verifies that the raw pieces blob size matches the expected size
         * ({@code piecesList.size() * 20} bytes, since each SHA-1 hash is 20 bytes).
         *
         * @throws TorrentException if any torrent parsing fails
         * @throws IOException      if any file cannot be read
         */
        @Test
        @DisplayName("Pieces blob size should match pieces list count * 20")
        void piecesBlobMatchesPiecesList() throws TorrentException, IOException {
            for (String filename : ALL_TORRENTS) {
                Torrent torrent = loadTorrent(filename);
                assertThat(torrent.getPiecesBlob())
                    .as("Pieces blob size for " + filename)
                    .hasSize(torrent.getPieces().size() * 20);
            }
        }
    }

    // ========================================================================
    // Creator-specific tests
    // ========================================================================

    @Nested
    @DisplayName("Creator-specific behavior")
    class CreatorTests {

        @Test
        @DisplayName("mktorrent-created torrents should have 'mktorrent' in createdBy")
        void mktorrentTorrents() throws TorrentException, IOException {
            String[] mktorrentTorrents = {
                "ubuntu-24.04.3-desktop-amd64.iso.torrent",
                "ubuntu-24.04.3-live-server-amd64.iso.torrent",
                "debian-live-12.12.0-amd64-standard.iso.torrent",
                "kubuntu-24.04.3.torrent",
                "lubuntu-24.04.3.torrent"
            };
            for (String filename : mktorrentTorrents) {
                Torrent torrent = loadTorrent(filename);
                assertThat(torrent.getCreatedBy())
                    .as("CreatedBy for " + filename)
                    .containsIgnoringCase("mktorrent");
            }
        }

        @Test
        @DisplayName("openSUSE torrent should be created by MirrorCache")
        void mirrorCacheTorrent() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("opensuse-leap-15.6.torrent");
            assertThat(torrent.getCreatedBy()).isEqualTo("MirrorCache");
        }

        @Test
        @DisplayName("Raspberry Pi OS torrent should be created by Transmission")
        void transmissionTorrent() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("raspios-arm64-latest.torrent");
            assertThat(torrent.getCreatedBy()).startsWith("Transmission/");
        }
    }

    // ========================================================================
    // Piece size diversity tests
    // ========================================================================

    @Nested
    @DisplayName("Piece size diversity")
    class PieceSizeTests {

        @Test
        @DisplayName("Ubuntu-family torrents should use 256KB pieces")
        void ubuntuFamilyUses256KB() throws TorrentException, IOException {
            String[] ubuntuFamily = {
                "ubuntu-24.04.3-desktop-amd64.iso.torrent",
                "ubuntu-24.04.3-live-server-amd64.iso.torrent",
                "kubuntu-24.04.3.torrent",
                "lubuntu-24.04.3.torrent"
            };
            for (String filename : ubuntuFamily) {
                Torrent torrent = loadTorrent(filename);
                assertThat(torrent.getPieceLength())
                    .as("Piece length for " + filename)
                    .isEqualTo(262144L);
            }
        }

        @Test
        @DisplayName("Debian torrent should use 256KB pieces")
        void debianUses256KB() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("debian-live-12.12.0-amd64-standard.iso.torrent");
            assertThat(torrent.getPieceLength()).isEqualTo(262144L);
        }

        @Test
        @DisplayName("openSUSE torrent should use 4MB pieces")
        void opensuseUses4MB() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("opensuse-leap-15.6.torrent");
            assertThat(torrent.getPieceLength()).isEqualTo(4194304L);
        }

        @Test
        @DisplayName("Raspberry Pi OS torrent should use 1MB pieces")
        void raspberrypiUses1MB() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("raspios-arm64-latest.torrent");
            assertThat(torrent.getPieceLength()).isEqualTo(1048576L);
        }

        @Test
        @DisplayName("All piece sizes should be powers of 2")
        void allPieceSizesArePowersOf2() throws TorrentException, IOException {
            for (String filename : ALL_TORRENTS) {
                Torrent torrent = loadTorrent(filename);
                long pieceLength = torrent.getPieceLength();
                assertThat(pieceLength & (pieceLength - 1))
                    .as("Piece length should be power of 2 for " + filename)
                    .isZero();
            }
        }
    }

    // ========================================================================
    // Tracker / announce tests
    // ========================================================================

    @Nested
    @DisplayName("Tracker and announce behavior")
    class TrackerTests {

        @Test
        @DisplayName("Ubuntu official torrents should have IPv6 tracker in announce list")
        void ubuntuOfficialHasIPv6Tracker() throws TorrentException, IOException {
            Torrent desktop = loadTorrent("ubuntu-24.04.3-desktop-amd64.iso.torrent");
            Torrent server = loadTorrent("ubuntu-24.04.3-live-server-amd64.iso.torrent");

            assertThat(desktop.getAnnounceList()).contains("https://ipv6.torrent.ubuntu.com/announce");
            assertThat(server.getAnnounceList()).contains("https://ipv6.torrent.ubuntu.com/announce");
        }

        @Test
        @DisplayName("Ubuntu flavor torrents should NOT have announce-list")
        void ubuntuFlavorsHaveNoAnnounceList() throws TorrentException, IOException {
            Torrent kubuntu = loadTorrent("kubuntu-24.04.3.torrent");
            Torrent lubuntu = loadTorrent("lubuntu-24.04.3.torrent");

            assertThat(kubuntu.getAnnounceList()).isEmpty();
            assertThat(lubuntu.getAnnounceList()).isEmpty();
        }

        @Test
        @DisplayName("All announce URLs should be valid HTTP(S) or UDP URLs (trackerless torrents excluded)")
        void allAnnounceUrlsAreValidHttp() throws TorrentException, IOException {
            for (String filename : ALL_TORRENTS) {
                Torrent torrent = loadTorrent(filename);
                if (torrent.getAnnounce() != null) {
                    assertThat(torrent.getAnnounce())
                        .as("Announce URL for " + filename)
                        .matches("(https?|udp)://.*");
                }
            }
        }

        @Test
        @DisplayName("Announce list entries should all be valid HTTP(S) or UDP URLs when present")
        void announceListEntriesAreValidHttp() throws TorrentException, IOException {
            for (String filename : ALL_TORRENTS) {
                Torrent torrent = loadTorrent(filename);
                if (!torrent.getAnnounceList().isEmpty()) {
                    assertThat(torrent.getAnnounceList())
                        .as("Announce list entries for " + filename)
                        .allMatch(url -> ANNOUNCE_URL.matcher(url).matches());
                }
            }
        }

        @Test
        @DisplayName("Debian torrent should use bttracker.debian.org")
        void debianUsesDebianTracker() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("debian-live-12.12.0-amd64-standard.iso.torrent");
            assertThat(torrent.getAnnounce()).contains("debian.org");
        }

        @Test
        @DisplayName("openSUSE torrent should use tracker.opensuse.org")
        void opensuseUsesOpensuseTracker() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("opensuse-leap-15.6.torrent");
            assertThat(torrent.getAnnounce()).contains("opensuse.org");
        }

        @Test
        @DisplayName("Raspberry Pi OS torrent should use tracker.raspberrypi.org")
        void raspberrypiUsesRpiTracker() throws TorrentException, IOException {
            Torrent torrent = loadTorrent("raspios-arm64-latest.torrent");
            assertThat(torrent.getAnnounce()).contains("raspberrypi.org");
        }
    }

    // ========================================================================
    // Size and content tests
    // ========================================================================

    @Nested
    @DisplayName("File size and content characteristics")
    class SizeTests {

        @Test
        @DisplayName("All torrents should represent files larger than 1GB")
        void allTorrentsAreLargeFiles() throws TorrentException, IOException {
            long oneGB = 1024L * 1024 * 1024;
            for (String filename : ALL_TORRENTS) {
                Torrent torrent = loadTorrent(filename);
                assertThat(torrent.getTotalSize())
                    .as("Total size for " + filename)
                    .isGreaterThan(oneGB);
            }
        }

        @Test
        @DisplayName("Ubuntu desktop should be the largest torrent")
        void ubuntuDesktopIsLargest() throws TorrentException, IOException {
            Torrent desktop = loadTorrent("ubuntu-24.04.3-desktop-amd64.iso.torrent");
            assertThat(desktop.getTotalSize()).isEqualTo(6345887744L); // ~5.9 GB

            for (String filename : ALL_TORRENTS) {
                Torrent torrent = loadTorrent(filename);
                assertThat(desktop.getTotalSize())
                    .as("Ubuntu desktop should be >= " + filename)
                    .isGreaterThanOrEqualTo(torrent.getTotalSize());
            }
        }

        @Test
        @DisplayName("Raspberry Pi OS should be the smallest torrent")
        void raspberrypiIsSmallest() throws TorrentException, IOException {
            Torrent rpi = loadTorrent("raspios-arm64-latest.torrent");
            assertThat(rpi.getTotalSize()).isEqualTo(1344722248L); // ~1.25 GB

            for (String filename : ALL_TORRENTS) {
                Torrent torrent = loadTorrent(filename);
                assertThat(rpi.getTotalSize())
                    .as("Raspberry Pi OS should be <= " + filename)
                    .isLessThanOrEqualTo(torrent.getTotalSize());
            }
        }

        @Test
        @DisplayName("Most torrents should have ISO file names, except Raspberry Pi OS")
        void fileExtensions() throws TorrentException, IOException {
            // ISO torrents
            String[] isoTorrents = {
                "ubuntu-24.04.3-desktop-amd64.iso.torrent",
                "ubuntu-24.04.3-live-server-amd64.iso.torrent",
                "debian-live-12.12.0-amd64-standard.iso.torrent",
                "kubuntu-24.04.3.torrent",
                "lubuntu-24.04.3.torrent",
                "opensuse-leap-15.6.torrent"
            };
            for (String filename : isoTorrents) {
                Torrent torrent = loadTorrent(filename);
                assertThat(torrent.getName())
                    .as("Name for " + filename)
                    .endsWith(".iso");
            }

            // Raspberry Pi OS uses .img.xz
            Torrent rpi = loadTorrent("raspios-arm64-latest.torrent");
            assertThat(rpi.getName()).endsWith(".img.xz");
        }
    }

    // ========================================================================
    // Large multi-file torrent tests (1M files)
    // ========================================================================

    @Nested
    @DisplayName("Large multi-file torrent (1M files)")
    class LargeMultiFileTests {
        
        private static Torrent torrent;
        
        @BeforeAll
        static void loadTorrentOnce() throws TorrentException, IOException {
            torrent = TorrentParser.parseTorrent(new File("src/test/resources/torrents/large-multi-file.torrent"));
        }

        @Test
        @DisplayName("Should parse torrent with 1,000,000 files successfully")
        void parseMillionFiles() {
            assertThat(torrent.getName()).isEqualTo("large-multi-file-test");
            assertThat(torrent.isSingleFileTorrent()).isFalse();
            assertThat(torrent.getFileList()).hasSize(1_000_000);
        }

        @Test
        @DisplayName("Should calculate correct total size across all files")
        void totalSizeCalculation() {
            long expectedTotal = torrent.getFileList().stream()
                .mapToLong(TorrentFile::getFileLength)
                .sum();

            assertThat(torrent.getTotalSize()).isEqualTo(expectedTotal);
            assertThat(torrent.getTotalSize()).isEqualTo(11868160000L);
        }

        @Test
        @DisplayName("Should handle diverse file sizes correctly")
        void diverseFileSizes() {
            var fileSizes = torrent.getFileList().stream()
                .mapToLong(TorrentFile::getFileLength)
                .distinct()
                .sorted()
                .toArray();

            assertThat(fileSizes).hasSize(3).containsExactly(512L, 10240L, 1048576L);
        }

        @Test
        @DisplayName("Should parse file paths with multiple directory levels")
        void nestedDirectoryPaths() {
            assertThat(torrent.getFileList())
                .allMatch(file -> file.getFileDirs().size() == 3);
        }

        @Test
        @DisplayName("Should handle various file extensions")
        void variousFileExtensions() {
            var extensions = torrent.getFileList().stream()
                .map(file -> {
                    String path = file.getFileDirs().get(file.getFileDirs().size() - 1);
                    int dotIndex = path.lastIndexOf('.');
                    return dotIndex >= 0 ? path.substring(dotIndex) : "";
                })
                .distinct()
                .toList();

            assertThat(extensions).hasSize(8).contains(".txt", ".dat", ".bin", ".log", ".csv", ".json", ".xml", ".html");
        }

        @Test
        @DisplayName("Should organize files into category directories")
        void categoryDirectories() {
            var categories = torrent.getFileList().stream()
                .map(file -> file.getFileDirs().get(0))
                .distinct()
                .toList();

            assertThat(categories).hasSize(8).contains("documents", "images", "videos", "audio", "archives", "source", "data", "config");
        }

        @Test
        @DisplayName("Should have subcategories within each category")
        void subcategoryStructure() {
            var subcategories = torrent.getFileList().stream()
                .map(file -> file.getFileDirs().get(0) + "/" + file.getFileDirs().get(1))
                .distinct()
                .count();

            assertThat(subcategories).isEqualTo(800);
        }

        @Test
        @DisplayName("Should parse piece information correctly")
        void pieceParsing() {
            assertThat(torrent.getPieces()).hasSize(45274);
            assertThat(torrent.getPieceLength()).isEqualTo(262144L);
        }

        @Test
        @DisplayName("Should have valid piece hashes (40 hex chars each)")
        void validPieceHashes() {
            assertThat(torrent.getPieces())
                .allMatch(hash -> hash.length() == 40 && hash.matches("[0-9a-fA-F]+"));
        }

        @Test
        @DisplayName("Should preserve file ordering from original torrent")
        void fileOrdering() {
            var fileNumbers = torrent.getFileList().stream()
                .mapToLong(file -> {
                    String filename = file.getFileDirs().get(file.getFileDirs().size() - 1);
                    int start = filename.indexOf('_') + 1;
                    int end = filename.indexOf('.');
                    return Long.parseLong(filename.substring(start, end));
                })
                .toArray();

            for (int i = 1; i < fileNumbers.length; i++) {
                assertThat(fileNumbers[i])
                    .as("File %d should come after file %d", i, i - 1)
                    .isGreaterThan(fileNumbers[i - 1]);
            }
        }

        @Test
        @DisplayName("Should have a large torrent file on disk (>50MB)")
        void largeTorrentFile() {
            var torrentFile = new File("src/test/resources/torrents/large-multi-file.torrent");
            assertThat(torrentFile.length()).isGreaterThan(50_000_000L);
        }

        @Test
        @DisplayName("Should have piece count consistent with totalSize/pieceLength")
        void reasonablePieceCount() {
            long expectedPieces = (torrent.getTotalSize() + torrent.getPieceLength() - 1) / torrent.getPieceLength();
            assertThat((long) torrent.getPieces().size()).isEqualTo(expectedPieces);
        }

        @Test
        @DisplayName("Should parse metadata correctly")
        void metadataParsing() {
            assertThat(torrent.getAnnounce()).isEqualTo("http://tracker.example.com/announce");
            assertThat(torrent.getComment()).isEqualTo("Synthetic multi-file torrent for parser stress testing");
            assertThat(torrent.getCreatedBy()).isEqualTo("JRomManager Test Generator");
            assertThat(torrent.getCreationDate()).isNotNull();
        }

        @Test
        @DisplayName("Should have uniform path depth (all 3 components)")
        void uniformPathDepth() {
            var pathDepths = torrent.getFileList().stream()
                .mapToInt(file -> file.getFileDirs().size())
                .distinct()
                .toArray();

            assertThat(pathDepths).containsExactly(3);
        }

        @Test
        @DisplayName("Should not have duplicate file paths (all 1M unique)")
        void uniqueFilePaths() {
            var uniquePaths = torrent.getFileList().stream()
                .map(file -> String.join("/", file.getFileDirs()))
                .distinct()
                .count();

            assertThat(uniquePaths).isEqualTo(1_000_000L);
        }

        @Test
        @DisplayName("Should correctly distribute files across categories")
        void categoryDistribution() {
            var categoryCounts = torrent.getFileList().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    file -> file.getFileDirs().get(0),
                    java.util.stream.Collectors.counting()));

            assertThat(categoryCounts).hasSize(8);
            categoryCounts.values().forEach(count ->
                assertThat(count).isEqualTo(125_000L));
        }

        @Test
        @DisplayName("Should correctly distribute file sizes (1% large, 9% medium, 90% small)")
        void fileSizeDistribution() {
            long largeFiles = torrent.getFileList().stream()
                .filter(f -> f.getFileLength() == 1048576L)
                .count();
            long mediumFiles = torrent.getFileList().stream()
                .filter(f -> f.getFileLength() == 10240L)
                .count();
            long smallFiles = torrent.getFileList().stream()
                .filter(f -> f.getFileLength() == 512L)
                .count();

            assertThat(largeFiles).isEqualTo(10_000L);
            assertThat(mediumFiles).isEqualTo(90_000L);
            assertThat(smallFiles).isEqualTo(900_000L);
            assertThat(largeFiles + mediumFiles + smallFiles).isEqualTo(1_000_000L);
        }

        @Test
        @DisplayName("First file should be file_0000000.txt in documents/sub0")
        void firstFileDetails() {
            TorrentFile firstFile = torrent.getFileList().get(0);

            assertThat(firstFile.getFileDirs()).containsExactly("documents", "sub0", "file_0000000.txt");
            // file index 0: 0 % 100 == 0 → large file (1MB)
            assertThat(firstFile.getFileLength()).isEqualTo(1048576L);
        }

        @Test
        @DisplayName("Last file should be file_0999999.html in config/sub99")
        void lastFileDetails() {
            TorrentFile lastFile = torrent.getFileList().get(torrent.getFileList().size() - 1);

            assertThat(lastFile.getFileDirs()).containsExactly("config", "sub99", "file_0999999.html");
            // file index 999999: not divisible by 10 → small file (512B)
            assertThat(lastFile.getFileLength()).isEqualTo(512L);
        }
    }
}
