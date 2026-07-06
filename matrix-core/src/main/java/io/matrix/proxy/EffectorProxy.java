package io.matrix.proxy;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Objects;

/**
 * Converts binary MPDT output commands into external actions.
 * Implements L7 §2 Effector Proxy specification.
 *
 * <p>MPDT output is a 5-bit action code (0-31), mapped to semantic actions.
 */
@ApplicationScoped
public class EffectorProxy {

    /** Action codes 0..31. */
    public static final int ACTION_COUNT = 32;

    private static final String[] ACTION_NAMES = {
            "IDLE",          // 0
            "MOVE_FORWARD",  // 1
            "MOVE_BACKWARD", // 2
            "MOVE_LEFT",     // 3
            "MOVE_RIGHT",    // 4
            "TURN_LEFT",     // 5
            "TURN_RIGHT",    // 6
            "JUMP",          // 7
            "ATTACK",        // 8
            "USE",           // 9
            "PICK_UP",       // 10
            "DROP",          // 11
            "OPEN_DOOR",     // 12
            "CLOSE_DOOR",    // 13
            "CRAFT",         // 14
            "BUILD",         // 15
            "MINE",          // 16
            "PLACE_BLOCK",   // 17
            "EAT",           // 18
            "DRINK",         // 19
            "SLEEP",         // 20
            "WAKE",          // 21
            "TALK",          // 22
            "LISTEN",        // 23
            "SEARCH",        // 24
            "FLEE",          // 25
            "FOLLOW",        // 26
            "TRADE",         // 27
            "GIVE",          // 28
            "PROTECT",       // 29
            "OBSERVE",       // 30
            "RESPOND"        // 31
    };

    /**
     * Converts a 5-bit action code to its action name.
     *
     * @param actionCode action code in {@code [0, 31]}
     * @return action name
     * @throws IllegalArgumentException if actionCode is out of range
     */
    public String bitsToAction(int actionCode) {
        validateActionCode(actionCode);
        return ACTION_NAMES[actionCode];
    }

    /**
     * Converts an action name to a Minecraft command string.
     *
     * @param action action name (from {@link #bitsToAction})
     * @param params optional parameters for the command
     * @return Minecraft command string
     */
    public String actionToMinecraftCommand(String action, Object... params) {
        Objects.requireNonNull(action, "action");
        return switch (action) {
            case "MOVE_FORWARD" -> "move forward";
            case "MOVE_BACKWARD" -> "move backward";
            case "MOVE_LEFT" -> "strafe left";
            case "MOVE_RIGHT" -> "strafe right";
            case "TURN_LEFT" -> "rotate -90";
            case "TURN_RIGHT" -> "rotate 90";
            case "JUMP" -> "jump";
            case "ATTACK" -> "attack";
            case "USE" -> "use";
            case "PICK_UP" -> "pickup";
            case "DROP" -> "drop " + (params.length > 0 ? params[0] : "item");
            case "OPEN_DOOR" -> "open door";
            case "CLOSE_DOOR" -> "close door";
            case "CRAFT" -> "craft " + (params.length > 0 ? params[0] : "item");
            case "BUILD" -> "build " + (params.length > 0 ? params[0] : "structure");
            case "MINE" -> "mine " + (params.length > 0 ? params[0] : "block");
            case "PLACE_BLOCK" -> "place " + (params.length > 0 ? params[0] : "block");
            case "EAT" -> "eat " + (params.length > 0 ? params[0] : "food");
            case "DRINK" -> "drink " + (params.length > 0 ? params[0] : "potion");
            case "SLEEP" -> "sleep";
            case "WAKE" -> "wake";
            case "TALK" -> "say " + (params.length > 0 ? params[0] : "");
            case "LISTEN" -> "listen";
            case "SEARCH" -> "search " + (params.length > 0 ? params[0] : "area");
            case "FLEE" -> "flee";
            case "FOLLOW" -> "follow " + (params.length > 0 ? params[0] : "entity");
            case "TRADE" -> "trade " + (params.length > 0 ? params[0] : "item");
            case "GIVE" -> "give " + (params.length > 0 ? params[0] : "item");
            case "PROTECT" -> "protect " + (params.length > 0 ? params[0] : "entity");
            case "OBSERVE" -> "observe";
            case "RESPOND" -> "respond " + (params.length > 0 ? params[0] : "");
            case "IDLE" -> "idle";
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        };
    }

    /**
     * Converts an action code to a text response for chat API.
     *
     * @param actionCode action code in {@code [0, 31]}
     * @param context   optional context string for the response
     * @return human-readable text response
     */
    public String actionToText(int actionCode, String context) {
        validateActionCode(actionCode);
        String action = ACTION_NAMES[actionCode];
        String ctx = (context != null && !context.isBlank()) ? context : "";
        return switch (action) {
            case "IDLE" -> "I am waiting.";
            case "MOVE_FORWARD" -> "Moving forward" + ctx(ctx, " toward ") + ".";
            case "MOVE_BACKWARD" -> "Stepping back" + ctx(ctx, " from ") + ".";
            case "MOVE_LEFT" -> "Moving left.";
            case "MOVE_RIGHT" -> "Moving right.";
            case "TURN_LEFT" -> "Turning left.";
            case "TURN_RIGHT" -> "Turning right.";
            case "JUMP" -> "Jumping!";
            case "ATTACK" -> "Attacking" + ctx(ctx, " ") + ".";
            case "USE" -> "Using item.";
            case "PICK_UP" -> "Picking up item.";
            case "DROP" -> "Dropping item.";
            case "OPEN_DOOR" -> "Opening door.";
            case "CLOSE_DOOR" -> "Closing door.";
            case "CRAFT" -> "Crafting" + ctx(ctx, " ") + ".";
            case "BUILD" -> "Building" + ctx(ctx, " ") + ".";
            case "MINE" -> "Mining block.";
            case "PLACE_BLOCK" -> "Placing block.";
            case "EAT" -> "Eating.";
            case "DRINK" -> "Drinking.";
            case "SLEEP" -> "Going to sleep.";
            case "WAKE" -> "Waking up.";
            case "TALK" -> "Talking: " + ctx;
            case "LISTEN" -> "Listening" + ctx(ctx, " to ") + ".";
            case "SEARCH" -> "Searching" + ctx(ctx, " for ") + ".";
            case "FLEE" -> "Retreating!";
            case "FOLLOW" -> "Following" + ctx(ctx, " ") + ".";
            case "TRADE" -> "Trading.";
            case "GIVE" -> "Giving item.";
            case "PROTECT" -> "Protecting" + ctx(ctx, " ") + ".";
            case "OBSERVE" -> "Observing surroundings.";
            case "RESPOND" -> "Responding: " + ctx;
            default -> "Unknown action (" + actionCode + ").";
        };
    }

    /**
     * Returns the total number of defined actions.
     *
     * @return action count (always 32)
     */
    public int actionCount() {
        return ACTION_COUNT;
    }

    /**
     * Validates that an action code is in {@code [0, 31]}.
     */
    private void validateActionCode(int actionCode) {
        if (actionCode < 0 || actionCode >= ACTION_COUNT) {
            throw new IllegalArgumentException(
                    "actionCode must be in [0, " + (ACTION_COUNT - 1) + "], got " + actionCode);
        }
    }

    /**
     * Formats a context string with a prefix, or returns empty if no context.
     */
    private static String ctx(String context, String prefix) {
        return context.isBlank() ? "" : prefix + context;
    }
}
