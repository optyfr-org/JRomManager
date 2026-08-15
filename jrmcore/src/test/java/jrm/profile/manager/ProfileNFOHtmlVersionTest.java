package jrm.profile.manager;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression: crafted .nfo version markup must be escaped before Swing HTML rendering.
 */
@DisplayName("ProfileNFO HTML version escaping")
class ProfileNFOHtmlVersionTest {

    @Test
    @DisplayName("getHTMLVersion displays crafted markup literally")
    void getHTMLVersionEscapesCraftedMarkup(@TempDir final Path tempDir) throws Exception {
        final File datFile = tempDir.resolve("test.dat").toFile();
        final Constructor<ProfileNFO> constructor = ProfileNFO.class.getDeclaredConstructor(File.class);
        constructor.setAccessible(true);
        final ProfileNFO nfo = constructor.newInstance(datFile);
        nfo.getStats().setVersion("<b>1.0</b>");

        final String html = nfo.getHTMLVersion();

        assertThat(html)
                .contains("&lt;b&gt;1.0&lt;/b&gt;")
                .doesNotContain("<b>1.0</b>");
    }
}
