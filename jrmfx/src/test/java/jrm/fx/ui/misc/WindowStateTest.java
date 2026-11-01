package jrm.fx.ui.misc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WindowState}.
 * <p>
 * Validates that the Lombok {@link lombok.Data} generated getters and setters
 * produce the expected default values and correctly propagate writes for all
 * seven properties: position {@code x}/{@code y}, size {@code w}/{@code h},
 * and the boolean state flags {@code m}, {@code f}, and {@code i}.
 */
@DisplayName("WindowState Unit Tests")
class WindowStateTest {

    /**
     * Verifies that a freshly constructed {@link WindowState} has zero
     * coordinates, zero dimensions, and all state flags set to {@code false}.
     */
    @Test
    @DisplayName("Should create WindowState with default values")
    void shouldCreateWindowStateWithDefaultValues() {
        WindowState state = new WindowState();
        
        assertThat(state.getX()).isEqualTo(0.0);
        assertThat(state.getY()).isEqualTo(0.0);
        assertThat(state.getW()).isEqualTo(0.0);
        assertThat(state.getH()).isEqualTo(0.0);
        assertThat(state.isMaximized()).isFalse();
        assertThat(state.isF()).isFalse();
        assertThat(state.isI()).isFalse();
    }

    /**
     * Ensures the X-coordinate getter returns the value written by its setter.
     */
    @Test
    @DisplayName("Should set and get X coordinate")
    void shouldSetAndGetXCoordinate() {
        WindowState state = new WindowState();
        state.setX(100.5);
        
        assertThat(state.getX()).isEqualTo(100.5);
    }

    /**
     * Ensures the Y-coordinate getter returns the value written by its setter.
     */
    @Test
    @DisplayName("Should set and get Y coordinate")
    void shouldSetAndGetYCoordinate() {
        WindowState state = new WindowState();
        state.setY(200.75);
        
        assertThat(state.getY()).isEqualTo(200.75);
    }

    /**
     * Ensures the width getter returns the value written by its setter.
     */
    @Test
    @DisplayName("Should set and get width")
    void shouldSetAndGetWidth() {
        WindowState state = new WindowState();
        state.setW(800.0);
        
        assertThat(state.getW()).isEqualTo(800.0);
    }

    /**
     * Ensures the height getter returns the value written by its setter.
     */
    @Test
    @DisplayName("Should set and get height")
    void shouldSetAndGetHeight() {
        WindowState state = new WindowState();
        state.setH(600.0);
        
        assertThat(state.getH()).isEqualTo(600.0);
    }

    /**
     * Ensures the maximized flag toggles correctly.
     */
    @Test
    @DisplayName("Should set and get maximized state")
    void shouldSetAndGetMaximizedState() {
        WindowState state = new WindowState();
        state.setMaximized(true);
        
        assertThat(state.isMaximized()).isTrue();
    }

    /**
     * Ensures the full-screen flag toggles correctly.
     */
    @Test
    @DisplayName("Should set and get full-screen state")
    void shouldSetAndGetFullScreenState() {
        WindowState state = new WindowState();
        state.setF(true);
        
        assertThat(state.isF()).isTrue();
    }

    /**
     * Ensures the iconified flag toggles correctly.
     */
    @Test
    @DisplayName("Should set and get iconified state")
    void shouldSetAndGetIconifiedState() {
        WindowState state = new WindowState();
        state.setI(true);
        
        assertThat(state.isI()).isTrue();
    }

    /**
     * Sets every property at once and confirms each getter reflects the written
     * value with no cross-property interference.
     */
    @Test
    @DisplayName("Should handle all properties together")
    void shouldHandleAllPropertiesTogether() {
        WindowState state = new WindowState();
        state.setX(150.0);
        state.setY(250.0);
        state.setW(1024.0);
        state.setH(768.0);
        state.setMaximized(true);
        state.setF(false);
        state.setI(false);
        
        assertThat(state.getX()).isEqualTo(150.0);
        assertThat(state.getY()).isEqualTo(250.0);
        assertThat(state.getW()).isEqualTo(1024.0);
        assertThat(state.getH()).isEqualTo(768.0);
        assertThat(state.isMaximized()).isTrue();
        assertThat(state.isF()).isFalse();
        assertThat(state.isI()).isFalse();
    }

    /**
     * Confirms that negative coordinate values are accepted and round-tripped
     * correctly (e.g. for off-screen window positions).
     */
    @Test
    @DisplayName("Should handle negative coordinates")
    void shouldHandleNegativeCoordinates() {
        WindowState state = new WindowState();
        state.setX(-100.0);
        state.setY(-50.0);
        
        assertThat(state.getX()).isEqualTo(-100.0);
        assertThat(state.getY()).isEqualTo(-50.0);
    }

    /**
     * Confirms that zero width and height are accepted and round-tripped
     * correctly.
     */
    @Test
    @DisplayName("Should handle zero dimensions")
    void shouldHandleZeroDimensions() {
        WindowState state = new WindowState();
        state.setW(0.0);
        state.setH(0.0);
        
        assertThat(state.getW()).isEqualTo(0.0);
        assertThat(state.getH()).isEqualTo(0.0);
    }
}
