package jrm.aui.status;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression: user-controlled text passed to {@link Html4Renderer#toLabel} must be HTML-escaped
 * before it is wrapped in styling tags.
 */
@DisplayName("Html4Renderer HTML escaping")
class Html4RendererTest {

    @Test
    @DisplayName("toLabel escapes crafted markup before wrapping")
    void toLabelEscapesCraftedMarkupBeforeWrapping() {
        final var renderer = new Html4Renderer();
        final String html = renderer.toLabel("<img src=x onerror=alert(1)>", "blue", false, false);

        assertThat(html).isEqualTo("<span style='color:blue'>&lt;img src=x onerror=alert(1)&gt;</span>");
    }

    @Test
    @DisplayName("toLabel applies bold and italic after escaping")
    void toLabelAppliesBoldAndItalicAfterEscaping() {
        final var renderer = new Html4Renderer();
        final String html = renderer.toLabel("<b>1.0</b>", "black", true, true);

        assertThat(html).isEqualTo("<span style='color:black'><b><i>&lt;b&gt;1.0&lt;/b&gt;</i></b></span>");
    }

    @Test
    @DisplayName("toBlue escapes filename markup")
    void toBlueEscapesFilenameMarkup() {
        final var renderer = new Html4Renderer();
        final String html = renderer.toBlue("foo&bar.dat");

        assertThat(html).isEqualTo("<span style='color:blue'>foo&amp;bar.dat</span>");
    }
}
