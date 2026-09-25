package io.matrix.embodied;

import java.util.*;

/**
 * W841 — Sensorimotor Cycle.
 *
 * Implements the perceive → plan → act → feel loop
 * (Sensorimotor Contingency Theory).
 */
public final class SensorimotorCycle {

    public enum Phase { PERCEIVE, PLAN, ACT, FEEL }

    public record SensorReading(String modality, double[] data, long timestamp) {}
    public record MotorCommand(String action, Map<String, Double> params) {}
    public record Feeling(double reward, double surprise, double energy) {}

    public record CycleResult(
            Phase phase,
            SensorReading perception,
            MotorCommand action,
            Feeling feeling,
            boolean success
    ) {}

    private final List<SensorReading> sensoryHistory = new ArrayList<>();
    private final List<MotorCommand> motorHistory = new ArrayList<>();
    private final List<Feeling> feelingHistory = new ArrayList<>();
    private double totalEnergy = 1.0;
    private final Random rng;

    public SensorimotorCycle(long seed) {
        this.rng = new Random(seed);
    }

    /**
     * Run one sensorimotor cycle.
     */
    public CycleResult runCycle(String actionType, Map<String, Double> actionParams) {
        // 1. Perceive
        SensorReading perception = perceive();

        // 2. Plan (simple heuristic)
        MotorCommand command = new MotorCommand(actionType, actionParams);

        // 3. Act
        boolean success = act(command);

        // 4. Feel
        Feeling feeling = feel(success, perception);
        totalEnergy -= feeling.energy();

        CycleResult result = new CycleResult(
            Phase.FEEL, perception, command, feeling, success
        );

        sensoryHistory.add(perception);
        motorHistory.add(command);
        feelingHistory.add(feeling);

        return result;
    }

    private SensorReading perceive() {
        double[] data = new double[10];
        for (int i = 0; i < data.length; i++) {
            data[i] = rng.nextGaussian();
        }
        return new SensorReading("vision", data, System.currentTimeMillis());
    }

    private boolean act(MotorCommand command) {
        // 80% success rate
        return rng.nextDouble() < 0.8;
    }

    private Feeling feel(boolean success, SensorReading perception) {
        double reward = success ? 1.0 : -0.5;
        double surprise = rng.nextGaussian() * 0.5;
        double energy = 0.01;
        return new Feeling(reward, Math.abs(surprise), energy);
    }

    public List<SensorReading> getSensoryHistory() {
        return new ArrayList<>(sensoryHistory);
    }

    public List<MotorCommand> getMotorHistory() {
        return new ArrayList<>(motorHistory);
    }

    public List<Feeling> getFeelingHistory() {
        return new ArrayList<>(feelingHistory);
    }

    public double getTotalEnergy() { return totalEnergy; }
}
