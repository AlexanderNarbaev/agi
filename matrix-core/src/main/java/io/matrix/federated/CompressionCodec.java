package io.matrix.federated;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.inject.Inject;
import java.util.Map;

/**
 * Compression codec for federated learning updates.
 * 
 * Reduces network overhead by compressing boolean arrays
 * using run-length encoding.
 */
@jakarta.enterprise.context.ApplicationScoped
public class CompressionCodec {

    /**
     * Compress boolean array using RLE.
     */
    public byte[] compress(boolean[] data) {
        if (data.length == 0) return new byte[0];

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        boolean current = data[0];
        int count = 1;

        for (int i = 1; i < data.length; i++) {
            if (data[i] == current && count < 255) {
                count++;
            } else {
                out.write((byte) (current ? count : -count));
                current = data[i];
                count = 1;
            }
        }
        out.write((byte) (current ? count : -count));
        return out.toByteArray();
    }

    /**
     * Decompress RLE-encoded data to boolean array.
     */
    public boolean[] decompress(byte[] data) {
        java.util.List<Boolean> bits = new java.util.ArrayList<>();
        for (byte b : data) {
            boolean value = b > 0;
            int count = Math.abs(b);
            for (int i = 0; i < count; i++) {
                bits.add(value);
            }
        }
        boolean[] result = new boolean[bits.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = bits.get(i);
        }
        return result;
    }

    /**
     * Get compression ratio for given data.
     */
    public double compressionRatio(boolean[] data) {
        if (data.length == 0) return 1.0;
        byte[] compressed = compress(data);
        return (double) compressed.length / data.length;
    }
}
