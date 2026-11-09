package io.matrix.contract;

import java.util.List;

/**
 * W51 — a validated, reusable way of doing something.
 *
 * <p><b>A skill is a procedure that earned the right to be reused.</b> The procedure says
 * <i>how</i>; the skill says <i>that this worked</i>, how many times it was tried, how often
 * it succeeded, and on what evidence. The repository already separates these — the algorithm
 * portfolio has a {@code Procedure} and a {@code ProcedureStore}, and the kernel promotes a
 * procedure when it has been validated — but the promoted thing has no canonical form and no
 * record of its own success rate, so "the system learned a skill" is not a fact the system can
 * report.</p>
 *
 * <p><b>The validation cases are named.</b> A skill that cannot say which cases validated it is
 * a claim, not a skill. The cases are kept by identifier so that the validation can be
 * re-run.</p>
 *
 * <p><b>Reuse is a decision, not a fact.</b> {@link #reusable()} is the system's own judgement
 * that the procedure should be offered again. It is set by what happened, and it can be
 * withdrawn.</p>
 *
 * @param id               the identity of the skill
 * @param name             what the skill is called
 * @param procedureId      the procedure the skill consists of
 * @param validationCaseIds the cases that validated the skill
 * @param attempts         how many times the skill was tried
 * @param successes        how many times it succeeded
 * @param reusable         whether the system offers the skill again
 * @param learnedFromEpisodeIds the episodes the skill was learned from
 * @param version          the revision number of the skill
 * @param provenance       where the skill came from
 */
public record Skill(
        String id,
        String name,
        String procedureId,
        List<String> validationCaseIds,
        int attempts,
        int successes,
        boolean reusable,
        List<String> learnedFromEpisodeIds,
        int version,
        Provenance provenance) {

    public Skill {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("skill id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("skill name must not be blank");
        }
        if (procedureId == null || procedureId.isBlank()) {
            throw new IllegalArgumentException("skill procedure id must not be blank");
        }
        validationCaseIds = validationCaseIds == null ? List.of() : List.copyOf(validationCaseIds);
        learnedFromEpisodeIds = learnedFromEpisodeIds == null ? List.of() : List.copyOf(learnedFromEpisodeIds);
        if (attempts < 0 || successes < 0) {
            throw new IllegalArgumentException("skill counters must not be negative");
        }
        if (successes > attempts) {
            throw new IllegalArgumentException("skill successes must not exceed its attempts");
        }
        if (version < 1) {
            throw new IllegalArgumentException("skill version must be at least one");
        }
    }

    /**
     * The rate at which the skill succeeded.
     *
     * @return the success rate in {@code [0,1]}, or zero when the skill was never tried
     */
    public double successRate() {
        if (attempts == 0) {
            return 0.0;
        }
        return (double) successes / (double) attempts;
    }

    /** True when the skill was validated by at least one case. */
    public boolean validated() {
        return !validationCaseIds.isEmpty();
    }

    /**
     * The skill after an attempt.
     *
     * @param succeeded whether the attempt succeeded
     * @return the skill with the attempt counted
     */
    public Skill afterAttempt(boolean succeeded) {
        return new Skill(id, name, procedureId, validationCaseIds, attempts + 1,
                succeeded ? successes + 1 : successes, reusable, learnedFromEpisodeIds, version,
                provenance);
    }

    @Override
    public String toString() {
        return "Skill[" + id + " " + name + " " + successes + "/" + attempts + "]";
    }
}
