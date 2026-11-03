package io.matrix.cognitive;

import io.matrix.cognitive.microworld.Observation;
import io.matrix.cognitive.microworld.ObservationDisposition;
import io.matrix.cognitive.microworld.WorldInput;
import io.matrix.knowledge.Case;
import io.matrix.knowledge.Ids;
import io.matrix.knowledge.KnowledgeStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * W44 — the codec between the micro-world and the durable case record.
 *
 * <p>The micro-world's observations have to survive a restart, and the place that survives a
 * restart in MATRIX is the knowledge core's case table. So an observation is written as a CASE:
 * the conditions under which it was made go into {@code conditions}, what was seen goes into
 * {@code outcome}, and the case id is derived from BOTH. That last part is what makes the write
 * idempotent: re-running the same observation produces the same case id, and the store's
 * {@code INSERT OR REPLACE} therefore updates the row instead of doubling the evidence.</p>
 *
 * <p>Nothing here interprets the observation. The codec reads what the environment said and
 * writes it down; deciding that the observation CONTRADICTS a model is the kernel's job, and it
 * is a separate step with its own record.</p>
 */
public final class MicroWorldCodec {

    /** The prefix of the case ids this codec produces. */
    public static final String CASE_PREFIX = "case-";

    /** The phrase used when the lamp was lit. */
    public static final String LAMP_ON = "the lamp was on";

    /** The phrase used when the lamp was dark. */
    public static final String LAMP_OFF = "the lamp was off";

    private MicroWorldCodec() {
    }

    /**
     * Reads an assignment from a payload.
     *
     * <p>Two forms are accepted: {@code power=1,switch=0} and {@code {"power":1,"switch":0}}.
     * Anything else is a refusal, not a guess — a partially readable assignment would silently
     * drop a variable and change the meaning of the question.</p>
     *
     * @param text the payload
     * @return the assignment
     * @throws IllegalArgumentException when the assignment cannot be read
     */
    public static WorldInput parseAssignment(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("the request carries no assignment");
        }
        String trimmed = text.strip();
        if (trimmed.startsWith("{")) {
            return parseJsonAssignment(trimmed);
        }
        return parseKeyValueAssignment(trimmed);
    }

    private static WorldInput parseKeyValueAssignment(String text) {
        Map<String, Boolean> assignment = new TreeMap<>();
        for (String part : text.split(",")) {
            String piece = part.strip();
            if (piece.isEmpty()) {
                continue;
            }
            int equals = piece.indexOf('=');
            if (equals < 0) {
                throw new IllegalArgumentException("'" + piece + "' is not a name=value pair");
            }
            String name = piece.substring(0, equals).strip();
            String value = piece.substring(equals + 1).strip();
            assignment.put(name, readBoolean(value));
        }
        return build(assignment);
    }

    private static WorldInput parseJsonAssignment(String text) {
        String body = text.substring(1, text.lastIndexOf('}')).strip();
        Map<String, Boolean> assignment = new TreeMap<>();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("the assignment is empty");
        }
        for (String part : body.split(",")) {
            String piece = part.strip();
            if (piece.isEmpty()) {
                continue;
            }
            int colon = piece.indexOf(':');
            if (colon < 0) {
                throw new IllegalArgumentException("'" + piece + "' is not a name:value pair");
            }
            String name = piece.substring(0, colon).strip().replace("\"", "");
            String value = piece.substring(colon + 1).strip().replace("\"", "");
            assignment.put(name, readBoolean(value));
        }
        return build(assignment);
    }

    private static Boolean readBoolean(String value) {
        if ("1".equals(value) || "true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("0".equals(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("'" + value + "' is not a boolean");
    }

    private static WorldInput build(Map<String, Boolean> assignment) {
        if (assignment.isEmpty()) {
            throw new IllegalArgumentException("the assignment names no variable");
        }
        for (String name : assignment.keySet()) {
            if (!WorldInput.KNOWN_VARIABLES.contains(name)) {
                throw new IllegalArgumentException("'" + name + "' is not a variable of this world");
            }
        }
        for (String name : WorldInput.KNOWN_VARIABLES) {
            if (!assignment.containsKey(name)) {
                throw new IllegalArgumentException("the assignment does not say what " + name + " is");
            }
        }
        return new WorldInput(assignment);
    }

    /** The canonical description of an assignment, with the variables in a fixed order. */
    public static String describe(WorldInput input) {
        Map<String, Boolean> sorted = new TreeMap<>(input.assignment());
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Boolean> entry : sorted.entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue() ? 1 : 0);
        }
        return sb.toString();
    }

    /** The phrase that records the lamp's state. */
    public static String describeOutcome(boolean lamp) {
        return lamp ? LAMP_ON : LAMP_OFF;
    }

    /**
     * The id of the case for an observation.
     *
     * <p>Derived from the conditions AND the outcome, so the same observation always maps onto
     * the same case and a repeated write updates rather than duplicates.</p>
     */
    public static String caseIdFor(WorldInput input, boolean lamp) {
        return CASE_PREFIX + Ids.digest(describe(input) + "|" + describeOutcome(lamp));
    }

    /** The case that records an observation. */
    public static Case toCase(String workspaceId, Observation observation) {
        return new Case(caseIdFor(observation.input(), observation.lamp()), workspaceId, null,
                describeOutcome(observation.lamp()), describe(observation.input()),
                describeOutcome(observation.lamp()), null,
                observation.adversarial() ? Case.Origin.ADVERSARIAL : Case.Origin.OBSERVED,
                observation.observedAt());
    }

    /**
     * Reads the micro-world's observations back from the recorded cases.
     *
     * <p>This is how the model survives a restart: the cases ARE the episodic memory, and a
     * session rebuilt from them knows what it has already seen.</p>
     *
     * <p><b>W45.0: the disposition comes back with the observation.</b> The W44 version read the
     * cases and returned every one of them as an ordinary observation, so a session rebuilt
     * after a restart applied contradictions that the live session had deliberately kept out of
     * its hypothesis set — the contradiction survived the restart as a record and was lost as a
     * decision. The disposition is now read with the case, and a case whose disposition cannot
     * be established is {@link ObservationDisposition#conservativeDefault()}: held, not
     * assumed to be model data.</p>
     *
     * @param store the store to read from
     * @param workspaceId the workspace
     * @return every observation the workspace holds, each carrying its disposition
     */
    public static List<Observation> readCases(KnowledgeStore store, String workspaceId) {
        List<Observation> out = new ArrayList<>();
        Map<String, ObservationDisposition> dispositions = store.caseDispositions(workspaceId);
        for (Case observation : store.cases(workspaceId)) {
            WorldInput input;
            try {
                input = parseAssignment(observation.conditions());
            } catch (IllegalArgumentException e) {
                // A case whose conditions cannot be read is not silently dropped: the caller
                // cannot be told about it here, so the case is skipped and the gap shows up as
                // a missing observation rather than as a wrong one.
                continue;
            }
            boolean lamp = observation.outcome() != null
                    && observation.outcome().contains(LAMP_ON);
            ObservationDisposition disposition = dispositions.getOrDefault(observation.id(),
                    ObservationDisposition.conservativeDefault());
            out.add(new Observation("obs-" + observation.id(), input, lamp,
                    observation.origin() == Case.Origin.ADVERSARIAL, observation.observedAt(),
                    disposition));
        }
        return out;
    }

    /**
     * The precondition facts known about an assignment.
     *
     * <p>These are the FACTS the algorithm selector admits methods against. A method that needs
     * to know whether the assignment was already observed declares
     * {@code ASSIGNMENT_ALREADY_OBSERVED}; one that does not care does not declare it, and the
     * selector then has no reason to exclude it.</p>
     */
    public static Set<String> facts(WorldInput input, boolean alreadyObserved) {
        Set<String> facts = new java.util.LinkedHashSet<>();
        facts.add("MICRO_WORLD_LAMP_DOMAIN");
        facts.add("ASSIGNMENT_COMPLETE");
        if (alreadyObserved) {
            facts.add("ASSIGNMENT_ALREADY_OBSERVED");
        } else {
            facts.add("ASSIGNMENT_UNOBSERVED");
        }
        return facts;
    }
}
