package io.matrix.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RUN 133 — StressTestRunner unit tests. */
class StressTestRunnerTest {

    @Test
    void constructorNoOp() {
        StressTestRunner r = new StressTestRunner();
        assertThat(r).isNotNull();
    }

    @Test
    void requiresLoadedBridge() {
        StressTestRunner r = new StressTestRunner();
        QwenOnnxBridge stub = new QwenOnnxBridge(Path.of("/tmp/none"));
        assertThatThrownBy(() -> r.run(stub, 5, 2, "hi", 4))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void stressResultRecordFields() {
        StressTestRunner.StressResult r =
                new StressTestRunner.StressResult(10, 8, 2, 1000, 10.0);
        assertThat(r.totalRequests()).isEqualTo(10);
        assertThat(r.successful()).isEqualTo(8);
        assertThat(r.failed()).isEqualTo(2);
        assertThat(r.elapsedMs()).isEqualTo(1000);
        assertThat(r.requestsPerSecond()).isEqualTo(10.0);
    }

    @Test
    void allSucceedWithStubBridge() throws Exception {
        QwenOnnxBridge stub = new QwenOnnxBridge(Path.of("/tmp/none")) {
            @Override
            public boolean isLoaded() { return true; }
            @Override
            public String generate(String prompt, int maxTokens) {
                return "ok";
            }
        };
        StressTestRunner runner = new StressTestRunner();
        var result = runner.run(stub, 5, 2, "hi", 4);
        assertThat(result.totalRequests()).isEqualTo(5);
        assertThat(result.successful()).isEqualTo(5);
        assertThat(result.failed()).isZero();
    }

    @Test
    void partialFailuresTracked() throws Exception {
        QwenOnnxBridge stub = new QwenOnnxBridge(Path.of("/tmp/none")) {
            @Override
            public boolean isLoaded() { return true; }
            @Override
            public String generate(String prompt, int maxTokens) {
                if (prompt.contains("fail")) {
                    throw new RuntimeException("simulated");
                }
                return "ok";
            }
        };
        StressTestRunner runner = new StressTestRunner();
        var result = runner.run(stub, 10, 2, "fail", 4);
        // All requests should fail because prompt contains "fail"
        assertThat(result.totalRequests()).isEqualTo(10);
        assertThat(result.failed()).isEqualTo(10);
    }
}
