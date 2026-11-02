package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Selects the next exercise for a learner (RECON-W42.4).
 *
 * <p>The mission's §10.1 asks for "an exercise generator and a learner
 * practice loop" and for the system to "suggest what to learn next,
 * considering the learner's current state and gaps". RECON-W41 had neither:
 * the assessment items are a static JSON file, and the caller supplies the
 * item id — so the learner had to know which item to practise.
 *
 * <p><b>The selection is derived from the learner's state.</b> The
 * learner's per-dimension stages and scores are read from the persisted
 * state machine, the gap detector names the concepts whose prerequisites are
 * missing, and the item is chosen from those two. The order of the rules is
 * the order of what a learner needs first:
 *
 * <ol>
 *   <li>an item of a dimension the learner has <b>not yet reached</b> — the
 *       weakest dimension first, because that is where the learning is;</li>
 *   <li>an item of a concept whose <b>prerequisites are missing</b> — the
 *       gap is upstream of every question about the concept;</li>
 *   <li>the <b>held-out</b> item of that dimension — the independent test,
 *       which is the only one that measures rather than rehearses;</li>
 *   <li>otherwise the next item the learner has not attempted.</li>
 * </ol>
 *
 * <p><b>Nothing is invented.</b> The selector chooses among the items the
 * dataset holds; it does not write a question, and it does not report a
 * score. The mission's §5.4 forbids promoting generated content to trusted
 * knowledge, and a generated question that the learner is graded on would
 * be exactly that.
 */
public final class ExerciseSelector {

    /** Why an item was selected. */
    public record Selection(
            AssessmentItem item,
            LearningDimension dimension,
            String reason,
            boolean heldOut,
            String conceptName) {

        public Selection {
            if (item == null) {
                throw new IllegalArgumentException("a selection must have an item");
            }
        }
    }

    private final AssessmentDataset dataset;
    private final List<LearningState> states;
    private final KnowledgeSnapshot snapshot;
    private final Set<String> attempted;
    private final Set<String> failed;

    /**
     * A selector over a learner.
     *
     * @param dataset the assessment items
     * @param states the learner's per-dimension states
     * @param snapshot the workspace
     * @param attempted the item ids the learner has already attempted
     */
    public ExerciseSelector(AssessmentDataset dataset, List<LearningState> states,
                            KnowledgeSnapshot snapshot, Set<String> attempted) {
        this(dataset, states, snapshot, attempted, Set.of());
    }

    /**
     * A selector over a learner, with the errors they made.
     *
     * <p>The failed items are the ones the learner is asked again, and
     * that is the error-driven part of the loop: an item the learner got
     * wrong is evidence of a gap, and the gap is what practice is for.
     * Without this, a failed item was "attempted" and therefore skipped
     * forever - the learner could not fail the same item twice, and the
     * system could not tell a learner who has understood from one who has
     * merely been exposed.
     *
     * @param dataset the assessment items
     * @param states the learner's per-dimension states
     * @param snapshot the workspace
     * @param attempted the item ids the learner has already attempted
     * @param failed the item ids the learner has answered wrongly
     */
    public ExerciseSelector(AssessmentDataset dataset, List<LearningState> states,
                            KnowledgeSnapshot snapshot, Set<String> attempted, Set<String> failed) {
        this.dataset = dataset;
        this.states = states == null ? List.of() : states;
        this.snapshot = snapshot;
        this.attempted = attempted == null ? Set.of() : attempted;
        this.failed = failed == null ? Set.of() : failed;
    }

    /**
     * The next exercise for the learner.
     *
     * @return the selection, or null when there is nothing to practise
     */
    public Selection next() {
        List<AssessmentItem> items = dataset.items();
        if (items.isEmpty()) {
            return null;
        }
        Map<LearningDimension, LearningState> byDimension = new LinkedHashMap<>();
        for (LearningState state : states) {
            byDimension.put(state.dimension(), state);
        }

        // The dimensions the learner has not reached, weakest first.
        List<LearningDimension> weak = new ArrayList<>();
        for (LearningDimension dimension : LearningDimension.values()) {
            LearningState state = byDimension.get(dimension);
            if (state == null || state.stage().index() < LearningStage.COMPETENCE.index()) {
                weak.add(dimension);
            }
        }
        weak.sort(Comparator.comparingInt(dimension -> {
            LearningState state = byDimension.get(dimension);
            return state == null ? -1 : (int) state.score();
        }));

        // The concepts whose prerequisites the workspace does not hold.
        Set<String> gapConcepts = new LinkedHashSet<>();
        KnowledgeGapDetector detector = new KnowledgeGapDetector(snapshot);
        for (Concept concept : snapshot.concepts()) {
            if (!detector.missingPrerequisites(concept.id()).isEmpty()) {
                gapConcepts.add(concept.id());
            }
        }

        // 1. THE ERROR-DRIVEN SELECTION. An item the learner answered
        // wrongly is the evidence of a gap, and it comes before the items
        // the learner has never seen.
        for (LearningDimension dimension : weak) {
            for (AssessmentItem item : items) {
                if (item.dimension() == dimension && failed.contains(item.id())) {
                    return new Selection(item, dimension,
                            "the learner answered this item wrongly, and the error is the gap",
                            item.heldOut(), nameOf(item.conceptId()));
                }
            }
        }
        // 2. An unattempted item of a weak dimension.
        for (LearningDimension dimension : weak) {
            for (AssessmentItem item : items) {
                if (item.dimension() == dimension && !attempted.contains(item.id())) {
                    return new Selection(item, dimension,
                            "the dimension has not reached competence yet", item.heldOut(),
                            nameOf(item.conceptId()));
                }
            }
        }
        // 3. An item of a concept with a missing prerequisite.
        for (String conceptId : gapConcepts) {
            for (AssessmentItem item : items) {
                if (item.conceptId().equals(conceptId) && !attempted.contains(item.id())) {
                    return new Selection(item, item.dimension(),
                            "the concept's prerequisites are not all in the workspace",
                            item.heldOut(), nameOf(item.conceptId()));
                }
            }
        }
        // 4. The held-out item of a weak dimension: the independent test.
        for (LearningDimension dimension : weak) {
            for (AssessmentItem item : items) {
                if (item.dimension() == dimension && item.heldOut()
                        && !attempted.contains(item.id())) {
                    return new Selection(item, dimension,
                            "the held-out item of the dimension is the independent test",
                            true, nameOf(item.conceptId()));
                }
            }
        }
        // 5. Otherwise, the next unattempted item.
        for (AssessmentItem item : items) {
            if (!attempted.contains(item.id())) {
                return new Selection(item, item.dimension(), "the next unattempted item",
                        item.heldOut(), nameOf(item.conceptId()));
            }
        }
        // 6. And finally the item the learner has attempted least recently.
        for (AssessmentItem item : items) {
            if (item.heldOut()) {
                return new Selection(item, item.dimension(),
                        "everything has been attempted; the held-out item measures the learner",
                        true, nameOf(item.conceptId()));
            }
        }
        return null;
    }

    /**
     * The exercises for the next session.
     *
     * @param count how many to select
     * @return the selections, in order
     */
    public List<Selection> next(int count) {
        List<Selection> selections = new ArrayList<>();
        Set<String> chosen = new LinkedHashSet<>(attempted);
        for (int i = 0; i < count; i++) {
            Selection selection = new ExerciseSelector(dataset, states, snapshot, chosen).next();
            if (selection == null) {
                break;
            }
            selections.add(selection);
            chosen.add(selection.item().id());
        }
        return selections;
    }

    private String nameOf(String conceptId) {
        if (snapshot == null) {
            return conceptId;
        }
        return snapshot.concepts().stream()
                .filter(concept -> concept.id().equals(conceptId))
                .map(Concept::name)
                .findFirst()
                .orElse(conceptId);
    }
}
