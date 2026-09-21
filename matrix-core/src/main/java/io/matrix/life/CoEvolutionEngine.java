package io.matrix.life;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * W1266 — Co-Evolution Engine.
 *
 * Swarm selects best agents; agents teach swarm new tricks.
 * Feedback: Swarm success → biochemical rewards → individual learning.
 */
public final class CoEvolutionEngine {

    public record Agent(int id, double fitness, List<String> skills) {}
    public record SwarmKnowledge(List<String> sharedSkills, double collectiveFitness) {}

    private final List<Agent> agents = new ArrayList<>();
    private final Set<String> swarmKnowledge = new HashSet<>();
    private final Random rng;

    public CoEvolutionEngine(long seed) {
        this.rng = new Random(seed);
    }

    public void addAgent(int id, double fitness, List<String> skills) {
        agents.add(new Agent(id, fitness, new ArrayList<>(skills)));
        swarmKnowledge.addAll(skills);
    }

    public List<Agent> select(int topN) {
        List<Agent> sorted = new ArrayList<>(agents);
        sorted.sort((a, b) -> Double.compare(b.fitness(), a.fitness()));
        return sorted.subList(0, Math.min(topN, sorted.size()));
    }

    public void agentDiscovers(int agentId, String newSkill) {
        for (int i = 0; i < agents.size(); i++) {
            Agent a = agents.get(i);
            if (a.id() == agentId) {
                List<String> updated = new ArrayList<>(a.skills());
                updated.add(newSkill);
                agents.set(i, new Agent(a.id(), a.fitness(), updated));
                swarmKnowledge.add(newSkill);
                return;
            }
        }
    }

    public SwarmKnowledge getSwarmKnowledge() {
        double totalFitness = 0;
        for (Agent a : agents) totalFitness += a.fitness();
        return new SwarmKnowledge(new ArrayList<>(swarmKnowledge), totalFitness);
    }

    public SwarmKnowledge evolve(int generations) {
        for (int gen = 0; gen < generations; gen++) {
            List<Agent> elite = select(agents.size() / 2 + 1);
            for (Agent eliteAgent : elite) {
                swarmKnowledge.addAll(eliteAgent.skills());
            }
            for (int i = 0; i < agents.size(); i++) {
                Agent a = agents.get(i);
                double bonus = a.skills().size() * 0.01;
                agents.set(i, new Agent(a.id(), a.fitness() + bonus, a.skills()));
            }
        }
        return getSwarmKnowledge();
    }

    public int getAgentCount() { return agents.size(); }
    public int getKnowledgeCount() { return swarmKnowledge.size(); }
}
