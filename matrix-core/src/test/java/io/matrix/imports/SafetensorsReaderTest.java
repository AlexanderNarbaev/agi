package io.matrix.imports;

import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the safetensors reader without any external dependencies.
 *
 * <p>Test fixtures are built in-memory by constructing synthetic safetensors
 * files in a temp directory (header + payload written by hand), then parsed
 * via {@link SafetensorsReader}.
 */
class SafetensorsReaderTest {

    @Test
    void shouldReadHeaderAndExtractTensorMetadata(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        // Build a tiny safetensors file with one F32 tensor of shape [2,3].
        Map<String, Object> headerJson = new LinkedHashMap<>();
        Map<String, Object> tensorBlock = new LinkedHashMap<>();
        tensorBlock.put("dtype", "F32");
        tensorBlock.put("shape", new int[]{2, 3});
        tensorBlock.put("data_offsets", new long[]{0L, 24L});
        headerJson.put("tensor_a", tensorBlock);

        byte[] headerBytes = buildHeaderJson(headerJson);
        byte[] payload = new byte[24];
        for (int i = 0; i < 6; i++) {
            int bits = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(i + 0.5f).getInt(0);
            payload[i * 4] = (byte) bits;
            payload[i * 4 + 1] = (byte) (bits >> 8);
            payload[i * 4 + 2] = (byte) (bits >> 16);
            payload[i * 4 + 3] = (byte) (bits >> 24);
        }
        Path file = tmp.resolve("test.safetensors");
        writeSafetensorsFile(file, headerBytes, payload);

        SafetensorsReader reader = new SafetensorsReader();
        SafetensorsReader.Header header = reader.readHeader(file);
        assertThat(header.tensorCount()).isEqualTo(1);
        assertThat(header.tensors()).containsKey("tensor_a");
        SafetensorsReader.TensorMeta meta = header.tensors().get("tensor_a");
        assertThat(meta.dtype()).isEqualTo("F32");
        assertThat(meta.shape()).containsExactly(2, 3);

        try (FileChannel ch = FileChannel.open(file)) {
            SafetensorsReader.Tensor t = reader.loadTensor(ch, header, "tensor_a");
            assertThat(t.elementCount()).isEqualTo(6);
            assertThat(t.dtype()).isEqualTo("F32");
            assertThat(t.shape()).containsExactly(2, 3);
            assertThat(t.data()[0]).isEqualTo(0.5f);
            assertThat(t.data()[5]).isEqualTo(5.5f);
        }
    }

    @Test
    void halfToFloatShouldHandleInfinityAndZero() {
        // Positive zero
        assertThat(SafetensorsReader.halfToFloat((short) 0x0000)).isEqualTo(0.0f);
        // Negative zero
        assertThat(SafetensorsReader.halfToFloat((short) 0x8000)).isEqualTo(-0.0f);
        // +Infinity
        assertThat(Float.isInfinite(SafetensorsReader.halfToFloat((short) 0x7C00))).isTrue();
        // -Infinity
        assertThat(Float.isInfinite(SafetensorsReader.halfToFloat((short) 0xFC00))).isTrue();
        // 1.0
        assertThat(SafetensorsReader.halfToFloat((short) 0x3C00)).isEqualTo(1.0f);
    }

    @Test
    void shouldRejectFileTooSmall(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("empty.safetensors");
        Files.write(file, new byte[]{1, 2, 3});
        SafetensorsReader reader = new SafetensorsReader();
        assertThatThrownBy(() -> reader.readHeader(file))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("too small");
    }

    @Test
    void shouldHandleUnknownDtypeInHeaderGracefully(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        // Header references an unsupported dtype — loadTensor should not crash parser, but loading fails.
        Map<String, Object> tensorBlock = new LinkedHashMap<>();
        tensorBlock.put("dtype", "F128");   // Unsupported
        tensorBlock.put("shape", new int[]{1});
        tensorBlock.put("data_offsets", new long[]{0L, 16L});
        Map<String, Object> headerJson = new HashMap<>();
        headerJson.put("bad_tensor", tensorBlock);

        byte[] bytes = buildHeaderJson(headerJson);
        Path file = tmp.resolve("bad.safetensors");
        writeSafetensorsFile(file, bytes, new byte[16]);

        SafetensorsReader reader = new SafetensorsReader();
        SafetensorsReader.Header h = reader.readHeader(file);
        assertThat(h.tensors()).containsKey("bad_tensor");
        // Dtype appears in metadata but is unsupported — loader should fail predictably.
        try (FileChannel ch = FileChannel.open(file)) {
            assertThatThrownBy(() -> reader.loadTensor(ch, h, "bad_tensor"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported dtype");
        }
    }

    /** Builds a minimal safetensors-shaped JSON header. */
    private static byte[] buildHeaderJson(Map<String, Object> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : entries.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            @SuppressWarnings("unchecked")
            Map<String, Object> block = (Map<String, Object>) e.getValue();
            sb.append('{');
            boolean firstBlock = true;
            for (Map.Entry<String, Object> b : block.entrySet()) {
                if (!firstBlock) sb.append(',');
                firstBlock = false;
                sb.append('"').append(b.getKey()).append("\":");
                Object val = b.getValue();
                if (val instanceof String) sb.append('"').append(val).append('"');
                else if (val instanceof int[]) {
                    sb.append('[');
                    int[] arr = (int[]) val;
                    for (int i = 0; i < arr.length; i++) {
                        if (i > 0) sb.append(',');
                        sb.append(arr[i]);
                    }
                    sb.append(']');
                } else if (val instanceof long[]) {
                    sb.append('[');
                    long[] arr = (long[]) val;
                    for (int i = 0; i < arr.length; i++) {
                        if (i > 0) sb.append(',');
                        sb.append(arr[i]);
                    }
                    sb.append(']');
                }
            }
            sb.append('}');
        }
        sb.append('}');
        return StandardCharsets.UTF_8.encode(sb.toString()).array();
    }

    private static void writeSafetensorsFile(Path file, byte[] header, byte[] payload) throws IOException {
        try (var ch = FileChannel.open(file, java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.WRITE)) {
            ByteBuffer lenBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(header.length);
            lenBuf.flip();
            ch.write(lenBuf);
            ch.write(ByteBuffer.wrap(header));
            ch.write(ByteBuffer.wrap(payload));
        }
    }
}
