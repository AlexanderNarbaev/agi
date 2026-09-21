package io.matrix.consciousness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 252 — CycleSignature EXP.
 *
 * <p>100 cycles, 100 unique signatures.
 */
@Tag("exp")
class Exp252CycleSignatureTest {

    @Test
    void hundredUniqueSignatures() {
        var svc = new BrainLoopService();
        Set<String> signatures = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            svc.cycle("Q-" + i);
            var sig = CycleSignature.compute(i, "Q-" + i, svc.trace());
            signatures.add(sig.cycleId() + ":" + sig.combinedHex());
        }
        System.out.printf("[SIG-100] unique=%d (should be 100)%n", signatures.size());
        assertThat(signatures).hasSize(100);
    }
}
