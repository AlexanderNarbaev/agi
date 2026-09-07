package io.matrix.dialog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 194 — ConversationRecorder unit tests. */
class ConversationRecorderTest {

    @Test
    void emptyRecorder() {
        var r = new ConversationRecorder();
        assertThat(r.turnCount()).isZero();
        assertThat(r.snapshot().turns()).isEmpty();
    }

    @Test
    void recordAddsTurns() {
        var r = new ConversationRecorder();
        r.record("user", "Hello");
        r.record("assistant", "Hi!");
        assertThat(r.turnCount()).isEqualTo(2);
    }

    @Test
    void maxTurnsDropsOldest() {
        var r = new ConversationRecorder().withMaxTurns(3);
        for (int i = 0; i < 10; i++) {
            r.record("u", "msg-" + i);
        }
        assertThat(r.turnCount()).isEqualTo(3);
        // Most recent
        var recent = r.recent(3);
        assertThat(recent.get(2).text()).isEqualTo("msg-9");
    }

    @Test
    void recentReturnsLastN() {
        var r = new ConversationRecorder();
        for (int i = 0; i < 10; i++) r.record("u", "msg-" + i);
        var last5 = r.recent(5);
        assertThat(last5).hasSize(5);
        assertThat(last5.get(0).text()).isEqualTo("msg-5");
    }

    @Test
    void snapshotIsImmutable() {
        var r = new ConversationRecorder();
        r.record("u", "first");
        var snap = r.snapshot();
        // Modify recorder after snapshot
        r.record("u", "second");
        // Snapshot stays
        assertThat(snap.turns()).hasSize(1);
    }

    @Test
    void clearResetsRecorder() {
        var r = new ConversationRecorder();
        r.record("u", "x");
        r.record("u", "y");
        r.clear();
        assertThat(r.turnCount()).isZero();
    }

    @Test
    void turnIndexesIncrement() {
        var r = new ConversationRecorder();
        r.record("u", "a");
        r.record("u", "b");
        r.record("u", "c");
        List<ConversationRecorder.Turn> turns = r.snapshot().turns();
        assertThat(turns.get(0).turnIndex()).isZero();
        assertThat(turns.get(1).turnIndex()).isEqualTo(1);
        assertThat(turns.get(2).turnIndex()).isEqualTo(2);
    }

    @Test
    void rolesPreserved() {
        var r = new ConversationRecorder();
        r.record("user", "Q");
        r.record("assistant", "A");
        r.record("system", "S");
        var t = r.snapshot().turns();
        assertThat(t.get(0).role()).isEqualTo("user");
        assertThat(t.get(1).role()).isEqualTo("assistant");
        assertThat(t.get(2).role()).isEqualTo("system");
    }
}
