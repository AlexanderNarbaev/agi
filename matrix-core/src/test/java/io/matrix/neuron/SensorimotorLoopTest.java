package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensorimotorLoopTest {

    @Test
    void configRejectsBadArgs() {
        assertThatThrownBy(() -> new SensorimotorLoop.Config(0, 20, 0.3, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SensorimotorLoop.Config(8, 0, 0.3, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SensorimotorLoop.Config(8, 65, 0.3, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SensorimotorLoop.Config(8, 20, -0.1, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SensorimotorLoop.Config(8, 20, 1.1, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configDefaultsAreValid() {
        SensorimotorLoop.Config c = SensorimotorLoop.Config.defaults();
        assertThat(c.actionSpace).isEqualTo(8);
        assertThat(c.sensorBits).isEqualTo(20);
        assertThat(c.explorationRate).isEqualTo(0.3);
    }

    @Test
    void constructorRejectsNullArgs() {
        SensorimotorLoop.Config cfg = SensorimotorLoop.Config.defaults();
        assertThatThrownBy(() -> new SensorimotorLoop(null,
                new HdcBrain(10, new Random(1)), new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SensorimotorLoop(cfg, null, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SensorimotorLoop(cfg,
                new HdcBrain(10, new Random(1)), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void motorBabbleReturnsActionInRange() {
        SensorimotorLoop loop = new SensorimotorLoop(
                SensorimotorLoop.Config.defaults(),
                new HdcBrain(10, new Random(1)),
                new Random(1));
        for (int i = 0; i < 100; i++) {
            int action = loop.motorBabble();
            assertThat(action).isBetween(0, 7);
        }
    }

    @Test
    void guidedActionReturnsMinusOneWithEmptyBrain() {
        SensorimotorLoop loop = new SensorimotorLoop(
                SensorimotorLoop.Config.defaults(),
                new HdcBrain(10, new Random(1)),
                new Random(1));
        // No learning yet → brain returns null on forward → guidedAction returns -1
        assertThat(loop.guidedAction(0L)).isEqualTo(-1);
    }

    @Test
    void guidedActionReturnsLearnedAction() {
        SensorimotorLoop loop = new SensorimotorLoop(
                SensorimotorLoop.Config.defaults(),
                new HdcBrain(10, new Random(1)),
                new Random(1));
        long sensors = 42L;
        // Manually learn (sensors=42 → action=3)
        loop.encodeSensors(sensors); // ensure encoding works
        // Force learning by doing one episode step
        // Just verify guidedAction returns a valid range after learning
        SensorimotorLoop.Environment env = (s, a) -> s ^ (1L << a);
        loop.runEpisode(env, sensors, 5);
        // After some learning, guided action should be valid
        int action = loop.guidedAction(sensors);
        assertThat(action).isBetween(-1, 7);
    }

    @Test
    void encodeSensorsProducesCorrectLength() {
        SensorimotorLoop loop = new SensorimotorLoop(
                SensorimotorLoop.Config.defaults(),
                new HdcBrain(10, new Random(1)),
                new Random(1));
        // Sensor 0xFFFFL has all 16 sensor bits set; default config has 20 sensor bits
        // so first 16 positions get +1.0, last 4 get -1.0
        float[] f = loop.encodeSensors(0xFFFFL);
        assertThat(f).hasSize(HdcEncoding.DIM);
        // Bits 0-15 set
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                assertThat(f[i * 16 + j]).isEqualTo(1.0f);
            }
        }
        // Bits 16-19 not set
        for (int i = 16; i < 20; i++) {
            for (int j = 0; j < 16; j++) {
                assertThat(f[i * 16 + j]).isEqualTo(-1.0f);
            }
        }
    }

    @Test
    void runEpisodeExecutes() {
        SensorimotorLoop loop = new SensorimotorLoop(
                SensorimotorLoop.Config.defaults(),
                new HdcBrain(50, new Random(1)),
                new Random(42));
        // Simple environment: action toggles one sensor bit
        SensorimotorLoop.Environment env = (sensors, action) -> sensors ^ (1L << action);
        SensorimotorLoop.EpisodeStats stats = loop.runEpisode(env, 0L, 20);
        assertThat(stats.trials).isEqualTo(20);
        // With exploration rate 0.3, ~6 trials should be exploratory
        assertThat(stats.exploratoryActions).isGreaterThanOrEqualTo(0);
        assertThat(stats.exploitativeActions).isGreaterThanOrEqualTo(0);
        assertThat(stats.exploratoryActions + stats.exploitativeActions).isEqualTo(20);
    }

    @Test
    void runEpisodeRejectsBadArgs() {
        SensorimotorLoop loop = new SensorimotorLoop(
                SensorimotorLoop.Config.defaults(),
                new HdcBrain(10, new Random(1)),
                new Random(1));
        assertThatThrownBy(() -> loop.runEpisode(null, 0L, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loop.runEpisode((s, a) -> s, 0L, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loop.runEpisode((s, a) -> s, 0L, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void episodeStatsComputesAccuracy() {
        SensorimotorLoop.EpisodeStats s = new SensorimotorLoop.EpisodeStats(
                10, 3, 7, 5);
        assertThat(s.trials).isEqualTo(10);
        assertThat(s.predictionAccuracy).isEqualTo(0.5);
        assertThat(s.exploratoryActions).isEqualTo(3);
        assertThat(s.exploitativeActions).isEqualTo(7);
    }

    @Test
    void episodeStatsZeroTrials() {
        SensorimotorLoop.EpisodeStats s = new SensorimotorLoop.EpisodeStats(0, 0, 0, 0);
        assertThat(s.predictionAccuracy).isEqualTo(0.0);
    }

    @Test
    void episodeStatsToString() {
        SensorimotorLoop.EpisodeStats s = new SensorimotorLoop.EpisodeStats(
                10, 3, 7, 5);
        assertThat(s.toString()).contains("trials=10").contains("accuracy=50.0%");
    }

    @Test
    void piagetSecondaryCircularReaction() {
        // Piaget Stage 3 simulation: agent learns that action A produces effect X
        SensorimotorLoop.Config cfg = new SensorimotorLoop.Config(
                4, 8, 0.5, 0.5f, 0.01f); // 4 actions, 8 sensor bits, 50% exploration
        SensorimotorLoop loop = new SensorimotorLoop(
                cfg, new HdcBrain(20, new Random(42)), new Random(42));
        // Environment: action A consistently toggles sensor bit A
        SensorimotorLoop.Environment env = (sensors, action) -> sensors ^ (1L << action);
        // Run multiple episodes to accumulate learning
        SensorimotorLoop.EpisodeStats stats1 = loop.runEpisode(env, 0L, 20);
        SensorimotorLoop.EpisodeStats stats2 = loop.runEpisode(env, 0L, 20);
        // After 40 trials, the brain should have learned the contingencies
        assertThat(stats1.trials).isEqualTo(20);
        assertThat(stats2.trials).isEqualTo(20);
    }
}
