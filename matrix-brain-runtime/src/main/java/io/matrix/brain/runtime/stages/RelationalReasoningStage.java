package io.matrix.brain.runtime.stages;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RECON-W22 — RelationalReasoningStage.
 *
 * <p><b>Why this exists.</b> W22 diagnosis showed GENERALIZATION scoring 0/7 because
 * <em>no stage in the pipeline could do relational reasoning at all</em>. The stage
 * roster is Arithmetic (regex), Analogy (seed table), BIR rules (bitmask lookup),
 * HDC retrieval (cosine over stored vectors), Tsetlin (inline classifier) and a
 * deferred MCTS placeholder. None of them can evaluate "A taller than B, B taller
 * than C ⇒ who is shortest?" — that requires composing a relation over the input
 * itself, not retrieving a stored answer. All 7 probes fell through
 * {@code composeReply} to {@code TsetlinStage.reply()}, which returns the empty
 * string, producing {@code answer: ""} at confidence 0.75.
 *
 * <p><b>What it does.</b> Two deterministic inference rules over the input text:</p>
 * <ol>
 *   <li><b>Transitivity over comparative chains</b> — parse "A taller than B, B
 *       taller than C" into a partial order, then answer the superlative question
 *       by walking the order to the requested extreme.</li>
 *   <li><b>Unanimous-attribute propagation</b> — "puppy small; kitten small; cub
 *       small. foal is ?" ⇒ every exemplar shares the attribute, so a novel item
 *       inherits it.</li>
 * </ol>
 *
 * <p><b>Article III (determinism).</b> No randomness, no wall clock, no I/O. All
 * tables are immutable constants. The same input always yields the same answer.</p>
 *
 * <p><b>Article VIII (no shadow logic).</b> {@link #matched()} is false unless a
 * rule genuinely fired; {@link #declined()} states WHY when it did not, so a miss
 * is never a silent zero.</p>
 */
public final class RelationalReasoningStage {

    // ---- Deterministic linguistic tables ------------------------------------

    /**
     * Comparative ("-er") adjective → its opposite. Used to decide whether the
     * superlative asks for the TOP or the BOTTOM of the chain.
     * e.g. chain "A taller than B" plus question "who is shortest" —
     * "tall" and "short" are opposites, so the answer is the chain's minimum.
     */
    private static final Map<String, String> OPPOSITE = Map.ofEntries(
        Map.entry("tall", "short"),
        Map.entry("short", "tall"),
        Map.entry("old", "young"),
        Map.entry("young", "old"),
        Map.entry("fast", "slow"),
        Map.entry("slow", "fast"),
        Map.entry("big", "small"),
        Map.entry("small", "big"),
        Map.entry("long", "short"),
        Map.entry("heavy", "light"),
        Map.entry("rich", "poor"),
        Map.entry("hot", "cold"),
        Map.entry("new", "old"),
        Map.entry("good", "bad"),
        Map.entry("happy", "sad")
    );

    /** Superlative ("-est"/irregular) → its OWN base adjective (not its antonym). */
    private static final Map<String, String> SUPERLATIVE_BASE = Map.ofEntries(
        Map.entry("shortest", "short"),
        Map.entry("tallest", "tall"),
        Map.entry("youngest", "young"),
        Map.entry("oldest", "old"),
        Map.entry("slowest", "slow"),
        Map.entry("fastest", "fast"),
        Map.entry("smallest", "small"),
        Map.entry("biggest", "big"),
        Map.entry("largest", "large"),
        Map.entry("lightest", "light"),
        Map.entry("heaviest", "heavy"),
        Map.entry("poorest", "poor"),
        Map.entry("richest", "rich"),
        Map.entry("coldest", "cold"),
        Map.entry("hottest", "hot")
    );

    /** Question words that request the BOTTOM of the ordering. */
    private static final List<String> BOTTOM_MARKERS =
        List.of("shortest", "youngest", "slowest", "smallest", "lightest",
                "poorest", "coldest", "weakest", "lowest", "fewest", "newest");

    /** Question words that request the TOP of the ordering. */
    private static final List<String> TOP_MARKERS =
        List.of("tallest", "oldest", "fastest", "biggest", "largest", "heaviest",
                "richest", "hottest", "strongest", "highest", "most", "greatest");

    /** Clues that an attribute is being requested rather than a person/object. */
    private static final List<String> ATTRIBUTE_CUES =
        List.of(" is ", " are ", "color", "colour", "adjective");

    // ---- Result type --------------------------------------------------------

    public record RelationalResult(
        boolean matched,
        String reply,
        double confidence,
        String engine,
        String rule,
        String declined
    ) {
        public static RelationalResult miss(String why) {
            return new RelationalResult(false, "", 0.0, "RelationalReasoningStage", "none", why);
        }
    }

    private final StringBuilder trace = new StringBuilder();

    /**
     * Try to answer {@code input}. Never throws; on any parse failure it returns a
     * miss carrying the reason (Article VIII).
     */
    public RelationalResult tryEvaluate(String input) {
        trace.setLength(0);
        if (input == null || input.isBlank()) {
            return RelationalResult.miss("empty-input");
        }
        String text = input.toLowerCase(Locale.ROOT).trim();

        RelationalResult transitivity = tryTransitivity(text);
        if (transitivity.matched()) return transitivity;
        String transitivityReason = transitivity.declined();

        RelationalResult composition = tryUnanimousAttribute(input);
        if (composition.matched()) return composition;
        String compositionReason = composition.declined();

        // Article VIII: report the SPECIFIC reasons, not a generic one. Discarding
        // them would be exactly the "silent zero" the constitution forbids.
        return RelationalResult.miss("transitivity=" + transitivityReason
            + "; composition=" + compositionReason);
    }

    // ---- Rule 1: transitivity over comparative chains -----------------------

    /**
     * Normalise a comparative adjective to its base form: "taller"→"tall",
     * "older"→"old", "faster"→"fast", "bigger"→"big", "richer"→"rich".
     * Handles the consonant-doubling and silent-e conventions.
     */
    private static String baseComparative(String adj) {
        if (adj == null || adj.isEmpty()) return adj;
        if (OPPOSITE.containsKey(adj)) return adj;   // already a base form
        String a = adj;
        if (a.endsWith("er") && a.length() > 3) {
            String stem = a.substring(0, a.length() - 2);
            // taller -> tall+er? no: "tall"+"er"; faster -> fast+er; older -> old+er
            if (OPPOSITE.containsKey(stem)) return stem;
            // doubled final consonant: bigger -> big, richer -> rich
            if (stem.length() > 2 && stem.charAt(stem.length() - 1) == stem.charAt(stem.length() - 2)) {
                String dedoubled = stem.substring(0, stem.length() - 1);
                if (OPPOSITE.containsKey(dedoubled)) return dedoubled;
            }
            // silent-e dropped: nicer -> nice
            if (stem.endsWith("e") && OPPOSITE.containsKey(stem)) return stem;
        }
        return adj;
    }

    /**
     * Parse "A taller than B, B taller than C" and answer the superlative question.
     *
     * <p>Edges are (subject, object) meaning "subject is GREATER than object"
     * along the chain. A topological walk yields the full order; the answer is the
     * head (greatest) or the tail (least) of that order.</p>
     */
    RelationalResult tryTransitivity(String text) {
        // Split into clauses on sentence punctuation and semicolons.
        String[] parts = text.split("[.;,]");
        Map<String, String> greaterThan = new LinkedHashMap<>(); // subject -> object
        String comparative = null;

        for (String rawPart : parts) {
            String part = rawPart.trim();
            if (part.isEmpty()) continue;
            int than = part.indexOf(" than ");
            if (than <= 0) continue;
            String left = part.substring(0, than).trim();
            String right = part.substring(than + 6).trim();
            if (left.isEmpty() || right.isEmpty()) continue;

            // Left is "<subject> <comparative>"; pull the comparative adjective.
            String[] leftWords = left.split("\\s+");
            if (leftWords.length < 2) continue;
            String adj = baseComparative(leftWords[leftWords.length - 1]);
            if (!OPPOSITE.containsKey(adj)) continue;   // not a known comparative
            if (comparative == null) comparative = adj;

            // Subject = everything before the adjective.
            String subject = String.join(" ",
                java.util.Arrays.copyOfRange(leftWords, 0, leftWords.length - 1)).trim();
            // Object: first token of the right side (strip a leading question word).
            String object = right.split("\\s+")[0].replaceAll("[^a-z\\-]", "");
            if (subject.isEmpty() || object.isEmpty()) continue;
            if (subject.equals(object)) continue;
            greaterThan.put(subject, object);
        }

        if (greaterThan.size() < 2) {
            return RelationalResult.miss("comparative-chain-shorter-than-2-edges");
        }

        // Which extreme is requested? The marker alone identifies the question; the
        // axis is resolved against the chain's comparative via SUPERLATIVE_BASE.
        String marker = null;
        for (String m : TOP_MARKERS) {
            if (text.contains(m)) { marker = m; break; }
        }
        if (marker == null) {
            for (String m : BOTTOM_MARKERS) {
                if (text.contains(m)) { marker = m; break; }
            }
        }
        if (marker == null) {
            return RelationalResult.miss("no-superlative-marker-in-question");
        }

        // Resolve the requested extreme against the comparative used in the chain.
        //
        // The chain is oriented "A taller than B" => A is the GREATER element, so
        // the chain head is the maximum and the tail is the minimum. The question
        // selects an extreme along an AXIS. If the superlative's own base adjective
        // is the OPPOSITE of the chain's comparative, the question is asking about
        // the reverse axis, so the head/tail choice flips:
        //   chain "tall", question "shortest" -> short is opposite tall -> TAIL
        //   chain "old",  question "youngest" -> young is opposite old  -> TAIL
        //   chain "old",  question "oldest"   -> old  is NOT opposite   -> HEAD
        String markerBase = SUPERLATIVE_BASE.get(marker);
        String chainOpposite = OPPOSITE.get(comparative);
        boolean opposite = markerBase != null && markerBase.equals(chainOpposite);

        List<String> order = topoOrder(greaterThan);
        if (order.size() < 2) {
            return RelationalResult.miss("chain-not-resolvable-to-a-total-order");
        }

        // head = maximum along the chain's axis; tail = minimum.
        String head = order.get(0);
        String tail = order.get(order.size() - 1);
        String answer = opposite ? tail : head;

        trace.append("chain=").append(order).append(";marker=").append(marker);
        return new RelationalResult(true, answer, 0.8,
            "RelationalReasoningStage", "transitivity", "");
    }

    /**
     * Walk the "greater than" edges into a total order. Head of the returned list
     * is the greatest element (nobody is greater than it); the tail is the least.
     */
    private static List<String> topoOrder(Map<String, String> greaterThan) {
        List<String> all = new ArrayList<>();
        for (Map.Entry<String, String> e : greaterThan.entrySet()) {
            if (!all.contains(e.getKey())) all.add(e.getKey());
            if (!all.contains(e.getValue())) all.add(e.getValue());
        }
        // Greater element first: repeatedly emit nodes that are not the object of
        // any remaining edge.
        List<String> order = new ArrayList<>();
        List<String> remaining = new ArrayList<>(all);
        while (!remaining.isEmpty()) {
            String best = null;
            for (String cand : remaining) {
                boolean isLessThanSomething = false;
                for (Map.Entry<String, String> e : greaterThan.entrySet()) {
                    if (e.getValue().equals(cand)) { isLessThanSomething = true; break; }
                }
                if (!isLessThanSomething) { best = cand; break; }
            }
            if (best == null) {           // cycle: fall back to insertion order
                order.addAll(remaining);
                break;
            }
            order.add(best);
            remaining.remove(best);
        }
        return order;
    }

    // ---- Rule 2: unanimous-attribute propagation ----------------------------

    /**
     * "puppy small; kitten small; cub small. foal is ?" — when every exemplar
     * carries the SAME attribute value, a novel item in the same series inherits it.
     *
     * <p>Deliberately narrow: it fires only on unanimity. When exemplars disagree
     * (GE-6: red/orange/yellow) the rule declines rather than guessing, because
     * picking one of several values would be an unearned answer.</p>
     */
    RelationalResult tryUnanimousAttribute(String original) {
        String text = original.toLowerCase(Locale.ROOT);

        // The open question at the tail ("... cub small. foal is ?") is NOT an
        // exemplar. Left in the stream it parses as the datum ("foal" -> "is"),
        // which both pollutes the map and breaks unanimity. Cut everything from
        // the last clause boundary that precedes the first '?'.
        String exemplarsText = text;
        int q = text.indexOf('?');
        if (q >= 0) {
            int cut = text.lastIndexOf('.', q);
            if (cut < 0) cut = text.lastIndexOf(';', q);
            if (cut < 0) cut = -1;
            exemplarsText = text.substring(0, cut < 0 ? 0 : cut);
        }

        String[] segments = exemplarsText.split("[.;?]");
        Map<String, String> exemplars = new LinkedHashMap<>();

        for (String seg : segments) {
            String s = seg.trim();
            if (s.isEmpty()) continue;
            String subject;
            String value;
            int is = s.indexOf(" is ");
            if (is > 0) {
                // Explicit copula: "tomato is red"
                subject = s.substring(0, is).trim();
                String predicate = s.substring(is + 4).trim();
                String[] pw = predicate.split("\\s+");
                if (pw.length == 0) continue;
                value = pw[pw.length - 1];
            } else {
                // Copula-less series: "puppy small" (GE-7). Exactly two tokens is
                // the only shape accepted — anything longer is prose, not a datum.
                String[] w = s.split("\\s+");
                if (w.length != 2) continue;
                if (!w[1].matches("[a-z\\-]+")) continue;
                if (isCopula(w[1])) continue;   // "foal is ?" tail, not a datum
                subject = w[0];
                value = w[1];
            }
            if (subject.isEmpty() || value.isEmpty()) continue;
            if (isCopula(value)) continue;
            if (subject.contains("?")) continue;
            exemplars.put(subject, value);
        }

        // Need at least two agreeing exemplars plus a trailing open question.
        if (exemplars.size() < 2) {
            return RelationalResult.miss("fewer-than-2-exemplars");
        }
        if (!text.contains("?")) {
            return RelationalResult.miss("no-open-question-at-tail");
        }
        String first = exemplars.values().iterator().next();
        for (String v : exemplars.values()) {
            if (!v.equals(first)) {
                return RelationalResult.miss("exemplar-attributes-disagree-no-unanimity");
            }
        }
        return new RelationalResult(true, first, 0.7,
            "RelationalReasoningStage", "unanimous-attribute", "");
    }

    /** Verbs that can never be an attribute VALUE — they mark the question clause. */
    private static boolean isCopula(String token) {
        if (token == null) return true;
        return switch (token) {
            case "is", "are", "was", "were", "be", "been" -> true;
            default -> false;
        };
    }

    /** Human-readable trace of the last evaluation (Article VIII diagnostics). */
    public String lastTrace() {
        return trace.toString();
    }
}
