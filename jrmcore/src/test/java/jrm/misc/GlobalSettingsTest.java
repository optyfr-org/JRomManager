package jrm.misc;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link GlobalSettings} AppImage portable-home resolution.
 */
@DisplayName("GlobalSettings")
class GlobalSettingsTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("returns null when APPIMAGE is absent")
    void returnsNullWhenAppImageAbsent() {
        assertThat(GlobalSettings.appImagePortableHome(null, tempDir.toString())).isNull();
        assertThat(GlobalSettings.appImagePortableHome("", tempDir.toString())).isNull();
    }

    @Test
    @DisplayName("returns null when HOME is absent")
    void returnsNullWhenHomeAbsent() {
        final Path appImage = tempDir.resolve("JRomManager.AppImage");
        assertThat(GlobalSettings.appImagePortableHome(appImage.toString(), null)).isNull();
        assertThat(GlobalSettings.appImagePortableHome(appImage.toString(), "")).isNull();
    }

    @Test
    @DisplayName("resolves portable home when it matches <AppImage>.home and exists")
    void resolvesPortableHomeWhenActive() throws Exception {
        final Path appImage = tempDir.resolve("JRomManager.AppImage");
        final Path home = tempDir.resolve("JRomManager.AppImage.home");
        Files.createFile(appImage);
        Files.createDirectories(home);

        assertThat(GlobalSettings.appImagePortableHome(appImage.toString(), home.toString()))
                .isEqualTo(home.toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("returns null when HOME differs from <AppImage>.home")
    void returnsNullWhenHomeDoesNotMatch() throws Exception {
        final Path appImage = tempDir.resolve("JRomManager.AppImage");
        Files.createFile(appImage);

        assertThat(GlobalSettings.appImagePortableHome(appImage.toString(), tempDir.toString())).isNull();
    }

    @Test
    @DisplayName("returns null when the portable home directory does not exist")
    void returnsNullWhenPortableHomeMissing() throws Exception {
        final Path appImage = tempDir.resolve("JRomManager.AppImage");
        final Path home = tempDir.resolve("JRomManager.AppImage.home");
        Files.createFile(appImage);

        assertThat(GlobalSettings.appImagePortableHome(appImage.toString(), home.toString())).isNull();
    }
}
