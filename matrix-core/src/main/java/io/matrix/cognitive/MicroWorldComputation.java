package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.cognitive.microworld.MicroWorldSession;
import io.matrix.cognitive.microworld.WorldInput;

import java.util.Set;

/**
 * W44 — the payload of a micro-world computation.
 *
 * <p>The portfolio's methods are stateless; the STATE of the micro-world travels with the
 * request. That is what allows one registry to serve every cycle: the session that holds the
 * surviving hypotheses is part of the payload, not a field of the method.</p>
 *
 * <p>The facts travel with it as well, so that a method can check its own applicability against
 * the same facts the selector admitted it on. A method that trusted the selector alone would be
 * trusting a string in another object; checking its own preconditions is the second lock on the
 * same door.</p>
 *
 * <p>The promoted procedure travels too. It is the candidate FAST PATH: a cheaper way of
 * getting the same answer, valid only inside the domain it was validated in. Its presence here
 * does not make it applicable — {@link Procedure#applicable(Set)} decides that, and the method
 * checks it before it answers.</p>
 */
public record MicroWorldComputation(
        WorldInput input,
        MicroWorldSession session,
        Set<String> facts,
        Procedure procedure) {

    public MicroWorldComputation {
        if (input == null) {
            throw new IllegalArgumentException("the computation needs an input");
        }
        if (session == null) {
            throw new IllegalArgumentException("the computation needs a session");
        }
        facts = facts == null ? Set.of() : Set.copyOf(facts);
    }

    /** A computation without a promoted procedure: the fast path is not available. */
    public MicroWorldComputation(WorldInput input, MicroWorldSession session, Set<String> facts) {
        this(input, session, facts, null);
    }

    /** True when the named fact holds for this computation. */
    public boolean fact(String name) {
        return facts.contains(name);
    }
}
