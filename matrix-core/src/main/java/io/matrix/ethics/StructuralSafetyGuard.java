package io.matrix.ethics;

import java.util.*;

/**
 * Structural Safety Guard — process-based safety enforcement.
 *
 * <p>Implements the principle: "Design the system so that bad outcomes are unreachable"
 * (Habr #1036626). Unlike prompt-based rules, structural constraints physically prevent
 * forbidden actions rather than asking the agent not to perform them.
 *
 * <p>Three enforcement patterns:
 * <ol>
 *   <li><b>Remove the tool:</b> Forbidden tools are not available to the agent</li>
 *   <li><b>Require human gate:</b> Destructive operations require explicit approval</li>
 *   <li><b>Dial autonomy:</b> Business rule table determines approval requirements</li>
 * </ol>
 *
 * <p>Ref: Research Synthesis 2026-Q3 §2.2
 */
public final class StructuralSafetyGuard {

    /**
     * Operation risk level.
     */
    public enum RiskLevel {
        /** Safe operation, no approval needed. */
        LOW,
        /** Moderate risk, logged but auto-approved. */
        MEDIUM,
        /** High risk, requires human approval. */
        HIGH,
        /** Critical risk, blocked at structural level. */
        CRITICAL
    }

    /**
     * Decision for an operation request.
     */
    public enum Decision {
        /** Operation approved to proceed. */
        APPROVED,
        /** Operation requires human approval. */
        REQUIRES_APPROVAL,
        /** Operation blocked at structural level. */
        BLOCKED
    }

    /**
     * Result of a safety evaluation.
     */
    public record SafetyVerdict(
            Decision decision,
            RiskLevel riskLevel,
            String reason,
            Optional<String> gateId
    ) {
        public static SafetyVerdict approved(RiskLevel risk, String reason) {
            return new SafetyVerdict(Decision.APPROVED, risk, reason, Optional.empty());
        }

        public static SafetyVerdict requiresApproval(String gateId, String reason) {
            return new SafetyVerdict(Decision.REQUIRES_APPROVAL, RiskLevel.HIGH,
                    reason, Optional.of(gateId));
        }

        public static SafetyVerdict blocked(String reason) {
            return new SafetyVerdict(Decision.BLOCKED, RiskLevel.CRITICAL,
                    reason, Optional.empty());
        }
    }

    // ── Configuration ──

    private final Set<String> removedTools;
    private final Set<String> gatedOperations;
    private final Map<String, RiskLevel> riskTable;
    private final double maxAutonomy;
    private final LieDetector lieDetector;

    private StructuralSafetyGuard(Builder builder) {
        this.removedTools = Set.copyOf(builder.removedTools);
        this.gatedOperations = Set.copyOf(builder.gatedOperations);
        this.riskTable = Map.copyOf(builder.riskTable);
        this.maxAutonomy = builder.maxAutonomy;
        this.lieDetector = builder.lieDetector != null
                ? builder.lieDetector : LieDetector.essential();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a default safety guard with standard constraints.
     */
    public static StructuralSafetyGuard defaults() {
        return builder()
                .removeTool("delete_database")
                .removeTool("drop_table")
                .removeTool("format_disk")
                .removeTool("kill_process")
                .removeTool("rm_rf")
                .gateOperation("deploy_production")
                .gateOperation("modify_ethics")
                .gateOperation("access_credentials")
                .gateOperation("modify_safety_constraints")
                .gateOperation("external_api_call")
                .gateOperation("scada.shutdown")
                .riskLevel("read_data", RiskLevel.LOW)
                .riskLevel("write_data", RiskLevel.MEDIUM)
                .riskLevel("delete_data", RiskLevel.HIGH)
                .riskLevel("deploy", RiskLevel.HIGH)
                .riskLevel("modify_config", RiskLevel.MEDIUM)
                .riskLevel("scada.sensor.override", RiskLevel.HIGH)
                .riskLevel("scada.valve.control", RiskLevel.MEDIUM)
                .riskLevel("scada.sensor.read", RiskLevel.LOW)
                .maxAutonomy(0.7)
                .build();
    }

    /**
     * Evaluates whether an operation is allowed.
     *
     * @param operation the operation name
     * @param context   additional context (e.g., environment, target)
     * @return safety verdict
     */
    public SafetyVerdict evaluate(String operation, Map<String, String> context) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(context, "context");

        // Pattern 1: Removed tools — blocked at structural level
        if (removedTools.contains(operation)) {
            return SafetyVerdict.blocked(
                    "Tool '" + operation + "' is structurally removed");
        }

        // Pattern 2: Gated operations — require human approval
        if (gatedOperations.contains(operation)) {
            String gateId = "gate-" + operation + "-" + UUID.randomUUID();
            return SafetyVerdict.requiresApproval(gateId,
                    "Operation '" + operation + "' requires human approval");
        }

        // Pattern 3: Risk-based autonomy dialing
        RiskLevel risk = riskTable.getOrDefault(operation, RiskLevel.MEDIUM);
        return switch (risk) {
            case LOW -> SafetyVerdict.approved(risk,
                    "Low-risk operation approved");
            case MEDIUM -> {
                String env = context.getOrDefault("environment", "development");
                if ("production".equals(env) && maxAutonomy < 0.8) {
                    yield SafetyVerdict.requiresApproval(
                            "gate-" + operation + "-" + UUID.randomUUID(),
                            "Medium-risk operation in production requires approval");
                }
                yield SafetyVerdict.approved(risk,
                        "Medium-risk operation approved in " + env);
            }
            case HIGH -> {
                String gateId = "gate-" + operation + "-" + UUID.randomUUID();
                yield SafetyVerdict.requiresApproval(gateId,
                        "High-risk operation requires human approval");
            }
            case CRITICAL -> SafetyVerdict.blocked(
                    "Critical-risk operation is structurally blocked");
        };
    }

    /**
     * Checks if a tool is available to the agent.
     *
     * @param toolName the tool name
     * @return true if the tool is available (not removed)
     */
    public boolean isToolAvailable(String toolName) {
        return !removedTools.contains(toolName);
    }

    /**
     * Returns the list of available tools from a given set.
     */
    public List<String> filterAvailableTools(Set<String> requestedTools) {
        return requestedTools.stream()
                .filter(this::isToolAvailable)
                .toList();
    }

    /**
     * Returns all removed tools.
     */
    public Set<String> removedTools() {
        return removedTools;
    }

    /**
     * Returns all gated operations.
     */
    public Set<String> gatedOperations() {
        return gatedOperations;
    }

    /**
     * Returns the configured Lie Detector.
     *
     * @since 3.25
     */
    public LieDetector lieDetector() {
        return lieDetector;
    }

    /**
     * Verifies agent output text against the configured {@link LieDetector}.
     *
     * @param output  the textual agent output to verify
     * @param context additional context for verification
     * @return detection result
     * @since 3.25
     */
    public LieDetector.DetectionResult verifyOutput(String output, Map<String, String> context) {
        return lieDetector.detect(output, context);
    }

    /**
     * Verifies agent output text against the configured Lie Detector with no context.
     */
    public LieDetector.DetectionResult verifyOutput(String output) {
        return lieDetector.detect(output);
    }

    /**
     * Builder for {@link StructuralSafetyGuard}.
     */
    public static final class Builder {
        private final Set<String> removedTools = new HashSet<>();
        private final Set<String> gatedOperations = new HashSet<>();
        private final Map<String, RiskLevel> riskTable = new HashMap<>();
        private double maxAutonomy = 0.7;
        private LieDetector lieDetector;

        public Builder removeTool(String tool) {
            removedTools.add(tool);
            return this;
        }

        public Builder gateOperation(String operation) {
            gatedOperations.add(operation);
            return this;
        }

        public Builder riskLevel(String operation, RiskLevel level) {
            riskTable.put(operation, level);
            return this;
        }

        public Builder maxAutonomy(double autonomy) {
            if (autonomy < 0 || autonomy > 1) {
                throw new IllegalArgumentException("autonomy must be 0-1");
            }
            this.maxAutonomy = autonomy;
            return this;
        }

        /**
         * Sets a {@link LieDetector} for post-output verification.
         *
         * @since 3.25
         */
        public Builder lieDetector(LieDetector detector) {
            this.lieDetector = detector;
            return this;
        }

        public StructuralSafetyGuard build() {
            return new StructuralSafetyGuard(this);
        }
    }
}
