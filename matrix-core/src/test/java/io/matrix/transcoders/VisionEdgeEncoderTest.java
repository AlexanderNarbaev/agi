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
}
