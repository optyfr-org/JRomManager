package jrm.aui.status;

/**
 * A simple implementation of the StatusRenderer interface that produces plain text output without any formatting or special
 * handling. This renderer is designed for use in contexts where a straightforward, unformatted representation of status messages is
 * sufficient, such as logging or console output. The PlainTextRenderer class implements all methods of the StatusRenderer interface
 * by simply returning the input string as-is, without applying any transformations or formatting. It does not support progress
 * rendering or any special label formatting, making it a basic and minimal implementation of the StatusRenderer interface.
 * 
 * @author optyfr
 */
public class PlainTextRenderer implements StatusRenderer {

    /**
     * Constructs a new PlainTextRenderer instance with default settings. This constructor does not perform any specific
     * initialization and can be used to create a renderer for plain text output.
     */
    public PlainTextRenderer() {
        /* default constructor */ }

    /**
     * Converts the given character sequence to a plain text document representation. In this implementation, it simply returns the
     * input string as-is without any modifications or formatting.
     *
     * @param str the character sequence to be converted to a document representation
     * 
     * @return the plain text representation of the input string
     */
    @Override
    public String toDocument(CharSequence str) {
        return str.toString();
    }

    /**
     * Converts the given character sequence to a plain text representation without line breaks. In this implementation, it simply
     * returns the input string as-is without any modifications or formatting.
     *
     * @param str the character sequence to be converted to a no-line-break representation
     * 
     * @return the plain text representation of the input string without line breaks
     */
    @Override
    public String toNoBR(CharSequence str) {
        return str.toString();
    }

    /**
     * Converts the given character sequence to a label representation. In this implementation, it simply returns the input string
     * as-is without any modifications or formatting, ignoring the webcolor, bold, and italic parameters.
     *
     * @param str the character sequence to be converted to a label representation
     * @param webcolor the web color associated with the label (ignored in this implementation)
     * @param bold whether the label should be bold (ignored in this implementation)
     * @param italic whether the label should be italic (ignored in this implementation)
     * 
     * @return the plain text representation of the input string as a label
     */
    @Override
    public String toLabel(CharSequence str, String webcolor, boolean bold, boolean italic) {
        return str.toString();
    }

    /**
     * Renders the progress message as a plain text representation. In this implementation, it simply returns the input message
     * as-is without any modifications or formatting, ignoring the width, current progress, and maximum progress parameters.
     *
     * @param width the width of the progress bar (ignored in this implementation)
     * @param i the current progress value (ignored in this implementation)
     * @param max the maximum progress value (ignored in this implementation)
     * @param msg the progress message to be rendered
     * 
     * @return the plain text representation of the progress message
     */
    @Override
    public String progress(int width, int i, int max, String msg) {
        return toDocument(msg == null ? "" : escape(msg));
    }

    /**
     * Escapes the given character sequence for use in plain text output. In this implementation, it simply returns the input string
     * as-is without any modifications or escaping.
     *
     * @param str the character sequence to be escaped
     * 
     * @return the escaped representation of the input string (same as input in this implementation)
     */
    @Override
    public String escape(CharSequence str) {
        return str.toString();
    }

    /**
     * Indicates whether this renderer supports progress rendering. In this implementation, it returns false, indicating that
     * progress rendering is not supported.
     *
     * @return false, indicating that progress rendering is not supported by this renderer
     */
    @Override
    public boolean hasProgress() {
        return false;
    }

}
