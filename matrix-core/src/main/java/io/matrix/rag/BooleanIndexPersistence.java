package io.matrix.rag;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Objects;

/**
 * Persistence layer for {@link BooleanIndex}.
 *
 * <p>Provides disk-based save/load with support for:
 * <ul>
 *   <li><b>Full save:</b> writes entire index to a binary file</li>
 *   <li><b>Full load:</b> reads entire index from a binary file</li>
 *   <li><b>Incremental save:</b> appends new vectors to an existing file</li>
 *   <li><b>Append marker:</b> detects incremental vs full format</li>
 * </ul>
 *
 * <p>Binary format:
 * <pre>
 *   Full:    [MAGIC(4)] [VERSION(4)] [DIM(4)] [COUNT(4)] [entries...]
 *   Entry:   [ID_LEN(4)] [ID(UTF)] [VECTOR(longsPerVector * 8)]
 *   Append:  [APPEND_MAGIC(4)] [COUNT(4)] [entries...]
 * </pre>
 *
 * <p>Thread-safe: all operations are stateless.
 */
public final class BooleanIndexPersistence {

    private static final int MAGIC = 0x424F4F4C;       // "BOOL"
    private static final int APPEND_MAGIC = 0x41505044; // "APPD"
    private static final int VERSION = 1;

    private BooleanIndexPersistence() {}

    /**
     * Saves the full index to a file (overwrites existing content).
     *
     * @param index the index to save
     * @param path  target file path
     * @throws IOException on I/O error
     */
    public static void save(BooleanIndex index, Path path) throws IOException {
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(path, "path");

        try (OutputStream out = Files.newOutputStream(path,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            index.serialize(out);
        }
    }

    /**
     * Loads a full index from a file.
     *
     * @param path source file path
     * @return the deserialized index
     * @throws IOException on I/O error or invalid format
     */
    public static BooleanIndex load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");

        try (InputStream in = Files.newInputStream(path)) {
            return BooleanIndex.deserialize(in);
        }
    }

    /**
     * Appends new vectors to an existing index file (incremental save).
     *
     * <p>If the file does not exist, performs a full save instead.
     *
     * @param index    the index containing vectors to append
     * @param path     target file path
     * @param newIds   IDs of vectors to append (must exist in index)
     * @throws IOException on I/O error
     */
    public static void append(BooleanIndex index, Path path, Iterable<String> newIds) throws IOException {
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(newIds, "newIds");

        if (!Files.exists(path)) {
            save(index, path);
            return;
        }

        // Collect vectors to append
        Map<String, long[]> toAppend = new java.util.LinkedHashMap<>();
        int longsPerVector = index.dimensions() / 64;
        for (String id : newIds) {
            long[] vec = index.get(id);
            if (vec != null) {
                toAppend.put(id, vec);
            }
        }

        if (toAppend.isEmpty()) return;

        // Append with append marker
        try (OutputStream out = Files.newOutputStream(path,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            DataOutputStream dos = new DataOutputStream(out);
            dos.writeInt(APPEND_MAGIC);
            dos.writeInt(toAppend.size());
            for (var entry : toAppend.entrySet()) {
                dos.writeUTF(entry.getKey());
                for (long l : entry.getValue()) {
                    dos.writeLong(l);
                }
            }
            dos.flush();
        }
    }

    /**
     * Loads an index from a file that may contain append markers.
     *
     * <p>Reads the full section first, then processes any appended sections.
     *
     * @param path source file path
     * @return the deserialized index with all appended vectors
     * @throws IOException on I/O error or invalid format
     */
    public static BooleanIndex loadWithAppends(Path path) throws IOException {
        Objects.requireNonNull(path, "path");

        try (InputStream in = Files.newInputStream(path)) {
            DataInputStream dis = new DataInputStream(in);

            // Read full section
            int magic = dis.readInt();
            if (magic != MAGIC) {
                throw new IOException("Invalid magic: 0x" + Integer.toHexString(magic));
            }
            int version = dis.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported version: " + version);
            }
            int dims = dis.readInt();
            BooleanIndex idx = new BooleanIndex(dims);
            int count = dis.readInt();
            int longsPerVector = dims / 64;
            for (int i = 0; i < count; i++) {
                String id = dis.readUTF();
                long[] vec = new long[longsPerVector];
                for (int j = 0; j < longsPerVector; j++) {
                    vec[j] = dis.readLong();
                }
                idx.vectors.put(id, vec);
            }

            // Read any appended sections
            while (dis.available() > 0) {
                int appendMagic = dis.readInt();
                if (appendMagic != APPEND_MAGIC) {
                    throw new IOException("Invalid append magic: 0x" + Integer.toHexString(appendMagic));
                }
                int appendCount = dis.readInt();
                for (int i = 0; i < appendCount; i++) {
                    String id = dis.readUTF();
                    long[] vec = new long[longsPerVector];
                    for (int j = 0; j < longsPerVector; j++) {
                        vec[j] = dis.readLong();
                    }
                    idx.vectors.put(id, vec);
                }
            }

            return idx;
        }
    }

    /**
     * Returns the file size in bytes for a saved index.
     *
     * @param path file path
     * @return file size, or -1 if file does not exist
     */
    public static long fileSize(Path path) {
        try {
            return Files.exists(path) ? Files.size(path) : -1;
        } catch (IOException e) {
            return -1;
        }
    }
}
