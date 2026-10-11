package io.matrix.brain.runtime.actuation;

/**
 * W35.2 — the seam between the bus and the policy that decides what may actuate.
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@link CommandBus} should depend on the ABSTRACTION of a gate, not on one concrete
 * policy. Two reasons, and the second is the important one.</p>
 *
 * <p>First, a second policy (a stricter fleet-wide denylist, a confirmation broker, a recording
 * gate) can be introduced without the queue learning anything about it.</p>
 *
 * <p>Second, and decisively: a fail-closed implementation that throws must be testable. With
 * the bus typed to the concrete final {@link SafetyGate}, proving that the bus refuses when
 * the gate explodes would require making the production class non-final and subclassable
 * purely for the test — which weakens the production type for the benefit of the test. Typed
 * to this interface, the exploding gate is a legitimate collaborator and the property is
 * provable without touching the real policy.</p>
 */
@FunctionalInterface
public interface CommandGate {

    /**
     * Decide whether one command may proceed.
     *
     * @param command the command to evaluate
     * @return the verdict; implementations must never return null, and a null return is
     *         treated by the bus as a refusal
     */
    SafetyGate.Verdict evaluate(DeviceCommand command);
}
