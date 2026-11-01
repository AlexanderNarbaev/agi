package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The bounded conversation the system is in (RECON-W42.3).
 *
 * <p>The mission's §6 asks for "bounded conversation history", "context
 * tracking" and "reference resolution", and gives the cases to survive:
 * "Explain this in more detail" and "Let's return to the topic we studied
 * yesterday".
 *
 * <p><b>What a turn is.</b> A turn is a question, the concepts the answer
 * rested on, and the language. The concepts are what a later reference
 * resolves against: "this" and "it" point at the subject of the last turn,
 * and the resolution is the CONCEPT, not a guess about the words.
 *
 * <p><b>The history is bounded</b> — both in the number of turns and in the
 * number of concepts each turn keeps — because a conversation that grows
 * without bound is a memory leak with a conversation's manners. The bound
 * is a constant, not a configuration knob that someone will turn up.
 *
 * <p><b>What the context is not.</b> It is not a summary and it is not a
 * model of the user. It is a bounded list of turns and a rule for
 * resolving a reference against it. The rule is deliberately narrow: a
 * question is treated as referential only when it carries a reference word
 * AND names no subject of its own, so that "What is the Calvin cycle?"
 * is never rewritten into a question about something else.
 */
public final class DialogueContext {

    /** The number of turns a conversation keeps. */
    public static final int MAXIMUM_TURNS = 32;

    /** The number of concepts a turn keeps. */
    public static final int MAXIMUM_CONCEPTS_PER_TURN = 8;

    /** One turn of the conversation. */
    public record Turn(
            String question,
            List<String> conceptIds,
            List<String> conceptNames,
            List<String> claimIds,
            String language,
            boolean answered,
            DialogueAct.Act act) {

        public Turn {
            conceptIds = List.copyOf(conceptIds == null ? List.of() : conceptIds);
            conceptNames = List.copyOf(conceptNames == null ? List.of() : conceptNames);
            claimIds = List.copyOf(claimIds == null ? List.of() : claimIds);
        }
    }

    private final String workspaceId;
    private final List<Turn> turns = new ArrayList<>();

    public DialogueContext(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    /**
     * Records a turn.
     *
     * @param question the question as it was asked
     * @param answer the answer it produced
     */
    public void record(String question, Answer answer) {
        List<String> conceptIds = new ArrayList<>();
        List<String> conceptNames = new ArrayList<>();
        List<String> claimIds = new ArrayList<>();
        if (answer != null) {
            for (Concept concept : answer.concepts()) {
                if (conceptIds.size() >= MAXIMUM_CONCEPTS_PER_TURN) {
                    break;
                }
                conceptIds.add(concept.id());
                conceptNames.add(concept.name());
            }
            for (Claim claim : answer.claims()) {
                if (claimIds.size() >= MAXIMUM_CONCEPTS_PER_TURN) {
                    break;
                }
                claimIds.add(claim.id());
            }
        }
        turns.add(new Turn(question, conceptIds, conceptNames, claimIds,
                TextProfiles.detect(question == null ? "" : question),
                answer != null && answer.answered(),
                DialogueAct.classify(question)));
        while (turns.size() > MAXIMUM_TURNS) {
            turns.remove(0);
        }
    }

    /** The turns of the conversation, oldest first. */
    public List<Turn> turns() {
        return List.copyOf(turns);
    }

    /** How many turns the conversation holds. */
    public int size() {
        return turns.size();
    }

    /** The workspace the conversation belongs to. */
    public String workspaceId() {
        return workspaceId;
    }

    /**
     * The subject a referential question is about.
     *
     * @param question the question as the user wrote it
     * @return the concept's name, or null when the question names no subject
     */
    public String resolveSubject(String question) {
        if (question == null || question.isBlank() || turns.isEmpty()) {
            return null;
        }
        if (!isReferential(question)) {
            return null;
        }
        // The most recent turn that named a subject.
        for (int i = turns.size() - 1; i >= 0; i--) {
            Turn turn = turns.get(i);
            if (!turn.conceptNames().isEmpty()) {
                return turn.conceptNames().get(0);
            }
        }
        return null;
    }

    /**
     * Whether a question refers to something without naming it.
     *
     * <p>The rule is narrow on purpose: the question must carry a reference
     * word AND carry no subject of its own. A question that names
     * something is never rewritten, whatever it also says.
     *
     * @param question the question as the user wrote it
     * @return whether the question is referential
     */
    public static boolean isReferential(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String lower = question.toLowerCase(Locale.ROOT);
        boolean hasReference = containsAny(lower,
                "it", "this", "that", "these", "those", "such", "the same",
                "it", "это", "этот", "эта", "эти", "тот", "та", "те", "этого",
                "про него", "про это");
        if (!hasReference) {
            return false;
        }
        // A question that names a subject of its own is not referential:
        // the content terms are what the retrieval will match on.
        Set<String> content = QuestionDemand.contentTerms(question);
        content.removeAll(Set.of("it", "this", "that", "these", "those", "such", "the",
                "same", "more", "detail", "details", "explain", "tell", "about", "again",
                "это", "этот", "эта", "эти", "тот", "та", "те", "этого", "про", "него",
                "подробнее", "объясни", "расскажи", "еще", "ещё"));
        return content.isEmpty();
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The question with its referent made explicit.
     *
     * <p>The original question is NOT changed: the returned question is a
     * SEPARATE, expanded form, and the caller reports both. The mission's
     * §6 requires the original message to be preserved alongside any
     * normalized representation.
     *
     * @param question the question as the user wrote it
     * @return the expanded question, or the question itself when there is
     *     nothing to resolve
     */
    public String expand(String question) {
        String subject = resolveSubject(question);
        if (subject == null) {
            return question;
        }
        return question.strip() + " (" + subject + ")";
    }

    /**
     * The claims of the last turn that answered something.
     *
     * <p>These are the claims a correction is about: the user is saying
     * that something the system said was wrong, and what it said was one
     * of these.
     */
    public List<String> lastTurnClaimIds() {
        if (turns.isEmpty()) {
            return List.of();
        }
        return turns.get(turns.size() - 1).claimIds();
    }

    /** The distinct concepts the conversation has touched. */
    public Set<String> conceptsTouched() {
        Set<String> concepts = new LinkedHashSet<>();
        for (Turn turn : turns) {
            concepts.addAll(turn.conceptIds());
        }
        return concepts;
    }
}
