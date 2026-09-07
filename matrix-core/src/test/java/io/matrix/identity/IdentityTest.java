package io.matrix.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RUN 221 — Identity unit tests. */
class IdentityTest {

    @Test
    void identityStoresNodeId() {
        var i = new Identity("node-1");
        assertThat(i.nodeId()).isEqualTo("node-1");
        assertThat(i.createdAtMillis()).isGreaterThan(0);
    }

    @Test
    void equalsBasedOnNodeId() {
        var a = new Identity("node-X");
        var b = new Identity("node-X");
        var c = new Identity("node-Y");
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void hashCodeIsDeterministic() {
        var a = new Identity("node-X");
        var b = new Identity("node-X");
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void nullNodeIdThrows() {
        assertThatThrownBy(() -> new Identity(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankNodeIdThrows() {
        assertThatThrownBy(() -> new Identity(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Identity("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
