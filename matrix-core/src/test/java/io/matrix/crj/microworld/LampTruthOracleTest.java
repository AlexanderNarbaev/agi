package io.matrix.crj.microworld;

import io.matrix.cognitive.microworld.LampEnvironment;
import io.matrix.cognitive.microworld.WorldInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lamp truth oracle's own tests, and the binding instrument cross-check.
 *
 * <p>The cross-check is the part that matters: the oracle and the world are compared on
 * <b>all four</b> assignments, and the result is recorded as
 * {@code instrument_crosscheck: {assignments: 4, agreements: 4}}. A disagreement is not scored
 * and not re-run to a nicer number — the run is <b>VOID</b> with the reason
 * {@code INSTRUMENT_MISMATCH}, and the mismatch is a finding in its own right.</p>
 *
 * <p>This test is the ONE place where the oracle and the world meet. The oracle itself does not
 * import the world, does not name its type and cannot ask it anything — the world is read here,
 * as data, and handed to the oracle.</p>
 */
class LampTruthOracleTest {

    @Test
    @DisplayName("the oracle's table is the conjunction the specification describes")
    void theTableIsTheConjunction() {
        LampTruthOracle oracle = new LampTruthOracle();

        assertThat(oracle.lit(false, false)).as("power=0, switch=0").isFalse();
        assertThat(oracle.lit(false, true)).as("power=0, switch=1").isFalse();
        assertThat(oracle.lit(true, false)).as("power=1, switch=0").isFalse();
        assertThat(oracle.lit(true, true)).as("power=1, switch=1").isTrue();
        assertThat(oracle.rows()).hasSize(4);
    }

    @Test
    @DisplayName("the cross-check against the world agrees on all four assignments")
    void theCrosscheckAgrees() {
        LampTruthOracle oracle = new LampTruthOracle();
        LampEnvironment world = new LampEnvironment();

        Map<String, Boolean> observed = new LinkedHashMap<>();
        for (LampTruthOracle.Row row : oracle.rows()) {
            WorldInput input = WorldInput.of("1".equals(row.power()), "1".equals(row.toggle()));
            observed.put(row.assignment(), world.probe(input).lamp());
        }

        LampTruthOracle.Crosscheck crosscheck = oracle.crosscheck(observed);

        assertThat(crosscheck.assignments()).isEqualTo(4);
        assertThat(crosscheck.agreements()).isEqualTo(4);
        assertThat(crosscheck.disagreements()).isEmpty();
        assertThat(crosscheck.agrees()).isTrue();
        assertThat(crosscheck.voidReason()).isEmpty();
        assertThat(crosscheck.asRecord())
                .isEqualTo("instrument_crosscheck: {assignments: 4, agreements: 4}");
    }

    @Test
    @DisplayName("a disagreement makes the run VOID with INSTRUMENT_MISMATCH")
    void theDisagreementVoidsTheRun() {
        LampTruthOracle oracle = new LampTruthOracle();

        Map<String, Boolean> observed = new LinkedHashMap<>();
        observed.put("power=0,switch=0", false);
        observed.put("power=0,switch=1", false);
        observed.put("power=1,switch=0", false);
        // the world "says" the lamp is lit without the switch — the oracle disagrees
        observed.put("power=1,switch=1", false);

        LampTruthOracle.Crosscheck crosscheck = oracle.crosscheck(observed);

        assertThat(crosscheck.agrees()).isFalse();
        assertThat(crosscheck.voidReason()).isEqualTo(LampTruthOracle.INSTRUMENT_MISMATCH);
        assertThat(crosscheck.disagreements()).hasSize(1);
        assertThat(crosscheck.disagreements().get(0)).contains("power=1,switch=1");
    }

    @Test
    @DisplayName("an assignment the world did not answer is a disagreement, not a pass")
    void theMissingAssignmentIsADisagreement() {
        LampTruthOracle oracle = new LampTruthOracle();

        LampTruthOracle.Crosscheck crosscheck = oracle.crosscheck(Map.of(
                "power=0,switch=0", false,
                "power=0,switch=1", false,
                "power=1,switch=0", false));

        assertThat(crosscheck.assignments()).isEqualTo(4);
        assertThat(crosscheck.agreements()).isEqualTo(3);
        assertThat(crosscheck.agrees()).isFalse();
        assertThat(crosscheck.voidReason()).isEqualTo(LampTruthOracle.INSTRUMENT_MISMATCH);
    }
}
