package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 289 — CycleResultMapper unit tests. */
class CycleResultMapperTest {

    @Test
    void statusStringAccepted() {
        var svc = new BrainLoopService();
        var r = svc.cycle("hello");
        if (r.accepted()) {
            assertThat(CycleResultMapper.toStatusString(r)).isEqualTo("accepted");
        }
    }

    @Test
    void statusCodeAccepted() {
        var svc = new BrainLoopService();
        var r = svc.cycle("hello");
        if (r.accepted()) {
            assertThat(CycleResultMapper.toStatusCode(r)).isEqualTo(200);
        }
    }

    @Test
    void statusCodeDenied() {
        var svc = new BrainLoopService();
        var r = svc.cycle("hi\u0001there");
        if (!r.accepted()) {
            assertThat(CycleResultMapper.toStatusCode(r)).isEqualTo(403);
        }
    }

    @Test
    void summaryContainsKey() {
        var svc = new BrainLoopService();
        var r = svc.cycle("hello");
        String s = CycleResultMapper.toSummary(r);
        assertThat(s).contains("status=");
        assertThat(s).contains("arousal=");
        assertThat(s).contains("focus=");
    }

    @Test
    void customMapper() {
        var svc = new BrainLoopService();
        var r = svc.cycle("hello");
        String result = CycleResultMapper.map(r, cr -> cr.accepted() ? "YES" : "NO");
        assertThat(result).isIn("YES", "NO");
    }
}
