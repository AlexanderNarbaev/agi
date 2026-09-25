package io.matrix.life;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CoEvolutionEngineTest {

    @Test
    void testCreateEngine() {
        CoEvolutionEngine engine = new CoEvolutionEngine(42L);
        assertNotNull(engine);
        assertEquals(0, engine.getAgentCount());
    }

    @Test
    void testAddAgent() {
        CoEvolutionEngine engine = new CoEvolutionEngine(42L);
        engine.addAgent(1, 0.5, List.of("skill-a"));
        assertEquals(1, engine.getAgentCount());
        assertEquals(1, engine.getKnowledgeCount());
    }

    @Test
    void testSelection() {
        CoEvolutionEngine engine = new CoEvolutionEngine(42L);
        engine.addAgent(1, 0.9, List.of());
        engine.addAgent(2, 0.5, List.of());
        engine.addAgent(3, 0.7, List.of());
        var elite = engine.select(2);
        assertEquals(2, elite.size());
        assertEquals(1, elite.get(0).id()); // Highest fitness
        assertEquals(3, elite.get(1).id());
    }

    @Test
    void testSkillPropagation() {
        CoEvolutionEngine engine = new CoEvolutionEngine(42L);
        engine.addAgent(1, 0.5, List.of("existing"));
        engine.agentDiscovers(1, "new-skill");
        assertEquals(2, engine.getKnowledgeCount(), "New skill should be in swarm knowledge");
    }

    @Test
    void testEvolution() {
        CoEvolutionEngine engine = new CoEvolutionEngine(42L);
        engine.addAgent(1, 0.5, List.of("a", "b"));
        engine.addAgent(2, 0.5, List.of("a", "c"));
        var knowledge = engine.evolve(5);
        assertTrue(knowledge.collectiveFitness() > 0);
    }

    @Test
    void testCollectiveFitness() {
        CoEvolutionEngine engine = new CoEvolutionEngine(42L);
        engine.addAgent(1, 0.8, List.of());
        engine.addAgent(2, 0.6, List.of());
        var knowledge = engine.getSwarmKnowledge();
        assertEquals(1.4, knowledge.collectiveFitness(), 0.01);
    }
}
