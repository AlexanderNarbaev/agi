package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OvernightRunnerTest {

    @Test
    void runresult_record_constructor_and_accessors() {
        OvernightRunner.RunResult r = new OvernightRunner.RunResult(
            true, 2, 1, List.of(), "accepted=2 rejected=1 violations=0");
        assertThat(r.anyCapabilityGain()).isTrue();
        assertThat(r.acceptedMutations()).isEqualTo(2);
        assertThat(r.rejectedMutations()).isEqualTo(1);
        assertThat(r.adversarialViolations()).isEmpty();
        assertThat(r.summary()).contains("accepted=2");
    }

    @Test
    void runresult_with_no_gain_returns_false() {
        OvernightRunner.RunResult r = new OvernightRunner.RunResult(
            false, 0, 0, List.of(), "no gain");
        assertThat(r.anyCapabilityGain()).isFalse();
        assertThat(r.adversarialViolations()).isEmpty();
    }
}
