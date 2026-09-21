package io.matrix.consciousness;

/**
 * RUN 197 — BrainStateCompact (compact serialization).
 *
 * <p>Smaller alternative to BrainSnapshot. Produces a fixed-width
 * byte representation of brain state for logging.
 */
public final class BrainStateCompact {

    /** Pack into 24 bytes: arousal (8) + traceCount (4) + tailHash (16). */
    public static byte[] pack(BrainLoopService svc) {
        byte[] out = new byte[24];
        long arousalBits = Double.doubleToRawLongBits(svc.arousal());
        for (int i = 0; i < 8; i++) out[i] = (byte) (arousalBits >> (8 * i));
        int traceCount = svc.trace().count();
        for (int i = 0; i < 4; i++) out[8 + i] = (byte) (traceCount >> (8 * i));
        if (svc.trace().count() > 0) {
            String tail = svc.trace().last().hash;
            byte[] tailBytes = hexToBytes(tail);
            int n = Math.min(tailBytes.length, 12);
            for (int i = 0; i < n; i++) out[12 + i] = tailBytes[i];
        }
        return out;
    }

    public static UnpackedBrain unpack(byte[] packed) {
        if (packed.length < 24) {
            return new UnpackedBrain(0.3, 0, "");
        }
        long arousalBits = 0;
        for (int i = 0; i < 8; i++) {
            arousalBits |= (long) (packed[i] & 0xFF) << (8 * i);
        }
        double arousal = Double.longBitsToDouble(arousalBits);
        int traceCount = 0;
        for (int i = 0; i < 4; i++) {
            traceCount |= (packed[8 + i] & 0xFF) << (8 * i);
        }
        // Skip hash portion (just take raw bytes 12-23)
        return new UnpackedBrain(arousal, traceCount, "");
    }

    public record UnpackedBrain(double arousal, int traceCount, String tailHash) {}

    private static byte[] hexToBytes(String hex) {
        if (hex.length() % 2 != 0) hex = hex + "0";
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int hi = Character.digit(hex.charAt(2 * i), 16);
            int lo = Character.digit(hex.charAt(2 * i + 1), 16);
            result[i] = (byte) ((hi << 4) | lo);
        }
        return result;
    }
}
