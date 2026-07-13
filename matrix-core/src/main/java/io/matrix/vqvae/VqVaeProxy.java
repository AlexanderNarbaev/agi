package io.matrix.vqvae;

import io.matrix.api.Text2VecService;

import java.util.Objects;

/**
 * Multimodal VQ-VAE proxy for sensor/effector integration.
 *
 * <p>Provides two conversion paths:
 * <ul>
 *   <li>Sensor proxy: continuous input → boolean vector (via VQ)</li>
 *   <li>Effector proxy: boolean vector → continuous output (via codebook lookup)</li>
 * </ul>
 *
 * <p>Integrates with {@link Text2VecService} for text-to-boolean conversion.
 */
public class VqVaeProxy {

    private final CodeBook codeBook;
    private final int dimension;
    private final Text2VecService text2VecService;

    private VqVaeProxy(Builder builder) {
        this.dimension = builder.dimension;
        this.codeBook = CodeBook.builder(builder.dimension)
                .codeSize(builder.codeSize)
                .momentum(builder.momentum)
                .build();
        this.text2VecService = builder.text2VecService;
    }

    /**
     * Returns the vector dimension.
     */
    public int dimension() {
        return dimension;
    }

    /**
     * Encodes a continuous vector to a boolean vector via VQ.
     *
     * <p>Steps:
     * <ol>
     *   <li>Find nearest codebook entry</li>
     *   <li>Convert code vector to boolean (positive → true)</li>
     * </ol>
     *
     * @param input continuous vector (must match dimension)
     * @return boolean vector of length dimension
     * @throws NullPointerException     if input is null
     * @throws IllegalArgumentException if input dimension mismatch
     */
    public boolean[] sensorEncode(double[] input) {
        Objects.requireNonNull(input, "input must not be null");
        if (input.length != dimension) {
            throw new IllegalArgumentException(
                    "Input dimension " + input.length + " != proxy dimension " + dimension);
        }

        int codeIndex = codeBook.encode(input);
        return codeBook.decode(codeIndex);
    }

    /**
     * Decodes a boolean vector to a continuous vector.
     *
     * <p>Steps:
     * <ol>
     *   <li>Find nearest codebook entry to the boolean vector</li>
     *   <li>Return the continuous code vector</li>
     * </ol>
     *
     * @param input boolean vector (must match dimension)
     * @return continuous vector of length dimension
     * @throws NullPointerException     if input is null
     * @throws IllegalArgumentException if input dimension mismatch
     */
    public double[] effectorDecode(boolean[] input) {
        Objects.requireNonNull(input, "input must not be null");
        if (input.length != dimension) {
            throw new IllegalArgumentException(
                    "Input dimension " + input.length + " != proxy dimension " + dimension);
        }

        // Convert boolean to double for codebook lookup
        double[] doubleInput = new double[dimension];
        for (int i = 0; i < dimension; i++) {
            doubleInput[i] = input[i] ? 1.0 : 0.0;
        }

        int codeIndex = codeBook.encode(doubleInput);
        return codeBook.getCode(codeIndex);
    }

    /**
     * Converts text to a boolean vector via Text2VecService and VQ.
     *
     * <p>Steps:
     * <ol>
     *   <li>Convert text to binary vector via Text2VecService</li>
     *   <li>Map binary vector to continuous space</li>
     *   <li>Encode via VQ to boolean vector</li>
     * </ol>
     *
     * @param text input text
     * @return boolean vector of length dimension
     * @throws NullPointerException if text is null
     */
    public boolean[] textToBoolean(String text) {
        Objects.requireNonNull(text, "text must not be null");

        // Convert text to binary vector (20-bit)
        long bits = text2VecService.textToBits(text);

        // Map to continuous vector of target dimension
        double[] continuous = new double[dimension];
        for (int i = 0; i < dimension; i++) {
            // Use different bit positions for each dimension
            int bitPos = i % Text2VecService.VECTOR_BITS;
            continuous[i] = ((bits >> bitPos) & 1) == 1 ? 1.0 : -1.0;
        }

        return sensorEncode(continuous);
    }

    /**
     * Creates a new builder for VqVaeProxy.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for VqVaeProxy with fluent API.
     */
    public static class Builder {
        private int dimension = 8;
        private int codeSize = 256;
        private double momentum = 0.1;
        private Text2VecService text2VecService;

        private Builder() {
        }

        /**
         * Sets the vector dimension (default: 8).
         */
        public Builder dimension(int dimension) {
            if (dimension <= 0) {
                throw new IllegalArgumentException("Dimension must be > 0, got " + dimension);
            }
            this.dimension = dimension;
            return this;
        }

        /**
         * Sets the codebook size (default: 256).
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
         * Sets the Text2VecService for text-to-boolean conversion.
         */
        public Builder text2VecService(Text2VecService text2VecService) {
            this.text2VecService = text2VecService;
            return this;
        }

        /**
         * Builds the VqVaeProxy.
         */
        public VqVaeProxy build() {
            return new VqVaeProxy(this);
        }
    }
}
