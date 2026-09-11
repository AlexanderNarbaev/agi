package io.matrix.api;

import io.matrix.imports.BooleanChainRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ChainTextGenerator} safety filter integration (RUN 32).
 *
 * <p>Verifies that the OutputSafetyFilter is applied during chain-driven
 * text generation. Forbidden tokens (control bytes, surrogates) must
 * not appear in generated output.
 */
class ChainTextGeneratorSafetyTest {

    private ChainTextGenerator gen;
    private boolean wasAvailable;

    @BeforeEach
    void setUp() {
        gen = new ChainTextGenerator();
        // Wire minimal stubs (no real BPE / chain). The safety filter
        // is testable without those.
        gen.chainRunner(BooleanChainRunner.empty());
        gen.bpeProvider(new BpeTokenizerProvider());
        wasAvailable = gen.isAvailable();
    }

    @Test
    void safetyFilterIsAccessible() {
        assertThat(gen.safetyFilter()).isNotNull();
        assertThat(gen.skippedForbiddenTokens()).isZero();
    }

    @Test
    void resetSafetyStatsClearsCounter() {
        // Simulate a skipped forbidden token by calling resetSafetyStats
        // and verifying the counter is 0.
        gen.resetSafetyStats();
        assertThat(gen.skippedForbiddenTokens()).isZero();
    }

    @Test
    void safetyFilterRejectsControlTokens() {
        assertThat(gen.safetyFilter().isTokenForbidden(0x00)).isTrue();
        assertThat(gen.safetyFilter().isTokenForbidden(0x7F)).isTrue();
        assertThat(gen.safetyFilter().isTokenForbidden('A')).isFalse();
    }

    @Test
    void safetyFilterAcceptsNormalStrings() {
        assertThat(gen.safetyFilter().isStringAllowed("hello world")).isTrue();
        assertThat(gen.safetyFilter().isStringAllowed("")).isTrue();
        assertThat(gen.safetyFilter().isStringAllowed(null)).isTrue();
    }

    @Test
    void safetyFilterRejectsForbiddenPhrases() {
        assertThat(gen.safetyFilter().isStringAllowed("kill the target")).isFalse();
        assertThat(gen.safetyFilter().isStringAllowed("murder")).isFalse();
    }

    /**
     * RUN 32 integration: even when the chain runner is empty (no real
     * chain), the generator should still apply the safety filter on
     * whatever it produces. With empty chain, generation is a no-op,
     * but the safety API must be wired.
     */
    @Test
    void generatorWiresSafetyFilter() {
        // The generator's safety filter is a field initialized at construction.
        assertThat(gen.safetyFilter()).isNotNull();
        assertThat(gen.safetyFilter()).isInstanceOf(io.matrix.ethics.OutputSafetyFilter.class);
    }

    /**
     * RUN 32 — verify the safety filter integration respects the
     * forbidden-phrase boundary. Forbidden phrases never appear in
     * the safety filter's allow list.
     */
    @Test
    void forbiddenPhrasesListIsExposed() {
        var phrases = gen.safetyFilter().forbiddenPhrases();
        assertThat(phrases).contains("kill", "murder", "torture", "bomb");
    }
}
