package io.matrix.knowledge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An immutable event in the append-only history of source revision selections.
 */
public record SourceRevisionSelection(
        String eventId,
        String workspaceId,
        String documentKey,
        String sourceId,
        String contentHash,
        String previousEventId) {

    public SourceRevisionSelection {
        eventId = requireText(eventId, "eventId");
        workspaceId = requireText(workspaceId, "workspaceId");
        documentKey = requireText(documentKey, "documentKey");
        sourceId = requireText(sourceId, "sourceId");
        contentHash = requireText(contentHash, "contentHash");
        if (previousEventId != null) {
            previousEventId = requireText(previousEventId, "previousEventId");
            if (previousEventId.equals(eventId)) {
                throw new IllegalArgumentException("an event cannot be its own predecessor");
            }
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    /** A snapshot may hold one linear, workspace-local chain per document. */
    static void validateChainAndSources(String workspaceId, List<SourceRevisionSelection> events,
            List<Source> sources) {
        if (events.isEmpty()) {
            return;
        }
        Map<String, String> sourceHashes = new HashMap<>();
        for (Source source : sources) {
            if (workspaceId.equals(source.workspaceId())) {
                sourceHashes.put(source.id(), source.contentHash());
            }
        }
        Map<String, SourceRevisionSelection> byId = new HashMap<>();
        Map<String, SourceRevisionSelection> successor = new HashMap<>();
        Map<String, String> sourceOwners = new HashMap<>();
        Map<String, SourceRevisionSelection> roots = new HashMap<>();
        for (SourceRevisionSelection event : events) {
            if (!workspaceId.equals(event.workspaceId())
                    || !event.contentHash().equals(sourceHashes.get(event.sourceId()))) {
                throw new IllegalArgumentException("source revision must name matching local source bytes");
            }
            if (byId.putIfAbsent(event.eventId(), event) != null) {
                throw new IllegalArgumentException("duplicate source revision event");
            }
            String owner = sourceOwners.putIfAbsent(event.sourceId(), event.documentKey());
            if (owner != null && !owner.equals(event.documentKey())) {
                throw new IllegalArgumentException("source revision belongs to another document");
            }
            if (event.previousEventId() == null) {
                if (roots.putIfAbsent(event.documentKey(), event) != null) {
                    throw new IllegalArgumentException("duplicate revision root");
                }
            } else if (successor.putIfAbsent(event.previousEventId(), event) != null) {
                throw new IllegalArgumentException("competing source revision successors");
            }
        }
        for (SourceRevisionSelection event : events) {
            if (event.previousEventId() == null) {
                continue;
            }
            SourceRevisionSelection parent = byId.get(event.previousEventId());
            if (parent == null || !parent.documentKey().equals(event.documentKey())
                    || parent.sourceId().equals(event.sourceId())) {
                throw new IllegalArgumentException("revision predecessor is missing, foreign, or unchanged");
            }
        }
        Set<String> visited = new HashSet<>();
        for (SourceRevisionSelection root : roots.values()) {
            for (SourceRevisionSelection event = root; event != null;
                    event = successor.get(event.eventId())) {
                if (!visited.add(event.eventId())) {
                    throw new IllegalArgumentException("cyclic source revision history");
                }
            }
        }
        if (visited.size() != events.size()) {
            throw new IllegalArgumentException("detached source revision history");
        }
    }
}
