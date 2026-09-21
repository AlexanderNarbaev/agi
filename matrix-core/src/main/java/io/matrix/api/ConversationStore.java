package io.matrix.api;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RUN 104 — Per-user conversation history store.
 *
 * <p>Maintains a bounded ring buffer of recent messages per user id,
 * enabling the chat endpoint to recall multi-turn context.
 */
public final class ConversationStore {

    private final int maxMessagesPerUser;
    private final long maxAgeMs;
    private final Map<String, Deque<Message>> users = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong totalMessages =
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong totalEvictions =
            new java.util.concurrent.atomic.AtomicLong();

    public ConversationStore(int maxMessagesPerUser, long maxAgeMs) {
        if (maxMessagesPerUser <= 0) {
            throw new IllegalArgumentException("max must be > 0");
        }
        this.maxMessagesPerUser = maxMessagesPerUser;
        this.maxAgeMs = maxAgeMs;
    }

    /** Append a message to a user's history. */
    public void append(String userId, Role role, String content) {
        if (userId == null || content == null) return;
        long now = System.currentTimeMillis();
        Deque<Message> history = users.computeIfAbsent(userId,
                k -> new ArrayDeque<>(maxMessagesPerUser));
        synchronized (history) {
            // Drop expired messages
            while (!history.isEmpty() && now - history.peekFirst().timestampMs > maxAgeMs) {
                history.pollFirst();
                totalEvictions.incrementAndGet();
            }
            // Drop oldest if at capacity
            while (history.size() >= maxMessagesPerUser) {
                history.pollFirst();
                totalEvictions.incrementAndGet();
            }
            history.addLast(new Message(role, content, now));
            totalMessages.incrementAndGet();
        }
    }

    /** Get user's history as immutable list. */
    public java.util.List<Message> get(String userId) {
        Deque<Message> history = users.get(userId);
        if (history == null) return java.util.List.of();
        synchronized (history) {
            return new java.util.ArrayList<>(history);
        }
    }

    /** Clear a user's history. */
    public void clear(String userId) {
        Deque<Message> removed = users.remove(userId);
        if (removed != null) {
            totalMessages.addAndGet(-removed.size());
        }
    }

    /** Clear all users. */
    public void clearAll() {
        long removed = 0;
        for (Deque<Message> h : users.values()) removed += h.size();
        users.clear();
        totalMessages.addAndGet(-removed);
    }

    public int userCount() { return users.size(); }
    public long totalMessages() { return totalMessages.get(); }
    public long totalEvictions() { return totalEvictions.get(); }
    public int maxMessagesPerUser() { return maxMessagesPerUser; }
    public long maxAgeMs() { return maxAgeMs; }

    public enum Role { USER, ASSISTANT, SYSTEM }

    public record Message(Role role, String content, long timestampMs) {}
}
