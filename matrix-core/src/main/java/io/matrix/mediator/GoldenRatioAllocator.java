package io.matrix.mediator;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Allocates resources using the Golden Ratio (61.8%/38.2%) as specified in
 * L4 section 3.4.
 *
 * <p>61.8% of resources go to the primary drivers (Safety, Energy, Social,
 * Ubuntu) which sustain the system. The remaining 38.2% goes to the
 * exploratory drivers (Curiosity, Entropy, SelfActualization, Attention)
 * which drive growth.
 */
@ApplicationScoped
public class GoldenRatioAllocator {

    public static final double GOLDEN_RATIO = 0.618;
    public static final double COMPLEMENT = 1.0 - GOLDEN_RATIO;

    /** Primary (sustaining) drivers receive the 61.8% share. */
    public static final Set<DriverType> PRIMARY = Set.of(
            DriverType.SAFETY, DriverType.ENERGY,
            DriverType.SOCIAL, DriverType.UBUNTU);

    /** Exploratory (growth) drivers receive the 38.2% share. */
    public static final Set<DriverType> EXPLORATORY = Set.of(
            DriverType.CURIOSITY, DriverType.ENTROPY,
            DriverType.SELFACTUALIZATION, DriverType.ATTENTION);

    /**
     * Allocates compute budget across drivers using the Golden Ratio split.
     *
     * @param totalBudget total available compute (e.g. CPU cycles, neuron slots)
     * @return per-driver allocation; the values sum to {@code totalBudget}
     */
    public Map<DriverType, Double> allocate(double totalBudget) {
        if (totalBudget < 0.0) {
            throw new IllegalArgumentException(
                    "totalBudget must be >= 0.0, got: " + totalBudget);
        }
        double primaryBudget = totalBudget * GOLDEN_RATIO;
        double exploratoryBudget = totalBudget * COMPLEMENT;

        Map<DriverType, Double> allocation = new EnumMap<>(DriverType.class);
        for (DriverType d : PRIMARY) {
            allocation.put(d, primaryBudget / PRIMARY.size());
        }
        for (DriverType d : EXPLORATORY) {
            allocation.put(d, exploratoryBudget / EXPLORATORY.size());
        }
        return allocation;
    }

    /**
     * Measures how far the current driver distribution deviates from the
     * Golden Ratio target. Returns the mean absolute deviation across the
     * two groups (primary vs exploratory share), in [0, 1].
     *
     * <p>A return value of {@code 0.0} means the current allocation matches
     * the Golden Ratio exactly.
     *
     * @param currentAllocation per-driver allocation (need not sum to a fixed total)
     * @return deviation in [0, 1]; 0.0 means a perfect match
     */
    public double deviation(Map<DriverType, Double> currentAllocation) {
        Objects.requireNonNull(currentAllocation, "currentAllocation");

        double total = currentAllocation.values().stream()
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        if (total <= 0.0) {
            return 0.0;
        }

        double primaryShare = 0.0;
        double exploratoryShare = 0.0;
        for (DriverType d : PRIMARY) {
            Double v = currentAllocation.get(d);
            if (v != null) {
                primaryShare += v;
            }
        }
        for (DriverType d : EXPLORATORY) {
            Double v = currentAllocation.get(d);
            if (v != null) {
                exploratoryShare += v;
            }
        }

        double primaryFraction = primaryShare / total;
        double exploratoryFraction = exploratoryShare / total;

        double primaryDeviation = Math.abs(primaryFraction - GOLDEN_RATIO);
        double exploratoryDeviation = Math.abs(exploratoryFraction - COMPLEMENT);
        return Math.max(primaryDeviation, exploratoryDeviation);
    }

    /**
     * Returns the expected share for a driver group.
     *
     * @param group the driver group
     * @return 0.618 for {@link #PRIMARY}, 0.382 for {@link #EXPLORATORY}
     */
    public double expectedShare(Set<DriverType> group) {
        if (group == PRIMARY) {
            return GOLDEN_RATIO;
        }
        if (group == EXPLORATORY) {
            return COMPLEMENT;
        }
        throw new IllegalArgumentException("Unknown driver group: " + group);
    }
}
