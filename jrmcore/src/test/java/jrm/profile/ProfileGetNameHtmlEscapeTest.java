package jrm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jrm.profile.manager.ProfileNFO;
import jrm.security.Session;

/**
 * Regression: profile names sent to the client must not embed unescaped markup from filenames or DAT headers.
 */
@DisplayName("Profile.getName HTML escaping")
class ProfileGetNameHtmlEscapeTest {

    private static final String JRM_DIR_PROP = "jrommanager.dir";

    @TempDir
    Path tempDir;

    private Session session;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty(JRM_DIR_PROP, tempDir.toString());
        Files.createDirectories(tempDir.resolve("users").resolve("JRomManager"));
        session = new Session("profile-getname-html-escape-test", "JRomManager", new String[] { "admin" });
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(JRM_DIR_PROP);
    }

    @Test
    @DisplayName("getName escapes filename ampersands and crafted header description")
    void getNameEscapesFilenameAndHeaderDescription() throws Exception {
        final Profile profile = newProfile(xmlfiles().resolve("foo&bar.dat").toFile());
        profile.getHeader().put("description", new StringBuilder("<img src=x onerror=alert(1)>"));

        final String html = profile.getName();

        assertThat(html).contains("foo&amp;bar.dat");
        assertThat(html).contains("&lt;img src=x onerror=alert(1)&gt;");
        assertThat(html).doesNotContain("<img src=x onerror=alert(1)>");
    }

    @Test
    @DisplayName("getName escapes crafted header name and version")
    void getNameEscapesHeaderNameAndVersion() throws Exception {
        final Profile profile = newProfile(xmlfiles().resolve("safe.dat").toFile());
        profile.getHeader().put("name", new StringBuilder("<b>evil</b>"));
        profile.getHeader().put("version", new StringBuilder("<script>alert(1)</script>"));

        final String html = profile.getName();

        assertThat(html).contains("&lt;b&gt;evil&lt;/b&gt;");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).doesNotContain("<b>evil</b>");
        assertThat(html).doesNotContain("<script>alert(1)</script>");
    }

    private Path xmlfiles() throws Exception {
        final Path xmlfiles = session.getUser().getSettings().getWorkPath().resolve("xmlfiles");
        Files.createDirectories(xmlfiles);
        return xmlfiles;
    }

    private Profile newProfile(final File datFile) throws Exception {
        Files.writeString(datFile.toPath(), "<datafile/>");

        final Constructor<Profile> constructor = Profile.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        final Profile profile = constructor.newInstance();
        setField(profile, "session", session);

        final Constructor<ProfileNFO> nfoCtor = ProfileNFO.class.getDeclaredConstructor(File.class);
        nfoCtor.setAccessible(true);
        setField(profile, "nfo", nfoCtor.newInstance(datFile));
        return profile;
    }

    private static void setField(final Profile profile, final String fieldName, final Object value) throws Exception {
        final Field field = Profile.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(profile, value);
    }
}
