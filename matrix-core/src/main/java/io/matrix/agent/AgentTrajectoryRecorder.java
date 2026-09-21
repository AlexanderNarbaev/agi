package io.matrix.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Record/Replay for agent trajectories — saves steps as JSONL and enables
 * deterministic replay for testing and evolution research.
 *
 * <p>Implements the "Record/Replay" pattern from research: every agent
 * trajectory step is captured and can be compared against reference runs.
 */
public final class AgentTrajectoryRecorder {

    private static final ThreadLocal<SessionState> CURRENT = ThreadLocal.withInitial(() -> null);

    private AgentTrajectoryRecorder() {}

    // ── Trajectory step ──

    /** A single step in an agent trajectory. */
    public record TrajectoryStep(int tick, String action, long sensorBits, int actionCode,
                                  long timestampNs, boolean success) {
        public TrajectoryStep {
            Objects.requireNonNull(action, "action");
            if (tick < 0) throw new IllegalArgumentException("tick must be >= 0");
        }
    }

    // ── Trajectory ──

    /** A full agent trajectory: session metadata plus ordered steps. */
    public record AgentTrajectory(UUID sessionId, UUID requestId, String agentId,
                                   List<TrajectoryStep> steps, long startTimeMs, long endTimeMs) {
        private static final ObjectMapper MAPPER = new ObjectMapper()
                .findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT);

        public AgentTrajectory {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(agentId, "agentId");
            steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
            if (startTimeMs < 0) throw new IllegalArgumentException("startTimeMs must be >= 0");
            if (endTimeMs < startTimeMs) {
                throw new IllegalArgumentException("endTimeMs must be >= startTimeMs");
            }
        }

        public String toJson() {
            try {
                return MAPPER.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize AgentTrajectory", e);
            }
        }

        public static AgentTrajectory fromJson(String json) {
            try {
                return MAPPER.readValue(json, AgentTrajectory.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to deserialize AgentTrajectory", e);
            }
        }
    }

    // ── Replay result ──

    /** Outcome of comparing a reference trajectory against an actual run. */
    public record ReplayResult(boolean matches, double stepMatchRate,
                                List<Integer> divergentSteps, long timingDeltaMs) {
        public ReplayResult {
            Objects.requireNonNull(divergentSteps, "divergentSteps");
            divergentSteps = List.copyOf(divergentSteps);
            if (stepMatchRate < 0.0 || stepMatchRate > 1.0) {
                throw new IllegalArgumentException("stepMatchRate must be in [0, 1]");
            }
        }
    }

    // ── Internal session state ──

    private static final class SessionState {
        final UUID sessionId = UUID.randomUUID();
        final long startTimeMs = System.currentTimeMillis();
        final List<TrajectoryStep> steps = new ArrayList<>();
    }

    // ── Recording API ──

    /**
     * Record a single step into the current thread-local session.
     * Automatically starts a new session if none exists.
     */
    public static TrajectoryStep recordStep(int tick, String action, long sensorBits,
                                             int actionCode, boolean success) {
        var state = CURRENT.get();
        if (state == null) {
            state = new SessionState();
            CURRENT.set(state);
        }
        var step = new TrajectoryStep(tick, action, sensorBits, actionCode,
                System.nanoTime(), success);
        state.steps.add(step);
        return step;
    }

    /**
     * Finish the current thread-local session and produce a trajectory.
     *
     * @throws IllegalStateException if no session is active on this thread
     */
    public static AgentTrajectory finishSession(UUID requestId, String agentId) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(agentId, "agentId");
        var state = CURRENT.get();
        if (state == null) {
            throw new IllegalStateException(
                    "No active session. Call recordStep() first.");
        }
        CURRENT.remove();
        return new AgentTrajectory(
                state.sessionId,
                requestId,
                agentId,
                List.copyOf(state.steps),
                state.startTimeMs,
                System.currentTimeMillis()
        );
    }

    // ── Persistence ──

    /**
     * Save a trajectory as pretty-printed JSON to {@code directory/{sessionId}.json}.
     */
    public static Path save(AgentTrajectory trajectory, Path directory) {
        Objects.requireNonNull(trajectory, "trajectory");
        Objects.requireNonNull(directory, "directory");
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve(trajectory.sessionId() + ".json");
            Files.writeString(file, trajectory.toJson(), CREATE, WRITE, TRUNCATE_EXISTING);
            return file;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save trajectory to " + directory, e);
        }
    }

    /**
     * Load a trajectory from a JSON file previously saved with {@link #save}.
     */
    public static AgentTrajectory load(Path file) {
        Objects.requireNonNull(file, "file");
        try {
            return AgentTrajectory.fromJson(Files.readString(file));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load trajectory from " + file, e);
        }
    }

    // ── Replay / comparison ──

    /**
     * Compare a reference trajectory against an actual run.
     * <p>Steps are compared by {@link TrajectoryStep#action()} only —
     * {@code sensorBits} (which may be non-deterministic) are ignored.
     */
    public static ReplayResult replay(AgentTrajectory reference,
                                       AgentTrajectory actual) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(actual, "actual");

        var refSteps = reference.steps();
        var actSteps = actual.steps();
        int maxLen = Math.max(refSteps.size(), actSteps.size());
        int minLen = Math.min(refSteps.size(), actSteps.size());

        var divergentSteps = new ArrayList<Integer>();
        int matches = 0;

        for (int i = 0; i < minLen; i++) {
            if (refSteps.get(i).action().equals(actSteps.get(i).action())) {
                matches++;
            } else {
                divergentSteps.add(i);
            }
        }

        // Steps beyond the common length are all divergent
        for (int i = minLen; i < maxLen; i++) {
            divergentSteps.add(i);
        }

        double stepMatchRate = maxLen == 0 ? 1.0 : (double) matches / maxLen;
        boolean allMatch = divergentSteps.isEmpty();
        long timingDeltaMs = actual.endTimeMs() - reference.endTimeMs();

        return new ReplayResult(allMatch, stepMatchRate, divergentSteps, timingDeltaMs);
    }
}
