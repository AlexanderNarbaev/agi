package io.matrix.brain.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * TRUE-W11 iteration #6 — Spelke / Spitz-Sokolov core-knowledge guard.
 *
 * <p>Three innate physical primitives per Spelke's object-perception
 * theory:</p>
 * <ol>
 *   <li><b>Object permanence</b>: objects persist through time, so a
 *       mind reply that says "the object vanished" without explanation
 *       is suspect.</li>
 *   <li><b>Continuity</b>: objects move on continuous paths; replies
 *       that claim teleportation are suspect.</li>
 *   <li><b>Contact</b>: objects act on each other only through contact;
 *       replies that claim long-range force without a carrier are
 *       suspect.</li>
 * </ol>
 */
public final class CoreKnowledgeGuard {

    public enum Violation {
        NONE,
        VANISH_OBJECT,         // object appears/disappears without explanation
        TELEPORTATION,        // object moves discontinuously
        ACTION_AT_DISTANCE    // forces act without contact carrier
    }

    // Pure object-permanence violations (no movement claim).
    private static final Set<String> VANISH_WORDS = Set.of(
        "vanishes", "vanished", "vanish",
        "poof", "magically appeared", "out of thin air");
    // Discontinuous-movement claims.
    private static final Set<String> TELEPORT_WORDS = Set.of("teleport", "teleported");

    private static final Set<String> DISTANT_FORCE_PHRASES = Set.of(
        "without touching", "from across the room",
        "telekinetically", "by magic alone");

    private int totalChecks = 0;
    private final Map<Violation, Integer> counts = new LinkedHashMap<>();

    public CoreKnowledgeGuard() {
        for (Violation v : Violation.values()) counts.put(v, 0);
    }

    /** Returns the first violation found, or NONE. */
    public Violation check(String reply) {
        totalChecks++;
        if (reply == null || reply.isBlank()) return Violation.NONE;
        String lower = reply.toLowerCase();
        // Teleportation (movement) takes priority over pure vanishing.
        for (String w : TELEPORT_WORDS) {
            if (lower.contains(w)) {
                counts.merge(Violation.TELEPORTATION, 1, Integer::sum);
                return Violation.TELEPORTATION;
            }
        }
        for (String w : VANISH_WORDS) {
            if (lower.contains(w)) {
                counts.merge(Violation.VANISH_OBJECT, 1, Integer::sum);
                return Violation.VANISH_OBJECT;
            }
        }
        for (String p : DISTANT_FORCE_PHRASES) {
            if (lower.contains(p)) {
                counts.merge(Violation.ACTION_AT_DISTANCE, 1, Integer::sum);
                return Violation.ACTION_AT_DISTANCE;
            }
        }
        counts.merge(Violation.NONE, 1, Integer::sum);
        return Violation.NONE;
    }

    public int totalChecks() { return totalChecks; }
    public Map<Violation, Integer> counts() { return counts; }
}
