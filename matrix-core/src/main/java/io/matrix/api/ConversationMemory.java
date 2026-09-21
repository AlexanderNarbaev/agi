package io.matrix.api;

import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory conversation memory keyed by conversation id.
 *
 * <p>Stores recent turns (user + assistant) per conversation so multi-turn
 * chat can build on the prior context — same input → same context.
 * Bounded size to keep memory bounded.
 */
@ApplicationScoped
@Startup
public class ConversationMemory {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemory.class);

    /** Max turns kept per conversation (oldest evicted). */
    private static final int MAX_TURNS = 32;

    /** A single conversation turn. */
    public record Turn(String role, String text, long timestampMs) {}

    /** Per-conversation state. */
    public static final class Conversation {
        public final String id;
        public final Deque<Turn> turns = new ArrayDeque<>();
        public long lastAccessMs = System.currentTimeMillis();

        public Conversation(String id) { this.id = id; }

        public void append(String role, String text) {
            turns.addLast(new Turn(role, text, System.currentTimeMillis()));
            while (turns.size() > MAX_TURNS) turns.removeFirst();
            lastAccessMs = System.currentTimeMillis();
        }

        public String contextBlock() {
            StringBuilder sb = new StringBuilder();
            // Walk from oldest to newest so the context reads naturally
            for (Turn t : turns) {
                sb.append(t.role()).append(": ").append(t.text()).append('\n');
            }
            return sb.toString();
        }
    }

    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();
    private final Map<String, String> lastSummary = new ConcurrentHashMap<>();

    /** Boot trace. */
    void onStart(@Observes StartupEvent ev) {
        log.info("ConversationMemory ready (max-turns-per-conversation={})", MAX_TURNS);
    }

    /** Get-or-create a conversation by id. */
    public Conversation getOrCreate(String id) {
        if (id == null || id.isBlank()) {
            id = "conv-" + System.currentTimeMillis();
        }
        return conversations.computeIfAbsent(id, Conversation::new);
    }

    /** Append a turn to the given conversation. */
    public void append(String id, String role, String text) {
        if (id == null || id.isBlank()) return;
        getOrCreate(id).append(role, text);
    }

    /** Get the context block (all turns joined). Empty if no convo. */
    public String contextBlock(String id) {
        Conversation c = conversations.get(id);
        return c == null ? "" : c.contextBlock();
    }

    /** List recent conversations (for debugging). */
    public List<Map<String, Object>> listConversations() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Conversation c : conversations.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id);
            m.put("turns", c.turns.size());
            m.put("last_access_ms", c.lastAccessMs);
            out.add(m);
        }
        return out;
    }

    public int conversationCount() { return conversations.size(); }

    /** Get all turns (most recent first) for a given conversation. */
    public List<Turn> turns(String id) {
        Conversation c = conversations.get(id);
        if (c == null) return List.of();
        List<Turn> out = new ArrayList<>(c.turns);
        Collections.reverse(out);
        return out;
    }
}
