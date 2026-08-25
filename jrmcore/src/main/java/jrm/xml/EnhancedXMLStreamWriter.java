/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.xml;

import java.util.ArrayDeque;
import java.util.Deque;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * A highly configurable wrapper implementing {@link XMLStreamWriter} that provides automated formatting, indentation, and line
 * returns for output XML documents.
 * <p>
 * The writer keeps track of the document's state structure to determine when to add indentation and carriage returns automatically,
 * preventing developers from manually having to write structural spaces and newlines.
 * <p>
 * <b>State Machine Design:</b> This wrapper acts as a structural state-machine with three states:
 * <ul>
 * <li>{@link Seen#NOTHING}: The initial state, or when entering a fresh element level.</li>
 * <li>{@link Seen#ELEMENT}: Written when a start element or an empty element is output.</li>
 * <li>{@link Seen#DATA}: Written when character data, CDATA blocks, or text are output.</li>
 * </ul>
 * Whenever elements are nested, the previous states are preserved on an internal stack so that end element tags are indented
 * symmetrically.
 *
 * @author optyfr
 * 
 * @since 1.0
 */
public final class EnhancedXMLStreamWriter implements XMLStreamWriter {

    /**
     * Internal enumeration used to track the structural state of the written XML document to decide on proper indentation and
     * formatting rules.
     */
    private enum Seen {

        /**
         * Indicates no elements have been written yet, or we are at the initial state.
         */
        NOTHING,

        /**
         * Indicates an element start tag or empty element tag was just written.
         */
        ELEMENT,

        /**
         * Indicates text characters or CDATA blocks have been written.
         */
        DATA;
    }

    /**
     * The underlying XMLStreamWriter instance to which all calls are delegated.
     */
    private final XMLStreamWriter writer;

    /**
     * The indentation string used per indent level (e.g., "\t" or spaces). If null, no automatic indentation or line returns are
     * applied.
     */
    private final String indentStep;

    /**
     * A stack storing the historical structural states to accurately handle end elements.
     */
    private final Deque<EnhancedXMLStreamWriter.Seen> stateStack = new ArrayDeque<>();

    /**
     * The current structural state of the writer.
     */
    private EnhancedXMLStreamWriter.Seen state = Seen.NOTHING;

    /**
     * The current nesting depth of XML elements in the document.
     */
    private int depth = 0;

    /**
     * Constructs a new EnhancedXMLStreamWriter that wraps the given writer with a default tab character ("\t") used as the
     * indentation step.
     *
     * @param writer the underlying {@link XMLStreamWriter} to wrap, must not be null
     * 
     * @throws NullPointerException if the {@code writer} parameter is null
     */
    public EnhancedXMLStreamWriter(final XMLStreamWriter writer) {
        this(writer, "\t"); //$NON-NLS-1$
    }

    /**
     * Constructs a new EnhancedXMLStreamWriter wrapping the given writer with a custom indentation step string.
     *
     * @param writer the underlying {@link XMLStreamWriter} to wrap, must not be null
     * @param indentStep the string used for one level of indentation (e.g., spaces or tab), or {@code null} to disable indentation
     *        and line breaks
     * 
     * @throws NullPointerException if the {@code writer} parameter is null
     */
    public EnhancedXMLStreamWriter(final XMLStreamWriter writer, final String indentStep) {
        if (writer == null) {
            throw new NullPointerException("The underlying XMLStreamWriter must not be null.");
        }
        this.writer = writer;
        this.indentStep = indentStep;
    }

    /**
     * Closes this writer and frees any resources associated with it. It also closes the underlying writer.
     *
     * @throws XMLStreamException if an error occurs while closing the stream
     */
    @Override
    public void close() throws XMLStreamException {
        writer.close();
    }

    /**
     * Flushes any cached data to the underlying output stream.
     *
     * @throws XMLStreamException if an error occurs during flushing
     */
    @Override
    public void flush() throws XMLStreamException {
        writer.flush();
    }

    /**
     * Returns the namespace context of the underlying writer.
     *
     * @return the current {@link NamespaceContext}
     */
    @Override
    public NamespaceContext getNamespaceContext() {
        return writer.getNamespaceContext();
    }

    /**
     * Gets the prefix associated with the given URI from the underlying writer.
     *
     * @param uri the URI to lookup, must not be null
     * 
     * @return the associated prefix string, or null if not found
     * 
     * @throws XMLStreamException if an error occurs during lookup
     * @throws NullPointerException if the {@code uri} is null
     */
    @Override
    public String getPrefix(final String uri) throws XMLStreamException {
        return writer.getPrefix(uri);
    }

    /**
     * Gets the value of a feature/property from the underlying writer.
     *
     * @param name the name of the property, must not be null
     * 
     * @return the value of the property
     * 
     * @throws IllegalArgumentException if the property is not supported
     * @throws NullPointerException if the {@code name} is null
     */
    @Override
    public Object getProperty(final String name) throws IllegalArgumentException {
        return writer.getProperty(name);
    }

    /**
     * Sets the default namespace URI in the underlying writer.
     *
     * @param uri the default namespace URI to set, must not be null
     * 
     * @throws XMLStreamException if an error occurs while setting the namespace
     * @throws NullPointerException if the {@code uri} is null
     */
    @Override
    public void setDefaultNamespace(final String uri) throws XMLStreamException {
        writer.setDefaultNamespace(uri);
    }

    /**
     * Sets the current namespace context for prefix resolution in the underlying writer.
     *
     * @param context the namespace context to set, must not be null
     * 
     * @throws XMLStreamException if an error occurs while setting the context
     * @throws NullPointerException if the {@code context} is null
     */
    @Override
    public void setNamespaceContext(final NamespaceContext context) throws XMLStreamException {
        writer.setNamespaceContext(context);
    }

    /**
     * Binds the given prefix to the specified namespace URI in the underlying writer.
     *
     * @param prefix the prefix to bind, must not be null
     * @param uri the namespace URI to bind to, must not be null
     * 
     * @throws XMLStreamException if an error occurs during binding
     * @throws NullPointerException if the {@code prefix} or {@code uri} is null
     */
    @Override
    public void setPrefix(final String prefix, final String uri) throws XMLStreamException {
        writer.setPrefix(prefix, uri);
    }

    /**
     * Writes an XML attribute. The attribute is omitted if the value is null.
     *
     * @param localName the local name of the attribute, must not be null
     * @param value the value of the attribute, or null (in which case nothing is written)
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if the {@code localName} is null and value is non-null
     */
    @Override
    public void writeAttribute(final String localName, final String value) throws XMLStreamException {
        if (value != null)
            writer.writeAttribute(localName, value);
    }

    /**
     * Writes an XML attribute with prefix and namespace URI. The attribute is omitted if the value is null.
     *
     * @param prefix the prefix of the attribute, must not be null
     * @param namespaceURI the namespace URI of the attribute, must not be null
     * @param localName the local name of the attribute, must not be null
     * @param value the value of the attribute, or null (in which case nothing is written)
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if any of the non-null parameters is null and value is non-null
     */
    @Override
    public void writeAttribute(final String prefix, final String namespaceURI, final String localName, final String value) throws XMLStreamException {
        if (value != null)
            writer.writeAttribute(prefix, namespaceURI, localName, value);
    }

    /**
     * Writes an XML attribute with namespace URI. The attribute is omitted if the value is null.
     *
     * @param namespaceURI the namespace URI of the attribute, must not be null
     * @param localName the local name of the attribute, must not be null
     * @param value the value of the attribute, or null (in which case nothing is written)
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if any of the non-null parameters is null and value is non-null
     */
    @Override
    public void writeAttribute(final String namespaceURI, final String localName, final String value) throws XMLStreamException {
        if (value != null)
            writer.writeAttribute(namespaceURI, localName, value);
    }

    /**
     * Writes an XML CDATA section block, setting the internal state to {@link Seen#DATA}.
     *
     * @param data the CDATA contents to write, must not be null
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if the {@code data} is null
     */
    @Override
    public void writeCData(final String data) throws XMLStreamException {
        state = Seen.DATA;
        writer.writeCData(data);
    }

    /**
     * Writes XML text characters, setting the internal state to {@link Seen#DATA}.
     *
     * @param text the text characters to write, must not be null
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if the {@code text} is null
     */
    @Override
    public void writeCharacters(final String text) throws XMLStreamException {
        state = Seen.DATA;
        writer.writeCharacters(text);
    }

    /**
     * Writes XML text characters from a buffer, setting the internal state to {@link Seen#DATA}.
     *
     * @param text the character buffer
     * @param start the start offset in the buffer
     * @param len the number of characters to write
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if the {@code text} buffer is null
     */
    @Override
    public void writeCharacters(final char[] text, final int start, final int len) throws XMLStreamException {
        state = Seen.DATA;
        writer.writeCharacters(text, start, len);
    }

    /**
     * Writes an XML comment.
     *
     * @param data the comment data to write, must not be null
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if the {@code data} is null
     */
    @Override
    public void writeComment(final String data) throws XMLStreamException {
        writer.writeComment(data);
    }

    /**
     * Writes an XML DTD.
     *
     * @param dtd the DTD block to write
     * 
     * @throws XMLStreamException if an error occurs during writing
     */
    @Override
    public void writeDTD(final String dtd) throws XMLStreamException {
        writer.writeDTD(dtd);
    }

    /**
     * Writes the default namespace URI.
     *
     * @param namespaceURI the default namespace URI to write, must not be null
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if the {@code namespaceURI} is null
     */
    @Override
    public void writeDefaultNamespace(final String namespaceURI) throws XMLStreamException {
        writer.writeDefaultNamespace(namespaceURI);
    }

    /**
     * Writes an empty XML element with a namespace URI, adjusting internal formatting.
     * <p>
     * Transitions the internal state machine to {@link Seen#ELEMENT} and outputs formatting if the current depth is greater than
     * zero.
     * </p>
     *
     * @param namespaceURI the namespace URI, must not be null
     * @param localName the local name of the element, must not be null
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if either parameter is null
     */
    @Override
    public void writeEmptyElement(final String namespaceURI, final String localName) throws XMLStreamException {
        onEmptyElement();
        writer.writeEmptyElement(namespaceURI, localName);
    }

    /**
     * Writes an empty XML element with prefix and namespace URI, adjusting internal formatting.
     * <p>
     * Transitions the internal state machine to {@link Seen#ELEMENT} and outputs formatting if the current depth is greater than
     * zero.
     * </p>
     *
     * @param prefix the prefix string, must not be null
     * @param localName the local name of the element, must not be null
     * @param namespaceURI the namespace URI, must not be null
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if any of the parameters is null
     */
    @Override
    public void writeEmptyElement(final String prefix, final String localName, final String namespaceURI) throws XMLStreamException {
        onEmptyElement();
        writer.writeEmptyElement(prefix, localName, namespaceURI);
    }

    /**
     * Writes an empty XML element with the specified local name, adjusting internal formatting.
     * <p>
     * Transitions the internal state machine to {@link Seen#ELEMENT} and outputs formatting if the current depth is greater than
     * zero.
     * </p>
     *
     * @param localName the local name of the element, must not be null
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if the {@code localName} is null
     */
    @Override
    public void writeEmptyElement(final String localName) throws XMLStreamException {
        onEmptyElement();
        writer.writeEmptyElement(localName);
    }

    /**
     * Closes any open tags and writes the end of the document.
     *
     * @throws XMLStreamException if an error occurs during writing
     */
    @Override
    public void writeEndDocument() throws XMLStreamException {
        writer.writeEndDocument();
    }

    /**
     * Writes an XML end tag, decrementing depth and managing closing tag indentation.
     * <p>
     * Restores the previous structural state from {@link #stateStack}. If formatting is active and the last written element was not
     * data-based, a newline and the aligned indents are output before closing the tag.
     * </p>
     *
     * @throws XMLStreamException if an error occurs during writing
     */
    @Override
    public void writeEndElement() throws XMLStreamException {
        onEndElement();
        writer.writeEndElement();
    }

    /**
     * Writes an XML entity reference.
     *
     * @param name the name of the entity reference, must not be null
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if the {@code name} is null
     */
    @Override
    public void writeEntityRef(final String name) throws XMLStreamException {
        writer.writeEntityRef(name);
    }

    /**
     * Writes an XML namespace declaration.
     *
     * @param prefix the prefix to write, must not be null
     * @param namespaceURI the namespace URI, must not be null
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if either parameter is null
     */
    @Override
    public void writeNamespace(final String prefix, final String namespaceURI) throws XMLStreamException {
        writer.writeNamespace(prefix, namespaceURI);
    }

    /**
     * Writes a processing instruction target.
     *
     * @param target the target of the processing instruction, must not be null
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if the {@code target} is null
     */
    @Override
    public void writeProcessingInstruction(final String target) throws XMLStreamException {
        writer.writeProcessingInstruction(target);
    }

    /**
     * Writes a processing instruction target and data.
     *
     * @param target the target of the processing instruction, must not be null
     * @param data the data of the processing instruction, must not be null
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if either parameter is null
     */
    @Override
    public void writeProcessingInstruction(final String target, final String data) throws XMLStreamException {
        writer.writeProcessingInstruction(target, data);
    }

    /**
     * Writes the XML document declaration, defaulting to version 1.0, and initiates a newline.
     *
     * @throws XMLStreamException if an error occurs during writing
     */
    @Override
    public void writeStartDocument() throws XMLStreamException {
        writer.writeStartDocument();

        if (indentStep != null) {
            doNewline();
        }
    }

    /**
     * Writes the XML document declaration with the specified version, and initiates a newline.
     *
     * @param version the XML version string, must not be null
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if the {@code version} is null
     */
    @Override
    public void writeStartDocument(final String version) throws XMLStreamException {
        writer.writeStartDocument(version);

        if (indentStep != null) {
            doNewline();
        }
    }

    /**
     * Writes the XML document declaration with specified encoding and version, and initiates a newline.
     *
     * @param encoding the encoding string (e.g., "UTF-8"), must not be null
     * @param version the XML version string, must not be null
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if either parameter is null
     */
    @Override
    public void writeStartDocument(final String encoding, final String version) throws XMLStreamException {
        writer.writeStartDocument(encoding, version);

        if (indentStep != null) {
            doNewline();
        }
    }

    /**
     * Writes a start element tag for the specified local name, adjusting depth and line spacing.
     * <p>
     * Pushes {@link Seen#ELEMENT} onto the {@link #stateStack} to track historical hierarchy, transitions current state to
     * {@link Seen#NOTHING}, and increments {@link #depth}.
     * </p>
     *
     * @param localName the local name of the element, must not be null
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if the {@code localName} is null
     */
    @Override
    public void writeStartElement(final String localName) throws XMLStreamException {
        onStartElement();
        writer.writeStartElement(localName);
    }

    /**
     * Writes a start element tag with namespace URI, adjusting depth and line spacing.
     * <p>
     * Pushes {@link Seen#ELEMENT} onto the {@link #stateStack} to track historical hierarchy, transitions current state to
     * {@link Seen#NOTHING}, and increments {@link #depth}.
     * </p>
     *
     * @param namespaceURI the namespace URI of the element, must not be null
     * @param localName the local name of the element, must not be null
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if either parameter is null
     */
    @Override
    public void writeStartElement(final String namespaceURI, final String localName) throws XMLStreamException {
        onStartElement();
        writer.writeStartElement(namespaceURI, localName);
    }

    /**
     * Writes a start element tag with prefix, local name, and namespace URI, adjusting depth and line spacing.
     * <p>
     * Pushes {@link Seen#ELEMENT} onto the {@link #stateStack} to track historical hierarchy, transitions current state to
     * {@link Seen#NOTHING}, and increments {@link #depth}.
     * </p>
     *
     * @param prefix the prefix string, must not be null
     * @param localName the local name of the element, must not be null
     * @param namespaceURI the namespace URI of the element, must not be null
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if any of the parameters is null
     */
    @Override
    public void writeStartElement(final String prefix, final String localName, final String namespaceURI) throws XMLStreamException {
        onStartElement();
        writer.writeStartElement(prefix, localName, namespaceURI);
    }

    /**
     * Generates structural spaces corresponding to the current element nesting depth.
     *
     * @throws XMLStreamException if an error occurs while writing the indentation characters
     */
    private void doIndent() throws XMLStreamException {
        if (indentStep != null) {
            for (int i = 0; i < depth; i++) {
                writer.writeCharacters(indentStep);
            }
        }
    }

    /**
     * Generates a newline/carriage return character in the XML output.
     *
     * @throws XMLStreamException if an error occurs while writing the newline character
     */
    private void doNewline() throws XMLStreamException {
        writer.writeCharacters("\n"); //$NON-NLS-1$
    }

    /**
     * Handles the state transitions, newlines, and indentation required when writing an empty element.
     *
     * @throws XMLStreamException if an error occurs while writing structural formatting
     */
    private void onEmptyElement() throws XMLStreamException {
        state = Seen.ELEMENT;

        if ((indentStep != null) && (depth > 0)) {
            doNewline();
            doIndent();
        }
    }

    /**
     * Handles the state transitions, depth reduction, and indentation when writing an end tag. If the element had no sub-elements
     * or inner text, indentation is kept on the same line where appropriate, otherwise a structured newline with adjusted
     * indentation is written.
     *
     * @throws XMLStreamException if an error occurs while writing structural formatting
     */
    private void onEndElement() throws XMLStreamException {
        depth--;

        if ((indentStep != null) && (state == Seen.ELEMENT)) {
            doNewline();
            doIndent();
        }

        state = stateStack.removeFirst();
    }

    /**
     * Handles the state transitions, depth increments, newlines, and indentation when writing a start tag.
     *
     * @throws XMLStreamException if an error occurs while writing structural formatting
     */
    private void onStartElement() throws XMLStreamException {
        stateStack.addFirst(Seen.ELEMENT);
        state = Seen.NOTHING;

        if ((indentStep != null) && (depth > 0)) {
            doNewline();
            doIndent();
        }

        depth++;
    }

    /**
     * Writes a self-closing element with the specified local name and a set of simple attributes. Any attribute with a null name,
     * empty name, or null value is skipped automatically.
     *
     * @param localName the local name of the element, must not be null
     * @param attributes a varargs list of {@link SimpleAttribute} instances to write with the element
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if {@code localName} is null
     */
    public void writeElement(final String localName, final SimpleAttribute... attributes) throws XMLStreamException {
        writeEmptyElement(localName);
        if (attributes != null)
            for (final SimpleAttribute attr : attributes)
                if (attr.name != null && !attr.name.isEmpty() && attr.value != null)
                    writeAttribute(attr.name, attr.value.toString());
    }

    /**
     * Starts an XML element with the specified local name and associates it with a set of simple attributes. Any attribute with a
     * null name, empty name, or null value is skipped automatically.
     *
     * @param localName the local name of the element, must not be null
     * @param attributes a varargs list of {@link SimpleAttribute} instances to write on the element
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if {@code localName} is null
     */
    public void writeStartElement(final String localName, final SimpleAttribute... attributes) throws XMLStreamException {
        writeStartElement(localName);
        if (attributes != null)
            for (final SimpleAttribute attr : attributes)
                if (attr.name != null && !attr.name.isEmpty() && attr.value != null)
                    writeAttribute(attr.name, attr.value.toString());
    }

    /**
     * Writes a complete XML element containing both child text data and a set of simple attributes. Any attribute with a null name,
     * empty name, or null value is skipped automatically.
     *
     * @param localName the local name of the element, must not be null
     * @param text the text content of the element, can be null (in which case only start and end tags are written)
     * @param attributes a varargs list of {@link SimpleAttribute} instances to write on the element
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if {@code localName} is null
     */
    public void writeElement(final String localName, final CharSequence text, final SimpleAttribute... attributes) throws XMLStreamException {
        writeStartElement(localName, attributes);
        if (text != null)
            writeCharacters(text.toString());
        writeEndElement();
    }

    /**
     * Writes an XML attribute key-value pair, validating the content first. If the value is null, the attribute is not written. If
     * the value is an instance of {@link String}, it is written directly; otherwise, its {@code toString()} representation is
     * written.
     *
     * @param code the local name or code of the attribute to write; must not be null
     * @param value the value associated with the attribute, which can be null, a String, or any Object
     * 
     * @throws XMLStreamException if an error occurs during writing
     * @throws NullPointerException if {@code code} is null and value is non-null
     */
    public void write(String code, Object value) throws XMLStreamException {
        if (value == null)
            return;
        writer.writeAttribute(code, value.toString());
    }

}
