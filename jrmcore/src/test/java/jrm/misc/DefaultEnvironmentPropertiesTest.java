package jrm.misc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultEnvironmentProperties} placeholder resolution.
 */
@DisplayName("DefaultEnvironmentProperties")
class DefaultEnvironmentPropertiesTest {

    @Test
    @DisplayName("resolves nested JRM_ placeholders")
    void resolvesNestedPlaceholders() {
        final var properties = new Properties();
        properties.setProperty("JRM_INNER", "world");
        properties.setProperty("JRM_OUTER", "hello ${JRM_INNER}");

        final var env = new DefaultEnvironmentProperties(properties);

        assertThat(env.getProperty("JRM_OUTER")).isEqualTo("hello world");
    }

    @Test
    @DisplayName("stops expanding self-referential placeholders")
    void stopsSelfReferentialPlaceholders() {
        final var properties = new Properties();
        properties.setProperty("JRM_LOOP", "${JRM_LOOP}");

        assertThatCode(() -> new DefaultEnvironmentProperties(properties)).doesNotThrowAnyException();
        assertThat(new DefaultEnvironmentProperties(properties).getProperty("JRM_LOOP")).isEqualTo("${JRM_LOOP}");
    }

    @Test
    @DisplayName("leaves unmatched placeholders and expands $$")
    void leavesUnmatchedAndExpandsDollarEscape() {
        final var properties = new Properties();
        properties.setProperty("JRM_ESC", "cost $$5 and ${JRM_MISSING}");

        final var env = new DefaultEnvironmentProperties(properties);

        assertThat(env.getProperty("JRM_ESC")).isEqualTo("cost $5 and ${JRM_MISSING}");
    }

    @Test
    @DisplayName("does not overflow on a long placeholder chain")
    void doesNotOverflowOnLongChain() {
        final var properties = new Properties();
        properties.setProperty("JRM_P" + DefaultEnvironmentProperties.MAX_REPLACEMENT_DEPTH, "end");
        for (var i = 0; i < DefaultEnvironmentProperties.MAX_REPLACEMENT_DEPTH; i++)
            properties.setProperty("JRM_P" + i, "${JRM_P" + (i + 1) + "}");

        assertThatCode(() -> new DefaultEnvironmentProperties(properties)).doesNotThrowAnyException();
        assertThat(new DefaultEnvironmentProperties(properties).getProperty("JRM_P0")).isNotNull();
    }
}
