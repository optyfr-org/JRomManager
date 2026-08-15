package jrm.misc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Log formatThrowable Tests")
class LogFormatThrowableTest {

    @Test
    @DisplayName("formatThrowable returns empty string for null")
    void formatThrowableNull() {
        assertThat(Log.formatThrowable(null)).isEmpty();
    }

    @Test
    @DisplayName("formatThrowable includes type and message without stack frames")
    void formatThrowableTypeAndMessage() {
        final var ex = new IllegalStateException("boom");
        final var formatted = Log.formatThrowable(ex);
        assertThat(formatted)
                .contains("IllegalStateException")
                .contains("boom")
                .doesNotContain("\tat ");
    }

    @Test
    @DisplayName("formatThrowable includes cause chain")
    void formatThrowableCauseChain() {
        final var root = new NullPointerException("npe");
        final var mid = new IllegalArgumentException("bad", root);
        final var top = new RuntimeException("wrap", mid);
        final var formatted = Log.formatThrowable(top);
        assertThat(formatted)
                .contains("RuntimeException")
                .contains("wrap")
                .contains("Caused by:")
                .contains("IllegalArgumentException")
                .contains("bad")
                .contains("NullPointerException")
                .contains("npe")
                .doesNotContain("\tat ");
    }

    @Test
    @DisplayName("Formatter appends throwable summary without stack frames")
    void formatterOmitsStackFrames() {
        final var logRecord = new LogRecord(Level.SEVERE, "failed");
        logRecord.setThrown(new RuntimeException("detail"));
        final var line = new Log.Formatter().format(logRecord);
        assertThat(line)
                .contains("[SEVERE]")
                .contains("failed")
                .contains("RuntimeException")
                .contains("detail")
                .doesNotContain("\tat ");
    }
}
