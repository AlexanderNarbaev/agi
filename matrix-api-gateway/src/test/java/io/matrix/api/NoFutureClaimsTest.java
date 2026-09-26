package io.matrix.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W3 Part A — NoFutureClaimsTest.
 *
 * <p>Anti-regression: any endpoint returning placeholder/fake success must
 * return {"status":"not-implemented","planned":"RECON-WX"} instead of
 * synthetic JSON that pretends the work is done.</p>
 */
class NoFutureClaimsTest {

    @Test
    void no_endpoint_returns_synthetic_distilled_json() throws Exception {
        Path p = Path.of("/home/alexandr-narbaev/Projects/agi/matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java");
        String src = Files.readString(p);
        // Must NOT contain fake "status":"distilled" (pre-N-2-fix style)
        assertThat(src)
            .as("handleDistill must not return synthetic 'distilled' JSON")
            .doesNotContain("\\\"status\\\":\\\"distilled\\\"");
    }

    @Test
    void handle_distill_reports_planned_wave() throws Exception {
        Path p = Path.of("/home/alexandr-narbaev/Projects/agi/matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java");
        String src = Files.readString(p);
        // Must contain honest "not-implemented" + planned wave reference
        assertThat(src).contains("not-implemented");
        assertThat(src).contains("RECON-W5");
    }

    @Test
    void no_future_tense_about_real_engine_completion() throws Exception {
        // All "armed" status messages should be paired with "planned" or
        // explicit "not-implemented" markers.
        Path p = Path.of("/home/alexandr-narbaev/Projects/agi/matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java");
        String src = Files.readString(p);
        // The old "status":"armed" without planned marker is a future-tense claim
        // (it implies the system is ready when it isn't).
        if (src.contains("status\\\":\\\"armed\\\"")) {
            // If armed appears, it MUST be paired with planned (RECON-Wx)
            int armedIdx = src.indexOf("armed");
            String context = src.substring(Math.max(0, armedIdx - 100), armedIdx + 100);
            assertThat(context).contains("RECON-W");
        }
    }
}
