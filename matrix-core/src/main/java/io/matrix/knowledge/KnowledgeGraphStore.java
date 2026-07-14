package io.matrix.knowledge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Entity-relation knowledge graph for MATRIX neurons.
 *
 * <p>Stores (entity) --[relation]--> (entity) triples and supports
 * graph traversal, centrality, and multi-hop reasoning.
 * Implements the GraphRAG pattern: entity extraction → relation building →
 * graph-based retrieval → multi-hop reasoning.
 *
 * <p>Thread-safe. Uses {@link ConcurrentHashMap} for entity storage,
 * {@link CopyOnWriteArrayList} for relations (optimized for read-heavy workloads),
 * and {@link StampedLock} for compound mutating operations.
 *
 * <p>Uses adjacency list index for O(1) neighbor lookups. Path finding
 * and traversal use BFS for guaranteed shortest paths.
 *
 * <p>Ref: GraphRAG pattern, L6_Memory.md
 */
public final class KnowledgeGraphStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * A node in the knowledge graph — a concept, entity, or neuron.
     */
    public record Entity(String id, String type, Map<String, Object> properties) {
        public Entity {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(type, "type must not be null");
            properties = properties == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new HashMap<>(properties));
        }

        /** Creates an entity with just id and type, no properties. */
        public Entity(String id, String type) {
            this(id, type, Map.of());
        }
    }

    /**
     * A directed, weighted edge between two entities.
     */
    public record Relation(String fromId, String toId, String predicate, double weight) {
        public Relation {
            Objects.requireNonNull(fromId, "fromId must not be null");
            Objects.requireNonNull(toId, "toId must not be null");
            Objects.requireNonNull(predicate, "predicate must not be null");
            if (weight < 0.0 || weight > 1.0) {
                throw new IllegalArgumentException("weight must be in [0.0, 1.0], got " + weight);
            }
        }

        /** Creates a relation with default weight 1.0. */
        public Relation(String fromId, String toId, String predicate) {
            this(fromId, toId, predicate, 1.0);
        }
    }

    /**
     * Result of a graph traversal: the path of entities and edges, plus total weight.
     */
    public record TraversalResult(List<Entity> path, List<Relation> edges, double totalWeight) {
        public TraversalResult {
            path = List.copyOf(path);
            edges = List.copyOf(edges);
        }

        /** Length of the path in number of edges. */
        public int depth() {
            return edges.size();
        }
    }

    // ─── Storage ───

    private final ConcurrentHashMap<String, Entity> entities = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Relation> relations = new CopyOnWriteArrayList<>();

    // Lazily-built adjacency index (null = needs rebuild)
    private volatile Map<String, List<Relation>> adjacencyIndex;

    private final StampedLock lock = new StampedLock();

    // ─── Builder ───

    /** Returns a new {@link Builder} for fluent construction. */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final KnowledgeGraphStore store = new KnowledgeGraphStore();

        public Builder entity(Entity entity) {
            store.addEntity(entity);
            return this;
        }

        public Builder entity(String id, String type) {
            store.addEntity(new Entity(id, type));
            return this;
        }

        public Builder relation(Relation relation) {
            store.addRelation(relation);
            return this;
        }

        public Builder relation(String fromId, String toId, String predicate, double weight) {
            store.addRelation(new Relation(fromId, toId, predicate, weight));
            return this;
        }

        public Builder relation(String fromId, String toId, String predicate) {
            store.addRelation(new Relation(fromId, toId, predicate));
            return this;
        }

        public KnowledgeGraphStore build() {
            store.ensureAdjacency();
            return store;
        }
    }

    // ─── CRUD ───

    /**
     * Adds an entity to the graph. Existing entity with same id is overwritten.
     */
    public void addEntity(Entity entity) {
        entities.put(entity.id(), entity);
    }

    /**
     * Adds a relation to the graph. Both endpoints must exist as entities.
     */
    public void addRelation(Relation relation) {
        if (!entities.containsKey(relation.fromId())) {
            throw new IllegalArgumentException(
                    "Source entity not found: " + relation.fromId());
        }
        if (!entities.containsKey(relation.toId())) {
            throw new IllegalArgumentException(
                    "Target entity not found: " + relation.toId());
        }
        relations.add(relation);
        adjacencyIndex = null; // invalidate cache
    }

    /**
     * Returns the entity with the given id, or empty if not found.
     */
    public Optional<Entity> getEntity(String id) {
        return Optional.ofNullable(entities.get(id));
    }

    /**
     * Returns all outgoing relations from the given entity.
     */
    public List<Relation> getRelations(String entityId) {
        ensureAdjacency();
        return adjacencyIndex.getOrDefault(entityId, List.of());
    }

    /** Returns the total number of entities. */
    public int entityCount() {
        return entities.size();
    }

    /** Returns the total number of relations. */
    public int relationCount() {
        return relations.size();
    }

    // ─── Traversal ───

    /**
     * Traverses the graph starting from {@code entityId} up to {@code maxDepth}.
     * Returns all paths that satisfy the optional {@code edgeFilter}.
     * Uses BFS.
     *
     * @param startId    starting entity id
     * @param maxDepth   maximum number of hops (edges) from start
     * @param edgeFilter optional predicate to filter relations; pass {@code r -> true} for all
     * @return list of traversal results, empty if start entity not found
     */
    public List<TraversalResult> traverse(String startId, int maxDepth,
                                           Predicate<Relation> edgeFilter) {
        if (!entities.containsKey(startId)) {
            return List.of();
        }
        ensureAdjacency();

        List<TraversalResult> results = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<TraversalState> queue = new ArrayDeque<>();

        visited.add(startId);
        queue.add(new TraversalState(
                List.of(entities.get(startId)), List.of(), 0.0, 0));

        while (!queue.isEmpty()) {
            TraversalState current = queue.poll();
            if (current.depth >= maxDepth) continue;

            String lastId = current.path.get(current.path.size() - 1).id();
            List<Relation> neighbors = adjacencyIndex.getOrDefault(lastId, List.of());

            for (Relation rel : neighbors) {
                if (!edgeFilter.test(rel)) continue;
                if (!entities.containsKey(rel.toId())) continue;

                List<Entity> newPath = new ArrayList<>(current.path);
                newPath.add(entities.get(rel.toId()));

                List<Relation> newEdges = new ArrayList<>(current.edges);
                newEdges.add(rel);

                double newWeight = current.totalWeight + rel.weight();

                TraversalResult result = new TraversalResult(newPath, newEdges, newWeight);
                results.add(result);

                if (!visited.contains(rel.toId())) {
                    visited.add(rel.toId());
                    queue.add(new TraversalState(newPath, newEdges, newWeight, current.depth + 1));
                }
            }
        }

        return results;
    }

    /**
     * Finds a path (list of entities) from {@code fromId} to {@code toId}.
     * Uses BFS for guaranteed shortest path (fewest edges).
     *
     * @return list of entity ids along the path, empty if no path found.
     *         Includes start and end entities.
     */
    public List<Entity> findPath(String fromId, String toId, int maxDepth) {
        if (!entities.containsKey(fromId) || !entities.containsKey(toId)) {
            return List.of();
        }
        if (fromId.equals(toId)) {
            return List.of(entities.get(fromId));
        }
        ensureAdjacency();

        // BFS with parent tracking
        Queue<String> queue = new ArrayDeque<>();
        Map<String, String> parent = new HashMap<>();
        Map<String, Integer> depth = new HashMap<>();
        Set<String> visited = new HashSet<>();

        queue.add(fromId);
        visited.add(fromId);
        depth.put(fromId, 0);

        String found = null;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDepth = depth.get(current);

            if (current.equals(toId)) {
                found = current;
                break;
            }
            if (currentDepth >= maxDepth) continue;

            List<Relation> neighbors = adjacencyIndex.getOrDefault(current, List.of());
            for (Relation rel : neighbors) {
                if (!entities.containsKey(rel.toId())) continue;
                String next = rel.toId();
                if (!visited.contains(next)) {
                    visited.add(next);
                    parent.put(next, current);
                    depth.put(next, currentDepth + 1);
                    queue.add(next);
                }
            }
        }

        if (found == null) {
            return List.of();
        }

        // Reconstruct path
        List<Entity> path = new ArrayList<>();
        String step = toId;
        while (step != null) {
            path.add(entities.get(step));
            step = parent.get(step);
        }
        Collections.reverse(path);
        return path;
    }

    // ─── Graph Metrics ───

    /**
     * Computes degree centrality for the given entity.
     * Degree centrality = (in-degree + out-degree) / (N - 1), where N = total entities.
     *
     * @return centrality in [0.0, 1.0], or 0.0 if entity not found or graph has ≤1 entities
     */
    public double centrality(String entityId) {
        if (!entities.containsKey(entityId)) {
            return 0.0;
        }
        int n = entities.size();
        if (n <= 1) return 0.0;

        int degree = 0;
        for (Relation rel : relations) {
            if (rel.fromId().equals(entityId) || rel.toId().equals(entityId)) {
                degree++;
            }
        }
        return (double) degree / (n - 1);
    }

    /**
     * Returns the top-k entities by degree centrality, sorted descending.
     */
    public List<String> topCentral(int k) {
        if (k <= 0) return List.of();

        record Entry(String id, double centrality) {}
        return entities.keySet().stream()
                .map(id -> new Entry(id, centrality(id)))
                .sorted(Comparator.comparingDouble(Entry::centrality).reversed())
                .limit(k)
                .map(Entry::id)
                .toList();
    }

    // ─── Serialization ───

    /**
     * Serializes the entire graph to a JSON string.
     */
    public String toJson() {
        long stamp = lock.readLock();
        try {
            record GraphData(List<Entity> entities, List<Relation> relations) {}
            GraphData data = new GraphData(
                    List.copyOf(entities.values()),
                    List.copyOf(relations));
            return MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize graph", e);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /**
     * Deserializes a JSON string into a new {@link KnowledgeGraphStore}.
     * The JSON must contain an object with {@code entities} and {@code relations} arrays.
     */
    public static KnowledgeGraphStore fromJson(String json) {
        try {
            record GraphData(List<Entity> entities, List<Relation> relations) {}
            GraphData data = MAPPER.readValue(json, new TypeReference<GraphData>() {});
            KnowledgeGraphStore store = new KnowledgeGraphStore();
            for (Entity entity : data.entities()) {
                store.addEntity(entity);
            }
            for (Relation relation : data.relations()) {
                store.addRelation(relation);
            }
            store.ensureAdjacency();
            return store;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize graph", e);
        }
    }

    // ─── Internal ───

    /**
     * (Re)builds the adjacency index from current relations.
     * Called lazily — only when the index is null (dirtied by a relation write).
     */
    private void ensureAdjacency() {
        if (adjacencyIndex != null) return;

        long stamp = lock.writeLock();
        try {
            if (adjacencyIndex != null) return; // double-check

            Map<String, List<Relation>> index = new HashMap<>();
            for (Relation rel : relations) {
                index.computeIfAbsent(rel.fromId(), k -> new ArrayList<>()).add(rel);
            }
            // Freeze lists for immutable reads
            index.replaceAll((k, v) -> List.copyOf(v));
            adjacencyIndex = index;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Internal BFS state for traversal.
     */
    private record TraversalState(
            List<Entity> path,
            List<Relation> edges,
            double totalWeight,
            int depth) {}
}
