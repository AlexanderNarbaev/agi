package io.matrix.experimental;

import io.matrix.consciousness.BrainLoopService;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 199 — ExperimentalPipeline (sequential stages).
 *
 * <p>Runs a sequence of cognitive cycles with parameters
 * changing between stages. Useful for ablation studies.
 *
 * <p>Deterministic given inputs and parameters.
 */
public final class ExperimentalPipeline {

    public record Stage(String name, int cycles, String description) {}

    public record PipelineResult(int totalCycles, int accepted,
                                  int denied, List<String> outputs) {}

    /** Configurable stages. */
    public static List<Stage> defaultPipeline() {
        return List.of(
                new Stage("warmup", 10, "Initial cycles to settle state"),
                new Stage("test",   20, "Main test cycles with input variation"),
                new Stage("cooldown", 5, "Settle back to baseline")
        );
    }

    public static PipelineResult run(BrainLoopService svc,
                                       List<Stage> stages,
                                       String baseInput) {
        List<String> outputs = new ArrayList<>();
        int total = 0, accepted = 0, denied = 0;
        for (Stage stage : stages) {
            for (int i = 0; i < stage.cycles(); i++) {
                String input = stage.name() + "-" + i;
                var r = svc.cycle(input);
                total++;
                if (r.accepted()) {
                    accepted++;
                    outputs.add(r.action());
                } else {
                    denied++;
                }
            }
        }
        return new PipelineResult(total, accepted, denied, outputs);
    }
}
