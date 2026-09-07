package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 234 — BrainLoopStateValidator unit tests. */
class BrainLoopStateValidatorTest {

    @Test
    void freshServiceValidates() {
        var svc = new BrainLoopService();
        var r = BrainLoopStateValidator.validate(svc);
        assertThat(r.valid()).isTrue();
    }

    @Test
    void afterCyclesStillValid() {
        var svc = new BrainLoopService();
        for (int i = 0; i < 50; i++) svc.cycle("X-" + i);
        var r = BrainLoopStateValidator.validate(svc);
        assertThat(r.valid()).isTrue();
    }

    @Test
    void chainIsIntact() {
        var svc = new BrainLoopService();
        for (int i = 0; i < 10; i++) svc.cycle("X-" + i);
        var r = BrainLoopStateValidator.validate(svc);
        assertThat(r.violations()).isEmpty();
    }

    @Test
    void validationResultRecord() {
        var r = new BrainLoopStateValidator.ValidationResult(
                true, 3, java.util.List.of());
        assertThat(r.valid()).isTrue();
        assertThat(r.checkedInvariants()).isEqualTo(3);
        assertThat(r.violations()).isEmpty();
    }

    @Test
    void invalidResultTrack() {
        var r = new BrainLoopStateValidator.ValidationResult(
                false, 3, java.util.List.of("test violation"));
        assertThat(r.valid()).isFalse();
        assertThat(r.violations()).hasSize(1);
    }
}
