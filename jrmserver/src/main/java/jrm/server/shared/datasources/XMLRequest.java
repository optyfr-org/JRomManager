package jrm.server.shared.datasources;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import jrm.misc.ExceptionUtils;
import jrm.misc.Log;
import jrm.server.shared.TempFileInputStream;
import jrm.server.shared.WebSession;
import jrm.server.shared.datasources.XMLRequest.Operation.Sorter;
import jrm.xml.XMLTools;
import lombok.Getter;

/**
 * Parses incoming XML-formatted requests from the client side into structured {@link Operation} and {@link Transaction} objects.
 * Utilizes a secure SAX-based parser to extract operational parameters such as row pagination, sorting criteria, and key-value data
 * payloads required for server-side datasources handling.
 * <p>
 * This class implements a two-phase parsing strategy: first, the XML stream is buffered to a temporary file via
 * {@link TempFileInputStream} to support large payloads without memory constraints; second, a custom {@link XMLRequestHandler}
 * processes the XML structure using SAX events to populate the operation model.
 * </p>
 * <p>
 * Security features include disabling external DTD and schema loading to prevent XXE (XML External Entity) attacks.
 * </p>
 *
 * @since 1.0
 * 
 * @see Operation
 * @see Transaction
 * @see TempFileInputStream
 */
public class XMLRequest {

    /**
     * Represents a single operational command parsed from the XML request stream.
     * <p>
     * Each operation encapsulates the complete execution context for a server-side action, including:
     * </p>
     * <ul>
     * <li><strong>Identity:</strong> operation type (e.g., "fetch", "add", "update", "remove") and unique identifier</li>
     * <li><strong>Pagination:</strong> row range boundaries for limiting result sets in fetch operations</li>
     * <li><strong>Sorting:</strong> multi-field sorting directives with ascending/descending control</li>
     * <li><strong>Payload:</strong> key-value data map supporting multiple values per key for complex inputs</li>
     * <li><strong>State tracking:</strong> original field values for update operations to support optimistic locking</li>
     * </ul>
     * <p>
     * Operations are typically created by the {@link XMLRequestHandler} during SAX parsing and can exist either as standalone
     * commands or as part of a {@link Transaction} for batch processing.
     * </p>
     */
    public static class Operation {

        /**
         * Default constructor for initializing an empty operation instance.
         * <p>
         * All fields are initialized to their default values: {@code startRow} is set to {@code 0}, {@code endRow} is set to
         * {@link Integer#MAX_VALUE}, and the data and oldValues maps are initialized as empty. The operation type and ID
         * accumulators are also initialized as empty {@link StringBuilder} instances.
         * </p>
         */
        public Operation() {
            /* Default constructor for Operation; no initialization required */
        }

        /**
         * Defines sorting criteria derived from the {@code sortBy} XML element.
         * <p>
         * The sorting convention follows SmartClient's format: field names prefixed with a hyphen ({@code -}) indicate descending
         * order, while unprefixed names indicate ascending order. Multiple sorters can be applied in sequence for multi-field
         * sorting.
         * </p>
         * <p>
         * Examples:
         * </p>
         * <ul>
         * <li>{@code "name"} - sort by name in ascending order</li>
         * <li>{@code "-date"} - sort by date in descending order</li>
         * </ul>
         */
        public static class Sorter {
            /**
             * Default constructor for Sorter; initializes an empty instance.
             * <p>
             * The {@link #name} field remains {@code null} and {@link #desc} defaults to {@code false}. This constructor is
             * primarily useful for deferred population via direct field access.
             * </p>
             */
            public Sorter() {
                /* Default constructor for Sorter; no initialization required */
            }

            /**
             * Name of the field or column to sort by.
             * <p>
             * This value is extracted from the raw sorting directive after stripping any leading hyphen prefix. The field name must
             * correspond to a valid column or property in the target datasource.
             * </p>
             */
            String name;

            /**
             * Flag indicating descending order.
             * <p>
             * Set to {@code true} if the original sorting directive string begins with a hyphen ({@code -}), indicating that
             * results should be sorted in reverse order. Defaults to {@code false} (ascending).
             * </p>
             */
            boolean desc = false;

            /**
             * Constructs a {@code Sorter} instance, determining sort direction based on string prefix. If the input value starts
             * with '-', sets descending mode to true and strips the prefix from the name.
             *
             * @param value the raw sorting directive string containing optional '-' prefix for descending order
             * 
             * @throws NullPointerException if {@code value} is {@code null}
             */
            public Sorter(String value) {
                if (!value.isEmpty() && value.charAt(0) == '-') {
                    desc = true;
                    name = value.substring(1);
                } else
                    name = value;
            }

            /**
             * Retrieves the field name used for sorting.
             * 
             * @return the name of the field used for sorting, or {@code null} if this sorter was created via the default
             *         constructor and the name was not subsequently set
             */
            public String getName() {
                return name;
            }

            /**
             * Indicates whether this sorter specifies descending order.
             * 
             * @return true if this sorter specifies descending order; false otherwise
             */
            public boolean isDesc() {
                return desc;
            }
        }

        /**
         * SAX character accumulator for the operation type identifier.
         * <p>
         * During XML parsing, character data between {@code <operationType>} tags is appended to this builder. Common values
         * include "fetch", "add", "update", "remove", and "custom".
         * </p>
         */
        StringBuilder operationType = new StringBuilder();

        /**
         * SAX character accumulator for the operation unique identifier.
         * <p>
         * During XML parsing, character data between {@code <operationId>} tags is appended to this builder. The operation ID is
         * typically a client-generated sequence number used to correlate requests with responses.
         * </p>
         */
        StringBuilder operationId = new StringBuilder();

        /**
         * Zero-based starting index for row pagination.
         * <p>
         * Specifies the first row to include in the result set. Used in conjunction with {@link #endRow} to implement server-side
         * pagination. Defaults to {@code 0} (start from the first row).
         * </p>
         */
        int startRow = 0;

        /**
         * Exclusive ending index for row pagination.
         * <p>
         * Specifies the row index at which to stop (not included in results). Used in conjunction with {@link #startRow} to limit
         * result set size. Defaults to {@link Integer#MAX_VALUE} to retrieve all rows.
         * </p>
         */
        int endRow = Integer.MAX_VALUE;

        /**
         * Ordered list of sorting directives applied to the result set during fetch operations.
         * <p>
         * Multiple sorters can be specified to implement multi-field sorting (e.g., sort by category, then by name within each
         * category). Sorters are applied in list order, with earlier entries taking precedence.
         * </p>
         */
        List<Sorter> sort = new ArrayList<>();

        /**
         * Multi-valued map storing key-value data pairs extracted from the request payload.
         * <p>
         * Supports multiple values per key to accommodate array-like inputs (e.g., multiple selected IDs). For single-valued
         * fields, use {@link #getData(String)} to retrieve the first value. For multi-valued fields, use {@link #getDatas(String)}
         * to retrieve the complete list.
         * </p>
         */
        private Map<String, List<String>> data = new HashMap<>();

        /**
         * Map storing original field values captured prior to applying updates.
         * <p>
         * Used in update operations to support optimistic locking and change tracking. The server can compare these original values
         * with the current database state to detect concurrent modifications.
         * </p>
         */
        Map<String, String> oldValues = new HashMap<>();

        /**
         * Checks whether a specific data key exists within the current operation's payload.
         *
         * @param key the data key to search for
         * 
         * @return true if the key is present in the data map; false otherwise
         */
        public boolean hasData(String key) {
            return data.containsKey(key);
        }

        /**
         * Retrieves the first value associated with the specified key.
         *
         * @param key the data key to look up
         * 
         * @return the first value mapped to the key, or {@code null} if the key is absent or the associated value list is empty
         */
        public String getData(String key) {
            if (data.containsKey(key)) {
                List<String> value = data.get(key);
                if (!value.isEmpty())
                    return value.get(0);
            }
            return null;
        }

        /**
         * Appends a value to the list associated with the given key in the data map. Automatically initializes the list if the key
         * does not already exist.
         *
         * @param key the data key under which to store the value
         * @param value the string value to append
         *
         * @return {@code true} unconditionally, since {@link java.util.ArrayList#add} always returns {@code true}
         */
        boolean addData(String key, String value) {
            return data.computeIfAbsent(key, _ -> new ArrayList<>()).add(value);
        }

        /**
         * Retrieves the complete list of values associated with a specific key.
         *
         * @param key the data key to look up
         * 
         * @return the list of values for the key, or null if the key is absent
         */
        public List<String> getDatas(String key) {
            return data.get(key);
        }

        /**
         * Retrieves the ordered list of sorting directives for this operation.
         * 
         * @return the ordered list of sorting directives for this operation
         */
        public List<Sorter> getSort() {
            return sort;
        }

        /**
         * Retrieves the mutable string builder containing the parsed operation ID.
         * 
         * @return the mutable string builder containing the parsed operation ID
         */
        public StringBuilder getOperationId() {
            return operationId;
        }

        /**
         * Retrieves the mutable string builder containing the parsed operation type.
         * 
         * @return the mutable string builder containing the parsed operation type
         */
        public StringBuilder getOperationType() {
            return operationType;
        }

        /**
         * Retrieves the zero-based starting row index for pagination filtering.
         * 
         * @return the zero-based starting row index for pagination filtering
         */
        public int getStartRow() {
            return startRow;
        }

        /**
         * Retrieves the exclusive ending row index for pagination filtering.
         * 
         * @return the exclusive ending row index for pagination filtering
         */
        public int getEndRow() {
            return endRow;
        }

        /**
         * Retrieves the map of original field values captured prior to applying updates.
         * 
         * @return the map of original field values captured prior to applying updates
         */
        public Map<String, String> getOldValues() {
            return oldValues;
        }
    }

    /**
     * Represents a transactional container capable of holding a sequence of related {@link Operation}s.
     * <p>
     * Transactions enable batch execution of multiple operations within a single request, reducing network overhead and ensuring
     * logical grouping of related actions. While operations within a transaction are processed sequentially, they share the same
     * web session context and can reference each other's results.
     * </p>
     * <p>
     * Note: The term "transaction" here refers to logical grouping rather than database transaction semantics. Individual
     * operations may still commit independently depending on the server implementation.
     * </p>
     */
    public class Transaction {
        /** Default constructor for initializing an empty transaction wrapper. */
        public Transaction() {
            /* No-op constructor for transaction initialization */
        }

        /**
         * Ordered list accumulating all individual operations belonging to this logical transaction block.
         * <p>
         * Operations are added in the order they appear in the XML request and should be processed in the same sequence to maintain
         * data consistency.
         * </p>
         */
        List<Operation> operations = new ArrayList<>();

        /**
         * Retrieves the list of operations contained within this transaction wrapper.
         * 
         * @return the list of operations contained within this transaction wrapper
         */
        public List<Operation> getOperations() {
            return operations;
        }
    }

    /**
     * Reference to the single operation extracted if no transaction wrapping is present.
     * <p>
     * This field is mutually exclusive with {@link #transaction}. When the XML request contains a standalone {@code <request>}
     * element without a parent {@code <transaction>}, the parsed operation is stored here. Use {@link #getOperation()} to access
     * it.
     * </p>
     */
    Operation operation = null;

    /**
     * Reference to the overarching transaction object if multiple operations were grouped.
     * <p>
     * This field is mutually exclusive with {@link #operation}. When the XML request contains a {@code <transaction>} element
     * wrapping one or more {@code <request>} elements, the transaction container is stored here. Use {@link #getTransaction()} to
     * access it.
     * </p>
     */
    Transaction transaction = null;

    /**
     * Active web session context bound to this parsed request for authentication and state tracking.
     * <p>
     * The session provides access to user credentials, localization messages, and shared state required for request processing. It
     * is passed to the constructor and stored for later use by datasource handlers.
     * </p>
     * 
     * @return the active web session context associated with this request
     */
    @Getter
    WebSession session;

    /**
     * Initializes an {@code XMLRequest} instance by parsing the provided input stream into structured operation models.
     * <p>
     * The constructor performs the following steps:
     * </p>
     * <ol>
     * <li>Stores the web session reference for later use</li>
     * <li>Configures a secure SAX parser with XXE protection (disables external DTD and schema loading)</li>
     * <li>Buffers the input stream to a temporary file via {@link TempFileInputStream} to handle large payloads</li>
     * <li>Parses the XML using a custom {@link XMLRequestHandler} to populate operation/transaction fields</li>
     * <li>Automatically cleans up the temporary file when parsing completes</li>
     * </ol>
     * <p>
     * If parsing fails due to configuration or SAX errors, they are logged but not rethrown, leaving the operation/transaction
     * fields in their initial null state.
     * </p>
     *
     * @param session the active web session binding context for authorization and routing
     * @param in the input stream containing the raw XML payload
     * @param len the expected length of the input stream for temporary file buffering calculations
     *
     * @throws IOException if a network or I/O error occurs while reading or processing the XML stream
     */
    public XMLRequest(WebSession session, InputStream in, long len) throws IOException {
        this.session = session;
        try {
            final var parser = XMLTools.getSaxParser();
            try (final var tfis = TempFileInputStream.newInstance(in, len)) {
                parser.parse(tfis, new XMLRequestHandler());
            }
        } catch (ParserConfigurationException | SAXException e) {
            Log.err(e.getMessage(), e);
        }
    }

    /**
     * Retrieves the transaction wrapper containing a batch of operations if present.
     * 
     * @return the transaction wrapper containing a batch of operations, or null if operating in single-operation mode
     */
    public Transaction getTransaction() {
        return transaction;
    }

    /**
     * Retrieves the single operation extracted from the XML request when no transaction wrapper is present.
     * 
     * @return the single standalone operation being processed, or null if wrapped inside a transaction
     */
    public Operation getOperation() {
        return operation;
    }

    /**
     * Internal SAX event handler implementing a state machine to parse XML request structures.
     * <p>
     * This handler processes three main XML structures:
     * </p>
     * <ul>
     * <li><strong>Standalone request:</strong> {@code <request>} at root level → populates {@link XMLRequest#operation}</li>
     * <li><strong>Transaction wrapper:</strong> {@code <transaction>} containing multiple {@code <request>} elements → populates
     * {@link XMLRequest#transaction}</li>
     * <li><strong>Operation fields:</strong> nested elements within {@code <request>} defining operation parameters</li>
     * </ul>
     * <p>
     * The handler uses boolean flags to track parsing state (e.g., {@link #inOperationType}, {@link #inData}) and routes character
     * data to the appropriate accumulator ({@link Operation#operationType}, {@link #datavalue}, etc.). Numeric fields are parsed
     * safely using {@link ExceptionUtils#unthrow} to handle malformed input gracefully.
     * </p>
     */
    private final class XMLRequestHandler extends org.xml.sax.helpers.DefaultHandler {
        /** XML element name for sort-by directive, used to identify sorting field in SAX callbacks. */
        private static final String SORT_BY = "sortBy";
        /** XML element name for end-row pagination parameter, used to identify pagination end in SAX callbacks. */
        private static final String END_ROW = "endRow";
        /** XML element name for start-row pagination parameter, used to identify pagination start in SAX callbacks. */
        private static final String START_ROW = "startRow";

        /**
         * Indicates whether the parser has entered a {@code <request>} element.
         * <p>
         * When {@code true}, the handler is processing operation-level fields. When {@code false}, it is at the root level or
         * inside a {@code <transaction>} wrapper.
         * </p>
         */
        boolean isRequest = false;

        /**
         * Flags active character accumulation for the operation type field.
         * <p>
         * Set to {@code true} when entering {@code <operationType>} and reset to {@code false} on exit.
         * </p>
         */
        boolean inOperationType = false;

        /**
         * Flags active character accumulation for the operation ID field.
         * <p>
         * Set to {@code true} when entering {@code <operationId>} and reset to {@code false} on exit.
         * </p>
         */
        boolean inOperationId = false;

        /**
         * Flags active character accumulation for the start row index field.
         * <p>
         * Set to {@code true} when entering {@code <startRow>} and reset to {@code false} on exit.
         * </p>
         */
        boolean inStartRow = false;

        /**
         * Flags active character accumulation for the end row index field.
         * <p>
         * Set to {@code true} when entering {@code <endRow>} and reset to {@code false} on exit.
         * </p>
         */
        boolean inEndRow = false;

        /**
         * Flags active character accumulation for the sort by directive field.
         * <p>
         * Set to {@code true} when entering {@code <sortBy>} and reset to {@code false} on exit.
         * </p>
         */
        boolean inSortBy = false;

        /**
         * Flags active character accumulation for dynamic data payload elements.
         * <p>
         * Set to {@code true} when entering the {@code <data>} container and reset to {@code false} on exit. While active, child
         * element names are treated as data keys and their text content as values.
         * </p>
         */
        boolean inData = false;

        /**
         * Flags active character accumulation for pre-update value mappings.
         * <p>
         * Set to {@code true} when entering the {@code <oldValues>} container and reset to {@code false} on exit. While active,
         * child element names are treated as field names and their text content as original values.
         * </p>
         */
        boolean inOldValues = false;

        /**
         * Temporary buffer reused across SAX callbacks to store raw text content before parsing.
         * <p>
         * This builder accumulates character data for fields that require post-processing (e.g., numeric conversion, sorter
         * creation). It is reset to zero length at the start and end of each field.
         * </p>
         */
        StringBuilder datavalue = new StringBuilder();

        /**
         * Currently active operation instance being populated by SAX events.
         * <p>
         * This reference is set when a {@code <request>} element is encountered and remains active until the element closes. All
         * field assignments during parsing target this operation.
         * </p>
         */
        Operation currentRequest;

        /**
         * Handles the start of XML elements, routing structural markers to instantiate objects or activate parser states.
         *
         * @param uri the Namespace URI, or the empty string if none is available
         * @param localName the local name (without prefix), or the empty string if namespaces are disabled
         * @param qName the qualified name (with prefix), or the empty string if qualified names are not available
         * @param attributes the attribute list of this element, or null if there are no attributes
         * 
         * @throws SAXException if the parser encounters an invalid XML structure
         */
        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if (qName.equals("request")) {
                currentRequest = new Operation();
                if (getTransaction() != null)
                    getTransaction().getOperations().add(currentRequest);
                else
                    operation = currentRequest;
                isRequest = true;
            } else if (qName.equals("transaction"))
                transaction = new Transaction();
            else if (isRequest) {
                switch (qName) {
                    case "operationType" -> inOperationType = true;
                    case "operationId" -> inOperationId = true;
                    case START_ROW, END_ROW, SORT_BY -> {
                        switch (qName) {
                            case START_ROW -> inStartRow = true;
                            case END_ROW -> inEndRow = true;
                            case SORT_BY -> inSortBy = true;
                            default -> {
                                /* No action needed */}
                        }
                        datavalue.setLength(0);
                    }
                    case "data" -> inData = true;
                    case "oldValues" -> inOldValues = true;
                    default -> {
                        if (inData || inOldValues) {
                            datavalue.setLength(0);
                        }
                    }
                }

            }
        }

        /**
         * Handles the end of XML elements, triggering type conversion and mapping of buffered text into the active operation.
         *
         * @param uri the Namespace URI, or the empty string if none is available
         * @param localName the local name (without prefix), or the empty string if namespaces are disabled
         * @param qName the qualified name (with prefix), or the empty string if qualified names are not available
         * 
         * @throws SAXException if the parser encounters an invalid XML structure
         */
        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            switch (qName) {
                case "operationType" -> inOperationType = false;
                case "operationId" -> inOperationId = false;
                case START_ROW -> {
                    inStartRow = false;
                    ExceptionUtils.unthrow(start -> currentRequest.startRow = start, Integer::parseInt, datavalue.toString());
                    datavalue.setLength(0);
                }
                case END_ROW -> {
                    inEndRow = false;
                    ExceptionUtils.unthrow(end -> currentRequest.endRow = end, Integer::parseInt, datavalue.toString());
                    datavalue.setLength(0);
                }
                case SORT_BY -> {
                    inSortBy = false;
                    currentRequest.getSort().add(new Sorter(datavalue.toString()));
                    datavalue.setLength(0);
                }
                case "data" -> inData = false;
                case "oldValues" -> inOldValues = false;
                default -> {
                    if (inData) {
                        currentRequest.addData(qName, datavalue.toString());
                        datavalue.setLength(0);
                    } else if (inOldValues) {
                        currentRequest.getOldValues().put(qName, datavalue.toString());
                        datavalue.setLength(0);
                    }
                }
            }
        }

        /**
         * Receives character data chunks between XML tags, appending them to appropriate accumulators based on active state flags.
         *
         * @param ch the buffer containing the characters read from the XML stream
         * @param start the offset in the buffer where the relevant characters begin
         * @param length the number of characters to read from the buffer
         * 
         * @throws SAXException if any unexpected character data format is encountered
         */
        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (inOperationType)
                currentRequest.getOperationType().append(ch, start, length);
            else if (inOperationId)
                currentRequest.getOperationId().append(ch, start, length);
            else if (inStartRow || inEndRow || inSortBy || inData || inOldValues)
                datavalue.append(ch, start, length);
        }
    }
}
