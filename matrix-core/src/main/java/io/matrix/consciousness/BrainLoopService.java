package io.matrix.consciousness;

import io.matrix.auditor.MatrixTrace;
import io.matrix.perception.SaliencyEngine;
import io.matrix.perception.TextEncoder;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 158 — BrainLoopService (cognitive cycle end-to-end).
 *
 * <p>Wires together the cognitive loop components into a single
 * deterministic cycle:
 * <pre>
 *   perception (TextEncoder)
 *     → attention (AttentionRouter)
 *       → deliberation (PredictionModel)
 *         → gate (ActionGate)
 *           → action (output)
 * </pre>
 *
 * <p>Per CONSTITUTION I: brain loop is fully deterministic. The
 * only stateful components are arousal and trace (recorded for
 * audit).
 *
 * <p>This service is intentionally a plain Java class — no CDI,
 * no Quarkus, no LLM. It can run in any Java environment,
 * including FPGA/edge/pocket targets.
 */
public final class BrainLoopService {

    private final TextEncoder encoder;
    private final SaliencyEngine saliency;
    private final AttentionRouter router;
    private final PredictionModel prediction;
    private final ArousalDynamics arousal;
    private final ActionGate gate;
    private final MatrixTrace trace = new MatrixTrace();

    public BrainLoopService() {
        this(new TextEncoder(),
             new SaliencyEngine(),
             new AttentionRouter(),
             new PredictionModel(256, 0.0),
             new ArousalDynamics(),
             new ActionGate());
    }

    public BrainLoopService(TextEncoder encoder,
                             SaliencyEngine saliency,
                             AttentionRouter router,
                             PredictionModel prediction,
                             ArousalDynamics arousal,
                             ActionGate gate) {
        this.encoder = encoder;
        this.saliency = saliency;
        this.router = router;
        this.prediction = prediction;
        this.arousal = arousal;
        this.gate = gate;
    }

    /** Result of one cycle of the cognitive loop. */
    public record CycleResult(boolean accepted,
                               String action,
                               double arousal,
                               int focusCount,
                               double predictionError) {}

    /** One deterministic cycle. */
    public CycleResult cycle(String input) {
        try (var perception = trace.begin("perception")
                .input(input)) {
            // 1. Encode text → boolean vector
            boolean[] bits = encoder.encode(input);
            perception.output("bits[" + bits.length + "]");
            perception.end("ok");

            try (var saliency = trace.begin("saliency")) {
                var s = this.saliency.score("text", bits);
                saliency.end("score=" + s.score());

                try (var attention = trace.begin("attention")) {
                    var items = router.merge(List.of(), List.of(s));
                    attention.end("focusCount=" + items.size());

                    try (var gate = trace.begin("gate")) {
                        var gd = this.gate.check(input);
                        gate.end(gd.verdict().name());

                        try (var action = trace.begin("action")) {
                            if (!gd.passed()) {
                                action.end("denied");
                                return new CycleResult(false, "denied",
                                        arousal.current(), items.size(), 0.0);
                            }
                            String out = "ok: " + input;
                            action.output(out).end("ok");

                            double err = prediction.updateError(bits.length / 2.0);
                            arousal.update(err);

                            return new CycleResult(true, out,
                                    arousal.current(), items.size(), err);
                        }
                    }
                }
            }
        }
    }

    public MatrixTrace trace() { return trace; }
    public double arousal() { return arousal.current(); }
}
