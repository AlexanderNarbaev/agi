package io.matrix.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exact-Term Guard — verifies that technical terms from the query
 * appear literally in the retrieved context before allowing generation.
 *
 * <p>Implements the "Exact-Term Guard" pattern: before generation,
 * checks that all identified technical tokens (CamelCase, UPPER_CASE,
 * snake_case, endpoints, quoted strings) are present verbatim in at
 * least one context chunk.
 *
 * <p>Thread-safe: all methods are stateless, regex patterns are
 * pre-compiled as static final fields. GuardResult is an immutable
 * record with defensive copies.
 *
 * <p>Ref: RAG — Exact-Term Verification Guard
 */
public final class ExactTermGuard {

    private ExactTermGuard() {
        // utility class — no instantiation
    }

    // Broad mixed-case: any word containing both upper- and lowercase.
    // Post-filtered via isTechnical() to exclude sentence-initial capitals
    // like "Use", "The" (only the first letter is uppercase, rest lowercase).
    private static final Pattern MIXED_CASE = Pattern.compile(
            "\\b(?=[a-zA-Z]*[a-z])(?=[a-zA-Z]*[A-Z])[a-zA-Z][a-zA-Z0-9]*\\b");

    // UPPER_CASE with underscores — CONSTANT_CASE convention
    private static final Pattern UPPER_CASE = Pattern.compile(
            "\\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\\b");

    // snake_case — lowercase with underscores
    private static final Pattern SNAKE_CASE = Pattern.compile(
            "\\b[a-z][a-z0-9]*(?:_[a-z0-9]+)+\\b");

    // Endpoints: /path/segments
    private static final Pattern ENDPOINT = Pattern.compile(
            "(?:/[a-zA-Z0-9][\\w.-]*)+");

    // Double-quoted strings
    private static final Pattern DOUBLE_QUOTED = Pattern.compile(
            "\"([^\"]*)\"");

    // Single-quoted strings
    private static final Pattern SINGLE_QUOTED = Pattern.compile(
            "'([^']*)'");

    /**
     * Result of the Exact-Term Guard check.
     *
     * @param allowed      whether generation is allowed (all terms matched)
     * @param matchedTerms technical terms found verbatim in context
     * @param missingTerms technical terms not found in any context chunk
     * @param confidence   fraction of terms that matched (0.0–1.0)
     */
    public record GuardResult(boolean allowed, List<String> matchedTerms,
                              List<String> missingTerms, double confidence) {
        public GuardResult {
            matchedTerms = List.copyOf(matchedTerms);
            missingTerms = List.copyOf(missingTerms);
        }
    }

    /**
     * Verifies that all extracted technical terms from {@code query}
     * appear literally in at least one chunk of {@code contextChunks}.
     *
     * @param query         the user query string
     * @param contextChunks the retrieved context chunks to verify against
     * @return a {@link GuardResult} summarizing the outcome
     */
    public static GuardResult check(String query, List<String> contextChunks) {
        if (query == null || query.isBlank()) {
            return new GuardResult(true, Collections.emptyList(),
                    Collections.emptyList(), 1.0);
        }

        List<String> terms = extractTechnicalTerms(query);
        if (terms.isEmpty()) {
            return new GuardResult(true, Collections.emptyList(),
                    Collections.emptyList(), 1.0);
        }

        String joinedContext = contextChunks != null && !contextChunks.isEmpty()
                ? String.join(" ", contextChunks)
                : "";

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String term : terms) {
            if (isTermInContext(term, joinedContext)) {
                matched.add(term);
            } else {
                missing.add(term);
            }
        }

        double confidence = (double) matched.size() / terms.size();
        boolean allowed = missing.isEmpty();

        return new GuardResult(allowed, matched, missing, confidence);
    }

    /**
     * Checks whether a technical term appears as a distinct token
     * in the joined context. For alphanumeric identifiers uses
     * word-boundary matching to avoid substring false positives
     * (e.g. "Use" matching inside "getUserId"). For terms with
     * non-word characters (paths, quoted phrases) uses substring match.
     */
    private static boolean isTermInContext(String term, String context) {
        if (context.isEmpty()) {
            return false;
        }
        if (term.codePoints().allMatch(Character::isLetterOrDigit)) {
            // Identifier token — require word boundaries
            return Pattern.compile("\\b" + Pattern.quote(term) + "\\b")
                    .matcher(context)
                    .find();
        }
        // Term contains special characters — use verbatim substring match
        return context.contains(term);
    }

    /**
     * Returns true if the given mixed-case word is a genuine technical
     * CamelCase/PascalCase identifier — not just a sentence-initial
     * capital (e.g. "Use", "The", "Proper name").
     */
    private static boolean isTechnicalCamelCase(String word) {
        if (word.isEmpty()) {
            return false;
        }
        if (Character.isLowerCase(word.charAt(0))) {
            // Starts lowercase → must have at least one uppercase internally
            for (int i = 1; i < word.length(); i++) {
                if (Character.isUpperCase(word.charAt(i))) {
                    return true;
                }
            }
            return false;
        }
        // Starts uppercase → need at least one more uppercase NOT at position 0
        for (int i = 1; i < word.length(); i++) {
            if (Character.isUpperCase(word.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts technical tokens from a query string.
     * Recognizes: CamelCase, UPPER_CASE, snake_case, endpoints (/path),
     * and quoted strings (content extracted without delimiters).
     *
     * @param query the query string
     * @return an unmodifiable list of extracted technical terms,
     *         in order of appearance
     */
    public static List<String> extractTechnicalTerms(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        List<String> terms = new ArrayList<>();

        // Quoted strings — extract content without quotes
        Matcher doubleQuoteMatcher = DOUBLE_QUOTED.matcher(query);
        while (doubleQuoteMatcher.find()) {
            String content = doubleQuoteMatcher.group(1);
            if (!content.isBlank()) {
                terms.add(content);
            }
        }
        Matcher singleQuoteMatcher = SINGLE_QUOTED.matcher(query);
        while (singleQuoteMatcher.find()) {
            String content = singleQuoteMatcher.group(1);
            if (!content.isBlank()) {
                terms.add(content);
            }
        }

        // Endpoints — full path like /api/v1/users
        Matcher endpointMatcher = ENDPOINT.matcher(query);
        while (endpointMatcher.find()) {
            String ep = endpointMatcher.group();
            if (!terms.contains(ep)) {
                terms.add(ep);
            }
        }

        // CamelCase / PascalCase — filter out sentence-capitalized words
        Matcher camelMatcher = MIXED_CASE.matcher(query);
        while (camelMatcher.find()) {
            String token = camelMatcher.group();
            if (!terms.contains(token) && isTechnicalCamelCase(token)) {
                terms.add(token);
            }
        }

        // UPPER_CASE — CONSTANT_CASE with underscores
        Matcher upperMatcher = UPPER_CASE.matcher(query);
        while (upperMatcher.find()) {
            String token = upperMatcher.group();
            if (!terms.contains(token)) {
                terms.add(token);
            }
        }

        // snake_case — lowercase with underscores
        Matcher snakeMatcher = SNAKE_CASE.matcher(query);
        while (snakeMatcher.find()) {
            String token = snakeMatcher.group();
            if (!terms.contains(token)) {
                terms.add(token);
            }
        }

        return Collections.unmodifiableList(terms);
    }
}
