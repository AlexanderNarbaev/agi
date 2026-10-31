package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the questions the workspace was asked into a list of what it does
 * not know (owner directive §19 Workflow C, §20 MemoryConsolidator).
 *
 * <p>This is the capability the MIND-W3 branch had and `develop` lost: the
 * episodic log was replayed, the recurring patterns were promoted to
 * canonical identifiers so that paraphrases collapsed, and the entries that
 * were no longer true were retired BY TOMBSTONE rather than by deletion.
 * The branch's own tests for it survive uncompiled at
 * `docs-v2/quality/lost-capability-tests/`.
 *
 * <p>What it produces here:
 *
 * <ul>
 *   <li><b>Canonical keys.</b> Two wordings of the same question collapse
 *       onto one record. The key is the sorted set of the question's folded
 *       content terms - not a hash, so the collapse is visible and can be
 *       checked.</li>
 *   <li><b>Promoted gaps.</b> A question that has been asked more than once
 *       is a recurring gap and is promoted into the explicit list of what
 *       the workspace does not know.</li>
 *   <li><b>Resolution by tombstone.</b> When the workspace can answer a
 *       question it could not answer before, the record is marked RESOLVED
 *       and KEPT. The gap did not stop existing because it was closed; the
 *       record of the gap is the evidence that the workspace learned
 *       something.</li>
 * </ul>
 */
public final class QuestionConsolidator {

    /** A question asked this many times is a recurring gap and is promoted. */
    public static final int PROMOTION_THRESHOLD = 2;

    /** What one consolidation pass found. */
    public record ConsolidationReport(
            int totalRecords,
            int openQuestions,
            int resolvedQuestions,
            int promotedGaps,
            List<AskedQuestion> open,
            List<AskedQuestion> resolved,
            List<String> notes) {

        public ConsolidationReport {
            open = List.copyOf(open == null ? List.of() : open);
            resolved = List.copyOf(resolved == null ? List.of() : resolved);
            notes = List.copyOf(notes == null ? List.of() : notes);
        }
    }

    public QuestionConsolidator() {
    }

    /**
     * The canonical key of a question: its folded content terms, sorted.
     *
     * <p>Sorting makes the key independent of the order of the words, so
     * "what is photosynthesis" and "photosynthesis is what" share a key. The
     * terms are the folded stems, so a plural or a gerund does not make a
     * new question.
     */
    public static String canonicalKey(String question) {
        Set<String> terms = new LinkedHashSet<>(TextFeatures.tokens(question));
        List<String> sorted = new ArrayList<>(terms);
        Collections.sort(sorted);
        return String.join(" ", sorted);
    }

    /**
     * Consolidate the episodic log of asked questions.
     *
     * @param asked the records, as the store holds them
     * @param retriever the workspace's own retriever, used to ask each open
     *     question again
     * @return what the pass found
     */
    public ConsolidationReport consolidate(List<AskedQuestion> asked, KnowledgeRetriever retriever) {
        List<AskedQuestion> open = new ArrayList<>();
        List<AskedQuestion> resolved = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        for (AskedQuestion record : asked) {
            if (record.status() == AskedQuestion.Status.RESOLVED) {
                resolved.add(record);
                continue;
            }
            Answer now = retriever.answer(record.question());
            if (now.answered()) {
                // The workspace can answer it now. The record is KEPT and
                // marked resolved: the gap is closed, and the record of the
                // gap is the evidence that something was learned.
                resolved.add(new AskedQuestion(record.id(), record.workspaceId(),
                        record.canonicalKey(), record.question(), record.uncoveredTerms(),
                        record.timesAsked(), record.answered(), record.firstAskedAt(), record.lastAskedAt(),
                        AskedQuestion.Status.RESOLVED,
                        "The workspace can answer this question now; the record of the gap is kept."));
                notes.add("resolved: " + record.question());
            } else {
                open.add(record);
                if (record.timesAsked() >= PROMOTION_THRESHOLD) {
                    notes.add("promoted: \"" + record.question() + "\" has been asked "
                            + record.timesAsked() + " times and is still unanswered");
                }
            }
        }

        int promoted = (int) open.stream()
                .filter(r -> r.timesAsked() >= PROMOTION_THRESHOLD)
                .count();

        return new ConsolidationReport(asked.size(), open.size(), resolved.size(), promoted,
                open, resolved, notes);
    }
}
