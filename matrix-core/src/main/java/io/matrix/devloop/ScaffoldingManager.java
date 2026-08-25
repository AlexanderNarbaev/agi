package io.matrix.devloop;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Tracks a per-skill scaffold level that decays with success and raises on failure
 * (SPEC-000#fr-4).
 *
 * <p>Higher level = more scaffolding (more hints/support). The decay schedule is fixed by
 * constants — no wall-clock, no randomness, no per-scenario schedule (SPEC-000#fr-4 requires
 * support to declare a decay schedule; here it is the constant {@link #SUCCESSES_TO_DECAY}).
 *
 * <ul>
 *   <li>On success: increment the consecutive-success streak; after {@link #SUCCESSES_TO_DECAY}
 *       consecutive successes the level decays by one (down to {@link #MIN_LEVEL}) and the
 *       streak resets.</li>
 *   <li>On failure: the level raises by one (up to {@link #MAX_LEVEL}) and the streak resets.</li>
 * </ul>
 */
public final class ScaffoldingManager {

    /** Number of consecutive successes required to decay the scaffold by one level. */
    public static final int SUCCESSES_TO_DECAY = 3;

    /** Initial scaffold level for an unseen skill. */
    public static final int INITIAL_LEVEL = 5;

    /** Floor of the scaffold level. */
    public static final int MIN_LEVEL = 0;

    /** Ceiling of the scaffold level. */
    public static final int MAX_LEVEL = 5;

    private final Map<String, Integer> levels = new HashMap<>();
    private final Map<String, Integer> streaks = new HashMap<>();

    /** Current scaffold level for a skill (defaults to {@link #INITIAL_LEVEL}). */
    public int level(String skill) {
        return levels.getOrDefault(skill, INITIAL_LEVEL);
    }

    /** Record a successful attempt for a skill; may decay the scaffold. */
    public void recordSuccess(String skill) {
        Objects.requireNonNull(skill, "skill");
        int streak = streaks.getOrDefault(skill, 0) + 1;
        if (streak >= SUCCESSES_TO_DECAY) {
            int level = levels.getOrDefault(skill, INITIAL_LEVEL);
            levels.put(skill, Math.max(MIN_LEVEL, level - 1));
            streaks.put(skill, 0);
        } else {
            streaks.put(skill, streak);
        }
    }

    /** Record a failed attempt for a skill; raises the scaffold. */
    public void recordFailure(String skill) {
        Objects.requireNonNull(skill, "skill");
        int level = levels.getOrDefault(skill, INITIAL_LEVEL);
        levels.put(skill, Math.min(MAX_LEVEL, level + 1));
        streaks.put(skill, 0);
    }
}
