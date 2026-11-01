package jrm.fx.ui.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.paint.Color;
import jrm.misc.Log;
import lombok.experimental.UtilityClass;

/**
 * Converts neutral markup XML strings into JavaFX {@link Node} graphs.
 * <p>
 * Parses XML containing {@code <label>} and {@code <progress>} elements and produces
 * corresponding {@link Label} and {@link ProgressBar} nodes. Labels support color,
 * bold, and italic attributes.
 *
 * @since 2.5
 */
public @UtilityClass class NeutralToNodeFormatter {
        /**
         * SAX handler that converts neutral markup XML elements into JavaFX nodes.
         * @see NeutralToNodeFormatter#toNodes(String)
         */
    private static class Handler extends DefaultHandler {
        /** The collected nodes. */
        private final List<Node> nodes = new ArrayList<>();

        /** The current label being built. */
        private Label current = null;

        /** The text buffer. */
        private final StringBuilder buffer = new StringBuilder();

        /**
         * Handles the start of an XML element.
         *
         * @param uri the namespace URI, or an empty string
         * @param localName the local name, or an empty string
         * @param qName the qualified name
         * @param attributes the element attributes
         * @throws SAXException if a SAX error occurs
         */
        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            switch (qName) {
                case "document":
                    break;
                case "label":
                    startLabel(attributes);
                    break;
                case "progress":
                    startProgress(attributes);
                    break;
                default:
                    break;
            }
        }

        /**
         * Processes the opening of a {@code label} element, reading {@code color},
         * {@code bold}, and {@code italic} attributes and applying them to a new {@link Label}.
         *
         * @param attributes the XML attributes of the label element
         */
        private void startLabel(Attributes attributes) {
            flush();
            current = new Label();
            var bold = false;
            var italic = false;
            for (int i = 0; i < attributes.getLength(); i++) {
                switch (attributes.getQName(i)) {
                    case "color":
                        current.setTextFill(Color.web(attributes.getValue(i)));
                        break;
                    case "bold":
                        bold = Boolean.parseBoolean(attributes.getValue(i));
                        break;
                    case "italic":
                        italic = Boolean.parseBoolean(attributes.getValue(i));
                        break;
                    default:
                        break;
                }
            }
            if (bold && italic)
                current.styleProperty().bind(new SimpleStringProperty("-fx-font-weight: bold; -fx-font-style: italic;"));
            else if (bold)
                current.styleProperty().bind(new SimpleStringProperty("-fx-font-weight: bold;"));
            else if (italic)
                current.styleProperty().bind(new SimpleStringProperty("-fx-font-style: italic;"));
        }

        /**
         * Processes the opening of a {@code progress} element, reading {@code width},
         * {@code value}, and {@code max} attributes (defaulting to 100, 0, 100
         * respectively) and applying them to a new {@link ProgressBar}.
         *
         * @param attributes the XML attributes of the progress element
         */
        private void startProgress(Attributes attributes) {
            flush();
            int width = 100;
            int value = 0;
            int max = 100;
            for (int i = 0; i < attributes.getLength(); i++) {
                switch (attributes.getQName(i)) {
                    case "width":
                        width = Integer.parseInt(attributes.getValue(i));
                        break;
                    case "value":
                        value = Integer.parseInt(attributes.getValue(i));
                        break;
                    case "max":
                        max = Integer.parseInt(attributes.getValue(i));
                        break;
                    default:
                        break;
                }
            }
            final var progress = new ProgressBar();
            progress.setPrefWidth(width);
            progress.setPrefHeight(10);
            progress.setProgress((double) value / (double) max);
            nodes.add(progress);
        }

        /**
         * Handles the end of an XML element.
         *
         * @param uri the namespace URI, or an empty string
         * @param localName the local name, or an empty string
         * @param qName the qualified name
         * @throws SAXException if a SAX error occurs
         */
        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            switch (qName) // NOSONAR
            {
                case "document", "label":
                    flush();
                    break;
                default:
                    break;
            }
        }

        /**
         * Flushes buffered text into the current {@link Label} node, creating a new
         * {@link Label} if none exists, and resets the buffer.
         */
        private void flush() {
            if (!buffer.isEmpty()) {
                if (current == null)
                    current = new Label();
                current.setText(buffer.toString());
                buffer.setLength(0);
                nodes.add(current);
                current = null;
            }
        }

        /**
         * Accumulates character data from the XML document.
         *
         * @param ch the characters from the XML document
         * @param start the start position in the array
         * @param length the number of characters to read from the array
         * @throws SAXException if a SAX error occurs
         */
        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            buffer.append(ch, start, length);
        }
    }

    /**
     * Converts a neutral markup XML string into a list of JavaFX {@link Node} objects.
     *
     * @param xml the XML string to parse
     * @return a list of nodes, or a list containing a single plain {@link Label} if parsing fails
     */
    public static List<Node> toNodes(String xml) {
        if (xml == null)
            return List.of();
        if (xml.startsWith("<document>")) {
            try (final var in = new ByteArrayInputStream(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + xml).getBytes(StandardCharsets.UTF_8))) {
                final var factory = SAXParserFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                final var parser = factory.newSAXParser();
                parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, ""); // Compliant
                parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); // compliant
                final var handler = new Handler();
                parser.parse(in, handler);
                return handler.nodes;
            } catch (SAXException | ParserConfigurationException | IOException e) {
                Log.warn("Failed to parse XML: " + e.getMessage());
            }
        }
        return List.of(new Label(xml));
    }
}
