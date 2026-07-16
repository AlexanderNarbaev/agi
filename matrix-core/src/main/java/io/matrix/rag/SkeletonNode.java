package io.matrix.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A node in a parsed document skeleton tree.
 *
 * <p>Represents a structural element (heading, paragraph, list item, code block)
 * with its content, depth, and path from the root. Used by {@link SkeletonTreeParser}
 * to provide breadcrumb context for RAG retrieval results.
 *
 * <p>Immutable — children are an unmodifiable list.
 *
 * <p>Ref: Research Synthesis 2026 Q3 v2 — Pattern B1 (Skeleton Tree RAG)
 *
 * @since 3.25
 */
public final class SkeletonNode {

    /** The type of structural element this node represents. */
    public enum NodeType {
        /** Document root (virtual). */
        ROOT,
        /** Markdown heading (H1-H6). */
        HEADING,
        /** Text paragraph. */
        PARAGRAPH,
        /** Bullet or numbered list item. */
        LIST_ITEM,
        /** Fenced or indented code block. */
        CODE_BLOCK
    }

    private final NodeType type;
    private final int depth;
    private final String content;
    private final String breadcrumb;
    private final List<SkeletonNode> children;

    private SkeletonNode(NodeType type, int depth, String content, String breadcrumb,
                         List<SkeletonNode> children) {
        this.type = Objects.requireNonNull(type, "type");
        this.depth = depth;
        this.content = Objects.requireNonNull(content, "content");
        this.breadcrumb = Objects.requireNonNull(breadcrumb, "breadcrumb");
        this.children = Collections.unmodifiableList(new ArrayList<>(children));
    }

    /**
     * Creates a root node with the given title.
     */
    public static SkeletonNode root(String title) {
        return new SkeletonNode(NodeType.ROOT, 0, title, title, List.of());
    }

    /**
     * Creates a node with the given parameters.
     */
    public static SkeletonNode of(NodeType type, int depth, String content,
                                   String breadcrumb, List<SkeletonNode> children) {
        return new SkeletonNode(type, depth, content, breadcrumb, children);
    }

    /**
     * Creates a copy of this node with new children.
     */
    public SkeletonNode withChildren(List<SkeletonNode> newChildren) {
        return new SkeletonNode(type, depth, content, breadcrumb, newChildren);
    }

    /**
     * Creates a copy of this node with an appended child.
     */
    public SkeletonNode addChild(SkeletonNode child) {
        List<SkeletonNode> newChildren = new ArrayList<>(this.children);
        newChildren.add(child);
        return new SkeletonNode(type, depth, content, breadcrumb, newChildren);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public NodeType type() { return type; }
    public int depth() { return depth; }
    public String content() { return content; }
    public String breadcrumb() { return breadcrumb; }
    public List<SkeletonNode> children() { return children; }

    /**
     * Returns true if this node is a leaf (has no children).
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }

    /**
     * Returns the total number of nodes in this subtree (including this node).
     */
    public int size() {
        int count = 1;
        for (SkeletonNode child : children) {
            count += child.size();
        }
        return count;
    }

    /**
     * Returns the maximum depth in this subtree.
     */
    public int maxDepth() {
        int max = depth;
        for (SkeletonNode child : children) {
            max = Math.max(max, child.maxDepth());
        }
        return max;
    }

    /**
     * Finds the first descendant node containing the given text (case-insensitive).
     *
     * @return the matching node, or null
     */
    public SkeletonNode findContaining(String text) {
        if (content.toLowerCase().contains(text.toLowerCase())) {
            return this;
        }
        for (SkeletonNode child : children) {
            SkeletonNode found = child.findContaining(text);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Collects all nodes in this subtree into a flat list (pre-order traversal).
     */
    public List<SkeletonNode> flatten() {
        List<SkeletonNode> result = new ArrayList<>();
        flattenInto(result);
        return result;
    }

    private void flattenInto(List<SkeletonNode> target) {
        target.add(this);
        for (SkeletonNode child : children) {
            child.flattenInto(target);
        }
    }

    // ── equals / hashCode / toString ─────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkeletonNode that)) return false;
        return type == that.type
                && depth == that.depth
                && content.equals(that.content)
                && breadcrumb.equals(that.breadcrumb)
                && children.equals(that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, depth, content, breadcrumb, children);
    }

    @Override
    public String toString() {
        return "SkeletonNode{" + type
                + ", depth=" + depth
                + ", content='" + (content.length() > 60 ? content.substring(0, 57) + "..." : content) + "'"
                + ", breadcrumb='" + breadcrumb + "'"
                + ", children=" + children.size()
                + "}";
    }
}
