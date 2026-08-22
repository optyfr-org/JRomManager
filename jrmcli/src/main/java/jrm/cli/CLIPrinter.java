package jrm.cli;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * Handles styled output for the CLI (errors, warnings, info, key-value pairs).
 */
public class CLIPrinter {

    static final AttributedStyle STYLE_RED_BOLD = AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold();
    static final AttributedStyle STYLE_YELLOW_BOLD = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold();
    static final AttributedStyle STYLE_GREEN_BOLD = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN).bold();
    static final AttributedStyle STYLE_CYAN_BOLD = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold();

    @SuppressWarnings("unused")
    static final AttributedStyle STYLE_DIM = AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT).italic();

    private final java.io.PrintWriter out;

    public CLIPrinter(java.io.PrintWriter out) {
        this.out = out;
    }

    void printError(final String msg) {
        out.println(new AttributedString(msg, STYLE_RED_BOLD).toAnsi());
    }

    void printWarning(final String msg) {
        out.println(new AttributedString(msg, STYLE_YELLOW_BOLD).toAnsi());
    }

    void printInfo(final String msg) {
        out.println(new AttributedString(msg, STYLE_GREEN_BOLD).toAnsi());
    }

    void printKeyValue(final String key, final String value) {
        final AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(STYLE_CYAN_BOLD);
        sb.append(key);
        sb.style(AttributedStyle.DEFAULT);
        sb.append("="); //$NON-NLS-1$
        sb.append(value);
        out.println(sb.toAnsi());
    }

    @SuppressWarnings("unused")
    void printLabel(final String label, final String value, final AttributedStyle labelStyle) {
        final AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(labelStyle);
        sb.append(label);
        sb.style(AttributedStyle.DEFAULT);
        sb.append(value);
        out.println(sb.toAnsi());
    }
}
