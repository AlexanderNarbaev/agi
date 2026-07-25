package io.matrix.federated;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Random;

/**
 * Differential privacy mechanism for federated learning.
 */
@ApplicationScoped
public class PrivacyMechanism {

    private final Random random = new Random();
    private double epsilon = 1.0;

    /**
     * Add differential privacy noise to update.
     */
    public LocalUpdate addNoise(LocalUpdate update) {
        boolean[] original = update.update();
        boolean[] noisy = new boolean[original.length];

        double scale = 1.0 / epsilon;

        for (int i = 0; i < original.length; i++) {
            if (original[i]) {
                // Flip bit with probability proportional to noise
                double noise = laplacianNoise(scale);
                noisy[i] = Math.random() > Math.abs(noise);
            } else {
                noisy[i] = original[i];
            }
        }

        return new LocalUpdate(
                update.nodeId(),
                noisy,
                update.dataSize(),
                update.loss()
        );
    }

    /**
     * Set privacy budget (epsilon).
     */
    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
    }

    /**
     * Get current epsilon.
     */
    public double getEpsilon() {
        return epsilon;
    }

    private double laplacianNoise(double scale) {
        double u = random.nextDouble() - 0.5;
        return -scale * Math.signum(u) * Math.log(1 - 2 * Math.abs(u));
    }
}
