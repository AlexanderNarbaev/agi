package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.AlgorithmRegistry;
import io.matrix.cognitive.microworld.LampEnvironment;
import io.matrix.cognitive.microworld.MicroWorldSession;
import io.matrix.knowledge.KnowledgeWorkbench;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W45.0 — the cognitive store under concurrency.
 *
 * <p>The store holds ONE JDBC connection and the kernel holds mutable regulatory state. Two
 * requests arriving at the same time must not corrupt each other's episodes, must not lose a
 * regulatory update, and must not leave an observation half-linked.</p>
 *
 * <p>The contract the W45 wave settled on is deliberately narrow and it is stated here rather
 * than discovered: <b>one cognitive cycle runs at a time per kernel</b>, and every write to the
 * store is serialised on the store's own monitor. The tests below check that the contract
 * holds, not that the system scales — it does not, and it does not claim to.</p>
 */
class CognitiveConcurrencyTest {

    /** The instant every record in this test carries. */
    private static final String INSTANT = "2026-11-03T00:00:00Z";

    @TempDir
    Path directory;

    private KnowledgeWorkbench workbench;
    private CognitiveStore store;

    @BeforeEach
    void setUp() {
        Path database = directory.resolve("cognitive.sqlite");
        workbench = KnowledgeWorkbench.at(database.toString());
        store = new CognitiveStore(database);
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
        if (workbench != null) {
            workbench.close();
        }
    }

    @Test
    @DisplayName("concurrent observations produce independent episodes, predictions and traces")
    void concurrentObservationsProduceIndependentEpisodes() throws Exception {
        CognitiveKernel kernel = kernel();
        int requests = 8;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<CognitiveOutcome>> futures = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                start.await();
                return kernel.process(CognitiveSignal.structured("sig-" + index,
                        "power=" + (index % 2) + ",switch=" + ((index / 2) % 2),
                        "the test", index + 1L));
            }));
        }
        start.countDown();

        Set<String> episodeIds = ConcurrentHashMap.newKeySet();
        for (Future<CognitiveOutcome> future : futures) {
            CognitiveOutcome outcome = future.get(60, TimeUnit.SECONDS);
            assertThat(outcome.episodeId()).isNotBlank();
            assertThat(episodeIds.add(outcome.episodeId()))
                    .withFailMessage("two requests shared one episode id")
                    .isTrue();
        }
        pool.shutdownNow();

        // Every episode is in the store, and every one of them has its own trace.
        assertThat(store.episodes(CognitiveKernel.WORKSPACE_ID)).hasSize(requests);
        for (String episodeId : episodeIds) {
            assertThat(store.events(episodeId)).isNotEmpty();
        }
    }

    @Test
    @DisplayName("concurrent reads during a write see a consistent store")
    void concurrentReadsDuringWritesAreConsistent() throws Exception {
        CognitiveKernel kernel = kernel();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();

        Future<?> writer = pool.submit(() -> {
            start.await();
            for (int i = 0; i < 8; i++) {
                kernel.process(CognitiveSignal.structured("sig-w" + i,
                        "power=" + (i % 2) + ",switch=" + ((i / 2) % 2), "the test", i + 1L));
            }
            return null;
        });
        List<Future<?>> readers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            readers.add(pool.submit(() -> {
                start.await();
                for (int j = 0; j < 40; j++) {
                    // The reads must not fail, and they must not see a half-written episode.
                    List<String> episodes = store.episodes(CognitiveKernel.WORKSPACE_ID);
                    assertThat(episodes).isNotNull();
                    for (String episodeId : episodes) {
                        store.events(episodeId);
                    }
                    reads.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        writer.get(120, TimeUnit.SECONDS);
        for (Future<?> reader : readers) {
            reader.get(120, TimeUnit.SECONDS);
        }
        pool.shutdownNow();
        assertThat(reads.get()).isPositive();
    }

    @Test
    @DisplayName("the episode sequence never hands out the same number twice")
    void theEpisodeSequenceIsUniqueUnderConcurrency() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return store.nextSequence("signal");
            }));
        }
        start.countDown();
        Set<Long> numbers = ConcurrentHashMap.newKeySet();
        for (Future<Long> future : futures) {
            numbers.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        assertThat(numbers).hasSize(32);
    }

    @Test
    @DisplayName("the regulatory version advances once per write, with no lost update")
    void theRegulatoryVersionAdvancesWithoutLostUpdates() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return store.recordRegulatoryState(CognitiveKernel.WORKSPACE_ID,
                        io.matrix.cognitive.neuromodulation.RegulatoryState.neutral(), INSTANT,
                        List.of());
            }));
        }
        start.countDown();
        long highest = 0;
        for (Future<Long> future : futures) {
            highest = Math.max(highest, future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        assertThat(store.regulatoryVersion(CognitiveKernel.WORKSPACE_ID)).isEqualTo(highest);
        assertThat(highest).isEqualTo(16);
    }

    @Test
    @DisplayName("the epistemic records of an observation are linked, never half-linked")
    void theEpistemicRecordsAreNeverHalfLinked() throws Exception {
        CognitiveKernel kernel = kernel();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                start.await();
                return kernel.process(CognitiveSignal.structured("sig-e" + index,
                        "power=" + (index % 2) + ",switch=" + ((index / 2) % 2),
                        "the test", index + 1L));
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(120, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        // Every case has its source, and every evidence points at a case that exists.
        io.matrix.knowledge.KnowledgeStore knowledge = workbench.storeForValidation();
        List<io.matrix.knowledge.Case> cases = knowledge.cases(CognitiveKernel.WORKSPACE_ID);
        for (io.matrix.knowledge.Case observation : cases) {
            assertThat(observation.sourceId()).isNotBlank();
            assertThat(knowledge.sources(CognitiveKernel.WORKSPACE_ID))
                    .extracting(io.matrix.knowledge.Source::id)
                    .contains(observation.sourceId());
        }
        // And every evidence points at a case that exists.
        for (io.matrix.knowledge.Evidence evidence : knowledge.evidence(CognitiveKernel.WORKSPACE_ID)) {
            String caseId = knowledge.caseIdForEvidence(evidence.id());
            assertThat(caseId).isNotBlank();
            assertThat(cases).extracting(io.matrix.knowledge.Case::id).contains(caseId);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private CognitiveKernel kernel() {
        LampEnvironment environment = new LampEnvironment();
        return new CognitiveKernel(workbench, store,
                new MicroWorldSession.DefaultFactory(environment),
                environment,
                new KernelSelector(new AlgorithmRegistry()
                        .register(new MicroWorldReferenceMethod())
                        .register(new MicroWorldSettledModelMethod())),
                new KernelRegulator(),
                () -> INSTANT);
    }
}
