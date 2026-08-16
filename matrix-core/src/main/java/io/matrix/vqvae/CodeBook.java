package io.matrix.vqvae;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Vector Quantization codebook with EMA (Exponential Moving Average) learning.
 *
 * <p>Maps continuous vectors to discrete code indices and back.
 * Supports 8-bit codes (256 entries by default) with arbitrary vector dimensions.
 *
 * <p>Thread-safe: all operations use read-write locks for concurrent access.
 *
 * <p>EMA formula: {@code code_new = (1-momentum) * code_old + momentum * input}
 */
public class CodeBook {

    private final int dimension;
    private final int codeSize;
    private final double momentum;
    private final double[][] codes;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private CodeBook(Builder builder) {
        this.dimension = builder.dimension;
        this.codeSize = builder.codeSize;
        this.momentum = builder.momentum;
        this.codes = new double[codeSize][dimension];

        // Initialize codes with small random values
        for (int i = 0; i < codeSize; i++) {
            for (int d = 0; d < dimension; d++) {
                codes[i][d] = (Math.random() - 0.5) * 0.1;
            }
        }
    }

    /**
     * Returns the vector dimension.
     */
    public int dimension() {
        return dimension;
    }

    /**
     * Returns the number of codes in the codebook.
     */
    public int codeSize() {
        return codeSize;
    }

    /**
     * Returns the EMA momentum parameter.
     */
    public double momentum() {
        return momentum;
    }

    /**
     * Encodes a continuous vector to the nearest code index.
     *
     * @param input continuous vector (must match dimension)
     * @return code index [0, codeSize)
     * @throws NullPointerException     if input is null
     * @throws IllegalArgumentException if input length != dimension
     */
    public int encode(double[] input) {
        Objects.requireNonNull(input, "input must not be null");
        if (input.length != dimension) {
            throw new IllegalArgumentException(
                    "Input dimension " + input.length + " != codebook dimension " + dimension);
        }

        lock.readLock().lock();
        try {
            int bestIndex = 0;
            double bestDistance = Double.MAX_VALUE;

            for (int i = 0; i < codeSize; i++) {
                double distance = squaredDistance(input, codes[i]);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestIndex = i;
                }
            }

            return bestIndex;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Decodes a code index to a boolean vector.
     *
     * <p>Each component is converted: positive → true, non-positive → false.
     *
     * @param index code index [0, codeSize)
     * @return boolean vector of length dimension
     * @throws IllegalArgumentException if index is out of range
     */
    public boolean[] decode(int index) {
        checkIndex(index);

        lock.readLock().lock();
        try {
            boolean[] result = new boolean[dimension];
            for (int d = 0; d < dimension; d++) {
                result[d] = codes[index][d] > 0;
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Updates a code using Exponential Moving Average.
     *
     * <p>Formula: {@code code_new = (1-momentum) * code_old + momentum * input}
     *
     * @param index code index [0, codeSize)
     * @param input input vector (must match dimension)
     * @throws NullPointerException     if input is null
     * @throws IllegalArgumentException if index is out of range or input dimension mismatch
     */
    public void emaUpdate(int index, double[] input) {
        checkIndex(index);
        Objects.requireNonNull(input, "input must not be null");
        if (input.length != dimension) {
            throw new IllegalArgumentException(
                    "Input dimension " + input.length + " != codebook dimension " + dimension);
        }

        lock.writeLock().lock();
        try {
            for (int d = 0; d < dimension; d++) {
                codes[index][d] = (1 - momentum) * codes[index][d] + momentum * input[d];
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Sets a code to a specific value.
     *
     * @param index code index [0, codeSize)
     * @param code  vector to set (must match dimension)
     * @throws IllegalArgumentException if index is out of range or dimension mismatch
     */
    public void setCode(int index, double[] code) {
        checkIndex(index);
        Objects.requireNonNull(code, "code must not be null");
        if (code.length != dimension) {
            throw new IllegalArgumentException(
                    "Code dimension " + code.length + " != codebook dimension " + dimension);
        }

        lock.writeLock().lock();
        try {
            System.arraycopy(code, 0, codes[index], 0, dimension);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns a copy of the code at the given index.
     *
     * @param index code index [0, codeSize)
     * @return copy of the code vector
     * @throws IllegalArgumentException if index is out of range
     */
    public double[] getCode(int index) {
        checkIndex(index);

        lock.readLock().lock();
        try {
            return Arrays.copyOf(codes[index], dimension);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= codeSize) {
            throw new IllegalArgumentException(
                    "Index " + index + " out of range [0, " + codeSize + ")");
        }
    }

    private static double squaredDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return sum;
    }

    /**
     * Creates a new builder for CodeBook.
     *
     * @param dimension vector dimension (must be > 0)
     * @return new builder
     */
    public static Builder builder(int dimension) {
        return new Builder(dimension);
    }

    /**
     * Builder for CodeBook with fluent API.
     */
    public static class Builder {
        private final int dimension;
        private int codeSize = 256;
        private double momentum = 0.1;

        private Builder(int dimension) {
            if (dimension <= 0) {
                throw new IllegalArgumentException("Dimension must be > 0, got " + dimension);
            }
            this.dimension = dimension;
        }

        /**
         * Sets the number of codes (default: 256).
         */
        public Builder codeSize(int codeSize) {
            if (codeSize <= 0) {
                throw new IllegalArgumentException("Code size must be > 0, got " + codeSize);
            }
            this.codeSize = codeSize;
            return this;
        }

        /**
         * Sets the EMA momentum (default: 0.1).
         *
         * <p>Must be in range (0, 1).
         */
        public Builder momentum(double momentum) {
            if (momentum <= 0 || momentum >= 1) {
                throw new IllegalArgumentException(
                        "Momentum must be in (0, 1), got " + momentum);
            }
            this.momentum = momentum;
            return this;
        }

        /**
         * Builds the CodeBook.
         */
        public CodeBook build() {
            return new CodeBook(this);
        }
    }
}
