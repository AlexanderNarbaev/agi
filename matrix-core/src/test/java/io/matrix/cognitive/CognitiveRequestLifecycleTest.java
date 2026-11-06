package io.matrix.cognitive;

import io.matrix.knowledge.KnowledgeWorkbench;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W45.1 — the lifecycle of ONE request, and what a terminated process leaves behind.
 *
 * <p>W45.0 had two states and two unclosed failure paths: a process that died with the key
 * claimed left the key claimed FOREVER, and a request that failed after a committed observation
 * DELETED its mapping, so the next delivery started a second episode and committed the same
 * observation twice.</p>
 *
 * <p>These tests drive the two paths directly.</p>
 */
class CognitiveRequestLifecycleTest {

    private static final String WORKSPACE = CognitiveKernel.WORKSPACE_ID;

    /** A pid that cannot exist on this host: the owner of it is gone. */
    private static final long DEAD_PID = 999_999_999L;

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
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        if (workbench != null) {
            workbench.close();
            workbench = null;
        }
    }

    /** A service whose identity source is deterministic. */
    private CognitiveService newService(RequestFailpoint failpoint) {
        CognitiveService created = new CognitiveService(() -> "token-" + tokens.incrementAndGet(),
                () -> "2026-11-04T00:00:00Z", failpoint);
        created.workbench = workbench;
        return created;
    }

    /** A service with no fault injection. */
    private CognitiveService newService() {
        return newService(RequestFailpoint.fromSystemProperties());
    }

    /** The token of an owner that is not running. */
    private OwnershipToken deadOwner() {
        return new OwnershipToken(DEAD_PID, "2026-11-04T00:00:00Z", "dead-owner");
    }

    @Test
    @DisplayName("a request whose owner is gone and that wrote nothing may be executed again")
    void aRequestWhoseOwnerIsGoneAndThatWroteNothingMayBeExecutedAgain() {
        service = newService();
        String fingerprint = IdempotencyRegistry.fingerprintOf("power=1,switch=0");
        CognitiveStore.IdempotencyRecord claimed = service.store().claimIdempotency("delivery-1",
                fingerprint, "req-1", "sig-1", 1L, "ep-sig-1", deadOwner(), "2026-11-04T00:00:00Z");
        assertThat(claimed.state()).isEqualTo(RequestExecutionState.IN_PROGRESS.name());

        IdempotencyRegistry.Claim claim = service.registry().claim("delivery-1", fingerprint);

        assertThat(claim.kind()).isEqualTo(IdempotencyRegistry.Kind.RECOVERED);
        // The SAME logical execution: the episode of the first claim is kept, never a second.
        assertThat(service.store().findIdempotency("delivery-1").orElseThrow()
                .episodeId()).isEqualTo("ep-sig-1");
    }

    @Test
    @DisplayName("a request whose owner is gone after a durable effect is NOT executed again")
    void aRequestWhoseOwnerIsGoneAfterADurableEffectIsNotExecutedAgain() {
        service = newService();
        String fingerprint = IdempotencyRegistry.fingerprintOf("power=1,switch=0");
        service.store().claimIdempotency("delivery-1", fingerprint, "req-1", "sig-1", 1L, "ep-sig-1",
                deadOwner(), "2026-11-04T00:00:00Z");
        service.store().recordCheckpoint("delivery-1", RequestCheckpoint.OBSERVATION_COMMITTED,
                "2026-11-04T00:00:01Z");

        IdempotencyRegistry.Claim claim = service.registry().claim("delivery-1", fingerprint);

        assertThat(claim.kind()).isEqualTo(IdempotencyRegistry.Kind.INDETERMINATE);
        assertThat(claim.checkpoint()).isEqualTo(RequestCheckpoint.OBSERVATION_COMMITTED);
    }

    @Test
    @DisplayName("the recovery of a dead request is recorded in the request history")
    void theRecoveryOfADeadRequestIsRecordedInTheRequestHistory() {
        service = newService();
        String fingerprint = IdempotencyRegistry.fingerprintOf("power=1,switch=0");
        service.store().claimIdempotency("delivery-1", fingerprint, "req-1", "sig-1", 1L, "ep-sig-1",
                deadOwner(), "2026-11-04T00:00:00Z");
        service.store().recordCheckpoint("delivery-1", RequestCheckpoint.OBSERVATION_COMMITTED,
                "2026-11-04T00:00:01Z");

        service.registry().claim("delivery-1", fingerprint);

        List<String> history = service.registry().history("delivery-1");
        assertThat(history).isNotEmpty();
        assertThat(String.join("\n", history))
                .contains(RequestExecutionState.INTERRUPTED_PARTIAL.name());
    }

    @Test
    @DisplayName("a process that lost the request cannot complete it")
    void aProcessThatLostTheRequestCannotCompleteIt() {
        service = newService();
        String fingerprint = IdempotencyRegistry.fingerprintOf("power=1,switch=0");
        service.store().claimIdempotency("delivery-1", fingerprint, "req-1", "sig-1", 1L, "ep-sig-1",
                deadOwner(), "2026-11-04T00:00:00Z");

        // A new owner takes the request over.
        IdempotencyRegistry.Claim claim = service.registry().claim("delivery-1", fingerprint);
        assertThat(claim.kind()).isEqualTo(IdempotencyRegistry.Kind.RECOVERED);

        // The OLD owner tries to complete the request: the fence refuses it.
        assertThatThrownBy(() -> service.store().completeIdempotency("delivery-1", "ep-sig-1",
                "sig-1", "{}", "2026-11-04T00:00:02Z", deadOwner()))
                .isInstanceOf(CognitiveConflictException.class)
                .hasMessageContaining("no longer owned");
    }

    @Test
    @DisplayName("a failure after the observation was committed does not duplicate the observation")
    void aFailureAfterTheObservationWasCommittedDoesNotDuplicateTheObservation() {
        service = newService(RequestFailpoint.throwing(RequestCheckpoint.OBSERVATION_COMMITTED));

        CognitiveOutcome failed = service.observe("power=1,switch=0", "delivery-1");

        assertThat(failed.status()).isEqualTo(CognitiveOutcome.Status.FAILED);
        int casesAfterTheFailure = workbench.storeForValidation().cases(WORKSPACE).size();
        assertThat(casesAfterTheFailure).isPositive();

        // The retry of the same key: the outcome of the failed request is replayed and no
        // work is done. The observation is NOT committed a second time.
        CognitiveOutcome replay = service.observe("power=1,switch=0", "delivery-1");

        assertThat(replay.status()).isEqualTo(CognitiveOutcome.Status.FAILED);
        assertThat(replay.episodeId()).isEqualTo(failed.episodeId());
        assertThat(workbench.storeForValidation().cases(WORKSPACE)).hasSize(casesAfterTheFailure);
        assertThat(service.store().episodes(WORKSPACE)).hasSize(1);
    }

    @Test
    @DisplayName("a failed cycle closes its episode instead of leaving it open")
    void aFailedCycleClosesItsEpisodeInsteadOfLeavingItOpen() {
        service = newService(RequestFailpoint.throwing(RequestCheckpoint.PREDICTION_WRITTEN));

        CognitiveOutcome failed = service.observe("power=1,switch=0", "delivery-1");

        assertThat(failed.status()).isEqualTo(CognitiveOutcome.Status.FAILED);
        assertThat(service.store().episodes(WORKSPACE)).hasSize(1);
        // The episode is closed: the store must never say that a cycle is still running.
        assertThat(service.store().events(failed.episodeId()))
                .anyMatch(step -> "FAILURE".equals(step.phase()));
    }

    @Test
    @DisplayName("a timeout is a terminal outcome that a retry replays")
    void aTimeoutIsATerminalOutcomeThatARetryReplays() {
        service = newService();
        // A clock that advances by a second on every read makes the budget expire at the
        // first phase boundary, which is what makes the expiry deterministic.
        java.util.function.LongSupplier stepped = new java.util.function.LongSupplier() {
            private long calls;

            @Override
            public long getAsLong() {
                return ++calls * 1_000_000_000L;
            }
        };
        service.monotonicClock(stepped);

        CognitiveOutcome timedOut = service.observe("power=1,switch=0", "delivery-1");

        assertThat(timedOut.status()).isEqualTo(CognitiveOutcome.Status.RESOURCE_LIMITED);
        // The episode is closed and the terminal outcome is durable.
        assertThat(service.store().episodes(WORKSPACE)).hasSize(1);

        // The retry of the same key replays the terminal outcome and does no work.
        CognitiveOutcome replay = service.observe("power=1,switch=0", "delivery-1");

        assertThat(replay.status()).isEqualTo(CognitiveOutcome.Status.RESOURCE_LIMITED);
        assertThat(replay.episodeId()).isEqualTo(timedOut.episodeId());
        assertThat(service.store().episodes(WORKSPACE)).hasSize(1);
    }

    @Test
    @DisplayName("the recovery closes the episode of a request that a dead process left open")
    void theRecoveryClosesTheEpisodeOfARequestThatADeadProcessLeftOpen() {
        service = newService();
        String fingerprint = IdempotencyRegistry.fingerprintOf("power=1,switch=0");
        service.store().startEpisode("ep-sig-1", WORKSPACE, "a cognitive cycle", "2026-11-04T00:00:00Z");
        service.store().claimIdempotency("delivery-1", fingerprint, "req-1", "sig-1", 1L, "ep-sig-1",
                deadOwner(), "2026-11-04T00:00:00Z");
        service.store().recordCheckpoint("delivery-1", RequestCheckpoint.EPISODE_OPENED,
                "2026-11-04T00:00:01Z");

        List<String> report = service.recoverInterruptedRequests();

        assertThat(report).isNotEmpty();
        assertThat(service.store().events("ep-sig-1"))
                .anyMatch(step -> "INTERRUPTED".equals(step.phase()));
        assertThat(service.store().findIdempotency("delivery-1").orElseThrow().state())
                .isEqualTo(RequestExecutionState.INTERRUPTED_PARTIAL.name());
    }

    @Test
    @DisplayName("the request mapping of a failed request is never deleted")
    void theRequestMappingOfAFailedRequestIsNeverDeleted() {
        service = newService(RequestFailpoint.throwing(RequestCheckpoint.EXPERIMENT_EXECUTED));

        service.observe("power=1,switch=0", "delivery-1");

        assertThat(service.store().findIdempotency("delivery-1")).isPresent();
    }
}
