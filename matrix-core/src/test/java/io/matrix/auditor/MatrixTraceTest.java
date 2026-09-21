package io.matrix.auditor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 143 — MatrixTrace unit tests. */
class MatrixTraceTest {

    @Test
    void emptyTraceHasZeroCount() {
        var t = new MatrixTrace();
        assertThat(t.count()).isZero();
        assertThat(t.steps()).isEmpty();
        assertThat(t.last()).isNull();
    }

    @Test
    void beginCreatesFirstStep() {
        var t = new MatrixTrace();
        var step = t.begin("perception.receive");
        assertThat(t.count()).isEqualTo(1);
        assertThat(step.name).isEqualTo("perception.receive");
        assertThat(step.status).isEqualTo("started");
    }

    @Test
    void endSetsStatusAndComputesHash() {
        var t = new MatrixTrace();
        var step = t.begin("X");
        step.end("ok");
        assertThat(step.status).isEqualTo("ok");
        assertThat(step.hash).isNotEqualTo("pending");
        assertThat(step.hash.length()).isEqualTo(64); // SHA-256 hex
    }

    @Test
    void prevHashChainsBetweenSteps() {
        var t = new MatrixTrace();
        var s1 = t.begin("X");
        s1.end("ok");
        var s2 = t.begin("Y");
        s2.end("ok");
        // Second step's prevHash should equal first step's hash
        assertThat(s2.prevHash).isEqualTo(s1.hash);
    }

    @Test
    void autoCloseRecordsCloseStatus() {
        var t = new MatrixTrace();
        var step = t.begin("X");
        try (var s = step) {
            // do work
        }
        // After try-with-resources, status should be "closed"
        assertThat(step.status).isEqualTo("closed");
        assertThat(step.hash).isNotEqualTo("pending");
    }

    @Test
    void inputAndOutputStored() {
        var t = new MatrixTrace();
        var step = t.begin("X").input("hello").output("world").end("ok");
        assertThat(step.inputHash).isNotEqualTo("0");
        assertThat(step.outputHash).isNotEqualTo("0");
    }

    @Test
    void hashHexIsDeterministic() {
        String h1 = MatrixTrace.hashHex("p", "name", "in", "out", 100L);
        String h2 = MatrixTrace.hashHex("p", "name", "in", "out", 100L);
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void hashHexChangesWithInput() {
        String h1 = MatrixTrace.hashHex("p", "name", "in1", "out", 100L);
        String h2 = MatrixTrace.hashHex("p", "name", "in2", "out", 100L);
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void hundredStepsInTrace() {
        var t = new MatrixTrace();
        for (int i = 0; i < 100; i++) {
            var s = t.begin("step-" + i);
            s.end("ok");
        }
        assertThat(t.count()).isEqualTo(100);
        var last = t.last();
        assertThat(last.name).isEqualTo("step-99");
    }
}
