package io.matrix.consciousness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 273 — Config roundtrip EXP.
 *
 * <p>JSON roundtrip for BrainLoopConfig.Config.
 */
@Tag("exp")
class Exp273ConfigRoundtripTest {

    @Test
    void configJsonRoundtrip() {
        var orig = new BrainLoopConfig.Config(0.4, 0.3, 0.07, 128, 100);
        String json = orig.toJson();
        var reloaded = BrainLoopConfig.Config.fromJson(json);
        System.out.printf("[CONFIG-ROUNDTRIP] %s -> %s -> %s%n",
                orig.arousalBaseline(), json, reloaded.arousalBaseline());
        assertThat(reloaded.arousalBaseline()).isEqualTo(orig.arousalBaseline());
        assertThat(reloaded.maxRise()).isEqualTo(orig.maxRise());
        assertThat(reloaded.decayRate()).isEqualTo(orig.decayRate());
        assertThat(reloaded.bitLength()).isEqualTo(orig.bitLength());
        assertThat(reloaded.maxTurns()).isEqualTo(orig.maxTurns());
    }
}
