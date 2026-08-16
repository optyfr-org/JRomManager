package jrm.ui.profile.report;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import jrm.aui.profile.report.ReportTreeHandler;
import jrm.profile.report.FilterOptions;
import jrm.profile.report.ReportIntf;

/**
 * Generic base class for report tree models.
 * <p>
 * Provides filtering and cloning support for report tree models.
 */
public abstract class ReportTreeModelGeneric<T extends ReportIntf<T>> extends DefaultTreeModel implements ReportTreeHandler<T> {
    /** The original unfiltered report root. */
    protected transient T orgRoot;
    /** The current set of filter options applied to the tree. */
    protected transient Set<FilterOptions> filterOptions = new HashSet<>();

    protected ReportTreeModelGeneric(TreeNode root) {
        super(root);
    }

    public void initClone() {
        setRoot(getNodeInstance(orgRoot.clone(filterOptions)));
    }

    public abstract ReportNodeGeneric<T> getNodeInstance(T report);

    @Override
    public T getOriginalReport() {
        return orgRoot;
    }

    /**
     * Gets the filter options.
     *
     * @return the filter options
     */
    public EnumSet<FilterOptions> getFilterOptions() {
        if (filterOptions.isEmpty())
            return EnumSet.noneOf(FilterOptions.class);
        return EnumSet.copyOf(filterOptions);
    }

    /**
     * Filter.
     *
     * @param filterOptions the filter options
     */
    @Override
    public void filter(final FilterOptions... filterOptions) {
        filter(Set.of(filterOptions));
    }

    /**
     * Filter.
     *
     * @param filterOptions the filter options
     */
    public void filter(final Set<FilterOptions> filterOptions) {
        this.filterOptions = filterOptions;
        setRoot(getNodeInstance(orgRoot.clone(filterOptions)));
    }

    @SuppressWarnings("unchecked")
    @Override
    public T getFilteredReport() {
        return ((ReportNodeGeneric<T>) getRoot()).getReport();
    }

    @Override
    public boolean hasListeners() {
        return getTreeModelListeners().length > 0;
    }

    @Override
    public void notifyInsertion(int[] childIndices, Object[] children) {
        if (getTreeModelListeners().length > 0) {
            final TreeModelEvent event = new TreeModelEvent(this, getPathToRoot((ReportNode) getRoot()), childIndices, children);
            for (final TreeModelListener l : getTreeModelListeners())
                l.treeNodesInserted(event);
        }
    }

}
