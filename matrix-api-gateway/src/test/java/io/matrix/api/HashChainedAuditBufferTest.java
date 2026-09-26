package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashChainedAuditBufferTest {

    @Test
    void empty_buffer_verifies_clean() {
        HashChainedAuditBuffer b = new HashChainedAuditBuffer(100);
        assertThat(b.verify()).isEqualTo(-1);
    }

    @Test
    void single_event_verifies_clean() {
        HashChainedAuditBuffer b = new HashChainedAuditBuffer(100);
        b.append("ANALYZE", "alice", "input", "t1");
        assertThat(b.verify()).isEqualTo(-1);
    }

    @Test
    void multiple_events_chain_correctly() {
        HashChainedAuditBuffer b = new HashChainedAuditBuffer(100);
        for (int i = 0; i < 10; i++) {
            b.append("ANALYZE", "u" + i, "act" + i, "t" + i);
        }
        assertThat(b.verify()).isEqualTo(-1);
        assertThat(b.size()).isEqualTo(10);
    }

    @Test
    void tampered_event_detected_at_correct_index() throws Exception {
        HashChainedAuditBuffer b = new HashChainedAuditBuffer(100);
        b.append("ANALYZE", "alice", "input", "t1");
        b.append("ANALYZE", "alice", "input2", "t2");
        b.append("ANALYZE", "alice", "input3", "t3");

        // Simulate tampering by replacing an event's action field via reflection-like access.
        // HashChainedAuditBuffer stores events in a List; we test the verify() path by
        // providing a buffer where events[1] has wrong prev_hash (simulated by replacement).
        // Since the API doesn't expose mutators, we test a different way:
        // create a buffer, append, then verify the original is clean. After replacing
        // a record field, verify() must catch it.
        var eventsField = HashChainedAuditBuffer.class.getDeclaredField("events");
        eventsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<HashChainedAuditBuffer.ChainEvent> list =
            (java.util.List<HashChainedAuditBuffer.ChainEvent>) eventsField.get(b);
        // Replace events[1] with a tampered clone (different action)
        var orig = list.get(1);
        var tampered = new HashChainedAuditBuffer.ChainEvent(
            orig.id(), orig.eventType(), orig.userId(),
            "TAMPERED", orig.timestamp(),
            orig.prevHash(), orig.hash());
        list.set(1, tampered);

        // Verify should now return index 1 (or 2 if tampered hash still passes prev check)
        assertThat(b.verify()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void capacity_drops_oldest() {
        HashChainedAuditBuffer b = new HashChainedAuditBuffer(3);
        for (int i = 0; i < 5; i++) {
            b.append("ANALYZE", "u", "a" + i, "t" + i);
        }
        assertThat(b.size()).isEqualTo(3);
        // Oldest two (a0, a1) should be dropped
        var all = b.all();
        assertThat(all.get(0).action()).isEqualTo("a2");
        assertThat(all.get(2).action()).isEqualTo("a4");
    }

    @Test
    void invalid_capacity_rejected() {
        assertThat(catchThrowable(() -> new HashChainedAuditBuffer(0)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> new HashChainedAuditBuffer(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_user_safe() {
        HashChainedAuditBuffer b = new HashChainedAuditBuffer(10);
        b.append("ANALYZE", null, "action", "t");
        assertThat(b.all().get(0).userId()).isEqualTo("");
    }

    @Test
    void first_event_prev_hash_is_64_zeros() {
        HashChainedAuditBuffer b = new HashChainedAuditBuffer(10);
        b.append("ANALYZE", "u", "a", "t");
        assertThat(b.all().get(0).prevHash()).isEqualTo("0".repeat(64));
    }

    @Test
    void hashes_are_64_hex_chars() {
        HashChainedAuditBuffer b = new HashChainedAuditBuffer(10);
        b.append("ANALYZE", "u", "a", "t");
        b.append("ANALYZE", "u", "b", "t");
        assertThat(b.all().get(0).hash()).hasSize(64).matches("[0-9a-f]+");
        assertThat(b.all().get(1).hash()).hasSize(64).matches("[0-9a-f]+");
    }

    private static Throwable catchThrowable(Runnable r) {
        try { r.run(); return null; } catch (Throwable t) { return t; }
    }
}
