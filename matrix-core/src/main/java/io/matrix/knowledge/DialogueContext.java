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
            String id,
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

        /** A turn without an identity yet. */
        public Turn(String question, List<String> conceptIds, List<String> conceptNames,
                    List<String> claimIds, String language, boolean answered,
                    DialogueAct.Act act) {
            this(null, question, conceptIds, conceptNames, claimIds, language, answered, act);
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
        turns.add(new Turn(io.matrix.knowledge.Ids.digest(workspaceId + "|turn|"
                        + (question == null ? "" : question) + "|" + turns.size()),
                question, conceptIds, conceptNames, claimIds,
                TextProfiles.detect(question == null ? "" : question),
                answer != null && answer.answered(),
                DialogueAct.classify(question)));
        while (turns.size() > MAXIMUM_TURNS) {
            turns.remove(0);
        }
    }

    /**
     * Loads the persisted turns of the conversation.
     *
     * <p>RECON-W42.7. The mission's §6 asks for a bounded PERSISTED
     * conversation history. The context used to be held in memory only,
     * so a restart lost it - and "explain this in more detail" had no
     * referent after a restart, which is the case the mission names.
     *
     * @param persisted the turns, oldest first
     */
    public void load(List<Turn> persisted) {
        if (persisted == null || persisted.isEmpty()) {
            return;
        }
        turns.clear();
        turns.addAll(persisted);
        while (turns.size() > MAXIMUM_TURNS) {
            turns.remove(0);
        }
    }

    /**
     * Whether the conversation holds a subject to refer to.
     *
     * <p>RECON-W42.7. A question like "How does it work?" is referential
     * in the ordinary sense, but {@link #isReferential(String)} says it is
     * not — the rule there is narrow on purpose, and the question carries
     * the content term "work". The conversation can still settle what such
     * a question is about: if it holds a turn that named a subject, the
     * referent has something to point at.
     *
     * @return whether the conversation holds a subject
     */
    public boolean hasSubject() {
        return currentSubject() != null;
    }

    /**
     * The subject of the conversation: the concept the last turn that named
     * one was about.
     *
     * <p>RECON-W42.7. The referential rule is narrow on purpose — a
     * question that names a subject is never rewritten — and "How does it
     * work?" carries the content term "work", so the rule misses it. The
     * conversation can still say what such a question is about.
     *
     * @return the subject, or null when the conversation has named none
     */
    public String currentSubject() {
        for (int i = turns.size() - 1; i >= 0; i--) {
            Turn turn = turns.get(i);
            if (!turn.conceptNames().isEmpty()) {
                return turn.conceptNames().get(0);
            }
        }
        return null;
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
        if (!hasReference(question)) {
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

    /**
     * Whether the question carries a reference word at all.
     *
     * <p>RECON-W42.7. {@link #isReferential(String)} couples the
     * reference word with the question naming no subject of its own, and
     * the two answers different questions: "does the question refer to
     * something?" and "does the question refer to something without
     * naming it?". The demand analysis needs the first.
     *
     * @param question the question as the user wrote it
     * @return whether the question carries a reference word
     */
    public static boolean hasReference(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        // The WORDS of the question, not its substrings.
        //
        // The first version matched the substrings, and the Russian "те"
        // matched inside another word — so the Russian question "Какие
        // газообразные вещества участвуют в фотосинтезе?" was read as
        // referring to something, and the workspace asked what it referred
        // to instead of answering. The accuracy fell from 0.917 to 0.875.
        Set<String> words = new LinkedHashSet<>(wordsOf(question));
        for (String word : REFERENCE_WORDS) {
            if (words.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The words of a question, lower-cased, with nothing removed.
     *
     * <p>The reference words are stopwords — "this", "it", "это" — and
     * {@link TextFeatures#originalTokens(String)} drops the stopwords, so
     * it cannot find them.
     *
     * @param text the text
     * @return the words
     */
    private static List<String> wordsOf(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char ch : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                current.append(ch);
            } else {
                if (current.length() > 0) {
                    out.add(current.toString());
                    current.setLength(0);
                }
            }
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
        return out;
    }

    /** The words that refer to something without naming it. */
    private static final List<String> REFERENCE_WORDS = List.of(
            "it", "this", "that", "these", "those", "such", "same",
            "это", "этот", "эта", "эти", "тот", "та", "те", "этого", "него",
            "про него", "про это");

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
