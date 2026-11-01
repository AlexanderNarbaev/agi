package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The evidence-grounded reasoning path (RECON-W41).
 *
 * <p>The mission's §6.1 names the pipeline this class implements:
 * <i>User Intent → Task Classification → Knowledge Retrieval → Evidence
 * Selection → Structured Inference → Verification → Answer Construction →
 * Provenance</i>. Every step is recorded in the result, because the mission's
 * §9.3 requires the answer to expose "key inference steps" and an auditable
 * explanation of the evidence and the logical derivation — and a step that
 * leaves no record cannot be shown.
 *
 * <p><b>What the engine will not do.</b> It will not produce a factual
 * statement that no claim or recorded derivation supports. When the
 * workspace does not cover the question, the engine abstains and says which
 * terms it could not cover. The mission's §3.2: "A system must not invent
 * an answer merely to avoid reporting that something is unknown."
 *
 * <p><b>What is derived and what is stored.</b> The result separates them:
 * {@code claims} are the stored propositions, {@code derivations} are the
 * recorded inferences, and the steps say which was used where. A conclusion
 * reached by inference is never presented as though it had been read from a
 * source.
 */
public final class ReasoningEngine {

    /** What kind of question was asked. */
    public enum TaskKind {
        /** A question about what something is or does. */
        FACT,
        /** A question about how two things relate. */
        RELATION,
        /** A question about what has to be understood first. */
        PREREQUISITE,
        /** A question whose answer has to be worked out from other answers. */
        DERIVATION,
        /** A question this engine cannot classify. */
        UNKNOWN
    }

    /** One recorded step of the pipeline. */
    public record Step(String stage, String detail) {
    }

    /** The result of reasoning over a workspace. */
    public record Result(
            boolean answered,
            String answer,
            TaskKind task,
            List<Step> steps,
            List<Claim> claims,
            List<Derivation> derivations,
            List<Source> sources,
            List<Evidence> evidence,
            List<String> limitations,
            List<String> gaps,
            String abstentionReason) {

        public Result {
            steps = List.copyOf(steps == null ? List.of() : steps);
            claims = List.copyOf(claims == null ? List.of() : claims);
            derivations = List.copyOf(derivations == null ? List.of() : derivations);
            sources = List.copyOf(sources == null ? List.of() : sources);
            evidence = List.copyOf(evidence == null ? List.of() : evidence);
            limitations = List.copyOf(limitations == null ? List.of() : limitations);
            gaps = List.copyOf(gaps == null ? List.of() : gaps);
        }
    }

    private final EntailmentChecker checker;
    private final ContradictionDetector detector;

    public ReasoningEngine() {
        this(new EntailmentChecker(), new ContradictionDetector());
    }

    public ReasoningEngine(EntailmentChecker checker, ContradictionDetector detector) {
        this.checker = checker;
        this.detector = detector;
    }

    /**
     * Reasons over a workspace and answers a question.
     *
     * @param snapshot the workspace to reason over
     * @param question the question
     * @param level the abstraction level the answer should be given at
     * @return the answer, with the steps that produced it
     */
    public Result reason(KnowledgeSnapshot snapshot, String question, AbstractionLevel level) {
        List<Step> steps = new ArrayList<>();

        // ── 1. Intent ───────────────────────────────────────────────────────
        List<String> terms = TextFeatures.tokens(question);
        steps.add(new Step("intent", "the question's content terms: "
                + (terms.isEmpty() ? "(none)" : String.join(", ", terms))
                + "; the requested level: " + level));

        if (terms.isEmpty()) {
            return abstain(question, TaskKind.UNKNOWN, steps,
                    "The question contains no searchable terms.");
        }

        // ── 2. Task classification ──────────────────────────────────────────
        TaskKind task = classify(terms);
        steps.add(new Step("classification", "the question is classified as " + task));

        // ── 3. Knowledge retrieval ──────────────────────────────────────────
        KnowledgeRetriever retriever = new KnowledgeRetriever(snapshot);
        Answer retrieved = retriever.answer(question, level);
        List<String> limitations = new ArrayList<>(retrieved.limitations());
        List<String> gaps = new ArrayList<>(retrieved.gaps());
        steps.add(new Step("retrieval", retrieved.answered()
                ? "the workspace covers " + retrieved.claims().size() + " claim(s) of the question"
                : "the workspace does not cover enough of the question to answer it directly"));

        // ── 4. Evidence selection ───────────────────────────────────────────
        List<Claim> claims = new ArrayList<>(retrieved.claims());
        List<Evidence> evidence = new ArrayList<>();
        List<Source> sources = new ArrayList<>(retrieved.sources());
        Set<String> factIds = new LinkedHashSet<>();
        for (Claim claim : claims) {
            if (claim.status() == ClaimStatus.VERIFIED || claim.status() == ClaimStatus.SUPPORTED) {
                factIds.add(claim.id());
            }
        }
        for (Evidence link : snapshot.evidence()) {
            if (claims.stream().anyMatch(claim -> claim.id().equals(link.claimId()))) {
                evidence.add(link);
            }
        }
        steps.add(new Step("evidence selection",
                factIds.size() + " established claim(s) available as premises; "
                        + evidence.size() + " evidence link(s) behind them"));

        // ── 5. Structured inference ─────────────────────────────────────────
        List<Derivation> derivations = new ArrayList<>();
        List<Derivation> rules = snapshot.derivations();
        for (Derivation rule : rules) {
            // A rule is usable when the workspace holds its conclusion and
            // its premises. The check is exact: VALID, INVALID or UNDECIDED.
            if (!snapshot.claims().stream()
                    .anyMatch(claim -> claim.id().equals(rule.conclusionClaimId()))) {
                continue;
            }
            // The premises are FRAME-SCOPED: only the claims this question
            // actually put on the table count, and only the ones the rule
            // reasons over. Passing every retrieved claim to the checker
            // would put the whole workspace's claims into the model and
            // exhaust the atom budget, which is a way of answering
            // UNDECIDED for everything.
            Set<String> frame = new LinkedHashSet<>();
            for (String premise : rule.premiseClaimIds()) {
                if (factIds.contains(premise)) {
                    frame.add(premise);
                }
            }
            DerivationStatus status = checker.check(rule, frame, rules);
            derivations.add(new Derivation(rule.id(), rule.workspaceId(), rule.ruleId(),
                    rule.ruleName(), rule.connective(), rule.conclusionClaimId(),
                    rule.premiseClaimIds(), status, rule.recordedAt()));
        }
        // A rule that could not fire is reported with the reason: a premise
        // the question did not supply is not the same as a false premise,
        // and the difference is the difference between "we do not know" and
        // "this does not follow".
        for (Derivation derivation : derivations) {
            if (derivation.status() == DerivationStatus.DERIVED_INVALID) {
                List<String> missing = derivation.premiseClaimIds().stream()
                        .filter(premise -> !factIds.contains(premise))
                        .toList();
                if (!missing.isEmpty()) {
                    limitations.add("The rule '" + derivation.ruleName() + "' could not be applied: "
                            + missing.size() + " of its premises were not established by this "
                            + "question. The question does not say whether they hold or not.");
                }
            }
        }
        if (derivations.isEmpty()) {
            steps.add(new Step("inference", "the workspace holds no rule that bears on this question"));
        } else {
            steps.add(new Step("inference", derivations.size() + " recorded rule(s) checked: "
                    + derivations.stream()
                            .map(derivation -> derivation.ruleName() + "="
                                    + derivation.status())
                            .reduce((left, right) -> left + ", " + right)
                            .orElse("")));
        }

        // A conclusion that is validly derived is a fact for this question,
        // even if the retrieval did not surface it.
        for (Derivation derivation : derivations) {
            if (derivation.valid()) {
                snapshot.claims().stream()
                        .filter(claim -> claim.id().equals(derivation.conclusionClaimId()))
                        .findFirst()
                        .ifPresent(claim -> {
                            if (!claims.contains(claim)) {
                                claims.add(claim);
                                factIds.add(claim.id());
                            }
                        });
            }
        }

        // ── 6. Verification ─────────────────────────────────────────────────
        List<Contradiction> conflicts = new ArrayList<>();
        for (Contradiction conflict : detector.detect(snapshot)) {
            boolean involvesAnAnsweredClaim = claims.stream()
                    .anyMatch(claim -> claim.id().equals(conflict.claimAId())
                            || claim.id().equals(conflict.claimBId()));
            if (involvesAnAnsweredClaim) {
                conflicts.add(conflict);
            }
        }
        if (conflicts.isEmpty()) {
            steps.add(new Step("verification",
                    "no conflict was found among the claims this answer rests on"));
        } else {
            steps.add(new Step("verification", conflicts.size()
                    + " conflict(s) among the claims this answer rests on; "
                    + "neither side is preferred, and the conflict is reported"));
            for (Contradiction conflict : conflicts) {
                limitations.add("The sources disagree about this point; the conflict is recorded "
                        + "and neither side is preferred.");
            }
        }

        // ── 7. Answer construction ──────────────────────────────────────────
        String statement = retrieved.statement();
        if (!retrieved.answered()) {
            // The direct retrieval abstained. A valid derivation may still
            // answer the question - that is the point of having one.
            Claim derived = claims.stream()
                    .filter(claim -> factIds.contains(claim.id()))
                    .filter(claim -> !retrieved.claims().contains(claim))
                    .findFirst()
                    .orElse(null);
            if (derived == null) {
                steps.add(new Step("answer", "the question is not answered"));
                return abstain(question, task, steps,
                        "No claim in this workspace covers enough of the question to answer it.");
            }
            statement = derived.proposition();
            steps.add(new Step("answer",
                    "the answer is derived rather than retrieved: " + derived.proposition()));
        } else {
            steps.add(new Step("answer", "the answer is the retrieved account at the "
                    + retrieved.level() + " level"));
        }

        // ── 8. Provenance ───────────────────────────────────────────────────
        List<Source> provenance = new ArrayList<>();
        for (Source source : sources) {
            provenance.add(source);
        }
        steps.add(new Step("provenance", provenance.size() + " source(s) behind the answer; "
                + claims.size() + " claim(s); " + derivations.size() + " derivation(s)"));

        if (gaps.isEmpty()) {
            gaps = List.copyOf(retrieved.unmatchedTerms());
        }

        return new Result(true, statement, task, steps, claims, derivations, provenance,
                evidence, limitations, gaps, null);
    }

    /**
     * Classifies a question by the kind of question it is.
     *
     * <p>The classification is a deterministic rule over the question's
     * terms, and it is reported in the result's steps, so the user can see
     * how the question was read. It is a heuristic, and it says so.
     */
    public TaskKind classify(List<String> terms) {
        Set<String> words = new LinkedHashSet<>(terms);
        if (containsAny(words, "require", "requires", "required", "prerequisite", "depend",
                "depends", "prerequisite", "before")) {
            return TaskKind.PREREQUISITE;
        }
        if (containsAny(words, "relate", "relation", "relationship", "connection", "compare",
                "difference", "differ", "link", "between")) {
            return TaskKind.RELATION;
        }
        if (containsAny(words, "follow", "follows", "imply", "implies", "therefore", "hence",
                "conclude", "conclusion", "entail", "entails")) {
            return TaskKind.DERIVATION;
        }
        return TaskKind.FACT;
    }

    private boolean containsAny(Set<String> words, String... candidates) {
        for (String candidate : candidates) {
            if (words.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private Result abstain(String question, TaskKind task, List<Step> steps, String reason) {
        steps.add(new Step("answer", "the question is not answered"));
        return new Result(false, "", task, steps, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), reason);
    }
}
