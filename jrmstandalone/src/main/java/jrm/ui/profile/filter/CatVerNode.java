package jrm.ui.profile.filter;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.swing.tree.TreeNode;

import jrm.profile.filter.CatVer;
import jrm.profile.filter.CatVer.Category;
import jrm.profile.filter.CatVer.Category.SubCategory;
import jrm.ui.basic.AbstractNGTreeNode;

/**
 * Tree node representing a category/version (CatVer) filter.
 * <p>
 * Provides a hierarchical view of machine categories and subcategories
 * for filtering purposes. Caches child category nodes for performance.
 */
public class CatVerNode extends AbstractNGTreeNode {

    /** The underlying CatVer filter data. */
    private final CatVer catver;

    /** Cache of child category nodes keyed by category name. */
    private final Map<String, CategoryNode> categoryNodeCache = new HashMap<>();

    /**
     * Constructs a new CatVer tree node.
     *
     * @param catver the CatVer filter data to represent
     */
    public CatVerNode(final CatVer catver) {
        this.catver = catver;
    }

    public CategoryNode getNode(Category cat) {
        return categoryNodeCache.computeIfAbsent(cat.getPropertyName(), _ -> new CategoryNode(cat));
    }

    @Override
    public Object getUserObject() {
        return catver.getUserObject();
    }

    @Override
    public boolean isSelected() {
        return catver.isSelected();
    }

    @Override
    public void setSelected(boolean selected) {
        catver.setSelected(selected);
    }

    @Override
    public CategoryNode getChildAt(int childIndex) {
        return getNode(catver.getListCategories().get(childIndex));
    }

    @Override
    public int getChildCount() {
        return catver.getListCategories().size();
    }

    @Override
    public TreeNode getParent() {
        return null;
    }

    @Override
    public int getIndex(TreeNode node) {
        return catver.getListCategories().indexOf(((CategoryNode) node).category);
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    @Override
    public boolean isLeaf() {
        return catver.getListCategories().isEmpty();
    }

    @Override
    public Enumeration<CategoryNode> children() {
        return new Enumeration<CategoryNode>() {
            private final Iterator<Category> iterator = catver.getListCategories().iterator();

            @Override
            public boolean hasMoreElements() {
                return iterator.hasNext();
            }

            @Override
            public CategoryNode nextElement() {
                return getNode(iterator.next());
            }
        };
    }

    public final class CategoryNode extends AbstractNGTreeNode {
        private final Category category;

        private final Map<String, SubCategoryNode> subcategoryNodeCache = new HashMap<>();

        public CategoryNode(final Category category) {
            this.category = category;
        }

        public SubCategoryNode getNode(SubCategory subcat) {
            return subcategoryNodeCache.computeIfAbsent(subcat.getPropertyName(), _ -> new SubCategoryNode(subcat));
        }

        @Override
        public Object getUserObject() {
            return category.getUserObject();
        }

        @Override
        public boolean isSelected() {
            return category.isSelected();
        }

        @Override
        public void setSelected(boolean selected) {
            category.setSelected(selected);

        }

        @Override
        public SubCategoryNode getChildAt(int childIndex) {
            return getNode(category.getListSubCategories().get(childIndex));
        }

        @Override
        public int getChildCount() {
            return category.getListSubCategories().size();
        }

        @Override
        public CatVerNode getParent() {
            return CatVerNode.this;
        }

        @Override
        public int getIndex(TreeNode node) {
            return category.getListSubCategories().indexOf(((SubCategoryNode) node).subcategory);
        }

        @Override
        public boolean getAllowsChildren() {
            return true;
        }

        @Override
        public boolean isLeaf() {
            return category.getListSubCategories().isEmpty();
        }

        @Override
        public Enumeration<SubCategoryNode> children() {
            return new Enumeration<SubCategoryNode>() {
                private final Iterator<SubCategory> iterator = category.getListSubCategories().iterator();

                @Override
                public SubCategoryNode nextElement() {
                    return getNode(iterator.next());
                }

                @Override
                public boolean hasMoreElements() {
                    return iterator.hasNext();
                }
            };
        }

        public final class SubCategoryNode extends AbstractNGTreeNode {
            private final SubCategory subcategory;

            public SubCategoryNode(final SubCategory subcategory) {
                this.subcategory = subcategory;
            }

            @Override
            public Object getUserObject() {
                return subcategory.getUserObject();
            }

            @Override
            public boolean isSelected() {
                return subcategory.isSelected();
            }

            @Override
            public void setSelected(boolean selected) {
                subcategory.setSelected(selected);
            }

            @Override
            public CategoryNode getParent() {
                return CategoryNode.this;
            }

        }

    }

}
