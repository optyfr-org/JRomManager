package jrm.aui.status;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression: user-controlled text passed to {@link NeutralRenderer#toLabel} must be XML-escaped
 * before it is wrapped in a label element.
 */
@DisplayName("NeutralRenderer XML escaping")
class NeutralRendererTest {

    @Test
    @DisplayName("toLabel escapes crafted markup before wrapping")
    void toLabelEscapesCraftedMarkupBeforeWrapping() {
        final var renderer = new NeutralRenderer();
        final String xml = renderer.toLabel("<img src=x onerror=alert(1)>", "blue", false, false);

        assertThat(xml).isEqualTo("<label color=\"blue\" bold=\"false\" italic=\"false\">&lt;img src=x onerror=alert(1)&gt;</label>");
    }
}
