package io.matrix.pilot.minecraft;

/**
 * Minecraft world configuration for agent initialization.
 *
 * @param worldType   world generator type ("flat", "default", "amplified")
 * @param seed        world seed for deterministic generation
 * @param renderDistance chunk render distance (2-32)
 * @param tickRate    server tick rate (default 20)
 */
public record WorldConfig(
        String worldType,
        long seed,
        int renderDistance,
        int tickRate) {

    public WorldConfig {
        if (worldType == null || worldType.isBlank()) {
            throw new IllegalArgumentException("worldType must not be blank");
        }
        if (renderDistance < 2 || renderDistance > 32) {
            throw new IllegalArgumentException("renderDistance must be 2-32, got: " + renderDistance);
        }
        if (tickRate < 1 || tickRate > 20) {
            throw new IllegalArgumentException("tickRate must be 1-20, got: " + tickRate);
        }
    }

    /** Flat world with seed 0 and defaults. */
    public static WorldConfig flatDefault() {
        return new WorldConfig("flat", 0L, 10, 20);
    }
}
