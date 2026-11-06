package io.matrix.knowledge;

import java.util.List;

/**
 * Explicit boolean features for a learner (owner directive §24).
 *
 * <p>The audit behind this mission found that every learner in the repository
 * consumes a HASH of the text: an FNV hash of the first 64 characters, or a
 * 256-bit hash of "input + reply". Two questions that mean the same thing
 * therefore share no features at all, and a learner trained on hashes cannot
 * generalise to a phrasing it has not seen.
 *
 * <p>This class produces the explicit features instead: the presence of the
 * key terms of an assessment item, whether the answer names the concept, the
 * shape of the answer, and the item's own dimensions. The vector is exactly
 * 20 bits wide, because the Tsetlin machine's input width is capped at 20 by
 * CONSTITUTION Article II, and the fold into that width is explicit and
 * inspectable rather than a parity hash.
 *
 * <p>The layout is fixed and documented, because a learner trained on these
 * features is only meaningful if the features can be read back:
 *
 * <pre>
 *   bits  0.. 7  the key terms of the item, in the item's own order
 *   bit   8      the answer shares the item's own wording
 *   bit   9      the answer contains a negation
 *   bit  10      the answer repeats a key term
 *   bits 11..14  the dimension the item measures, encoded
 *   bit  15      the answer is long
 * </pre>
 *
 * The width is 16 and not the 20 that CONSTITUTION Article II allows,
 * because the distillation of the learned rule enumerates the whole input
 * space: at 20 bits that is a million vectors, and each one is evaluated
 * against every clause. Sixteen bits is sixty-four thousand, which is fast
 * enough to run in a test and still wide enough for the features above.
 */
public final class TextFeatureVector {

    /** The width of the feature vector, and of the Tsetlin input. */
    public static final int WIDTH = 16;

    private TextFeatureVector() {
    }

    /**
     * The features of an answer to an item.
     *
     * @param item the item that was answered
     * @param answer what the learner answered
     */
    public static long features(AssessmentItem item, String answer) {
        String text = answer == null ? "" : answer;
        List<String> present = TextFeatures.tokens(text);
        long bits = 0L;

        // Bits 0..7: the key terms of the item, in the item's own order.
        for (int i = 0; i < item.keyTerms().size() && i < 8; i++) {
            String term = TextFeatures.fold(item.keyTerms().get(i).toLowerCase(java.util.Locale.ROOT));
            if (present.contains(term)) {
                bits |= 1L << i;
            }
        }

        // Bit 8: the answer shares the item's own wording.
        if (sharesTheItemsWording(item, present)) {
            bits |= 1L << 8;
        }

        // Bit 9: the answer contains a negation.
        if (containsNegation(text)) {
            bits |= 1L << 9;
        }

        // Bit 10: the answer repeats a key term.
        if (repeatsKeyTerm(item, present)) {
            bits |= 1L << 10;
        }

        // Bits 11..14: the dimension, encoded over four bits.
        int dimension = Math.min(15, item.dimension().ordinal());
        bits |= ((long) dimension) << 11;

        // Bit 15: the answer is long.
        if (present.size() > item.keyTerms().size() * 2) {
            bits |= 1L << 15;
        }

        return bits;
    }

    private static boolean sharesTheItemsWording(AssessmentItem item, List<String> answerTerms) {
        // The item's prompt usually names the concept; an answer that repeats
        // the prompt's own distinctive words is what this bit catches.
        List<String> promptTerms = TextFeatures.tokens(item.prompt());
        if (promptTerms.isEmpty() || answerTerms.isEmpty()) {
            return false;
        }
        int shared = 0;
        for (String term : promptTerms) {
            if (answerTerms.contains(term)) {
                shared++;
            }
        }
        return shared >= 2;
    }

    /**
     * Whether the text contains a negation.
     *
     * <p>The RAW words are read, not the content terms: "not" is a
     * stopword, so the content tokens never carry it, and a negation check
     * that reads the content tokens cannot see the most common English
     * negation at all. That was a live defect - the negation bit could
     * never fire for "not".
     */
    private static boolean containsNegation(String answer) {
        for (String term : TextFeatures.rawWords(answer)) {
            switch (term) {
                case "not", "no", "never", "cannot", "nothing", "neither", "nor" -> {
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }

    private static boolean repeatsKeyTerm(AssessmentItem item, List<String> answerTerms) {
        for (String term : item.keyTerms()) {
            String folded = TextFeatures.fold(term.toLowerCase(java.util.Locale.ROOT));
            int count = 0;
            for (String candidate : answerTerms) {
                if (candidate.equals(folded)) {
                    count++;
                }
            }
            if (count > 1) {
                return true;
            }
        }
        return false;
    }

    /** The feature vector as a list of booleans, for display and for tests. */
    public static List<Boolean> bits(long features) {
        List<Boolean> out = new java.util.ArrayList<>(WIDTH);
        for (int i = 0; i < WIDTH; i++) {
            out.add((features & (1L << i)) != 0);
        }
        return out;
    }
}
