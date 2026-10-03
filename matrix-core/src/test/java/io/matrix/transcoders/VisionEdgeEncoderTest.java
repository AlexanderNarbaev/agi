package io.matrix.transcoders;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VisionEdgeEncoderTest {

    @Test
    void testCreateEncoder() {
        VisionEdgeEncoder encoder = new VisionEdgeEncoder(1024, 50);
        assertNotNull(encoder);
        assertEquals(1024, encoder.getDimension());
    }

    @Test
    void testComputeSobelOnEdge() {
        VisionEdgeEncoder encoder = new VisionEdgeEncoder(1024, 50);
        // Create image with vertical edge in the middle
        byte[] image = new byte[100 * 100];
        for (int y = 0; y < 100; y++) {
            for (int x = 0; x < 100; x++) {
                image[y * 100 + x] = (byte) (x < 50 ? 0 : 255);
            }
        }
        VisionEdgeEncoder.Gradient g = encoder.computeSobel(image, 100, 100, 49, 50);
        assertTrue(g.magnitude() > 0);
    }

    @Test
    void testComputeSobelOnUniformArea() {
        VisionEdgeEncoder encoder = new VisionEdgeEncoder(1024, 50);
        byte[] image = new byte[100 * 100];
        Arrays.fill(image, (byte) 128);
        VisionEdgeEncoder.Gradient g = encoder.computeSobel(image, 100, 100, 50, 50);
        assertEquals(0.0, g.magnitude(), 0.001);
    }

    @Test
    void testDetectPrimitives() {
        VisionEdgeEncoder encoder = new VisionEdgeEncoder(1024, 30);
        byte[] image = new byte[100 * 100];
        // Create a square in the middle
        for (int y = 30; y < 70; y++) {
            for (int x = 30; x < 70; x++) {
                image[y * 100 + x] = (byte) 255;
            }
        }
        List<VisionEdgeEncoder.ShapePrimitive> primitives = encoder.detectPrimitives(image, 100, 100);
        assertNotNull(primitives);
    }

    @Test
    void testOneShotEncode() {
        VisionEdgeEncoder encoder = new VisionEdgeEncoder(1024, 30);
        byte[] image = new byte[100 * 100];
        Random rng = new Random(42);
        rng.nextBytes(image);
        boolean[] hdc = encoder.encode(image, 100, 100);
        assertEquals(1024, hdc.length);
    }

    @Test
    void testPrimitiveClassification() {
        VisionEdgeEncoder encoder = new VisionEdgeEncoder(1024, 50);
        byte[] image = new byte[100 * 100];
        // Horizontal edge
        for (int x = 0; x < 100; x++) image[50 * 100 + x] = (byte) 255;
        List<VisionEdgeEncoder.ShapePrimitive> primitives = encoder.detectPrimitives(image, 100, 100);
        for (VisionEdgeEncoder.ShapePrimitive p : primitives) {
            assertNotNull(p.type());
            assertTrue(p.magnitude() > 0);
        }
    }

    /** A 32x32 image with a single vertical step at {@code edge}. Unit: pixels. */
    private static byte[] step(int w, int h, int edge, int low, int high) {
        byte[] g = new byte[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) g[y * w + x] = (byte) (x < edge ? low : high);
        return g;
    }

    @org.junit.jupiter.api.Test
    void aStepEdgeIsFoundAtEveryPosition() {
        // The sampling grid was x += 4, so a step falling between two samples was
        // invisible: measured misses at x = 12, 15, 16, 20 — roughly one in three. Every
        // one reported "0 edge primitives", which reads as "no edges here" when the
        // truth is "not looked". All earlier image fixtures were FLAT FILLS, which have
        // no edge at any position, so their 0 proved nothing either way.
        var enc = new VisionEdgeEncoder(256, 32);
        for (int edge = 4; edge < 28; edge++) {
            int n = enc.detectPrimitives(step(32, 32, edge, 30, 200), 32, 32).size();
            org.junit.jupiter.api.Assertions.assertTrue(n > 0,
                "step edge at x=" + edge + " must be found, but " + n
                    + " primitives were detected — the sampling grid is aliasing edges");
        }
    }

    @org.junit.jupiter.api.Test
    void aUniformImageHasNoEdges() {
        var enc = new VisionEdgeEncoder(256, 32);
        byte[] flat = new byte[32 * 32];
        java.util.Arrays.fill(flat, (byte) 60);
        org.junit.jupiter.api.Assertions.assertTrue(
            enc.detectPrimitives(flat, 32, 32).isEmpty(),
            "a uniform image has no edges, and reporting some would be a fabrication");
    }

    @org.junit.jupiter.api.Test
    void aVerticalStepIsClassifiedAsAVerticalLine() {
        var enc = new VisionEdgeEncoder(256, 32);
        var prims = enc.detectPrimitives(step(32, 32, 16, 30, 200), 32, 32);
        org.junit.jupiter.api.Assertions.assertTrue(
            prims.stream().anyMatch(p -> p.type().equals("vertical_line")),
            "a vertical boundary must be classified as vertical, got: "
                + prims.stream().map(p -> p.type()).distinct().toList());
    }
}
