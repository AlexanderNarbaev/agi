package io.matrix.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cooperative resource pool — shared computing infrastructure.
 *
 * <p>Instances contribute idle resources and earn credits proportional
 * to their contribution. Other instances can consume resources by
 * spending credits. This creates a regenerative cycle where unused
 * capacity benefits the collective.
 *
 * <p>Ref: L8_Roadmap.md §3.9-3
 */
public class CooperativePool {

    public record ResourceContribution(
            String instanceId,
            double cpuCores,
            double memoryGb,
            double storageGb,
            long contributedAt
    ) {}

    public record ResourceAllocation(
            String consumerId,
            double cpuCores,
            double memoryGb,
            double storageGb,
            double creditCost,
            long allocatedAt
    ) {}

    private final Map<String, ResourceContribution> contributions = new HashMap<>();
    private final List<ResourceAllocation> allocations = new ArrayList<>();

    private static final double CREDIT_PER_CPU = 5.0;
    private static final double CREDIT_PER_GB_MEM = 2.0;
    private static final double CREDIT_PER_GB_STORAGE = 0.5;

    /**
     * Contributes idle resources to the pool.
     *
     * @return credits earned for the contribution
     */
    public double contribute(String instanceId, double cpuCores,
                              double memoryGb, double storageGb) {
        double credits = cpuCores * CREDIT_PER_CPU
                + memoryGb * CREDIT_PER_GB_MEM
                + storageGb * CREDIT_PER_GB_STORAGE;

        contributions.put(instanceId,
                new ResourceContribution(instanceId, cpuCores,
                        memoryGb, storageGb, System.currentTimeMillis()));

        return credits;
    }

    /**
     * Allocates resources from the pool to a consumer.
     *
     * @return allocation details, or null if insufficient resources
     */
    public ResourceAllocation allocate(String consumerId, double cpuCores,
                                         double memoryGb, double storageGb) {
        double availableCpu = contributions.values().stream()
                .mapToDouble(ResourceContribution::cpuCores).sum();
        double availableMem = contributions.values().stream()
                .mapToDouble(ResourceContribution::memoryGb).sum();
        double availableStorage = contributions.values().stream()
                .mapToDouble(ResourceContribution::storageGb).sum();

        if (cpuCores > availableCpu || memoryGb > availableMem
                || storageGb > availableStorage) {
            return null;
        }

        double cost = cpuCores * CREDIT_PER_CPU
                + memoryGb * CREDIT_PER_GB_MEM
                + storageGb * CREDIT_PER_GB_STORAGE;

        var allocation = new ResourceAllocation(consumerId, cpuCores,
                memoryGb, storageGb, cost, System.currentTimeMillis());
        allocations.add(allocation);
        return allocation;
    }

    public double totalCpuAvailable() {
        return contributions.values().stream()
                .mapToDouble(ResourceContribution::cpuCores).sum();
    }

    public double totalMemoryAvailable() {
        return contributions.values().stream()
                .mapToDouble(ResourceContribution::memoryGb).sum();
    }

    public List<ResourceContribution> contributions() {
        return List.copyOf(contributions.values());
    }

    public List<ResourceAllocation> allocations() {
        return List.copyOf(allocations);
    }

    public int contributorCount() { return contributions.size(); }
}
