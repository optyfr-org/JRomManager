package jrm.fx.ui.misc;

import javafx.stage.Stage;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Captures and restores the state of a JavaFX window.
 * <p>
 * Stores position (x, y), size (width, height), and state flags (maximized, full-screen,
 * iconified) for persistence across application sessions.
 *
 * @since 2.5
 */
@Data
@NoArgsConstructor
class WindowState {
    /**
     * The X coordinate of the window.
     *
     * @param x the X coordinate
     * @return the X coordinate
     */
    private double x;
    /**
     * The Y coordinate of the window.
     *
     * @param y the Y coordinate
     * @return the Y coordinate
     */
    private double y;
    /**
     * The window width.
     *
     * @param w the width
     * @return the width
     */
    private double w;
    /**
     * The window height.
     *
     * @param h the height
     * @return the height
     */
    private double h;
    /**
     * Whether the window was maximized.
     *
     * @param maximized whether maximized
     * @return whether maximized
     */
    private boolean maximized = false;
    /**
     * Whether the window was full-screen.
     *
     * @param f whether full-screen
     * @return whether full-screen
     */
    private boolean f = false;
    /**
     * Whether the window was iconified.
     *
     * @param i whether iconified
     * @return whether iconified
     */
    private boolean i = false;

    /**
     * Constructs a window state from a stage.
     *
     * @param window the window to capture
     */
    private WindowState(Stage window) {
        maximized = window.isMaximized();
        f = window.isFullScreen();
        i = window.isIconified();
        window.setIconified(false);
        window.setFullScreen(false);
        window.setMaximized(false);
        x = window.getX();
        y = window.getY();
        w = window.getWidth();
        h = window.getHeight();
    }

    /**
     * Creates a window state instance from a stage.
     *
     * @param window the window to capture
     * @return the window state
     */
    public static WindowState getInstance(Stage window) {
        return new WindowState(window);
    }

    /**
     * Restores the window state to a stage.
     *
     * @param window the window to restore
     */
    public void restore(Stage window) {
        window.setX(x);
        window.setY(y);
        window.setWidth(w);
        window.setHeight(h);
        window.setMaximized(maximized);
        window.setFullScreen(f);
        window.setIconified(i);
    }
}
