package io.matrix.mediator;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class GoldenRatioAllocatorTest {

    private final GoldenRatioAllocator allocator = new GoldenRatioAllocator();

    @Test
    void allocate1000SplitsPrimaryAndExploratory() {
        Map<DriverType, Double> a = allocator.allocate(1000.0);
        double primaryTotal = GoldenRatioAllocator.PRIMARY.stream()
                .mapToDouble(a::get).sum();
        double exploratoryTotal = GoldenRatioAllocator.EXPLORATORY.stream()
                .mapToDouble(a::get).sum();
        assertThat(primaryTotal).isCloseTo(618.0, within(0.01));
        assertThat(exploratoryTotal).isCloseTo(382.0, within(0.01));
    }

    @Test
    void eachPrimaryDriverGetsEqualShare() {
        Map<DriverType, Double> a = allocator.allocate(1000.0);
        double expected = (1000.0 * GoldenRatioAllocator.GOLDEN_RATIO)
                / GoldenRatioAllocator.PRIMARY.size();
        for (DriverType d : GoldenRatioAllocator.PRIMARY) {
            assertThat(a.get(d)).isCloseTo(expected, within(0.0001));
        }
    }

    @Test
    void eachExploratoryDriverGetsEqualShare() {
        Map<DriverType, Double> a = allocator.allocate(1000.0);
        double expected = (1000.0 * GoldenRatioAllocator.COMPLEMENT)
                / GoldenRatioAllocator.EXPLORATORY.size();
        for (DriverType d : GoldenRatioAllocator.EXPLORATORY) {
            assertThat(a.get(d)).isCloseTo(expected, within(0.0001));
        }
    }

    @Test
    void allocationSumsToTotal() {
        double total = 4242.0;
        Map<DriverType, Double> a = allocator.allocate(total);
        double sum = a.values().stream().mapToDouble(Double::doubleValue).sum();
        assertThat(sum).isCloseTo(total, within(0.001));
    }

    @Test
    void allocationCoversAllEightDrivers() {
        Map<DriverType, Double> a = allocator.allocate(100.0);
        assertThat(a).hasSize(8);
        for (DriverType d : DriverType.values()) {
            assertThat(a).containsKey(d);
        }
    }

    @Test
    void allocateZeroBudgetGivesAllZero() {
        Map<DriverType, Double> a = allocator.allocate(0.0);
        assertThat(a.values()).allSatisfy(v ->
                assertThat(v).isCloseTo(0.0, within(0.0)));
    }

    @Test
    void allocateRejectsNegativeBudget() {
        assertThatThrownBy(() -> allocator.allocate(-1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deviationZeroForPerfectGoldenRatioAllocation() {
        Map<DriverType, Double> a = allocator.allocate(1000.0);
        assertThat(allocator.deviation(a)).isCloseTo(0.0, within(0.0001));
    }

    @Test
    void deviationPositiveForFlatAllocation() {
        Map<DriverType, Double> flat = new EnumMap<>(DriverType.class);
        for (DriverType d : DriverType.values()) {
            flat.put(d, 125.0);
        }
        assertThat(allocator.deviation(flat)).isGreaterThan(0.05);
    }

    @Test
    void deviationZeroForEmptyAllocation() {
        assertThat(allocator.deviation(Map.of())).isCloseTo(0.0, within(0.0));
    }

    @Test
    void deviationRejectsNull() {
        assertThatThrownBy(() -> allocator.deviation(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void expectedShareReturnsGoldenRatioForPrimary() {
        assertThat(allocator.expectedShare(GoldenRatioAllocator.PRIMARY))
                .isEqualTo(GoldenRatioAllocator.GOLDEN_RATIO);
        assertThat(allocator.expectedShare(GoldenRatioAllocator.EXPLORATORY))
                .isEqualTo(GoldenRatioAllocator.COMPLEMENT);
    }

    @Test
    void goldenRatioConstantsAreCorrect() {
        assertThat(GoldenRatioAllocator.GOLDEN_RATIO).isCloseTo(0.618, within(0.001));
        assertThat(GoldenRatioAllocator.COMPLEMENT).isCloseTo(0.382, within(0.001));
        assertThat(GoldenRatioAllocator.GOLDEN_RATIO + GoldenRatioAllocator.COMPLEMENT)
                .isCloseTo(1.0, within(0.0));
    }

    @Test
    void primaryAndExploratoryPartitionAllDrivers() {
        assertThat(GoldenRatioAllocator.PRIMARY).hasSize(4);
        assertThat(GoldenRatioAllocator.EXPLORATORY).hasSize(4);
        assertThat(GoldenRatioAllocator.PRIMARY)
                .doesNotContainAnyElementsOf(GoldenRatioAllocator.EXPLORATORY);
    }
}
