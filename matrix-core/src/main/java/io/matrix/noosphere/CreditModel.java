package io.matrix.noosphere;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Noosphere credit model — incentivizes knowledge sharing.
 *
 * <p>Instances earn credits for publishing FNLs to the Noosphere.
 * Credits grant priority access, storage discounts, and voting weight.
 * This is an internal accounting system, not a cryptocurrency.
 *
 * <p>Ref: L6_Memory.md §6.3
 */
public class CreditModel {

    public record InstanceCredits(String instanceId, double balance, int publications,
                                    int downloads, double reputation) {}

    private final Map<String, InstanceCredits> accounts = new HashMap<>();

    private static final double PUBLICATION_REWARD = 10.0;
    private static final double CERTIFIED_BONUS = 5.0;
    private static final double DOWNLOAD_COST = 1.0;

    /**
     * Awards credits for publishing an FNL.
     */
    public double awardPublication(String instanceId, FnlPackage fnl) {
        double reward = PUBLICATION_REWARD;
        if (fnl.certified()) reward += CERTIFIED_BONUS;
        if (fnl.accuracy() > 0.8) reward += 5.0;

        InstanceCredits current = accounts.getOrDefault(instanceId,
                new InstanceCredits(instanceId, 0, 0, 0, 0));

        InstanceCredits updated = new InstanceCredits(
                instanceId,
                current.balance() + reward,
                current.publications() + 1,
                current.downloads(),
                Math.min(1.0, current.reputation() + 0.01));
        accounts.put(instanceId, updated);

        return reward;
    }

    /**
     * Charges credits for downloading an FNL.
     *
     * @return true if download allowed
     */
    public boolean chargeDownload(String instanceId) {
        InstanceCredits current = accounts.getOrDefault(instanceId,
                new InstanceCredits(instanceId, 0, 0, 0, 0));

        if (current.balance() < DOWNLOAD_COST) return false;

        InstanceCredits updated = new InstanceCredits(
                instanceId,
                current.balance() - DOWNLOAD_COST,
                current.publications(),
                current.downloads() + 1,
                current.reputation());
        accounts.put(instanceId, updated);
        return true;
    }

    /**
     * Checks if an instance has sufficient credits for a resource.
     */
    public boolean canAfford(String instanceId, double cost) {
        return accounts.getOrDefault(instanceId,
                new InstanceCredits(instanceId, 0, 0, 0, 0)).balance() >= cost;
    }

    public InstanceCredits getCredits(String instanceId) {
        return accounts.getOrDefault(instanceId,
                new InstanceCredits(instanceId, 0, 0, 0, 0));
    }

    public List<InstanceCredits> allAccounts() { return List.copyOf(accounts.values()); }

    /**
     * Top-N instances by reputation for council voting.
     */
    public List<InstanceCredits> topReputation(int n) {
        return accounts.values().stream()
                .sorted((a, b) -> Double.compare(b.reputation(), a.reputation()))
                .limit(n)
                .toList();
    }
}
