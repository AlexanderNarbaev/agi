package io.matrix.knowledge;

import io.matrix.cognitive.CognitiveError;
import io.matrix.cognitive.CognitiveErrorStream;
import io.matrix.neuron.HebbianUpdater;
import io.matrix.neuron.HdcEncoding;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The workspace's Hebbian bindings, and the error-driven decay over them
 * (RECON-W42.7).
 *
 * <p>The owner's decision D-012: the shadow classes are wired to the live
 * engines. The {@link io.matrix.cognitive.ErrorDrivenLearner} was the
 * named example — "to the consolidation". Its mathematics was verified
 * before it was wired, and the verification found a real defect: the decay
 * had no floor, so the same error repeated drove the accumulator
 * arbitrarily far below zero, which is what the mission's §9 forbids.
 *
 * <p><b>What this class does.</b> It gives the knowledge domain the two
 * things the error-driven learner needs:
 *
 * <ul>
 *   <li>an <b>observation</b>: the learner's wrong answer, encoded into the
 *       positions of the hypercube. The encoding is deterministic — the
 *       same terms always light the same positions — so the same error
 *       always decays the same bindings, and the effect is
 *       reproducible;</li>
 *   <li>a <b>state</b>: the concept's own bindings, built by a Hebbian
 *       update over its claims. The concept is the pre-synaptic vector
 *       and each of its claims the post-synaptic one, so the positions
 *       that the concept and its claim agree on are the positions that
 *       hold the concept's binding.</li>
 * </ul>
 *
 * <p><b>What the decay means here.</b> The learner has been wrong about a
 * concept, in two different sessions — only the VERIFIED errors are
 * acted upon, because a single wrong answer may be a slip. The
 * association that the wrong answer shared with the concept is weakened,
 * and only that association: the decay is targeted at the direction of
 * the failure, and it is floored at the deadband's lower edge, so a
 * repeated error cannot weaken the association without bound.
 */
public final class HebbianBindings {

    /** How many positions each term lights in the hypercube. */
    public static final int BITS_PER_TERM = 8;

    /** The learning rate of the state build. */
    public static final float BUILD_ETA = 0.5f;

    /** The decay rate of the state build: the build does not decay. */
    public static final float BUILD_LAMBDA = 0.0f;

    /** The learning rate of the error-driven decay. */
    public static final float DECAY_ETA = 0.05f;

    /** The deadband's threshold. */
    public static final float THRESHOLD = HebbianUpdater.DEFAULT_THRESHOLD;

    private HebbianBindings() {
    }

    /**
     * The observation of a text: the positions of its terms, lit.
     *
     * <p>The encoding is deterministic: a term's positions come from the
     * term's own hash, so the same term always lights the same
     * positions, in this run and in the next.
     *
     * @param terms the terms
     * @return the observation
     */
    public static float[] observation(List<String> terms) {
        float[] observation = new float[HdcEncoding.DIM];
        if (terms == null) {
            return observation;
        }
        for (String term : terms) {
            if (term == null || term.isBlank()) {
                continue;
            }
            for (int position : positionsOf(term)) {
                observation[position] = 1.0f;
            }
        }
        return observation;
    }

    /**
     * The observation of a text.
     *
     * @param text the text
     * @return the observation
     */
    public static float[] observation(String text) {
        return observation(termsOf(text));
    }

    /**
     * The positions a term lights.
     *
     * @param term the term
     * @return the positions
     */
    public static int[] positionsOf(String term) {
        int hash = term.hashCode();
        int[] positions = new int[BITS_PER_TERM];
        for (int i = 0; i < BITS_PER_TERM; i++) {
            int mixed = hash * (i + 1) * 0x9E3779B1;
            positions[i] = Math.floorMod(mixed, HdcEncoding.DIM);
        }
        return positions;
    }

    /**
     * The observation as the hypercube's bits.
     *
     * @param observation the observation
     * @return the bits
     */
    public static long[] bits(float[] observation) {
        long[] bits = new long[HdcEncoding.WORDS];
        for (int i = 0; i < Math.min(observation.length, HdcEncoding.DIM); i++) {
            if (observation[i] > 0.0f) {
                bits[i >>> 6] |= 1L << (i & 63);
            }
        }
        return bits;
    }

    /**
     * The strength of a state in a direction: the sum of its accumulator
     * at the observation's positions.
     *
     * <p>This is the measure the decay moves. The decay lowers the
     * ACCUMULATOR; the binding's bit is cleared by the next thresholding
     * pass, and a test that counted the bits would not see the first
     * decay at all. The sum is the honest measurement of what the decay
     * did.
     *
     * @param state the state
     * @param observation the direction
     * @return the strength
     */
    public static double strengthAt(io.matrix.neuron.HebbianUpdater.State state,
                                     float[] observation) {
        double sum = 0.0;
        for (int i = 0; i < Math.min(observation.length, HdcEncoding.DIM); i++) {
            if (observation[i] > 0.0f) {
                sum += state.accumulator[i];
            }
        }
        return sum;
    }

    /**
     * The terms of a text.
     *
     * @param text the text
     * @return the terms
     */
    public static List<String> termsOf(String text) {
        Set<String> terms = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return List.of();
        }
        for (String token : TextFeatures.originalTokens(text)) {
            if (!TextFeatures.isStopWord(token)) {
                terms.add(token);
            }
        }
        return List.copyOf(terms);
    }

    /**
     * The state of a concept: its bindings, built from its claims.
     *
     * @param snapshot the workspace
     * @param conceptId the concept
     * @return the state
     */
    public static HebbianUpdater.State stateFor(KnowledgeSnapshot snapshot, String conceptId) {
        HebbianUpdater.State state = HebbianUpdater.empty();
        if (snapshot == null || conceptId == null) {
            return state;
        }
        Concept concept = snapshot.concepts().stream()
                .filter(c -> c.id().equals(conceptId))
                .findFirst()
                .orElse(null);
        if (concept == null) {
            return state;
        }
        long[] pre = bits(observation(conceptTerms(concept)));
        for (Claim claim : snapshot.claims()) {
            if (!claim.conceptId().equals(conceptId)) {
                continue;
            }
            long[] post = bits(observation(termsOf(claim.proposition())));
            HebbianUpdater.update(state, pre, post, BUILD_ETA, BUILD_LAMBDA, THRESHOLD);
        }
        return state;
    }

    /**
     * The terms of a concept: its name and its definition.
     *
     * @param concept the concept
     * @return the terms
     */
    public static List<String> conceptTerms(Concept concept) {
        List<String> terms = new ArrayList<>(termsOf(concept.name()));
        terms.addAll(termsOf(concept.definition()));
        return terms;
    }

    /**
     * The cognitive error stream of a workspace's VERIFIED errors.
     *
     * <p>Only the verified errors become errors: the mission's §9 says the
     * error-driven integration happens "only after verifying the math",
     * and an error the learner has made in one session may be a slip.
     *
     * @param errors the learning errors
     * @return the stream
     */
    public static CognitiveErrorStream streamOf(List<LearningError> errors) {
        CognitiveErrorStream stream = new CognitiveErrorStream(
                Math.max(1, Math.min(64, errors == null ? 1 : errors.size())));
        if (errors == null) {
            return stream;
        }
        long cycle = 0;
        for (LearningError error : errors) {
            if (error == null || !error.verified()) {
                continue;
            }
            float[] observation = observation(termsOf(error.learnerAnswer()));
            stream.record(new CognitiveError(cycle, CognitiveError.ErrorKind.MEMORY_RETRIEVAL_MISS,
                    observation, error.dimension() + ": " + error.id()));
            cycle++;
        }
        return stream;
    }
}
