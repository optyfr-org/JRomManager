package jrm.server.shared.datasources;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import jrm.profile.filter.NPlayer;
import jrm.server.shared.datasources.XMLRequest.Operation;
import jrm.xml.SimpleAttribute;

/**
 * Handles XML responses for retrieving the list of NPlayers configurations.
 * <p>
 * This class processes incoming XML requests to fetch metadata about the NPlayers.ini file, which maps ROM sets to their supported
 * simultaneous player counts in the Retro-Gaming ROM manager. It outputs the data in a format suitable for UI list components,
 * including selection states.
 * </p>
 * <h2>XML Protocol</h2>
 * <ul>
 * <li><b>Incoming Request Parameters:</b>
 * <ul>
 * <li>No specific parameters required; relies on the current profile's NPlayers data.</li>
 * </ul>
 * </li>
 * <li><b>Outgoing Response Structure:</b>
 * 
 * <pre>
 *     <code class="language-xml">
 * &lt;response&gt;
 *   &lt;status&gt;0&lt;/status&gt;
 *   &lt;startRow&gt;0&lt;/startRow&gt;
 *   &lt;endRow&gt;N&lt;/endRow&gt;
 *   &lt;totalRows&gt;N+1&lt;/totalRows&gt;
 *   &lt;data&gt;
 *     &lt;record ID="..." Name="..." Cnt="..." isSelected="..."/&gt;
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
 * @see NPlayer
 */
public class NPlayersXMLResponse extends XMLResponse {

    /**
     * Constructs a new NPlayersXMLResponse for the given request.
     *
     * @param request The incoming XML request containing operation data.
     * 
     * @throws IOException If an I/O error occurs during response generation.
     * @throws XMLStreamException If an XML streaming error occurs.
     */
    public NPlayersXMLResponse(XMLRequest request) throws IOException, XMLStreamException {
        super(request);
    }

    /**
     * Fetches the NPlayers data and writes it to the XML response.
     * <p>
     * Iterates through the current profile's NPlayers list and writes a record for each entry, including its property name, display
     * name, player count, and selection state.
     * </p>
     *
     * @param operation The operation containing request parameters.
     * 
     * @throws XMLStreamException If an XML streaming error occurs during writing.
     */
    @Override
    protected void fetch(Operation operation) throws XMLStreamException {
        final var session = request.session;
        writer.writeStartElement("response");
        writer.writeElement("status", "0");
        writer.writeElement("startRow", "0");
        final int rowCount = session.getCurrProfile().getNplayers() == null ? 0 : request.getSession().getCurrProfile().getNplayers().getListNPlayers().size();
        writer.writeElement("endRow", Integer.toString(rowCount == 0 ? 0 : rowCount - 1));
        writer.writeElement("totalRows", Integer.toString(rowCount));
        writer.writeStartElement("data");
        if (session.getCurrProfile().getNplayers() != null) {
            for (NPlayer nplayer : session.getCurrProfile().getNplayers()) {
                writer.writeElement("record", new SimpleAttribute("ID", nplayer.getPropertyName()), new SimpleAttribute("Name", nplayer.name),
                        new SimpleAttribute("Cnt", nplayer.size()), new SimpleAttribute("isSelected", nplayer.isSelected(session.getCurrProfile())));
            }
        }
        writer.writeEndElement();
        writer.writeEndElement();
    }
}