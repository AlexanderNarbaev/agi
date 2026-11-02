package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The bounded consolidation of a workspace (RECON-W42.5).
 *
 * <p>The mission's §8 describes a consolidation in phases, with kinds of
 * forgetting that are bounded and reversible, and it states two rules that
 * decide the design:
 *
 * <ul>
 *   <li><b>"A knowledge claim must not become false merely because it was
 *       recently unused. Frequency of access is not a measure of truth."</b>
 *       — so the consolidation must never touch a claim's status. It may
 *       REPORT that a claim's support has thinned; it may not demote the
 *       claim, because the claim's truth comes from its evidence and not
 *       from how often it was asked about.</li>
 *   <li><b>"forgetting must not silently destroy the historical
 *       evidence."</b> — so nothing is deleted. A forgotten record is
 *       MARKED, and the mark says when and on what grounds it was
 *       forgotten.</li>
 * </ul>
 *
 * <p><b>The consolidation is bounded</b> — a fixed budget of records, a
 * fixed number of phases — and it is <b>idempotent</b>: a second run over
 * an unchanged workspace changes nothing.
 *
 * <p><b>No wall-clock in the decision path</b> (the CONSTITUTION's first
 * article): the caller supplies the instant the run is made.
 */
public final class KnowledgeConsolidator {

    /** How much one run may do. */
    public record Budget(int maxForgotten, long ageWindowMs) {

        public Budget {
            if (maxForgotten < 0) {
                throw new IllegalArgumentException("the budget must not be negative");
            }
            if (ageWindowMs < 0) {
                throw new IllegalArgumentException("the age window must not be negative");
            }
        }

        /** The default budget: a hundred records, and a thirty-day window. */
        public static Budget defaults() {
            return new Budget(100, 30L * 24 * 60 * 60 * 1000);
        }
    }

    /**
     * The kinds of forgetting the consolidation knows.
     *
     * <p>Each kind is a RULE over the workspace's records, and each rule
     * names the records it would forget. A record that no rule names is
     * not forgotten — the consolidation does not forget what it cannot
     * explain.
     */
    public enum Kind {
        /** The trace fades: the record is older than the window. */
        DECAY,
        /** The record was asked once and never again. */
        DISUSE,
        /** Another record covers the same ground; the older copy is redundant. */
        REDUNDANCY,
        /** A newer record asks the same thing in other words. */
        INTERFERENCE,
        /** The gap the record holds has been closed by the workspace. */
        SUPERSESSION,
        /** The record is about a subject the workspace has refuted. */
        REFUTATION,
        /** The budget of records is exceeded; the least important go first. */
        CAPACITY
    }

    /** One record the consolidation would forget, and why. */
    public record Candidate(
            String questionId,
            String question,
            Kind kind,
            String reason,
            long ageMs) {
    }

    /** The result of one run. */
    public record Report(
            int examined,
            int candidates,
            int forgotten,
            List<Candidate> marked,
            List<String> invariants,
            boolean idempotent) {

        public Report {
            marked = List.copyOf(marked == null ? List.of() : marked);
            invariants = List.copyOf(invariants == null ? List.of() : invariants);
        }

        /** Whether the run forgot anything. */
        public boolean changedAnything() {
            return forgotten > 0;
        }
    }

    private KnowledgeConsolidator() {
    }

    /**
     * Consolidates a workspace.
     *
     * @param workbench the workspace
     * @param workspaceId the workspace
     * @param nowMs the instant of the run, supplied by the caller
     * @param budget how much the run may do
     * @return the report
     */
    public static Report consolidate(KnowledgeWorkbench workbench, String workspaceId,
                                     long nowMs, Budget budget) {
        List<AskedQuestion> questions = workbench.askedQuestions(workspaceId);
        List<Candidate> candidates = candidates(questions, workbench.workspace(workspaceId),
                nowMs, budget);

        // PHASE: THE MARK. A forgotten record is marked, never deleted.
        int forgotten = 0;
        for (Candidate candidate : candidates) {
            if (forgotten >= budget.maxForgotten()) {
                break;
            }
            if (workbench.forgetQuestion(workspaceId, candidate.questionId(), candidate.kind().name(),
                    candidate.reason(), nowMs)) {
                forgotten++;
            }
        }

        List<String> invariants = List.of(
                "No claim was changed: the consolidation reports the support of the claims and "
                        + "never changes their status. A claim's truth comes from its evidence, "
                        + "not from how often it was asked about.",
                "Nothing was deleted: every forgotten record is marked, with the instant and the "
                        + "grounds of the forgetting.",
                "The run is bounded: at most " + budget.maxForgotten()
                        + " records may be forgotten in one run.",
                "The run is idempotent: a second run over an unchanged workspace forgets nothing.");

        // IDEMPOTENCY: the run is idempotent when the rules, applied to the
        // state the run left behind, name nothing. The first version of
        // this compared the candidates with the forgotten records, and it
        // reported "not idempotent" for a run that had forgotten
        // everything the rules named - which is the opposite of the truth.
        List<Candidate> after = candidates(workbench.askedQuestions(workspaceId),
                workbench.workspace(workspaceId), nowMs, budget);
        boolean idempotent = after.isEmpty();
        return new Report(questions.size(), candidates.size(), forgotten, candidates, invariants,
                idempotent);
    }

    /**
     * The records the rules would forget.
     *
     * @param questions the workspace's dialogue memory
     * @param snapshot the workspace
     * @param nowMs the instant of the run
     * @param budget the budget
     * @return the candidates, in the order they would be forgotten
     */
    static List<Candidate> candidates(List<AskedQuestion> questions, KnowledgeSnapshot snapshot,
                                       long nowMs, Budget budget) {
        List<Candidate> candidates = new ArrayList<>();
        if (questions == null || questions.isEmpty()) {
            return candidates;
        }
        // The live records only: a forgotten record is already forgotten.
        List<AskedQuestion> live = questions.stream().filter(q -> !q.forgotten()).toList();

        // REDUNDANCY: of the records that share a canonical key, the older
        // ones are redundant - the newest carries the same ground.
        Map<String, AskedQuestion> newestByKey = new LinkedHashMap<>();
        for (AskedQuestion question : live) {
            newestByKey.merge(question.canonicalKey(), question,
                    (left, right) -> left.lastAskedAt().compareTo(right.lastAskedAt()) >= 0
                            ? left : right);
        }

        // INTERFERENCE: a record that asks the same thing as a NEWER record
        // in other words is displaced by it.
        Map<String, AskedQuestion> newestByTerms = new LinkedHashMap<>();
        for (AskedQuestion question : live) {
            String key = String.join("|", new java.util.TreeSet<>(question.uncoveredTerms()));
            newestByTerms.merge(key, question,
                    (left, right) -> left.lastAskedAt().compareTo(right.lastAskedAt()) >= 0
                            ? left : right);
        }

        Set<String> refutedTerms = new LinkedHashSet<>();
        for (Claim claim : snapshot.claims()) {
            if (claim.status() == ClaimStatus.REFUTED || claim.status() == ClaimStatus.CONTESTED) {
                for (String term : QuestionDemand.contentTerms(claim.proposition())) {
                    refutedTerms.add(term);
                }
            }
        }

        for (AskedQuestion question : live) {
            long age = ageMs(question, nowMs);
            String id = question.id();

            // DECAY: the record is older than the window.
            if (age > budget.ageWindowMs()) {
                candidates.add(new Candidate(id, question.question(), Kind.DECAY,
                        "the record is older than the consolidation window", age));
                continue;
            }
            // DISUSE: asked once and never again. The record must be
            // PAST THE WINDOW as well: a question asked a moment ago has
            // not been disused, it has simply just been asked.
            if (question.timesAsked() <= 1 && age > budget.ageWindowMs()) {
                candidates.add(new Candidate(id, question.question(), Kind.DISUSE,
                        "the question was asked once and never again", age));
                continue;
            }
            // REDUNDANCY: another record covers the same ground.
            AskedQuestion newest = newestByKey.get(question.canonicalKey());
            if (newest != null && !newest.id().equals(id)
                    && newest.lastAskedAt().compareTo(question.lastAskedAt()) > 0) {
                candidates.add(new Candidate(id, question.question(), Kind.REDUNDANCY,
                        "a newer record of the same question carries the same ground", age));
                continue;
            }
            // INTERFERENCE: a newer record asks it in other words.
            String terms = String.join("|", new java.util.TreeSet<>(question.uncoveredTerms()));
            AskedQuestion other = newestByTerms.get(terms);
            if (other != null && !other.id().equals(id)
                    && other.lastAskedAt().compareTo(question.lastAskedAt()) > 0) {
                candidates.add(new Candidate(id, question.question(), Kind.INTERFERENCE,
                        "a newer record asks the same thing in other words", age));
                continue;
            }
            // SUPERSESSION: the gap the record holds has been closed, and
            // the record is old enough to let go of.
            if (question.status() == AskedQuestion.Status.RESOLVED && question.timesAsked() <= 1
                    && age > budget.ageWindowMs()) {
                candidates.add(new Candidate(id, question.question(), Kind.SUPERSESSION,
                        "the workspace can answer the question now", age));
                continue;
            }
            // REFUTATION: the record is about a subject the workspace has
            // refuted - the open gap is not a gap any more.
            if (!question.uncoveredTerms().isEmpty()
                    && question.uncoveredTerms().stream().allMatch(refutedTerms::contains)) {
                candidates.add(new Candidate(id, question.question(), Kind.REFUTATION,
                        "the subject of the question has been refuted in the workspace", age));
                continue;
            }
        }

        // CAPACITY: the budget of records is exceeded. The least important
        // go first, and only as many as the excess demands.
        int excess = live.size() - budget.maxForgotten();
        if (excess > 0) {
            List<AskedQuestion> byAge = new ArrayList<>(live);
            byAge.sort((left, right) -> left.lastAskedAt().compareTo(right.lastAskedAt()));
            Set<String> already = new LinkedHashSet<>();
            candidates.forEach(candidate -> already.add(candidate.questionId()));
            for (AskedQuestion question : byAge) {
                if (excess <= 0) {
                    break;
                }
                if (already.contains(question.id())) {
                    continue;
                }
                already.add(question.id());
                excess--;
                candidates.add(new Candidate(question.id(), question.question(), Kind.CAPACITY,
                        "the workspace holds more records than the consolidation budget", ageMs(question, nowMs)));
            }
        }
        return candidates;
    }

    /**
     * How long ago the record was last asked, in milliseconds.
     *
     * <p>The store writes the instants as ISO strings, so they are parsed
     * as such. The first version of this method parsed them as numbers
     * and returned zero for every record, which made every age-based rule
     * silent.
     */
    private static long ageMs(AskedQuestion question, long nowMs) {
        String last = question.lastAskedAt();
        if (last == null || last.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, nowMs - java.time.Instant.parse(last).toEpochMilli());
        } catch (java.time.format.DateTimeParseException e) {
            try {
                return Math.max(0, nowMs - Long.parseLong(last));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
    }
}
