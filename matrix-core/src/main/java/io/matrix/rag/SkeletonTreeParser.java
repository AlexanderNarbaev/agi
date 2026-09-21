package io.matrix.rag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Markdown documents into {@link SkeletonNode} trees.
 *
 * <p>Extracts structural hierarchy (headings, paragraphs, lists, code blocks)
 * while preserving breadcrumb paths for each node. Designed for integration
 * with {@link BooleanRag} to provide structure-aware context in retrieval.
 *
 * <p>Parsing rules:
 * <ul>
 *   <li>ATX headings ({@code #}), depth = number of #s</li>
 *   <li>Fenced code blocks ({@code ```}) — entire block as one node</li>
 *   <li>Unordered lists ({@code -}, {@code *}) — depth by indentation level</li>
 *   <li>Ordered lists ({@code 1.}) — depth by indentation level</li>
 *   <li>Everything else — paragraph</li>
 * </ul>
 *
 * <p>Thread-safe — immutable output.
 *
 * <p>Ref: Research Synthesis 2026 Q3 v2 — Pattern B1 (Skeleton Tree RAG)
 *
 * @since 3.25
 */
public final class SkeletonTreeParser {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern FENCE = Pattern.compile("^```");
    private static final Pattern UNORDERED_LIST = Pattern.compile("^(\\s*)[-*]\\s+(.+)$");
    private static final Pattern ORDERED_LIST = Pattern.compile("^(\\s*)\\d+\\.\\s+(.+)$");

    private SkeletonTreeParser() {
        // utility class
    }

    /**
     * Parses a Markdown string into a skeleton tree.
     *
     * @param markdown the markdown content
     * @param title    the document title (becomes root breadcrumb)
     * @return root SkeletonNode
     */
    public static SkeletonNode parse(String markdown, String title) {
        String[] lines = markdown.split("\n");
        SkeletonNode root = SkeletonNode.root(title);

        List<SkeletonNode> children = new ArrayList<>();
        // Breadcrumb stack: keeps track of current heading path
        List<String> headingStack = new ArrayList<>();
        headingStack.add(title);

        StringBuilder paragraphBuf = new StringBuilder();
        StringBuilder codeBuf = null;
        List<String> codeLines = null;
        int listIndent = -1;

        for (String line : lines) {
            String trimmed = line.stripTrailing();

            // ── Fenced code block ──
            if (FENCE.matcher(trimmed).matches()) {
                if (codeBuf == null) {
                    // Start code block
                    flushParagraph(paragraphBuf, children, headingStack, 1);
                    codeBuf = new StringBuilder();
                    codeLines = new ArrayList<>();
                } else {
                    // End code block
                    String code = codeBuf.toString().stripTrailing();
                    if (!code.isEmpty()) {
                        String breadcrumb = buildBreadcrumb(headingStack) + " > Code";
                        children.add(SkeletonNode.of(
                                SkeletonNode.NodeType.CODE_BLOCK, 1,
                                code, breadcrumb, List.of()));
                    }
                    codeBuf = null;
                    codeLines = null;
                }
                continue;
            }
            if (codeBuf != null) {
                codeBuf.append(trimmed).append("\n");
                continue;
            }

            // ── Empty line: flush paragraph ──
            if (trimmed.isBlank()) {
                flushParagraph(paragraphBuf, children, headingStack, 1);
                listIndent = -1;
                continue;
            }

            // ── Heading ──
            Matcher hm = HEADING.matcher(trimmed);
            if (hm.matches()) {
                flushParagraph(paragraphBuf, children, headingStack, 1);
                int level = hm.group(1).length();
                String text = hm.group(2).trim();

                // Update breadcrumb stack
                while (headingStack.size() > level) {
                    headingStack.remove(headingStack.size() - 1);
                }
                headingStack.add(text);

                String breadcrumb = buildBreadcrumb(headingStack);
                children.add(SkeletonNode.of(
                        SkeletonNode.NodeType.HEADING, level,
                        text, breadcrumb, List.of()));
                listIndent = -1;
                continue;
            }

            // ── Unordered list ──
            Matcher ulm = UNORDERED_LIST.matcher(trimmed);
            if (ulm.matches()) {
                flushParagraph(paragraphBuf, children, headingStack, 1);
                int indent = ulm.group(1).length() / 2;
                String text = ulm.group(2).trim();
                String breadcrumb = buildBreadcrumb(headingStack) + " > List";
                children.add(SkeletonNode.of(
                        SkeletonNode.NodeType.LIST_ITEM, indent + 1,
                        text, breadcrumb, List.of()));
                listIndent = indent;
                continue;
            }

            // ── Ordered list ──
            Matcher olm = ORDERED_LIST.matcher(trimmed);
            if (olm.matches()) {
                flushParagraph(paragraphBuf, children, headingStack, 1);
                int indent = olm.group(1).length() / 2;
                String text = olm.group(2).trim();
                String breadcrumb = buildBreadcrumb(headingStack) + " > List";
                children.add(SkeletonNode.of(
                        SkeletonNode.NodeType.LIST_ITEM, indent + 1,
                        text, breadcrumb, List.of()));
                listIndent = indent;
                continue;
            }

            // ── Regular text → paragraph ──
            if (paragraphBuf.length() > 0) {
                paragraphBuf.append(" ");
            }
            paragraphBuf.append(trimmed);
            listIndent = -1;
        }

        flushParagraph(paragraphBuf, children, headingStack, 1);

        return root.withChildren(children);
    }

    /**
     * Parses a Markdown file into a skeleton tree.
     *
     * @param path  path to the markdown file
     * @param title document title
     * @return root SkeletonNode
     * @throws IOException if file cannot be read
     */
    public static SkeletonNode parseFile(Path path, String title) throws IOException {
        String content = Files.readString(path);
        return parse(content, title);
    }

    /**
     * Finds all sections (heading nodes) matching the given heading text.
     */
    public static List<SkeletonNode> findSections(SkeletonNode root, String headingText) {
        List<SkeletonNode> results = new ArrayList<>();
        for (SkeletonNode child : root.children()) {
            if (child.type() == SkeletonNode.NodeType.HEADING
                    && child.content().toLowerCase().contains(headingText.toLowerCase())) {
                results.add(child);
            }
            results.addAll(findSections(child, headingText));
        }
        return results;
    }

    /**
     * Finds the leaf node whose content best matches the given text snippet,
     * returning its breadcrumb path. Used to add document structure context
     * to RAG retrieval results.
     *
     * @return breadcrumb string, or the root title if no match
     */
    public static String findBreadcrumbFor(SkeletonNode root, String text) {
        if (text == null || text.isBlank()) {
            return root.breadcrumb();
        }
        // Look for the deepest node containing this text
        SkeletonNode match = findDeepestMatch(root, text);
        return match != null ? match.breadcrumb() : root.breadcrumb();
    }

    private static SkeletonNode findDeepestMatch(SkeletonNode node, String text) {
        SkeletonNode best = null;
        if (node.content().toLowerCase().contains(text.toLowerCase())) {
            best = node;
        }
        for (SkeletonNode child : node.children()) {
            SkeletonNode childMatch = findDeepestMatch(child, text);
            if (childMatch != null && (best == null || childMatch.depth() > best.depth())) {
                best = childMatch;
            }
        }
        return best;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static void flushParagraph(StringBuilder buf, List<SkeletonNode> children,
                                        List<String> headings, int depth) {
        String text = buf.toString().trim();
        buf.setLength(0);
        if (!text.isEmpty()) {
            String breadcrumb = buildBreadcrumb(headings);
            children.add(SkeletonNode.of(
                    SkeletonNode.NodeType.PARAGRAPH, depth,
                    text, breadcrumb, List.of()));
        }
    }

    private static String buildBreadcrumb(List<String> headings) {
        return String.join(" > ", headings);
    }
}
