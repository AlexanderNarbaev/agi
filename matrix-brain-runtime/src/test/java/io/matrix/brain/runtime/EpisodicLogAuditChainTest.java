package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W42.9 / D-016: a promotion REFUSAL used to be only counted, so the reason a
 * lesson did not become knowledge was lost. It is now in the chain.
 */
class EpisodicLogAuditChainTest {

    @TempDir
    Path dir;

    @Test
    void everyPromotionDecisionIsRecordedWithItsReason() {
        EpisodicLog log = new EpisodicLog(dir.resolve("episodic.ndjson"));

        log.append("What is photosynthesis?", "The process by which plants make sugars.", 0.9, true, List.of());
        log.append("What is photosynthesis?", "The process by which plants make sugars.", 0.9, false, List.of());

        AuditChain chain = log.auditChain();
        assertThat(chain.size()).isEqualTo(2);
        assertThat(chain.verify()).isTrue();

        var entries = chain.all();
        assertThat(entries.get(0).action()).isEqualTo("episode.promoted");
        assertThat(entries.get(1).action()).isEqualTo("episode.rejected");
        assertThat(entries.get(1).details()).contains("NOT_PROMOTED_BY_STAGE");
    }
}
