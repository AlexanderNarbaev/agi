package io.matrix.brain.runtime.actuation;

import io.matrix.brain.runtime.safety.HarmfulIntentClassifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * W35.2 — the interceptor every command must pass before an adapter may see it.
 *
 * <h2>The single invariant</h2>
 *
 * <p>{@link #evaluate} never returns null and has no "allow by default" branch. A command
 * either produces an explicit {@link Verdict#allow()} or an explicit
 * {@link Verdict#refuse(reason)} carrying the reason it was refused. There is no code path in
 * which a command reaches a device without someone having decided to let it.</p>
 *
 * <h2>Fail-closed, and asymmetric on purpose</h2>
 *
 * <p>If an internal check throws, the command is refused. This is the opposite of the usual
 * preference for keeping a service available, and it is the right trade here: a safety gate
 * that fails open is not a degraded gate, it is no gate. An availability incident is visible
 * and recoverable; a device that acted on a command nobody vetted is neither.</p>
 *
 * <h2>Ordering matters</h2>
 *
 * <p>Checks run from cheapest and most certain to most inferential: structural validity, then
 * an exact device/action denylist, then a narrow refusal of irreversible actions, then the
 * text-level intent classifier last. A refusal found early reports that reason, so an operator
 * reading the log learns the precise cause rather than "something looked wrong".</p>
 */
public final class SafetyGate implements CommandGate {

    /**
     * Devices this gate will never drive, whatever the action.
     *
     * <p>Exact device identifiers only. Substring or prefix matching would refuse
     * {@code "light-switch-lab"} because it contains "light", which is how a denylist becomes
     * a source of confusing refusals.</p>
     */
    private static final java.util.Set<String> FORBIDDEN_DEVICES = java.util.Set.of(
            "core", "kernel", "bootloader", "bios", "firmware", "self", "mind", "safety");

    /**
     * Actions that are refused regardless of target, because the damage is not reversible by
     * re-issuing a corrected command.
     */
    private static final java.util.Set<String> IRREVERSIBLE_ACTIONS = java.util.Set.of(
            "destroy", "wipe", "erase", "brick", "kill", "terminate", "shutdown",
            "power-off", "uninstall", "format", "decommission");

    /** Actions that change the physical world or access, and always need confirmation. */
    private static final java.util.Set<String> CONFIRMATION_REQUIRED = java.util.Set.of(
            "unlock", "open", "open-door", "unlock-door", "disable-safety", "override",
            "grant", "revoke", "arm", "disarm", "launch", "fire", "spray", "dispense");

    private final HarmfulIntentClassifier intent;

    public SafetyGate() {
        this(HarmfulIntentClassifier.trainedDefault());
    }

    public SafetyGate(HarmfulIntentClassifier intent) {
        this.intent = intent == null ? HarmfulIntentClassifier.unavailable() : intent;
    }

    /**
     * The outcome of gating one command.
     *
     * @param allowed      whether the command may proceed
     * @param reason       why it was refused, or why it needs confirmation; never null
     * @param needsConfirm whether an explicit confirmation is required before dispatch
     */
    public record Verdict(boolean allowed, String reason, boolean needsConfirm) {

        static Verdict allow() {
            return new Verdict(true, "allowed", false);
        }

        static Verdict refuse(String reason) {
            return new Verdict(false, reason, false);
        }

        static Verdict confirmRequired(String reason) {
            return new Verdict(true, reason, true);
        }
    }

    /**
     * Gate one command.
     *
     * @param command the command to evaluate; null is refused rather than throwing
     * @return an explicit verdict, never null
     */
    @Override
    public Verdict evaluate(DeviceCommand command) {
        if (command == null) {
            return Verdict.refuse("no command was supplied");
        }
        try {
            String device = command.deviceId() == null
                    ? "" : command.deviceId().trim().toLowerCase(Locale.ROOT);
            String action = command.action() == null
                    ? "" : command.action().trim().toLowerCase(Locale.ROOT);

            if (device.isBlank()) {
                return Verdict.refuse("command names no device");
            }
            if (action.isBlank()) {
                return Verdict.refuse("command names no action");
            }
            if (FORBIDDEN_DEVICES.contains(device)) {
                return Verdict.refuse(
                        "device '" + device + "' is protected: MATRIX may not actuate itself");
            }
            if (IRREVERSIBLE_ACTIONS.contains(action)) {
                return Verdict.refuse("action '" + action + "' is irreversible");
            }
            if (action.contains("rm") && action.contains("rf")) {
                return Verdict.refuse("action '" + action + "' looks like a recursive force delete");
            }

            // Intent is evaluated last: it is the most inferential check, so letting it
            // override a precise structural refusal would replace a certain reason with a
            // probabilistic one.
            String intentText = device + " " + action + " "
                    + String.join(" ", command.parameters().values());
            if (intent.saysHarmful(intentText)) {
                return Verdict.refuse("harmful intent detected for '" + command.fingerprint() + "'");
            }

            if (CONFIRMATION_REQUIRED.contains(action)) {
                return Verdict.confirmRequired(
                        "action '" + action + "' requires explicit confirmation");
            }
            return Verdict.allow();
        } catch (RuntimeException internalFailure) {
            return Verdict.refuse("safety gate failed closed: " + internalFailure.getMessage());
        }
    }

    /**
     * Evaluate every command, preserving order.
     *
     * @param commands the commands to gate
     * @return one verdict per input, in the same order and the same length
     */
    public List<Verdict> evaluateAll(List<DeviceCommand> commands) {
        List<Verdict> out = new ArrayList<>();
        for (DeviceCommand c : commands) {
            out.add(evaluate(c));
        }
        return out;
    }
}