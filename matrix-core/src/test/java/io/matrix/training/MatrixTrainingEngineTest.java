package io.matrix.training;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for M.A.T.R.I.X. Training Engine.
 */
class MatrixTrainingEngineTest {

    @Test
    @DisplayName("MatrixTrainingEngine: creates with default state")
    void createsWithDefaultState() {
        MatrixTrainingEngine engine = new MatrixTrainingEngine();

        assertNotNull(engine);
        MatrixTrainingEngine.TrainingStats stats = engine.getStats();

        assertEquals(0, stats.trainingSteps());
        assertEquals(0, stats.verificationAttempts());
        assertEquals(0, stats.successfulVerifications());
        assertEquals(0, stats.trainingDataSize());
    }

    @Test
    @DisplayName("MatrixTrainingEngine: adds training pairs")
    void addsTrainingPairs() {
        MatrixTrainingEngine engine = new MatrixTrainingEngine();

        engine.addTrainingPair("Hello", "Hi there!");
        engine.addTrainingPair("What is AI?", "AI is artificial intelligence.");

        MatrixTrainingEngine.TrainingStats stats = engine.getStats();
        assertEquals(2, stats.trainingDataSize());
    }

    @Test
    @DisplayName("MatrixTrainingEngine: start and stop")
    void startAndStop() {
        MatrixTrainingEngine engine = new MatrixTrainingEngine();

        // Start should not throw
        engine.start();

        // Stop should not throw
        engine.stop();

        // Stats should be accessible
        MatrixTrainingEngine.TrainingStats stats = engine.getStats();
        assertNotNull(stats);
    }

    @Test
    @DisplayName("MatrixTrainingEngine: verification rate calculation")
    void verificationRateCalculation() {
        MatrixTrainingEngine engine = new MatrixTrainingEngine();

        // Add some training pairs
        engine.addTrainingPair("test", "response");

        MatrixTrainingEngine.TrainingStats stats = engine.getStats();

        // Verification rate should be 0 when no verifications
        assertEquals(0.0, stats.verificationRate());
    }

    @Test
    @DisplayName("MatrixTrainingEngine: multiple starts are idempotent")
    void multipleStartsAreIdempotent() {
        MatrixTrainingEngine engine = new MatrixTrainingEngine();

        // Multiple starts should not throw
        engine.start();
        engine.start();
        engine.start();

        // Stop should work
        engine.stop();
    }
}
