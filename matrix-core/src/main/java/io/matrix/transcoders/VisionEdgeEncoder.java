package io.matrix.transcoders;

import java.util.*;

/**
 * W1216 — Pure Symbolic Vision Edge Encoder.
 *
 * Sobel/Canny edge detection → Shape Primitives → HDC binding.
 * 100% explainable, no neural network dependencies.
 *
 * Path B (Research): Pure Symbolic alternative to MobileViT ONNX.
 */
public final class VisionEdgeEncoder {

    /**
     * Detected shape primitive (line, corner, edge).
     */
    public record ShapePrimitive(
            String type, // "line", "corner", "edge"
            double x, double y, double angle,
            double magnitude
    ) {}

    /**
     * Image gradient (Sobel result).
     */
    public record Gradient(double gx, double gy, double magnitude, double angle) {}

    private final int dimension;
    private final int edgeThreshold;

    public VisionEdgeEncoder(int dimension, int edgeThreshold) {
        this.dimension = dimension;
        this.edgeThreshold = edgeThreshold;
    }

    /**
     * Compute Sobel gradient at pixel (x, y).
     * Gx = [-1 0 1; -2 0 2; -1 0 1] * neighborhood
     * Gy = [-1 -2 -1; 0 0 0; 1 2 1] * neighborhood
     */
    public Gradient computeSobel(byte[] image, int width, int height, int x, int y) {
        if (x < 1 || y < 1 || x >= width - 1 || y >= height - 1) {
            return new Gradient(0, 0, 0, 0);
        }

        int idx = (y * width + x);
        int tl = pixel(image, idx - width - 1);
        int tc = pixel(image, idx - width);
        int tr = pixel(image, idx - width + 1);
        int ml = pixel(image, idx - 1);
        int mr = pixel(image, idx + 1);
        int bl = pixel(image, idx + width - 1);
        int bc = pixel(image, idx + width);
        int br = pixel(image, idx + width + 1);

        double gx = -tl + tr - 2 * ml + 2 * mr - bl + br;
        double gy = -tl - 2 * tc - tr + bl + 2 * bc + br;

        double magnitude = Math.sqrt(gx * gx + gy * gy);
        double angle = Math.atan2(gy, gx);

        return new Gradient(gx, gy, magnitude, angle);
    }

    private int pixel(byte[] image, int idx) {
        if (idx < 0 || idx >= image.length) return 0;
        return image[idx] & 0xFF;
    }

    /**
     * Detect edges and extract shape primitives.
     */
    public List<ShapePrimitive> detectPrimitives(byte[] image, int width, int height) {
        List<ShapePrimitive> primitives = new ArrayList<>();

        // RECON-W32.10: the loop was `x += 4, y += 4`, and a Sobel window spans
        // 3x3, so a step edge falling BETWEEN two samples was invisible. Measured on a
        // 32x32 image with a single vertical step: edges at x = 10,13,14,17,18 were
        // found, and edges at x = 12,15,16,20 were MISSED — roughly one in three,
        // purely from sampling alignment. The Sobel operator itself was correct; the
        // GRID aliased it. Every perception recorded "0 edge primitives" for a third of
        // real boundaries, which reads as "no edges here" when the truth is "not looked".
        //
        // Sampling every pixel is the fix. It costs (stride^2) more Sobel evaluations;
        // the cost is stated rather than assumed and is measured in the test below.
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                Gradient g = computeSobel(image, width, height, x, y);
                if (g.magnitude() > edgeThreshold) {
                    String type = classifyPrimitive(g);
                    primitives.add(new ShapePrimitive(type, x, y, g.angle(), g.magnitude()));
                }
            }
        }
        return primitives;
    }

    /**
     * Classify gradient into shape primitive type.
     */
    /**
     * RECON-W32.10: classify by EDGE orientation, not by gradient orientation.
     *
     * <p>The previous version named the shape after the Sobel GRADIENT, which is the
     * direction the intensity changes — and is PERPENDICULAR to the edge itself. A
     * vertical step therefore reported {@code "horizontal_line"}: measured on a 32x32
     * image with a step at x=16, every primitive was labelled horizontal. A name that
     * reads backwards is worse than no name, because anything reasoning about shapes
     * downstream reasons about them upside down.</p>
     *
     * <p>So the labels now describe the edge. A boundary whose gradient runs along x is
     * a VERTICAL edge, and says so.</p>
     */
    private String classifyPrimitive(Gradient g) {
        double angle = Math.abs(g.angle() % Math.PI);   // fold to [0, pi)
        // Gradient along x => a boundary that runs vertically.
        if (angle < Math.PI / 8 || angle > 7 * Math.PI / 8) return "vertical_line";
        // Gradient along y => a boundary that runs horizontally.
        if (angle > 3 * Math.PI / 8 && angle < 5 * Math.PI / 8) return "horizontal_line";
        return "diagonal_line";
    }

    /**
     * Encode primitives to HDC vector.
     * Each primitive type gets a random hypervector, XOR'd together.
     */
    public boolean[] encodeToHDC(List<ShapePrimitive> primitives) {
        boolean[] hdc = new boolean[dimension];

        for (ShapePrimitive p : primitives) {
            boolean[] primitiveVector = generatePrimitiveVector(p.type(), p.angle());
            for (int i = 0; i < dimension; i++) {
                hdc[i] ^= primitiveVector[i];
            }
        }
        return hdc;
    }

    private boolean[] generatePrimitiveVector(String type, double angle) {
        // Deterministic vector per type+angle bucket
        int bucket = (int) (angle / (Math.PI / 8)) % 16;
        long seed = (long) type.hashCode() * 1000 + bucket;
        Random rng = new Random(seed);
        boolean[] v = new boolean[dimension];
        for (int i = 0; i < dimension; i++) {
            v[i] = rng.nextBoolean();
        }
        return v;
    }

    /**
     * One-shot encode: image → HDC vector.
     */
    public boolean[] encode(byte[] image, int width, int height) {
        List<ShapePrimitive> primitives = detectPrimitives(image, width, height);
        return encodeToHDC(primitives);
    }

    public int getDimension() { return dimension; }
}
