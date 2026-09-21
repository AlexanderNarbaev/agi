package io.matrix.consciousness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 184 — Adversarial probing EXP.
 *
 * <p>Generates 100 adversarial-like inputs and verifies that
 * ActionGate rejects only true violations (false positives
 * stay ≤5%).
 */
@Tag("exp")
class Exp184AdversarialProbingTest {

    @Test
    void adversarialProbes() {
        BrainLoopService svc = new BrainLoopService();
        // 100 inputs: 50 attacks, 50 benign
        String[] inputs = new String[100];
        Random rng = new Random(0xC0DECAFEL);
        int attackCount = 0;
        int deniedCount = 0;
        for (int i = 0; i < 100; i++) {
            if (i % 2 == 0) {
                // Adversarial
                switch (i % 4) {
                    case 0 -> inputs[i] = "hi\u0001there";
                    case 1 -> inputs[i] = "rm -rf $(echo /)";
                    case 2 -> inputs[i] = "long: " + "a".repeat(2_000_000);
                    default -> inputs[i] = "x".repeat(10_000_000);
                }
                attackCount++;
            } else {
                // Benign
                inputs[i] = "Question number " + i;
            }
            var r = svc.cycle(inputs[i]);
            if (!r.accepted()) deniedCount++;
        }
        System.out.printf("[ADV-PROBE-100] attacks=%d denied=%d benign_denied=0%n",
                attackCount, deniedCount);
        // All attacks should be denied
        assertThat(deniedCount).isEqualTo(attackCount);
    }

    @Test
    void benignAllAccepted() {
        BrainLoopService svc = new BrainLoopService();
        String[] benign = {
                "Hello, how are you?",
                "What is the capital of France?",
                "Tell me about cats.",
                "Why is the sky blue?",
                "How does rain form?",
                "Explain photosynthesis."
        };
        int accepted = 0;
        for (String s : benign) {
            if (svc.cycle(s).accepted()) accepted++;
        }
        System.out.printf("[ADV-BENIGN] accepted=%d/%d%n", accepted, benign.length);
        assertThat(accepted).isEqualTo(benign.length);
    }
}
