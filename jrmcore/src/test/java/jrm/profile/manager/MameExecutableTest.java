package jrm.profile.manager;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("MAME executable launch checks")
class MameExecutableTest {

    private static final byte[] PE_MAGIC = { 'M', 'Z', 0x00, 0x00 };
    private static final byte[] ELF_MAGIC = { 0x7F, 'E', 'L', 'F' };
    private static final byte[] MACHO_MAGIC = { (byte) 0xCF, (byte) 0xFA, (byte) 0xED, (byte) 0xFE };
    private static final byte[] SHEBANG = { '#', '!', '/', 'b', 'i', 'n', '/', 's', 'h' };

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("null and missing files are not launchable")
    void rejectsNullAndMissing() {
        assertThat(MameExecutable.isLaunchable(null)).isFalse();
        assertThat(MameExecutable.isLaunchable(tempDir.resolve("missing-mame.exe").toFile())).isFalse();
    }

    @Test
    @DisplayName("directories are not launchable")
    void rejectsDirectories() throws IOException {
        final Path dir = Files.createDirectory(tempDir.resolve("mame"));
        assertThat(MameExecutable.isLaunchable(dir.toFile())).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "evil.bat", "evil.cmd", "evil.ps1", "evil.sh", "evil.py", "evil.jar", "mame.bat", "mess.cmd" })
    @DisplayName("scripts and archives are not launchable even with emulator names")
    void rejectsScriptExtensions(final String name) throws IOException {
        assertThat(MameExecutable.isLaunchable(writeExecutable(tempDir.resolve(name), PE_MAGIC))).isFalse();
    }

    @Test
    @DisplayName("shebang scripts are not launchable")
    void rejectsShebangScripts() throws IOException {
        assertThat(MameExecutable.isLaunchable(writeExecutable(tempDir.resolve("mame"), SHEBANG))).isFalse();
        assertThat(MameExecutable.isLaunchable(writeExecutable(tempDir.resolve("mess64"), SHEBANG))).isFalse();
    }

    @Test
    @DisplayName("native binaries without mame/mess names are rejected")
    void rejectsUnrelatedNativeBinaries() throws IOException {
        assertThat(MameExecutable.isLaunchable(writeExecutable(tempDir.resolve("cmd.exe"), PE_MAGIC))).isFalse();
        assertThat(MameExecutable.isLaunchable(writeExecutable(tempDir.resolve("notepad"), ELF_MAGIC))).isFalse();
    }

    @Test
    @DisplayName("MAME and MESS native binaries are launchable")
    void acceptsMameAndMessNativeBinaries() throws IOException {
        assertThat(MameExecutable.isLaunchable(writeExecutable(tempDir.resolve("mame.exe"), PE_MAGIC))).isTrue();
        assertThat(MameExecutable.isLaunchable(writeExecutable(tempDir.resolve("mess64"), ELF_MAGIC))).isTrue();
        assertThat(MameExecutable.isLaunchable(writeExecutable(tempDir.resolve("sdlmame"), ELF_MAGIC))).isTrue();
        assertThat(MameExecutable.isLaunchable(writeExecutable(tempDir.resolve("hbmame"), MACHO_MAGIC))).isTrue();
    }

    @Test
    @DisplayName("emulator-named files without native magic are rejected")
    void rejectsNamedFilesWithoutMagic() throws IOException {
        assertThat(MameExecutable.isLaunchable(writeExecutable(tempDir.resolve("mame.exe"), new byte[] { 0x00, 0x01, 0x02, 0x03 }))).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "mame.exe,true",
            "MAME64,true",
            "mess,true",
            "mess64.exe,true",
            "sdlmame,true",
            "cmd.exe,false",
            "python,false",
            "'',false"
    })
    @DisplayName("emulator name gate allows MAME and MESS only")
    void emulatorNameGate(final String name, final boolean expected) {
        assertThat(MameExecutable.hasSupportedEmulatorName(name)).isEqualTo(expected);
    }

    @Test
    @DisplayName("listxml output must look like MAME or MESS XML")
    void listXmlOutputGate() {
        assertThat(MameExecutable.isMameListOutput("<?xml version=\"1.0\"?>\n<mame build=\"0.261\">", false)).isTrue();
        assertThat(MameExecutable.isMameListOutput("<?xml version=\"1.0\"?>\n<!DOCTYPE mess>\n<mess>", false)).isTrue();
        assertThat(MameExecutable.isMameListOutput("<?xml version=\"1.0\"?>\n<softwarelists>", true)).isTrue();
        assertThat(MameExecutable.isMameListOutput("<?xml version=\"1.0\"?>\n<root/>", false)).isFalse();
        assertThat(MameExecutable.isMameListOutput("not xml", false)).isFalse();
        assertThat(MameExecutable.isMameListOutput("", false)).isFalse();
        assertThat(MameExecutable.isMameListOutput(null, false)).isFalse();
    }

    private static File writeExecutable(final Path path, final byte[] content) throws IOException {
        Files.write(path, content);
        final File file = path.toFile();
        file.setExecutable(true, false);
        return file;
    }
}
