package io.matrix.perf;

import io.matrix.memory.HierarchicalMemory;
import io.matrix.memory.SqliteMemoryBackend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * RECON-W30 — SQLite write and read throughput for the memory tier, against the real
 * {@link SqliteMemoryBackend} in {@code matrix-core}.
 *
 * <p>The fourth hot kernel the wave plan named, and the one that most directly bounds
 * everything else. Two facts from the inventory make it matter here specifically:</p>
 * <ul>
 *   <li>the working set is a knowledge base that RECON-W31 proposes to grow by 10x, and a
 *       knowledge store whose writes do not keep up caps how much knowledge can be
 *       admitted;</li>
 *   <li>the database lives on NVMe, and the two disks in this machine are different
 *       models (KINGSTON SFYRS1000G and YMTC PC41Q-1TB-B). Sustained-write behaviour
 *       differs between them, so a write number measured here is specific to the device
 *       the file happened to be on. The teardown prints the resolved path for that
 *       reason.</li>
 * </ul>
 *
 * <p>{@code save()} is append-and-index, so {@code writeThroughput} is the number that
 * matters for ingestion, while {@code readBack} covers retrieval. Both use a real
 * {@link HierarchicalMemory.MemoryEntry} rather than a stub, so column widths and
 * indexing are representative.</p>
 *
 * <p>Each trial uses its own database under the system temp directory and deletes it
 * afterwards. The teardown is deliberately defensive: a benchmark that leaks a database
 * per trial would eventually fill the disk this whole campaign is trying to keep tidy.</p>
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
public class SqliteMemoryBenchmark {

    /** Entries written per benchmark invocation. */
    @Param({"100", "1000"})
    public int batchSize;

    /** Payload size in bytes, to show how write cost scales with content width. */
    @Param({"256"})
    public int contentBytes;

    private SqliteMemoryBackend backend;
    private Path dbPath;
    private HierarchicalMemory.MemoryEntry[] entries;
    private long rowsWritten;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        // JDBC driver registration, explicit.
        //
        // DriverManager discovers drivers through ServiceLoader, and the JMH fat jar
        // contains FIVE colliding `META-INF/services/java.sql.Driver` resources — one
        // per JDBC driver on the classpath. Jar assembly overwrites duplicates rather
        // than merging them, so only one registration survives and org.sqlite.JDBC is
        // not it. The symptom was `No suitable driver found for jdbc:sqlite:` at @Setup,
        // after which JMH exited 0 with an EMPTY result JSON — a benchmark that measured
        // nothing and reported success.
        //
        // Production is unaffected: the gateway runs on a normal module classpath where
        // each driver keeps its own services file. This is a fat-jar artefact, not a
        // product defect. Loading the class directly sidesteps ServiceLoader entirely.
        Class.forName("org.sqlite.JDBC");

        // Unique per trial so concurrent or repeated runs cannot collide on an existing
        // database and silently measure reads of pre-populated data.
        dbPath = Files.createTempDirectory("matrix-perf-sqlite").resolve("memory.db");
        backend = new SqliteMemoryBackend(dbPath.toString());

        entries = new HierarchicalMemory.MemoryEntry[batchSize];
        for (int i = 0; i < batchSize; i++) {
            entries[i] = new HierarchicalMemory.MemoryEntry(
                    UUID.nameUUIDFromBytes(("entry-" + i).getBytes()).toString(),
                    HierarchicalMemory.Level.L1_PATTERN,
                    payload(i),
                    "perf",
                    Set.of("w30", "benchmark"),
                    0.5d,
                    // Fixed epoch: CONSTITUTION I forbids wall-clock in runtime paths and
                    // a benchmark that stamps System.currentTimeMillis() would make its
                    // own output non-reproducible.
                    1_700_000_000_000L + i,
                    1_700_000_000_000L + i,
                    0,
                    null,
                    Set.of());
        }
        rowsWritten = 0;
    }

    /** Append throughput — the ingestion path. */
    @Benchmark
    public int writeThroughput(Blackhole bh) {
        for (int i = 0; i < batchSize; i++) {
            backend.save(entries[i]);
        }
        rowsWritten += batchSize;
        bh.consume(backend.count());
        return batchSize;
    }

    /** Point-ish read: load everything back. Retrieval goes through this. */
    @Benchmark
    public int readBack(Blackhole bh) {
        var all = backend.loadAll();
        bh.consume(all);
        return all.size();
    }

    /** Filtered read, which is what a domain-scoped retrieval issues. */
    @Benchmark
    public int readByDomain(Blackhole bh) {
        var rows = backend.searchByDomain("perf");
        bh.consume(rows);
        return rows.size();
    }

    @TearDown(Level.Trial)
    public void reportAndClose() throws Exception {
        System.err.printf("[SqliteMemory] path=%s rowsWritten=%d finalCount=%d healthy=%s%n",
                dbPath.toAbsolutePath(), rowsWritten, backend.count(), backend.isHealthy());
        backend.close();
        Files.deleteIfExists(dbPath);
        Path dir = dbPath.getParent();
        if (dir != null) {
            Files.deleteIfExists(dir);
        }
    }

    private String payload(int seed) {
        StringBuilder sb = new StringBuilder(contentBytes);
        for (int i = 0; i < contentBytes; i++) {
            sb.append((char) ('a' + ((seed + i) % 26)));
        }
        return sb.toString();
    }
}
