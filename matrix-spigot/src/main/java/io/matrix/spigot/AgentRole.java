package io.matrix.spigot;

/**
 * Agent role enum for multi-agent swarm.
 *
 * <p>Each role loads different pretrained neuron layers and biases
 * the bot's behavior toward specific tasks.
 */
public enum AgentRole {

    /** Prioritizes mining — uses layers 0,1 for specialized mine neurons. */
    MINER,

    /** Prioritizes crafting — uses layers 2,3 for specialized craft neurons. */
    CRAFTER,

    /** Prioritizes movement — uses layers 4,5 for specialized move neurons. */
    EXPLORER,

    /** Combat-focused bot — uses mix of sensor (0) and action (4) layers. */
    FIGHTER,

    /** Balanced — current default behavior (all layers). */
    GENERALIST;

    /**
     * Returns the pretrained layer indices associated with this role.
     */
    public int[] pretrainedLayers() {
        return switch (this) {
            case MINER -> new int[]{0, 1};
            case CRAFTER -> new int[]{2, 3};
            case EXPLORER -> new int[]{4, 5};
            case FIGHTER -> new int[]{0, 4};   // sensor + action layers for combat
            case GENERALIST -> new int[]{0, 1, 2, 3, 4, 5};
        };
    }

    /**
     * Parses role from a case-insensitive string.
     *
     * @throws IllegalArgumentException if the string does not match any role
     */
    public static AgentRole fromString(String s) {
        for (AgentRole r : values()) {
            if (r.name().equalsIgnoreCase(s)) return r;
        }
        throw new IllegalArgumentException(
                "Unknown role: " + s + ". Valid: " +
                java.util.Arrays.toString(values()));
    }
}
