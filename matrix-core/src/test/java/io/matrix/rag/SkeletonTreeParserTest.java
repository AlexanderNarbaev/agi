package io.matrix.rag;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SkeletonTreeParser}.
 */
class SkeletonTreeParserTest {

    @Test
    void shouldParseSimpleMarkdown() {
        String md = """
                # Title
                Some content here.
                
                ## Subsection
                More text in subsection.
                """;

        SkeletonNode tree = SkeletonTreeParser.parse(md, "TestDoc");

        assertThat(tree.type()).isEqualTo(SkeletonNode.NodeType.ROOT);
        assertThat(tree.children()).hasSize(4); // H1, P, H2, P

        // H1 "Title"
        SkeletonNode h1 = tree.children().get(0);
        assertThat(h1.type()).isEqualTo(SkeletonNode.NodeType.HEADING);
        assertThat(h1.content()).isEqualTo("Title");
        assertThat(h1.depth()).isEqualTo(1);

        // H2 "Subsection"
        SkeletonNode h2 = tree.children().get(2);
        assertThat(h2.type()).isEqualTo(SkeletonNode.NodeType.HEADING);
        assertThat(h2.content()).isEqualTo("Subsection");
        assertThat(h2.depth()).isEqualTo(2);
    }

    @Test
    void shouldParseCodeBlocks() {
        // Using \n instead of text block to avoid escaping issues with triple backticks
        String md = "# Overview\n\n```\npublic class Foo {\n    int x = 1;\n}\n```\n\nEnd text.\n";

        SkeletonNode tree = SkeletonTreeParser.parse(md, "Doc");
        List<SkeletonNode> flat = tree.flatten();

        // Find the code block node
        List<SkeletonNode> codeNodes = flat.stream()
                .filter(n -> n.type() == SkeletonNode.NodeType.CODE_BLOCK)
                .toList();

        assertThat(codeNodes).hasSize(1);
        assertThat(codeNodes.get(0).content()).contains("public class Foo");
    }

    @Test
    void shouldParseUnorderedLists() {
        String md = """
                # Features
                
                - Item one
                - Item two
                - Item three
                """;

        SkeletonNode tree = SkeletonTreeParser.parse(md, "Doc");
        List<SkeletonNode> flat = tree.flatten();

        List<SkeletonNode> items = flat.stream()
                .filter(n -> n.type() == SkeletonNode.NodeType.LIST_ITEM)
                .toList();

        assertThat(items).hasSize(3);
        assertThat(items.get(0).content()).isEqualTo("Item one");
        assertThat(items.get(2).content()).isEqualTo("Item three");
    }

    @Test
    void shouldParseOrderedLists() {
        String md = """
                # Steps
                
                1. First step
                2. Second step
                """;

        SkeletonNode tree = SkeletonTreeParser.parse(md, "Doc");
        List<SkeletonNode> flat = tree.flatten();

        List<SkeletonNode> items = flat.stream()
                .filter(n -> n.type() == SkeletonNode.NodeType.LIST_ITEM)
                .toList();

        assertThat(items).hasSize(2);
        assertThat(items.get(0).content()).isEqualTo("First step");
    }

    @Test
    void breadcrumbsShouldReflectHeadingHierarchy() {
        String md = """
                # Architecture
                
                ## Core
                MPDT neurons.
                
                ### Neuron Layer
                Layer details.
                """;

        SkeletonNode tree = SkeletonTreeParser.parse(md, "MATRIX");

        // Find "Neuron Layer" heading
        SkeletonNode h3 = tree.findContaining("Neuron Layer");
        assertThat(h3).isNotNull();
        assertThat(h3.breadcrumb()).isEqualTo("MATRIX > Architecture > Core > Neuron Layer");
    }

    @Test
    void shouldHandleEmptyMarkdown() {
        SkeletonNode tree = SkeletonTreeParser.parse("", "EmptyDoc");
        assertThat(tree.type()).isEqualTo(SkeletonNode.NodeType.ROOT);
        assertThat(tree.children()).isEmpty();
    }

    @Test
    void shouldHandleMultipleHeadingLevels() {
        String md = """
                # H1
                Text1
                ## H2
                Text2
                ### H3
                Text3
                # Another H1
                Text4
                """;

        SkeletonNode tree = SkeletonTreeParser.parse(md, "Doc");

        List<SkeletonNode> headings = tree.flatten().stream()
                .filter(n -> n.type() == SkeletonNode.NodeType.HEADING)
                .toList();

        assertThat(headings).hasSize(4);
        assertThat(headings.get(0).depth()).isEqualTo(1); // H1
        assertThat(headings.get(1).depth()).isEqualTo(2); // H2
        assertThat(headings.get(2).depth()).isEqualTo(3); // H3
        assertThat(headings.get(3).depth()).isEqualTo(1); // Another H1
    }

    @Test
    void findSectionsShouldFindByHeadingText() {
        String md = """
                # Getting Started
                Intro text.
                
                # Architecture
                Arch text.
                
                # Getting Started with Clusters
                More intro.
                """;

        SkeletonNode tree = SkeletonTreeParser.parse(md, "Doc");
        List<SkeletonNode> sections = SkeletonTreeParser.findSections(tree, "Getting Started");

        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).content()).contains("Getting Started");
    }

    @Test
    void findBreadcrumbForShouldReturnExactMatchPath() {
        String md = """
                # API Reference
                ## REST Endpoints
                ### POST /api/v1/query
                Query the neural system.
                """;

        SkeletonNode tree = SkeletonTreeParser.parse(md, "MATRIX");
        String breadcrumb = SkeletonTreeParser.findBreadcrumbFor(tree, "POST /api/v1/query");

        assertThat(breadcrumb).contains("API Reference")
                .contains("REST Endpoints")
                .contains("POST /api/v1/query");
    }

    @Test
    void findBreadcrumbForShouldReturnRootForNoMatch() {
        SkeletonNode tree = SkeletonTreeParser.parse("# Hello\nWorld", "Doc");
        String breadcrumb = SkeletonTreeParser.findBreadcrumbFor(tree, "ZZZ");

        assertThat(breadcrumb).isEqualTo("Doc");
    }

    @Test
    void parseFileShouldWorkWithRealFile() throws IOException {
        Path tmp = Files.createTempFile("skeleton-test", ".md");
        Files.writeString(tmp, "# Test\nContent here.");

        SkeletonNode tree = SkeletonTreeParser.parseFile(tmp, "TestFile");
        assertThat(tree.findContaining("Test")).isNotNull();
        assertThat(tree.findContaining("Content here")).isNotNull();

        Files.deleteIfExists(tmp);
    }

    @Test
    void shouldCollapseMultipleParagraphs() {
        String md = """
                Line one.
                Line two continuation.
                Line three more.
                
                New paragraph.
                """;

        SkeletonNode tree = SkeletonTreeParser.parse(md, "Doc");
        List<SkeletonNode> paragraphs = tree.flatten().stream()
                .filter(n -> n.type() == SkeletonNode.NodeType.PARAGRAPH)
                .toList();

        assertThat(paragraphs).hasSize(2);
        assertThat(paragraphs.get(0).content()).contains("Line one")
                .contains("Line two continuation")
                .contains("Line three more");
    }
}
