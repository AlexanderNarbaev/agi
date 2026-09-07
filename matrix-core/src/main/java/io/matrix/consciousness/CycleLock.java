package io.matrix.consciousness;

/**
 * RUN 257 — CycleLock (read-write coordination).
 *
 * <p>Simple read-write lock for thread-safe access to a single
 * cycle. Multiple readers can hold the lock simultaneously; only
 * one writer at a time.
 */
public final class CycleLock {

    private int readers = 0;
    private boolean writing = false;
    private int waitingWriters = 0;

    public synchronized void readLock() throws InterruptedException {
        while (writing || waitingWriters > 0) {
            wait();
        }
        readers++;
    }

    public synchronized void readUnlock() {
        readers--;
        notifyAll();
    }

    public synchronized void writeLock() throws InterruptedException {
        waitingWriters++;
        while (writing || readers > 0) {
            wait();
        }
        waitingWriters--;
        writing = true;
    }

    public synchronized void writeUnlock() {
        writing = false;
        notifyAll();
    }

    public synchronized int readerCount() { return readers; }
    public synchronized boolean isWriting() { return writing; }
    public synchronized int waitingWriterCount() { return waitingWriters; }
}
