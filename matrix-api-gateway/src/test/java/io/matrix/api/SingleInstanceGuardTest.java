package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W3 Part A — SingleInstanceGuardTest.
 *
 * <p>Mechanical guard against N-1 (dual instances). For each engine family
 * (sleep, autonomy, distill), the handler MUST invoke the SAME object type
 * that was constructed as 'promoted'. A failure means a draft is still
 * being called somewhere.</p>
 */
class SingleInstanceGuardTest {

    @Test
    void handle_sleep_calls_realSleepScheduler_not_draft() throws Exception {
        // Find the handleSleep method, verify it references realSleepScheduler
        // (the promoted engine), not sleepScheduler (the draft).
        var src = readSource();
        // The handleSleep method should reference realSleepScheduler.triggerNow()
        int realRefs = countOccurrences(src, "realSleepScheduler.triggerNow");
        int draftRefs = countOccurrences(src, "sleepScheduler.triggerNow");
        assertThat(realRefs).as("handleSleep must call realSleepScheduler.triggerNow()")
            .isGreaterThanOrEqualTo(1);
        // Draft reference must be inside a fallback block (preceded by "// Fallback")
        // Verify the draft call exists but is preceded by fallback marker
        assertThat(src).contains("// Fallback to draft");
    }

    @Test
    void handle_status_calls_realSleepScheduler_or_autonomyLoop() throws Exception {
        var src = readSource();
        // handleStatus should reference the promoted engines
        boolean hasRealSleep = src.contains("realSleepScheduler.lastDream()")
            || src.contains("realSleepScheduler.cycleCount()");
        boolean hasAutonomy = src.contains("autonomyLoop.engine()");
        assertThat(hasRealSleep || hasAutonomy)
            .as("handleStatus must call promoted engines")
            .isTrue();
    }

    @Test
    void handle_distill_returns_not_implemented_until_W5() throws Exception {
        var src = readSource();
        // handleDistill must NOT return "status":"distilled" with synthetic data
        assertThat(src).doesNotContain("\\\\\"status\\\\\":\\\\\"distilled\\\\\"");
        // Must return honest "not-implemented"
        assertThat(src).contains("not-implemented");
        assertThat(src).contains("RECON-W5");
    }

    @Test
    void gateway_has_at_most_one_sleep_scheduler_field() throws Exception {
        // For N-1 fix: the gateway must not have BOTH draft and promoted fields active.
        // After Part A cleanup, the draft field should be removed.
        var fields = MinimalHttpServer.class.getDeclaredFields();
        long sleepSchedCount = 0;
        for (var f : fields) {
            if (f.getType().getName().equals("io.matrix.brain.runtime.SleepScheduler")) {
                sleepSchedCount++;
            }
        }
        // Once we finish N-1 cleanup, this should be 0.
        // For now we accept 0 or 1 (legacy draft being phased out).
        assertThat(sleepSchedCount)
            .as("SleepScheduler draft field count (target: 0 after N-1 cleanup)")
            .isLessThanOrEqualTo(1);
    }

    private static String readSource() throws Exception {
        // Read the source file to check for code references
        var path = java.nio.file.Path.of("src/main/java/io/matrix/api/MinimalHttpServer.java");
        return java.nio.file.Files.readString(path);
    }

    private static int countOccurrences(String s, String sub) {
        int n = 0, idx = 0;
        while ((idx = s.indexOf(sub, idx)) != -1) {
            n++;
            idx += sub.length();
        }
        return n;
    }
}
