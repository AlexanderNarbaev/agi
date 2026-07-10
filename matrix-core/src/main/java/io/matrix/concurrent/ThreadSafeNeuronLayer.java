package io.matrix.concurrent;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.NeuronLayer;
import io.matrix.neuron.TruthTable;

import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe wrapper around {@link NeuronLayer}.
 *
 * <p>Provides concurrent access to neuron evaluation with:
 * <ul>
 *   <li>{@link ConcurrentHashMap} for neuron result caching</li>
 *   <li>{@link ReadWriteLock} for concurrent {@link #evaluate(BitSet)} calls</li>
 *   <li>{@link AtomicLong} for metrics counters (evaluation count, cache hits)</li>
 * </ul>
 *
 * <p>All public methods are thread-safe and can be called concurrently from
 * multiple threads without external synchronization.
 *
 * <p>Ref: Phase8 — Multithreading & Concurrency
 */
public final class ThreadSafeNeuronLayer {

    private final NeuronLayer delegate;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ConcurrentHashMap<Integer, BitSet> evaluateCache = new ConcurrentHashMap<>();
    private final AtomicLong evaluateCount = new AtomicLong(0);
    private final AtomicLong cacheHitCount = new AtomicLong(0);
    private final AtomicLong cacheMissCount = new AtomicLong(0);

    /**
     * Creates a thread-safe layer wrapping the given {@link NeuronLayer}.
     *
     * @param delegate the underlying neuron layer (must not be null)
     */
    public ThreadSafeNeuronLayer(NeuronLayer delegate) {
        this.delegate = delegate;
    }

    /**
     * Creates a thread-safe layer with randomly initialized neurons.
     *
     * @param neuronCount number of neurons
     * @param k           input width per neuron
     * @param rng         random generator
     */
    public ThreadSafeNeuronLayer(int neuronCount, int k, java.util.Random rng) {
        this.delegate = new NeuronLayer(neuronCount, k, rng);
    }

    /**
     * Evaluates all neurons on the input, producing {@code outputWidth} bits.
     *
     * <p>Uses a read lock for concurrent access. Results are cached by input
     * hash for repeated evaluations of the same input.
     *
     * @param input flattened BitSet of size at least {@code outputWidth × k}
     * @return BitSet of size {@code outputWidth}
     */
    public BitSet evaluate(BitSet input) {
        int cacheKey = input.hashCode();
        BitSet cached = evaluateCache.get(cacheKey);
        if (cached != null) {
            cacheHitCount.incrementAndGet();
            evaluateCount.incrementAndGet();
            return (BitSet) cached.clone();
        }

        rwLock.readLock().lock();
        try {
            // Double-check after acquiring lock
            cached = evaluateCache.get(cacheKey);
            if (cached != null) {
                cacheHitCount.incrementAndGet();
                evaluateCount.incrementAndGet();
                return (BitSet) cached.clone();
            }

            BitSet result = delegate.evaluate(input);
            cacheMissCount.incrementAndGet();
            evaluateCount.incrementAndGet();
            evaluateCache.put(cacheKey, (BitSet) result.clone());
            return result;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns an unmodifiable view of the neurons in this layer.
     * Acquires a read lock to ensure visibility.
     */
    public List<DecisionTree> neurons() {
        rwLock.readLock().lock();
        try {
            return delegate.neurons();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns input width per neuron.
     */
    public int k() {
        return delegate.k();
    }

    /**
     * Returns number of neurons (output bit width).
     */
    public int outputWidth() {
        return delegate.outputWidth();
    }

    /**
     * Serializes this layer to bytes.
     * Acquires a read lock to ensure consistent state during serialization.
     */
    public byte[] toAvroBytes() {
        rwLock.readLock().lock();
        try {
            return delegate.toAvroBytes();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Clears the evaluation cache. Useful after layer mutation.
     */
    public void invalidateCache() {
        rwLock.writeLock().lock();
        try {
            evaluateCache.clear();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Returns the total number of evaluations performed.
     */
    public long evaluateCount() {
        return evaluateCount.get();
    }

    /**
     * Returns the number of cache hits.
     */
    public long cacheHitCount() {
        return cacheHitCount.get();
    }

    /**
     * Returns the number of cache misses.
     */
    public long cacheMissCount() {
        return cacheMissCount.get();
    }

    /**
     * Returns the current cache size.
     */
    public int cacheSize() {
        return evaluateCache.size();
    }

    /**
     * Returns the underlying delegate layer (for testing/inspection).
     */
    public NeuronLayer delegate() {
        return delegate;
    }

    /**
     * Returns the truth table for serialization support.
     */
    public static ThreadSafeNeuronLayer fromAvroBytes(byte[] data) {
        return new ThreadSafeNeuronLayer(NeuronLayer.fromAvroBytes(data));
    }

    /**
     * Creates from truth tables.
     */
    public static ThreadSafeNeuronLayer fromTruthTables(List<TruthTable> tables) {
        return new ThreadSafeNeuronLayer(NeuronLayer.fromTruthTables(tables));
    }

    @Override
    public String toString() {
        return "ThreadSafeNeuronLayer{" + delegate + ", cache=" + evaluateCache.size() + "}";
    }
}
