package io.matrix.cognitive.worldstate;

import io.matrix.contract.BeliefStatus;
import io.matrix.contract.Provenance;
import io.matrix.contract.StateTransition;
import io.matrix.contract.WorldState;
import io.matrix.cognitive.MicroWorldCodec;
import io.matrix.cognitive.microworld.Observation;
import io.matrix.cognitive.microworld.WorldInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 §4 — the world-state memory.
 *
 * <p>These tests are the proof that the role the W51 audit found missing now exists and
 * works: a memory of the system's estimate of a domain, rebuilt from the durable record,
 * consulted before an action, and honest about how much it believes.</p>
 */
class WorldStateMemoryTest {

    /** The instant the tests stamp their facts with. */
    private static final String AT = "2026-11-08T00:00:00Z";

    private static final Provenance PROVENANCE = Provenance.of("the test", AT);

    private static WorldInput assignment(boolean power, boolean switchOn) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        values.put("power", power);
        values.put("switch", switchOn);
        return new WorldInput(values);
    }

    private static Observation observation(String id, WorldInput input, boolean lamp) {
        return new Observation(id, input, lamp, false, AT);
    }

    @Test
    @DisplayName("the memory holds what was observed and says how much it believes it")
    void theMemoryHoldsWhatWasObserved() {
        WorldStateMemory memory = new WorldStateMemory("micro-world.lamp", "ws", PROVENANCE);

        StateTransition transition = memory.apply(
                MicroWorldStateAdapter.fact(observation("obs-1", assignment(false, true), false),
                        StateTransition.Cause.OBSERVATION, null));

        WorldState state = memory.state();
        assertEquals(1L, state.revision());
        assertEquals(1, state.entities().size());
        assertEquals(1, state.properties().size());
        assertTrue(memory.knows(MicroWorldStateAdapter.entityId(assignment(false, true)),
                MicroWorldStateAdapter.PROPERTY_LAMP));
        assertEquals(false, memory.valueOf(
                MicroWorldStateAdapter.entityId(assignment(false, true)),
                MicroWorldStateAdapter.PROPERTY_LAMP).orElseThrow());
        assertEquals(BeliefStatus.OBSERVED, state.properties().get(0).status());
        assertEquals(StateTransition.Cause.OBSERVATION, transition.cause());
    }

    @Test
    @DisplayName("a fact that says what the memory already says changes nothing")
    void theSameFactChangesNothing() {
        WorldStateMemory memory = new WorldStateMemory("micro-world.lamp", "ws", PROVENANCE);
        Observation observed = observation("obs-1", assignment(false, true), false);

        memory.apply(MicroWorldStateAdapter.fact(observed, StateTransition.Cause.OBSERVATION, null));
        StateTransition again = memory.apply(
                MicroWorldStateAdapter.fact(observed, StateTransition.Cause.OBSERVATION, null));

        assertEquals(1L, memory.revision(),
                "the world did not change, so the revision must not advance");
        assertTrue(again.changedProperties().isEmpty(),
                "a transition that changed nothing must say so");
        assertEquals(again.fromStateId(), again.toStateId());
    }

    @Test
    @DisplayName("a fact that changes a value advances the revision and names the change")
    void aChangedValueAdvancesTheRevision() {
        WorldStateMemory memory = new WorldStateMemory("micro-world.lamp", "ws", PROVENANCE);
        WorldInput input = assignment(false, true);

        memory.apply(MicroWorldStateAdapter.fact(observation("obs-1", input, false),
                StateTransition.Cause.OBSERVATION, null));
        StateTransition changed = memory.apply(
                MicroWorldStateAdapter.fact(observation("obs-2", input, true),
                        StateTransition.Cause.ACTION, "act-1"));

        assertEquals(2L, memory.revision());
        assertEquals(1, changed.changedProperties().size());
        assertTrue(changed.changedProperties().containsKey(
                MicroWorldStateAdapter.entityId(input) + "#"
                        + MicroWorldStateAdapter.PROPERTY_LAMP));
        assertEquals(StateTransition.Cause.ACTION, changed.cause());
        assertEquals("act-1", changed.actionId());
        assertTrue(changed.agentCaused());
    }

    @Test
    @DisplayName("a faulty instrument produces a contested fact, not an observed one")
    void aFaultyInstrumentProducesAContestedFact() {
        WorldInput input = assignment(true, false);
        Observation adversarial = new Observation("obs-adv", input, true, true, AT);

        StateFact fact = MicroWorldStateAdapter.fact(adversarial,
                StateTransition.Cause.OBSERVATION, null);

        assertEquals(BeliefStatus.CONTESTED, fact.status(),
                "an answer from a fault-injecting probe must not establish anything");
    }

    @Test
    @DisplayName("the memory is rebuilt by replaying the durable record, so a restart loses nothing")
    void theMemorySurvivesARestartByBeingReplayed() {
        WorldInput first = assignment(false, true);
        WorldInput second = assignment(true, true);
        List<Observation> durable = List.of(
                observation("obs-1", first, false),
                observation("obs-2", second, true));

        WorldStateMemory before = MicroWorldStateAdapter.replay("ws", PROVENANCE, durable);
        WorldStateMemory after = MicroWorldStateAdapter.replay("ws", PROVENANCE, durable);

        assertEquals(before.revision(), after.revision());
        assertEquals(before.transitions().size(), after.transitions().size());
        assertEquals(before.state().entities().keySet(), after.state().entities().keySet());
        assertEquals(before.state().properties().size(), after.state().properties().size());
        assertEquals(2L, before.revision());
        assertTrue(before.knows(MicroWorldStateAdapter.entityId(first),
                MicroWorldStateAdapter.PROPERTY_LAMP));
        assertFalse(before.knows(MicroWorldStateAdapter.entityId(assignment(true, false)),
                MicroWorldStateAdapter.PROPERTY_LAMP),
                "an assignment nobody probed is not known");
    }

    @Test
    @DisplayName("the entity of an assignment is the assignment itself, not the lamp")
    void theEntityIsTheSituation() {
        WorldInput input = assignment(false, true);

        String entityId = MicroWorldStateAdapter.entityId(input);

        assertEquals("assign:" + MicroWorldCodec.describe(input), entityId);
        assertFalse(entityId.contains("lamp"),
                "the entity must be the situation, and the lamp is its property");
    }
}
