package io.matrix.pilot.minecraft;

/**
 * Result of a step: the action the agent decided to take.
 *
 * @param action action name (e.g. "MOVE_FORWARD", "JUMP", "MINE", "PLACE")
 * @param dx     X-axis movement delta
 * @param dy     Y-axis movement delta
 * @param dz     Z-axis movement delta
 * @param tool   optional tool/hand slot selection (null if none)
 */
public record ActionResult(
        String action,
        double dx,
        double dy,
        double dz,
        String tool) {

    /** Create a movement-only action result. */
    public static ActionResult move(String action, double dx, double dy, double dz) {
        return new ActionResult(action, dx, dy, dz, null);
    }

    /** Create an action with tool selection. */
    public static ActionResult use(String action, String tool) {
        return new ActionResult(action, 0.0, 0.0, 0.0, tool);
    }

    /** Stand still. */
    public static ActionResult idle() {
        return new ActionResult("IDLE", 0.0, 0.0, 0.0, null);
    }
}
