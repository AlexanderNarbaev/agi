package io.matrix.consciousness;

import io.matrix.auditor.MatrixTrace;
import io.matrix.perception.SaliencyEngine;
import io.matrix.perception.TextEncoder;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 182 — BrainLoopServiceV2 (extended, with Impulse input).
 *
 * <p>Variant of BrainLoopService that also accepts top-down
 * impulses. The full SPEC-006 step:
 * perception → saliency → attention(merge) → deliberation →
 * gate → action.
 */
public final class BrainLoopServiceV2 {

    public record CycleResultV2(boolean accepted,
                                 String action,
                                 double arousal,
                                 int focusCount,
                                 double predictionError,
                                 int impulseCount,
                                 String gateReason) {}

    private final TextEncoder encoder;
    private final SaliencyEngine saliency;
    private final AttentionRouter router;
    private final PredictionModel prediction;
    private final ArousalDynamics arousal;
    private final ActionGate gate;
    private final MatrixTrace trace = new MatrixTrace();

    public BrainLoopServiceV2() {
        this(new TextEncoder(), new SaliencyEngine(),
             new AttentionRouter(),
             new PredictionModel(256, 0.0),
             new ArousalDynamics(),
             new ActionGate());
    }

    public BrainLoopServiceV2(TextEncoder encoder,
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

    public CycleResultV2 cycle(String input, List<Impulse> impulses) {
        try (var s = trace.begin("perception").input(input)) {
            boolean[] bits = encoder.encode(input);
            s.output("bits[" + bits.length + "]").end("ok");

            try (var sl = trace.begin("saliency")) {
                var salScore = saliency.score("text", bits);
                sl.end("score=" + salScore.score());

                try (var at = trace.begin("attention")) {
                    var items = router.merge(impulses, List.of(salScore));
                    at.end("focus=" + items.size());

                    try (var g = trace.begin("gate")) {
                        var gd = gate.check(input);
                        g.end(gd.verdict().name() + ":" + gd.reason());

                        try (var a = trace.begin("action")) {
                            if (!gd.passed()) {
                                a.end("denied");
                                return new CycleResultV2(false, "denied",
                                        arousal.current(), items.size(), 0,
                                        impulses.size(), gd.reason());
                            }
                            String out = "ok: " + input;
                            a.output(out).end("ok");
                            double err = prediction.updateError(bits.length / 2.0);
                            arousal.update(err);
                            return new CycleResultV2(true, out,
                                    arousal.current(), items.size(), err,
                                    impulses.size(), gd.reason());
                        }
                    }
                }
            }
        }
    }

    public MatrixTrace trace() { return trace; }
    public double arousal() { return arousal.current(); }
}
