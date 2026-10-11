package io.matrix.brain.runtime.actuation;

import java.util.Locale;
import java.util.Map;

/**
 * W35.2 — one immutable instruction to one device.
 *
 * <h2>Why this is a value and not a method call</h2>
 *
 * <p>Actuation is the first place MATRIX proposes to change the world outside itself, so the
 * boundary is deliberately narrow. A {@code DeviceCommand} carries no behaviour, opens no
 * resource and reaches no adapter by itself; it can only be carried on a {@link CommandBus},
 * which routes it through a {@link SafetyGate} first. Nothing in the system can execute a
 * device action without passing the gate.</p>
 *
 * <h2>Parsing is strict on purpose</h2>
 *
 * <p>{@link #parse} rejects rather than repairs. A command arrives as text from an inbox, a
 * federation peer or a device transcript, and a torn or padded JSON line is the normal case
 * when a writer is interrupted. Accepting a partially-parsed command would mean executing an
 * instruction whose fields were guesses, so every required field must be present and
 * non-blank, and an unrecognised field is an error rather than something to skip.</p>
 */
public record DeviceCommand(
        String deviceId,
        String action,
        Map<String, String> parameters,
        int priority,
        long ttlMillis) {

    /** Higher runs sooner. Documented as a named constant so the ordering is not folklore. */
    public static final int PRIORITY_CRITICAL = 100;
    public static final int PRIORITY_NORMAL = 50;
    public static final int PRIORITY_LOW = 10;

    public DeviceCommand {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    /** A normal-priority command with no time limit. */
    public static DeviceCommand of(String deviceId, String action) {
        return new DeviceCommand(deviceId, action, Map.of(), PRIORITY_NORMAL, 0L);
    }

    public DeviceCommand withPriority(int p) {
        return new DeviceCommand(deviceId, action, parameters, p, ttlMillis);
    }

    public DeviceCommand withTtl(long millis) {
        return new DeviceCommand(deviceId, action, parameters, priority, millis);
    }

    /**
     * Whether the command asks for a change that affects the physical world or other systems.
     *
     * <p>Every command is actuation — there is no read-only variant, because a device that
     * reports state is a sensor and belongs on the input path. The gate treats all of them as
     * outward-facing.</p>
     */
    public boolean isOutwardFacing() {
        return true;
    }

    /** Stable identity for logging and dedup: device plus action plus sorted parameters. */
    public String fingerprint() {
        StringBuilder sb = new StringBuilder(deviceId).append('/').append(action);
        parameters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(';').append(e.getKey()).append('=').append(e.getValue()));
        return sb.toString();
    }

    /**
     * Parse a strict JSON object literal into a command.
     *
     * <p>Accepted shape, with no other keys permitted:</p>
     * <pre>{"device":"light","action":"on","priority":50,"ttlMillis":0,"params":{}}</pre>
     *
     * <p>This mirrors {@code KnowledgeExchangeProtocol.fromJsonLine}, which rejects torn
     * federation records for the same reason. Rejection is explicit and says which field was
     * wrong, because a silent default here would be a command executed with invented values.</p>
     *
     * @param json the raw line
     * @return the parsed command
     * @throws IllegalArgumentException when the line is blank, malformed, carries an unknown
     *                                  field, or omits a required one — never null
     */
    public static DeviceCommand parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("command line is blank");
        }
        String line = json.trim();
        if (!line.startsWith("{") || !line.endsWith("}")) {
            throw new IllegalArgumentException("command must be a single JSON object, got: " + line);
        }
        Map<String, String> flat = MiniJson.parseFlatObject(line);

        for (String key : flat.keySet()) {
            switch (key) {
                case "device", "action", "priority", "ttlMillis", "params" -> { }
                default -> throw new IllegalArgumentException("unknown field in command: " + key);
            }
        }

        String device = requireNonBlank(flat.get("device"), "device");
        String action = requireNonBlank(flat.get("action"), "action");
        int priority = parseIntOrDefault(flat.get("priority"), PRIORITY_NORMAL, "priority");
        long ttl = parseLongOrDefault(flat.get("ttlMillis"), 0L, "ttlMillis");
        Map<String, String> params = MiniJson.parseNestedStringMap(flat.get("params"));

        return new DeviceCommand(device.toLowerCase(Locale.ROOT), action.toLowerCase(Locale.ROOT),
                params, priority, ttl);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("command field '" + field + "' is missing or blank");
        }
        return value;
    }

    private static int parseIntOrDefault(String raw, int fallback, String field) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "command field '" + field + "' is not a number: " + raw, e);
        }
    }

    private static long parseLongOrDefault(String raw, long fallback, String field) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "command field '" + field + "' is not a number: " + raw, e);
        }
    }

    @Override
    public String toString() {
        return "DeviceCommand{" + fingerprint()
                + ", priority=" + priority
                + ", ttlMillis=" + ttlMillis + '}';
    }
}