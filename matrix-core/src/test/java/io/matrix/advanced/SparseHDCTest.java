package io.matrix.advanced;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SparseHDCTest {

    @Test
    void testCreateSparseHDC() {
        SparseHDC hdc = new SparseHDC(1000, 0.9, 42L);
        assertNotNull(hdc);
        assertEquals(1000, hdc.getDimension());
        assertEquals(0.9, hdc.getSparsity());
    }

    @Test
    void testGenerateSparseVector() {
        SparseHDC hdc = new SparseHDC(1000, 0.9, 42L);
        SparseHDC.SparseVector v = hdc.generateSparse("A");
        assertEquals(1000, v.dimension());
        assertTrue(v.nonZeroCount() >= 95 && v.nonZeroCount() <= 100,
            "Should have ~100 non-zero: " + v.nonZeroCount());
        assertTrue(v.actualSparsity() >= 0.85);
    }

    @Test
    void testBindOperation() {
        SparseHDC hdc = new SparseHDC(1000, 0.9, 42L);
        SparseHDC.SparseVector a = hdc.generateSparse("A");
        SparseHDC.SparseVector b = hdc.generateSparse("B");
        SparseHDC.SparseVector bound = hdc.bind(a, b);
        assertNotNull(bound);
        // Bound vector should also be sparse
        assertTrue(bound.actualSparsity() > 0.7);
    }

    @Test
    void testSimilarity() {
        SparseHDC hdc = new SparseHDC(1000, 0.5, 42L);
        SparseHDC.SparseVector a = hdc.generateSparse("A");
        SparseHDC.SparseVector b = hdc.generateSparse("B");

        double sim = hdc.similarity(a, b);
        assertTrue(sim >= 0 && sim <= 1, "Similarity should be in [0,1]: " + sim);
    }

    @Test
    void testInvalidSparsity() {
        assertThrows(IllegalArgumentException.class,
            () -> new SparseHDC(1000, 1.5, 42L));
        assertThrows(IllegalArgumentException.class,
            () -> new SparseHDC(1000, -0.1, 42L));
    }

    @Test
    void testStorage() {
        SparseHDC hdc = new SparseHDC(1000, 0.9, 42L);
        assertEquals(0, hdc.getStoredVectorCount());

        hdc.generateSparse("A");
        hdc.generateSparse("B");
        hdc.generateSparse("C");
        assertEquals(3, hdc.getStoredVectorCount());
    }

    @Test
    void testCompressionRatio() {
        // Sparse HDC with 90% sparsity = 10x compression vs dense
        SparseHDC hdc = new SparseHDC(1000, 0.9, 42L);
        SparseHDC.SparseVector v = hdc.generateSparse("test");

        int denseSize = 1000; // 1 bit per dimension
        int sparseSize = v.nonZeroCount(); // only non-zero indices

        double compression = (double) denseSize / sparseSize;
        assertTrue(compression >= 9.0 && compression <= 11.0,
            "Compression should be ~10x: " + compression);
    }
}
