package io.matrix.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SkeletonNode}.
 */
class SkeletonNodeTest {

    @Test
    void rootNodeShouldHaveCorrectDefaults() {
        SkeletonNode root = SkeletonNode.root("MyDoc");
        assertThat(root.type()).isEqualTo(SkeletonNode.NodeType.ROOT);
        assertThat(root.depth()).isEqualTo(0);
        assertThat(root.content()).isEqualTo("MyDoc");
        assertThat(root.breadcrumb()).isEqualTo("MyDoc");
        assertThat(root.children()).isEmpty();
        assertThat(root.isLeaf()).isTrue();
    }

    @Test
    void headingNodeShouldHaveCorrectProperties() {
        SkeletonNode heading = SkeletonNode.of(
                SkeletonNode.NodeType.HEADING, 2,
                "Section 1", "MyDoc > Section 1", List.of());

        assertThat(heading.type()).isEqualTo(SkeletonNode.NodeType.HEADING);
        assertThat(heading.depth()).isEqualTo(2);
        assertThat(heading.breadcrumb()).isEqualTo("MyDoc > Section 1");
        assertThat(heading.isLeaf()).isTrue();
    }

    @Test
    void addChildShouldCreateNewNode() {
        SkeletonNode root = SkeletonNode.root("Doc");
        SkeletonNode child = SkeletonNode.of(
                SkeletonNode.NodeType.PARAGRAPH, 1,
                "Some text", "Doc", List.of());

        SkeletonNode withChild = root.addChild(child);

        assertThat(root.children()).isEmpty(); // immutable
        assertThat(withChild.children()).hasSize(1);
        assertThat(withChild.children().get(0)).isEqualTo(child);
    }

    @Test
    void withChildrenShouldReplaceChildren() {
        SkeletonNode root = SkeletonNode.root("Doc");
        SkeletonNode child1 = SkeletonNode.of(
                SkeletonNode.NodeType.PARAGRAPH, 1, "A", "Doc", List.of());
        SkeletonNode child2 = SkeletonNode.of(
                SkeletonNode.NodeType.PARAGRAPH, 1, "B", "Doc", List.of());

        SkeletonNode updated = root.withChildren(List.of(child1, child2));
        assertThat(updated.children()).hasSize(2);
    }

    @Test
    void sizeShouldCountAllNodesInTree() {
        SkeletonNode root = SkeletonNode.root("Doc");
        SkeletonNode child = SkeletonNode.of(
                SkeletonNode.NodeType.HEADING, 1, "H1", "Doc > H1", List.of());
        SkeletonNode grandchild = SkeletonNode.of(
                SkeletonNode.NodeType.PARAGRAPH, 2, "Text", "Doc > H1", List.of());

        SkeletonNode tree = root
                .addChild(child.addChild(grandchild));

        assertThat(tree.size()).isEqualTo(3);
    }

    @Test
    void maxDepthShouldReturnMaxDepthInTree() {
        SkeletonNode root = SkeletonNode.root("Doc");
        SkeletonNode h1 = SkeletonNode.of(
                SkeletonNode.NodeType.HEADING, 1, "H1", "Doc > H1", List.of());
        SkeletonNode h2 = SkeletonNode.of(
                SkeletonNode.NodeType.HEADING, 2, "H2", "Doc > H1 > H2", List.of());

        SkeletonNode tree = root.addChild(h1.addChild(h2));
        assertThat(tree.maxDepth()).isEqualTo(2);
    }

    @Test
    void findContainingShouldFindTextInTree() {
        SkeletonNode root = SkeletonNode.root("Doc");
        SkeletonNode h1 = SkeletonNode.of(
                SkeletonNode.NodeType.HEADING, 1,
                "Architecture Overview", "Doc > Architecture Overview", List.of());
        SkeletonNode p = SkeletonNode.of(
                SkeletonNode.NodeType.PARAGRAPH, 2,
                "This is about MPDT neurons", "Doc > Architecture Overview", List.of());

        SkeletonNode tree = root.addChild(h1.addChild(p));

        assertThat(tree.findContaining("MPDT")).isNotNull();
        assertThat(tree.findContaining("MPDT").content()).isEqualTo("This is about MPDT neurons");
        assertThat(tree.findContaining("nonexistent")).isNull();
    }

    @Test
    void flattenShouldReturnAllNodesInPreOrder() {
        SkeletonNode root = SkeletonNode.root("R");
        SkeletonNode a = SkeletonNode.of(
                SkeletonNode.NodeType.HEADING, 1, "A", "R > A", List.of());
        SkeletonNode b = SkeletonNode.of(
                SkeletonNode.NodeType.PARAGRAPH, 2, "B", "R > A", List.of());

        SkeletonNode tree = root.addChild(a.addChild(b));
        List<SkeletonNode> flat = tree.flatten();

        assertThat(flat).hasSize(3);
        assertThat(flat.get(0).content()).isEqualTo("R");
        assertThat(flat.get(1).content()).isEqualTo("A");
        assertThat(flat.get(2).content()).isEqualTo("B");
    }

    @Test
    void equalsShouldWorkCorrectly() {
        SkeletonNode a = SkeletonNode.root("Doc");
        SkeletonNode b = SkeletonNode.root("Doc");
        SkeletonNode c = SkeletonNode.root("Other");

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void toStringShouldShowKeyInfo() {
        SkeletonNode node = SkeletonNode.of(
                SkeletonNode.NodeType.HEADING, 3,
                "Long section title that should be truncated in toString",
                "Doc > Long section title...", List.of());

        String str = node.toString();
        assertThat(str).contains("HEADING").contains("depth=3").contains("breadcrumb");
    }
}
