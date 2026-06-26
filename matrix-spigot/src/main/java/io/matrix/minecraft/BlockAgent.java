package io.matrix.minecraft;

import java.util.HashMap;
import java.util.Map;

/**
 * Minecraft-like agent with inventory, tools, health, and hunger.
 *
 * <p>Actions: move, mine, place, craft, eat.
 * Sensors: 5x5 vision grid (25 cells) + internal state (health, hunger, tool).
 * Sensor encoding: 25 vision bits + 4 health bits + 4 hunger bits + 2 tool bits = 35 bits.
 */
public class BlockAgent {

    public enum Direction { N, S, W, E, STAY }

    private BlockWorld.Position position;
    private int health;
    private int maxHealth = 20;
    private int hunger;
    private int maxHunger = 20;
    private BlockType.ToolTier toolTier = BlockType.ToolTier.NONE;
    private final Map<String, Integer> inventory = new HashMap<>();
    private int stepsSurvived;
    private int blocksMined;
    private int itemsCrafted;

    public BlockAgent(BlockWorld.Position startPos) {
        this.position = startPos;
        this.health = maxHealth;
        this.hunger = maxHunger;
        inventory.put("WOOD", 0);
        inventory.put("STONE", 0);
        inventory.put("COAL", 0);
        inventory.put("IRON_ORE", 0);
        inventory.put("IRON_INGOT", 0);
        inventory.put("DIAMOND", 0);
        inventory.put("BREAD", 0);
    }

    public BlockWorld.Position position() { return position; }
    public int health() { return health; }
    public int hunger() { return hunger; }
    public BlockType.ToolTier toolTier() { return toolTier; }
    public int stepsSurvived() { return stepsSurvived; }
    public int blocksMined() { return blocksMined; }
    public int itemsCrafted() { return itemsCrafted; }
    public Map<String, Integer> inventory() { return Map.copyOf(inventory); }
    public boolean isAlive() { return health > 0 && hunger > 0; }

    /**
     * Encodes agent state as a compact 20-bit vector for the neural network.
     *
     * <p>20 bits total:
     * bits 0-8:   5x5 vision cells, each bit = any solid block in that cell (9 bits, center excluded)
     * bit 9:      block ahead is solid
     * bits 10-12: health quartile (0-4)
     * bits 13-15: hunger quartile (0-4)
     * bits 16-18: tool tier (0-4)
     * bit 19:     has food in inventory
     */
    public long encodeSensors(BlockWorld world) {
        long bits = 0;

        int bitIndex = 0;
        int x0 = position.x() - 2;
        int y0 = position.y() - 2;
        for (int dy = 0; dy < 5; dy++) {
            for (int dx = 0; dx < 5; dx++) {
                if (dx == 2 && dy == 2) continue;
                if (world.isSolid(x0 + dx, y0 + dy)) {
                    bits |= (1L << bitIndex);
                }
                bitIndex++;
            }
        }

        if (world.isSolid(position.x(), position.y() - 1)) {
            bits |= (1L << 9);
        }

        int healthQ = Math.min(4, health * 5 / maxHealth);
        for (int i = 0; i < 3; i++) {
            if ((healthQ & (1 << i)) != 0) bits |= (1L << (10 + i));
        }

        int hungerQ = Math.min(4, hunger * 5 / maxHunger);
        for (int i = 0; i < 3; i++) {
            if ((hungerQ & (1 << i)) != 0) bits |= (1L << (13 + i));
        }

        int toolBits = Math.min(4, toolTier.ordinal());
        for (int i = 0; i < 3; i++) {
            if ((toolBits & (1 << i)) != 0) bits |= (1L << (16 + i));
        }

        if (inventory.getOrDefault("BREAD", 0) > 0) {
            bits |= (1L << 19);
        }

        return bits;
    }

    /**
     * Interprets neural network output as an action.
     *
     * <p>3 bits encode action: 0=STAY, 1=N, 2=S, 3=W, 4=E, 5=MINE, 6=CRAFT, 7=EAT.
     */
    public Action decodeAction(int neuralOutput) {
        return switch (neuralOutput & 0x7) {
            case 0 -> new Action.Move(Direction.STAY);
            case 1 -> new Action.Move(Direction.N);
            case 2 -> new Action.Move(Direction.S);
            case 3 -> new Action.Move(Direction.W);
            case 4 -> new Action.Move(Direction.E);
            case 5 -> new Action.Mine();
            case 6 -> new Action.Craft();
            case 7 -> new Action.Eat();
            default -> new Action.Move(Direction.STAY);
        };
    }

    public void move(BlockWorld.Position newPos) {
        this.position = newPos;
        stepsSurvived++;
        hunger = Math.max(0, hunger - 1);
        if (hunger == 0) health--;
    }

    public void mineBlock(BlockWorld world, BlockWorld.Position targetPos) {
        BlockType block = world.get(targetPos);
        if (!block.mineable()) return;

        if (toolTier.ordinal() < block.minTool().ordinal()) return;

        world.set(targetPos.x(), targetPos.y(), BlockType.AIR);
        blocksMined++;
        hunger = Math.max(0, hunger - 1);

        String resource = resourceForBlock(block);
        if (resource != null) {
            inventory.merge(resource, 1, Integer::sum);
        }
    }

    public void eat() {
        if (inventory.getOrDefault("BREAD", 0) > 0) {
            inventory.merge("BREAD", -1, Integer::sum);
            hunger = Math.min(maxHunger, hunger + 10);
            health = Math.min(maxHealth, health + 2);
        }
    }

    public void upgradeTool(String toolName) {
        if (inventory.getOrDefault(toolName, 0) <= 0) return;

        inventory.merge(toolName, -1, Integer::sum);
        toolTier = switch (toolName) {
            case "WOODEN_PICKAXE" -> BlockType.ToolTier.WOOD;
            case "STONE_PICKAXE" -> BlockType.ToolTier.STONE;
            case "IRON_PICKAXE" -> BlockType.ToolTier.IRON;
            case "DIAMOND_PICKAXE" -> BlockType.ToolTier.DIAMOND;
            default -> toolTier;
        };
        itemsCrafted++;
    }

    private String resourceForBlock(BlockType block) {
        return switch (block) {
            case WOOD -> "WOOD";
            case STONE -> "COBBLESTONE";
            case COAL_ORE -> "COAL";
            case IRON_ORE -> "IRON_ORE";
            case GOLD_ORE -> "GOLD_ORE";
            case DIAMOND_ORE -> "DIAMOND";
            case DIRT, GRASS -> null;
            default -> null;
        };
    }

    public sealed interface Action {
        record Move(Direction direction) implements Action {}
        record Mine() implements Action {}
        record Craft() implements Action {}
        record Eat() implements Action {}
    }
}
