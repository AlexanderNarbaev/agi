package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 257 — CycleLock unit tests. */
class CycleLockTest {

    @Test
    void freshLockHasNoActivity() {
        var l = new CycleLock();
        assertThat(l.readerCount()).isZero();
        assertThat(l.isWriting()).isFalse();
        assertThat(l.waitingWriterCount()).isZero();
    }

    @Test
    void readLockIncrementsReaders() throws Exception {
        var l = new CycleLock();
        l.readLock();
        assertThat(l.readerCount()).isEqualTo(1);
        l.readUnlock();
        assertThat(l.readerCount()).isZero();
    }

    @Test
    void multipleReadersConcurrent() throws Exception {
        var l = new CycleLock();
        l.readLock();
        l.readLock();
        l.readLock();
        assertThat(l.readerCount()).isEqualTo(3);
        l.readUnlock();
        l.readUnlock();
        l.readUnlock();
    }

    @Test
    void writeLockBlocksNewReaders() throws Exception {
        var l = new CycleLock();
        l.writeLock();
        assertThat(l.isWriting()).isTrue();
        l.writeUnlock();
        assertThat(l.isWriting()).isFalse();
    }
}
