package io.matrix.vqvae;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for CodeBook — vector quantization codebook with EMA learning.
 */
class CodeBookTest {

    @Nested
    class Builder {

        @Test
        void shouldBuildWithDefaults() {
            CodeBook book = CodeBook.builder(8).build();
            assertThat(book.codeSize()).isEqualTo(256);
            assertThat(book.dimension()).isEqualTo(8);
        }

        @Test
        void shouldBuildWithCustomCodeSize() {
            CodeBook book = CodeBook.builder(16).codeSize(64).build();
            assertThat(book.codeSize()).isEqualTo(64);
            assertThat(book.dimension()).isEqualTo(16);
        }

        @Test
        void shouldBuildWithCustomMomentum() {
            CodeBook book = CodeBook.builder(8).momentum(0.5).build();
            assertThat(book.momentum()).isEqualTo(0.5);
        }

        @Test
        void shouldRejectZeroDimension() {
            assertThatThrownBy(() -> CodeBook.builder(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectNegativeDimension() {
            assertThatThrownBy(() -> CodeBook.builder(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectInvalidCodeSize() {
            assertThatThrownBy(() -> CodeBook.builder(8).codeSize(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectInvalidMomentum() {
            assertThatThrownBy(() -> CodeBook.builder(8).momentum(0.0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> CodeBook.builder(8).momentum(1.0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> CodeBook.builder(8).momentum(-0.1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Encode {

        private CodeBook book;

        @BeforeEach
        void setUp() {
            book = CodeBook.builder(4).codeSize(8).build();
        }

        @Test
        void shouldReturnValidIndex() {
            double[] input = {1.0, 0.0, 0.0, 0.0};
            int index = book.encode(input);
            assertThat(index).isBetween(0, 7);
        }

        @Test
        void shouldBeDeterministic() {
            double[] input = {0.5, 0.5, 0.5, 0.5};
            int first = book.encode(input);
            int second = book.encode(input);
            assertThat(first).isEqualTo(second);
        }

        @Test
        void shouldRejectNullInput() {
            assertThatThrownBy(() -> book.encode(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldRejectWrongDimension() {
            assertThatThrownBy(() -> book.encode(new double[]{1.0, 2.0}))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldFindNearestCode() {
            // Set up book with known codes
            CodeBook known = CodeBook.builder(2).codeSize(4).build();
            // Manually set codes: [0,0], [1,0], [0,1], [1,1]
            known.setCode(0, new double[]{0.0, 0.0});
            known.setCode(1, new double[]{1.0, 0.0});
            known.setCode(2, new double[]{0.0, 1.0});
            known.setCode(3, new double[]{1.0, 1.0});

            // Input closest to [1,0]
            assertThat(known.encode(new double[]{0.9, 0.1})).isEqualTo(1);
            // Input closest to [0,1]
            assertThat(known.encode(new double[]{0.1, 0.9})).isEqualTo(2);
        }
    }

    @Nested
    class Decode {

        private CodeBook book;

        @BeforeEach
        void setUp() {
            book = CodeBook.builder(4).codeSize(8).build();
        }

        @Test
        void shouldReturnVectorOfCorrectDimension() {
            boolean[] result = book.decode(0);
            assertThat(result).hasSize(4);
        }

        @Test
        void shouldRejectNegativeIndex() {
            assertThatThrownBy(() -> book.decode(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectIndexTooLarge() {
            assertThatThrownBy(() -> book.decode(8))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldConvertToBoolean() {
            // Set code with known values
            book.setCode(0, new double[]{0.7, 0.2, -0.5, 0.0});
            boolean[] result = book.decode(0);
            // Positive → true, non-positive → false
            assertThat(result[0]).isTrue();   // 0.7 > 0
            assertThat(result[1]).isTrue();   // 0.2 > 0
            assertThat(result[2]).isFalse();  // -0.5 <= 0
            assertThat(result[3]).isFalse();  // 0.0 <= 0
        }
    }

    @Nested
    class EmaUpdate {

        @Test
        void shouldUpdateCodeWithEma() {
            CodeBook book = CodeBook.builder(2).codeSize(4).momentum(0.5).build();
            book.setCode(0, new double[]{1.0, 1.0});

            // EMA: code_new = (1-momentum) * code_old + momentum * input
            // = 0.5 * [1.0, 1.0] + 0.5 * [3.0, 3.0] = [2.0, 2.0]
            book.emaUpdate(0, new double[]{3.0, 3.0});

            double[] code = book.getCode(0);
            assertThat(code[0]).isEqualTo(2.0);
            assertThat(code[1]).isEqualTo(2.0);
        }

        @Test
        void shouldNotChangeOtherCodes() {
            CodeBook book = CodeBook.builder(2).codeSize(4).momentum(0.5).build();
            book.setCode(0, new double[]{1.0, 1.0});
            book.setCode(1, new double[]{2.0, 2.0});

            book.emaUpdate(0, new double[]{3.0, 3.0});

            double[] untouched = book.getCode(1);
            assertThat(untouched[0]).isEqualTo(2.0);
            assertThat(untouched[1]).isEqualTo(2.0);
        }

        @Test
        void shouldRejectNullInput() {
            CodeBook book = CodeBook.builder(2).codeSize(4).build();
            assertThatThrownBy(() -> book.emaUpdate(0, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldRejectWrongDimension() {
            CodeBook book = CodeBook.builder(2).codeSize(4).build();
            assertThatThrownBy(() -> book.emaUpdate(0, new double[]{1.0}))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectInvalidIndex() {
            CodeBook book = CodeBook.builder(2).codeSize(4).build();
            assertThatThrownBy(() -> book.emaUpdate(-1, new double[]{1.0, 1.0}))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> book.emaUpdate(4, new double[]{1.0, 1.0}))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ThreadSafety {

        @Test
        void shouldHandleConcurrentEncode() throws InterruptedException {
            CodeBook book = CodeBook.builder(8).codeSize(256).build();
            int threadCount = 10;
            int opsPerThread = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            double[] input = new double[8];
                            for (int d = 0; d < 8; d++) {
                                input[d] = Math.random();
                            }
                            int idx = book.encode(input);
                            if (idx < 0 || idx >= 256) {
                                errors.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();
            assertThat(errors.get()).isZero();
        }

        @Test
        void shouldHandleConcurrentEmaUpdate() throws InterruptedException {
            CodeBook book = CodeBook.builder(4).codeSize(16).momentum(0.1).build();
            int threadCount = 8;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int codeIndex = t % 16;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < 100; i++) {
                            double[] input = new double[]{Math.random(), Math.random(),
                                    Math.random(), Math.random()};
                            book.emaUpdate(codeIndex, input);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            // Verify all codes are still valid (finite values)
            for (int i = 0; i < 16; i++) {
                double[] code = book.getCode(i);
                for (double v : code) {
                    assertThat(Double.isFinite(v)).isTrue();
                }
            }
        }
    }

    @Nested
    class SetAndGetCode {

        @Test
        void shouldSetAndGetCode() {
            CodeBook book = CodeBook.builder(3).codeSize(4).build();
            double[] code = {1.0, 2.0, 3.0};
            book.setCode(0, code);

            double[] retrieved = book.getCode(0);
            assertThat(retrieved).containsExactly(1.0, 2.0, 3.0);
        }

        @Test
        void shouldReturnCopy() {
            CodeBook book = CodeBook.builder(3).codeSize(4).build();
            book.setCode(0, new double[]{1.0, 2.0, 3.0});

            double[] copy = book.getCode(0);
            copy[0] = 999.0;

            // Original should be unchanged
            assertThat(book.getCode(0)[0]).isEqualTo(1.0);
        }

        @Test
        void shouldRejectSetCodeWithWrongDimension() {
            CodeBook book = CodeBook.builder(3).codeSize(4).build();
            assertThatThrownBy(() -> book.setCode(0, new double[]{1.0, 2.0}))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectSetCodeWithInvalidIndex() {
            CodeBook book = CodeBook.builder(3).codeSize(4).build();
            assertThatThrownBy(() -> book.setCode(-1, new double[]{1.0, 2.0, 3.0}))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> book.setCode(4, new double[]{1.0, 2.0, 3.0}))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
