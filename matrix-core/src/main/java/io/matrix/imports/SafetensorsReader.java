package io.matrix.imports;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal hand-written Safetensors reader.
 *
 * <p>Safetensors file format (per HuggingFace spec):
 * <ol>
 *   <li>First 8 bytes: little-endian uint64 {@code N} = byte-length of header JSON.</li>
 *   <li>Next {@code N} bytes: UTF-8 JSON header
 *       ({@code {"tensor_name": {"dtype": "F32", "shape": [...], "data_offsets": [start, end]}, ...}}).</li>
 *   <li>Remaining bytes: raw tensor data in the order listed by {@code data_offsets}.</li>
 * </ol>
 *
 * <p>This implementation deliberately avoids heavy NLP libraries — only
 * {@link java.nio} + {@link java.util.Map}. It supports F32/F16/BF16/I8/U8/I16/I32/I64
 * tensor decoding into a {@link Tensor}. Multi-GB files are read via {@link FileChannel}
 * with positional access for the header (so we don't load the entire file into memory).
 *
 * <p>Reference: <a href="https://huggingface.co/docs/safetensors/index">huggingface.co/docs/safetensors</a>.
 */
public final class SafetensorsReader {

    /** Bytes-per-element for each supported dtype. */
    private static final Map<String, Integer> DTYPE_BYTES = Map.ofEntries(
            Map.entry("F64", 8), Map.entry("I64", 8), Map.entry("U64", 8),
            Map.entry("F32", 4), Map.entry("I32", 4), Map.entry("U32", 4),
            Map.entry("F16", 2), Map.entry("BF16", 2),
            Map.entry("I16", 2), Map.entry("U16", 2),
            Map.entry("I8", 1), Map.entry("U8", 1), Map.entry("BOOL", 1)
    );

    public SafetensorsReader() {}

    /**
     * Reads the header of the file and returns per-tensor metadata + a {@link FileChannel}
     * positioned at the start of payload bytes.
     *
     * @param file a {@code *.safetensors} shard
     * @return header describing named tensors and total payload offset
     */
    public Header readHeader(Path file) throws IOException {
        long fileSize = Files.size(file);
        if (fileSize < 8) {
            throw new IOException("File too small to contain safetensors header: " + file);
        }

        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer lenBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            int read = ch.read(lenBuf, 0);
            if (read != 8) throw new IOException("Could not read header length");
            ch.position(0);
            long headerLen = ByteBuffer.wrap(lenBuf.array()).order(ByteOrder.LITTLE_ENDIAN).getLong();
            if (headerLen <= 0 || headerLen > fileSize) {
                throw new IOException("Invalid safetensors header length: " + headerLen);
            }

            ByteBuffer headerBuf = ByteBuffer.allocate((int) headerLen);
            int off = 0;
            while (off < headerLen) {
                int n = ch.read(headerBuf, 8 + off);
                if (n < 0) throw new IOException("Unexpected EOF reading header");
                off += n;
            }
            String headerJson = StandardCharsets.UTF_8.decode(headerBuf.flip()).toString();

            long payloadStart = 8 + headerLen;
            Map<String, TensorMeta> tensors = parseHeader(headerJson, payloadStart);
            return new Header(file, tensors, payloadStart, fileSize - payloadStart);
        }
    }

    /**
     * Loads a single tensor's raw bytes (sliced from the file) and quantises to
     * {@code float[]} for downstream projection.
     *
     * <p>The channel {@code ch} must be opened in READ mode on the same file as
     * {@code h.file()}; we use {@code meta.startOffset()} / {@code endOffset()}
     * (which already include {@code h.payloadOffset()}) for the seek.
     *
     * @param ch   already-opened channel on {@code h.file()}
     * @param h    header from {@link #readHeader(Path)}
     * @param name tensor name
     * @return loaded tensor with float values
     */
    public Tensor loadTensor(FileChannel ch, Header h, String name) throws IOException {
        TensorMeta meta = h.tensors().get(name);
        if (meta == null) throw new IOException("Unknown tensor: " + name + " in " + h.file());

        long byteLen = meta.endOffset() - meta.startOffset();
        if (byteLen < 0 || byteLen > Integer.MAX_VALUE) {
            throw new IOException("Invalid tensor byte length: " + byteLen);
        }
        ByteBuffer raw = ByteBuffer.allocate((int) byteLen);
        int pos = 0;
        while (pos < byteLen) {
            // meta.startOffset() already INCLUDES h.payloadOffset() — see parseHeader.
            int n = ch.read(raw, meta.startOffset() + pos);
            if (n < 0) throw new IOException("EOF reading tensor " + name);
            pos += n;
        }
        raw.flip();

        float[] data = decode(raw, meta.dtype(), (int) meta.elementCount());
        return new Tensor(name, meta.dtype(), meta.shape(), data);
    }

    /** Parses the JSON header into typed metadata. */
    private Map<String, TensorMeta> parseHeader(String json, long payloadStart) {
        Map<String, TensorMeta> out = new LinkedHashMap<>();
        int idx = 0;
        while (idx < json.length()) {
            // Find next quoted key
            int keyStart = json.indexOf('"', idx);
            if (keyStart < 0) break;
            int keyEnd = json.indexOf('"', keyStart + 1);
            if (keyEnd < 0) break;
            String key = json.substring(keyStart + 1, keyEnd);
            idx = keyEnd + 1;

            // Skip "__metadata__" pseudo-section
            if ("__metadata__".equals(key)) {
                int braceEnd = skipBalanced(json, idx, '{', '}');
                if (braceEnd < 0) break;
                idx = braceEnd + 1;
                continue;
            }
            // Locate value block
            int valStart = json.indexOf('{', idx);
            if (valStart < 0) break;
            int valEnd = skipBalanced(json, valStart, '{', '}');
            if (valEnd < 0) break;
            String block = json.substring(valStart + 1, valEnd);
            idx = valEnd + 1;

            String dtype = extractJsonString(block, "dtype");
            int[] shape = extractJsonIntArray(block, "shape");
            long[] offsets = extractJsonLongArray(block, "data_offsets");
            if (dtype == null || shape == null || offsets == null || offsets.length != 2) {
                continue;  // malformed entry — skip rather than fail
            }
            long elements = 1;
            for (int s : shape) elements *= Math.max(1, s);
            out.put(key, new TensorMeta(dtype, shape,
                    payloadStart + offsets[0], payloadStart + offsets[1],
                    Math.max(1, elements)));
        }
        return out;
    }

    /** Skips forward past a balanced delimiter scope. */
    private int skipBalanced(String s, int from, char open, char close) {
        int depth = 0;
        boolean inString = false;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '"' && s.charAt(i - 1) != '\\') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /** Extracts a JSON string value for {@code key} in a flat object substring. */
    private String extractJsonString(String obj, String key) {
        String qk = "\"" + key + "\"";
        int p = obj.indexOf(qk);
        if (p < 0) return null;
        int colon = obj.indexOf(':', p + qk.length());
        if (colon < 0) return null;
        int s = obj.indexOf('"', colon + 1);
        if (s < 0) return null;
        int e = obj.indexOf('"', s + 1);
        if (e < 0) return null;
        return obj.substring(s + 1, e);
    }

    /** Extracts {@code key: [a, b, c, ...]} as an int[]. */
    private int[] extractJsonIntArray(String obj, String key) {
        String qk = "\"" + key + "\"";
        int p = obj.indexOf(qk);
        if (p < 0) return null;
        int colon = obj.indexOf(':', p + qk.length());
        if (colon < 0) return null;
        int lb = obj.indexOf('[', colon + 1);
        if (lb < 0) return null;
        int rb = obj.indexOf(']', lb + 1);
        if (rb < 0) return null;
        String[] parts = obj.substring(lb + 1, rb).split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { out[i] = Integer.parseInt(parts[i].trim()); } catch (NumberFormatException e) { return null; }
        }
        return out;
    }

    private long[] extractJsonLongArray(String obj, String key) {
        int[] ia = extractJsonIntArray(obj, key);
        if (ia == null) return null;
        long[] out = new long[ia.length];
        for (int i = 0; i < ia.length; i++) out[i] = ia[i];
        return out;
    }

    /** Decode raw bytes (already read into buffer) into a float[] for the given dtype. */
    private float[] decode(ByteBuffer raw, String dtype, int count) {
        Integer bpe = DTYPE_BYTES.get(dtype);
        if (bpe == null) throw new IllegalArgumentException("Unsupported dtype: " + dtype);
        raw.order(ByteOrder.LITTLE_ENDIAN);  // safetensors is little-endian
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            switch (dtype) {
                case "F32" -> out[i] = raw.getFloat();
                case "F16" -> out[i] = halfToFloat(raw.getShort());
                case "BF16" -> {
                    int bits = raw.getShort() & 0xFFFF;
                    out[i] = Float.intBitsToFloat((bits << 16));
                }
                case "I8" -> out[i] = raw.get();
                case "U8" -> out[i] = raw.get() & 0xFF;
                case "BOOL" -> out[i] = (raw.get() & 0xFF) != 0 ? 1f : 0f;
                case "I16" -> out[i] = raw.getShort();
                case "I32" -> out[i] = raw.getInt();
                case "F64" -> out[i] = (float) raw.getDouble();
                case "I64" -> out[i] = raw.getLong();
                default -> out[i] = 0f;  // never reached (validated by DTYPE_BYTES lookup)
            }
        }
        return out;
    }

    /** IEEE 754 binary16 → float32 conversion. */
    public static float halfToFloat(short bits) {
        int sign = (bits >> 15) & 0x1;
        int exp = (bits >> 10) & 0x1F;     // F16: 5-bit exponent
        int mant = bits & 0x3FF;          // F16: 10-bit mantissa
        int fBits;
        if (exp == 0) {
            if (mant == 0) {
                // +0.0 or -0.0
                fBits = sign << 31;
            } else {
                // Subnormal: normalize.
                int e = -1;
                int m = mant;
                while ((m & 0x400) == 0) { m <<= 1; e++; }
                fBits = (sign << 31) | ((127 - 14 - e) << 23) | ((m & 0x3FF) << 13);
            }
        } else if (exp == 31) {
            // Inf or NaN: F32 exp = 255 (all 8 bits set), so use 0xFF << 23 (== 0x7F800000).
            fBits = (sign << 31) | (0xFF << 23) | (mant << 13);
        } else {
            // Normal: bias 15 → 127.
            fBits = (sign << 31) | ((exp - 15 + 127) << 23) | (mant << 13);
        }
        return Float.intBitsToFloat(fBits);
    }

    /** Header record returned by {@link #readHeader(Path)}. */
    public record Header(
            Path file,
            Map<String, TensorMeta> tensors,
            long payloadOffset,
            long payloadBytes) {

        public int tensorCount() { return tensors.size(); }
        public List<String> tensorNames() { return new ArrayList<>(tensors.keySet()); }
    }

    /** Metadata for a single tensor in the safetensors header. */
    public record TensorMeta(String dtype, int[] shape, long startOffset, long endOffset, long elementCount) {}

    /** Loaded tensor with values projected to {@code float[]}. */
    public record Tensor(String name, String dtype, int[] shape, float[] data) {
        public int dimensionCount() { return shape.length; }
        public long elementCount() { return data.length; }
        public float min() {
            float m = Float.POSITIVE_INFINITY;
            for (float f : data) if (f < m) m = f;
            return m;
        }
        public float max() {
            float m = Float.NEGATIVE_INFINITY;
            for (float f : data) if (f > m) m = f;
            return m;
        }
    }
}
