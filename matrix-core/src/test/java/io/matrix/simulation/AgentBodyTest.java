package io.matrix.simulation;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.DecisionTree.Leaf;
import io.matrix.neuron.DecisionTree.Split;
import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import static io.matrix.simulation.CellType.*;
import static io.matrix.simulation.Direction.*;
import static org.assertj.core.api.Assertions.assertThat;

class AgentBodyTest {

    @Test
    void agentShouldStartAtGivenPositionWithEnergy() {
        AgentBody agent = new AgentBody(new Position(5, 5), 100);
        assertThat(agent.position()).isEqualTo(new Position(5, 5));
        assertThat(agent.energy()).isEqualTo(100);
    }

    @Test
    void moveShouldUpdatePosition() {
        AgentBody agent = new AgentBody(new Position(5, 5), 100);
        agent.move(N);
        assertThat(agent.position()).isEqualTo(new Position(5, 4));
        agent.move(E);
        assertThat(agent.position()).isEqualTo(new Position(6, 4));
    }

    @Test
    void consumeEnergyShouldDecrease() {
        AgentBody agent = new AgentBody(new Position(0, 0), 50);
        agent.consumeEnergy(5);
        assertThat(agent.energy()).isEqualTo(45);
    }

    @Test
    void addEnergyShouldIncrease() {
        AgentBody agent = new AgentBody(new Position(0, 0), 10);
        agent.addEnergy(20);
        assertThat(agent.energy()).isEqualTo(30);
    }

    @Test
    void isAliveShouldCheckEnergy() {
        AgentBody agent = new AgentBody(new Position(0, 0), 10);
        assertThat(agent.isAlive()).isTrue();
        agent.consumeEnergy(10);
        assertThat(agent.isAlive()).isFalse();
    }

    @Test
    void sensorsShouldEncodeMooreNeighborhood() {
        Grid grid = new Grid(5, 5);

        grid.setCell(new Position(1, 1), WALL);
        grid.setCell(new Position(1, 3), RESOURCE);

        AgentBody agent = new AgentBody(new Position(2, 2), 100);
        long bits = agent.sensors(grid);

        int nwBits = (int) ((bits >> 14) & 0b11);
        int swBits = (int) ((bits >> 10) & 0b11);

        assertThat(nwBits).isEqualTo(WALL.bits());
        assertThat(swBits).isEqualTo(RESOURCE.bits());
    }

    @Test
    void sensorsShouldIncludeEnergyLevel() {
        Grid grid = new Grid(5, 5);
        AgentBody agent = new AgentBody(new Position(2, 2), 80);
        long bits = agent.sensors(grid);

        int energyBits = (int) ((bits >> 16) & 0b11);
        assertThat(energyBits).isEqualTo(0b11);
    }

}
