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
 * fixed number of phases — and it is <b>idempotent per record</b>: a
 * record that was forgotten is never forgotten again, and a second run
 * over an unchanged workspace touches no claim. While the rules still
 * name more candidates than the budget allows, each run forgets more —
 * and the report says so instead of claiming a guarantee it does not
 * have.
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

    /** How many held-out items the acceptance probe asks. */
    public static final int PROBE_SIZE = 12;

    /**
     * The held-out items the acceptance probe asks.
     *
     * <p>The probe is bounded: it asks the first {@link #PROBE_SIZE}
     * held-out items of the dataset, in the dataset's order, so the same
     * workspace is always probed the same way.
     *
     * @param workbench the workspace
     * @param workspaceId the workspace
     * @return the items
     */
    private static List<AssessmentItem> probeItems(KnowledgeWorkbench workbench, String workspaceId) {
        try {
            List<AssessmentItem> held = workbench.dataset(workspaceId).heldOutItems();
            return held.size() > PROBE_SIZE ? held.subList(0, PROBE_SIZE) : held;
        } catch (RuntimeException e) {
            // A workspace with no dataset cannot be probed, and a probe
            // that cannot run does not refuse the consolidation.
            return List.of();
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

    /**
     * The acceptance criterion of one run.
     *
     * <p>RECON-W42.7. The sleep consolidator's rule, wired into the
     * knowledge domain: <em>a proposal is taken only if the held-out
     * measurement does not fall, and the proposal is rolled back as a
     * batch when it does</em>.
     *
     * @param probed how many held-out items were asked
     * @param answeredBefore how many of them the workspace answered before
     * @param answeredAfter how many of them it answers after
     * @param accepted whether the proposal was kept
     * @param rolledBack how many records were brought back
     */
    public record Acceptance(int probed, int answeredBefore, int answeredAfter, boolean accepted,
                             int rolledBack) {
    }

    /**
     * The result of one run.
     *
     * @param examined how many records were examined
     * @param candidates how many the rules named
     * @param forgotten how many were actually marked and forgotten
     * @param marked the records that were ACTUALLY marked — RECON-W42.7:
     *     the field used to hold every candidate the rules named, so a
     *     run that forgot one record out of sixty reported sixty marked
     *     records. The name and the semantics disagreed, and the report
     *     is what the reader trusts.
     * @param invariants the invariants the run held to
     * @param idempotent whether the rules name nothing more
     * @param integration the error-driven integration's result
     * @param acceptance the probe's acceptance
     * @param decay the error-driven decay's result
     */
    public record Report(
            int examined,
            int candidates,
            int forgotten,
            List<Candidate> marked,
            List<String> invariants,
            boolean idempotent,
            ErrorIntegrator.Result integration,
            Acceptance acceptance,
            KnowledgeWorkbench.DecayResult decay) {

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

        // PHASE: THE ERROR-DRIVEN INTEGRATION. This is the phase that makes
        // the consolidation affect the LEARNER and not only the dialogue's
        // memory: the verified errors are recorded, and the exercise
        // selector acts on them. The mission's §9 says the integration
        // happens only after the error is verified, so only the errors the
        // learner has repeated are integrated.
        ErrorIntegrator.Result integration = ErrorIntegrator.integrate(workbench, workspaceId);

        // THE ERROR-DRIVEN DECAY.
        //
        // The owner's decision D-012: the shadow classes are wired to the
        // live engines. The ErrorDrivenLearner is the live engine of the
        // targeted forgetting, and this is the live phase that forgets.
        // The decay acts on the workspace's own Hebbian bindings, over the
        // VERIFIED errors only, and the weakened state is written to the
        // store so that it survives a restart.
        KnowledgeWorkbench.DecayResult decay = workbench.applyErrorDrivenDecay(workspaceId);

        // PHASE: THE PROBE BEFORE.
        //
        // The consolidation now PROPOSES, and the proposal is accepted
        // only if the knowledge's answerability on the held-out items does
        // not fall. This is the sleep consolidator's rule, wired into the
        // knowledge domain: "a proposal is taken iff held-out accuracy
        // does not fall", and the proposal is rolled back as a batch when
        // it does.
        //
        // The probe asks the HELD-OUT items - the independent test the
        // learner has never seen - and counts how many the workspace can
        // answer. The probe uses the retriever directly, so it records
        // nothing: a probe must not change what it measures.
        int probed = 0;
        int answeredBefore = 0;
        List<AssessmentItem> probeItems = probeItems(workbench, workspaceId);
        for (AssessmentItem item : probeItems) {
            probed++;
            if (new KnowledgeRetriever(workbench.workspace(workspaceId))
                    .answer(item.prompt(), AbstractionLevel.INTRODUCTORY).answered()) {
                answeredBefore++;
            }
        }

        // PHASE: THE MARK. A forgotten record is marked, never deleted.
        int forgotten = 0;
        List<String> markedIds = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (forgotten >= budget.maxForgotten()) {
                break;
            }
            if (workbench.forgetQuestion(workspaceId, candidate.questionId(), candidate.kind().name(),
                    candidate.reason(), nowMs)) {
                forgotten++;
                markedIds.add(candidate.questionId());
            }
        }

        // PHASE: THE PROBE AFTER, AND THE ROLLBACK.
        int answeredAfter = 0;
        for (AssessmentItem item : probeItems) {
            if (new KnowledgeRetriever(workbench.workspace(workspaceId))
                    .answer(item.prompt(), AbstractionLevel.INTRODUCTORY).answered()) {
                answeredAfter++;
            }
        }
        boolean accepted = answeredAfter >= answeredBefore;
        int rolledBack = 0;
        if (!accepted && forgotten > 0) {
            // The batch is rolled back: an early removal must not mask a
            // later degradation, and the state that is left must be a
            // state that was measured.
            for (String id : markedIds) {
                if (workbench.reviveQuestion(workspaceId, id)) {
                    rolledBack++;
                }
            }
            forgotten = 0;
        }
        Acceptance acceptance = new Acceptance(probed, answeredBefore, answeredAfter, accepted,
                rolledBack);

        List<String> invariants = List.of(
                "No claim was changed: the consolidation reports the support of the claims and "
                        + "never changes their status. A claim's truth comes from its evidence, "
                        + "not from how often it was asked about.",
                "Nothing was deleted: every forgotten record is marked, with the instant and the "
                        + "grounds of the forgetting.",
                "The run is bounded: at most " + budget.maxForgotten()
                        + " records may be forgotten in one run.",
                "The run is idempotent PER RECORD: a record that was forgotten is never forgotten "
                        + "again, and a second run over an unchanged workspace touches no claim. It "
                        + "is NOT idempotent over the workspace while the rules still name more "
                        + "candidates than the budget allows - a bounded consolidation with more to "
                        + "forget will forget more next time, and the report says so rather than "
                        + "claiming a guarantee it does not have.",
                "Only the VERIFIED errors are integrated: an error the learner has made in one "
                        + "session may be a slip, and it is not acted upon.",
                "The error-driven decay is targeted and bounded: it weakens the association "
                        + "that the wrong answer shared with the concept, and it stops at the "
                        + "deadband's lower edge, so a repeated error cannot weaken the "
                        + "association without bound.");

        // IDEMPOTENCY: the flag says whether the rules, applied to the
        // state the run left behind, name nothing MORE. The first version
        // of this compared the candidates with the forgotten records, and
        // it reported "not idempotent" for a run that had forgotten
        // everything the rules named - which is the opposite of the truth.
        //
        // RECON-W42.7: the flag was right and the TEXT around it was not.
        // The invariant claimed that "a second run over an unchanged
        // workspace forgets nothing", and the live measurement says
        // otherwise: with the age window at zero, every record is a decay
        // candidate, and three runs in a row forgot 5, 5 and 5 - the
        // records drop out of the candidate list exactly once each, which
        // is the per-record idempotency, but the workspace still has more
        // to forget. The invariant now says that, and the flag keeps its
        // meaning: the rules name nothing more.
        List<Candidate> after = candidates(workbench.askedQuestions(workspaceId),
                workbench.workspace(workspaceId), nowMs, budget);
        boolean idempotent = after.isEmpty();

        // THE MARKED RECORDS ARE THE ONES THE RUN ACTUALLY MARKED.
        //
        // The report used to carry every candidate the rules named, which
        // is not what "marked" says: with a budget of five and sixty
        // candidates, the report said sixty marked records and five
        // forgotten ones, and both numbers were true and neither was the
        // answer to the question the reader was asking.
        List<Candidate> marked = candidates.stream()
                .filter(candidate -> markedIds.contains(candidate.questionId()))
                .toList();
        return new Report(questions.size(), candidates.size(), forgotten, marked, invariants,
                idempotent, integration, acceptance, decay);
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
