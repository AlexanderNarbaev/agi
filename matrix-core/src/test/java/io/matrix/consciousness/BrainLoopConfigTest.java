package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 210 — BrainLoopConfig unit tests. */
class BrainLoopConfigTest {

    @Test
    void defaultsAreConservative() {
        BrainLoopConfig.Config defaults = BrainLoopConfig.Config.defaults();
        assertThat(defaults.arousalBaseline()).isEqualTo(ArousalDynamics.BASELINE);
        assertThat(defaults.maxRise()).isBetween(0.0, 1.0);
        assertThat(defaults.decayRate()).isBetween(0.0, 1.0);
        assertThat(defaults.bitLength()).isEqualTo(256);
    }

    @Test
    void jsonRoundtrip() {
        var orig = new BrainLoopConfig.Config(0.5, 0.4, 0.05, 256, 32);
        String json = orig.toJson();
        var reloaded = BrainLoopConfig.Config.fromJson(json);
        assertThat(reloaded.arousalBaseline()).isEqualTo(orig.arousalBaseline());
        assertThat(reloaded.maxRise()).isEqualTo(orig.maxRise());
        assertThat(reloaded.decayRate()).isEqualTo(orig.decayRate());
        assertThat(reloaded.bitLength()).isEqualTo(orig.bitLength());
        assertThat(reloaded.maxTurns()).isEqualTo(orig.maxTurns());
    }

    @Test
    void customConfig() {
        var c = new BrainLoopConfig.Config(0.4, 0.3, 0.07, 512, 100);
        assertThat(c.arousalBaseline()).isEqualTo(0.4);
        assertThat(c.bitLength()).isEqualTo(512);
    }

    @Test
    void fromDefaultsReturnsValid() {
        var c = BrainLoopConfig.fromDefaults();
        assertThat(c).isNotNull();
        assertThat(c.bitLength()).isEqualTo(256);
    }
}
