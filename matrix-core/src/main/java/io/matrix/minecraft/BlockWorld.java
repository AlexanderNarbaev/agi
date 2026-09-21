package io.matrix.minecraft;

import java.util.Random;

/**
 * 2D block world — Minecraft-like sandbox with biomes, ores, and caves.
 *
 * <p>World generation: layers of dirt/stone with ore veins, surface grass,
 * occasional trees and water bodies.
 */
public class BlockWorld {

    public record Position(int x, int y) {
        public Position add(int dx, int dy) { return new Position(x + dx, y + dy); }
    }

    private final int width;
    private final int height;
    private final BlockType[][] blocks;

    public BlockWorld(int width, int height, Random rng) {
        this.width = width;
        this.height = height;
        this.blocks = new BlockType[height][width];
        generate(rng);
    }

    public int width() { return width; }
    public int height() { return height; }

    public BlockType get(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return BlockType.BEDROCK;
        return blocks[y][x];
    }

    public BlockType get(Position pos) { return get(pos.x(), pos.y()); }

    public void set(int x, int y, BlockType type) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            blocks[y][x] = type;
        }
    }

    public boolean isSolid(int x, int y) { return get(x, y).solid(); }

    private void generate(Random rng) {
        int surfaceY = height * 2 / 3;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (y > surfaceY + rng.nextInt(3)) {
                    blocks[y][x] = BlockType.STONE;
                } else if (y > surfaceY - 1) {
                    blocks[y][x] = BlockType.DIRT;
                } else if (y == surfaceY - 1) {
                    blocks[y][x] = rng.nextDouble() < 0.7
                            ? BlockType.GRASS : BlockType.DIRT;
                } else {
                    blocks[y][x] = BlockType.AIR;
                }
            }
        }

        placeOreVeins(rng, BlockType.COAL_ORE, 6, 3, 5);
        placeOreVeins(rng, BlockType.IRON_ORE, 4, 2, 4);
        placeOreVeins(rng, BlockType.GOLD_ORE, 2, 1, 3);
        placeOreVeins(rng, BlockType.DIAMOND_ORE, 1, 1, 2);

        placeTrees(rng, 8);
        placeWater(rng, 3);
        placeLava(rng, 1);
    }

    private void placeOreVeins(Random rng, BlockType ore, int veins,
                                int minSize, int maxSize) {
        for (int v = 0; v < veins; v++) {
            int cx = rng.nextInt(width);
            int cy = height * 2 / 3 + rng.nextInt(height / 3);
            int size = minSize + rng.nextInt(maxSize - minSize + 1);
            for (int i = 0; i < size; i++) {
                int x = cx - 1 + rng.nextInt(3);
                int y = cy - 1 + rng.nextInt(3);
                if (get(x, y) == BlockType.STONE) {
                    set(x, y, ore);
                }
            }
        }
    }

    private void placeTrees(Random rng, int count) {
        int surfaceY = height * 2 / 3;
        for (int t = 0; t < count; t++) {
            int tx = rng.nextInt(width);
            int ty = surfaceY - 1;
            if (get(tx, ty) == BlockType.GRASS) {
                for (int dy = 0; dy < 3 + rng.nextInt(3); dy++) {
                    set(tx, ty - dy - 1, BlockType.WOOD);
                }
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -2; dy <= 0; dy++) {
                        if (rng.nextDouble() < 0.6) {
                            set(tx + dx, ty - 3 - dy, BlockType.LEAVES);
                        }
                    }
                }
            }
        }
    }

    private void placeWater(Random rng, int count) {
        int surfaceY = height * 2 / 3;
        for (int w = 0; w < count; w++) {
            int wx = rng.nextInt(width - 4) + 2;
            int wy = surfaceY - rng.nextInt(2);
            for (int dx = 0; dx < 3; dx++) {
                for (int dy = 0; dy < 2; dy++) {
                    if (get(wx + dx, wy + dy) == BlockType.AIR
                            || get(wx + dx, wy + dy) == BlockType.GRASS) {
                        set(wx + dx, wy + dy, BlockType.WATER);
                    }
                }
            }
        }
    }

    private void placeLava(Random rng, int count) {
        for (int l = 0; l < count; l++) {
            int lx = rng.nextInt(width - 2) + 1;
            int ly = height * 3 / 4 + rng.nextInt(height / 4);
            set(lx, ly, BlockType.LAVA);
            if (rng.nextBoolean()) set(lx + 1, ly, BlockType.LAVA);
        }
    }

    /**
     * Renders the world as ASCII with color codes.
     */
    public String render(Position agentPos, int viewRadius) {
        StringBuilder sb = new StringBuilder();
        int x0 = Math.max(0, agentPos.x() - viewRadius);
        int x1 = Math.min(width - 1, agentPos.x() + viewRadius);
        int y0 = Math.max(0, agentPos.y() - viewRadius);
        int y1 = Math.min(height - 1, agentPos.y() + viewRadius);

        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                if (x == agentPos.x() && y == agentPos.y()) {
                    sb.append('@');
                } else {
                    sb.append(blockChar(get(x, y)));
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private char blockChar(BlockType type) {
        return switch (type) {
            case AIR -> '.';
            case DIRT -> '#';
            case GRASS -> '"';
            case STONE -> 'O';
            case COBBLESTONE -> 'o';
            case COAL_ORE -> 'C';
            case IRON_ORE -> 'I';
            case GOLD_ORE -> 'G';
            case DIAMOND_ORE -> 'D';
            case WOOD -> '|';
            case LEAVES -> '*';
            case WATER -> '~';
            case LAVA -> '!';
            case SAND -> ':';
            case GRAVEL -> ';';
            case BEDROCK -> 'X';
        };
    }
}
