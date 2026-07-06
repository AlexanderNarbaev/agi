package io.matrix.ethics;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ProactiveEthicalScannerTest {
    private final ProactiveEthicalScanner scanner = new ProactiveEthicalScanner();

    @Test void shouldDetectLowSafety() {
        var risks = scanner.scan(Map.of("SAFETY", 0.1, "CURIOSITY", 0.5));
        assertThat(risks).anyMatch(r -> r.contains("Safety driver critically low"));
    }
    @Test void shouldDetectDangerousExploration() {
        var risks = scanner.scan(Map.of("SAFETY", 0.3, "CURIOSITY", 0.9));
        assertThat(risks).anyMatch(r -> r.contains("Dangerous exploration"));
    }
    @Test void shouldDetectHighEntropy() {
        var risks = scanner.scan(Map.of("ENTROPY", 0.95));
        assertThat(risks).anyMatch(r -> r.contains("Entropy critically high"));
    }
    @Test void shouldReturnEmptyForSafeState() {
        var risks = scanner.scan(Map.of("SAFETY", 0.9, "CURIOSITY", 0.3));
        assertThat(risks).isEmpty();
    }
    @Test void shouldReturnEmptyForNull() {
        assertThat(scanner.scan(null)).isEmpty();
    }
    @Test void shouldAssessMutationRisk() {
        assertThat(scanner.scanMutations(0, 0)).isEqualTo("OK");
        assertThat(scanner.scanMutations(100, 2)).isEqualTo("OK");
        assertThat(scanner.scanMutations(100, 15)).contains("HIGH_RISK");
    }
}
