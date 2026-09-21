package io.matrix.experimental;

import io.matrix.consciousness.BrainLoopService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 199 — ExperimentalPipeline unit tests. */
class ExperimentalPipelineTest {

    @Test
    void defaultPipelineRuns() {
        var svc = new BrainLoopService();
        var stages = ExperimentalPipeline.defaultPipeline();
        var result = ExperimentalPipeline.run(svc, stages, "base");
        // 10 + 20 + 5 = 35 cycles total
        assertThat(result.totalCycles()).isEqualTo(35);
        assertThat(result.accepted() + result.denied()).isEqualTo(35);
    }

    @Test
    void customPipeline() {
        var svc = new BrainLoopService();
        var stages = List.of(
                new ExperimentalPipeline.Stage("x", 5, "test 5"),
                new ExperimentalPipeline.Stage("y", 3, "test 3")
        );
        var result = ExperimentalPipeline.run(svc, stages, "x");
        assertThat(result.totalCycles()).isEqualTo(8);
    }

    @Test
    void emptyPipeline() {
        var svc = new BrainLoopService();
        var result = ExperimentalPipeline.run(svc, List.of(), "x");
        assertThat(result.totalCycles()).isZero();
        assertThat(result.outputs()).isEmpty();
    }

    @Test
    void stageRecord() {
        var stage = new ExperimentalPipeline.Stage("test", 10, "desc");
        assertThat(stage.name()).isEqualTo("test");
        assertThat(stage.cycles()).isEqualTo(10);
        assertThat(stage.description()).isEqualTo("desc");
    }

    @Test
    void pipelineResultRecord() {
        var result = new ExperimentalPipeline.PipelineResult(
                100, 80, 20, List.of("output1", "output2"));
        assertThat(result.totalCycles()).isEqualTo(100);
        assertThat(result.accepted()).isEqualTo(80);
        assertThat(result.denied()).isEqualTo(20);
        assertThat(result.outputs()).hasSize(2);
    }

    @Test
    void outputsAreAcceptedActions() {
        var svc = new BrainLoopService();
        var stages = List.of(new ExperimentalPipeline.Stage("a", 3, ""));
        var result = ExperimentalPipeline.run(svc, stages, "x");
        // All 3 should accept (clean inputs)
        assertThat(result.accepted()).isEqualTo(3);
        assertThat(result.denied()).isZero();
    }
}
