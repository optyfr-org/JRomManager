package jrm.profile.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("MAME launch argument sanitization")
class MameLaunchTest {

    private static final byte[] PE_MAGIC = { 'M', 'Z', 0x00, 0x00 };

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("machine argv uses validated names and dest dirs")
    void machineArgsAreSafe() throws IOException {
        final File exe = writeMame();
        final var dest = tempDir.resolve("roms").toAbsolutePath().normalize().toString();

        final var args = MameLaunch.machine(exe, "pacman", exe.getParent(), List.of(dest));

        assertThat(args).containsExactly(
                exe.getAbsolutePath(),
                "pacman",
                "-homepath",
                exe.getParent(),
                "-rompath",
                dest);
    }

    @Test
    @DisplayName("software argv inserts a single validated device option")
    void softwareArgsAreSafe() throws IOException {
        final File exe = writeMame();
        final var dest = tempDir.resolve("sw").toAbsolutePath().normalize().toString();

        final var args = MameLaunch.software(exe, "a2600", "cart", "pacman", exe.getParent(), List.of(dest));

        assertThat(args).containsExactly(
                exe.getAbsolutePath(),
                "a2600",
                "-cart",
                "pacman",
                "-homepath",
                exe.getParent(),
                "-rompath",
                dest);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "-window", "-str", "pac man", "pacman;calc", "name\nflag", "\"quoted\"" })
    @DisplayName("flag-like or token-splitting names are rejected")
    void rejectsUnsafeShortNames(final String name) {
        assertThatThrownBy(() -> MameLaunch.requireShortName(name, "machine name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid machine name");
    }

    @ParameterizedTest
    @ValueSource(strings = { "pacman", "1942", "mess64", "a2600", "pacman_f" })
    @DisplayName("real MAME short names are accepted")
    void acceptsMameShortNames(final String name) {
        assertThat(MameLaunch.requireShortName(name, "machine name")).isEqualTo(name);
    }

    @Test
    @DisplayName("dest dirs cannot inject extra rompath or flag tokens")
    void rejectsPoisonedRomPaths() {
        assertThat(MameLaunch.sanitizeRomPath(null, "/roms;-window")).isNull();
        assertThat(MameLaunch.sanitizeRomPath(null, "-cheatpath")).isNull();
        assertThat(MameLaunch.sanitizeRomPath(null, "C:\\roms;calc.exe")).isNull();
        assertThat(MameLaunch.sanitizeRomPath(null, "path\0evil")).isNull();
        assertThat(MameLaunch.sanitizeRomPath(null, "")).isNull();
        assertThat(MameLaunch.sanitizeRomPath(null, null)).isNull();
    }

    @Test
    @DisplayName("plain dest dirs survive sanitization as absolute paths")
    void acceptsPlainRomPaths() {
        final var dest = tempDir.resolve("roms").toAbsolutePath().normalize().toString();
        assertThat(MameLaunch.sanitizeRomPath(null, dest)).isEqualTo(dest);
        assertThat(MameLaunch.sanitizeRomPaths(null, List.of(dest, "", "/roms;-x"))).containsExactly(dest);
    }

    @Test
    @DisplayName("multiple dest dirs are joined with a single rompath value")
    void joinsRomPathsWithoutSplittingArgv() throws IOException {
        final File exe = writeMame();
        final var roms = tempDir.resolve("roms").toAbsolutePath().normalize().toString();
        final var disks = tempDir.resolve("disks").toAbsolutePath().normalize().toString();

        final var args = MameLaunch.machine(exe, "pacman", exe.getParent(), List.of(roms, disks));

        assertThat(args.get(args.size() - 2)).isEqualTo("-rompath");
        assertThat(args.get(args.size() - 1)).isEqualTo(roms + ";" + disks);
        assertThat(args).hasSize(6);
    }

    @Test
    @DisplayName("non-launchable binaries cannot be turned into argv")
    void rejectsNonLaunchableExecutable() throws IOException {
        final File script = tempDir.resolve("mame.bat").toFile();
        Files.writeString(script.toPath(), "echo pwned");
        script.setExecutable(true, false);

        final String homePath = script.getParent();
        final List<String> destDirs = List.of();
        assertThatThrownBy(() -> MameLaunch.machine(script, "pacman", homePath, destDirs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MAME executable");
    }

    private File writeMame() throws IOException {
        final File file = tempDir.resolve("mame.exe").toFile();
        Files.write(file.toPath(), PE_MAGIC);
        file.setExecutable(true, false);
        return file;
    }
}
