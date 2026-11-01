package jrm.fx.ui;

import java.net.URL;

import javafx.beans.NamedArg;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.paint.Paint;
import jrm.misc.EnumWithDefault;
import lombok.Getter;
import lombok.Setter;

/**
 * Custom {@link Scene} subclass that manages application-wide style sheets.
 * <p>
 * Provides a set of predefined size variants ({@link StyleSheet}) and applies the
 * selected one on top of any base style sheets already attached to the scene.
 *
 * @since 2.5
 */
public class JRMScene extends Scene {
    /**
     * Predefined style sheet size variants.
     */
    public enum StyleSheet {
        /** System default (no extra CSS). */
        SYSTEM(null),
        /** Extra-extra-small. */
        XXS("XXS.css"),
        /** Extra-small. */
        XS("XS.css"),
        /** Small. */
        S("S.css"),
        /** Medium. */
        M("M.css"),
        /** Large. */
        L("L.css"),
        /** Extra-large (default). */
        XL("XL.css"),
        /** Extra-extra-large. */
        XXL("XXL.css");

        /**
         * The CSS file name, or {@code null} for the system default.
         * @return the CSS file name, or {@code null} for the system default
         */
        @Getter
        private String fileName;

        private StyleSheet(String fileName) {
            this.fileName = fileName;
        }
    }

    /**
     * Scene-level preferences backed by {@link EnumWithDefault}.
     */
    public enum ScenePrefs implements EnumWithDefault {
        /** The active style sheet. */
        style_sheet(StyleSheet.SYSTEM); // NOSONAR

        /** The default value. */
        private final Object deflt;

        private ScenePrefs(Object deflt) {
            this.deflt = deflt;
        }

        @Override
        public Object getDefault() {
            return deflt;
        }

    }

    /**
     * The currently active style sheet.
     * @param sheet the currently active style sheet
     * @return the currently active style sheet
     */
    @Getter
    @Setter
    private static StyleSheet sheet = StyleSheet.XL;

    /**
     * The original style sheets attached before the size variant is applied.
     */
    private String[] orgSheets;

    /**
     * Constructs a scene with full rendering options.
     *
     * @param root         the scene graph root
     * @param width        the scene width, or {@code -1} for automatic
     * @param height       the scene height, or {@code -1} for automatic
     * @param depthBuffer  whether to use a depth buffer
     * @param antiAliasing the anti-aliasing mode
     */
    public JRMScene(@NamedArg("root") Parent root, @NamedArg(value = "width", defaultValue = "-1") double width, @NamedArg(value = "height", defaultValue = "-1") double height,
            @NamedArg("depthBuffer") boolean depthBuffer, @NamedArg(value = "antiAliasing", defaultValue = "DISABLED") SceneAntialiasing antiAliasing) {
        super(root, width, height, depthBuffer, antiAliasing);
        initSheets();
    }

    /**
     * Constructs a scene with a depth buffer option.
     *
     * @param root        the scene graph root
     * @param width       the scene width, or {@code -1} for automatic
     * @param height      the scene height, or {@code -1} for automatic
     * @param depthBuffer whether to use a depth buffer
     */
    public JRMScene(@NamedArg("root") Parent root, @NamedArg(value = "width", defaultValue = "-1") double width, @NamedArg(value = "height", defaultValue = "-1") double height,
            @NamedArg("depthBuffer") boolean depthBuffer) {
        super(root, width, height, depthBuffer);
        initSheets();
    }

    /**
     * Constructs a scene with a fill paint.
     *
     * @param root   the scene graph root
     * @param width  the scene width, or {@code -1} for automatic
     * @param height the scene height, or {@code -1} for automatic
     * @param fill   the fill paint
     */
    public JRMScene(@NamedArg("root") Parent root, @NamedArg(value = "width", defaultValue = "-1") double width, @NamedArg(value = "height", defaultValue = "-1") double height,
            @NamedArg(value = "fill", defaultValue = "WHITE") Paint fill) {
        super(root, width, height, fill);
        initSheets();
    }

    /**
     * Constructs a scene with explicit dimensions.
     *
     * @param root   the scene graph root
     * @param width  the scene width, or {@code -1} for automatic
     * @param height the scene height, or {@code -1} for automatic
     */
    public JRMScene(@NamedArg("root") Parent root, @NamedArg(value = "width", defaultValue = "-1") double width, @NamedArg(value = "height", defaultValue = "-1") double height) {
        super(root, width, height);
        initSheets();
    }

    /**
     * Constructs a scene with a fill paint and automatic sizing.
     *
     * @param root the scene graph root
     * @param fill the fill paint
     */
    public JRMScene(@NamedArg("root") Parent root, Paint fill) {
        super(root, fill);
        initSheets();
    }

    /**
     * Constructs a scene wrapping the given root node.
     *
     * @param root the scene graph root
     */
    public JRMScene(@NamedArg("root") Parent root) {
        super(root);
        initSheets();
    }

    /**
     * Captures the original style sheets and applies the current size variant.
     */
    private void initSheets() {
        final var styleSheets = getStylesheets();
        orgSheets = styleSheets.toArray(String[]::new);
        applySheet(sheet);
    }

    /**
     * Re-applies the current size variant style sheet on top of the original sheets.
     */
    public void applySheet() {
        getStylesheets().clear();
        getStylesheets().addAll(orgSheets);
        applySheet(this);
    }

    /**
     * Sets the given style sheet as active and re-applies it.
     *
     * @param ss the style sheet to activate
     */
    public void applySheet(StyleSheet ss) {
        setSheet(ss);
        applySheet();
    }

    /**
     * Applies the current size variant style sheet to the given scene.
     *
     * @param scene the scene to style
     */
    public static void applySheet(Scene scene) {
        if (sheet.fileName != null) {
            String css = "/jrm/fx/ui/css/%s".formatted(sheet.fileName);
            URL resource = JRMScene.class.getResource(css);
            if (resource != null) {
                final var url = resource.toExternalForm();
                scene.getStylesheets().add(url);
            }
        }
    }
}
