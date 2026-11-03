package io.matrix.cognitive;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W45.0 — the durable registry of idempotency keys.
 *
 * <p>The registry is the part of the service that answers one question: is this delivery a NEW
 * request, a REPLAY of an earlier one, or a CONFLICT? The answer must be the same before and
 * after a restart, because the mapping lives in the store.</p>
 */
class IdempotencyRegistryTest {

    @TempDir
    Path directory;

    private CognitiveStore store;
    private IdempotencyRegistry registry;

    @BeforeEach
    void setUp() {
        store = new CognitiveStore(directory.resolve("cognitive.sqlite"));
        registry = new IdempotencyRegistry(store, () -> "2026-11-03T00:00:00Z");
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    @Test
    @DisplayName("the first delivery of a key claims it")
    void theFirstDeliveryOfAKeyClaimsIt() {
        String fingerprint = IdempotencyRegistry.fingerprintOf("power=1,switch=0");

        IdempotencyRegistry.Claim claim = registry.claim("delivery-1", fingerprint);

        assertThat(claim.kind()).isEqualTo(IdempotencyRegistry.Kind.ACQUIRED);
    }

    @Test
    @DisplayName("a key that is claimed and not yet finished is in progress, not free")
    void aKeyThatIsClaimedIsInProgress() {
        String fingerprint = IdempotencyRegistry.fingerprintOf("power=1,switch=0");
        registry.claim("delivery-1", fingerprint);

        IdempotencyRegistry.Claim claim = registry.claim("delivery-1", fingerprint);

        assertThat(claim.kind()).isEqualTo(IdempotencyRegistry.Kind.IN_PROGRESS);
    }

    @Test
    @DisplayName("a finished key replays its outcome")
    void aFinishedKeyReplaysItsOutcome() {
        String fingerprint = IdempotencyRegistry.fingerprintOf("power=1,switch=0");
        registry.claim("delivery-1", fingerprint);
        registry.complete("delivery-1", "ep-1", "sig-1", outcome());

        IdempotencyRegistry.Claim claim = registry.claim("delivery-1", fingerprint);

        assertThat(claim.kind()).isEqualTo(IdempotencyRegistry.Kind.REPLAY);
        assertThat(claim.episodeId()).isEqualTo("ep-1");
        assertThat(claim.outcome().episodeId()).isEqualTo("ep-1");
        assertThat(claim.outcome().answer()).isEqualTo("lamp=true.");
    }

    @Test
    @DisplayName("one key with two payloads is a conflict")
    void oneKeyWithTwoPayloadsIsAConflict() {
        String fingerprint = IdempotencyRegistry.fingerprintOf("power=1,switch=0");
        registry.claim("delivery-1", fingerprint);
        registry.complete("delivery-1", "ep-1", "sig-1", outcome());

        IdempotencyRegistry.Claim claim =
                registry.claim("delivery-1", IdempotencyRegistry.fingerprintOf("power=0,switch=1"));

        assertThat(claim.kind()).isEqualTo(IdempotencyRegistry.Kind.CONFLICT);
    }

    @Test
    @DisplayName("a released key can be used again")
    void aReleasedKeyCanBeUsedAgain() {
        String fingerprint = IdempotencyRegistry.fingerprintOf("power=1,switch=0");
        registry.claim("delivery-1", fingerprint);
        registry.release("delivery-1");

        IdempotencyRegistry.Claim claim = registry.claim("delivery-1", fingerprint);

        assertThat(claim.kind()).isEqualTo(IdempotencyRegistry.Kind.ACQUIRED);
    }

    @Test
    @DisplayName("the mapping of a key survives a restart")
    void theMappingOfAKeySurvivesARestart() {
        String fingerprint = IdempotencyRegistry.fingerprintOf("power=1,switch=0");
        registry.claim("delivery-1", fingerprint);
        registry.complete("delivery-1", "ep-1", "sig-1", outcome());

        store.close();
        store = new CognitiveStore(directory.resolve("cognitive.sqlite"));
        registry = new IdempotencyRegistry(store, () -> "2026-11-03T00:00:00Z");

        IdempotencyRegistry.Claim claim = registry.claim("delivery-1", fingerprint);

        assertThat(claim.kind()).isEqualTo(IdempotencyRegistry.Kind.REPLAY);
        assertThat(claim.outcome().episodeId()).isEqualTo("ep-1");
    }

    @Test
    @DisplayName("the fingerprint of a payload is stable, and two payloads do not share one")
    void theFingerprintOfAPayloadIsStable() {
        assertThat(IdempotencyRegistry.fingerprintOf("power=1,switch=0"))
                .isEqualTo(IdempotencyRegistry.fingerprintOf("power=1,switch=0"));
        assertThat(IdempotencyRegistry.fingerprintOf("power=1,switch=0"))
                .isNotEqualTo(IdempotencyRegistry.fingerprintOf("power=0,switch=1"));
        assertThat(IdempotencyRegistry.fingerprintOf("power=1,switch=0"))
                .isNotEqualTo(IdempotencyRegistry.fingerprintOf("power=1,switch=1"));
    }

    private static CognitiveOutcome outcome() {
        Percept percept = new Percept("sig-1", CognitiveSignal.Channel.STRUCTURED,
                "power=1,switch=0", 1.0, false, List.of(), List.of());
        Prediction prediction = new Prediction("prd-ep-1", "microworld", "ep-1", "lamp@power=1",
                "true", "surviving-hypotheses", "v1", 1.0, false, List.of(), "2026-11-03T00:00:00Z");
        LearningTrace trace = LearningTrace.builder("ep-1", "ep-1")
                .step("PERCEIVE", "ChannelClassifier", "channel=STRUCTURED")
                .step("ANSWER", "CognitiveKernel", "lamp=true.")
                .build();
        return new CognitiveOutcome("ep-1", CognitiveOutcome.Status.COMPLETED, "lamp=true.",
                percept, prediction, null, null, trace, List.of());
    }
}
