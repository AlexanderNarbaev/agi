package io.matrix.consciousness;

import java.util.List;

/**
 * RUN 246 — BrainLoopTopology (architecture description).
 *
 * <p>Lists the components of the brain loop and their
 * relationships, as a programmatic artifact.
 */
public final class BrainLoopTopology {

    public record Node(String name, String role) {}

    public record Edge(String from, String to, String type) {}

    public static List<Node> nodes() {
        return List.of(
                new Node("perception", "TextEncoder: text→boolean"),
                new Node("saliency", "SaliencyEngine: bottom-up score"),
                new Node("attention", "AttentionRouter: top-down × bottom-up"),
                new Node("deliberation", "PredictionModel: predict next"),
                new Node("gate", "ActionGate: 4-cascade ethics"),
                new Node("action", "output decision"),
                new Node("trace", "MatrixTrace: x-matrix-trace")
        );
    }

    public static List<Edge> edges() {
        return List.of(
                new Edge("perception", "saliency", "dataflow"),
                new Edge("saliency", "attention", "dataflow"),
                new Edge("attention", "deliberation", "dataflow"),
                new Edge("deliberation", "gate", "dataflow"),
                new Edge("gate", "action", "dataflow"),
                new Edge("perception", "trace", "audit"),
                new Edge("gate", "trace", "audit"),
                new Edge("action", "trace", "audit")
        );
    }

    public static int nodeCount() { return nodes().size(); }
    public static int edgeCount() { return edges().size(); }
}
