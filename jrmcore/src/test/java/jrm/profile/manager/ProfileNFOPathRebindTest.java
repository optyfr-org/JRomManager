package jrm.profile.manager;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jrm.security.Session;
import jrm.security.SignedObjectStore;

/**
 * Regression: crafted serialized ProfileNFO must not redirect delete to arbitrary paths.
 */
@DisplayName("ProfileNFO path rebind security")
class ProfileNFOPathRebindTest {

    private static final String JRM_DIR_PROP = "jrommanager.dir";

    @TempDir
    Path tempDir;

    private Session session;

    @BeforeEach
    void setUp() throws IOException {
        System.setProperty(JRM_DIR_PROP, tempDir.toString());
        Files.createDirectories(tempDir.resolve("users").resolve("JRomManager"));
        session = new Session("profile-nfo-path-rebind-test");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(JRM_DIR_PROP);
    }

    @Test
    @DisplayName("load rebinds file and delete only removes trusted profile path")
    void loadRebindsFileAndDeleteIgnoresSerializedPath() throws Exception {
        final Path profilesDir = session.getUser().getSettings().getWorkPath().resolve("xmlfiles");
        Files.createDirectories(profilesDir);

        final File trustedProfile = profilesDir.resolve("legit.dat").toFile();
        Files.writeString(trustedProfile.toPath(), "profile-body");

        final File victim = tempDir.resolve("outside-victim.txt").toFile();
        Files.writeString(victim.toPath(), "do-not-delete");

        final File siblingRomDat = profilesDir.resolve("mame-roms.xml").toFile();
        Files.writeString(siblingRomDat.toPath(), "roms");

        final File outsideCompanion = tempDir.resolve("outside-companion.xml").toFile();
        Files.writeString(outsideCompanion.toPath(), "companion");

        final ProfileNFO crafted = ProfileNFO.load(session, trustedProfile);
        crafted.getMame().setFileroms(outsideCompanion);
        // Persist under the real NFO path first, then overwrite payload with attacker-controlled paths.
        crafted.save(session);

        final File nfoFile = session.getUser().getSettings().getWorkFile(trustedProfile.getParentFile(), trustedProfile.getName(), ".nfo");
        final ProfileNFO attackerPayload = ProfileNFO.load(session, trustedProfile);
        // Use reflection-free path: relocate would move NFO; instead write a fresh object via SignedObjectStore
        // by saving after temporarily pointing at victim through bind + manual field via delete target test helper.
        writeCraftedNfo(nfoFile, victim, outsideCompanion);

        // Ensure NFO is considered fresh relative to the profile.
        trustedProfile.setLastModified(System.currentTimeMillis() - 60_000L);
        nfoFile.setLastModified(System.currentTimeMillis());

        final ProfileNFO loaded = ProfileNFO.load(session, trustedProfile);
        assertThat(loaded.getFile()).isEqualTo(trustedProfile);
        assertThat(loaded.getMame().getFileroms()).isNull();

        assertThat(loaded.delete()).isTrue();

        assertThat(trustedProfile).doesNotExist();
        assertThat(victim).exists();
        assertThat(outsideCompanion).exists();
        assertThat(Files.readString(victim.toPath())).isEqualTo("do-not-delete");
    }

    @Test
    @DisplayName("deleteAlongside only removes companion files next to the profile")
    void deleteAlongsideOnlyRemovesSiblingCompanions() throws Exception {
        final Path profilesDir = session.getUser().getSettings().getWorkPath().resolve("xmlfiles");
        Files.createDirectories(profilesDir);

        final File profile = profilesDir.resolve("game.dat").toFile();
        Files.writeString(profile.toPath(), "p");

        final File sibling = profilesDir.resolve("roms.xml").toFile();
        Files.writeString(sibling.toPath(), "s");

        final File outsider = tempDir.resolve("secret.xml").toFile();
        Files.writeString(outsider.toPath(), "x");

        final var mame = new ProfileNFOMame();
        mame.setFileroms(sibling);
        mame.setFilesl(outsider);
        mame.deleteAlongside(profile);

        assertThat(sibling).doesNotExist();
        assertThat(outsider).exists();
    }

    /**
     * Builds a ProfileNFO whose serialized {@code file} and MAME companion paths point at attacker-chosen locations.
     */
    private void writeCraftedNfo(final File nfoFile, final File fakeProfilePath, final File fakeCompanion) throws Exception {
        final ProfileNFO shell = ProfileNFO.load(session, fakeProfilePath);
        shell.getMame().setFileroms(fakeCompanion);
        SignedObjectStore.write(session, nfoFile, shell);
    }
}
