package io.matrix.consciousness;

import io.matrix.perception.TextEncoder;
import io.matrix.perception.SaliencyEngine;

/**
 * RUN 274 — BrainLoopServiceBuilder (builder pattern).
 *
 * <p>Constructs a BrainLoopService with custom components.
 * Useful for testing and research configurations.
 */
public final class BrainLoopServiceBuilder {

    private TextEncoder encoder = new TextEncoder();
    private SaliencyEngine saliency = new SaliencyEngine();
    private AttentionRouter router = new AttentionRouter();
    private PredictionModel prediction = new PredictionModel(256, 0.0);
    private ArousalDynamics arousal = new ArousalDynamics();
    private ActionGate gate = new ActionGate();

    public BrainLoopServiceBuilder encoder(TextEncoder e) { this.encoder = e; return this; }
    public BrainLoopServiceBuilder saliency(SaliencyEngine s) { this.saliency = s; return this; }
    public BrainLoopServiceBuilder router(AttentionRouter r) { this.router = r; return this; }
    public BrainLoopServiceBuilder prediction(PredictionModel p) { this.prediction = p; return this; }
    public BrainLoopServiceBuilder arousal(ArousalDynamics a) { this.arousal = a; return this; }
    public BrainLoopServiceBuilder gate(ActionGate g) { this.gate = g; return this; }

    public BrainLoopService build() {
        return new BrainLoopService(encoder, saliency, router,
                prediction, arousal, gate);
    }
}
