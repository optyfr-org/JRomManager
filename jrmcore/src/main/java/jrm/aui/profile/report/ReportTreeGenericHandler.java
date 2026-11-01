package jrm.aui.profile.report;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import jrm.profile.report.FilterOptions;
import jrm.profile.report.ReportIntf;

/**
 * ReportTreeGenericHandler is an abstract class that implements the ReportTreeHandler interface. It provides a generic
 * implementation for handling report trees, allowing for filtering and cloning of the original report. The class maintains the
 * original report and a filtered version of it, which can be updated based on the specified filter options.
 *
 * @param <T> the type of report that extends ReportIntf
 */
public abstract class ReportTreeGenericHandler<T extends ReportIntf<T>> implements ReportTreeHandler<T> {

    /**
     * The original report that serves as the root of the tree. It is initialized in the constructor and remains unchanged
     * throughout the lifecycle of the handler.
     */
    protected T originalRoot;
    /**
     * The filtered version of the original report. It is initialized as a clone of the original report and can be updated based on
     * the specified filter options.
     */
    protected T filteredRoot;

    /**
     * A set of filter options that are applied to the report tree. It is used to determine how the original report should be
     * filtered when creating the filtered version. The filter options can be updated through the filter method, which will trigger
     * a re-cloning of the original report with the new filter options.
     */
    protected Set<FilterOptions> filterOptions = new HashSet<>();

    /**
     * Constructs a new ReportTreeGenericHandler with the specified original report as the root of the tree. The constructor
     * initializes the original report and sets the handler for it, then calls the initClone method to create the initial filtered
     * version of the report.
     *
     * @param root the original report to be used as the root of the tree
     */
    protected ReportTreeGenericHandler(final T root) {
        this.originalRoot = root;
        root.setHandler(this);
        initClone();
    }

    /**
     * Initializes the filtered version of the report by cloning the original report with the current filter options. This method is
     * called in the constructor and whenever the filter options are updated to ensure that the filtered version of the report
     * reflects the current filtering criteria.
     */
    @Override
    public void initClone() {
        filteredRoot = originalRoot.clone(filterOptions);
    }

    /**
     * Updates the filter options for the report tree and re-initializes the filtered version of the report. This method takes a set
     * of filter options as input, updates the internal filterOptions field, and then calls the initClone method to create a new
     * filtered version of the report based on the updated filter options.
     *
     * @param filterOptions the set of filter options to be applied to the report tree
     */
    @Override
    public void filter(Set<FilterOptions> filterOptions) {
        this.filterOptions = filterOptions;
        initClone();
    }

    /**
     * Retrieves the current set of filter options applied to the report tree. This method returns a copy of the internal
     * filterOptions set to ensure that the caller cannot modify the internal state of the handler. If there are no filter options
     * applied, it returns an empty EnumSet.
     *
     * @return an EnumSet containing the current filter options applied to the report tree
     */
    @Override
    public EnumSet<FilterOptions> getFilterOptions() {
        if (filterOptions.isEmpty())
            return EnumSet.noneOf(FilterOptions.class);
        return EnumSet.copyOf(filterOptions);
    }

    /**
     * Retrieves the filtered version of the report. This method returns the current state of the filtered report, which reflects
     * the original report with the applied filter options. The filtered report is updated whenever the filter options are changed
     * through the filter method.
     *
     * @return the filtered version of the report
     */
    @Override
    public T getFilteredReport() {
        return filteredRoot;
    }

    /**
     * Retrieves the original report that serves as the root of the tree. This method returns the original report that was provided
     * during the construction of the handler. The original report remains unchanged throughout the lifecycle of the handler and is
     * used as the basis for creating the filtered version of the report.
     *
     * @return the original report that serves as the root of the tree
     */
    @Override
    public T getOriginalReport() {
        return originalRoot;
    }
}
