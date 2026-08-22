package jrm.server.shared.datasources;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import jrm.misc.Log;
import jrm.profile.data.Anyware;
import jrm.profile.data.AnywareList;
import jrm.profile.data.Machine;
import jrm.profile.data.MachineList;
import jrm.server.shared.datasources.XMLRequest.Operation;
import jrm.server.shared.datasources.XMLRequest.Operation.Sorter;

/**
 * Handles XML responses for retrieving, updating, and managing lists of Anyware items (e.g., Machines, Software).
 * <p>
 * This class processes incoming XML requests to fetch detailed metadata about ROMs or software items within a specific list. It
 * supports advanced filtering (by name, description, clone/rom/sample relationships, status, and selection state), sorting, and
 * bulk selection operations (select all, select none, invert selection, find).
 * </p>
 * <h2>XML Protocol</h2>
 * <ul>
 * <li><b>Incoming Request Parameters (fetch):</b>
 * <ul>
 * <li>{@code list}: The name of the list to query (e.g., "MAME", or "*" for the main MachineList).</li>
 * <li>{@code status}: (Optional) Comma-separated list of status strings to filter items.</li>
 * <li>{@code name}: (Optional) Substring to filter by item name.</li>
 * <li>{@code description}: (Optional) Substring to filter by item description.</li>
 * <li>{@code cloneof}: (Optional) Substring to filter by clone parent name.</li>
 * <li>{@code romof}: (Optional) Substring to filter by ROM parent name.</li>
 * <li>{@code sampleof}: (Optional) Substring to filter by sample parent name.</li>
 * <li>{@code selected}: (Optional) Boolean to filter by selection state.</li>
 * <li>{@code reset}: (Optional) Boolean flag indicating whether to reset the cache before fetching.</li>
 * </ul>
 * </li>
 * <li><b>Incoming Request Parameters (update):</b>
 * <ul>
 * <li>{@code list}: The name of the list.</li>
 * <li>{@code name}: The name of the specific item to update.</li>
 * <li>{@code selected}: Boolean indicating the new selection state.</li>
 * </ul>
 * </li>
 * <li><b>Incoming Request Parameters (custom operations):</b>
 * <ul>
 * <li>{@code operationId}: "find", "selectNone", "selectAll", or "selectInvert".</li>
 * <li>{@code find}: (For "find" operation) The exact name of the item to locate.</li>
 * <li>{@code list} and filtering parameters (same as fetch) to scope the operation.</li>
 * </ul>
 * </li>
 * <li><b>Outgoing Response Structure:</b>
 * 
 * <pre>
 * <code class="language-xml">
 * &lt;response&gt;
 *   &lt;status&gt;0&lt;/status&gt;
 *   &lt;!-- For fetch: Multiple record elements --&gt;
 *   &lt;record list="..." status="..." name="..." description="..." have="x/y" type="..." cloneof="..." cloneof_status="..." romof="..." romof_status="..." sampleof="..." sampleof_status="..." selected="..."/&gt;
 *   &lt;!-- For find: &lt;found&gt;index&lt;/found&gt; --&gt;
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
 */
public class AnywareListXMLResponse extends XMLResponse {

    private static final String RESPONSE = "response";
    private static final String NAME = "name";
    private static final String SELECTED = "selected";
    private static final String SAMPLEOF = "sampleof";
    private static final String ROMOF = "romof";
    private static final String CLONEOF = "cloneof";
    private static final String DESCRIPTION = "description";
    private static final String STATUS = "status";

    /**
     * Constructs a new AnywareListXMLResponse for the given request.
     *
     * @param request The incoming XML request containing operation data.
     * 
     * @throws IOException If an I/O error occurs during response generation.
     * @throws XMLStreamException If an XML streaming error occurs.
     */
    public AnywareListXMLResponse(XMLRequest request) throws IOException, XMLStreamException {
        super(request);
    }

    /**
     * Retrieves the target AnywareList based on the operation parameters.
     *
     * @param operation The operation containing the "list" parameter.
     * 
     * @return The matching AnywareList, or null if not found or not specified.
     */
    private AnywareList<? extends Anyware> getList(Operation operation) {
        String list = operation.getData("list");
        final AnywareList<?> al;
        if (list == null)
            al = null;
        else if (list.equals("*"))
            al = request.session.getCurrProfile().getMachineListList().get(0);
        else
            al = request.session.getCurrProfile().getMachineListList().getSoftwareListList().getByName(list);
        return al;
    }

    /**
     * Creates a Predicate to filter Anyware items based on operation parameters.
     *
     * @param operation The operation containing filter criteria (status, name, description, cloneof, romof, sampleof, selected).
     * 
     * @return A Predicate that evaluates to true if the item matches all specified criteria.
     */
    private Predicate<Anyware> getFilter(Operation operation) {
        final Set<String> lstatus = operation.hasData(STATUS) ? Stream.of(operation.getData(STATUS).split(",")).collect(Collectors.toSet()) : null;
        final String lname = operation.hasData(NAME) ? operation.getData(NAME).toLowerCase() : null;
        final String ldesc = operation.hasData(DESCRIPTION) ? operation.getData(DESCRIPTION).toLowerCase() : null;
        final String lcloneof = operation.hasData(CLONEOF) ? operation.getData(CLONEOF).toLowerCase() : null;
        final String lromof = operation.hasData(ROMOF) ? operation.getData(ROMOF).toLowerCase() : null;
        final String lsampleof = operation.hasData(SAMPLEOF) ? operation.getData(SAMPLEOF).toLowerCase() : null;
        final Boolean lselected = operation.hasData(SELECTED) ? Boolean.valueOf(operation.getData(SELECTED)) : null;
        return ware -> !filterStatus(lstatus, ware) && !filterSelected(lselected, ware) && !filterName(lname, ware) && !filterDesc(ldesc, ware) && !filterCloneOf(lcloneof, ware)
                && !filterRomOf(lromof, ware) && !filterSampleOf(lsampleof, ware);
    }

    /**
     * Filters an Anyware item based on its sample parent relationship.
     *
     * @param lsampleof The lowercase substring to match against the sample parent name.
     * @param ware The Anyware item to evaluate.
     * 
     * @return True if the item should be excluded from the results, false otherwise.
     */
    private boolean filterSampleOf(final String lsampleof, Anyware ware) {
        return lsampleof != null && ware instanceof Machine m && (m.getSampleof() == null || !m.getSampleof().toLowerCase().contains(lsampleof));
    }

    /**
     * Filters an Anyware item based on its ROM parent relationship.
     *
     * @param lromof The lowercase substring to match against the ROM parent name.
     * @param ware The Anyware item to evaluate.
     * 
     * @return True if the item should be excluded from the results, false otherwise.
     */
    private boolean filterRomOf(final String lromof, Anyware ware) {
        return lromof != null && ware instanceof Machine m && (m.getRomof() == null || !m.getRomof().toLowerCase().contains(lromof));
    }

    /**
     * Filters an Anyware item based on its clone parent relationship.
     *
     * @param lcloneof The lowercase substring to match against the clone parent name.
     * @param ware The Anyware item to evaluate.
     * 
     * @return True if the item should be excluded from the results, false otherwise.
     */
    private boolean filterCloneOf(final String lcloneof, Anyware ware) {
        return lcloneof != null && (ware.getCloneof() == null || !ware.getCloneof().toLowerCase().contains(lcloneof));
    }

    /**
     * Filters an Anyware item based on its description.
     *
     * @param ldesc The lowercase substring to match against the item description.
     * @param ware The Anyware item to evaluate.
     * 
     * @return True if the item should be excluded from the results, false otherwise.
     */
    private boolean filterDesc(final String ldesc, Anyware ware) {
        return ldesc != null && !ware.description.toString().toLowerCase().contains(ldesc);
    }

    /**
     * Filters an Anyware item based on its base name.
     *
     * @param lname The lowercase substring to match against the item base name.
     * @param ware The Anyware item to evaluate.
     * 
     * @return True if the item should be excluded from the results, false otherwise.
     */
    private boolean filterName(final String lname, Anyware ware) {
        return lname != null && !ware.getBaseName().toLowerCase().contains(lname);
    }

    /**
     * Filters an Anyware item based on its selection state.
     *
     * @param lselected The expected selection state (true or false).
     * @param ware The Anyware item to evaluate.
     * 
     * @return True if the item should be excluded from the results, false otherwise.
     */
    private boolean filterSelected(final Boolean lselected, Anyware ware) {
        return lselected != null && ware.isSelected() != lselected;
    }

    /**
     * Filters an Anyware item based on its status.
     *
     * @param lstatus The set of acceptable status strings.
     * @param ware The Anyware item to evaluate.
     * 
     * @return True if the item should be excluded from the results, false otherwise.
     */
    private boolean filterStatus(final Set<String> lstatus, Anyware ware) {
        return lstatus != null && !lstatus.contains(ware.getStatus().toString());
    }

    /**
     * Creates a Comparator to sort Anyware items based on operation parameters.
     *
     * @param operation The operation containing sorting criteria.
     * 
     * @return A Comparator that sorts items according to the specified fields and directions.
     */
    private Comparator<Anyware> getSorter(Operation operation) {
        return (o1, o2) -> {
            if (operation.getSort().isEmpty())
                return o1.getBaseName().compareToIgnoreCase(o2.getBaseName());
            for (Sorter s : operation.getSort()) {
                switch (s.getName()) {
                    case NAME: {
                        final int ret = sortByName(o1, o2, s);
                        if (ret != 0)
                            return ret;
                        break;
                    }
                    case DESCRIPTION: {
                        final int ret = sortByDesc(o1, o2, s);
                        if (ret != 0)
                            return ret;
                        break;
                    }
                    default:
                        break;
                }
            }
            return 0;
        };
    }

    /**
     * Compares two Anyware items by their description.
     *
     * @param o1 The first Anyware item.
     * @param o2 The second Anyware item.
     * @param s The Sorter configuration (including direction).
     * 
     * @return A negative integer, zero, or a positive integer as the first argument is less than, equal to, or greater than the
     *         second.
     */
    private int sortByDesc(Anyware o1, Anyware o2, Sorter s) {
        return (s.isDesc() ? o2 : o1).description.toString().compareToIgnoreCase((s.isDesc() ? o1 : o2).description.toString());
    }

    /**
     * Compares two Anyware items by their base name.
     *
     * @param o1 The first Anyware item.
     * @param o2 The second Anyware item.
     * @param s The Sorter configuration (including direction).
     * 
     * @return A negative integer, zero, or a positive integer as the first argument is less than, equal to, or greater than the
     *         second.
     */
    private int sortByName(Anyware o1, Anyware o2, Sorter s) {
        return (s.isDesc() ? o2 : o1).getBaseName().compareToIgnoreCase((s.isDesc() ? o1 : o2).getBaseName());
    }

    /**
     * Builds a filtered and sorted Stream of Anyware items.
     *
     * @param al The AnywareList to process.
     * @param operation The operation containing filter and sort criteria.
     * 
     * @return A Stream of filtered and sorted Anyware items.
     */
    private Stream<? extends Anyware> buildStream(AnywareList<? extends Anyware> al, Operation operation) {
        return al.getFilteredList().stream().filter(getFilter(operation)).sorted(getSorter(operation));
    }

    /**
     * Builds a filtered and sorted List of Anyware items.
     *
     * @param al The AnywareList to process.
     * @param operation The operation containing filter and sort criteria.
     * 
     * @return A List of filtered and sorted Anyware items.
     */
    private List<Anyware> buildList(AnywareList<?> al, Operation operation) {
        return al.getFilteredList().stream().filter(getFilter(operation)).sorted(getSorter(operation)).collect(Collectors.toList());
    }

    /**
     * Writes a single record for an Anyware item to the XML response.
     *
     * @param al The AnywareList containing the item.
     * @param aw The Anyware item to write.
     */
    private void writeRecord(final AnywareList<?> al, final Anyware aw) {
        try {
            writer.writeEmptyElement("record");
            writer.writeAttribute("list", al instanceof MachineList ? "*" : al.getBaseName());
            writer.writeAttribute(STATUS, aw.getStatus().toString());
            writer.writeAttribute(NAME, aw.getBaseName());
            writer.writeAttribute(DESCRIPTION, aw.getDescription().toString());
            writer.writeAttribute("have", String.format("%d/%d", aw.countHave(), aw.countAll()));
            if (aw.getCloneof() != null) {
                writer.writeAttribute(CLONEOF, aw.getCloneof());
                writer.writeAttribute("cloneof_status", al.getByName(aw.getCloneof()).getStatus().toString());
            }
            if (aw instanceof Machine m) {
                MachineList ml = (MachineList) al;
                if (m.isIsbios())
                    writer.writeAttribute("type", "BIOS");
                else if (m.isIsdevice())
                    writer.writeAttribute("type", "DEVICE");
                else if (m.isIsmechanical())
                    writer.writeAttribute("type", "MECHANICAL");
                else
                    writer.writeAttribute("type", "STANDARD");
                if (m.getRomof() != null) {
                    writer.writeAttribute(ROMOF, m.getRomof());
                    writer.writeAttribute("romof_status", al.getByName(m.getRomof()).getStatus().toString());
                }
                if (m.getSampleof() != null) {
                    writer.writeAttribute(SAMPLEOF, m.getSampleof());
                    writer.writeAttribute("sampleof_status", ml.samplesets.getByName(m.getSampleof()).getStatus().toString());
                }
            }
            writer.writeAttribute(SELECTED, Boolean.toString(aw.isSelected()));
        } catch (XMLStreamException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Fetches a list of Anyware items and writes them to the XML response.
     * <p>
     * If the {@code reset} parameter is true, the cache of the target list is reset. The items are then filtered and sorted based
     * on the operation parameters.
     * </p>
     *
     * @param operation The operation containing request parameters.
     * 
     * @throws XMLStreamException If an XML streaming error occurs during writing.
     */
    @Override
    protected void fetch(Operation operation) throws XMLStreamException {
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");
        final var reset = Boolean.parseBoolean(operation.getData("reset"));
        final AnywareList<?> al = getList(operation);
        if (al != null) {
            if (reset)
                al.resetCache();
            fetchStream(operation, buildStream(al, operation), rec -> writeRecord(al, rec));
        }
        writer.writeEndElement();
    }

    /**
     * Updates the selection state of a specific Anyware item and writes the updated record to the XML response.
     *
     * @param operation The operation containing the "list", "name", and "selected" parameters.
     * 
     * @throws XMLStreamException If an XML streaming error occurs during writing.
     */
    @Override
    protected void update(Operation operation) throws XMLStreamException {
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");
        final AnywareList<?> al = getList(operation);
        if (al != null) {
            String name = operation.getData(NAME);
            if (name != null) {
                Anyware aw = al.getByName(name);
                if (aw != null) {
                    if (operation.hasData(SELECTED))
                        aw.setSelected(Boolean.parseBoolean(operation.getData(SELECTED)));
                    writeRecord(al, aw);
                }
            }
        }
        writer.writeEndElement();
    }

    /**
     * Handles custom operations such as finding an item or bulk selection changes.
     *
     * @param operation The operation containing the "operationId" and relevant parameters.
     * 
     * @throws XMLStreamException If an XML streaming error occurs during writing.
     */
    @Override
    protected void custom(Operation operation) throws XMLStreamException {
        if (operation.getOperationId().toString().equals("find")) {
            find(operation);
        } else if (operation.getOperationId().toString().equals("selectNone")) {
            selectNone(operation);
        } else if (operation.getOperationId().toString().equals("selectAll")) {
            selectAll(operation);
        } else if (operation.getOperationId().toString().equals("selectInvert")) {
            selectInvert(operation);
        } else
            failure("custom operation with id " + operation.getOperationId() + " not implemented");
    }

    /**
     * Inverts the selection state of all Anyware items matching the current filter.
     *
     * @param operation The operation containing filter criteria to scope the inversion.
     * 
     * @throws XMLStreamException If an XML streaming error occurs during writing.
     */
    private void selectInvert(Operation operation) throws XMLStreamException {
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");
        final AnywareList<?> al = getList(operation);
        if (al != null) {
            List<Anyware> list = buildList(al, operation);
            for (Anyware aw : list)
                aw.setSelected(!aw.isSelected());
        }
        writer.writeEndElement();
    }

    /**
     * Selects all Anyware items matching the current filter.
     *
     * @param operation The operation containing filter criteria to scope the selection.
     * 
     * @throws XMLStreamException If an XML streaming error occurs during writing.
     */
    private void selectAll(Operation operation) throws XMLStreamException {
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");
        final AnywareList<?> al = getList(operation);
        if (al != null) {
            List<Anyware> list = buildList(al, operation);
            for (Anyware aw : list)
                aw.setSelected(true);
        }
        writer.writeEndElement();
    }

    /**
     * Deselects all Anyware items matching the current filter.
     *
     * @param operation The operation containing filter criteria to scope the deselection.
     * 
     * @throws XMLStreamException If an XML streaming error occurs during writing.
     */
    private void selectNone(Operation operation) throws XMLStreamException {
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");
        final AnywareList<?> al = getList(operation);
        if (al != null) {
            List<Anyware> list = buildList(al, operation);
            for (Anyware aw : list)
                aw.setSelected(false);
        }
        writer.writeEndElement();
    }

    /**
     * Finds the index of a specific Anyware item within the filtered and sorted list.
     *
     * @param operation The operation containing the "find" parameter (exact name to match) and filter criteria.
     * 
     * @throws XMLStreamException If an XML streaming error occurs during writing.
     */
    private void find(Operation operation) throws XMLStreamException {
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");
        final AnywareList<?> al = getList(operation);
        if (al != null && operation.hasData("find")) {
            List<Anyware> list = buildList(al, operation);
            final String find = operation.getData("find");
            for (var i = 0; i < list.size(); i++) {
                if (list.get(i).getBaseName().equals(find)) {
                    writer.writeElement("found", Integer.toString(i));
                    break;
                }
            }
        }
        writer.writeEndElement();
    }
}
