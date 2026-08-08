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
 * - tags TEXT (comma-separated)
 * - importance REAL
 * - created_at INTEGER
 * - last_accessed INTEGER
 * - access_count INTEGER
 */
public final class SqliteMemoryBackend {

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
                        tags TEXT,
                        importance REAL DEFAULT 0.5,
                        created_at INTEGER,
                        last_accessed INTEGER,
                        access_count INTEGER DEFAULT 0
                    )
                    """);
                st.execute("CREATE INDEX IF NOT EXISTS idx_level ON memory(level)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_domain ON memory(domain)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_importance ON memory(importance)");
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQLite backend init failed: " + dbPath, e);
        }
    }

    /** Save a memory entry. */
    public void save(HierarchicalMemory.MemoryEntry entry) {
        String sql = """
            INSERT OR REPLACE INTO memory
            (id, level, content, domain, tags, importance, created_at, last_accessed, access_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.id());
            ps.setInt(2, entry.level().index());
            ps.setString(3, entry.content());
            ps.setString(4, entry.domain());
            ps.setString(5, String.join(",", entry.tags()));
            ps.setDouble(6, entry.importance());
            ps.setLong(7, entry.createdAt());
            ps.setLong(8, entry.lastAccessedAt());
            ps.setInt(9, entry.accessCount());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Save failed: " + entry.id(), e);
        }
    }

    /** Load all entries. */
    public Map<String, HierarchicalMemory.MemoryEntry> loadAll() {
        Map<String, HierarchicalMemory.MemoryEntry> result = new ConcurrentHashMap<>();
        String sql = "SELECT * FROM memory";
        try (var st = conn.createStatement(); var rs = st.executeQuery(sql)) {
            while (rs.next()) {
                var entry = new HierarchicalMemory.MemoryEntry(
                        rs.getString("id"),
                        HierarchicalMemory.Level.values()[rs.getInt("level")],
                        rs.getString("content"),
                        rs.getString("domain"),
                        Set.of(rs.getString("tags").split(",")),
                        rs.getDouble("importance"),
                        rs.getLong("created_at"),
                        rs.getLong("last_accessed"),
                        rs.getInt("access_count"),
                        null, Set.of()
                );
                result.put(entry.id(), entry);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Load failed", e);
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
    public void close() {
        try {
            if (conn != null) conn.close();
        } catch (SQLException e) {
            // ignore
        }
    }

    /** Check if backend is healthy. */
    public boolean isHealthy() {
        try {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}
