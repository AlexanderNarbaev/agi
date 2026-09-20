package io.matrix.federation.liquid;

import java.util.*;

/**
 * W588 — Federation Map.
 *
 * Force-directed graph of nodes, roles, and consensus links.
 * Generates visualization data for browser rendering.
 */
public final class FederationMap {

    /**
     * A node in the federation map.
     */
    public record MapNode(
            long id,
            String label,
            NodeRole role,
            double x,
            double y,
            Map<String, String> metadata
    ) {}

    /**
     * An edge in the federation map.
     */
    public record MapEdge(
            long fromId,
            long toId,
            String type,
            double weight
    ) {}

    private final Map<Long, MapNode> nodes = new HashMap<>();
    private final List<MapEdge> edges = new ArrayList<>();
    private final Random rng;

    public FederationMap(long seed) {
        this.rng = new Random(seed);
    }

    /**
     * Add a node to the map.
     */
    public void addNode(long id, String label, NodeRole role) {
        double x = rng.nextDouble() * 800;
        double y = rng.nextDouble() * 600;
        nodes.put(id, new MapNode(id, label, role, x, y, Map.of()));
    }

    /**
     * Add an edge between nodes.
     */
    public void addEdge(long fromId, long toId, String type, double weight) {
        edges.add(new MapEdge(fromId, toId, type, weight));
    }

    /**
     * Generate JSON for visualization.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder("{");

        // Nodes
        sb.append("\"nodes\":[");
        boolean first = true;
        for (MapNode node : nodes.values()) {
            if (!first) sb.append(",");
            sb.append("{\"id\":").append(node.id())
              .append(",\"label\":\"").append(node.label()).append("\"")
              .append(",\"role\":\"").append(node.role()).append("\"")
              .append(",\"x\":").append(String.format("%.1f", node.x()))
              .append(",\"y\":").append(String.format("%.1f", node.y()))
              .append(",\"color\":\"").append(roleToColor(node.role())).append("\"")
              .append("}");
            first = false;
        }
        sb.append("],");

        // Edges
        sb.append("\"edges\":[");
        first = true;
        for (MapEdge edge : edges) {
            if (!first) sb.append(",");
            sb.append("{\"from\":").append(edge.fromId())
              .append(",\"to\":").append(edge.toId())
              .append(",\"type\":\"").append(edge.type()).append("\"")
              .append(",\"weight\":").append(edge.weight())
              .append("}");
            first = false;
        }
        sb.append("]}");

        return sb.toString();
    }

    /**
     * Generate HTML visualization.
     */
    public String toHtml() {
        return "<!DOCTYPE html><html><head><title>MATRIX Federation Map</title>" +
            "<script src='https://d3js.org/d3.v7.min.js'></script>" +
            "<style>body{font-family:monospace;background:#0a0a0a;padding:20px;}" +
            "h1{color:#00ffff;}svg{background:#111;border:1px solid #333;}" +
            ".node{stroke:#00ff88;stroke-width:2px;}" +
            ".link{stroke:#333;stroke-width:1px;}" +
            ".label{fill:#00ff88;font-size:12px;}</style></head><body>" +
            "<h1>MATRIX Federation Map</h1>" +
            "<svg id='map' width='800' height='600'></svg>" +
            "<script>" +
            "const data=" + toJson() + ";" +
            "const svg=d3.select('#map');" +
            "svg.selectAll('line').data(data.edges).enter().append('line')" +
            ".attr('x1',d=>data.nodes.find(n=>n.id===d.from).x)" +
            ".attr('y1',d=>data.nodes.find(n=>n.id===d.from).y)" +
            ".attr('x2',d=>data.nodes.find(n=>n.id===d.to).x)" +
            ".attr('y2',d=>data.nodes.find(n=>n.id===d.to).y)" +
            ".attr('class','link');" +
            "svg.selectAll('circle').data(data.nodes).enter().append('circle')" +
            ".attr('cx',d=>d.x).attr('cy',d=>d.y).attr('r',20)" +
            ".attr('fill',d=>d.color).attr('class','node');" +
            "svg.selectAll('text').data(data.nodes).enter().append('text')" +
            ".attr('x',d=>d.x).attr('y',d=>d.y+5)" +
            ".attr('text-anchor','middle').attr('class','label')" +
            ".text(d=>d.label);" +
            "</script></body></html>";
    }

    private String roleToColor(NodeRole role) {
        return switch (role) {
            case INFANT -> "#666666";
            case LEARNER -> "#0066ff";
            case ADULT -> "#00ff88";
            case SPECIALIST -> "#ffaa00";
            case GUARDIAN -> "#ff4444";
        };
    }

    public int getNodeCount() { return nodes.size(); }
    public int getEdgeCount() { return edges.size(); }
    public MapNode getNode(long id) { return nodes.get(id); }
}
