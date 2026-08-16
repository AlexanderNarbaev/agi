package io.matrix.proxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensorProxyTest {

    private final SensorProxy proxy = new SensorProxy();

    @Nested
    class TextToBits {

        @Test
        void shouldBeDeterministic() {
            long first = proxy.textToBits("hello world");
            long second = proxy.textToBits("hello world");
            assertThat(first).isEqualTo(second);
        }

        @Test
        void shouldBeNonZeroForNonEmptyText() {
            long bits = proxy.textToBits("test");
            assertThat(bits).isNotZero();
        }

        @Test
        void shouldBeZeroForEmptyText() {
            assertThat(proxy.textToBits("")).isZero();
            assertThat(proxy.textToBits("   ")).isZero();
        }

        @Test
        void shouldBeCaseInsensitive() {
            long lower = proxy.textToBits("Hello World");
            long upper = proxy.textToBits("HELLO WORLD");
            assertThat(lower).isEqualTo(upper);
        }

        @Test
        void shouldUseAtMost20Bits() {
            long bits = proxy.textToBits("the quick brown fox jumps over the lazy dog");
            assertThat(bits & ~0xFFFFFL).isZero();
        }

        @Test
        void differentWordsShouldProduceDifferentBits() {
            long hello = proxy.textToBits("hello world foo");
            long world = proxy.textToBits("bar baz qux");
            assertThat(hello).isNotEqualTo(world);
        }

        @Test
        void shouldSetMoreBitsForMoreWords() {
            long one = proxy.textToBits("alpha");
            long two = proxy.textToBits("alpha beta");
            assertThat(Long.bitCount(two)).isGreaterThanOrEqualTo(Long.bitCount(one));
        }

        @Test
        void shouldRejectNull() {
            assertThatThrownBy(() -> proxy.textToBits(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class NumericToBits {

        @Test
        void shouldReturnZeroForValueAtMin() {
            long bits = proxy.numericToBits(0.0, 0.0, 100.0, 20);
            assertThat(bits).isZero();
        }

        @Test
        void shouldReturnAllBitsForValueAtMax() {
            long bits = proxy.numericToBits(100.0, 0.0, 100.0, 20);
            assertThat(Long.bitCount(bits)).isEqualTo(20);
            assertThat(bits & ~0xFFFFFL).isZero();
        }

        @Test
        void shouldReturnCorrectThresholdMapping() {
            // 50% of range → 10 out of 20 levels
            long bits = proxy.numericToBits(50.0, 0.0, 100.0, 20);
            assertThat(Long.bitCount(bits)).isEqualTo(10);
            // Thermometer encoding: bits 0..9 set
            for (int i = 0; i < 10; i++) {
                assertThat((bits >> i) & 1).isEqualTo(1);
            }
            for (int i = 10; i < 20; i++) {
                assertThat((bits >> i) & 1).isEqualTo(0);
            }
        }

        @Test
        void shouldBeMonotonic() {
            long prev = 0;
            for (int v = 0; v <= 100; v += 10) {
                long bits = proxy.numericToBits(v, 0.0, 100.0, 20);
                assertThat(Long.bitCount(bits)).isGreaterThanOrEqualTo(Long.bitCount(prev));
                prev = bits;
            }
        }

        @Test
        void shouldClampOutOfRangeValues() {
            long below = proxy.numericToBits(-50.0, 0.0, 100.0, 20);
            long above = proxy.numericToBits(200.0, 0.0, 100.0, 20);
            assertThat(below).isZero();
            assertThat(Long.bitCount(above)).isEqualTo(20);
        }

        @Test
        void shouldHandleZeroRange() {
            long bits = proxy.numericToBits(5.0, 5.0, 5.0, 20);
            assertThat(bits).isZero();
        }

        @Test
        void shouldClampThresholdsTo20() {
            long bits = proxy.numericToBits(50.0, 0.0, 100.0, 100);
            assertThat(bits & ~0xFFFFFL).isZero();
        }

        @Test
        void shouldRejectZeroOrNegativeThresholds() {
            assertThatThrownBy(() -> proxy.numericToBits(50.0, 0.0, 100.0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> proxy.numericToBits(50.0, 0.0, 100.0, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void defaultThresholdsShouldWork() {
            long bits = proxy.numericToBits(100.0, 0.0, 100.0);
            assertThat(Long.bitCount(bits)).isEqualTo(20);
        }
    }

    @Nested
    class GridToBits {

        @Test
        void shouldReturnZeroForEmptyGrid() {
            int[][] grid = new int[5][5];
            long bits = proxy.gridToBits(grid, 2, 2, 1);
            assertThat(bits).isZero();
        }

        @Test
        void shouldReturnNonZeroForOccupiedGrid() {
            int[][] grid = {
                    {0, 1, 0},
                    {0, 0, 0},
                    {0, 0, 1}
            };
            long bits = proxy.gridToBits(grid, 1, 1, 1);
            assertThat(bits).isNotZero();
            assertThat(bits & ~0xFFFFFL).isZero();
        }

        @Test
        void shouldBeCenterWeighted_SameGridDifferentCenter() {
            int[][] grid = {
                    {1, 0, 0, 0, 0},
                    {0, 0, 0, 0, 0},
                    {0, 0, 0, 0, 0},
                    {0, 0, 0, 0, 0},
                    {0, 0, 0, 0, 0}
            };
            // Center at (0,0) with radius 0 → sees block at (0,0)
            long nearBits = proxy.gridToBits(grid, 0, 0, 0);
            // Center at (4,4) with radius 0 → doesn't see block at (0,0)
            long farBits = proxy.gridToBits(grid, 4, 4, 0);
            assertThat(nearBits).isNotZero();
            assertThat(farBits).isZero();
        }

        @Test
        void shouldHandleOutOfBoundsCenter() {
            int[][] grid = {{1, 0}, {0, 0}};
            long bits = proxy.gridToBits(grid, 10, 10, 1);
            assertThat(bits).isZero();
        }

        @Test
        void largerRadiusShouldSeeMoreBlocks() {
            int[][] grid = {
                    {1, 1, 1},
                    {1, 1, 1},
                    {1, 1, 1}
            };
            long r0 = proxy.gridToBits(grid, 1, 1, 0);
            long r1 = proxy.gridToBits(grid, 1, 1, 1);
            assertThat(Long.bitCount(r1)).isGreaterThan(Long.bitCount(r0));
        }

        @Test
        void shouldRejectNullGrid() {
            assertThatThrownBy(() -> proxy.gridToBits(null, 0, 0, 1))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldRejectNegativeRadius() {
            assertThatThrownBy(() -> proxy.gridToBits(new int[3][3], 1, 1, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class InventoryToBits {

        @Test
        void shouldReturnZeroForEmptyInventory() {
            int[] slots = {0, 0, 0, 0, 0};
            long bits = proxy.inventoryToBits(slots, 5);
            assertThat(bits).isZero();
        }

        @Test
        void shouldEncodeOccupiedSlots() {
            int[] slots = {5, 0, 3, 0, 1};
            long bits = proxy.inventoryToBits(slots, 5);
            // Slots 0, 2, 4 are occupied
            assertThat((bits >> 0) & 1).isEqualTo(1);
            assertThat((bits >> 1) & 1).isEqualTo(0);
            assertThat((bits >> 2) & 1).isEqualTo(1);
            assertThat((bits >> 3) & 1).isEqualTo(0);
            assertThat((bits >> 4) & 1).isEqualTo(1);
        }

        @Test
        void shouldHandleFewerSlotsThanRequested() {
            int[] slots = {1, 0, 1};
            long bits = proxy.inventoryToBits(slots, 10);
            assertThat(Long.bitCount(bits)).isEqualTo(2);
        }

        @Test
        void shouldHashWhenSlotCountExceeds20() {
            int[] slots = new int[30];
            for (int i = 0; i < 30; i++) {
                slots[i] = i + 1; // all occupied
            }
            long bits = proxy.inventoryToBits(slots, 30);
            assertThat(bits & ~0xFFFFFL).isZero();
            assertThat(bits).isNotZero();
        }

        @Test
        void shouldRejectNullSlots() {
            assertThatThrownBy(() -> proxy.inventoryToBits(null, 5))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldTreatZeroCountAsEmpty() {
            int[] slots = {1, 2, 3};
            long bits = proxy.inventoryToBits(slots, 0);
            assertThat(bits).isZero();
        }
    }
}
