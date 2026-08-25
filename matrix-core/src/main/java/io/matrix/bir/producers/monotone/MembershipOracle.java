package io.matrix.bir.producers.monotone;

/**
 * Membership oracle for monotone Boolean function decoding (DESIGN-09 §2).
 *
 * <p>The oracle abstracts the teacher: any source that can evaluate the
 * target monotone function {@code f : {0,1}^k -> {0,1}} on a single vertex.
 */
@FunctionalInterface
public interface MembershipOracle {

    /** @param vertex index in {@code [0, 2^k)}; bit i = (vertex >> i) & 1
     *  @return value of the target monotone function */
    boolean eval(int vertex);
}
