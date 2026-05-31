package io.matrix.minecraft;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class BlockWorldTest {

    @Test
    void shouldGenerateWorld() {
        BlockWorld world = new BlockWorld(40, 30, new Random(42));

        assertThat(world.width()).isEqualTo(40);
        assertThat(world.height()).isEqualTo(30);
    }

    @Test
    void shouldHaveSolidGround() {
        BlockWorld world = new BlockWorld(60, 40, new Random(42));

        int solidCount = 0;
        for (int x = 0; x < 60; x++) {
            if (world.isSolid(x, 39)) solidCount++;
        }

        assertThat(solidCount).isGreaterThan(0);
    }

    @Test
    void shouldHaveResources() {
        BlockWorld world = new BlockWorld(80, 40, new Random(42));

        boolean hasCoal = false, hasIron = false;
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 80; x++) {
                BlockType b = world.get(x, y);
                if (b == BlockType.COAL_ORE) hasCoal = true;
                if (b == BlockType.IRON_ORE) hasIron = true;
            }
        }

        assertThat(hasCoal).isTrue();
        assertThat(hasIron).isTrue();
    }

    @Test
    void shouldRender() {
        BlockWorld world = new BlockWorld(40, 30, new Random(42));
        BlockWorld.Position pos = new BlockWorld.Position(20, 15);

        String rendered = world.render(pos, 5);

        assertThat(rendered).isNotEmpty();
        assertThat(rendered).contains("@");
    }

    @Test
    void borderShouldBeBedrock() {
        BlockWorld world = new BlockWorld(20, 20, new Random(42));

        assertThat(world.get(-1, 0)).isEqualTo(BlockType.BEDROCK);
        assertThat(world.get(99, 0)).isEqualTo(BlockType.BEDROCK);
    }
}
