package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.AlgorithmRegistry;
import io.matrix.cognitive.distributed.RemoteExecutor;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W53 — the remote task belongs to the same cognitive episode as the local work.
 *
 * <p><b>What these tests hold.</b> When the capability the selector chose is one another
 * process can serve, the cycle delegates it, and the episode's own trace shows the four
 * transitions the mandate names: the dispatch, the receipt, the validation and the epistemic
 * evaluation of what came back. The delegation is bounded — a capability the peer does not
 * serve is not delegated, and the local computation continues on its own terms.</p>
 *
 * <p><b>What they do not claim.</b> The remote answer is never promoted to a fact. It enters
 * the cycle as a note beside the trace, and the local computation still decides the cycle's
 * own outcome.</p>
 */
class CognitiveKernelRemoteTest {

    @TempDir
    Path directory;

    private KnowledgeWorkbench workbench;
    private CognitiveStore store;
    private final AtomicInteger clock = new AtomicInteger();

    /** The delegations the cycle made. */
    private final List<String> delegated = new ArrayList<>();

    /** Whether the peer serves the capability at all. */
    private boolean peerServes = true;

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

    /** The port: it records what it was asked and answers with a report, not a fact. */
    private final class RecordingExecutor implements RemoteExecutor {

        @Override
        public boolean serves(String capabilityId) {
            return peerServes;
        }

        @Override
        public RemoteOutcome execute(String capabilityId, String input, String expectation,
                                     String episodeId) {
            delegated.add(capabilityId + "@" + episodeId);
            return new RemoteOutcome("req-" + episodeId, "chain-b", capabilityId,
                    "COMPLETED", "UNRESOLVED",
                    "the peer's confidence rests on MEASURED, which is not independent evidence",
                    "the lamp is lit");
        }
    }

    /** The kernel under test, with the remote port attached. */
    private CognitiveKernel kernel() {
        LampEnvironment environment = new LampEnvironment();
        CognitiveKernel kernel = new CognitiveKernel(workbench, store,
                new MicroWorldSession.DefaultFactory(environment),
                environment,
                new KernelSelector(new AlgorithmRegistry()
                        .register(new MicroWorldReferenceMethod())
                        .register(new MicroWorldSettledModelMethod())),
                new KernelRegulator(),
                () -> "2026-11-09T00:00:0" + (clock.incrementAndGet() % 10) + "Z");
        return kernel.withRemoteExecutor(new RecordingExecutor());
    }

    /** One cycle, with a signal the kernel accepts. */
    private CognitiveOutcome cycle(CognitiveKernel kernel, String signalId) {
        return kernel.process(CognitiveSignal.structured(signalId,
                "{\"power\":true,\"switch\":false}", "the test",
                clock.incrementAndGet()));
    }

    @Test
    @DisplayName("a delegated capability is delegated, and the episode's trace says so")
    void aDelegatedCapabilityIsRecordedInTheTrace() {
        CognitiveKernel kernel = kernel();

        CognitiveOutcome outcome = cycle(kernel, "sig-1");

        assertThat(delegated).isNotEmpty();
        LearningTrace trace = outcome.trace();
        assertThat(trace).isNotNull();
        assertThat(trace.steps().stream().map(LearningTrace.Step::phase))
                .contains("DISPATCH_REMOTE", "RECEIVE", "VALIDATE", "EVALUATE_EVIDENCE");
        assertThat(trace.phase("RECEIVE")).isNotEmpty();
        assertThat(trace.phase("RECEIVE").get(0).detail())
                .contains("chain-b");
    }

    @Test
    @DisplayName("a capability the peer does not serve is not delegated")
    void aLocalCapabilityIsNotDelegated() {
        peerServes = false;
        CognitiveKernel kernel = kernel();

        CognitiveOutcome outcome = cycle(kernel, "sig-2");

        assertThat(delegated).isEmpty();
        assertThat(outcome.trace().steps().stream().map(LearningTrace.Step::phase))
                .doesNotContain("DISPATCH_REMOTE");
    }

    @Test
    @DisplayName("the peer's answer is a note, never a settled fact")
    void thePeersAnswerIsANote() {
        CognitiveKernel kernel = kernel();

        CognitiveOutcome outcome = cycle(kernel, "sig-3");

        assertThat(outcome.notes()).isNotNull();
        assertThat(outcome.notes().stream()
                .anyMatch(note -> note.contains("a remote execution contributed")))
                .isTrue();
        assertThat(outcome.notes().stream()
                .anyMatch(note -> note.contains("not a settled fact")))
                .isTrue();
    }
}
