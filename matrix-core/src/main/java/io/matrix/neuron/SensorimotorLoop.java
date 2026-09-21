package io.matrix.neuron;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * RUN 452 — Sensorimotor loop with motor babble (DESIGN-58 Level 4, Piaget 1954).
 *
 * <p>Closes the sensorimotor loop: agent performs motor actions, observes
 * sensory consequences via HdcBrain, and learns contingencies
 * (action → outcome). Implements Piaget's secondary circular reactions
 * (4-8 month infants): random motor actions → noticed effects → repeated
 * actions to reproduce effects.
 *
 * <h2>Architecture</h2>
 * <pre>
 *   motor babble → motor command (int)
 *      │
 *      ▼
 *   environment transition (sensors_t → sensors_t+1)
 *      │
 *      ▼
 *   HdcBrain.learn(sensors_t, motor_t)  ←  contingency memory
 *      │
 *      ▼
 *   on next sensor pattern: forward → predicted action
 * </pre>
 *
 * <h2>Piaget sensorimotor stages</h2>
 * <ul>
 *   <li>Stage 2 (1-4 mo): Primary circular reactions — repeat own body movements</li>
 *   <li>Stage 3 (4-8 mo): Secondary circular reactions — repeat actions that produce interesting effects (this class)</li>
 *   <li>Stage 4 (8-12 mo): Coordination of secondary schemes — combine actions for goals</li>
 *   <li>Stage 5 (12-18 mo): Tertiary circular reactions — active experimentation with variations</li>
 * </ul>
 *
 * <h2>CONSTITUTION I</h2>
 * Caller supplies RNG. All operations deterministic given inputs.
 */
public final class SensorimotorLoop {

    /** Configuration for the sensorimotor loop. */
    public static final class Config {
        /** Number of possible motor actions. */
        public final int actionSpace;
        /** Number of sensory bits (sensors are long-packed bits). */
        public final int sensorBits;
        /** Probability of random exploration vs exploitation per trial. */
        public final double explorationRate;
        /** HDC learning rate. */
        public final float hdcEta;
        /** HDC decay rate. */
        public final float hdcLambda;

        public Config(int actionSpace, int sensorBits, double explorationRate,
                      float hdcEta, float hdcLambda) {
            if (actionSpace < 1) throw new IllegalArgumentException("actionSpace must be ≥ 1");
            if (sensorBits < 1 || sensorBits > 64) {
                throw new IllegalArgumentException("sensorBits must be in [1, 64]");
            }
            if (explorationRate < 0.0 || explorationRate > 1.0) {
                throw new IllegalArgumentException("explorationRate must be in [0, 1]");
            }
            this.actionSpace = actionSpace;
            this.sensorBits = sensorBits;
            this.explorationRate = explorationRate;
            this.hdcEta = hdcEta;
            this.hdcLambda = hdcLambda;
        }

        public static Config defaults() {
            return new Config(8, 20, 0.3, 0.5f, 0.01f);
        }
    }

    /** Statistics from a sensorimotor episode. */
    public static final class EpisodeStats {
        public final int trials;
        public final int exploratoryActions;
        public final int exploitativeActions;
        public final int successfulPredictions;
        public final double predictionAccuracy;

        public EpisodeStats(int trials, int exploratoryActions, int exploitativeActions,
                            int successfulPredictions) {
            this.trials = trials;
            this.exploratoryActions = exploratoryActions;
            this.exploitativeActions = exploitativeActions;
            this.successfulPredictions = successfulPredictions;
            this.predictionAccuracy = trials > 0
                    ? (double) successfulPredictions / trials : 0.0;
        }

        @Override
        public String toString() {
            return "EpisodeStats{trials=" + trials
                    + ", exploration=" + exploratoryActions
                    + ", exploitation=" + exploitativeActions
                    + ", predictions=" + successfulPredictions
                    + ", accuracy=" + String.format("%.1f%%", predictionAccuracy * 100) + "}";
        }
    }

    private final Config config;
    private final HdcBrain brain;
    private final Random rng;

    /**
     * Create a new sensorimotor loop.
     */
    public SensorimotorLoop(Config config, HdcBrain brain, Random rng) {
        if (config == null) throw new IllegalArgumentException("null config");
        if (brain == null) throw new IllegalArgumentException("null brain");
        if (rng == null) throw new IllegalArgumentException("null rng");
        this.config = config;
        this.brain = brain;
        this.rng = rng;
    }

    /**
     * Run one sensorimotor episode: agent interacts with environment for
     * {@code numTrials} steps, mixing exploration (motor babble) with
     * exploitation (HDC retrieval).
     *
     * @param environment function that maps (current sensors, action) → next sensors
     * @param initialSensors initial sensor state
     * @param numTrials number of trials to run
     */
    public EpisodeStats runEpisode(Environment environment,
                                     long initialSensors, int numTrials) {
        if (environment == null) throw new IllegalArgumentException("null environment");
        if (numTrials < 1) throw new IllegalArgumentException("numTrials must be ≥ 1");
        int exploratory = 0;
        int exploitative = 0;
        int successful = 0;
        long sensors = initialSensors;
        int previousAction = -1;

        for (int t = 0; t < numTrials; t++) {
            // Decide action: exploration or exploitation
            int action;
            boolean isExploratory;
            if (rng.nextDouble() < config.explorationRate) {
                // Motor babble: random action
                action = rng.nextInt(config.actionSpace);
                isExploratory = true;
            } else {
                // Exploit: query HDC brain for predicted action
                float[] features = encodeSensors(sensors);
                HdcBrain.Recall hit = brain.forward(features);
                if (hit != null && hit.similarity > 0.0) {
                    int predicted = parseActionLabel(hit.label);
                    if (predicted >= 0 && predicted < config.actionSpace) {
                        action = predicted;
                        isExploratory = false;
                    } else {
                        action = rng.nextInt(config.actionSpace);
                        isExploratory = true;
                    }
                } else {
                    action = rng.nextInt(config.actionSpace);
                    isExploratory = true;
                }
            }

            // Environment transition
            long nextSensors = environment.transition(sensors, action);

            // Learn contingency: sensors → action
            float[] features = encodeSensors(sensors);
            brain.learn(features, actionLabel(action), config.hdcEta, config.hdcLambda);

            // Track success: if previous action led to non-trivial sensor change
            if (previousAction >= 0 && sensors != nextSensors) {
                successful++;
            }

            if (isExploratory) exploratory++;
            else exploitative++;

            sensors = nextSensors;
            previousAction = action;
        }

        return new EpisodeStats(numTrials, exploratory, exploitative, successful);
    }

    /**
     * Motor babble only: pure random action selection for exploration
     * without HDC memory. Useful as a baseline.
     */
    public int motorBabble() {
        return rng.nextInt(config.actionSpace);
    }

    /**
     * HDC-guided action: query brain for predicted action given sensors.
     * Returns -1 if no confident prediction.
     */
    public int guidedAction(long sensors) {
        float[] features = encodeSensors(sensors);
        HdcBrain.Recall hit = brain.forward(features);
        if (hit != null && hit.similarity > 0.0) {
            return parseActionLabel(hit.label);
        }
        return -1;
    }

    /**
     * Encode sensor long as 1024-float bipolar feature vector.
     */
    public float[] encodeSensors(long sensors) {
        float[] features = new float[HdcEncoding.DIM];
        for (int i = 0; i < config.sensorBits; i++) {
            float value = ((sensors >>> i) & 1L) != 0L ? 1.0f : -1.0f;
            for (int j = 0; j < 16; j++) {
                features[i * 16 + j] = value;
            }
        }
        return features;
    }

    private static String actionLabel(int action) {
        return "act-" + action;
    }

    private static int parseActionLabel(String label) {
        if (label == null || !label.startsWith("act-")) return -1;
        try {
            return Integer.parseInt(label.substring(4));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Environment interface: deterministic state transition function. */
    public interface Environment {
        /**
         * Map (current sensors, action) → next sensors.
         * Implementations must be pure functions.
         */
        long transition(long sensors, int action);
    }
}
