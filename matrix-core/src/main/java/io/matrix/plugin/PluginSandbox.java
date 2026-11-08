package io.matrix.plugin;

import java.util.Map;

/**
 * W51 — the boundary a plugin's artefact runs behind.
 *
 * <p><b>Why this interface exists at all.</b> The W51 mandate says: "Prefer a genuine
 * sandbox such as an appropriately configured WASM/WASI runtime or another verified isolation
 * mechanism. Do not describe a Java classloader as a security sandbox." The reconnaissance
 * established that this repository has no sandbox-capable dependency in any of its sixteen
 * build files — no WASM or WASI runtime, no GraalVM Polyglot, no chroot — and that the
 * mechanisms named "sandbox" in the existing code are string filters in front of
 * {@code bash -c}, which are allow-lists, not boundaries.</p>
 *
 * <p>This interface is therefore the seam on which a real isolation mechanism is hung, and
 * the wave ships one implementation of it that needs nothing from the environment: a
 * declarative interpreter whose entire authority is to read a map, compute over numbers,
 * strings, booleans and lists, and write a map. It has no operation that touches a file, a
 * socket, a process, a class loader or a reflection API, because those operations do not
 * exist in it.</p>
 *
 * <p><b>A sandbox that cannot run the artefact must refuse it, not degrade.</b> The
 * {@link SandboxResult.Status#REFUSED} status exists for exactly that, and the W51 failure
 * list — "a corrupted or invalid plugin artifact", "an attempted operation outside granted
 * capabilities" — is a list of refusals.</p>
 *
 * <p><b>The budget is the sandbox's to enforce.</b> The evaluation-step limit is what makes a
 * runaway program stop, and it is what a test measures.</p>
 */
public interface PluginSandbox {

    /**
     * The limits a sandbox enforces.
     *
     * @param maxSteps       the number of evaluation steps the program may perform
     * @param maxWallClockMillis the time the program may take
     */
    record Limits(int maxSteps, long maxWallClockMillis) {
        public Limits {
            if (maxSteps < 1) {
                throw new IllegalArgumentException("the step limit must be at least one");
            }
            if (maxWallClockMillis < 1L) {
                throw new IllegalArgumentException("the time limit must be at least one");
            }
        }
    }

    /**
     * Runs an artefact.
     *
     * @param artifact the artefact to run
     * @param input    the input of the run
     * @param limits   the budget of the run
     * @return what the run produced
     */
    SandboxResult run(PluginArtifact artifact, Map<String, Object> input, Limits limits);

    /**
     * The isolation this sandbox provides.
     *
     * @return the isolation
     */
    io.matrix.contract.Plugin.Isolation isolation();
}
