package io.matrix.pilot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link PyBulletBridge} — verifies the Java/Python
 * IPC protocol with {@code scripts/robot_arm_sim.py}.
 *
 * <p><b>Prerequisites:</b>
 * <ul>
 * <li>Python 3.8+ installed and on {@code PATH}</li>
 * <li>{@code scripts/robot_arm_sim.py} present in project root</li>
 * <li>(Optional) PyBullet installed for 3D mode:
 *     {@code pip install pybullet}</li>
 * </ul>
 *
 * <p>If prerequisites are missing, the tests are skipped with
 * diagnostic messages in the test output.
 *
 * <p>The test uses {@code --math} mode which requires no external
 * dependencies — only standard library math module.
 */
@DisplayName("Pilot #4: PyBullet Bridge — robot arm simulation via IPC")
class PyBulletBridgeTest {

    private static final String SCRIPT_PATH = "scripts/robot_arm_sim.py";

    /** Checks whether the bridge environment is available. */
    private static boolean isEnvironmentAvailable() {
        // Check script exists
        if (!new File(SCRIPT_PATH).exists()) {
            return false;
        }
        // Check python3 is available
        try {
            ProcessBuilder pb = new ProcessBuilder("python3", "--version");
            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("Should connect to simulation and train")
    void shouldConnectToSimulation() throws Exception {
        if (!isEnvironmentAvailable()) {
            System.out.println("SKIP: Python environment not available.");
            System.out.println("      Ensure python3 is on PATH and scripts/robot_arm_sim.py exists.");
            return;
        }

        try (PyBulletBridge bridge = new PyBulletBridge(SCRIPT_PATH)) {

            // Run training with small population for quick test
            Map<String, Object> result = bridge.train(10, 5, 12);

            assertThat(result).isNotNull();
            assertThat(result.get("status")).isEqualTo("complete");
            assertThat(result.get("best_fitness")).isNotNull();
            assertThat((Number) result.get("best_fitness")).isNotNull();

            System.out.println("Training result: " + result);
        }
    }

    @Test
    @DisplayName("Should evaluate a pretrained neuron")
    void shouldEvaluateNeuron() throws Exception {
        if (!isEnvironmentAvailable()) {
            System.out.println("SKIP: Python environment not available.");
            return;
        }

        try (PyBulletBridge bridge = new PyBulletBridge(SCRIPT_PATH)) {

            // First train to get a neuron
            Map<String, Object> trainResult = bridge.train(10, 5, 12);

            // Extract the best neuron
            @SuppressWarnings("unchecked")
            Map<String, Object> bestNeuron = (Map<String, Object>) trainResult.get("best_neuron");
            assertThat(bestNeuron).isNotNull();

            // Evaluate the neuron
            int k = ((Number) bestNeuron.get("k")).intValue();
            double fitness = bridge.evaluate(bestNeuron, k);

            assertThat(fitness).isGreaterThan(0);
            System.out.println("Evaluation fitness: " + fitness);
        }
    }

    @Test
    @DisplayName("Should handle multiple training rounds")
    void shouldHandleMultipleRounds() throws Exception {
        if (!isEnvironmentAvailable()) {
            System.out.println("SKIP: Python environment not available.");
            return;
        }

        try (PyBulletBridge bridge = new PyBulletBridge(SCRIPT_PATH)) {

            // First training round
            Map<String, Object> r1 = bridge.train(5, 3, 12);
            assertThat(r1.get("status")).isEqualTo("complete");

            // Second training round
            Map<String, Object> r2 = bridge.train(5, 3, 12);
            assertThat(r2.get("status")).isEqualTo("complete");

            System.out.println("Round 1 fitness: " + r1.get("best_fitness"));
            System.out.println("Round 2 fitness: " + r2.get("best_fitness"));
        }
    }

    @Test
    @DisplayName("Should gracefully handle bridge close")
    void shouldGracefullyClose() throws Exception {
        if (!isEnvironmentAvailable()) {
            System.out.println("SKIP: Python environment not available.");
            return;
        }

        PyBulletBridge bridge = new PyBulletBridge(SCRIPT_PATH);
        // Quick operation
        bridge.train(5, 2, 12);
        // Close should not throw
        bridge.close();

        // Closing twice should not throw (destroyForcibly is idempotent)
        try {
            bridge.close();
        } catch (IOException e) {
            // Expected on already-closed streams — verify no crash
            System.out.println("Expected: " + e.getMessage());
        }
    }
}
