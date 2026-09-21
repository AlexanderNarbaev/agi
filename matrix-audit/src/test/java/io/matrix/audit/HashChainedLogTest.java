package io.matrix.audit;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HashChainedLogTest {

    @Test
    void testAppendFirstEventUsesZeroPrevHash() {
        HashChainedLog log = new HashChainedLog();
        AuditEvent event = log.append(
            AuditEvent.builder()
                .userId("user-1")
                .action("LOGIN")
                .target("alice@example.com")
        );

        assertNotNull(event.hash());
        assertEquals(64, event.hash().length(), "SHA-256 hex should be 64 chars");
        assertEquals("0".repeat(64), event.prevHash());
        assertEquals(1, log.size());
    }

    @Test
    void testSecondEventLinksToFirst() {
        HashChainedLog log = new HashChainedLog();
        AuditEvent first = log.append(AuditEvent.builder().action("A").userId("u1"));
        AuditEvent second = log.append(AuditEvent.builder().action("B").userId("u1"));

        assertEquals(first.hash(), second.prevHash());
    }

    @Test
    void testVerifyIntactChain() {
        HashChainedLog log = new HashChainedLog();
        log.append(AuditEvent.builder().action("A").userId("u1"));
        log.append(AuditEvent.builder().action("B").userId("u1"));
        log.append(AuditEvent.builder().action("C").userId("u1"));

        assertNull(log.verify(), "Chain should be intact");
    }

    @Test
    void testDetectTampering() {
        HashChainedLog log = new HashChainedLog();
        log.append(AuditEvent.builder().action("A").userId("u1"));
        AuditEvent tampered = log.append(AuditEvent.builder().action("B").userId("u1"));

        // Mutate an existing event directly via the list (simulate attacker)
        // ... but events are immutable records. Instead simulate tampering
        // by manually constructing a chain with a broken prev_hash.
        HashChainedLog fakeLog = new HashChainedLog(List.of(
            AuditEvent.builder()
                .userId("u1")
                .action("A")
                .prevHash("wrong_value")
                .hash("0".repeat(64))
                .build()
        ));

        assertNotNull(fakeLog.verify(), "Should detect tampering");
    }

    @Test
    void testFindByUser() {
        HashChainedLog log = new HashChainedLog();
        log.append(AuditEvent.builder().userId("u1").action("A"));
        log.append(AuditEvent.builder().userId("u2").action("B"));
        log.append(AuditEvent.builder().userId("u1").action("C"));

        List<AuditEvent> u1Events = log.findByUser("u1");
        assertEquals(2, u1Events.size());
    }

    @Test
    void testRecentReturnsNewestFirst() {
        HashChainedLog log = new HashChainedLog();
        log.append(AuditEvent.builder().userId("u1").action("A"));
        log.append(AuditEvent.builder().userId("u1").action("B"));
        log.append(AuditEvent.builder().userId("u1").action("C"));

        List<AuditEvent> recent = log.recent(2);
        assertEquals(2, recent.size());
        assertEquals("C", recent.get(0).action());
        assertEquals("B", recent.get(1).action());
    }

    @Test
    void testEmptyLogSize() {
        HashChainedLog log = new HashChainedLog();
        assertEquals(0, log.size());
        assertNull(log.verify());
    }

    @Test
    void testFindById() {
        HashChainedLog log = new HashChainedLog();
        AuditEvent e = log.append(AuditEvent.builder().action("A").userId("u1"));

        assertTrue(log.findById(e.eventId()).isPresent());
        assertTrue(log.findById("evt_nonexistent").isEmpty());
    }

    @Test
    void testHashIsDeterministic() {
        AuditEvent e = AuditEvent.builder()
            .userId("u1")
            .action("A")
            .prevHash("0".repeat(64))
            .build();
        String h1 = HashChainedLog.computeHash(e);
        String h2 = HashChainedLog.computeHash(e);
        assertEquals(h1, h2);
    }

    @Test
    void testHashChangesWithPayload() {
        AuditEvent e1 = AuditEvent.builder().userId("u1").action("A").prevHash("0".repeat(64)).build();
        AuditEvent e2 = AuditEvent.builder().userId("u1").action("B").prevHash("0".repeat(64)).build();
        assertNotEquals(HashChainedLog.computeHash(e1), HashChainedLog.computeHash(e2));
    }

    @Test
    void testAllReturnsImmutable() {
        HashChainedLog log = new HashChainedLog();
        log.append(AuditEvent.builder().userId("u1").action("A"));
        List<AuditEvent> all = log.all();
        assertThrows(UnsupportedOperationException.class, () -> all.clear());
    }
}
