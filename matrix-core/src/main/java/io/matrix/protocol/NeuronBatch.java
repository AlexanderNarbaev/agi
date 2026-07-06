package io.matrix.protocol;

import io.matrix.cluster.NeuronId;
import io.matrix.cluster.Signal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Neuron Batch Protocol (NBP) — aggregates signals for efficient
 * inter-cluster transmission. Implements L2 §3.
 *
 * <p>Compression modes:
 * <ul>
 *   <li>{@link CompressionMode#RAW RAW} — no compression (small batches)</li>
 *   <li>{@link CompressionMode#RLE RLE} — Run-Length Encoding for repeated signals</li>
 *   <li>{@link CompressionMode#BITMASK BITMASK} — bitmask encoding (saves 1 byte per signal)</li>
 * </ul>
 *
 * <p>Wire format (all multi-byte integers big-endian):
 * <pre>
 *   [mode:1][payload...]
 *
 *   RAW:      [count:4][signal_0]...[signal_{count-1}]
 *   RLE:      [numGroups:4][group_0]...[group_{numGroups-1}]
 *   BITMASK:  [count:4][bitmask:ceil(count/8) bytes][signal_0]...[signal_{count-1}]
 *
 *   signal (RAW):  [srcUUID:16][srcGen:8][tgtUUID:16][tgtGen:8][value:1][timestamp:8] = 57 bytes
 *   signal (BITMASK): [srcUUID:16][srcGen:8][tgtUUID:16][tgtGen:8][timestamp:8] = 48 bytes (value in bitmask)
 *   group (RLE):  [srcUUID:16][srcGen:8][tgtUUID:16][tgtGen:8][value:1][runLen:4][ts_0:8]...[ts_{runLen-1}:8]
 * </pre>
 */
public final class NeuronBatch {

    public enum CompressionMode { RAW, RLE, BITMASK }

    private static final int UUID_BYTES = 16;
    private static final int RAW_SIGNAL_BYTES = UUID_BYTES + 8 + UUID_BYTES + 8 + 1 + 8; // 57
    private static final int BITMASK_SIGNAL_BYTES = UUID_BYTES + 8 + UUID_BYTES + 8 + 8;   // 48

    private final List<Signal> signals;
    private final CompressionMode compression;
    private final byte[] compressedData;
    private final int originalCount;

    private NeuronBatch(List<Signal> signals, CompressionMode compression,
                        byte[] compressedData, int originalCount) {
        this.signals = signals;
        this.compression = compression;
        this.compressedData = compressedData;
        this.originalCount = originalCount;
    }

    /**
     * Packs signals into a batch, auto-selecting the best compression mode.
     *
     * @param signals signals to pack (order preserved)
     * @return packed batch
     */
    public static NeuronBatch pack(List<Signal> signals) {
        Objects.requireNonNull(signals, "signals");
        if (signals.isEmpty()) {
            return new NeuronBatch(List.of(), CompressionMode.RAW, encodeRaw(signals), 0);
        }

        byte[] raw = encodeRaw(signals);
        byte[] rle = encodeRLE(signals);
        byte[] bitmask = encodeBitmask(signals);

        if (rle.length <= bitmask.length && rle.length <= raw.length) {
            return new NeuronBatch(List.copyOf(signals), CompressionMode.RLE, rle, signals.size());
        } else if (bitmask.length <= raw.length) {
            return new NeuronBatch(List.copyOf(signals), CompressionMode.BITMASK, bitmask, signals.size());
        } else {
            return new NeuronBatch(List.copyOf(signals), CompressionMode.RAW, raw, signals.size());
        }
    }

    /**
     * Packs signals using a specific compression mode.
     *
     * @param signals     signals to pack
     * @param compression compression mode to use
     * @return packed batch
     */
    public static NeuronBatch pack(List<Signal> signals, CompressionMode compression) {
        Objects.requireNonNull(signals, "signals");
        Objects.requireNonNull(compression, "compression");
        byte[] data = switch (compression) {
            case RAW -> encodeRaw(signals);
            case RLE -> encodeRLE(signals);
            case BITMASK -> encodeBitmask(signals);
        };
        return new NeuronBatch(List.copyOf(signals), compression, data, signals.size());
    }

    /**
     * Unpacks compressed batch back to signals.
     *
     * @return list of signals
     */
    public List<Signal> unpack() {
        if (originalCount == 0) {
            return List.of();
        }
        return switch (compression) {
            case RAW -> decodeRaw(compressedData);
            case RLE -> decodeRLE(compressedData);
            case BITMASK -> decodeBitmask(compressedData);
        };
    }

    /**
     * Returns the compression ratio: {@code originalSize / compressedSize}.
     *
     * @return ratio ≥ 0.0 (1.0 = no compression, >1.0 = compressed)
     */
    public double compressionRatio() {
        if (originalCount == 0) {
            return 1.0;
        }
        int originalSize = originalCount * RAW_SIGNAL_BYTES;
        if (compressedData.length == 0) {
            return 0.0;
        }
        return (double) originalSize / compressedData.length;
    }

    public CompressionMode compression() {
        return compression;
    }

    public int signalCount() {
        return originalCount;
    }

    public int compressedSize() {
        return compressedData.length;
    }

    public byte[] compressedData() {
        return compressedData.clone();
    }

    // --- RAW encoding ---

    private static byte[] encodeRaw(List<Signal> signals) {
        try (var bos = new ByteArrayOutputStream();
             var out = new DataOutputStream(bos)) {
            out.writeInt(signals.size());
            for (Signal s : signals) {
                writeSignal(out, s, true);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("RAW encode failed", e);
        }
    }

    private static List<Signal> decodeRaw(byte[] data) {
        try (var bis = new ByteArrayInputStream(data);
             var in = new DataInputStream(bis)) {
            int count = in.readInt();
            List<Signal> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                result.add(readSignal(in, true));
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("RAW decode failed", e);
        }
    }

    // --- RLE encoding ---

    private static byte[] encodeRLE(List<Signal> signals) {
        try (var bos = new ByteArrayOutputStream();
             var out = new DataOutputStream(bos)) {
            int numGroups = 0;
            var groups = new ArrayList<List<Signal>>();
            List<Signal> current = new ArrayList<>();
            for (Signal s : signals) {
                if (current.isEmpty() || sameGroup(current.get(0), s)) {
                    current.add(s);
                } else {
                    groups.add(current);
                    current = new ArrayList<>();
                    current.add(s);
                }
            }
            if (!current.isEmpty()) {
                groups.add(current);
            }
            numGroups = groups.size();

            out.writeInt(numGroups);
            for (List<Signal> group : groups) {
                Signal first = group.get(0);
                writeUUID(out, first.sourceId().uuid());
                out.writeLong(first.sourceId().generation());
                writeUUID(out, first.targetId().uuid());
                out.writeLong(first.targetId().generation());
                out.writeBoolean(first.value());
                out.writeInt(group.size());
                for (Signal s : group) {
                    out.writeLong(s.timestamp());
                }
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("RLE encode failed", e);
        }
    }

    private static List<Signal> decodeRLE(byte[] data) {
        try (var bis = new ByteArrayInputStream(data);
             var in = new DataInputStream(bis)) {
            int numGroups = in.readInt();
            List<Signal> result = new ArrayList<>();
            for (int g = 0; g < numGroups; g++) {
                UUID srcUuid = readUUID(in);
                long srcGen = in.readLong();
                UUID tgtUuid = readUUID(in);
                long tgtGen = in.readLong();
                boolean value = in.readBoolean();
                int runLen = in.readInt();
                NeuronId src = new NeuronId(srcUuid, srcGen);
                NeuronId tgt = new NeuronId(tgtUuid, tgtGen);
                for (int i = 0; i < runLen; i++) {
                    long ts = in.readLong();
                    result.add(new Signal(src, tgt, value, ts));
                }
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("RLE decode failed", e);
        }
    }

    private static boolean sameGroup(Signal a, Signal b) {
        return a.sourceId().equals(b.sourceId())
                && a.targetId().equals(b.targetId())
                && a.value() == b.value();
    }

    // --- BITMASK encoding ---

    private static byte[] encodeBitmask(List<Signal> signals) {
        try (var bos = new ByteArrayOutputStream();
             var out = new DataOutputStream(bos)) {
            int count = signals.size();
            out.writeInt(count);
            int bitmaskBytes = (count + 7) / 8;
            byte[] bitmask = new byte[bitmaskBytes];
            for (int i = 0; i < count; i++) {
                if (signals.get(i).value()) {
                    bitmask[i / 8] |= (1 << (i % 8));
                }
            }
            out.write(bitmask);
            for (Signal s : signals) {
                writeSignal(out, s, false);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("BITMASK encode failed", e);
        }
    }

    private static List<Signal> decodeBitmask(byte[] data) {
        try (var bis = new ByteArrayInputStream(data);
             var in = new DataInputStream(bis)) {
            int count = in.readInt();
            int bitmaskBytes = (count + 7) / 8;
            byte[] bitmask = new byte[bitmaskBytes];
            in.readFully(bitmask);
            List<Signal> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                boolean value = (bitmask[i / 8] & (1 << (i % 8))) != 0;
                UUID srcUuid = readUUID(in);
                long srcGen = in.readLong();
                UUID tgtUuid = readUUID(in);
                long tgtGen = in.readLong();
                long ts = in.readLong();
                result.add(new Signal(
                        new NeuronId(srcUuid, srcGen),
                        new NeuronId(tgtUuid, tgtGen),
                        value, ts));
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("BITMASK decode failed", e);
        }
    }

    // --- Signal I/O helpers ---

    private static void writeSignal(DataOutputStream out, Signal s, boolean includeValue) throws IOException {
        writeUUID(out, s.sourceId().uuid());
        out.writeLong(s.sourceId().generation());
        writeUUID(out, s.targetId().uuid());
        out.writeLong(s.targetId().generation());
        if (includeValue) {
            out.writeBoolean(s.value());
        }
        out.writeLong(s.timestamp());
    }

    private static Signal readSignal(DataInputStream in, boolean includeValue) throws IOException {
        UUID srcUuid = readUUID(in);
        long srcGen = in.readLong();
        UUID tgtUuid = readUUID(in);
        long tgtGen = in.readLong();
        boolean value = includeValue ? in.readBoolean() : false;
        long ts = in.readLong();
        return new Signal(new NeuronId(srcUuid, srcGen), new NeuronId(tgtUuid, tgtGen), value, ts);
    }

    private static void writeUUID(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUUID(DataInputStream in) throws IOException {
        long msb = in.readLong();
        long lsb = in.readLong();
        return new UUID(msb, lsb);
    }
}
