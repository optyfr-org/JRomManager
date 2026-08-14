package jrm.aui.status;

import java.util.Optional;

import org.apache.commons.text.StringEscapeUtils;

/**
 * A neutral implementation of the StatusRenderer interface that provides basic rendering functionality without any specific styling
 * or formatting. This renderer produces simple XML-like output for documents and labels, and includes a progress element for
 * displaying progress bars. It also provides an escape method to safely handle special characters in the input strings. This
 * implementation can be used as a default or fallback renderer when no specific styling is required.
 */
public class NeutralRenderer implements StatusRenderer {

    /**
     * Constructs a new NeutralRenderer instance with default settings. This constructor does not perform any specific
     * initialization and can be used to create a basic renderer for status rendering tasks.
     */
    public NeutralRenderer() {
        /* default constructor */ }

    /**
     * Renders the given string as a document by wrapping it in a &lt;document&gt; tag. This method takes a CharSequence input and
     * returns a string that represents the input as a document in a simple XML-like format. The resulting string will have the
     * format "&lt;document&gt;input&lt;/document&gt;", where "input" is the original string provided to the method.
     * 
     * @param str the input string to be rendered as a document
     * 
     * @return a string representing the input as a document in XML-like format
     */
    @Override
    public String toDocument(CharSequence str) {
        return "<document>" + str + "</document>";
    }

    /**
     * Renders the given string without any line breaks by simply converting it to a string. This method takes a CharSequence input
     * and returns a string that represents the input without any modifications or formatting. The resulting string will be the same
     * as the original input, but converted to a standard String type.
     * 
     * @param str the input string to be rendered without line breaks
     * 
     * @return a string representing the input without any modifications or formatting
     */
    @Override
    public String toNoBR(CharSequence str) {
        return str.toString();
    }

    /**
     * Renders the given string as a label with optional styling attributes such as color, bold, and italic. This method takes a
     * CharSequence input along with optional parameters for web color, bold, and italic styling. It returns a string that
     * represents the input as a label in a simple XML-like format, including the specified styling attributes. The resulting string
     * will have the format "&lt;label color="color" bold="bold" italic="italic"&gt;input&lt;/label&gt;", where "color", "bold", and
     * "italic" are the provided styling attributes, and "input" is the original string provided to the method.
     * 
     * @param str the input string to be rendered as a label
     * @param webcolor an optional web color to be applied to the label (default is "black" if null)
     * @param bold a boolean indicating whether the label should be rendered in bold
     * @param italic a boolean indicating whether the label should be rendered in italic
     * 
     * @return a string representing the input as a label with optional styling attributes in XML-like format
     */
    @Override
    public String toLabel(CharSequence str, String webcolor, boolean bold, boolean italic) {
        return "<label color=\"%s\" bold=\"%b\" italic=\"%b\">%s</label>".formatted(Optional.ofNullable(webcolor).orElse("black"), bold, italic, escape(str));
    }

    /**
     * Renders the progress bar for the given progress parameters, returning a document with the progress bar and an optional
     * message. This method takes the width, current progress value, maximum value, and an optional message, constructs a progress
     * bar element using internalProgress, and wraps it in a document tag with the escaped message if provided.
     * 
     * @param width the width of the progress bar
     * @param i the current progress value
     * @param max the maximum value for the progress
     * @param msg an optional message to display alongside the progress bar
     * 
     * @return a string representing the progress element and message in a document format
     */
    @Override
    public String progress(int width, int i, int max, String msg) {
        if (msg == null)
            return toDocument(internalProgress(width, i, max));
        return toDocument(internalProgress(width, i, max) + " " + escape(msg));
    }

    /**
     * Generates a progress element in a simple XML-like format based on the provided width, current progress value, and maximum
     * value. This method constructs a string that represents a progress bar using the &lt;progress&gt; tag, where the width
     * attribute specifies the width of the progress bar, the value attribute indicates the current progress, and the max attribute
     * defines the maximum value for the progress. The resulting string will have the format "&lt;progress width="width" value="i"
     * max="max"&gt;&lt;/progress&gt;", where "width", "i", and "max" are replaced with the corresponding input values.
     * 
     * @param width the width of the progress bar
     * @param i the current progress value
     * @param max the maximum value for the progress
     * 
     * @return a string representing a progress element in XML-like format
     */
    protected String internalProgress(final int width, final long i, final long max) {
        return String.format("<progress width=\"%d\" value=\"%d\" max=\"%d\"></progress>", width, i, max);
    }

    /**
     * Escapes special characters in the input string to ensure that it can be safely included in XML-like output. This method uses
     * the StringEscapeUtils.escapeXml10 method from the Apache Commons Text library to perform the escaping, which converts special
     * characters such as &lt;, &gt;, &amp;, ", and ' into their corresponding XML entities. The resulting string will be safe to
     * include in XML-like output without causing parsing issues or unintended formatting.
     * 
     * @param str the input string to be escaped
     * 
     * @return a string with special characters escaped for safe inclusion in XML-like output
     */
    @Override
    public String escape(CharSequence str) {
        return StringEscapeUtils.escapeXml10(str.toString());
    }

    /**
     * Indicates that this renderer supports progress rendering by returning true. This method can be used by clients to check if
     * the renderer has the capability to render progress bars before attempting to use the progress method.
     * 
     * @return true, indicating that this renderer supports progress rendering
     */
    @Override
    public boolean hasProgress() {
        return true;
    }
}
