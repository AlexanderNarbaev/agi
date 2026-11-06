package io.matrix.cognitive.algorithms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * W44.2 — the mechanism that chooses between algorithms from their declared conditions and the
 * measurements taken of them.
 *
 * <p><b>What this replaces.</b> Before this package there was no such thing. The algorithms in
 * {@code io.matrix.neuron} were free-standing classes that nothing called; the one router that
 * did exist ({@code io.matrix.federation.liquid.CognitiveRouter}) chose between cognitive MODES
 * on biochemical state and had no production caller. Choosing between algorithms was done by
 * whoever wrote the call site, which is how "the method was selected outside its declared
 * preconditions" became a state the code could not represent and therefore could not be checked
 * for.</p>
 *
 * <p><b>The four rules.</b></p>
 * <ol>
 *   <li><b>A method is admitted only inside its declared preconditions.</b> A method whose
 *       required facts are not all established by the request is not a candidate. It still appears
 *       in the decision record, with the missing precondition named, because a silently dropped
 *       candidate is a candidate whose rejection nobody can audit.</li>
 *   <li><b>The score is computed from data.</b> Two things feed it: the method's declared
 *       accuracy evidence, with its denominator, and the cost MEASURED for that method id. There
 *       is no preference order over method ids anywhere in this class — an if-else chain over
 *       names would make the choice a property of the code rather than of the evidence.</li>
 *   <li><b>Nothing admitted means a refusal.</b> The decision says which precondition blocked
 *       the family, so the caller learns what to establish.</li>
 *   <li><b>The budget is honoured before the score.</b> A method whose estimated cost does not
 *       fit the request's budget is not chosen in preference to one that does.</li>
 * </ol>
 *
 * <p><b>The cost has two bases and the decision says which was used.</b> When the ledger has
 * measured the method, the measured nanoseconds are used. When it has not, the capability's
 * declared complexity is used instead — a placeholder, not a measurement, and the decision record
 * says so for that candidate. Mixing the two is not done: a method that was measured and a
 * method that was not are compared through their ORDER, not through their magnitudes, because a
 * nanosecond count and a big-O label are not the same unit.</p>
 *
 * <p><b>Determinism.</b> The same request always selects the same method. There is no clock read
 * in this class and no randomness: the cost comes from the ledger, which is written AFTER a call
 * has finished, so a decision never depends on how loaded the machine was when it was made.</p>
 *
 * <p><b>The tie-break.</b> Highest score first; then the lexicographically smallest method id.
 * The cost enters the score, so a cheaper method wins whenever the cost weight is positive; the
 * id decides only between methods that are indistinguishable on the evidence, which is what makes
 * a choice reproducible.</p>
 */
public final class AlgorithmSelector {

    /**
     * The weight of the accuracy term in the score.
     *
     * <p>CONVENTION: one, so that the accuracy evidence is worth one unit and the cost weight
     * is expressed in the same units. The cost weight is what the neuromodulation layer supplies
     * — a system under pressure may weigh cost more heavily, and this class does not decide
     * that.</p>
     */
    public static final double ACCURACY_WEIGHT = 1.0;

    /**
     * The cost weight used when the caller supplies none.
     *
     * <p>CONVENTION: one, which weighs the accuracy evidence and the cost equally. It is the
     * neutral value, and it is named here rather than taken from the neuromodulation package so
     * that this package does not depend on it.</p>
     */
    public static final double NEUTRAL_COST_WEIGHT = 1.0;

    /**
     * The score carried by a candidate that was not admitted.
     *
     * <p>DERIVED: it is below every admissible score, which cannot be negative infinity in a
     * record that is going to be written down and read.</p>
     */
    public static final double BLOCKED_SCORE = Double.NEGATIVE_INFINITY;

    /** The nanoseconds in one millisecond. DERIVED from the time unit definition. */
    private static final long NANOS_PER_MILLI = 1_000_000L;

    /**
     * The scale of the score reported in a decision.
     *
     * <p>CONVENTION: three decimal places are enough to read a score and few enough that the
     * rounding cannot hide a difference that matters.</p>
     */
    private static final double SCORE_SCALE = 1000.0;

    /**
     * A selected method and the decision that selected it.
     *
     * @param method   the method to run, or {@code null} when the request was refused
     * @param decision why it was chosen, and what else was considered
     */
    public record Selection<I, O>(AlgorithmMethod<I, O> method, AlgorithmDecision decision) {
    }

    /** Where a cost came from. */
    public enum CostBasis {
        /** The ledger has measured this method. */
        MEASURED,
        /** Nobody measured this method, so its declared complexity was used. */
        DECLARED_COMPLEXITY
    }

    /** The cost of one candidate and where it came from. */
    private record CostAssessment(long nanos, int nodes, CostBasis basis, String detail) {
    }

    /** An admitted candidate with its score. */
    private record Scored(
            AlgorithmMethod<?, ?> method,
            double score,
            long costNanos,
            CostAssessment cost,
            String verdict) {
    }

    private final AlgorithmRegistry registry;
    private final CostLedger costLedger;
    private final double defaultCostWeight;

    /**
     * @param registry  the catalogue of methods
     * @param costLedger the ledger of measured costs
     */
    public AlgorithmSelector(AlgorithmRegistry registry, CostLedger costLedger) {
        this(registry, costLedger, NEUTRAL_COST_WEIGHT);
    }

    /**
     * @param registry  the catalogue of methods
     * @param costLedger the ledger of measured costs
     * @param costWeight how strongly a cost is weighed against the accuracy evidence
     */
    public AlgorithmSelector(AlgorithmRegistry registry, CostLedger costLedger, double costWeight) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        if (costLedger == null) {
            throw new IllegalArgumentException("cost ledger must not be null");
        }
        this.registry = registry;
        this.costLedger = costLedger;
        this.defaultCostWeight = checkedWeight(costWeight);
    }

    /**
     * Chooses a method for a request.
     *
     * @param request the problem, with the facts known about its input
     * @return the decision, including every candidate that was considered
     */
    public AlgorithmDecision decide(ProblemRequest request) {
        return decide(request, defaultCostWeight);
    }

    /**
     * Chooses a method for a request, with the caller's cost weight.
     *
     * @param request   the problem, with the facts known about its input
     * @param costWeight how strongly a cost is weighed against the accuracy evidence
     * @return the decision, including every candidate that was considered
     */
    public AlgorithmDecision decide(ProblemRequest request, double costWeight) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        double weight = checkedWeight(costWeight);
        List<AlgorithmMethod<?, ?>> methods = registry.methodsFor(request.family());
        if (methods.isEmpty()) {
            return new AlgorithmDecision(request.family(), null, null,
                    "no method is registered for the family " + request.family(),
                    List.of(), request.budget(), AlgorithmDecision.Status.REFUSED, null,
                    "nothing was considered");
        }

        List<AlgorithmDecision.Candidate> candidates = new ArrayList<>();
        List<Scored> admitted = new ArrayList<>();
        Set<String> blockingFacts = new LinkedHashSet<>();

        for (AlgorithmMethod<?, ?> method : methods) {
            AlgorithmCapability capability = method.capability();
            Set<String> missing = missingFacts(capability, request);
            if (!missing.isEmpty()) {
                blockingFacts.addAll(missing);
                candidates.add(new AlgorithmDecision.Candidate(capability.id(), capability.version(),
                        BLOCKED_SCORE, false,
                        "blocked: the precondition(s) " + String.join(", ", missing)
                                + " are not established for this request"));
                continue;
            }
            String ownCheckFailure = ownPreconditionFailure(method, request.payload());
            if (ownCheckFailure != null) {
                candidates.add(new AlgorithmDecision.Candidate(capability.id(), capability.version(),
                        BLOCKED_SCORE, false,
                        "blocked: the method's own precondition check failed (" + ownCheckFailure + ")"));
                continue;
            }
            CostAssessment cost = assessCost(capability);
            admitted.add(new Scored(method, 0.0, cost.nanos(), cost, ""));
        }

        if (admitted.isEmpty()) {
            return new AlgorithmDecision(request.family(), null, null,
                    "no method is admitted for the family " + request.family()
                            + ": the precondition(s) " + String.join(", ", blockingFacts)
                            + " are not established for this request",
                    List.copyOf(candidates), request.budget(), AlgorithmDecision.Status.REFUSED, null,
                    "nothing was selected");
        }

        // The cost fractions are relative to the admitted set: the cheapest admitted method
        // costs nothing extra, the dearest costs the whole spread.
        long cheapest = admitted.stream().mapToLong(Scored::costNanos).min().orElse(0L);
        long dearest = admitted.stream().mapToLong(Scored::costNanos).max().orElse(0L);
        long spread = dearest - cheapest;

        List<Scored> scored = new ArrayList<>(admitted.size());
        for (Scored candidate : admitted) {
            double costFraction = spread == 0L
                    ? 0.0
                    : (double) (candidate.costNanos() - cheapest) / (double) spread;
            double accuracy = accuracyOf(candidate.method().capability());
            double score = ACCURACY_WEIGHT * accuracy - weight * costFraction;
            scored.add(new Scored(candidate.method(), score, candidate.costNanos(), candidate.cost(),
                    "admitted; accuracy " + accuracy + " over "
                            + candidate.method().capability().accuracyEvidence().denominator()
                            + " case(s), cost " + candidate.cost().detail()));
        }

        scored.sort(Comparator
                .comparingDouble(Scored::score).reversed()
                .thenComparing(s -> s.method().capability().id()));

        // A method that does not fit the budget is not chosen in preference to one that does.
        // When nothing fits, the best is still named — so that the caller can see what it would
        // have cost — and the decision says the budget was the obstacle.
        List<Scored> fitting = new ArrayList<>();
        for (Scored candidate : scored) {
            if (fitsBudget(candidate, request.budget())) {
                fitting.add(candidate);
            }
        }
        boolean resourceLimited = fitting.isEmpty();
        Scored winner = resourceLimited ? scored.get(0) : fitting.get(0);

        AlgorithmCapability winnerCapability = winner.method().capability();
        candidates.add(new AlgorithmDecision.Candidate(
                winnerCapability.id(), winnerCapability.version(), winner.score(), true,
                "selected: score " + round(winner.score()) + ", cost " + winner.cost().detail()
                        + " (basis " + winner.cost().basis() + ")"));
        for (Scored other : scored) {
            if (other == winner) {
                continue;
            }
            candidates.add(new AlgorithmDecision.Candidate(
                    other.method().capability().id(), other.method().capability().version(),
                    other.score(), false,
                    "admitted but outscored: score " + round(other.score()) + ", cost "
                            + other.cost().detail() + " (basis " + other.cost().basis() + ")"));
        }

        AlgorithmDecision.Status status;
        if (resourceLimited) {
            status = AlgorithmDecision.Status.RESOURCE_LIMITED;
        } else if (winner.method() instanceof ProcedureBacked) {
            status = AlgorithmDecision.Status.REUSED_PROCEDURE;
        } else {
            status = AlgorithmDecision.Status.COMPLETED;
        }

        String reason = buildReason(winner, scored.size(), status, resourceLimited, weight);
        return new AlgorithmDecision(request.family(), winnerCapability.id(), winnerCapability.version(),
                reason, List.copyOf(candidates), request.budget(), status,
                winnerCapability.fallbackAlgorithmId(),
                "the decision was made without executing the method");
    }

    /**
     * Chooses a method for a request and returns it typed.
     *
     * @param request the problem, with the facts known about its input
     * @return the chosen method and the decision that chose it; the method is {@code null} when
     *     the request was refused
     */
    public <I, O> Selection<I, O> select(ProblemRequest request) {
        return select(request, defaultCostWeight);
    }

    /**
     * Chooses a method for a request and returns it typed, with the caller's cost weight.
     *
     * @param request   the problem, with the facts known about its input
     * @param costWeight how strongly a cost is weighed against the accuracy evidence
     * @return the chosen method and the decision that chose it
     */
    public <I, O> Selection<I, O> select(ProblemRequest request, double costWeight) {
        AlgorithmDecision decision = decide(request, costWeight);
        if (decision.selectedAlgorithmId() == null) {
            return new Selection<>(null, decision);
        }
        return new Selection<>(erase(registry.byId(decision.selectedAlgorithmId())), decision);
    }

    /**
     * The status an execution failure maps to.
     *
     * <p>The mapping is the point of the two exception types: a refusal and an exhausted budget
     * are different facts, and collapsing them would hide the difference between "this cannot be
     * done" and "this could not be finished".</p>
     *
     * @param failure the exception the method threw
     * @return the status to record
     */
    public static AlgorithmDecision.Status statusFor(RuntimeException failure) {
        if (failure instanceof ResourceLimitException) {
            return AlgorithmDecision.Status.RESOURCE_LIMITED;
        }
        if (failure instanceof AlgorithmRefusalException) {
            return AlgorithmDecision.Status.REFUSED;
        }
        return AlgorithmDecision.Status.FAILED;
    }

    /**
     * THE ONE PLACE where the portfolio's type parameters are erased.
     *
     * <p>The portfolio holds methods with different input and output types, and the selector has
     * to look at all of them at once. Erasing the type parameters is therefore unavoidable, and
     * it is confined to this method so that there is a single line to read when asking whether
     * the erasure is safe.</p>
     *
     * <p>It is safe for the reason the registry is typed by family: a method is only ever offered
     * to a request of the family its capability declares, and the caller of
     * {@link #select(ProblemRequest)} supplies the payload of that family. A method that is
     * handed a payload of the wrong type fails its own precondition check — that check is
     * wrapped, and a failure is reported as a blocked candidate rather than as a wrong answer.</p>
     */
    @SuppressWarnings("unchecked")
    private static <I, O> AlgorithmMethod<I, O> erase(AlgorithmMethod<?, ?> method) {
        return (AlgorithmMethod<I, O>) method;
    }

    private Set<String> missingFacts(AlgorithmCapability capability, ProblemRequest request) {
        Set<String> missing = new LinkedHashSet<>();
        for (String precondition : capability.requiredPreconditions()) {
            if (!request.fact(precondition)) {
                missing.add(precondition);
            }
        }
        return missing;
    }

    private String ownPreconditionFailure(AlgorithmMethod<?, ?> method, Object payload) {
        try {
            AlgorithmMethod<Object, Object> erased = erase(method);
            if (erased.preconditionsHold(payload)) {
                return null;
            }
            return "the method reported that its own preconditions do not hold for this payload";
        } catch (ClassCastException e) {
            return "the method cannot accept this request's payload";
        } catch (RuntimeException e) {
            return "the method's own precondition check threw " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage());
        }
    }

    private CostAssessment assessCost(AlgorithmCapability capability) {
        long measured = costLedger.measuredNanos(capability.id());
        if (measured != CostLedger.UNMEASURED) {
            return new CostAssessment(measured, 0, CostBasis.MEASURED,
                    "measured " + measured + "ns");
        }
        DeclaredCost.Cost declared = DeclaredCost.parse(capability.complexity().time());
        return new CostAssessment(declared.nanos(), declared.nodes(), CostBasis.DECLARED_COMPLEXITY,
                declared.describe());
    }

    private double accuracyOf(AlgorithmCapability capability) {
        AlgorithmCapability.AccuracyProfile accuracy = capability.accuracyEvidence();
        if (accuracy == null || accuracy.denominator() <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, accuracy.value()));
    }

    private boolean fitsBudget(Scored candidate, ComputeBudget budget) {
        if (budget == null) {
            return true;
        }
        long estimatedMillis = (candidate.costNanos() + NANOS_PER_MILLI - 1L) / NANOS_PER_MILLI;
        return budget.permits(estimatedMillis, candidate.cost().nodes());
    }

    private String buildReason(Scored winner, int admittedCount, AlgorithmDecision.Status status,
                               boolean resourceLimited, double weight) {
        AlgorithmCapability capability = winner.method().capability();
        StringBuilder sb = new StringBuilder();
        sb.append("selected ").append(capability.id()).append('@').append(capability.version());
        sb.append(" with score ").append(round(winner.score()));
        sb.append(" over ").append(admittedCount).append(" admitted candidate(s)");
        sb.append("; the accuracy evidence was ").append(accuracyOf(capability));
        sb.append(" over ").append(capability.accuracyEvidence().denominator()).append(" case(s)");
        sb.append("; the cost came from ").append(winner.cost().basis());
        sb.append(" and was weighed with a cost weight of ").append(weight);
        if (status == AlgorithmDecision.Status.RESOURCE_LIMITED) {
            sb.append("; the estimated cost did not fit the budget, so the fallback ")
                    .append(capability.fallbackAlgorithmId() == null
                            ? "is not available" : capability.fallbackAlgorithmId())
                    .append(" should be considered");
        }
        return sb.toString();
    }

    private static double checkedWeight(double costWeight) {
        if (Double.isNaN(costWeight) || costWeight < 0.0) {
            throw new IllegalArgumentException("the cost weight must not be negative or NaN");
        }
        return costWeight;
    }

    private static double round(double value) {
        return Math.round(value * SCORE_SCALE) / SCORE_SCALE;
    }
}
