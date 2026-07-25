package io.matrix.federated;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PrivacyMechanismTest {

    @Test
    void addNoiseProducesValidOutput() {
        var mechanism = new PrivacyMechanism();
        mechanism.setEpsilon(1.0);
        var original = new LocalUpdate("node1", new boolean[]{true, false, true, true}, 10, 0.1);
        var noisy = mechanism.addNoise(original);
        assertNotNull(noisy);
        assertEquals(4, noisy.update().length);
        assertEquals(10, noisy.dataSize());
    }

    @Test
    void epsilonBoundedCorrectly() {
        var mechanism = new PrivacyMechanism();
        mechanism.setEpsilon(0.001);
        assertEquals(0.01, mechanism.getEpsilon(), 0.001);
        mechanism.setEpsilon(200.0);
        assertEquals(100.0, mechanism.getEpsilon(), 0.001);
        mechanism.setEpsilon(1.5);
        assertEquals(1.5, mechanism.getEpsilon(), 0.001);
    }

    @Test
    void defaultEpsilonIsOne() {
        var mechanism = new PrivacyMechanism();
        assertEquals(1.0, mechanism.getEpsilon(), 0.001);
    }

    @Test
    void emptyUpdateHandlesGracefully() {
        var mechanism = new PrivacyMechanism();
        var original = new LocalUpdate("node1", new boolean[0], 0, 0.0);
        var noisy = mechanism.addNoise(original);
        assertEquals(0, noisy.update().length);
    }
}
