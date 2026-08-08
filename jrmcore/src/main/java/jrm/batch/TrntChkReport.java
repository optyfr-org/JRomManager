package jrm.batch;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jrm.aui.profile.report.ReportTreeHandler;
import jrm.aui.status.StatusRendererFactory;
import jrm.misc.Log;
import jrm.profile.report.FilterOptions;
import jrm.profile.report.ReportIntf;
import jrm.profile.report.Subject;
import jrm.security.DeserializationFilter;
import jrm.security.Session;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/** Report to manage and store status of torrent checking process. */
public final class TrntChkReport implements Serializable, StatusRendererFactory, ReportIntf<TrntChkReport> {
    /** the serial version UID for serialization compatibility */
    private static final long serialVersionUID = 4L;

    /**
     * the atomic counter for generating unique IDs for Child nodes (not serialized)
     */
    private transient AtomicLong uidCnt = new AtomicLong();
    /** the source file for this report (not serialized) */
    private transient File file = null;
    /** the last modified timestamp of the source file (not serialized) */
    private transient long fileModified = 0L;

    /**
     * the list of root nodes in the report tree
     * 
     * @return the list of root nodes in the report tree note: this list represents the top-level nodes in the report tree and is
     *         serialized to maintain the structure of the report when saved and loaded
     */
    private @Getter List<Child> nodes = new ArrayList<>();

    /**
     * A map of all child nodes in the report indexed by their unique ID.
     * <p>
     * This allows for constant-time lookups of any node within the report's tree structure.
     *
     * @return the map containing all child nodes by ID
     */
    private @Getter Map<Long, Child> all = new HashMap<>();

    /**
     * The linked UI tree model handler.
     * <p>
     * This handler manages the visual hierarchy of the report within the user interface and is marked transient to prevent
     * serialization of UI-bound components.
     *
     * @return the active report tree handler, or {@code null} if none is bound
     * 
     * @param handler the report tree handler to bind to this report
     */
    private transient @Setter @Getter ReportTreeHandler<TrntChkReport> handler = null;

    /**
     * Constructs a new TrntChkReport with the specified source file.
     *
     * @param src the source file associated with this report
     */
    public TrntChkReport(final File src) {
        this.file = src;
    }

    /**
     * Enumeration representing the possible status values for report nodes.
     */
    public enum Status {
        /** Indicates that the status is OK and no issues were found. */
        OK,
        /** Indicates that the size of the item is incorrect. */
        SIZE,
        /**
         * Indicates that the SHA1 hash of the item does not match the expected value.
         */
        SHA1,
        /** Indicates that the item is missing. */
        MISSING,
        /** Indicates that the item was skipped during processing. */
        SKIPPED,
        /** Indicates that the status of the item is unknown. */
        UNKNOWN
    }

    /**
     * Predicate implementation for filtering report nodes based on specified filter options.
     */
    class FilterPredicate implements Predicate<Child> {
        /** the set of filter options to apply when testing nodes */
        Set<FilterOptions> filterOptions;

        /**
         * Constructs a FilterPredicate with the specified filter options.
         *
         * @param filterOptions the set of filter options to apply when testing nodes
         */
        public FilterPredicate(final Set<FilterOptions> filterOptions) {
            this.filterOptions = filterOptions;
        }

        /**
         * Tests whether a given Child node should be included based on the current filter options.
         *
         * @param t the Child node to test
         * 
         * @return {@code true} if the node should be included, {@code false} otherwise
         */
        @Override
        public boolean test(final Child t) {
            if (!filterOptions.contains(FilterOptions.SHOWOK) && t.data.status == Status.OK)
                return false;
            if (filterOptions.contains(FilterOptions.HIDEMISSING) && t.data.status == Status.MISSING) // NOSONAR
                return false;
            return true;
        }

    }

    /**
     * the transient filter predicate used for filtering nodes based on current filter options (not serialized)
     */
    private transient FilterPredicate filterPredicate = new FilterPredicate(new HashSet<>());

    /**
     * Represents the data associated with a Child node in the report tree, including title, length, and status.
     */
    public static final class ChildData implements Serializable {
        /** the serial version UID for serialization compatibility */
        private static final long serialVersionUID = 2L;
        /**
         * the title of the child node
         * 
         * @return the title of the child node
         */
        private @Getter String title;

        /**
         * the length of the item represented by the child node
         * 
         * @param length the length of the item represented by the child node to set
         * 
         * @return the length of the item represented by the child node
         */
        private @Getter @Setter @Accessors(chain = true) Long length = null;

        /**
         * the status of the item represented by the child node
         * 
         * @return the status of the item represented by the child node
         */
        private @Getter Status status = Status.UNKNOWN;

        /**
         * Constructs a new ChildData instance with default values for title, length, and status. This constructor is used for
         * serialization purposes.
         */
        public ChildData() {
            /* default constructor for serialization */ }
    }

    /**
     * Represents a node in the report tree, which can have child nodes and associated data.
     */
    public final class Child implements Serializable, StatusRendererFactory {
        /** the serial version UID for serialization compatibility */
        private static final long serialVersionUID = 3L;

        /**
         * the list of child nodes under this node
         * 
         * @return the list of child nodes under this node note: this list represents the direct children of this node in the report
         *         tree and is serialized to maintain the structure of the report when saved and loaded
         */
        private @Getter List<Child> children;
        /**
         * the unique identifier for this child node
         * 
         * @return the unique identifier for this child node note: this ID is generated using an atomic counter to ensure uniqueness
         *         across all nodes in the report and is not serialized to allow for regeneration upon loading
         */
        private @Getter long uid;
        /**
         * the parent node of this child node (note: this reference is not serialized to avoid circular references and can be
         * regenerated based on the tree structure when loading the report)
         * 
         * @return the parent node of this child node
         */
        private @Getter Child parent = null;
        /**
         * the data associated with this child node, including title, length, and status
         * 
         * @return the data associated with this child node note: this data is serialized to maintain the information of each node
         *         when saved and loaded
         */
        private @Getter ChildData data = null;

        /**
         * Constructs a new Child node with a unique identifier and initializes its data. The parent reference is set to null for
         * root nodes.
         */
        public Child() {
            uid = uidCnt.incrementAndGet();
            all.put(uid, this);
            this.parent = null;
            this.data = new ChildData();
        }

        /**
         * Constructs a new Child node with a unique identifier, sets its parent reference, and initializes its data.
         *
         * @param parent the parent node of this child node
         */
        public Child(Child parent) {
            uid = uidCnt.incrementAndGet();
            all.put(uid, this);
            this.parent = parent;
            this.data = new ChildData();
        }

        /**
         * Adds a new child node with the specified title under this node and returns the newly created child node.
         *
         * @param title the title to set for the new child node
         * 
         * @return the newly created child node with the specified title
         */
        Child add(String title) {
            Child node = new Child(this);
            node.data.title = title;
            if (children == null)
                children = new ArrayList<>();
            children.add(node);
            return node;
        }

        /**
         * Adds a new child node with the same data as the specified original node under this node and returns the newly created
         * child node.
         *
         * @param org the original child node whose data will be copied to the new child node
         * 
         * @return the newly created child node with the same data as the specified original node
         */
        Child add(Child org) {
            Child node = new Child(this);
            node.data = org.data;
            if (children == null)
                children = new ArrayList<>();
            children.add(node);
            return node;
        }

        /**
         * Sets the status of this child node and, if applicable, updates the status of its child nodes based on specific
         * conditions.
         *
         * @param status the status to set for this child node
         */
        void setStatus(TrntChkReport.Status status) {
            data.status = status;
            if (children != null) {
                children.forEach(n -> {
                    if (n.data.status == Status.UNKNOWN || n.data.status == Status.OK)
                        n.data.status = status;
                });
            }
        }

        /**
         * * Returns a string representation of this child node.
         * 
         * @return a string representation of this child node
         */
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append(String.format("%s%-50s %12d %s%n", parent == null ? "" : "|_ ", data.title, data.length, data.status));
            if (children != null)
                for (Child child : children)
                    sb.append(child);
            return sb.toString();
        }

        /**
         * Creates a shallow copy of this child node.
         * 
         * @return a new Child node with the same data, parent, and children references.
         */
        public Child copy() {
            final var node = new Child();
            node.uid = this.uid;
            node.children = this.children;
            node.parent = this.parent;
            node.data = this.data;
            return node;
        }
    }

    /**
     * Adds a new child node with the specified title to the root level of the report and returns the newly created child node.
     *
     * @param title the title to set for the new child node
     * 
     * @return the newly created child node with the specified title
     */
    Child add(String title) {
        final Child node = new Child();
        node.data.title = title;
        nodes.add(node);
        return node;
    }

    /**
     * Returns a string representation of the entire report, including all child nodes and their data.
     * 
     * @return a string representation of the entire report
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (final Child node : nodes)
            sb.append(node);
        return sb.toString();
    }

    /**
     * Retrieves the file associated with this report.
     *
     * @return the file associated with this report, or {@code null} if no file is associated
     */
    @Override
    public File getFile() {
        return this.file;
    }

    /**
     * Retrieves the last modified timestamp of the file associated with this report.
     *
     * @return the last modified timestamp of the file associated with this report, or {@code 0} if no file is associated
     */
    @Override
    public long getFileModified() {
        return fileModified;
    }

    /**
     * Saves this report to the specified file using object serialization. If an error occurs during the save process, a warning
     * message is logged.
     *
     * @param file the file to which this report should be saved
     */
    public void save(final File file) {
        try (final ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            oos.writeObject(TrntChkReport.this);
        } catch (final Exception e) {
            Log.warn(e.getMessage());
        }
    }

    /**
     * Loads a TrntChkReport from the specified file using object deserialization. If an error occurs during the load process, a
     * warning message is logged and {@code null} is returned.
     *
     * @param session the active user session
     * @param file the file from which to load the report
     * 
     * @return the loaded TrntChkReport, or {@code null} if an error occurs during loading
     */
    public static TrntChkReport load(final Session session, final File file) {
        try (final ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(ReportIntf.getReportFile(session, file))))) {
            // Apply deserialization filter to prevent arbitrary code execution
            ois.setObjectInputFilter(DeserializationFilter.createFilter());
            final TrntChkReport report = (TrntChkReport) ois.readObject();
            report.file = file;
            report.fileModified = ReportIntf.getReportFile(session, file).lastModified();
            return report;
        } catch (final Exception e) {
            Log.warn(e.getMessage());
            // may fail to load because serialized classes did change since last cache save
        }
        return null;
    }

    /**
     * Constructs a new TrntChkReport based on another report with applied filter options.
     *
     * @param report the original report
     * @param filterOptions the filter options to apply
     */
    private TrntChkReport(TrntChkReport report, Set<FilterOptions> filterOptions) {
        filterPredicate = report.filterPredicate;
        fileModified = report.fileModified;
        uidCnt = new AtomicLong();
        handler = report.handler;
        nodes = report.filter(filterOptions);
        all = report.all;
    }

    /**
     * Creates a clone of this report with the specified filter options applied.
     *
     * @param filterOptions the filter options to apply to the cloned report
     * 
     * @return a new TrntChkReport instance that is a clone of this report with the specified filter options applied
     */
    @Override
    public TrntChkReport clone(Set<FilterOptions> filterOptions) {
        return new TrntChkReport(this, filterOptions);
    }

    /**
     * Filter subjects using current {@link FilterPredicate}
     * 
     * @param filterOptions the {@link FilterOptions} {@link List} to apply
     * 
     * @return a {@link List} of {@link Subject}
     */
    public List<Child> filter(final Set<FilterOptions> filterOptions) {
        return stream(filterOptions)
                .collect(Collectors.toList()); //NOSONAR
    }

    /**
     * Stream subjects using current {@link FilterPredicate}
     * 
     * @param filterOptions the {@link FilterOptions} {@link List} to apply
     * 
     * @return a {@link Stream} of {@link Child}
     */
    public Stream<Child> stream(final Set<FilterOptions> filterOptions) {
        filterPredicate = new FilterPredicate(filterOptions);
        return nodes.stream().filter(filterPredicate);
    }

}