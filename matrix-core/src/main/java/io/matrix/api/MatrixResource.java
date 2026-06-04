package io.matrix.api;

import io.matrix.cauldron.CauldronProtocol;
import io.matrix.evolution.EvolutionLoop;
import io.matrix.evolution.FitnessFn;
import io.matrix.neuron.TruthTable;
import io.matrix.snapshot.ClusterSnapshot;
import io.matrix.snapshot.SnapshotStore;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MatrixResource {

    private final Random rng = new Random();
    private final Map<String, EvolutionLoop> activeLoops = new ConcurrentHashMap<>();
    private final Map<String, Integer> loopGenerations = new ConcurrentHashMap<>();

    @GET
    @Path("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "version", "2.0.0",
                "activeLoops", activeLoops.size()
        );
    }

    @POST
    @Path("/simulate")
    public Map<String, Object> simulate(SimulateRequest req) {
        int generations = req.generations > 0 ? req.generations : 20;
        int population = req.population > 0 ? req.population : 30;
        int k = req.k > 0 ? req.k : 8;

        FitnessFn fitness = new FitnessFn(20, 20, 5, 10, 50, 3, new Random());
        EvolutionLoop loop = new EvolutionLoop(generations, population, k, fitness, rng);
        loop.run();

        var history = loop.bestFitnessHistory();
        long bestFit = history.isEmpty() ? 0 : history.get(history.size() - 1);

        return Map.of(
                "status", "completed",
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
        var snapshot = store.createSnapshot(Map.of(), 0);
        java.nio.file.Path saved = store.save(snapshot);

        return Map.of(
                "snapshotId", snapshot.snapshotId(),
                "path", saved.toString(),
                "neuronCount", snapshot.neuronCount()
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
        TruthTable table = TruthTable.random(req.k > 0 ? req.k : 4, rng);
        int input = req.input & ((1 << table.k()) - 1);
        boolean result = table.evaluate(input);

        return Map.of(
                "k", table.k(),
                "input", input,
                "output", result,
                "cardinality", table.table().cardinality()
        );
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

    public static class TruthTableRequest {
        public int k;
        public int input;
    }
}
