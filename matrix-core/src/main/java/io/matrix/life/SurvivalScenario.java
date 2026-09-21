package io.matrix.life;

import java.util.*;

/**
 * W1241 — Survival Scenario (1000 Minecraft Days).
 *
 * Simulates long-term agent survival: food, health, shelter.
 * Real-time hours of simulation compressed into testable runs.
 */
public final class SurvivalScenario {

    public enum Activity { GATHER, CRAFT, BUILD, EAT, SLEEP, EXPLORE, SOCIALIZE }

    public record SurvivalState(
            int day,
            double health, // 0-100
            double hunger, // 0-100 (100 = starving)
            boolean hasShelter,
            List<String> inventory,
            Map<Activity, Integer> activityLog
    ) {}

    public record DayResult(int day, SurvivalState state, List<Activity> activities) {}

    private final Random rng;
    private SurvivalState currentState;
    private final List<DayResult> history = new ArrayList<>();

    public SurvivalScenario(long seed) {
        this.rng = new Random(seed);
        this.currentState = new SurvivalState(
            0, 100.0, 0.0, false,
            new ArrayList<>(List.of("wooden_pickaxe", "bread")),
            new HashMap<>()
        );
    }

    /**
     * Simulate one Minecraft day.
     */
    public DayResult simulateDay() {
        int day = currentState.day() + 1;
        List<Activity> activities = new ArrayList<>();

        double health = currentState.health();
        double hunger = currentState.hunger() + 0.5; // Gets hungrier each day
        boolean hasShelter = currentState.hasShelter();
        List<String> inventory = new ArrayList<>(currentState.inventory());
        Map<Activity, Integer> activityLog = new HashMap<>(currentState.activityLog());

        // Choose activities based on state
        if (hunger > 70 && inventory.contains("bread")) {
            activities.add(Activity.EAT);
            hunger = Math.max(0, hunger - 30);
            inventory.remove("bread");
        }

        if (!hasShelter && day <= 3) {
            activities.add(Activity.BUILD);
            for (int i = 0; i < 3; i++) activities.add(Activity.GATHER);
            hasShelter = true;
            inventory.add("cobblestone");
        } else {
            activities.add(Activity.GATHER);
            inventory.add("wood");
        }

        // Random exploration
        if (rng.nextDouble() < 0.3) {
            activities.add(Activity.EXPLORE);
            if (rng.nextDouble() < 0.5) {
                inventory.add("iron_ore");
            }
        }

        // Socialize occasionally
        if (day % 10 == 0) {
            activities.add(Activity.SOCIALIZE);
        }

        // Update activity log
        for (Activity a : activities) {
            activityLog.merge(a, 1, Integer::sum);
        }

        // Random health fluctuation (damage from mobs)
        if (!hasShelter && rng.nextDouble() < 0.1) {
            health -= 10;
        }

        currentState = new SurvivalState(day, health, hunger, hasShelter, inventory, activityLog);
        DayResult result = new DayResult(day, currentState, activities);
        history.add(result);
        return result;
    }

    /**
     * Run N days of simulation.
     */
    public List<DayResult> runDays(int days) {
        List<DayResult> results = new ArrayList<>();
        for (int i = 0; i < days && currentState.health() > 0; i++) {
            results.add(simulateDay());
        }
        return results;
    }

    public SurvivalState getCurrentState() { return currentState; }
    public List<DayResult> getHistory() { return new ArrayList<>(history); }

    /**
     * Verify survival: did the agent survive all days?
     */
    public boolean survived(int days) {
        return runDays(days).size() == days && currentState.health() > 0;
    }
}
