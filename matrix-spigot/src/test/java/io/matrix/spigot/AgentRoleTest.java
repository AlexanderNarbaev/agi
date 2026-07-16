package io.matrix.spigot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AgentRole}.
 */
class AgentRoleTest {

    @Test
    void eachRoleShouldHaveUniqueLayers() {
        // Verify all roles have different layer assignments
        int[][] allLayers = new int[AgentRole.values().length][];
        for (int i = 0; i < AgentRole.values().length; i++) {
            allLayers[i] = AgentRole.values()[i].pretrainedLayers();
        }
        for (int i = 0; i < allLayers.length; i++) {
            for (int j = i + 1; j < allLayers.length; j++) {
                // GENERALIST has all layers, so it's expected to overlap
                if (AgentRole.values()[i] == AgentRole.GENERALIST
                        || AgentRole.values()[j] == AgentRole.GENERALIST) {
                    continue;
                }
                assertThat(allLayers[i])
                        .as("%s vs %s should have different layers",
                                AgentRole.values()[i], AgentRole.values()[j])
                        .isNotEqualTo(allLayers[j]);
            }
        }
    }

    @Test
    void generalistShouldHaveAllLayers() {
        int[] layers = AgentRole.GENERALIST.pretrainedLayers();
        assertThat(layers).containsExactly(0, 1, 2, 3, 4, 5);
    }

    @Test
    void minerShouldUseSensorAndMiningLayers() {
        assertThat(AgentRole.MINER.pretrainedLayers()).containsExactly(0, 1);
    }

    @Test
    void crafterShouldUseCraftingLayers() {
        assertThat(AgentRole.CRAFTER.pretrainedLayers()).containsExactly(2, 3);
    }

    @Test
    void explorerShouldUseMovementLayers() {
        assertThat(AgentRole.EXPLORER.pretrainedLayers()).containsExactly(4, 5);
    }

    @Test
    void fighterShouldUseSensorAndActionLayers() {
        assertThat(AgentRole.FIGHTER.pretrainedLayers()).containsExactly(0, 4);
    }

    @ParameterizedTest
    @CsvSource({
            "MINER, MINER",
            "miner, MINER",
            "MiNeR, MINER",
            "crafter, CRAFTER",
            "explorer, EXPLORER",
            "fighter, FIGHTER",
            "generalist, GENERALIST"
    })
    void fromStringShouldBeCaseInsensitive(String input, AgentRole expected) {
        assertThat(AgentRole.fromString(input)).isEqualTo(expected);
    }

    @Test
    void fromStringShouldThrowForUnknownRole() {
        assertThatThrownBy(() -> AgentRole.fromString("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown role");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "unknown", "123", "MINER "})
    void fromStringShouldRejectInvalid(String input) {
        assertThatThrownBy(() -> AgentRole.fromString(input))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
