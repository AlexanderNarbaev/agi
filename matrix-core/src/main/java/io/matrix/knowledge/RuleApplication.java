package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * RECON-W42.7 — the application of a stated rule to a new instance.
 *
 * <p>The mission's §7 names the <b>transfer</b> category: a learner who
 * has grasped a principle must apply it to an instance the principle was
 * not stated in. The workspace can state the principle — the logic package
 * holds modus ponens — and it could not apply it: a question that stated a
 * conditional and asked what followed was answered with the definition of
 * the rule and nothing about the instance.
 *
 * <p>This class recognises the narrow shape of such a question:
 *
 * <ol>
 *   <li>the question states a conditional — "if <i>P</i> then <i>Q</i>";</li>
 *   <li>the question asserts <i>P</i>;</li>
 *   <li>the question asks what follows.</li>
 * </ol>
 *
 * <p>and it reports the <i>Q</i> the question itself stated. Nothing is
 * invented: the consequent is the user's own text, and the application is
 * grounded in a claim the workspace holds — the rule itself. A workspace
 * that does not hold the rule is not licensed to apply it, and this class
 * says so rather than reasoning on its own.
 */
public final class RuleApplication {

    /** The question states a conditional and asks what follows from it. */
    public record Application(
            String antecedent,
            String consequent,
            String assertion,
            String groundingClaimId,
            String groundingProposition) {
    }

    /** The words that ask for what follows. */
    private static final List<String> ASK_MARKERS = List.of(
            "what must be true",
            "what must follow",
            "what follows",
            "what can be concluded",
            "what should follow",
            "what is entailed",
            "what has to be true");

    /** The words that ask what is true, without asking for an inference. */
    private static final List<String> NOT_AN_INFERENCE = List.of(
            "what is true", "what is the truth");

    /**
     * The terms a claim must carry for the workspace to hold the rule.
     *
     * <p>The terms are STEMS, and the English fold turns "implies" into
     * "impli" - the "y" is lost with the "es". So the rule is looked for by
     * the PREFIX, which survives every folding, and by the word "true",
     * which the fold leaves alone.
     */
    private static final String RULE_TERM_PREFIX = "impl";
    private static final List<String> RULE_TERMS = List.of("true");

    private RuleApplication() {
    }

    /**
     * Whether the question states a conditional and asks what follows.
     *
     * @param question the question
     * @return whether it has that shape
     */
    public static boolean asksForAnApplication(String question) {
        return parse(question) != null;
    }

    /**
     * The application of the stated rule, if the workspace holds the rule.
     *
     * @param question the question
     * @param claims the claims the workspace holds
     * @return the application, or null when the question has no such shape
     *     or the workspace holds no rule that licenses it
     */
    public static Application apply(String question, List<Claim> claims) {
        StatedConditional conditional = parse(question);
        if (conditional == null) {
            return null;
        }
        for (Claim claim : claims) {
            if (claim.status() == ClaimStatus.REFUTED || claim.status() == ClaimStatus.SUPERSEDED) {
                continue;
            }
            Set<String> terms = TextFeatures.terms(claim.proposition());
            boolean licensed = RULE_TERMS.stream().allMatch(terms::contains)
                    && terms.stream().anyMatch(term -> term.startsWith(RULE_TERM_PREFIX));
            if (licensed) {
                return new Application(conditional.antecedent(), conditional.consequent(),
                        conditional.assertion(), claim.id(), claim.proposition());
            }
        }
        return null;
    }

    /**
     * The statement of the application, grounded in the rule's claim.
     *
     * @param application the application
     * @return the statement
     */
    public static String statementOf(Application application) {
        String assertion = application.assertion().strip();
        if (!assertion.isEmpty() && Character.isUpperCase(assertion.charAt(0))) {
            assertion = Character.toLowerCase(assertion.charAt(0)) + assertion.substring(1);
        }
        return "The workspace holds this rule: " + application.groundingProposition()
                + "\n\nApplied here: if " + application.antecedent() + ", then "
                + application.consequent() + "; and " + assertion
                + ". So: " + application.consequent() + ".";
    }

    /** The conditional the question states, its antecedent and its consequent. */
    private record StatedConditional(String antecedent, String consequent, String assertion) {
    }

    /**
     * The conditional the question states.
     *
     * @param question the question
     * @return the conditional, or null
     */
    private static StatedConditional parse(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }
        String lower = question.toLowerCase(Locale.ROOT);
        boolean asks = ASK_MARKERS.stream().anyMatch(lower::contains)
                && NOT_AN_INFERENCE.stream().noneMatch(lower::contains);
        if (!asks) {
            return null;
        }
        int ifAt = lower.indexOf("if ");
        if (ifAt < 0) {
            return null;
        }
        int thenAt = lower.indexOf(" then ", ifAt);
        if (thenAt < 0) {
            return null;
        }
        String antecedent = question.substring(ifAt + 3, thenAt).strip();
        String after = question.substring(thenAt + 6);
        // The consequent ends at the end of the sentence it is stated in.
        int stop = indexOfSentenceEnd(after);
        String consequent = (stop < 0 ? after : after.substring(0, stop)).strip();
        if (antecedent.isEmpty() || consequent.isEmpty()) {
            return null;
        }
        String assertion = findAssertion(question, antecedent);
        if (assertion == null) {
            return null;
        }
        return new StatedConditional(antecedent, consequent, assertion);
    }

    /**
     * The sentence in the question that asserts the antecedent.
     *
     * @param question the question
     * @param antecedent the antecedent
     * @return the assertion, or null when none does
     */
    private static String findAssertion(String question, String antecedent) {
        Set<String> antecedentTerms = new LinkedHashSet<>(TextFeatures.terms(antecedent));
        if (antecedentTerms.isEmpty()) {
            return null;
        }
        for (String sentence : sentences(question)) {
            String trimmed = sentence.strip();
            if (trimmed.isEmpty() || trimmed.toLowerCase(Locale.ROOT).contains(" then ")) {
                continue;
            }
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("if ")) {
                continue;
            }
            Set<String> terms = new LinkedHashSet<>(TextFeatures.terms(trimmed));
            if (terms.isEmpty() || !antecedentTerms.containsAll(terms)) {
                continue;
            }
            return trimmed;
        }
        return null;
    }

    /** The question's sentences. */
    private static List<String> sentences(String question) {
        List<String> sentences = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char character : question.toCharArray()) {
            current.append(character);
            if (character == '.' || character == '?' || character == '!') {
                sentences.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            sentences.add(current.toString());
        }
        return sentences;
    }

    /** The end of the first sentence in the text. */
    private static int indexOfSentenceEnd(String text) {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '.' || character == '?' || character == '!') {
                return index;
            }
        }
        return -1;
    }
}
