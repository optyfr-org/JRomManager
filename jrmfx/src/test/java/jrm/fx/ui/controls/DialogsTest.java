package jrm.fx.ui.controls;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Dialogs}.
 * <p>
 * Tests the utility methods that don't require UI interaction.
 * The dialog display methods (showError, showAlert, showConfirmation) are not tested
 * as they call showAndWait() which blocks in test context and depend on MainFrame/JRMScene.
 *
 * @since 3.0.5
 */
@DisplayName("Dialogs Tests")
class DialogsTest {

    /**
     * Tests the private formatException() method via reflection.
     *
     * @param e the exception to format
     * @return the formatted exception summary
     */
    private String invokeFormatException(Throwable e) throws Exception {
        Method method = Dialogs.class.getDeclaredMethod("formatException", Throwable.class);
        method.setAccessible(true);
        return (String) method.invoke(null, e);
    }

    /**
     * Verifies that formatException returns type and message without stack frames.
     */
    @Test
    @DisplayName("Should format exception type and message without stack frames")
    void shouldFormatExceptionTypeAndMessageWithoutStackFrames() throws Exception {
        Exception exception = new RuntimeException("Test exception message");

        String formatted = invokeFormatException(exception);

        assertThat(formatted)
                .as("Formatted exception should not be null")
                .isNotNull()
                .as("Should contain exception class name")
                .contains("RuntimeException")
                .as("Should contain exception message")
                .contains("Test exception message")
                .as("Should not contain stack frames")
                .doesNotContain("\tat ");
    }

    /**
     * Verifies that the cause chain is included without stack frames.
     */
    @Test
    @DisplayName("Should format exception with cause")
    void shouldFormatExceptionWithCause() throws Exception {
        Exception cause = new IllegalArgumentException("Root cause");
        Exception exception = new RuntimeException("Wrapper exception", cause);

        String formatted = invokeFormatException(exception);

        assertThat(formatted)
                .as("Should contain wrapper exception")
                .contains("RuntimeException")
                .as("Should contain wrapper message")
                .contains("Wrapper exception")
                .as("Should contain cause exception")
                .contains("IllegalArgumentException")
                .as("Should contain cause message")
                .contains("Root cause")
                .as("Should contain 'Caused by'")
                .contains("Caused by")
                .as("Should not contain stack frames")
                .doesNotContain("\tat ");
    }

    /**
     * Verifies that a chain of three exception causes is fully present.
     */
    @Test
    @DisplayName("Should format exception with multiple causes")
    void shouldFormatExceptionWithMultipleCauses() throws Exception {
        Exception rootCause = new NullPointerException("Null value");
        Exception middleCause = new IllegalStateException("Invalid state", rootCause);
        Exception topException = new RuntimeException("Top level", middleCause);

        String formatted = invokeFormatException(topException);

        assertThat(formatted)
                .as("Should contain all exception types")
                .contains("RuntimeException")
                .contains("IllegalStateException")
                .contains("NullPointerException")
                .as("Should contain all messages")
                .contains("Top level")
                .contains("Invalid state")
                .contains("Null value");
    }

    /**
     * Verifies formatting when the exception has no detail message.
     */
    @Test
    @DisplayName("Should format exception without message")
    void shouldFormatExceptionWithoutMessage() throws Exception {
        Exception exception = new RuntimeException();

        String formatted = invokeFormatException(exception);

        assertThat(formatted)
                .as("Formatted exception should not be null")
                .isNotNull()
                .as("Should contain exception class name")
                .contains("RuntimeException");
    }

    /**
     * Verifies formatting when the exception message is an empty string.
     */
    @Test
    @DisplayName("Should format exception with empty message")
    void shouldFormatExceptionWithEmptyMessage() throws Exception {
        Exception exception = new RuntimeException("");

        String formatted = invokeFormatException(exception);

        assertThat(formatted)
                .as("Formatted exception should not be null")
                .isNotNull()
                .as("Should contain exception class name")
                .contains("RuntimeException");
    }

    /**
     * Verifies that special characters in messages are preserved.
     */
    @Test
    @DisplayName("Should format exception with special characters in message")
    void shouldFormatExceptionWithSpecialCharactersInMessage() throws Exception {
        Exception exception = new RuntimeException("Error: <tag> & \"quotes\" \n newlines \t tabs");

        String formatted = invokeFormatException(exception);

        assertThat(formatted)
                .as("Should contain special characters")
                .contains("<tag>")
                .contains("&")
                .contains("\"quotes\"");
    }

    /**
     * Verifies that a checked exception is properly included.
     */
    @Test
    @DisplayName("Should handle checked exception")
    void shouldHandleCheckedException() throws Exception {
        Exception exception = new java.io.IOException("File not found");

        String formatted = invokeFormatException(exception);

        assertThat(formatted)
                .as("Should contain IOException")
                .contains("IOException")
                .as("Should contain message")
                .contains("File not found");
    }

    /**
     * Verifies that a {@link java.lang.Error} is properly included.
     */
    @Test
    @DisplayName("Should handle error")
    void shouldHandleError() throws Exception {
        Error error = new OutOfMemoryError("Java heap space");

        String formatted = invokeFormatException(error);

        assertThat(formatted)
                .as("Should contain OutOfMemoryError")
                .contains("OutOfMemoryError")
                .as("Should contain message")
                .contains("Java heap space");
    }
}
