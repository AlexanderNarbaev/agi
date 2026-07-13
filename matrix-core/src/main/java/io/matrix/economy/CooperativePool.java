package io.matrix.economy;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.MemoryMXBean;
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
 * <p>Uses {@link OperatingSystemMXBean} for real-time CPU and memory
 * monitoring to enforce resource limits and detect overcommitment.
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

    /**
     * Snapshot of the local system's current resource state.
     *
     * @param availableProcessors number of logical CPUs
     * @param totalMemoryGb       total physical memory in GB
     * @param freeMemoryGb        currently free memory in GB
     * @param systemLoadAverage   system CPU load (0.0–1.0+ per core, -1 if unavailable)
     * @param processCpuLoad      JVM process CPU usage (0.0–1.0, -1 if unavailable)
     */
    public record SystemResourceSnapshot(
            int availableProcessors,
            double totalMemoryGb,
            double freeMemoryGb,
            double systemLoadAverage,
            double processCpuLoad
    ) {}

    private final Map<String, ResourceContribution> contributions = new HashMap<>();
    private final List<ResourceAllocation> allocations = new ArrayList<>();
    private volatile boolean localContributionActive = false;

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
     * Auto-detects local system resources and contributes idle capacity
     * based on actual {@link OperatingSystemMXBean} metrics.
     *
     * <p>Contributes the free memory and estimated idle CPU cores
     * (total cores minus current load). Storage is not auto-detected
     * and defaults to 0.
     *
     * @param instanceId the local instance identifier
     * @return credits earned for the contribution
     */
    public double contributeLocal(String instanceId) {
        SystemResourceSnapshot snapshot = systemResourceSnapshot();
        double idleCpu = Math.max(0,
                snapshot.availableProcessors() - (snapshot.systemLoadAverage() >= 0
                        ? snapshot.systemLoadAverage() : 0));
        double freeMemGb = snapshot.freeMemoryGb();
        localContributionActive = true;
        return contribute(instanceId, idleCpu, freeMemGb, 0);
    }

    /**
     * Allocates resources from the pool to a consumer.
     *
     * <p>When local contributions are active (via {@link #contributeLocal}),
     * allocation is also validated against real system capacity via
     * {@link OperatingSystemMXBean} to prevent overcommitment.
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

        // When local resources are tracked, enforce real system limits
        if (localContributionActive) {
            SystemResourceSnapshot sys = systemResourceSnapshot();
            availableMem = Math.min(availableMem, sys.freeMemoryGb());
            availableCpu = Math.min(availableCpu, sys.availableProcessors());
        }

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

    /**
     * Returns a real-time snapshot of the local system's resource state
     * using {@link OperatingSystemMXBean} and {@link MemoryMXBean}.
     */
    public SystemResourceSnapshot systemResourceSnapshot() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();

        int processors = osBean.getAvailableProcessors();
        double loadAvg = osBean.getSystemLoadAverage();

        // Process CPU load — may return -1 if unsupported
        double processCpuLoad = -1;
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            processCpuLoad = sunOs.getProcessCpuLoad();
        }

        // Memory from MemoryMXBean (heap + non-heap proxy via Runtime)
        Runtime runtime = Runtime.getRuntime();
        double totalMemGb = (double) runtime.maxMemory() / (1024 * 1024 * 1024);
        double freeMemGb = (double) (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory())
                / (1024 * 1024 * 1024);

        return new SystemResourceSnapshot(processors, totalMemGb, freeMemGb, loadAvg, processCpuLoad);
    }

    /**
     * Checks whether the pool can safely allocate the requested resources
     * without exceeding real system limits.
     */
    public boolean canAllocate(double cpuCores, double memoryGb, double storageGb) {
        SystemResourceSnapshot sys = systemResourceSnapshot();
        double availableCpu = contributions.values().stream()
                .mapToDouble(ResourceContribution::cpuCores).sum();
        double availableMem = contributions.values().stream()
                .mapToDouble(ResourceContribution::memoryGb).sum();
        double availableStorage = contributions.values().stream()
                .mapToDouble(ResourceContribution::storageGb).sum();

        return cpuCores <= Math.min(availableCpu, sys.availableProcessors())
                && memoryGb <= Math.min(availableMem, sys.freeMemoryGb())
                && storageGb <= availableStorage;
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
