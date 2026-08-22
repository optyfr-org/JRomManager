package jrm.ui.batch;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.swing.tree.TreeNode;

import jrm.batch.TrntChkReport;
import jrm.batch.TrntChkReport.Child;
import jrm.ui.profile.report.ReportNodeGeneric;
import lombok.Getter;

/**
 * Tree node representing a torrent check report.
 * <p>
 * Provides a hierarchical view of torrent check results with cached child nodes.
 */
public class BatchTrrntChkReportNode extends ReportNodeGeneric<TrntChkReport> {
    private Map<Long, ChildNode> nodeCache = new HashMap<>();

    public BatchTrrntChkReportNode(final TrntChkReport report) {
        super(report);
    }

    public ChildNode getNode(Child child) {
        if (child == null)
            return null;
        ChildNode node;
        if (null == (node = nodeCache.get(child.getUid()))) {
            node = new ChildNode(child);
            nodeCache.put(child.getUid(), node);
        }
        return node;
    }

    @Override
    public TreeNode getChildAt(int childIndex) {
        return getNode(report.getNodes().get(childIndex));
    }

    @Override
    public int getChildCount() {
        return report.getNodes().size();
    }

    @Override
    public int getIndex(TreeNode node) {
        return report.getNodes().indexOf(((ChildNode) node).child);
    }

    @Override
    public boolean isLeaf() {
        return report.getNodes().isEmpty();
    }

    @Override
    public Enumeration<ChildNode> children() {
        return new Enumeration<ChildNode>() {
            private final Iterator<Child> i = report.getNodes().iterator();

            public boolean hasMoreElements() {
                return i.hasNext();
            }

            public ChildNode nextElement() {
                return getNode(i.next());
            }
        };
    }

    /**
     * Tree node representing a child entry in the torrent check report.
     */
    public class ChildNode implements TreeNode {
        /**
         * The child data.
         * @return the child
         */
        private final @Getter Child child;

        /**
         * Constructs a child node.
         *
         * @param child the child data
         */
        public ChildNode(final Child child) {
            this.child = child;
        }

        @Override
        public TreeNode getChildAt(int childIndex) {
            return getNode(child.getChildren() == null ? null : child.getChildren().get(childIndex));
        }

        @Override
        public int getChildCount() {
            return child.getChildren() == null ? 0 : child.getChildren().size();
        }

        @Override
        public TreeNode getParent() {
            return child.getParent() == null ? BatchTrrntChkReportNode.this : getNode(child.getParent());
        }

        @Override
        public int getIndex(TreeNode node) {
            return child.getChildren() == null ? -1 : child.getChildren().indexOf(((ChildNode) node).child);
        }

        @Override
        public boolean getAllowsChildren() {
            return true;
        }

        @Override
        public boolean isLeaf() {
            return child.getChildren() == null || child.getChildren().isEmpty();
        }

        @Override
        public Enumeration<ChildNode> children() {
            return child.getChildren() == null ? null : new Enumeration<ChildNode>() {
                private final Iterator<Child> i = child.getChildren().iterator();

                public boolean hasMoreElements() {
                    return i.hasNext();
                }

                public ChildNode nextElement() {
                    return getNode(i.next());
                }
            };
        }

    }

}
