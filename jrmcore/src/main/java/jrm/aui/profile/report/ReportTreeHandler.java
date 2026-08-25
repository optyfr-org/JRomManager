package jrm.aui.profile.report;

import java.util.Set;

import jrm.profile.report.FilterOptions;

/**
 * ReportTreeHandler is an interface that defines the contract for handling a tree structure of reports. It provides methods for
 * initializing the clone, filtering the reports based on specified options, and retrieving both the filtered and original reports.
 * Implementations of this interface can be used to manage and manipulate report trees in various ways, such as applying different
 * filters or transformations to the report data.
 *
 * @param <T> the type of report being handled
 */
public interface ReportTreeHandler<T> {

    /**
     * Initializes the clone of the original report. This method is responsible for creating a filtered version of the original
     * report based on the current filter options. It is typically called after the filter options have been updated to ensure that
     * the filtered report reflects the new criteria.
     */
    void initClone();

    /**
     * Filter the report tree based on the specified filter options. This method takes a variable number of filter options as input
     * and applies them to the report tree, resulting in an updated filtered version of the report. The default implementation
     * converts the variable arguments into a set and calls the filter method that accepts a set of filter options.
     *
     * @param filterOptions the filter options to be applied to the report tree
     */
    public default void filter(final FilterOptions... filterOptions) {
        filter(Set.of(filterOptions));
    }

    /**
     * Filter the report tree based on the specified set of filter options. This method takes a set of filter options as input and
     * applies them to the report tree, resulting in an updated filtered version of the report. Implementations of this method
     * should update the internal state of the handler to reflect the new filter options and then call initClone to create a new
     * filtered version of the report based on those options.
     *
     * @param filterOptions the set of filter options to be applied to the report tree
     */
    public void filter(final Set<FilterOptions> filterOptions);

    /**
     * Retrieves the current set of filter options applied to the report tree. This method returns a set of filter options that are
     * currently being used to filter the report tree. Implementations of this method should return a copy of the internal filter
     * options to ensure that the caller cannot modify the internal state of the handler.
     *
     * @return a set of filter options currently applied to the report tree
     */
    public Set<FilterOptions> getFilterOptions();

    /**
     * Checks if there are any listeners registered for changes in the report tree. This method returns a boolean value indicating
     * whether there are listeners that should be notified when changes occur in the report tree. The default implementation returns
     * false, indicating that there are no listeners by default.
     *
     * @return true if there are listeners registered for changes in the report tree, false otherwise
     */
    public default boolean hasListeners() {
        return false;
    }

    /**
     * Notifies listeners of an insertion event in the report tree. This method is called when new nodes are inserted into the
     * report tree, and it should notify any registered listeners about the change. The default implementation does nothing, as
     * there are no listeners by default.
     *
     * @param childIndices the indices of the inserted child nodes
     * @param children the inserted child nodes
     */
    public default void notifyInsertion(int[] childIndices, Object[] children) {

    }

    /**
     * Retrieves the filtered version of the report. This method returns the current filtered report that reflects the applied
     * filter options. Implementations of this method should return the filtered version of the report that is maintained internally
     * by the handler.
     *
     * @return the filtered version of the report
     */
    public T getFilteredReport();

    /**
     * Retrieves the original report. This method returns the original report that is being handled by the tree structure.
     * Implementations of this method should return the original report that was provided to the handler during initialization.
     *
     * @return the original report
     */
    public T getOriginalReport();

}
