package io.matrix.minecraft;

import java.util.Random;

/**
 * Block types for the Minecraft-like sandbox.
 *
 * <p>Each block has mineable, solid, flammable, and resource properties.
 * Higher-tier blocks require better tools to mine.
 */
@Deprecated(since = "2.2.0", forRemoval = true)
@SuppressWarnings("removal")
public enum BlockType {
    AIR(false, false, false, 0, ToolTier.NONE),
    DIRT(true, true, false, 1, ToolTier.NONE),
    GRASS(true, true, false, 1, ToolTier.NONE),
    STONE(true, true, false, 2, ToolTier.WOOD),
    COBBLESTONE(true, true, false, 2, ToolTier.WOOD),
    COAL_ORE(true, true, false, 1, ToolTier.WOOD),
    IRON_ORE(true, true, false, 3, ToolTier.STONE),
    GOLD_ORE(true, true, false, 3, ToolTier.IRON),
    DIAMOND_ORE(true, true, false, 5, ToolTier.IRON),
    WOOD(true, true, true, 1, ToolTier.NONE),
    LEAVES(true, true, true, 1, ToolTier.NONE),
    WATER(false, false, false, 0, ToolTier.NONE),
    LAVA(false, false, false, 0, ToolTier.NONE),
    SAND(true, true, false, 1, ToolTier.NONE),
    GRAVEL(true, true, false, 1, ToolTier.NONE),
    BEDROCK(false, false, false, 0, ToolTier.NONE);

    private final boolean solid;
    private final boolean mineable;
    private final boolean flammable;
    private final int hardness;
    private final ToolTier minTool;

    BlockType(boolean solid, boolean mineable, boolean flammable,
              int hardness, ToolTier minTool) {
        this.solid = solid;
        this.mineable = mineable;
        this.flammable = flammable;
        this.hardness = hardness;
        this.minTool = minTool;
    }

    public boolean solid() { return solid; }
    public boolean mineable() { return mineable; }
    public boolean flammable() { return flammable; }
    public int hardness() { return hardness; }
    public ToolTier minTool() { return minTool; }

    public enum ToolTier { NONE, WOOD, STONE, IRON, DIAMOND }
}
