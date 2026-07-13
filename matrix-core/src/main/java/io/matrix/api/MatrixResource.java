package io.matrix.api;

import io.matrix.agent.AgentBrainService;
import io.matrix.cauldron.CauldronProtocol;
import io.matrix.evolution.EvolutionLoop;
import io.matrix.evolution.FitnessFn;
import io.matrix.neuron.TruthTable;
import io.matrix.snapshot.ClusterSnapshot;
import io.matrix.snapshot.SnapshotStore;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@TenantAware
public class MatrixResource {

    private static final Logger log = LoggerFactory.getLogger(MatrixResource.class);

    private final Random rng = new Random();
    private final Map<String, EvolutionLoop> activeLoops = new ConcurrentHashMap<>();
    private final Map<String, Integer> loopGenerations = new ConcurrentHashMap<>();

    @Inject
    TenantFilter tenantFilter;

    @Inject
    AgentBrainService brainService;

    @GET
    @Path("/health")
    public Map<String, Object> health() {
        TenantContext tc = CurrentTenant.get();
        return Map.of(
                "status", "UP",
                "version", "2.1.0",
                "tenantId", tc != null ? tc.tenantId() : "system",
                "tenantName", tc != null ? tc.displayName() : "System",
                "activeLoops", activeLoops.size()
        );
    }

    @GET
    @Path("/tenants")
    public Map<String, Object> listTenants() {
        return Map.of(
                "count", tenantFilter.allTenants().size(),
                "tenants", tenantFilter.allTenants().values().stream()
                        .map(t -> Map.of("tenantId", t.tenantId(),
                                "displayName", t.displayName()))
                        .toList()
        );
    }

    @POST
    @Path("/tenants")
    public Map<String, Object> createTenant(TenantCreateRequest req) {
        String id = req.id != null && !req.id.isBlank()
                ? req.id : UUID.randomUUID().toString().substring(0, 12);
        TenantContext tenant = tenantFilter.getOrCreate(id);
        return Map.of(
                "tenantId", tenant.tenantId(),
                "instanceId", tenant.instanceId(),
                "displayName", tenant.displayName()
        );
    }

    @POST
    @Path("/simulate")
    public Map<String, Object> simulate(SimulateRequest req) {
        int generations = req.generations > 0 ? req.generations : 20;
        int population = req.population > 0 ? req.population : 30;
        int k = req.k > 0 ? req.k : 8;

        FitnessFn fitness = new FitnessFn(20, 20, 5, 10, 50, 3,
                new Random(CurrentTenant.tenantId().hashCode()));
        EvolutionLoop loop = new EvolutionLoop(generations, population, k, fitness, rng);
        loop.run();

        var history = loop.bestFitnessHistory();
        long bestFit = history.isEmpty() ? 0 : history.get(history.size() - 1);

        return Map.of(
                "status", "completed",
                "tenantId", CurrentTenant.tenantId(),
                "generations", generations,
                "population", population,
                "k", k,
                "bestFitness", bestFit
        );
    }

    @POST
    @Path("/evolve")
    public Map<String, Object> evolve(EvolveRequest req) {
        String loopId = UUID.randomUUID().toString().substring(0, 8);
        int generations = req.generations > 0 ? req.generations : 10;

        FitnessFn fitness = new FitnessFn(20, 20, 5, 10, 50, 3, new Random());
        EvolutionLoop loop = new EvolutionLoop(generations,
                req.population > 0 ? req.population : 20,
                req.k > 0 ? req.k : 8, fitness, rng);

        new Thread(() -> {
            loop.run();
            activeLoops.put(loopId, loop);
        }).start();

        return Map.of("loopId", loopId, "status", "started", "generations", generations);
    }

    @GET
    @Path("/evolve/{loopId}")
    public Map<String, Object> evolveStatus(@PathParam("loopId") String loopId) {
        EvolutionLoop loop = activeLoops.get(loopId);
        if (loop == null) {
            throw new NotFoundException("Loop not found or not yet completed: " + loopId);
        }

        var history = loop.bestFitnessHistory();
        long bestFit = history.isEmpty() ? 0 : history.get(history.size() - 1);

        return Map.of(
                "loopId", loopId,
                "status", "completed",
                "bestFitness", bestFit,
                "generations", history.size()
        );
    }

    @POST
    @Path("/cauldron")
    public Map<String, Object> cauldron(CauldronRequest req) {
        String task = req.task != null ? req.task : "navigation";
        CauldronProtocol cauldron = new CauldronProtocol(rng);
        var result = cauldron.evolveForTask(task);
        var pkg = cauldron.packageResult(result, task,
                task.toUpperCase(), "api-instance");

        return Map.of(
                "status", result.state().toString(),
                "task", task,
                "fnlName", pkg.name(),
                "fnlType", pkg.type(),
                "accuracy", pkg.accuracy(),
                "generations", result.generations(),
                "bestFitness", result.bestFitness()
        );
    }

    @POST
    @Path("/snapshot")
    public Map<String, Object> createSnapshot() throws Exception {
        java.nio.file.Path storePath = java.nio.file.Path.of("/tmp/matrix-snapshots");
        SnapshotStore store = new SnapshotStore(storePath, "api-instance");

        // Create snapshot with brain configuration metadata
        // Real neuron snapshots require NeuronInstance which needs full cluster state
        var snapshot = store.createSnapshot(Map.of(), 0);
        java.nio.file.Path saved = store.save(snapshot);

        // Report brain state alongside snapshot
        int neuronCount = 0;
        if (brainService != null && brainService.brain() != null) {
            var brain = brainService.brain();
            neuronCount = brain.sensorLayer().neurons().size()
                    + brain.featureLayer().neurons().size()
                    + brain.actionLayer().neurons().size();
        }

        return Map.of(
                "snapshotId", snapshot.snapshotId(),
                "path", saved.toString(),
                "neuronCount", snapshot.neuronCount(),
                "brainNeurons", neuronCount
        );
    }

    @GET
    @Path("/snapshot/latest")
    public Map<String, Object> latestSnapshot() throws Exception {
        java.nio.file.Path storePath = java.nio.file.Path.of("/tmp/matrix-snapshots");
        SnapshotStore store = new SnapshotStore(storePath, "api-instance");
        ClusterSnapshot latest = store.loadLatest();

        if (latest == null) {
            throw new NotFoundException("No snapshots found");
        }

        return Map.of(
                "snapshotId", latest.snapshotId(),
                "instanceId", latest.instanceId(),
                "neuronCount", latest.neuronCount()
        );
    }

    @POST
    @Path("/truth-table")
    public Map<String, Object> evaluateTruthTable(TruthTableRequest req) {
        TruthTable table;
        if (req.tableBits != null && req.k > 0) {
            // Use user-provided truth table
            BitSet bits = new BitSet(1 << req.k);
            for (int i = 0; i < req.tableBits.length() && i < (1 << req.k); i++) {
                if (req.tableBits.charAt(i) == '1') {
                    bits.set(i);
                }
            }
            table = TruthTable.of(req.k, bits);
        } else {
            // Create random table if none provided
            table = TruthTable.random(req.k > 0 ? req.k : 4, rng);
        }
        int input = req.input & ((1 << table.k()) - 1);
        boolean result = table.evaluate(input);

        return Map.of(
                "k", table.k(),
                "input", input,
                "output", result,
                "cardinality", table.table().cardinality()
        );
    }

    // ─── Agent endpoints ───

    @POST
    @Path("/agent/infer")
    public Map<String, Object> inferAgent(AgentInferRequest req) {
        String action = brainService.act(req.sensorBits);
        return Map.of("action", action, "sensorBits", req.sensorBits);
    }

    @POST
    @Path("/agent/train")
    public Map<String, Object> trainAgent(AgentTrainRequest req) {
        int generations = req.generations > 0 ? req.generations : 20;
        int population = req.population > 0 ? req.population : 30;
        int k = req.k > 0 ? req.k : 8;

        AgentBrainService.EvolutionResult result = brainService.train(generations, population, k);

        return Map.of(
                "status", "completed",
                "generations", result.generations(),
                "bestFitness", result.bestFitness()
        );
    }

    @POST
    @Path("/agent/save")
    public Map<String, Object> saveAgent(AgentSaveRequest req) throws Exception {
        String path = req.path != null ? req.path : "/tmp/matrix-brain-default.json";
        java.nio.file.Path saved = brainService.save(path);

        return Map.of("status", "saved", "path", saved.toAbsolutePath().toString());
    }

    @POST
    @Path("/agent/load")
    public Map<String, Object> loadAgent(AgentLoadRequest req) throws Exception {
        String path = req.path != null ? req.path : "/tmp/matrix-brain-default.json";
        brainService.load(path);

        return Map.of("status", "loaded", "path", path);
    }

    @POST
    @Path("/agent/train-online")
    public Map<String, Object> trainOnline(TrainOnlineRequest req) {
        int iterations = req.iterations > 0 ? req.iterations : 5;
        brainService.onlineTrain(iterations);

        return Map.of(
                "status", "completed",
                "method", "online",
                "iterations", iterations
        );
    }

    // ─── Swarm intelligence: Neuron sharing via Noosphere ───

    private final Map<String, List<SharedNeuron>> sharedNeurons = new ConcurrentHashMap<>();

    /** A neuron shared by an agent to the swarm. */
    public record SharedNeuron(String agentId, String neuronData, double fitness, long timestamp) {}

    @POST
    @Path("/agent/share")
    public Map<String, Object> shareNeurons(NeuronShareRequest req) {
        String role = req.role != null ? req.role.toLowerCase() : "generalist";
        SharedNeuron shared = new SharedNeuron(
                req.agentId != null ? req.agentId : "unknown",
                req.neuronData,
                req.fitness,
                System.currentTimeMillis());

        sharedNeurons.computeIfAbsent(role, k -> new ArrayList<>()).add(shared);

        // Keep only top 50 neurons per role
        List<SharedNeuron> list = sharedNeurons.get(role);
        if (list.size() > 50) {
            list.sort((a, b) -> Double.compare(b.fitness, a.fitness));
            while (list.size() > 50) list.remove(list.size() - 1);
        }

        log.info("Neuron shared: role={} agentId={} fitness={:.3f}",
                role, shared.agentId(), shared.fitness());

        return Map.of(
                "status", "shared",
                "role", role,
                "totalShared", sharedNeurons.get(role).size()
        );
    }

    @GET
    @Path("/agent/neurons/{role}")
    public List<Map<String, Object>> getSharedNeurons(@PathParam("role") String role) {
        List<SharedNeuron> neurons = sharedNeurons.getOrDefault(role.toLowerCase(), List.of());

        return neurons.stream()
                .sorted((a, b) -> Double.compare(b.fitness, a.fitness))
                .map(n -> Map.<String, Object>of(
                        "agentId", n.agentId(),
                        "neuronData", n.neuronData(),
                        "fitness", n.fitness(),
                        "timestamp", n.timestamp()))
                .toList();
    }

    public static class SimulateRequest {
        public int generations;
        public int population;
        public int k;
    }

    public static class EvolveRequest {
        public int generations;
        public int population;
        public int k;
    }

    public static class CauldronRequest {
        public String task;
    }

    public static class TenantCreateRequest {
        public String id;
    }

    public static class TruthTableRequest {
        public int k;
        public int input;
        public String tableBits; // Optional: binary string representation of truth table outputs
    }

    public static class AgentInferRequest {
        public long sensorBits;
    }

    public static class AgentTrainRequest {
        public int generations;
        public int population;
        public int k;
    }

    public static class AgentSaveRequest {
        public String path;
    }

    public static class AgentLoadRequest {
        public String path;
    }

    public static class TrainOnlineRequest {
        public int iterations;
    }
}
