package io.matrix.agent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Agent Genome — structured representation of agent configuration parameters.
 *
 * <p>Inspired by genome-based evolution of RAG systems (Habr #1019018) and
 * meta-harness optimization (Habr #1045532). Each genome represents a complete
 * configuration of an agent that can be evolved through genetic algorithms.
 *
 * <p>Lifecycle: candidate → evaluated → pending_approval → active
 *
 * <p>Ref: Research Synthesis 2026-Q3 §2.1
 */
public record AgentGenome(
        /** Unique genome identifier. */
        String id,
        /** Prompt patches (not full replacement — incremental changes only). */
        Map<String, String> promptPatches,
        /** Ordered list of workflow stage names. */
        List<String> stageOrder,
        /** Tool sets available at each stage. */
        Map<String, Set<String>> toolSets,
        /** Memory strategy configuration. */
        MemoryConfig memoryConfig,
        /** RAG configuration. */
        RagConfig ragConfig,
        /** Safety constraints (structural, not prompt-based). */
        SafetyConstraints safetyConstraints,
        /** Genome lifecycle status. */
        Status status,
        /** Fitness scores (multi-objective). */
        FitnessScore fitness,
        /** Generation number (0 = initial). */
        int generation,
        /** Parent genome ID (null for initial generation). */
        String parentId
) {
    /**
     * Genome lifecycle status.
     */
    public enum Status {
        /** Newly generated, not yet evaluated. */
        CANDIDATE,
        /** Evaluated by fitness function. */
        EVALUATED,
        /** Awaiting human approval before activation. */
        PENDING_APPROVAL,
        /** Active genome currently in use. */
        ACTIVE,
        /** Retired (replaced by better genome). */
        RETIRED
    }

    /**
     * Memory strategy configuration.
     */
    public record MemoryConfig(
            /** Maximum number of memory entries to retain. */
            int maxEntries,
            /** Compaction strategy: SLIDING_WINDOW, IMPORTANCE_BASED, HYBRID. */
            String compactionStrategy,
            /** Minimum importance threshold for retention. */
            double importanceThreshold,
            /** Whether to enable drift detection. */
            boolean driftDetection
    ) {
        public static MemoryConfig defaults() {
            return new MemoryConfig(1000, "HYBRID", 0.3, true);
        }
    }

    /**
     * RAG configuration.
     */
    public record RagConfig(
            /** Number of results to retrieve (static fallback). */
            int topK,
            /** Whether to use adaptive context management (knee-point pruning). */
            boolean adaptiveContext,
            /** Sensitivity for knee-point pruning (0.0-1.0). */
            double kneeSensitivity,
            /** Minimum relevance threshold for strong matches. */
            double strongThreshold,
            /** Minimum relevance threshold for borderline matches. */
            double borderlineThreshold,
            /** Whether to use hybrid search (dense + sparse + RRF). */
            boolean hybridSearch,
            /** Whether to use structure-aware chunking with breadcrumbs. */
            boolean structureAware
    ) {
        public static RagConfig defaults() {
            return new RagConfig(5, true, 0.5, 0.015, 0.010, true, true);
        }
    }

    /**
     * Safety constraints (structural, not prompt-based).
     *
     * <p>Principle: "Design the system so that bad outcomes are unreachable"
     * (Habr #1036626).
     */
    public record SafetyConstraints(
            /** Tools that are completely removed (not just forbidden). */
            Set<String> removedTools,
            /** Operations that require human approval. */
            Set<String> gatedOperations,
            /** Maximum autonomy level (0-1, where 0 = full human control). */
            double maxAutonomy,
            /** Whether destructive operations are blocked at structural level. */
            boolean structuralBlocking
    ) {
        public static SafetyConstraints defaults() {
            return new SafetyConstraints(
                    Set.of("delete_database", "drop_table", "format_disk"),
                    Set.of("deploy_production", "modify_ethics", "access_credentials"),
                    0.7,
                    true
            );
        }
    }

    /**
     * Multi-objective fitness score.
     */
    public record FitnessScore(
            /** Quality of outputs (0-1). */
            double quality,
            /** Robustness to edge cases (0-1). */
            double robustness,
            /** Latency performance (0-1, higher = faster). */
            double latency,
            /** Complexity score (0-1, lower = simpler). */
            double complexity,
            /** Overall weighted score. */
            double overall
    ) {
        /**
         * Computes overall score with default weights.
         */
        public static FitnessScore compute(double quality, double robustness,
                                           double latency, double complexity) {
            double overall = 0.4 * quality + 0.3 * robustness
                    + 0.2 * latency + 0.1 * (1.0 - complexity);
            return new FitnessScore(quality, robustness, latency, complexity, overall);
        }
    }

    /**
     * Creates a default initial genome.
     */
    public static AgentGenome defaults() {
        return new AgentGenome(
                "genome-initial-0",
                Collections.emptyMap(),
                List.of("observe", "think", "act", "verify"),
                Map.of(
                        "observe", Set.of("sensor_read", "memory_recall"),
                        "think", Set.of("boolean_reason", "rag_query"),
                        "act", Set.of("effector_execute", "tool_call"),
                        "verify", Set.of("ethical_filter", "convergence_check")
                ),
                MemoryConfig.defaults(),
                RagConfig.defaults(),
                SafetyConstraints.defaults(),
                Status.CANDIDATE,
                null,
                0,
                null
        );
    }

    /**
     * Creates a mutated copy of this genome with new prompt patches.
     */
    public AgentGenome withPromptPatch(String stage, String patch) {
        var newPatches = new java.util.HashMap<>(promptPatches);
        newPatches.put(stage, patch);
        return new AgentGenome(
                id + "-mut-" + System.currentTimeMillis(),
                Collections.unmodifiableMap(newPatches),
                stageOrder, toolSets, memoryConfig, ragConfig, safetyConstraints,
                Status.CANDIDATE, null, generation + 1, id
        );
    }

    /**
     * Creates a mutated copy with reordered stages.
     */
    public AgentGenome withStageOrder(List<String> newOrder) {
        return new AgentGenome(
                id + "-mut-" + System.currentTimeMillis(),
                promptPatches,
                List.copyOf(newOrder),
                toolSets, memoryConfig, ragConfig, safetyConstraints,
                Status.CANDIDATE, null, generation + 1, id
        );
    }

    /**
     * Creates a copy with updated fitness and status.
     */
    public AgentGenome withFitness(FitnessScore score) {
        return new AgentGenome(
                id, promptPatches, stageOrder, toolSets,
                memoryConfig, ragConfig, safetyConstraints,
                Status.EVALUATED, score, generation, parentId
        );
    }

    /**
     * Creates a copy with updated status.
     */
    public AgentGenome withStatus(Status newStatus) {
        return new AgentGenome(
                id, promptPatches, stageOrder, toolSets,
                memoryConfig, ragConfig, safetyConstraints,
                newStatus, fitness, generation, parentId
        );
    }
}
