package io.matrix.cognitive.algorithms;

/**
 * W53 — the reliability of a method, as an estimate with its uncertainty, and the state that
 * says whether the estimate may be acted on at all.
 *
 * <p><b>This class implements the rule of the wave's own research</b>
 * ({@code docs-v2/research/W53-RESEARCH-D-RELIABILITY-CALIBRATION.md} §4). It is written
 * down here so that the decision path carries the rule and not a summary of it.</p>
 *
 * <p><b>Why an interval and not a number.</b> A point estimate over a handful of observations
 * says "80 %" with no hint that the same method would say "45 %" on five other observations.
 * The Wilson score interval says what the data can bear, and <b>the width of that interval is
 * the honest statement about the evidence</b>. The estimator is closed-form — no iterative
 * solver, no bootstrap, no clock and no randomness — so an identical state and an identical
 * evidence give an identical decision, down to the last bit.</p>
 *
 * <p><b>The three states.</b></p>
 * <ul>
 *   <li>{@link State#NO_EVIDENCE} — nothing was observed for this stratum. The term is zero.</li>
 *   <li>{@link State#NOT_YET_USABLE} — something was observed, but the interval is too wide
 *       to act on. The term is zero, and the observation changes the <i>record</i> and the
 *       <i>state</i>, never the score.</li>
 *   <li>{@link State#ESTIMATED} — the interval is narrow enough to act on. The term is the
 *       lower limit of the interval, never the point estimate: a method is preferred on the
 *       evidence that <i>guarantees</i> the most.</li>
 * </ul>
 *
 * <p><b>The refusal to pool.</b> The verdicts recorded without a stratum say how a method
 * behaved over everything it was ever asked. That is not evidence for any particular request,
 * so a pooled row is {@link State#NO_EVIDENCE} for a stratified request — the refusal to pool
 * is a first-class state, not an accident of the key.</p>
 */
public record MethodReliability(
        String methodId,
        String family,
        String capabilityVersion,
        int calls,
        int decisions,
        int validated,
        int abstentions,
        int notEvidence,
        double jeffreysMean,
        double lowerBound,
        double upperBound,
        double width,
        State state,
        String reason) {

    /**
     * The state of an estimate.
     */
    public enum State {

        /** Nothing was observed for this stratum; the term is zero. */
        NO_EVIDENCE,

        /** Something was observed, but the interval is too wide to act on; the term is zero. */
        NOT_YET_USABLE,

        /** The interval is narrow enough to act on; the term is its lower limit. */
        ESTIMATED
    }

    /**
     * The confidence level of the interval.
     *
     * <p>NAMED, not derived: the level is part of the rule and must not drift with a change
     * of the default of anything else.</p>
     */
    public static final double RELIABILITY_CONFIDENCE = 0.90;

    /**
     * The z-score of the confidence level.
     *
     * <p>DERIVED from {@link #RELIABILITY_CONFIDENCE} — the 90 % two-sided value of the
     * normal quantile. It is a constant of the estimator, not a tunable of the system, and it
     * is written down so that the interval is reproducible to the last bit.</p>
     */
    public static final double RELIABILITY_Z = 1.6448536269514722;

    /**
     * The widest interval that may be acted on.
     *
     * <p>NAMED: an interval wider than this says nothing, and a method whose evidence says
     * nothing must not move the score.</p>
     */
    public static final double RELIABILITY_MAX_WIDTH = 0.20;

    /**
     * The success side of the Jeffreys prior.
     *
     * <p>NAMED: the prior of the estimator is part of the rule and must not drift.</p>
     */
    public static final double JEFFREYS_PRIOR_SUCCESSES = 0.5;

    /**
     * The trial side of the Jeffreys prior.
     *
     * <p>NAMED: the prior of the estimator is part of the rule and must not drift.</p>
     */
    public static final double JEFFREYS_PRIOR_TRIALS = 1.0;

    /**
     * The scale that turns a fraction into a percentage.
     *
     * <p>NAMED: the decision text reads the confidence level in per cent.</p>
     */
    public static final double PERCENT = 100.0;

    /**
     * The estimate of a method that has never been measured.
     *
     * @param methodId the method
     * @return the empty estimate
     */
    public static MethodReliability neverMeasured(String methodId) {
        return new MethodReliability(methodId, "", "", 0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 1.0,
                State.NO_EVIDENCE, "no measured reliability for this method in this stratum");
    }

    /**
     * The estimate of a method, computed over the trials in which it decided.
     *
     * @param verdict the verdicts of the method in the stratum
     * @return the estimate, never null
     */
    public static MethodReliability of(MethodVerdict verdict) {
        if (verdict == null) {
            throw new IllegalArgumentException("the verdict must not be null");
        }
        int decisions = verdict.decisions();
        int validated = verdict.validated();
        if (verdict.pooled()) {
            // The observations exist, but POOLED over the strata. A pooled rate answers "how
            // often does this method behave as recorded, over everything we ever ran it on" —
            // a different question from "how reliable is this method for this request" — and a
            // confidence interval over it would be a precise statement about the wrong
            // quantity. The refusal to pool is therefore a state of its own.
            return new MethodReliability(verdict.methodId(), verdict.family(),
                    verdict.capabilityVersion(), verdict.calls(), decisions, validated,
                    verdict.abstained(), verdict.notEvidence(), 0.0, 0.0, 0.0, 1.0,
                    State.NO_EVIDENCE,
                    "the observations are pooled over the strata, and a pooled rate is not "
                            + "evidence for this request");
        }
        double jeffreys = decisions == 0 ? 0.0
                : (validated + JEFFREYS_PRIOR_SUCCESSES) / (decisions + JEFFREYS_PRIOR_TRIALS);
        if (decisions == 0) {
            // Nothing was decided. Something may still have been observed: a call that
            // abstained or that was never verified is an observation, and the state says
            // so. Only a stratum in which nothing at all was observed is NO_EVIDENCE.
            String reason = verdict.calls() == 0
                    ? "no measured reliability for this method in this stratum"
                    : "no call of this method asserted an outcome in this stratum, so there is "
                            + "nothing to estimate";
            State state = verdict.calls() == 0 ? State.NO_EVIDENCE : State.NOT_YET_USABLE;
            return new MethodReliability(verdict.methodId(), verdict.family(),
                    verdict.capabilityVersion(), verdict.calls(), 0, 0, verdict.abstained(),
                    verdict.notEvidence(), 0.0, 0.0, 0.0, 1.0, state, reason);
        }
        double lower = wilsonLower(validated, decisions);
        double upper = wilsonUpper(validated, decisions);
        double width = upper - lower;
        State state = width < RELIABILITY_MAX_WIDTH ? State.ESTIMATED : State.NOT_YET_USABLE;
        String reason = state == State.ESTIMATED
                ? "the " + (int) (RELIABILITY_CONFIDENCE * PERCENT) + " % lower bound is "
                        + lower + " over " + decisions + " decided trial(s) of " + verdict.calls()
                        + " call(s)"
                : "measured over " + decisions + " decided trial(s) of " + verdict.calls()
                        + " call(s); the estimate is too imprecise to act on (the interval is "
                        + width + " wide)";
        return new MethodReliability(verdict.methodId(), verdict.family(),
                verdict.capabilityVersion(), verdict.calls(), decisions, validated,
                verdict.abstained(), verdict.notEvidence(), jeffreys, lower, upper, width,
                state, reason);
    }

    /**
     * The lower limit of the Wilson score interval.
     *
     * @param successes how many of the decided trials were validated
     * @param decisions how many decided trials there were
     * @return the lower limit of the interval
     */
    public static double wilsonLower(int successes, int decisions) {
        return wilsonBound(successes, decisions, true);
    }

    /**
     * The upper limit of the Wilson score interval.
     *
     * @param successes how many of the decided trials were validated
     * @param decisions how many decided trials there were
     * @return the upper limit of the interval
     */
    public static double wilsonUpper(int successes, int decisions) {
        return wilsonBound(successes, decisions, false);
    }

    private static double wilsonBound(int successes, int decisions, boolean lower) {
        if (decisions <= 0) {
            return 0.0;
        }
        double n = decisions;
        double z = RELIABILITY_Z;
        double p = successes / n;
        double zSquared = z * z;
        double centre = p + zSquared / (2.0 * n);
        double margin = z * Math.sqrt(p * (1.0 - p) / n + zSquared / (4.0 * n * n));
        double denominator = 1.0 + zSquared / n;
        double bound = lower ? (centre - margin) / denominator : (centre + margin) / denominator;
        return Math.max(0.0, Math.min(1.0, bound));
    }
}
