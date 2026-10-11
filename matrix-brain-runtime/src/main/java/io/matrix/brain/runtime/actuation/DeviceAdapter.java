package io.matrix.brain.runtime.actuation;

/**
 * W35.2 — the contract every physical or remote device must satisfy.
 *
 * <h2>The anti-corruption boundary</h2>
 *
 * <p>This is the seam between reasoning and the world. Everything above it — the mind, the
 * bus, the gate — speaks in {@link DeviceCommand}s. Everything below it speaks whatever the
 * device actually is: GPIO, HTTP, MQTT, a relay board. Neither side learns the other's
 * vocabulary, so adding a device cannot require changing a single line of reasoning code, and
 * a reasoning bug cannot express itself in device terms.</p>
 *
 * <h2>Implementations must not re-implement safety</h2>
 *
 * <p>An adapter is trusted to translate a command into a device action and to report what
 * happened. It must not make its own decisions about whether a command is acceptable — that is
 * the gate's job, and an adapter that second-guesses would produce two conflicting policies.
 * Where an adapter genuinely cannot comply (an unsupported device, a locked-out relay), it
 * says so by returning a refusal from {@link #execute}, not by silently doing nothing.</p>
 */
public interface DeviceAdapter {

    /**
     * The stable device identifier this adapter drives.
     *
     * @return the identifier commands are routed by, never null
     */
    String deviceId();

    /**
     * Whether this adapter can carry out the given command at all.
     *
     * <p>Capability, not permission: this asks "does this device have that ability", and the
     * gate separately asks "should it be done now". Keeping the two apart is what stops an
     * unsupported action from being reported as a policy refusal.</p>
     *
     * @param command the command to check
     * @return true when the adapter could attempt the action
     */
    boolean supports(DeviceCommand command);

    /**
     * Act on one already-gated command.
     *
     * <p>Callers must have a permitting {@link SafetyGate.Verdict} for this exact command.
     * Implementations should treat an unexpected call as a defect and refuse rather than
     * act.</p>
     *
     * @param command the permitted command
     * @return what the device did
     */
    Outcome execute(DeviceCommand command);

    /**
     * The result of an actuation attempt.
     *
     * @param succeeded whether the device acted
     * @param detail    a human-readable account, always populated so a log entry is never empty
     */
    record Outcome(boolean succeeded, String detail) {

        static Outcome ok(String detail) {
            return new Outcome(true, detail);
        }

        static Outcome refused(String detail) {
            return new Outcome(false, detail);
        }
    }
}