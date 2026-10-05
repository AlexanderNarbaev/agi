package io.matrix.brain.runtime;

/**
 * Byte fixtures for {@link MediaMetadataProbeTest}.
 *
 * <p>Kept apart from the test so the probe's own logic stays readable, and so the byte layouts
 * are documented once instead of being commented inline at every use. Nothing here is a real
 * image: the point of every fixture is a boundary — the shortest thing that still counts, and
 * the thing one byte short of the next stage.
 */
final class MediaMetadataProbeFixtures {

    /** Longest description {@code describe} may produce, in characters. */
    static final int MAX_DESCRIPTION_CHARS = 1200;

    private MediaMetadataProbeFixtures() {
    }

    /**
     * A structurally valid PNG of the requested dimensions.
     *
     * <p>Built byte by byte rather than base64'd so the IHDR offsets are visible and a reader
     * can check the probe against them. Signature occupies bytes 0-7; the IHDR length field
     * sits at 8-11, the {@code IHDR} type at 12-15, and {@code width}/{@code height} are
     * big-endian ints at 16-19 and 20-23.</p>
     *
     * @param width  image width in pixels; must be 1..65535 to fit the PNG field
     * @param height image height in pixels; must be 1..65535
     * @return a PNG byte sequence beginning with a real IHDR
     */
    static byte[] minimalPng(int width, int height) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.writeBytes(new byte[]{
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A  // signature
        });
        writeInt(out, 13);                                        // IHDR data length
        out.writeBytes(new byte[]{'I', 'H', 'D', 'R'});           // chunk type
        writeInt(out, width);                                     // bytes 16..19
        writeInt(out, height);                                    // bytes 20..23
        out.write(8);                                             // bit depth
        out.write(6);                                             // colour type: RGBA
        return out.toByteArray();
    }

    /**
     * Write a 4-byte big-endian value.
     *
     * <p>Unit: bytes. Written explicitly rather than via {@code ByteBuffer} so the fixture has
     * no dependency on a buffer's default byte order, which is the classic way an
     * interoperability-format helper quietly becomes wrong.</p>
     *
     * @param out   destination stream
     * @param value the value to encode
     */
    private static void writeInt(java.io.ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}