package jrm.misc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * A simple generic tree data structure containing nodes linked to parent and child nodes.
 * 
 * @param <T> the type of data stored within the tree nodes
 * 
 * @author optyfr
 */
public class Tree<T> {
    /**
     * The root node of this tree structure.
     * 
     * @return the root node
     */
    private @Getter Node<T> root;

    /**
     * Constructs a new {@code Tree} with the specified generic root data.
     * 
     * @param rootData the data to store in the root node
     */
    public Tree(T rootData) {
        root = new Node<>(rootData);
    }

    /**
     * Represents a single logical node inside the {@link Tree} structure. Implements {@link Iterable} over its direct children.
     * 
     * @param <T> the type of data stored within this node
     */
    public static class Node<T> implements Iterable<Node<T>> {
        /**
         * The generic data payload stored within this node.
         * 
         * @param data the data payload to set
         * 
         * @return the generic data payload
         */
        private @Getter @Setter T data;

        /**
         * The parent node of this node, or {@code null} if this is the root.
         * 
         * @return the parent node
         */
        private @Getter Node<T> parent = null;

        /**
         * The list of child nodes branching off from this node.
         */
        private final List<Node<T>> children;

        /**
         * Constructs a new {@code Node} with the specified data.
         * 
         * @param data the generic data payload
         */
        public Node(T data) {
            this.data = data;
            this.children = new ArrayList<>();
        }

        /**
         * Creates and appends a child node with the specified data to this node.
         * 
         * @param child the generic data for the child node to create
         * 
         * @return the newly created child node
         */
        public Node<T> addChild(T child) {
            final var childNode = new Node<>(child);
            childNode.parent = this;
            this.children.add(childNode);
            return childNode;
        }

        /**
         * Retrieves the number of child nodes branching off from this node.
         * 
         * @return the direct child count
         */
        public int getChildCount() {
            return children.size();
        }

        /**
         * Returns an iterator over the direct child nodes.
         * 
         * @return a child nodes iterator
         */
        @Override
        public Iterator<Node<T>> iterator() {
            return children.iterator();
        }

    }
}
