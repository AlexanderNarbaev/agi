package io.matrix.noosphere;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Noosphere registry — global ledger of published FNLs.
 *
 * <p>Tracks all published FNLs with their status (ACTIVE, REVOKED, DEPRECATED).
 * Each entry includes author signature and metadata.
 *
 * <p>Ref: L6_Memory.md §6.2
 */
public class NoosphereRegistry {

    public enum EntryStatus { ACTIVE, REVOKED, DEPRECATED }

    public record RegistryEntry(
            UUID entryId,
            FnlPackage fnlPackage,
            EntryStatus status,
            long registeredAt
    ) {}

    public record PublishResult(UUID entryId, boolean success, String message) {
        public static PublishResult ok(UUID entryId) {
            return new PublishResult(entryId, true, "Published successfully");
        }

        public static PublishResult rejected(String message) {
            return new PublishResult(null, false, message);
        }
    }

    private final Map<UUID, RegistryEntry> entries = new HashMap<>();
    private final Map<String, List<UUID>> byAuthor = new HashMap<>();
    private final Map<String, List<UUID>> byType = new HashMap<>();
    private final List<String> eventLog = new ArrayList<>();

    /**
     * Publishes an FNL to the registry.
     */
    public PublishResult publish(FnlPackage fnl) {
        if (fnl.name() == null || fnl.authorInstanceId() == null) {
            return PublishResult.rejected("Missing required fields");
        }

        UUID entryId = UUID.randomUUID();
        RegistryEntry entry = new RegistryEntry(entryId, fnl,
                EntryStatus.ACTIVE, System.currentTimeMillis());

        entries.put(entryId, entry);
        byAuthor.computeIfAbsent(fnl.authorInstanceId(), k -> new ArrayList<>()).add(entryId);
        byType.computeIfAbsent(fnl.type(), k -> new ArrayList<>()).add(entryId);

        eventLog.add("PUBLISH:" + fnl.name() + " by " + fnl.authorInstanceId()
                + " type=" + fnl.type() + " accuracy=" + fnl.accuracy());
        return PublishResult.ok(entryId);
    }

    /**
     * Revokes a previously published FNL.
     */
    public boolean revoke(UUID entryId) {
        RegistryEntry entry = entries.get(entryId);
        if (entry == null) return false;

        RegistryEntry revoked = new RegistryEntry(entry.entryId(), entry.fnlPackage(),
                EntryStatus.REVOKED, System.currentTimeMillis());
        entries.put(entryId, revoked);
        eventLog.add("REVOKE:" + entryId);
        return true;
    }

    /**
     * Returns an entry by id.
     */
    public RegistryEntry get(UUID entryId) {
        return entries.get(entryId);
    }

    /**
     * Returns all active entries.
     */
    public List<RegistryEntry> activeEntries() {
        return entries.values().stream()
                .filter(e -> e.status() == EntryStatus.ACTIVE)
                .toList();
    }

    /**
     * Returns entries by author.
     */
    public List<RegistryEntry> byAuthor(String authorId) {
        return byAuthor.getOrDefault(authorId, List.of()).stream()
                .map(entries::get)
                .filter(e -> e != null && e.status() == EntryStatus.ACTIVE)
                .toList();
    }

    /**
     * Returns entries by FNL type.
     */
    public List<RegistryEntry> byType(String type) {
        return byType.getOrDefault(type, List.of()).stream()
                .map(entries::get)
                .filter(e -> e != null && e.status() == EntryStatus.ACTIVE)
                .toList();
    }

    public int size() { return entries.size(); }

    public List<String> eventLog() { return List.copyOf(eventLog); }
}
