package io.matrix.cognitive.canonical;

import io.matrix.cognitive.ErrorSignal;
import io.matrix.cognitive.LearningTrace;
import io.matrix.cognitive.Percept;
import io.matrix.cognitive.Prediction;
import io.matrix.cognitive.algorithms.AlgorithmDecision;
import io.matrix.cognitive.microworld.Experiment;
import io.matrix.cognitive.microworld.RuleHypothesis;
import io.matrix.cognitive.worldstate.WorldStateMemory;
import io.matrix.contract.Reflection;

import java.util.List;

/**
 * W52 §3 — the facts of one cycle, as the runtime holds them.
 *
 * <p><b>Why this record exists.</b> The runtime's own vocabulary is not the canonical one:
 * the kernel holds a {@link Prediction} of its own, a {@link Percept} of its own, a
 * {@link RuleHypothesis} and an {@link Experiment} of the micro-world's own, and the store
 * holds a procedure of the algorithms package's own. The canonical vocabulary lives in
 * {@code io.matrix.contract}. This record is the <b>explicit boundary</b> between the two:
 * everything the mapper needs, in one place, named — so that the mapping is a tested
 * component rather than an accident of who happened to import what.</p>
 *
 * <p><b>It is a bag of facts, and it says so.</b> It is not a domain model and it does not
 * carry behaviour. Its purpose is to be the single, explicit list of what a cycle knows
 * about itself at the moment it ends.</p>
 *
 * <p><b>Everything here may be absent.</b> A cycle that was refused before the action has
 * no observation, no prediction and no decision; a cycle in which nothing was promoted has
 * no procedure. The mapper turns an absent fact into an absent canonical record — never
 * into an invented one.</p>
 *
 * @param episodeId    the episode the cycle belongs to
 * @param at           the cycle's own timestamp
 * @param signalId     the identifier of the input signal
 * @param channel      the channel the signal arrived on
 * @param subject      what the cycle was about, in the runtime's own words
 * @param percept      the perception of the signal
 * @param hypotheses   the hypotheses that survived to the end of the cycle
 * @param experiment   the experiment the cycle proposed
 * @param prediction   the prediction the cycle made before it acted
 * @param observation  the observation the cycle received
 * @param error        the error signal, when the prediction and the observation disagreed
 * @param decision     the method selection
 * @param world        the world-state memory
 * @param procedure    the promoted procedure, when one was promoted
 * @param trace        the learning trace
 * @param reflection   the cycle's reflection
 * @param capabilityId the capability of the method that ran, when one ran
 */
public record CycleFacts(
        String episodeId,
        String at,
        String signalId,
        io.matrix.cognitive.CognitiveSignal.Channel channel,
        String subject,
        Percept percept,
        List<RuleHypothesis> hypotheses,
        Experiment experiment,
        Prediction prediction,
        io.matrix.cognitive.microworld.Observation observation,
        ErrorSignal error,
        AlgorithmDecision decision,
        WorldStateMemory world,
        io.matrix.cognitive.algorithms.Procedure procedure,
        LearningTrace trace,
        Reflection reflection,
        String capabilityId) {

    public CycleFacts {
        hypotheses = hypotheses == null ? List.of() : List.copyOf(hypotheses);
    }
}
