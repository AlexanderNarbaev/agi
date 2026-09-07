package io.matrix.consciousness;

import java.util.List;

/**
 * RUN 177 — BrainLoopArchitecture (documentation as code).
 *
 * <p>Provides a programmatic description of the cognitive loop,
 * intended for both documentation generation and architecture
 * assertions in tests. Phase δ.4 deliverable.
 *
 * <p>The architecture is recorded once and tests can verify
 * that all expected components are present in the cycle.
 */
public final class BrainLoopArchitecture {

    public record Component(String name, String role) {}

    public static List<Component> components() {
        return List.of(
                new Component("perception", "TextEncoder: text→boolean[]"),
                new Component("saliency", "SaliencyEngine: bottom-up attention"),
                new Component("attention", "AttentionRouter: top-down × bottom-up"),
                new Component("deliberation", "PredictionModel + impulses"),
                new Component("gate", "ActionGate: 4-cascade ethical filter"),
                new Component("action", "output decision"),
                new Component("trace", "MatrixTrace: append-only x-matrix-trace")
        );
    }

    public static int componentCount() {
        return components().size();
    }

    public static boolean hasComponent(String name) {
        for (Component c : components()) {
            if (c.name().equals(name)) return true;
        }
        return false;
    }

    public static String summary() {
        StringBuilder sb = new StringBuilder("BrainLoop architecture:\n");
        for (Component c : components()) {
            sb.append("  - ").append(c.name()).append(": ").append(c.role()).append('\n');
        }
        return sb.toString();
    }
}
