package io.matrix.cognitive.microworld;

import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * W44.4 — the lamp world: two inputs, one output, one hidden rule.
 *
 * <p>The world is the simplest thing that still has something to learn: {@code power} and
 * {@code switch} go in, {@code lamp} comes out, and what connects them is not written down
 * anywhere a learner can reach.</p>
 *
 * <h2>The rule does not leave this class</h2>
 *
 * <p>The rule is applied by {@link #applyHiddenRule(WorldInput)}, which is <b>private</b>. There is
 * no public method, no public constant and no public field that returns it, and there is no way to
 * ask this object what it does: {@link #probe(WorldInput)} performs the rule and reports the
 * result. That is deliberate, and it is the property the whole scenario rests on — a learner that
 * could read the rule would not be learning, it would be copying.</p>
 *
 * <p>The rule is still REACHABLE BY INFERENCE, and that is the point: the sixteen Boolean
 * functions over two variables are all in the hypothesis space, and the conjunction is one of
 * them. Narrowing the space to the conjunction is the learner's work, and the ambiguity the
 * learner has to resolve is real because the space starts unbiased.</p>
 *
 * <h2>The adversarial probe is a fault injector, not a second rule</h2>
 *
 * <p>{@link #adversarialProbe(WorldInput)} answers with the NEGATION of what the world does. It
 * exists so that a scenario can prove the learner DETECTS a contradiction and does not silently
 * rewrite its model — a property that cannot be demonstrated against a world that never
 * contradicts itself. It is not an alternative behaviour of the world, and nothing in the learning
 * path may treat it as one: an observation it produces is marked
 * {@link Observation#adversarial() adversarial}, and the session treats a contradiction it causes
 * as an OPEN question about the instrument rather than as evidence about the world.</p>
 *
 * <h2>No clock, no randomness</h2>
 *
 * <p>The instant of an observation is supplied by the caller through a {@link Supplier}; the
 * default is a fixed constant, so the no-argument constructor is fully deterministic. The
 * identifiers of the observations are a monotonic counter, so the same sequence of probes always
 * produces the same identifiers.</p>
 */
public final class LampEnvironment implements MicroWorldEnvironment {

    /** The name of the first input variable. */
    public static final String POWER_VARIABLE = "power";

    /** The name of the second input variable. */
    public static final String SWITCH_VARIABLE = "switch";

    /** The name of the outcome variable. */
    public static final String LAMP_VARIABLE = "lamp";

    /** The instant a probe reports when the caller supplies none. */
    public static final String DEFAULT_OBSERVED_AT = "1970-01-01T00:00:00Z";

    /** The prefix of the identifier of an ordinary observation. */
    public static final String OBSERVATION_ID_PREFIX = "obs-";

    /** The prefix of the identifier of a fault-injected observation. */
    public static final String ADVERSARIAL_ID_PREFIX = "obs-adv-";

    private final Supplier<String> observedAt;
    private int probeCount;

    /** An environment that reports a fixed instant. */
    public LampEnvironment() {
        this(() -> DEFAULT_OBSERVED_AT);
    }

    /**
     * An environment that reports the instant the caller supplies.
     *
     * @param observedAt supplies the ISO-8601 instant of each observation; must not be null and
     *                    must not return null
     */
    public LampEnvironment(Supplier<String> observedAt) {
        this.observedAt = Objects.requireNonNull(observedAt, "observedAt");
    }

    @Override
    public Observation probe(WorldInput input) {
        Objects.requireNonNull(input, "input");
        requireKnownAssignment(input);
        return new Observation(OBSERVATION_ID_PREFIX + (++probeCount), input, applyHiddenRule(input),
                false, observedAt());
    }

    /**
     * Asks the world a question and gets an answer that contradicts the world.
     *
     * <p><b>THIS IS A FAULT-INJECTION FACILITY, NOT A SECOND RULE.</b> The world has one
     * behaviour; this method reports the opposite of it so that a scenario can prove the learner
     * notices a contradiction instead of quietly rewriting its model. The observation it returns
     * is flagged {@link Observation#adversarial() adversarial}, and the session refuses to apply
     * such an observation to its model — it records the conflict and asks for review.</p>
     *
     * @param input the assignment to try
     * @return the observation, flagged as adversarial
     */
    public Observation adversarialProbe(WorldInput input) {
        Objects.requireNonNull(input, "input");
        requireKnownAssignment(input);
        return new Observation(ADVERSARIAL_ID_PREFIX + (++probeCount), input, !applyHiddenRule(input),
                true, observedAt());
    }

    @Override
    public Set<String> variables() {
        return WorldInput.KNOWN_VARIABLES;
    }

    @Override
    public String outcomeVariable() {
        return LAMP_VARIABLE;
    }

    /**
     * The world's behaviour.
     *
     * <p><b>PRIVATE, ON PURPOSE.</b> Nothing outside this class may read the rule. It is not
     * merely undocumented: it is inaccessible, so that a learner cannot reach it by accident, by
     * a subclass, or by a test in this package.</p>
     */
    private boolean applyHiddenRule(WorldInput input) {
        return input.get(POWER_VARIABLE) && input.get(SWITCH_VARIABLE);
    }

    /**
     * Refuses an assignment the world does not understand.
     *
     * <p>The world's vocabulary is the world's business; a caller that names a variable the world
     * does not have gets a refusal rather than a silently false answer.</p>
     */
    private void requireKnownAssignment(WorldInput input) {
        if (!input.variables().equals(WorldInput.KNOWN_VARIABLES)) {
            throw new IllegalArgumentException("the world has the variables " + variables()
                    + " and does not accept the assignment " + input.key());
        }
    }

    private String observedAt() {
        String instant = observedAt.get();
        if (instant == null || instant.isBlank()) {
            throw new IllegalStateException("the observedAt supplier returned no instant");
        }
        return instant;
    }
}
