package io.matrix.bir;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LineageLedger}: append-only hash chain with Ed25519
 * signatures (CONSTITUTION Article V). A valid chain verifies; any tampering
 * with content, links, or signatures is detected.
 */
class LineageLedgerTest {

    private static byte[] hash(String seed) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair ed25519() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Accesses the internal mutable chain for tamper simulation. */
    @SuppressWarnings("unchecked")
    private static List<LineageLedger.LedgerEntry> internalChain(LineageLedger ledger) {
        try {
            Field f = LineageLedger.class.getDeclaredField("chain");
            f.setAccessible(true);
            return (List<LineageLedger.LedgerEntry>) f.get(ledger);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static LineageLedger.LedgerEntry replaced(
            LineageLedger.LedgerEntry e, byte[] prevHash, byte[] contentHash) {
        return new LineageLedger.LedgerEntry(
                e.birId(), e.op(), e.timestamp(),
                prevHash, contentHash, e.phi(), e.signature());
    }

    @Test
    void appendBuildsLinkedChain() {
        LineageLedger ledger = new LineageLedger();
        LineageLedger.LedgerEntry e0 = ledger.append("bir:a", LineageLedger.Operation.CREATE, hash("a"), 0.0);
        LineageLedger.LedgerEntry e1 = ledger.append("bir:a", LineageLedger.Operation.TT_TO_BDD, hash("b"), 1.0);
        LineageLedger.LedgerEntry e2 = ledger.append("bir:b", LineageLedger.Operation.VERIFY, hash("c"), 2.0);

        assertThat(ledger.size()).isEqualTo(3);
        assertThat(e0.prevHash()).isNull();
        assertThat(e1.prevHash()).isEqualTo(e0.computeHash());
        assertThat(e2.prevHash()).isEqualTo(e1.computeHash());
        assertThat(e0.signature()).isNotEmpty();
        assertThat(ledger.publicKey()).isNotNull();
    }

    @Test
    void validChainVerifies() {
        LineageLedger ledger = new LineageLedger();
        for (LineageLedger.Operation op : LineageLedger.Operation.values()) {
            ledger.append("bir:" + op.name().toLowerCase(), op, hash(op.name()), 1.0);
        }
        assertThat(ledger.verifyChain()).isEmpty();
    }

    @Test
    void tamperedContentHashRejected() {
        LineageLedger ledger = new LineageLedger();
        ledger.append("bir:a", LineageLedger.Operation.CREATE, hash("a"), 0.0);
        ledger.append("bir:b", LineageLedger.Operation.CREATE, hash("b"), 1.0);
        ledger.append("bir:c", LineageLedger.Operation.CREATE, hash("c"), 2.0);

        // Tamper with the LAST entry's content hash (keeping its signature):
        // the signature check fails for it; earlier links stay intact.
        List<LineageLedger.LedgerEntry> chain = internalChain(ledger);
        LineageLedger.LedgerEntry last = chain.get(2);
        byte[] corrupted = hash("c").clone();
        corrupted[0] ^= 1;
        chain.set(2, replaced(last, last.prevHash(), corrupted));

        assertThat(ledger.verifyChain()).containsExactly(2);
    }

    @Test
    void tamperedChainLinkRejected() {
        LineageLedger ledger = new LineageLedger();
        ledger.append("bir:a", LineageLedger.Operation.CREATE, hash("a"), 0.0);
        ledger.append("bir:b", LineageLedger.Operation.TT_TO_BDD, hash("b"), 1.0);
        ledger.append("bir:c", LineageLedger.Operation.VERIFY, hash("c"), 2.0);

        // Replace entry 1's prevHash with garbage: the link check flags it,
        // and entry 2's link breaks too because computeHash covers prevHash.
        List<LineageLedger.LedgerEntry> chain = internalChain(ledger);
        LineageLedger.LedgerEntry e1 = chain.get(1);
        chain.set(1, replaced(e1, hash("forged"), e1.contentHash()));

        assertThat(ledger.verifyChain()).containsExactly(1, 2);
    }

    @Test
    void foreignSignatureRejected() {
        // An entry signed by a different key must not verify in this ledger.
        LineageLedger foreign = new LineageLedger(ed25519());
        LineageLedger.LedgerEntry forged =
                foreign.append("bir:x", LineageLedger.Operation.CREATE, hash("x"), 0.0);

        LineageLedger ledger = new LineageLedger(ed25519());
        internalChain(ledger).add(forged); // genesis position: prevHash null, link OK

        assertThat(ledger.verifyChain()).containsExactly(0);
    }

    @Test
    void getHistoryFiltersByBirId() {
        LineageLedger ledger = new LineageLedger();
        ledger.append("bir:a", LineageLedger.Operation.CREATE, hash("a0"), 0.0);
        ledger.append("bir:b", LineageLedger.Operation.CREATE, hash("b0"), 0.0);
        ledger.append("bir:a", LineageLedger.Operation.TT_TO_BDD, hash("a1"), 1.0);

        List<LineageLedger.LedgerEntry> history = ledger.getHistory("bir:a");
        assertThat(history).hasSize(2);
        assertThat(history).allSatisfy(e -> assertThat(e.birId()).isEqualTo("bir:a"));
        assertThat(ledger.getHistory("bir:missing")).isEmpty();
    }

    @Test
    void chainIsReadOnlyCopy() {
        LineageLedger ledger = new LineageLedger();
        ledger.append("bir:a", LineageLedger.Operation.CREATE, hash("a"), 0.0);
        List<LineageLedger.LedgerEntry> view = ledger.chain();
        assertThat(view).hasSize(1);
        ledger.append("bir:b", LineageLedger.Operation.CREATE, hash("b"), 1.0);
        assertThat(view).hasSize(1); // snapshot, not live
        assertThat(ledger.chain()).hasSize(2);
    }
}
