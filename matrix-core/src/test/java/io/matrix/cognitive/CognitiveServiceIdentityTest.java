package io.matrix.cognitive;

import io.matrix.knowledge.KnowledgeWorkbench;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W45.0 — the identity of a request, and the idempotency key that names it.
 *
 * <p>W44 derived the signal id from the payload's {@code String.hashCode()} and wrote every
 * record with {@code INSERT OR REPLACE}, so a repeated request destroyed the history of the
 * first one. These tests drive the service the way the endpoint does — a payload in, an outcome
 * out — and then read the durable record back.</p>
 */
class CognitiveServiceIdentityTest {

    private static final String WORKSPACE = CognitiveKernel.WORKSPACE_ID;

    @TempDir
    Path directory;

    private Path database;
    private KnowledgeWorkbench workbench;
    private CognitiveService service;
    private final AtomicInteger tokens = new AtomicInteger();

    @BeforeEach
    void setUp() {
        database = directory.resolve("cognitive.sqlite");
        workbench = KnowledgeWorkbench.at(database.toString());
        service = newService();
        service.workbench = workbench;
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
        if (workbench != null) {
            workbench.close();
        }
    }

    /** A service whose identity source is deterministic, so a test can name every request. */
    private CognitiveService newService() {
        return new CognitiveService(() -> "token-" + tokens.incrementAndGet(),
                () -> "2026-11-03T00:00:00Z");
    }

    @Test
    @DisplayName("two requests with the same payload are two requests, and both survive")
    void twoRequestsWithTheSamePayloadAreTwoRequests() {
        CognitiveOutcome first = service.observe("power=1,switch=0");
        CognitiveOutcome second = service.observe("power=1,switch=0");

        assertThat(first.episodeId()).isNotEqualTo(second.episodeId());
        assertThat(service.store().episodes(WORKSPACE))
                .containsExactlyInAnyOrder(first.episodeId(), second.episodeId());
        // Every episode keeps its own facts: the prediction of the first request was not
        // replaced by the prediction of the second one.
        assertThat(predictionsOf(first.episodeId())).isNotEmpty();
        assertThat(predictionsOf(second.episodeId())).isNotEmpty();
        assertThat(service.store().events(first.episodeId())).isNotEmpty();
        assertThat(service.store().events(second.episodeId())).isNotEmpty();
    }

    /** The predictions that belong to one episode. */
    private java.util.List<Prediction> predictionsOf(String episodeId) {
        return service.store().predictions(WORKSPACE).stream()
                .filter(prediction -> prediction.episodeId().equals(episodeId))
                .toList();
    }

    @Test
    @DisplayName("the signal id is unique per request, and the episode follows from it")
    void theSignalIdIsUniquePerRequest() {
        CognitiveOutcome first = service.observe("power=1,switch=0");
        CognitiveOutcome second = service.observe("power=1,switch=0");

        assertThat(first.episodeId()).isEqualTo("ep-sig-token-1");
        assertThat(second.episodeId()).isEqualTo("ep-sig-token-2");
    }

    @Test
    @DisplayName("the arrival number of the signals is durable and never repeats")
    void theArrivalNumberOfTheSignalsIsDurable() {
        service.observe("power=1,switch=0");
        service.observe("power=0,switch=1");

        assertThat(service.store().nextSequence(EpisodeIdAllocator.SIGNAL_SEQUENCE)).isEqualTo(3L);
    }

    @Test
    @DisplayName("without a key every request is a new request, even for an identical payload")
    void withoutAKeyEveryRequestIsANewRequest() {
        CognitiveOutcome first = service.observe("power=1,switch=0");
        CognitiveOutcome second = service.observe("power=1,switch=0");

        assertThat(second.episodeId()).isNotEqualTo(first.episodeId());
        assertThat(service.store().episodes(WORKSPACE)).hasSize(2);
    }

    @Test
    @DisplayName("a repeated delivery of one key returns the original outcome and does no work")
    void aRepeatedDeliveryOfOneKeyReturnsTheOriginalOutcome() {
        CognitiveOutcome first = service.observe("power=1,switch=0", "delivery-1");
        int episodes = service.store().episodes(WORKSPACE).size();
        int events = service.store().events(first.episodeId()).size();
        int predictions = service.store().predictions(WORKSPACE).size();

        CognitiveOutcome second = service.observe("power=1,switch=0", "delivery-1");

        assertThat(second.episodeId()).isEqualTo(first.episodeId());
        assertThat(second.status()).isEqualTo(first.status());
        assertThat(second.answer()).isEqualTo(first.answer());
        assertThat(second.trace().steps()).hasSameSizeAs(first.trace().steps());
        // No new episode, no new events, no new prediction.
        assertThat(service.store().episodes(WORKSPACE)).hasSize(episodes);
        assertThat(service.store().events(first.episodeId())).hasSize(events);
        assertThat(service.store().predictions(WORKSPACE)).hasSize(predictions);
    }

    @Test
    @DisplayName("one key with two payloads is a conflict, and the first outcome is kept")
    void oneKeyWithTwoPayloadsIsAConflict() {
        CognitiveOutcome first = service.observe("power=1,switch=0", "delivery-1");
        int predictions = service.store().predictions(WORKSPACE).size();

        assertThatThrownBy(() -> service.observe("power=0,switch=1", "delivery-1"))
                .isInstanceOf(CognitiveConflictException.class)
                .hasMessageContaining("delivery-1");

        // The first request keeps its episode, its facts and its events; the second one did
        // NO work at all.
        assertThat(service.store().episodes(WORKSPACE)).containsExactly(first.episodeId());
        assertThat(service.store().predictions(WORKSPACE)).hasSize(predictions);
    }

    @Test
    @DisplayName("the mapping of a key survives a restart")
    void theMappingOfAKeySurvivesARestart() {
        CognitiveOutcome first = service.observe("power=1,switch=0", "delivery-1");

        service.shutdown();
        workbench.close();
        workbench = KnowledgeWorkbench.at(database.toString());
        service = newService();
        service.workbench = workbench;

        CognitiveOutcome replayed = service.observe("power=1,switch=0", "delivery-1");

        assertThat(replayed.episodeId()).isEqualTo(first.episodeId());
        assertThat(replayed.answer()).isEqualTo(first.answer());
        assertThat(service.store().episodes(WORKSPACE)).hasSize(1);
    }

    @Test
    @DisplayName("the outcome that is replayed is the whole outcome, not a shell")
    void theOutcomeThatIsReplayedIsTheWholeOutcome() {
        CognitiveOutcome first = service.observe("power=1,switch=0", "delivery-1");

        CognitiveOutcome replayed = service.observe("power=1,switch=0", "delivery-1");

        assertThat(replayed.prediction()).isEqualTo(first.prediction());
        assertThat(replayed.percept()).isEqualTo(first.percept());
        assertThat(replayed.decision()).isEqualTo(first.decision());
        assertThat(replayed.trace()).isEqualTo(first.trace());
        assertThat(replayed.notes()).isEqualTo(first.notes());
    }
}
