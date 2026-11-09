package io.matrix.learning.env;

/**
 * W51 §7 — one learner's episode measured against another's.
 *
 * <p><b>Why a comparison and not a score.</b> A single episode's utility says how much the
 * learner got out of one run; it does not say whether that run was good. Only a second run
 * of a second learner says that, and the comparison is the only place where the two
 * episodes' numbers meet.</p>
 *
 * <p><b>Why the comparison holds reports and not numbers.</b> The numbers are derived. A
 * comparison that carried a copy of the utility would be able to disagree with the report
 * it was made from; this one cannot, because every figure below is read from the reports
 * themselves.</p>
 *
 * <p><b>Why the improvement is in utility units.</b> The utility is a fraction of the
 * hypothesis space, so the difference between two utilities is a fraction of the same space:
 * "this learner eliminated a tenth more of the space than that one". Introducing a second
 * scale for the comparison would make the numbers incomparable with the microworld's own
 * benchmark, which is the measurement this whole layer exists to extend.</p>
 *
 * @param candidate the episode of the learner under test
 * @param baseline  the episode of the learner it is measured against
 */
public record EpisodeComparison(EpisodeReport candidate, EpisodeReport baseline) {

    public EpisodeComparison {
        if (candidate == null) {
            throw new IllegalArgumentException("a comparison needs a candidate");
        }
        if (baseline == null) {
            throw new IllegalArgumentException("a comparison needs a baseline");
        }
    }

    /**
     * Compares two episodes.
     *
     * @param candidate the episode of the learner under test
     * @param baseline  the episode of the learner it is measured against
     * @return the comparison
     */
    public static EpisodeComparison between(EpisodeReport candidate, EpisodeReport baseline) {
        return new EpisodeComparison(candidate, baseline);
    }

    /**
     * How many probes the candidate made.
     *
     * @return the candidate's step count
     */
    public int candidateSteps() {
        return candidate.steps();
    }

    /**
     * How many probes the baseline made.
     *
     * @return the baseline's step count
     */
    public int baselineSteps() {
        return baseline.steps();
    }

    /**
     * How many probes the candidate saved against the baseline.
     *
     * <p>A positive number means the candidate reached its result with fewer probes. A
     * negative number is not a defect: it means the candidate spent more probes to get
     * there, and the utility gain below says whether it was worth them.</p>
     *
     * @return the baseline's steps minus the candidate's steps
     */
    public int stepsSaved() {
        return baseline.steps() - candidate.steps();
    }

    /**
     * How many hypotheses survived the candidate's episode.
     *
     * @return the candidate's survivor count
     */
    public int candidateFinalSurvivors() {
        return candidate.finalSurvivors();
    }

    /**
     * How many hypotheses survived the baseline's episode.
     *
     * @return the baseline's survivor count
     */
    public int baselineFinalSurvivors() {
        return baseline.finalSurvivors();
    }

    /**
     * The utility the candidate reached.
     *
     * @return the candidate's utility
     */
    public double candidateUtility() {
        return candidate.utility();
    }

    /**
     * The utility the baseline reached.
     *
     * @return the baseline's utility
     */
    public double baselineUtility() {
        return baseline.utility();
    }

    /**
     * How much utility the candidate gained over the baseline.
     *
     * @return the candidate's utility minus the baseline's
     */
    public double utilityGain() {
        return candidate.utility() - baseline.utility();
    }

    /**
     * Whether the candidate identified the rule.
     *
     * @return true when the candidate's model settled
     */
    public boolean candidateLearned() {
        return candidate.learned();
    }

    /**
     * Whether the baseline identified the rule.
     *
     * @return true when the baseline's model settled
     */
    public boolean baselineLearned() {
        return baseline.learned();
    }

    /**
     * How much better the candidate is than the given baseline, in utility units.
     *
     * <p>A positive number means the candidate eliminated a larger fraction of the
     * hypothesis space. The argument is a REPORT and not another comparison, so that a
     * candidate can be measured against any episode at all — including one that was not
     * recorded as anybody's baseline.</p>
     *
     * @param baseline the episode to measure against
     * @return the difference in utility
     */
    public double improvementOver(EpisodeReport baseline) {
        return candidate.utility() - baseline.utility();
    }

    @Override
    public String toString() {
        return "EpisodeComparison[candidate " + candidate.episodeId() + " steps="
                + candidateSteps() + " utility=" + candidateUtility()
                + " | baseline " + this.baseline.episodeId() + " steps="
                + baselineSteps() + " utility=" + baselineUtility() + "]";
    }
}
