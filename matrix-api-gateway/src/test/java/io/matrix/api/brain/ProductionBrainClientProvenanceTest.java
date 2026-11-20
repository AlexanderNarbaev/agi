package io.matrix.api.brain;

import io.matrix.brain.runtime.EpisodicLog;
import io.matrix.brain.runtime.EpisodeRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EPISTEMIC-INTEGRITY P0.4 (review fix F2) — the production writer declares the
 * provenance of the traffic it serves.
 *
 * <p><b>The defect.</b> {@code ProductionBrainClient.cycle} wrote the episode with
 * the five-argument {@code episodicLog.append(...)}. That overload declares nothing,
 * so every episode the gateway wrote was labelled ORGANIC by construction — the
 * provenance the cycle had produced was dropped on the floor, and any evaluation
 * traffic that reached the gateway became organic learning material.</p>
 *
 * <p><b>What is pinned here.</b> The gateway declares its traffic at its own
 * boundary — it serves users, so it declares ORGANIC — and a deployment that drives
 * it with evaluation traffic declares something else. In both cases the role the
 * cycle produced is the role the episode carries, and the admission gate does the
 * rest.</p>
 */
class ProductionBrainClientProvenanceTest {

    /** The instant the fixtures claim. */
    private static final String AT = "2026-11-15T12:00:00Z";

    /**
     * An input the mind answers confidently, and that is NOT a frozen battery item.
     *
     * <p>The promotion gate refuses a refusal ("nothing to learn from a reply that
     * declines to assert"), so a question the mind cannot answer produces NO episode
     * at all. The arithmetic stage answers this one, and the arithmetic value is not
     * in the frozen battery — so the record is written and the role on it is the
     * role the gateway declared.</p>
     */
    private static final String ARITHMETIC_INPUT = "What is 17*23?";

    @Test
    @DisplayName("the gateway's organic traffic is declared ORGANIC and stays learnable")
    void theGatewaysOrganicTrafficIsDeclaredOrganic(@TempDir Path dir) {
        Path logPath = dir.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);
        {
            ProductionBrainClient client = new ProductionBrainClient(null, log, null);

            // An input the mind ANSWERS: the promotion gate refuses a refusal, and
            // a refused interaction is never written. This one is arithmetic, and it
            // is deliberately NOT a frozen battery item.
            client.cycle(ARITHMETIC_INPUT, ARITHMETIC_INPUT, "", "");

            List<io.matrix.brain.runtime.EpisodicLog.Entry> entries = log.readAll();
            assertThat(entries).as("the interaction must be recorded durably").isNotEmpty();
            assertThat(entries.get(entries.size() - 1).role())
                .as("the gateway serves user traffic and declares it")
                .isEqualTo(EpisodeRole.ORGANIC);
            assertThat(log.learningCandidates())
                .as("organic traffic must still be learnable")
                .isNotEmpty();
        }
    }

    @Test
    @DisplayName("a client that serves evaluation traffic writes records that are NOT learnable")
    void aClientThatServesEvaluationTrafficIsNotLearnable(@TempDir Path dir) {
        Path logPath = dir.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);
        {
            ProductionBrainClient client =
                new ProductionBrainClient(null, log, null, EpisodeRole.EXPERIMENT);

            // An input the mind ANSWERS: the promotion gate refuses a refusal, and
            // a refused interaction is never written. This one is arithmetic, and it
            // is deliberately NOT a frozen battery item.
            client.cycle(ARITHMETIC_INPUT, ARITHMETIC_INPUT, "", "");

            List<io.matrix.brain.runtime.EpisodicLog.Entry> entries = log.readAll();
            assertThat(entries)
                .as("the observation is recorded durably — a refusal that leaves a "
                    + "durable trace is a correct refusal")
                .isNotEmpty();
            assertThat(entries.get(entries.size() - 1).role())
                .as("the declared role travels into the episode")
                .isEqualTo(EpisodeRole.EXPERIMENT);
            assertThat(log.learningCandidates())
                .as("evaluation traffic must never enter the learning feed")
                .isEmpty();
            assertThat(log.learningWindow()).isEmpty();
        }
    }
}
