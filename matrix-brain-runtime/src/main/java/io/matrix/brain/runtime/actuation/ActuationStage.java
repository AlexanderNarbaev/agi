package io.matrix.brain.runtime.actuation;

import io.matrix.brain.runtime.TrueMindCycle;
import java.util.Locale;

/**
 * W35.2 — the bridge from a mind decision to the bus.
 *
 * <h2>Intent extraction is deliberately literal</h2>
 *
 * <p>The bridge recognises a small fixed vocabulary of phrasings and does nothing clever. An
 * intent parser that guesses would sit directly upstream of the only component in the system
 * that can change the physical world, and a guess there is a guess about what to switch off.
 * An unrecognised utterance produces no command, which is visible as a refusal rather than
 * silently doing the wrong thing.</p>
 *
 * <h2>The bridge cannot bypass the gate</h2>
 *
 * <p>It holds a {@link CommandBus} and calls {@link CommandBus#submit}, which is the only
 * public route into the queue. It has no reference to any {@link DeviceAdapter}, so there is
 * no version of this class that can actuate without gating.</p>
 */
public final class ActuationStage {

    private final TrueMindCycle mind;
    private final CommandBus bus;

    public ActuationStage(TrueMindCycle mind, CommandBus bus) {
        this.mind = mind;
        this.bus = bus;
    }

    /** One recognised intention. */
    public record Intent(String device, String action) { }

    /**
     * Interpret a user utterance as an actuation intent.
     *
     * @param utterance what the user said
     * @return the intent, or null when the utterance is not an actuation request — null is a
     *         normal outcome for ordinary conversation and is not an error
     */
    public Intent interpret(String utterance) {
        if (utterance == null || utterance.isBlank()) {
            return null;
        }
        String text = utterance.toLowerCase(Locale.ROOT).trim();

        // Fixed phrasings only. Each maps to exactly one device and action.
        if (text.contains("turn on the light") || text.equals("turn on light")) {
            return new Intent("light", "on");
        }
        if (text.contains("turn off the light") || text.equals("turn off light")) {
            return new Intent("light", "off");
        }
        if (text.contains("toggle the light")) {
            return new Intent("light", "toggle");
        }
        if (text.contains("brighten the light")) {
            return new Intent("light", "brighten");
        }
        if (text.contains("read the sensor")) {
            return new Intent("sensor", "read");
        }
        return null;
    }

    /**
     * Take one utterance through the mind and, if it expresses an actuation intent, offer the
     * resulting command to the bus.
     *
     * <p>The mind runs FIRST. This is not decoration: it means a refusal from the keyword or
     * intent gate can stop an actuation before a command is ever constructed, and the refusal
     * is reported for the utterance rather than for a command the user never saw.</p>
     *
     * @param utterance what the user said
     * @return the gate verdict, or null when the utterance was not an actuation request
     */
    public SafetyGate.Verdict handle(String utterance) {
        var result = mind.think(utterance);
        if (!result.accepted()) {
            // The mind refused outright. No command is constructed, so there is nothing for the
            // gate to evaluate and nothing that could reach a device.
            return SafetyGate.Verdict.refuse(
                    "the mind refused the utterance before any command was constructed");
        }
        Intent intent = interpret(utterance);
        if (intent == null) {
            return null;
        }
        return bus.submit(DeviceCommand.of(intent.device(), intent.action()));
    }
}