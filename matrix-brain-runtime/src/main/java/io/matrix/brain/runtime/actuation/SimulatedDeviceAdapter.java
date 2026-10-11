package io.matrix.brain.runtime.actuation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * W35.2 — an in-memory device used to test the actuation path end to end.
 *
 * <h2>Why a simulated device rather than a mock</h2>
 *
 * <p>A mock stubs out the thing under test. This is the opposite: it is a complete
 * {@link DeviceAdapter} with real state, real capability rules and a real receipt log, so the
 * tests exercise the bus's routing, gating and expiry logic rather than a set of stubbed
 * expectations that agree with whatever the implementation happened to do.</p>
 *
 * <h2>It still has to say no</h2>
 *
 * <p>The adapter refuses actions it does not support rather than pretending to perform them.
 * A simulator that accepted everything would let a routing bug look like success, which is the
 * failure this whole component exists to make visible.</p>
 *
 * <p>No clock and no I/O: state changes are recorded, never enacted. Nothing here can touch
 * real hardware, which is what makes it safe to run in CI.</p>
 */
public final class SimulatedDeviceAdapter implements DeviceAdapter {

    /** Actions this device pretends to implement. */
    private static final List<String> CAPABILITIES = List.of(
            "on", "off", "toggle", "set", "dim", "brighten", "read");

    private final String id;
    private final List<DeviceCommand> receipts = new ArrayList<>();
    private String state = "off";

    public SimulatedDeviceAdapter(String deviceId) {
        this.id = deviceId.toLowerCase(Locale.ROOT);
    }

    @Override
    public String deviceId() {
        return id;
    }

    @Override
    public boolean supports(DeviceCommand command) {
        return CAPABILITIES.contains(command.action().toLowerCase(Locale.ROOT));
    }

    @Override
    public Outcome execute(DeviceCommand command) {
        String action = command.action().toLowerCase(Locale.ROOT);
        if (!supports(command)) {
            return Outcome.refused("device '" + id + "' does not support '" + action + "'");
        }
        receipts.add(command);
        switch (action) {
            case "on", "brighten" -> state = "on";
            case "off" -> state = "off";
            case "toggle" -> state = state.equals("on") ? "off" : "on";
            case "set", "dim" -> state = "on";
            default -> { /* read leaves state untouched */ }
        }
        return Outcome.ok("device '" + id + "' performed '" + action + "'; state=" + state);
    }

    /** The device's current state, as a real adapter would report it. */
    public String state() {
        return state;
    }

    /** Every command this device actually performed, in order. */
    public List<DeviceCommand> receipts() {
        return List.copyOf(receipts);
    }
}