package io.matrix.reservoir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BinaryReservoirTest {

    @Test
    void writeAndRead() {
        var r = new BinaryReservoir(64, 0.9, 0.5);
        long[] bits = {1L}; // bit 0 set
        r.write(bits);
        long[] result = r.read();
        assertEquals(1L, result[0] & 1L);
    }

    @Test
    void fadingOverTime() {
        var r = new BinaryReservoir(64, 0.5, 0.3);
        long[] bits = {1L};
        r.write(bits);
        // Advance time
        for (int i = 0; i < 10; i++) r.read();
        // Activation should have decayed
        assertTrue(r.activation(0) < 0.01);
    }

    @Test
    void forgetThreshold() {
        var r = new BinaryReservoir(64, 0.8, 0.5);
        long[] bits = {1L};
        r.write(bits);
        // Read once (activation = 1.0 * 0.8 = 0.8 > 0.5)
        assertEquals(1L, r.read()[0] & 1L);
        // Read many times (activation decays below 0.5)
        for (int i = 0; i < 5; i++) r.read();
        assertEquals(0L, r.read()[0] & 1L); // forgotten
    }

    @Test
    void activeCount() {
        var r = new BinaryReservoir(64, 0.99, 0.5);
        long[] bits = {0b1111L}; // 4 bits set
        r.write(bits);
        assertEquals(4, r.activeCount());
    }
}
