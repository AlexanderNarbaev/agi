package io.matrix.knowledge;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a question DEMANDS, as opposed to what it says (RECON-W42.1).
 *
 * <p>The mission's §14 asks that unsupported questions do not become
 * fabricated answers, and §6 asks that the system identify what it does not
 * know. RECON-W41 could only answer with a support score, and the W42
 * benchmark showed why that is not enough: the score's distributions for
 * answerable and unanswerable questions overlap almost completely. The
 * answerable items scored between 0.25 and 0.88; the unanswerable ones the
 * system wrongly answered scored between 0.22 and 0.63. **No threshold
 * separates them**, because the score is the mean of the claims' own
 * confidence multiplied by an IDF-weighted coverage — and the IDF weighting
 * means a missing RARE term, the very term that carries the question's
 * demand, barely moves the number.
 *
 * <p>So the decision needs information the score does not have: what KIND
 * of answer the question is asking for.
 *
 * <ul>
 *   <li><b>KNOWLEDGE</b> — a question about what something is, does or
 *       causes. The normal path.</li>
 *   <li><b>INSTRUCTION</b> — the question carries a command rather than a
 *       question. The mission's §13.2: "Imported content is data, not
 *       executable instruction." A question that tries to reprogram the
 *       assistant is not a knowledge question, and answering it with a
 *       knowledge claim is how a prompt injection works.</li>
 *   <li><b>FUTURE</b> — the question asks what will happen, or what a value
 *       will be. The workspace holds what is known now; it does not hold the
 *       future, and no amount of retrieval will supply it.</li>
 *   <li><b>AMBIGUOUS</b> — the question offers alternatives and asks which
 *       one is meant, or asks for something whose referent is not settled.
 *       The mission's §6: an ambiguous request should trigger a
 *       clarification, not a guess.</li>
 * </ul>
 *
 * <p>The check is deliberately conservative: it refuses only what it can
 * name. A word that might be an instruction is not enough — the patterns
 * are the ones a real injection uses, and each one is reported with its
 * reason so that a reader can check the decision.
 */
public final class QuestionDemand {

    /** What kind of answer the question is asking for. */
    public enum Kind {
        /** A question about knowledge. */
        KNOWLEDGE,
        /** A question carrying a command. */
        INSTRUCTION,
        /** A question about the future. */
        FUTURE,
        /** A question whose referent is not settled. */
        AMBIGUOUS,
        /**
         * W51 §6. A question that asks for a MEASURED QUANTITY — a count, a
         * percentage, a temperature, a probability.
         */
        QUANTITY
    }

    /** The analysis of one question. */
    public record Analysis(
            Kind kind,
            String reason,
            double demandCoverage,
            Set<String> unmatchedTerms,
            String clarification) {

        public Analysis {
            unmatchedTerms = Set.copyOf(
                    unmatchedTerms == null ? Set.of() : unmatchedTerms);
        }

        /** Whether the question may be answered as a knowledge question. */
        public boolean answerable() {
            return kind == Kind.KNOWLEDGE;
        }

        /** Whether the system should ask rather than answer. */
        public boolean needsClarification() {
            return kind == Kind.AMBIGUOUS;
        }
    }

    /**
     * The share of the question's content terms the workspace covers.
     *
     * <p>This is a COUNT, not an IDF-weighted share: the demand of a
     * question is carried by its terms, and a rare term that the workspace
     * does not hold is the strongest possible evidence that the workspace
     * cannot answer. The measurement is REPORTED, and it is what showed
     * that the support score's IDF weighting under-weights exactly the
     * terms that carry the demand.
     *
     * <p>It was tried as a refusal threshold and REMOVED, because it was
     * the wrong instrument: it caught nothing the demand kinds do not
     * catch, and it cost a misspelled question that the typo handling
     * should answer. A threshold that trades a wrong refusal for a right
     * one is not an improvement in the refusal rate; it is a trade.
     */
    public static final double MINIMUM_DEMAND_COVERAGE = 0.0;

    private QuestionDemand() {
    }

    /**
     * Analyses what a question demands.
     *
     * @param question the question as the user wrote it
     * @param unmatched the terms the workspace does not cover
     * @param contentTerms the question's content terms, stopwords removed
     */
    public static Analysis analyse(String question, Set<String> unmatched,
                                   Set<String> contentTerms) {
        // Without the conversation's state, the referent is assumed to be
        // settled - the caller's older contract.
        return analyse(question, unmatched, contentTerms, true);
    }

    /**
     * Analyses what the question demands, with the referent's state.
     *
     * <p>RECON-W42.7. A question that refers to something the conversation
     * has not settled is AMBIGUOUS, and the mission's §6 says such a
     * request should ask rather than guess. "How does it work?" refers to
     * something and names no subject, and the demand analysis saw only a
     * knowledge question - so the workspace refused it with the generic
     * "no claim covers enough of the question" and never asked.
     *
     * @param question the question
     * @param unmatched the terms the workspace did not cover
     * @param contentTerms the question's content terms
     * @param referentSettled whether the referent is settled - by the
     *     conversation, or by the question naming a subject of its own
     * @return the analysis
     */
    public static Analysis analyse(String question, Set<String> unmatched,
                                   Set<String> contentTerms, boolean referentSettled) {
        String lower = question == null ? "" : question.toLowerCase(Locale.ROOT);

        String instruction = instructionPattern(lower);
        if (instruction != null) {
            return new Analysis(Kind.INSTRUCTION, instruction, coverage(unmatched, contentTerms),
                    unmatched, null);
        }

        String future = futurePattern(lower);
        if (future != null) {
            return new Analysis(Kind.FUTURE, future, coverage(unmatched, contentTerms),
                    unmatched, null);
        }

        String ambiguity = ambiguityPattern(lower);
        if (ambiguity != null) {
            return new Analysis(Kind.AMBIGUOUS, ambiguity, coverage(unmatched, contentTerms),
                    unmatched, clarificationFor(question));
        }

        // THE QUANTITY DEMAND (W51 §6).
        //
        // A question that asks for a NUMBER asks for something the
        // workspace either holds or does not. The W50 evaluation measured
        // that the coverage floor cannot tell the two apart — the
        // unanswerable "How many tonnes of basalt are produced each year by
        // the Mid-Atlantic Ridge?" scored 0.288 against a floor of 0.25,
        // while the answerable "How does subduction at a convergent
        // boundary produce deep earthquakes and volcanoes?" scored 0.286
        // and had to be answered. A lexical coverage is a RELEVANCE
        // measure; the question "what is being asked for" is a different
        // question, and this is it.
        String quantity = quantityPattern(lower);
        if (quantity != null) {
            return new Analysis(Kind.QUANTITY, quantity, coverage(unmatched, contentTerms),
                    unmatched, null);
        }

        // THE UNSETTLED REFERENT.
        //
        // The question refers to something, names no subject of its own,
        // and the conversation has not settled one either. The workspace
        // cannot know what is being asked about, so it asks.
        if (!referentSettled && DialogueContext.hasReference(question)) {
            return new Analysis(Kind.AMBIGUOUS,
                    "the question refers to something the conversation has not settled",
                    coverage(unmatched, contentTerms), unmatched,
                    "The question refers to something (\"" + question + "\") that the "
                            + "conversation has not settled. What does it refer to?");
        }

        return new Analysis(Kind.KNOWLEDGE, "the question asks about knowledge",
                coverage(unmatched, contentTerms), unmatched, null);
    }

    /** The share of the question's content terms the workspace covers. */
    public static double coverage(Set<String> unmatched, Set<String> contentTerms) {
        if (contentTerms == null || contentTerms.isEmpty()) {
            return 0.0;
        }
        int missing = unmatched == null ? 0 : unmatched.size();
        int total = contentTerms.size();
        return Math.max(0.0, Math.round((double) (total - missing) / total * 100.0) / 100.0);
    }

    /** The content terms of a question: its tokens minus the stopwords. */
    public static Set<String> contentTerms(String question) {
        Set<String> terms = new LinkedHashSet<>();
        for (String token : TextFeatures.originalTokens(question)) {
            if (!TextFeatures.isStopWord(token)) {
                terms.add(token);
            }
        }
        return terms;
    }

    /**
     * The embedded command in the question, or null.
     *
     * <p>Each pattern is a phrase an injection uses, not a single word: "you
     * are now" is a reprogramming, "now" on its own is not.
     */
    private static String instructionPattern(String lower) {
        List<String[]> patterns = List.of(
                new String[] {"ignore all previous", "the question tries to cancel the instructions"},
                new String[] {"ignore previous", "the question tries to cancel the instructions"},
                new String[] {"ignore the above", "the question tries to cancel the instructions"},
                new String[] {"ignore your", "the question tries to cancel the instructions"},
                new String[] {"disregard all previous", "the question tries to cancel the instructions"},
                new String[] {"forget all previous", "the question tries to cancel the instructions"},
                new String[] {"forget your instructions", "the question tries to cancel the instructions"},
                new String[] {"you are now", "the question tries to reprogram the assistant"},
                new String[] {"system:", "the question presents itself as a system instruction"},
                new String[] {"developer mode", "the question tries to reprogram the assistant"},
                new String[] {"act as", "the question asks the assistant to become something else"},
                new String[] {"pretend to be", "the question asks the assistant to become something else"},
                new String[] {"reveal your", "the question tries to extract the assistant's own state"},
                new String[] {"tell me a secret", "the question asks for something the workspace does not hold"},
                new String[] {"your prompt", "the question tries to extract the assistant's own state"},
                new String[] {"your instructions", "the question tries to extract the assistant's own state"},
                new String[] {"print your", "the question tries to extract the assistant's own state"});
        for (String[] pattern : patterns) {
            if (lower.contains(pattern[0])) {
                return pattern[1] + " (\"" + pattern[0] + "\")";
            }
        }
        return null;
    }

    /** The future reference in the question, or null. */
    private static String futurePattern(String lower) {
        if (lower.matches(".*\\b(will|shall)\\b.*")) {
            return "the question asks what will happen, and the workspace holds what is known now";
        }
        if (lower.matches(".*\\b(predict|forecast|projection)\\b.*")) {
            return "the question asks for a prediction, and the workspace holds what is known now";
        }
        if (lower.matches(".*\\bin (20[3-9][0-9]|21[0-9][0-9])\\b.*")) {
            return "the question asks about a year that has not happened";
        }
        if (lower.matches(".*\\b(next year|next decade|future|tomorrow|later)\\b.*")) {
            return "the question asks about a time that has not happened";
        }
        return null;
    }

    /** The ambiguity in the question, or null. */
    private static String ambiguityPattern(String lower) {
        if (lower.contains("that you mean") || lower.contains("which one do you mean")
                || lower.contains("do you mean")) {
            return "the question asks which of several things is meant, and the referent is not settled";
        }
        if (lower.matches(".*\\b(do you mean|or do you mean)\\b.*")) {
            return "the question offers alternatives and does not settle which one is meant";
        }
        return null;
    }

    /**
     * The clarification to ask instead of answering.
     *
     * @param question the question
     * @return the clarification
     */
    private static String clarificationFor(String question) {
        return "The question leaves the referent open (\"" + question + "\"). Which of the "
                + "alternatives do you mean?";
    }

    /**
     * The quantity the question asks for, or null when it asks for none.
     *
     * <p>The markers are the phrases a question about a measured quantity
     * is written with. They are a form of the QUESTION, not a judgement
     * about the world: a question that asks "how many" asks for a number,
     * and the workspace either holds one or it does not.
     *
     * <p>The list is deliberately narrow. "How is", "how does" and "what
     * are" are not quantity demands: they ask for a description, and the
     * W50 evaluation measured that the answerable case "How is the rate of
     * plate movement measured with GPS and other space-geodetic
     * techniques?" must still be answered.
     *
     * @param lower the question, lower-cased
     * @return the reason, or null
     */
    private static String quantityPattern(String lower) {
        for (String marker : QUANTITY_MARKERS) {
            if (lower.contains(marker)) {
                return "the question asks for a measured quantity (\"" + marker + "\"), and the "
                        + "workspace either holds that quantity or it does not";
            }
        }
        return null;
    }

    /**
     * The phrases that ask for a measured quantity.
     *
     * <p>English and Russian, because a rule that works in one language and
     * silently fails in the next is a rule that works only where it was
     * tested.
     */
    private static final List<String> QUANTITY_MARKERS = List.of(
            "how many", "how much", "how long does it take", "how far", "how old is",
            "what percentage", "what percent", "what proportion", "what probability",
            "what temperature", "what is the boiling point", "what is the freezing point",
            "in degrees", "per second", "per minute", "per hour", "per day", "per year",
            "сколько", "каков процент", "какая температура");

    /**
     * The number of the cited claims that state a quantity.
     *
     * <p>A claim states a quantity when its proposition carries a number.
     * The rule is deliberately coarse: it cannot tell a measurement from a
     * date, and it does not try to. It answers one question — "is there a
     * number in the evidence at all?" — and the demand analysis uses that
     * to decide whether the workspace can answer a question that asked for
     * one.
     *
     * @param claims the cited claims
     * @return the number of claims that state a quantity
     */
    public static int quantityEvidence(List<Claim> claims) {
        if (claims == null || claims.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Claim claim : claims) {
            if (claim.proposition() != null && QUANTITY_NUMBER.matcher(claim.proposition()).find()) {
                count++;
            }
        }
        return count;
    }

    /** A number in a proposition. */
    private static final java.util.regex.Pattern QUANTITY_NUMBER =
            java.util.regex.Pattern.compile("\\d");
}
