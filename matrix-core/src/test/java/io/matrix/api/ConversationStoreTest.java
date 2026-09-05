package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RUN 104 — ConversationStore unit tests. */
class ConversationStoreTest {

    @Test
    void emptyStoreReturnsEmpty() {
        ConversationStore s = new ConversationStore(10, 60_000);
        assertThat(s.get("user-1")).isEmpty();
        assertThat(s.userCount()).isZero();
        assertThat(s.totalMessages()).isZero();
    }

    @Test
    void appendAndGet() {
        ConversationStore s = new ConversationStore(10, 60_000);
        s.append("user-1", ConversationStore.Role.USER, "hello");
        s.append("user-1", ConversationStore.Role.ASSISTANT, "hi");
        var history = s.get("user-1");
        assertThat(history).hasSize(2);
        assertThat(history.get(0).role()).isEqualTo(ConversationStore.Role.USER);
        assertThat(history.get(0).content()).isEqualTo("hello");
        assertThat(history.get(1).role()).isEqualTo(ConversationStore.Role.ASSISTANT);
        assertThat(history.get(1).content()).isEqualTo("hi");
        assertThat(s.totalMessages()).isEqualTo(2);
    }

    @Test
    void evictsOldestWhenAtCapacity() {
        ConversationStore s = new ConversationStore(3, 60_000);
        s.append("u", ConversationStore.Role.USER, "m1");
        s.append("u", ConversationStore.Role.USER, "m2");
        s.append("u", ConversationStore.Role.USER, "m3");
        s.append("u", ConversationStore.Role.USER, "m4");  // evicts m1
        var h = s.get("u");
        assertThat(h).hasSize(3);
        assertThat(h.get(0).content()).isEqualTo("m2");
        assertThat(h.get(2).content()).isEqualTo("m4");
        assertThat(s.totalEvictions()).isEqualTo(1);
    }

    @Test
    void differentUsersTrackedSeparately() {
        ConversationStore s = new ConversationStore(10, 60_000);
        s.append("alice", ConversationStore.Role.USER, "hi");
        s.append("bob", ConversationStore.Role.USER, "yo");
        assertThat(s.get("alice")).hasSize(1);
        assertThat(s.get("bob")).hasSize(1);
        assertThat(s.userCount()).isEqualTo(2);
    }

    @Test
    void clearUser() {
        ConversationStore s = new ConversationStore(10, 60_000);
        s.append("u1", ConversationStore.Role.USER, "x");
        s.append("u2", ConversationStore.Role.USER, "y");
        s.clear("u1");
        assertThat(s.get("u1")).isEmpty();
        assertThat(s.get("u2")).hasSize(1);
        assertThat(s.userCount()).isEqualTo(1);
    }

    @Test
    void clearAll() {
        ConversationStore s = new ConversationStore(10, 60_000);
        s.append("u1", ConversationStore.Role.USER, "x");
        s.append("u2", ConversationStore.Role.USER, "y");
        s.clearAll();
        assertThat(s.userCount()).isZero();
        assertThat(s.totalMessages()).isZero();
    }

    @Test
    void expirationByAge() throws Exception {
        ConversationStore s = new ConversationStore(10, 50);  // 50ms expiry
        s.append("u", ConversationStore.Role.USER, "x");
        Thread.sleep(80);
        s.append("u", ConversationStore.Role.USER, "y");  // triggers expiry
        // First message should be evicted
        var h = s.get("u");
        assertThat(h).hasSize(1);
        assertThat(h.get(0).content()).isEqualTo("y");
    }

    @Test
    void invalidCapacityRejected() {
        assertThatThrownBy(() -> new ConversationStore(0, 1000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConversationStore(-1, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accessors() {
        ConversationStore s = new ConversationStore(50, 30_000);
        assertThat(s.maxMessagesPerUser()).isEqualTo(50);
        assertThat(s.maxAgeMs()).isEqualTo(30_000);
    }

    @Test
    void messageTimestampSet() {
        ConversationStore s = new ConversationStore(10, 60_000);
        long before = System.currentTimeMillis();
        s.append("u", ConversationStore.Role.USER, "x");
        long after = System.currentTimeMillis();
        var h = s.get("u");
        assertThat(h.get(0).timestampMs()).isBetween(before, after);
    }
}
