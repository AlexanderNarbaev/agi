package io.matrix.contract;

import java.util.List;
import java.util.Map;

/**
 * W51 — the declaration of a capability the system can host.
 *
 * <p><b>What this replaces.</b> Nothing: the repository has no plugin architecture. The
 * W51 reconnaissance established that there is no {@code ServiceLoader}, no
 * {@code META-INF/services}, no extension point and no classpath scanning in any module — the
 * only registries that exist ({@code ActionRegistry}, {@code AlgorithmRegistry},
 * {@code SignalModuleRegistry}, {@code DocumentExtractorRegistry}, the {@code Tool} contract)
 * are populated by hard-coded constructors inside their own modules, so none of them can be
 * extended from outside the jar. This record is the declaration that such an extension would
 * have to present.</p>
 *
 * <p><b>The lifecycle is explicit and it is the plugin's own.</b> {@link Stage} names where the
 * plugin is in its life. A plugin that is not {@link Stage#ACTIVE} registers no service and
 * handles no event; a plugin that is {@link Stage#REVOKED} has been shut down and left
 * nothing behind. The state is a property of the plugin, not of the registry that holds it,
 * so a reader of a plugin record always knows what it is doing.</p>
 *
 * <p><b>Capabilities are declared and separated from what is granted.</b>
 * {@link #declaredCapabilities()} is what the plugin asks for;
 * {@link #grantedCapabilities()} is what the runtime actually gave it. A plugin that tries to
 * use a capability it did not receive is refused at the call, not at load time — because the
 * grant may be narrower than the request, and the runtime may revoke it later.</p>
 *
 * <p><b>The isolation is declared.</b> {@link #isolation()} says how the artefact is kept away
 * from the runtime. This matters because the repository cannot claim a Java classloader is a
 * sandbox: a classloader is a namespace, not a boundary. An artefact whose isolation is
 * {@link Isolation#NONE} is executed inside the runtime's own trust domain and is therefore
 * restricted to what the runtime itself is allowed to do; an artefact whose isolation is
 * {@link Isolation#DECLARATIVE} runs inside a closed interpreter with no I/O at all.</p>
 *
 * <p><b>Nothing a plugin declares can widen its own authority.</b> The manifest says what the
 * plugin needs; the runtime decides what it gets. That asymmetry is the whole of the safety
 * story, and it is why {@link #grantedCapabilities()} is a separate field from
 * {@link #declaredCapabilities()}.</p>
 *
 * @param id                  the identity of the plugin
 * @param version             the revision number of the plugin
 * @param name                what the plugin is called
 * @param purpose             what the plugin is for, in one honest sentence
 * @param inputContract       the contract of what the plugin consumes
 * @param outputContract      the contract of what the plugin produces
 * @param declaredCapabilities the capabilities the plugin asks for
 * @param grantedCapabilities the capabilities the runtime granted it
 * @param dependencies        the plugins this one needs
 * @param isolation           how the artefact is kept away from the runtime
 * @param budget              the execution budget of the plugin
 * @param artifactDigest      the digest of the artefact the plugin was validated against
 * @param stage               where the plugin is in its lifecycle
 * @param validationIds       the identities of the validations the plugin passed
 * @param provenance          where the plugin came from
 * @param revocationPath      how the plugin is revoked and undone
 */
public record Plugin(
        String id,
        int version,
        String name,
        String purpose,
        String inputContract,
        String outputContract,
        List<String> declaredCapabilities,
        List<String> grantedCapabilities,
        List<String> dependencies,
        Isolation isolation,
        Budget budget,
        String artifactDigest,
        Stage stage,
        List<String> validationIds,
        Provenance provenance,
        String revocationPath) {

    /** Where a plugin is in its lifecycle. */
    public enum Stage {
        /** Proposed; nothing has been built. */
        PROPOSED,
        /** The artefact is being produced. */
        BUILDING,
        /** The artefact exists and runs inside an isolation boundary. */
        SANDBOXED,
        /** The artefact passed its declared validation. */
        VALIDATED,
        /** The artefact runs but its results are not yet used. */
        SHADOW,
        /** The artefact's results are used. */
        ACTIVE,
        /** The artefact failed and is not usable. */
        FAILED,
        /** The artefact was withdrawn; its registrations are gone. */
        REVOKED,
        /** The artefact was retired on purpose. */
        RETIRED
    }

    /** How an artefact is kept away from the runtime. */
    public enum Isolation {
        /** The artefact runs inside the runtime's own trust domain. */
        NONE,
        /** The artefact is a declarative program run by a closed interpreter with no I/O. */
        DECLARATIVE,
        /** The artefact runs in a separate process with its own resource limits. */
        PROCESS
    }

    /**
     * The execution budget of a plugin.
     *
     * @param maxInvocationsPerEpisode how many times the plugin may be invoked in one episode
     * @param maxWallClockMillis       how long one invocation may take
     * @param maxMemoryBytes           how much memory one invocation may use
     */
    public record Budget(int maxInvocationsPerEpisode, long maxWallClockMillis,
                         long maxMemoryBytes) {
        public Budget {
            if (maxInvocationsPerEpisode < 0) {
                throw new IllegalArgumentException("invocation budget must not be negative");
            }
            if (maxWallClockMillis < 0L) {
                throw new IllegalArgumentException("wall-clock budget must not be negative");
            }
            if (maxMemoryBytes < 0L) {
                throw new IllegalArgumentException("memory budget must not be negative");
            }
        }
    }

    public Plugin {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("plugin id must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("plugin version must be at least one");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("plugin name must not be blank");
        }
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("plugin purpose must not be blank");
        }
        if (inputContract == null || inputContract.isBlank()) {
            throw new IllegalArgumentException("plugin input contract must not be blank");
        }
        if (outputContract == null || outputContract.isBlank()) {
            throw new IllegalArgumentException("plugin output contract must not be blank");
        }
        declaredCapabilities = declaredCapabilities == null ? List.of() : List.copyOf(declaredCapabilities);
        grantedCapabilities = grantedCapabilities == null ? List.of() : List.copyOf(grantedCapabilities);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        validationIds = validationIds == null ? List.of() : List.copyOf(validationIds);
        if (isolation == null) {
            throw new IllegalArgumentException("plugin isolation must not be null");
        }
        if (stage == null) {
            throw new IllegalArgumentException("plugin stage must not be null");
        }
        if (budget == null) {
            budget = new Budget(0, 0L, 0L);
        }
    }

    /**
     * True when the plugin holds a capability.
     *
     * @param capability the capability to check
     * @return true when the runtime granted it
     */
    public boolean holds(String capability) {
        return grantedCapabilities.contains(capability);
    }

    /**
     * True when the plugin declared a capability it was not granted.
     *
     * <p>Such a plugin is refused at the call rather than at load time, because the grant may
     * be narrowed later and a plugin that was merely loaded is not yet an offender.</p>
     */
    public boolean requestsUngranted() {
        return !grantedCapabilities.containsAll(declaredCapabilities);
    }

    /** True when the plugin is allowed to serve. */
    public boolean serving() {
        return stage == Stage.ACTIVE || stage == Stage.SHADOW;
    }

    /** True when the plugin has been validated. */
    public boolean validated() {
        return stage == Stage.VALIDATED || stage == Stage.SHADOW || stage == Stage.ACTIVE;
    }

    /**
     * The plugin in a new stage.
     *
     * @param newStage the stage the plugin moved to
     * @return the plugin in that stage
     */
    public Plugin at(Stage newStage) {
        return new Plugin(id, version, name, purpose, inputContract, outputContract,
                declaredCapabilities, grantedCapabilities, dependencies, isolation, budget,
                artifactDigest, newStage, validationIds, provenance, revocationPath);
    }

    /**
     * The plugin with a narrower grant.
     *
     * @param capabilities the capabilities the runtime actually granted
     * @return the plugin with that grant
     */
    public Plugin granting(List<String> capabilities) {
        return new Plugin(id, version, name, purpose, inputContract, outputContract,
                declaredCapabilities, capabilities, dependencies, isolation, budget,
                artifactDigest, stage, validationIds, provenance, revocationPath);
    }

    @Override
    public String toString() {
        return "Plugin[" + id + " v" + version + " " + stage + " isolation=" + isolation + "]";
    }
}
