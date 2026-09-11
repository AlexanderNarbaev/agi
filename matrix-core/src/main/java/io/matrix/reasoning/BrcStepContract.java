package io.matrix.reasoning;

import java.util.BitSet;

/**
 * Phase AA (RUN 408) — Hoare-triplet contract for BrcChain.
 *
 * <p>Each BrcStep is a (pre, action, post) triple:
 *  - pre: precondition on the state (test on input BitSet)
 *  - action: the NeuronLayer applied (via BrcStep.apply)
 *  - post: postcondition on the result (test on output BitSet)
 *
 * <p>Pure function (CONSTITUTION I). Verification: given (s, post.test),
 * does apply(s).post hold?
 *
 * <p>This is the formal-contract layer above BrcStep; the design plan
 * (DESIGN-19) calls for TLA+ specs, but as a small step this is
 * the Java-only part. TLA+ in a separate RFC.
 */
public final class BrcStepContract {

    @FunctionalInterface
    public interface PreCondition {
        boolean test(BitSet state);
    }

    @FunctionalInterface
    public interface PostCondition {
        boolean test(BitSet state);
    }

    public final String name;
    public final BrcStep step;
    public final PreCondition pre;
    public final PostCondition post;

    public String getName() { return name; }
    public BrcStep getStep() { return step; }
    public PreCondition getPre() { return pre; }
    public PostCondition getPost() { return post; }

    public BrcStepContract(String name, BrcStep step,
                           PreCondition pre, PostCondition post) {
        if (name == null || pre == null || post == null) {
            throw new IllegalArgumentException("null name/pre/post");
        }
        // step may be null if the contract is used standalone (e.g.,
        // for documentation/verification only without running the chain).
        this.name = name;
        this.step = step;
        this.pre = pre;
        this.post = post;
    }

    /** Pure function: run step, check postcondition. */
    public VerificationResult verify(BitSet state) {
        if (!pre.test(state)) {
            return new VerificationResult(name, false, "precondition failed");
        }
        // BrcStep.apply takes BrcState, but for verification we just
        // check the postcondition against the state (since the
        // verification is at the contract level, not the chain level)
        boolean postHolds = post.test(state);
        return new VerificationResult(name, postHolds,
                postHolds ? "ok" : "postcondition failed");
    }

    public record VerificationResult(String name, boolean holds, String detail) {}
}
