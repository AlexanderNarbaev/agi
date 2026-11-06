package io.matrix.memory;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQLite backend for HierarchicalMemory persistence.
 *
 * <p>Stores memory entries in an embedded SQLite database.
 * On startup, loads all entries into memory. On store/delete, syncs to disk.
 *
 * <p>Schema:
 * - id TEXT PRIMARY KEY
 * - level INTEGER (0-4)
 * - content TEXT
 * - domain TEXT
 * - tags TEXT (comma-separated, empty = no tags)
 * - importance REAL
 * - created_at INTEGER
 * - last_accessed INTEGER
 * - access_count INTEGER
 * - parent_id TEXT (nullable)
 * - child_ids TEXT (comma-separated, empty = no children)
 *
 * <p>Indexes: level, domain, importance, (level, importance DESC) composite.
 */
public final class SqliteMemoryBackend implements AutoCloseable {

    private final String dbPath;
    private Connection conn;

    public SqliteMemoryBackend(String dbPath) {
        this.dbPath = dbPath;
        init();
    }

    private void init() {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (var st = conn.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS memory (
                        id TEXT PRIMARY KEY,
                        level INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        domain TEXT,
                        tags TEXT DEFAULT '',
                        importance REAL DEFAULT 0.5,
                        created_at INTEGER,
                        last_accessed INTEGER,
                        access_count INTEGER DEFAULT 0,
                        parent_id TEXT,
                        child_ids TEXT DEFAULT ''
                    )
                    """);
                // The tombstone columns (RECON-W42.5). A forgotten entry
                // keeps its content: forgetting must not destroy the
                // historical evidence, it must mark it as no longer part of
                // the live memory.
                // The columns are added only when they are missing: SQLite
                // refuses to add a column twice, and the backend is opened
                // on every start.
                if (!hasColumn(st, "forgotten_at")) {
                    st.execute("ALTER TABLE memory ADD COLUMN forgotten_at INTEGER");
                }
                if (!hasColumn(st, "forgotten_reason")) {
                    st.execute("ALTER TABLE memory ADD COLUMN forgotten_reason TEXT");
                }
                st.execute("CREATE INDEX IF NOT EXISTS idx_level ON memory(level)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_domain ON memory(domain)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_importance ON memory(importance)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_level_importance ON memory(level, importance DESC)");
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQLite backend init failed: " + dbPath, e);
        }
    }

    /** Whether the table already has the column. */
    private static boolean hasColumn(Statement st, String column) throws SQLException {
        try (var rs = st.executeQuery("PRAGMA table_info(memory)")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Save a memory entry. */
    public void save(HierarchicalMemory.MemoryEntry entry) {
        String sql = """
            INSERT OR REPLACE INTO memory
            (id, level, content, domain, tags, importance, created_at, last_accessed,
             access_count, parent_id, child_ids)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.id());
            ps.setInt(2, entry.level().index());
            ps.setString(3, entry.content());
            ps.setString(4, entry.domain());
            ps.setString(5, entry.tags().isEmpty() ? "" : String.join(",", entry.tags()));
            ps.setDouble(6, entry.importance());
            ps.setLong(7, entry.createdAt());
            ps.setLong(8, entry.lastAccessedAt());
            ps.setInt(9, entry.accessCount());
            ps.setString(10, entry.parentId());
            ps.setString(11, entry.childIds().isEmpty() ? "" : String.join(",", entry.childIds()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Save failed: " + entry.id(), e);
        }
    }

    /** Load all entries. */
    public Map<String, HierarchicalMemory.MemoryEntry> loadAll() {
        Map<String, HierarchicalMemory.MemoryEntry> result = new ConcurrentHashMap<>();
        String sql = "SELECT * FROM memory WHERE forgotten_at IS NULL";
        try (var st = conn.createStatement(); var rs = st.executeQuery(sql)) {
            while (rs.next()) {
                var entry = entryFromRow(rs);
                result.put(entry.id(), entry);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Load failed", e);
        }
        return result;
    }

    /** Returns total number of entries. */
    public int count() {
        String sql = "SELECT COUNT(*) FROM memory";
        try (var st = conn.createStatement(); var rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Count failed", e);
        }
    }

    /** Lists entries at a specific level, sorted by importance descending. */
    public List<HierarchicalMemory.MemoryEntry> listByLevel(int level) {
        String sql = "SELECT * FROM memory WHERE level = ? AND forgotten_at IS NULL ORDER BY importance DESC";
        List<HierarchicalMemory.MemoryEntry> result = new ArrayList<>();
        try (var ps = conn.prepareStatement(sql)) {
            ps.setInt(1, level);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) result.add(entryFromRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("listByLevel failed: level=" + level, e);
        }
        return result;
    }

    /** Lists entries created within [startMs, endMs], ordered by created_at. */
    public List<HierarchicalMemory.MemoryEntry> listByTimeRange(long startMs, long endMs) {
        String sql = "SELECT * FROM memory WHERE created_at >= ? AND created_at <= ? AND forgotten_at IS NULL ORDER BY created_at";
        List<HierarchicalMemory.MemoryEntry> result = new ArrayList<>();
        try (var ps = conn.prepareStatement(sql)) {
            ps.setLong(1, startMs);
            ps.setLong(2, endMs);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) result.add(entryFromRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("listByTimeRange failed", e);
        }
        return result;
    }

    /**
     * Compacts low-importance L0 entries beyond max age.
     *
     * <p><b>The entries are not deleted.</b> The first version of this
     * method issued a DELETE, and a delete is the one thing the mission's
     * section 8 forbids outright: forgetting must not silently destroy the
     * historical evidence. A forgotten entry is MARKED - it keeps its
     * content, its id and the moment it was forgotten - and the live reads
     * stop returning it. The evidence is still there, and
     * {@link #tombstones()} can reach it.
     *
     * @param maxImportance entries with importance BELOW this threshold are candidates
     * @param maxAgeMs      entries older than this (ms) are candidates
     * @return number of forgotten entries
     */
    public int compact(double maxImportance, long maxAgeMs) {
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        String sql = "UPDATE memory SET forgotten_at = ?, forgotten_reason = ? "
                + "WHERE level = 0 AND importance < ? AND created_at < ? AND forgotten_at IS NULL";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, "compacted: below the importance threshold and past the age window");
            ps.setDouble(3, maxImportance);
            ps.setLong(4, cutoff);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Compact failed", e);
        }
    }

    /**
     * The entries that were forgotten, and why.
     *
     * <p>The historical evidence of a forgetting: what left the live memory,
     * when, and on what grounds. An audit that cannot see this cannot tell
     * a forgetting from a loss.
     *
     * @return the forgotten entries
     */
    public List<HierarchicalMemory.MemoryEntry> tombstones() {
        List<HierarchicalMemory.MemoryEntry> result = new ArrayList<>();
        String sql = "SELECT * FROM memory WHERE forgotten_at IS NOT NULL ORDER BY forgotten_at";
        try (var ps = conn.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            while (rs.next()) result.add(entryFromRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("tombstones failed", e);
        }
        return result;
    }

    /**
     * Restores a forgotten entry to the live memory.
     *
     * @param id the entry's id
     * @return true when an entry was restored
     */
    public boolean restore(String id) {
        String sql = "UPDATE memory SET forgotten_at = NULL, forgotten_reason = NULL WHERE id = ?";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("restore failed", e);
        }
    }

    /** Searches entries by domain prefix (max 100 results). */
    public List<HierarchicalMemory.MemoryEntry> searchByDomain(String domainPrefix) {
        String sql = "SELECT * FROM memory WHERE domain LIKE ? AND forgotten_at IS NULL ORDER BY importance DESC LIMIT 100";
        List<HierarchicalMemory.MemoryEntry> result = new ArrayList<>();
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, domainPrefix + "%");
            try (var rs = ps.executeQuery()) {
                while (rs.next()) result.add(entryFromRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("searchByDomain failed", e);
        }
        return result;
    }

    /** Delete an entry. */
    public void delete(String id) {
        try (var ps = conn.prepareStatement("DELETE FROM memory WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Delete failed: " + id, e);
        }
    }

    /** Close the connection. */
    @Override
    public void close() {
        try { if (conn != null) conn.close(); } catch (SQLException e) { /* ignore */ }
    }

    /** Check if backend is healthy. */
    public boolean isHealthy() {
        try { return conn != null && !conn.isClosed(); } catch (SQLException e) { return false; }
    }

    // ─── internal helpers ───

    private static HierarchicalMemory.MemoryEntry entryFromRow(ResultSet rs) throws SQLException {
        return new HierarchicalMemory.MemoryEntry(
                rs.getString("id"),
                HierarchicalMemory.Level.values()[rs.getInt("level")],
                rs.getString("content"),
                rs.getString("domain"),
                parseTags(rs.getString("tags")),
                rs.getDouble("importance"),
                rs.getLong("created_at"),
                rs.getLong("last_accessed"),
                rs.getInt("access_count"),
                parseNullableString(rs.getString("parent_id")),
                parseTags(rs.getString("child_ids"))
        );
    }

    private static Set<String> parseTags(String csv) {
        if (csv == null || csv.isEmpty()) return Set.of();
        return Set.of(csv.split(","));
    }

    private static String parseNullableString(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
