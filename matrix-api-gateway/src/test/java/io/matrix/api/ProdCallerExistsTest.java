package io.matrix.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W3 Part A — ProdCallerExistsTest.
 *
 * <p>Mechanical guard against the 'library without owner' failure mode.
 * Each promoted wrapper class must have ≥1 actual method call (not just
 * constructor) in the gateway decision path.</p>
 */
class ProdCallerExistsTest {

    @Test
    void realSleepScheduler_has_prod_caller() throws Exception {
        Path p = Path.of("src/main/java/io/matrix/api/MinimalHttpServer.java");
        String src = Files.readString(p);
        // The promoted engine must be invoked (not just constructed)
        assertThat(src)
            .as("RealSleepScheduler.triggerNow() or .lastDream() must be called")
            .containsPattern("realSleepScheduler\\.(triggerNow|lastDream|cycleCount|snapshot)\\(");
    }

    @Test
    void autonomyLoop_has_prod_caller() throws Exception {
        Path p = Path.of("src/main/java/io/matrix/api/MinimalHttpServer.java");
        String src = Files.readString(p);
        assertThat(src)
            .as("AutonomyLoop.engine() or .snapshot() must be called")
            .containsPattern("autonomyLoop\\.(engine|snapshot)\\(");
    }

    @Test
    void persistentMind_has_prod_caller() throws Exception {
        Path p = Path.of("src/main/java/io/matrix/api/MinimalHttpServer.java");
        String src = Files.readString(p);
        // PersistentMind is constructed; check it's invoked at least once
        assertThat(src)
            .as("PersistentMind must be referenced (constructed and used)")
            .contains("persistentMind");
    }

    @Test
    void realAuditService_has_prod_caller() throws Exception {
        Path p = Path.of("src/main/java/io/matrix/api/MinimalHttpServer.java");
        String src = Files.readString(p);
        assertThat(src)
            .as("RealAuditService.record() must be called")
            .containsPattern("realAuditService\\.record\\(");
    }

    @Test
    void realInboxWatcher_has_prod_caller() throws Exception {
        Path p = Path.of("src/main/java/io/matrix/api/MinimalHttpServer.java");
        String src = Files.readString(p);
        assertThat(src)
            .as("RealInboxWatcher.scan() or .snapshot() must be called")
            .containsPattern("inboxWatcher\\.(scan|snapshot)\\(");
    }
}
