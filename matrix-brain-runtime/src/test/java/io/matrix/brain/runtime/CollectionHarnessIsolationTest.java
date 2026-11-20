package io.matrix.brain.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * EPISTEMIC-INTEGRITY P0.4 — the previous wave's collection harness must still
 * behave, and must no longer be able to contaminate the learning feed.
 *
 * <p><b>What the harness is.</b> {@link C2ARealEpisodeCollectionTest} is the C2A
 * measurement harness: it runs the REAL conductor ({@link TrueMindCycle}) against the
 * machine's real knowledge store and writes the resulting episodes through the
 * production write path. Before this wave it wrote them into the machine's real
 * episodic log with no provenance, and the learner then trained on the harness's
 * fabricated interleaving.</p>
 *
 * <p><b>Three regressions are asserted here.</b></p>
 * <ol>
 *   <li><b>Opt-in.</b> Without the explicit opt-in flag the harness touches
 *       NOTHING: the machine's real log is byte-identical afterwards. The measured
 *       run is guarded by an assumption, and this test proves the guard works by
 *       invoking it without the flag and checking the log's digest.</li>
 *   <li><b>Sandbox by default.</b> The harness's rehearsal path writes into the
 *       module's build directory, never into the machine's real log.</li>
 *   <li><b>Tagged.</b> Every record the harness writes carries
 *       {@link EpisodeRole#EXPERIMENT}, so even a real-log run cannot reach the
 *       learning window: the contamination is impossible by construction, not
 *       merely avoided by not running the harness.</li>
 * </ol>
 */
class CollectionHarnessIsolationTest {

    /** The machine's real episodic log, if this machine has it. */
    private static Path realLog() {
        Path start = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (Path directory = start; directory != null; directory = directory.getParent()) {
            Path candidate = directory.resolve("data/mind/episodic.ndjson");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    private static Path repoRoot() {
        Path start = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (Path directory = start; directory != null; directory = directory.getParent()) {
            if (Files.isDirectory(directory.resolve("matrix-brain-runtime"))
                    && Files.isDirectory(directory.resolve("docs-v2"))) {
                return directory;
            }
        }
        return start;
    }

    @Test
    @DisplayName("without the opt-in flag the harness does not touch the real log")
    void theMeasuredRunIsOptIn() throws Exception {
        Path real = realLog();
        assumeTrue(real != null,
            "this machine has no runtime episodic log: there is nothing the harness could "
                + "contaminate, and the guard is asserted by the harness itself");
        String before = EpisodeQuarantineTool.sha256(real);
        long linesBefore = Files.readAllLines(real).size();

        // The harness's measured run is guarded by assumeTrue: without the flag it
        // aborts before it writes anything.
        boolean aborted = false;
        try {
            new C2ARealEpisodeCollectionTest().collectRealEpisodes();
        } catch (org.opentest4j.TestAbortedException expected) {
            aborted = true;
        }

        assertThat(aborted)
            .as("the measured collection must be opt-in, not a side effect of a build")
            .isTrue();
        assertThat(EpisodeQuarantineTool.sha256(real))
            .as("the real log must be byte-identical").isEqualTo(before);
        assertThat(Files.readAllLines(real).size()).isEqualTo(linesBefore);
    }

    @Test
    @DisplayName("the harness writes to the sandbox and every record it writes is an experiment")
    void theHarnessWritesToTheSandboxAndTagsItsRecords() throws Exception {
        Path real = realLog();
        String before = real == null ? "absent" : EpisodeQuarantineTool.sha256(real);

        // The harness's own rehearsal path: the sandbox under the module's build dir.
        new C2ARealEpisodeCollectionTest().probeWorkloadAgainstSandbox();

        Path sandbox = repoRoot().resolve("matrix-brain-runtime/build/c2a-sandbox");
        Path sandboxLog = sandbox.resolve("episodic.ndjson");
        assertThat(Files.isRegularFile(sandboxLog))
            .as("the rehearsal must have written its sandbox log").isTrue();

        List<EpisodicLog.Entry> entries = new EpisodicLog(sandboxLog).readAll();
        assertThat(entries).isNotEmpty();
        assertThat(entries).allSatisfy(e ->
            assertThat(e.role())
                .as("a collection record is an EXPERIMENT, never organic traffic")
                .isEqualTo(EpisodeRole.EXPERIMENT));

        // And the consequence: none of them may be learned from.
        assertThat(LearningAdmission.admitted(entries, null))
            .as("no collected record may be admitted to the learning feed").isEmpty();
        assertThat(new EpisodicLog(sandboxLog).learningWindow()).isEmpty();

        if (real != null) {
            assertThat(EpisodeQuarantineTool.sha256(real))
                .as("the rehearsal must not touch the machine's real log").isEqualTo(before);
        }
    }
}
