package io.matrix.knowledge;

import java.util.Locale;

/**
 * What an utterance IS (RECON-W42.3).
 *
 * <p>The mission's §5 names the classes a dialogue must distinguish:
 * "question, correction, observation, hypothesis, teach-request, example,
 * failed-explanation, experiment-result".
 *
 * <p><b>Why the class matters.</b> A user's correction must trigger a
 * re-evaluation of the affected knowledge and not an overwriting of it
 * (§5); a teach-request must produce an explanation rather than a fact; a
 * failed-explanation must produce a different explanation rather than the
 * same one again. The system cannot do any of that without knowing which
 * class the utterance is in, and the class is not visible in the words
 * alone — "no, that's wrong" and "what is wrong with that?" share a
 * vocabulary and nothing else.
 *
 * <p>The classifier is deterministic and pattern-based. A pattern it does
 * not know leaves the utterance in the QUESTION class, because that is
 * the class the system already knows how to handle — and a misclassification
 * that turns a question into a correction would rewrite the knowledge on
 * the strength of a phrase.
 */
public final class DialogueAct {

    /** The class of an utterance. */
    public enum Act {
        /** A question about knowledge. */
        QUESTION,
        /** A correction of what the system said. */
        CORRECTION,
        /** An observation the user makes. */
        OBSERVATION,
        /** A hypothesis the user offers. */
        HYPOTHESIS,
        /** A request to be taught. */
        TEACH_REQUEST,
        /** An example the user gives. */
        EXAMPLE,
        /** A signal that the explanation did not land. */
        FAILED_EXPLANATION,
        /** The result of an experiment the user ran. */
        EXPERIMENT_RESULT
    }

    private DialogueAct() {
    }

    /**
     * The class of an utterance.
     *
     * @param utterance the utterance as the user wrote it
     * @return the class
     */
    public static Act classify(String utterance) {
        if (utterance == null || utterance.isBlank()) {
            return Act.QUESTION;
        }
        String lower = utterance.toLowerCase(Locale.ROOT).strip();

        // The order matters: the patterns that mean "the user is telling
        // the system it was wrong" come before the question patterns,
        // because a correction often ends with a question mark.
        if (matches(lower, "no,", "that's wrong", "that is wrong", "not correct", "incorrect",
                "you are wrong", "you're wrong", "that's not right", "that is not right",
                "это неверно", "неверно", "ошибка", "неправильно", "это не так")) {
            return Act.CORRECTION;
        }
        if (matches(lower, "i don't understand", "i do not understand", "doesn't make sense",
                "does not make sense", "i'm confused", "im confused", "unclear",
                "не понимаю", "непонятно", "неясно")) {
            return Act.FAILED_EXPLANATION;
        }
        if (matches(lower, "i tried", "i tested", "i ran", "the result was", "i measured",
                "i observed", "in my experiment", "я попробовал", "я провел", "результат")) {
            return Act.EXPERIMENT_RESULT;
        }
        if (matches(lower, "for example", "for instance", "such as", "e.g.",
                "например", "к примеру")) {
            return Act.EXAMPLE;
        }
        if (matches(lower, "maybe", "perhaps", "possibly", "i think that", "i suppose",
                "what if", "hypothesis", "might be",
                "возможно", "наверное", "пожалуй", "гипотеза", "что если")) {
            return Act.HYPOTHESIS;
        }
        if (matches(lower, "teach me", "explain it", "explain this", "explain how",
                "explain why", "show me how", "walk me through", "how do i",
                "научи меня", "объясни мне", "объясни как", "объясни почему",
                "как мне", "покажи как")) {
            return Act.TEACH_REQUEST;
        }
        if (matches(lower, "i see that", "i notice that", "it seems that", "i observed that",
                "я вижу что", "я замечаю", "похоже что")) {
            return Act.OBSERVATION;
        }
        return Act.QUESTION;
    }

    /**
     * Whether the utterance is a correction.
     *
     * <p>The mission's §5: a correction triggers a re-evaluation of the
     * affected knowledge and not an overwriting of it.
     */
    public static boolean isCorrection(String utterance) {
        return classify(utterance) == Act.CORRECTION;
    }

    /** Whether a pattern occurs in the utterance. */
    private static boolean matches(String text, String... patterns) {
        for (String pattern : patterns) {
            if (text.startsWith(pattern) || text.contains(" " + pattern)
                    || text.contains(" " + pattern + ",") || text.contains(pattern + "?")) {
                return true;
            }
        }
        return false;
    }
}
