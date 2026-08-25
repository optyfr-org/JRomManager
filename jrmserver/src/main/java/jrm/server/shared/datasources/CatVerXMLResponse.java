package jrm.server.shared.datasources;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import jrm.profile.filter.CatVer;
import jrm.profile.filter.CatVer.Category;
import jrm.profile.filter.CatVer.Category.SubCategory;
import jrm.server.shared.datasources.XMLRequest.Operation;

/**
 * Handles XML responses for retrieving Category and Version (CatVer) classification data.
 * <p>
 * This class processes incoming XML requests to fetch a hierarchical tree structure of categories and sub-categories used for
 * filtering ROMs in the Retro-Gaming ROM manager. It outputs the data in a format suitable for UI tree components, including
 * selection states and folder indicators.
 * </p>
 * <h2>XML Protocol</h2>
 * <ul>
 * <li><b>Incoming Request Parameters:</b>
 * <ul>
 * <li>No specific parameters required; relies on the current profile's CatVer data.</li>
 * </ul>
 * </li>
 * <li><b>Outgoing Response Structure:</b>
 * 
 * <pre>
 * <code class="language-xml">
 * &lt;response&gt;
 *   &lt;status&gt;0&lt;/status&gt;
 *   &lt;startRow&gt;0&lt;/startRow&gt;
 *   &lt;endRow&gt;N&lt;/endRow&gt;
 *   &lt;totalRows&gt;N+1&lt;/totalRows&gt;
 *   &lt;data&gt;
 *     &lt;record ID="..." Name="..." ParentID="1" isFolder="..." isSelected="..." isOpen="true"/&gt;
 *     &lt;!-- Category records --&gt;
 *     &lt;record ID="..." Name="..." ParentID="..." isFolder="..." isSelected="..." isOpen="..."/&gt;
 *     &lt;!-- SubCategory records --&gt;
 *     &lt;record ID="..." Name="..." ParentID="..." Cnt="..." isFolder="false" isSelected="..."/&gt;
 *   &lt;/data&gt;
 * &lt;/response&gt;
 *     </code>
 * </pre>
 * 
 * </li>
 * </ul>
 *
 * @author JRomManager Team
 * 
 * @see XMLResponse
 * @see XMLRequest.Operation
 * @see CatVer
 */
public class CatVerXMLResponse extends XMLResponse {

    /**
     * XML attribute name for the selection state.
     */
    private static final String IS_SELECTED = "isSelected";

    /**
     * XML attribute name indicating if the node represents a folder (has children).
     */
    private static final String IS_FOLDER = "isFolder";

    /**
     * XML attribute name for the parent node's ID.
     */
    private static final String PARENT_ID = "ParentID";

    /**
     * XML element name for individual data records.
     */
    private static final String RECORD = "record";

    /**
     * Constructs a new CatVerXMLResponse for the given request.
     *
     * @param request The incoming XML request containing operation data.
     * 
     * @throws IOException If an I/O error occurs during response generation.
     * @throws XMLStreamException If an XML streaming error occurs.
     */
    public CatVerXMLResponse(XMLRequest request) throws IOException, XMLStreamException {
        super(request);
    }

    /**
     * Counts the total number of nodes (root, categories, and sub-categories) in the CatVer hierarchy.
     *
     * @param catver The CatVer object to count nodes for.
     * 
     * @return The total number of nodes.
     */
    private int countNode(CatVer catver) {
        var count = 0;
        if (catver != null) {
            count++;
            for (final var cat : catver.getListCategories()) {
                count++;
                count += cat.getListSubCategories().size();
            }
        }
        return count;
    }

    /**
     * Outputs the entire CatVer hierarchy to the XML writer.
     *
     * @param writer The XMLStreamWriter to write to.
     * @param catver The CatVer object containing the hierarchy.
     * 
     * @throws XMLStreamException If an XML streaming error occurs.
     */
    private void outputNode(XMLStreamWriter writer, CatVer catver) throws XMLStreamException {
        if (catver != null) {
            writeRootNode(writer, catver);
            for (Category cat : catver) {
                writeCatNode(writer, catver, cat);
                for (SubCategory subcat : cat) {
                    writeSubCatNode(writer, cat, subcat);
                }
            }
        }
    }

    /**
     * Writes a single SubCategory node to the XML response.
     *
     * @param writer The XMLStreamWriter to write to.
     * @param cat The parent Category.
     * @param subcat The SubCategory to write.
     * 
     * @throws XMLStreamException If an XML streaming error occurs.
     */
    private void writeSubCatNode(XMLStreamWriter writer, Category cat, SubCategory subcat) throws XMLStreamException {
        writer.writeStartElement(RECORD);
        writer.writeAttribute("ID", subcat.getPropertyName());
        writer.writeAttribute("Name", subcat.name);
        writer.writeAttribute(PARENT_ID, cat.getPropertyName());
        writer.writeAttribute("Cnt", Integer.toString(subcat.size()));
        writer.writeAttribute(IS_FOLDER, Boolean.toString(false));
        writer.writeAttribute(IS_SELECTED, Boolean.toString(subcat.isSelected()));
        writer.writeEndElement();
    }

    /**
     * Writes a single Category node to the XML response.
     * <p>
     * Calculates the {@code isOpen} state based on whether the sub-categories have mixed or uniform selection states.
     * </p>
     *
     * @param writer The XMLStreamWriter to write to.
     * @param catver The parent CatVer object.
     * @param cat The Category to write.
     * 
     * @throws XMLStreamException If an XML streaming error occurs.
     */
    private void writeCatNode(XMLStreamWriter writer, CatVer catver, Category cat) throws XMLStreamException {
        writer.writeStartElement(RECORD);
        writer.writeAttribute("ID", cat.getPropertyName());
        writer.writeAttribute("Name", cat.name);
        writer.writeAttribute(PARENT_ID, catver.getPropertyName());
        writer.writeAttribute(IS_FOLDER, Boolean.toString(!cat.getListSubCategories().isEmpty()));
        writer.writeAttribute(IS_SELECTED, Boolean.toString(cat.isSelected()));
        byte isOpen = 0;
        for (SubCategory subcat : cat) {
            isOpen |= (subcat.isSelected() ? 0x1 : 0x0);
            isOpen |= (!subcat.isSelected() ? 0x2 : 0x0);
        }
        writer.writeAttribute("isOpen", Boolean.toString(isOpen == 3));
        writer.writeEndElement();
    }

    /**
     * Writes the root node of the CatVer hierarchy to the XML response.
     *
     * @param writer The XMLStreamWriter to write to.
     * @param catver The CatVer object representing the root.
     * 
     * @throws XMLStreamException If an XML streaming error occurs.
     */
    private void writeRootNode(XMLStreamWriter writer, CatVer catver) throws XMLStreamException {
        writer.writeStartElement(RECORD);
        writer.writeAttribute("ID", catver.getPropertyName());
        writer.writeAttribute("Name", request.session.getMsgs().getString("CatVer.AllCategories"));
        writer.writeAttribute(PARENT_ID, "1");
        writer.writeAttribute(IS_FOLDER, Boolean.toString(!catver.getListCategories().isEmpty()));
        writer.writeAttribute(IS_SELECTED, Boolean.toString(catver.isSelected()));
        writer.writeAttribute("isOpen", Boolean.TRUE.toString());
        writer.writeEndElement();
    }

    /**
     * Fetches the CatVer data and writes it to the XML response.
     *
     * @param operation The operation containing request parameters.
     * 
     * @throws XMLStreamException If an XML streaming error occurs during writing.
     */
    @Override
    protected void fetch(Operation operation) throws XMLStreamException {
        int nodecount = countNode(request.session.getCurrProfile().getCatver());
        writer.writeStartElement("response");
        writer.writeElement("status", "0");
        writer.writeElement("startRow", "0");
        writer.writeElement("endRow", Integer.toString(nodecount - 1));
        writer.writeElement("totalRows", Integer.toString(nodecount));
        writer.writeStartElement("data");
        outputNode(writer, request.session.getCurrProfile().getCatver());
        writer.writeEndElement();
        writer.writeEndElement();
    }
}