package io.matrix.rag;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Boolean vector index with Hamming distance search.
 *
 * <p>Stores boolean vectors as {@code long[]} arrays and supports nearest-neighbor
 * lookup via Hamming distance (XOR + popcount). Vectors are 64-bit (1 long) or
 * 128-bit (2 longs).
 *
 * <p>All operations are thread-safe via {@link ReentrantReadWriteLock}.
 *
 * <p>Ref: Phase 2 — Boolean RAG
 */
public final class BooleanIndex {

    private static final int MAGIC = 0x424F4F4C; // "BOOL"
    private static final int VERSION = 1;

    private final int dimensions;
    private final int longsPerVector;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, long[]> vectors = new LinkedHashMap<>();

    private BooleanIndex(int dimensions) {
        this.dimensions = dimensions;
        this.longsPerVector = dimensions / 64;
    }

    /**
     * Returns a new builder for {@code BooleanIndex}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Adds or overwrites a vector with the given ID.
     *
     * @param id      non-null unique identifier
     * @param vector  non-null boolean vector; length must equal {@code dimensions / 64}
     */
    public void add(String id, long[] vector) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(vector, "vector");
        if (vector.length != longsPerVector) {
            throw new IllegalArgumentException(
                    "Vector length " + vector.length + " does not match dimensions " + dimensions);
        }
        lock.writeLock().lock();
        try {
            vectors.put(id, Arrays.copyOf(vector, vector.length));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes the vector with the given ID.
     *
     * @return {@code true} if a vector was removed
     */
    public boolean remove(String id) {
        lock.writeLock().lock();
        try {
            return vectors.remove(id) != null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns the vector for the given ID, or {@code null} if absent.
     */
    public long[] get(String id) {
        lock.readLock().lock();
        try {
            long[] v = vectors.get(id);
            return v != null ? Arrays.copyOf(v, v.length) : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns {@code true} if the index contains the given ID.
     */
    public boolean contains(String id) {
        lock.readLock().lock();
        try {
            return vectors.containsKey(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Searches for the top-K nearest neighbors by Hamming distance.
     *
     * @param query boolean vector to search for
     * @param k     maximum number of results
     * @return list of search results sorted by distance (ascending)
     */
    public List<SearchResult> search(long[] query, int k) {
        Objects.requireNonNull(query, "query");
        if (query.length != longsPerVector) {
            throw new IllegalArgumentException(
                    "Query length " + query.length + " does not match dimensions " + dimensions);
        }

        lock.readLock().lock();
        try {
            List<SearchResult> results = new ArrayList<>(vectors.size());
            for (var entry : vectors.entrySet()) {
                int dist = hammingDistance(query, entry.getValue());
                results.add(new SearchResult(entry.getKey(), dist));
            }
            results.sort(Comparator.comparingInt(SearchResult::distance));
            return results.stream().limit(k).toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Computes Hamming distance between two boolean vectors via XOR + popcount.
     *
     * <p>Time complexity: O(longsPerVector) — effectively O(1) for 64/128-bit vectors.
     */
    static int hammingDistance(long[] a, long[] b) {
        int dist = 0;
        for (int i = 0; i < a.length; i++) {
            dist += Long.bitCount(a[i] ^ b[i]);
        }
        return dist;
    }

    /**
     * Returns the number of stored vectors.
     */
    public int size() {
        lock.readLock().lock();
        try {
            return vectors.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the vector dimensions (64 or 128).
     */
    public int dimensions() {
        return dimensions;
    }

    /**
     * Returns a list of all stored IDs.
     */
    public List<String> ids() {
        lock.readLock().lock();
        try {
            return List.copyOf(vectors.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Removes all vectors from the index.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            vectors.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Serializes the index to the given output stream.
     *
     * <p>Format: [MAGIC(4)] [VERSION(4)] [DIMENSIONS(4)] [COUNT(4)]
     *           [ID_LEN(4)] [ID(UTF)] [VECTOR(longsPerVector * 8)] ...
     */
    public void serialize(OutputStream out) throws IOException {
        lock.readLock().lock();
        try {
            DataOutputStream dos = new DataOutputStream(out);
            dos.writeInt(MAGIC);
            dos.writeInt(VERSION);
            dos.writeInt(dimensions);
            dos.writeInt(vectors.size());
            for (var entry : vectors.entrySet()) {
                dos.writeUTF(entry.getKey());
                for (long l : entry.getValue()) {
                    dos.writeLong(l);
                }
            }
            dos.flush();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Deserializes an index from the given input stream.
     */
    public static BooleanIndex deserialize(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
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
        for (int i = 0; i < count; i++) {
            String id = dis.readUTF();
            long[] vec = new long[idx.longsPerVector];
            for (int j = 0; j < idx.longsPerVector; j++) {
                vec[j] = dis.readLong();
            }
            idx.vectors.put(id, vec);
        }
        return idx;
    }

    // --- Records ---

    /**
     * A search result containing an ID and its Hamming distance to the query.
     */
    public record SearchResult(String id, int distance) {}

    // --- Builder ---

    /**
     * Builder for {@link BooleanIndex}.
     */
    public static final class Builder {
        private int dimensions = 64;

        /**
         * Sets the vector dimensions. Must be a multiple of 64 (64 or 128).
         */
        public Builder dimensions(int dimensions) {
            if (dimensions <= 0 || dimensions % 64 != 0) {
                throw new IllegalArgumentException(
                        "Dimensions must be a positive multiple of 64, got: " + dimensions);
            }
            this.dimensions = dimensions;
            return this;
        }

        /**
         * Builds the {@link BooleanIndex}.
         */
        public BooleanIndex build() {
            return new BooleanIndex(dimensions);
        }
    }
}
