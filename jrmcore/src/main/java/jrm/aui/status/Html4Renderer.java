package jrm.aui.status;

import java.util.Optional;

import org.apache.commons.text.StringEscapeUtils;

/**
 * Implementation of the StatusRenderer interface that generates HTML4-compliant output. This renderer provides methods to format
 * text as HTML documents, apply styling such as bold and italic, and create progress bars using HTML tables. It also includes
 * functionality to escape special characters for safe HTML rendering.
 * 
 * @author optyfr
 */
public class Html4Renderer implements StatusRenderer {

    /**
     * Constructs a new Html4Renderer instance with default settings. This constructor does not perform any specific initialization
     * and can be used to create a renderer for HTML4-compliant output.
     */
    public Html4Renderer() {
        /* default constructor */ }

    /**
     * Converts the given character sequence into an HTML document format by wrapping it with the necessary HTML tags. This method
     * takes a CharSequence as input and returns a String that represents the content formatted as an HTML document, including the
     * &lt;html&gt; and &lt;body&gt; tags.
     * 
     * @param str the character sequence to be converted into an HTML document
     * 
     * @return a String representing the input formatted as an HTML document
     */
    @Override
    public String toDocument(CharSequence str) {
        return "<html><body>" + str + "</body></html>";
    }

    /**
     * Wraps the given character sequence in a &lt;nobr&gt; tag to prevent line breaks. This method takes a CharSequence as input
     * and returns a String that represents the content wrapped in a &lt;nobr&gt; tag, ensuring that the text will not break into
     * multiple lines when rendered in HTML.
     * 
     * @param str the character sequence to be wrapped in a &lt;nobr&gt; tag
     * 
     * @return a String representing the input wrapped in a &lt;nobr&gt; tag
     */
    @Override
    public String toNoBR(CharSequence str) {
        return "<nobr>" + str + "</nobr>";
    }

    /**
     * Converts the given character sequence into an HTML label format with optional styling. This method takes a CharSequence, a
     * web color, and boolean flags for bold and italic styling. It returns a String that represents the input formatted as an HTML
     * label, applying the specified color and styles as needed.
     * 
     * @param str the character sequence to be converted into an HTML label
     * @param webcolor the web color to be applied to the text (e.g., "red", "#ff0000"), or null for default color
     * @param bold a boolean flag indicating whether to apply bold styling to the text
     * @param italic a boolean flag indicating whether to apply italic styling to the text
     * 
     * @return a String representing the input formatted as an HTML label with the specified color and styles
     */
    @Override
    public String toLabel(CharSequence str, String webcolor, boolean bold, boolean italic) {
        String fstr = escape(str);
        if (italic)
            fstr = "<i>" + fstr + "</i>";
        if (bold)
            fstr = "<b>" + fstr + "</b>";
        return "<span style='color:%s'>%s</span>".formatted(Optional.ofNullable(webcolor).orElse("black"), fstr);
    }

    /**
     * Generates an HTML representation of a progress bar based on the given parameters. This method takes the width of the progress
     * bar, the current progress value, the maximum value, and an optional message. It returns a String that represents the progress
     * bar formatted as an HTML table, with the progress visually indicated by a colored cell and the message displayed alongside it
     * if provided.
     * 
     * @param width the total width of the progress bar in pixels
     * @param i the current progress value
     * @param max the maximum progress value
     * @param msg an optional message to be displayed alongside the progress bar, or null if no message is needed
     * 
     * @return a String representing the progress bar formatted as an HTML table with the specified parameters
     */
    @Override
    public String progress(int width, int i, int max, String msg) {
        if (msg == null)
            return String.format("<html><table cellpadding=2 cellspacing=0><tr><td valign='middle'>%s</td></table></html>", internalProgress(width, i, max));
        return String.format("<html><table cellpadding=2 cellspacing=0><tr><td valign='middle'>%s</td><td style='font-size:95%%;white-space:nowrap'>%s</td></table></html>",
                internalProgress(width, i, max), escape(msg));
    }

    /**
     * Generates the internal HTML table representation of the progress bar.
     * <p>
     * This method constructs a styled HTML table with a fixed layout to visually represent the progress. It calculates the width of
     * the completed progress indicator based on the current progress and maximum value.
     *
     * @param width the base width of the progress bar in pixels
     * @param i the current progress value
     * @param max the maximum progress value
     * 
     * @return a String containing the HTML table representing the progress indicator
     */
    protected String internalProgress(final int width, final long i, final long max) {
        return String.format(
                "<table cellpadding=0 cellspacing=0 style='width:%dpx;font-size:2px;border:1px solid gray;table-layout:fixed'><tr><td style='width:%dpx;height:2px;background-color:#00ff00'></td><td></td></table>",
                width + 8, i * width / max);
    }

    /**
     * Escapes the given character sequence to make it safe for inclusion in HTML output. This method utilizes string escaping to
     * convert special characters into their corresponding HTML entities, preventing issues such as XSS attacks or improper
     * rendering when the input contains characters that have special meaning in HTML.
     *
     * @param str the character sequence to be escaped
     * 
     * @return a String representing the escaped version of the input character sequence
     */
    @Override
    public String escape(CharSequence str) {
        return StringEscapeUtils.escapeHtml4(str.toString());
    }

    /**
     * Indicates whether this renderer supports progress rendering. In this implementation, it returns true, indicating that the
     * Html4Renderer does support rendering progress bars using the progress method.
     * 
     * @return true, indicating that this renderer supports progress rendering
     */
    @Override
    public boolean hasProgress() {
        return true;
    }
}
