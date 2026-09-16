package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveReActTest {

    @Test
    void nullReturnsEmpty() {
        CognitiveReAct.ReActResult r =
            CognitiveReAct.run(null, 3, null, null, null);
        assertThat(r.steps()).isEmpty();
    }

    @Test
    void zeroIterationsReturnsEmpty() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveReAct.ReActResult r =
            CognitiveReAct.run(p, 0, null, null, null);
        assertThat(r.steps()).isEmpty();
        assertThat(r.iterations()).isEqualTo(0);
    }

    @Test
    void runProducesSteps() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveReAct.ReActResult r =
            CognitiveReAct.run(p, 3, null, null, null);
        assertThat(r.steps().size()).isEqualTo(3);
        assertThat(r.iterations()).isEqualTo(3);
    }

    @Test
    void terminationStopsEarly() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveReAct.TerminationCheck stop = (i, obs) -> i >= 1;
        CognitiveReAct.ReActResult r =
            CognitiveReAct.run(p, 10, null, null, stop);
        assertThat(r.steps().size()).isEqualTo(2); // i=0 and i=1
    }

    @Test
    void customThoughtGen() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveReAct.ThoughtGenerator custom = (state, i, lastObs) ->
            "custom thought at " + i;
        CognitiveReAct.ReActResult r =
            CognitiveReAct.run(p, 2, custom, null, null);
        assertThat(r.steps().get(0).thought()).startsWith("custom");
    }

    @Test
    void customActionExec() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveReAct.ActionExecutor custom = (action, state) ->
            "executed: " + action;
        CognitiveReAct.ReActResult r =
            CognitiveReAct.run(p, 2, null, custom, null);
        assertThat(r.steps().get(0).observation()).startsWith("executed");
    }

    @Test
    void finalAnswerSet() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveReAct.ReActResult r =
            CognitiveReAct.run(p, 2, null, null, null);
        assertThat(r.finalAnswer()).isNotEmpty();
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
