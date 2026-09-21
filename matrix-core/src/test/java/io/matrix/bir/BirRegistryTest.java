package io.matrix.bir;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link BirRegistry}: registration, lookup by ID and content
 * hash, duplicate detection, lineage metadata, listeners, and clear.
 *
 * <p>INV-4 provenance rejection is covered by {@code BirInvariantsTest}
 * and intentionally not duplicated here.
 */
class BirRegistryTest {

    private static TtForm tt(String tableBits, String provenance) {
        return new TtForm(2, new long[]{Long.parseLong(tableBits, 2)}, provenance, 1.0);
    }

    @Test
    void registerAndGetById() {
        BirRegistry registry = new BirRegistry();
        TtForm bir = tt("1000", "test-genesis");
        BirRegistry.Entry entry = registry.register("bir:and2", bir, "AND gate", 1.0, null);

        assertThat(registry.size()).isEqualTo(1);
        BirRegistry.Entry found = registry.get("bir:and2");
        assertThat(found).isSameAs(entry);
        assertThat(found.id()).isEqualTo("bir:and2");
        assertThat(found.name()).isEqualTo("AND gate");
        assertThat(found.phi()).isEqualTo(1.0);
        assertThat(found.bir()).isSameAs(bir);
        assertThat(found.provenance()).isEqualTo("test-genesis");
        assertThat(found.lineageHash()).isNull();
        assertThat(found.registeredAt()).isPositive();
    }

    @Test
    void getUnknownIdReturnsNull() {
        assertThat(new BirRegistry().get("bir:missing")).isNull();
    }

    @Test
    void duplicateIdRejected() {
        BirRegistry registry = new BirRegistry();
        registry.register("bir:a", tt("1000", "gen-a"), "a", 0.0, null);
        assertThatThrownBy(() -> registry.register("bir:a", tt("1110", "gen-b"), "b", 0.0, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bir:a");
        // Original entry is untouched
        assertThat(registry.get("bir:a").name()).isEqualTo("a");
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void nullArgumentsRejected() {
        BirRegistry registry = new BirRegistry();
        TtForm bir = tt("1000", "gen");
        assertThatThrownBy(() -> registry.register(null, bir, "n", 0.0, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registry.register("bir:x", null, "n", 0.0, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registry.register("bir:x", bir, null, 0.0, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void lookupByContentHashGroupsDuplicates() {
        // TtForm.contentHash covers only (k, table) — provenance is not
        // hashed, so two artifacts of the same function share a hash.
        BirRegistry registry = new BirRegistry();
        TtForm a = tt("1000", "gen-a");
        TtForm b = tt("1000", "gen-b");
        registry.register("bir:a", a, "a", 0.0, null);
        registry.register("bir:b", b, "b", 1.0, new byte[]{1, 2, 3});

        byte[] hash = a.contentHash();
        assertThat(b.contentHash()).isEqualTo(hash);
        assertThat(registry.containsHash(hash)).isTrue();

        List<BirRegistry.Entry> byHash = registry.getByHash(hash);
        assertThat(byHash).extracting(BirRegistry.Entry::id)
                .containsExactlyInAnyOrder("bir:a", "bir:b");
        assertThat(byHash).allSatisfy(e -> assertThat(e.contentHash()).isEqualTo(hash));
    }

    @Test
    void lookupByUnknownHash() {
        BirRegistry registry = new BirRegistry();
        registry.register("bir:a", tt("1000", "gen-a"), "a", 0.0, null);
        byte[] otherHash = tt("0111", "gen-c").contentHash();
        assertThat(registry.containsHash(otherHash)).isFalse();
        assertThat(registry.getByHash(otherHash)).isEmpty();
        assertThat(registry.getByHash(new byte[32])).isEmpty();
    }

    @Test
    void lineageHashStoredDefensively() {
        BirRegistry registry = new BirRegistry();
        byte[] lineage = {1, 2, 3};
        BirRegistry.Entry entry = registry.register("bir:a", tt("1000", "gen"), "a", 0.0, lineage);
        lineage[0] = 99; // mutate caller array after registration
        assertThat(entry.lineageHash()).containsExactly(1, 2, 3);
    }

    @Test
    void listAllAndSize() {
        BirRegistry registry = new BirRegistry();
        registry.register("bir:a", tt("1000", "gen"), "a", 0.0, null);
        registry.register("bir:b", tt("1110", "gen"), "b", 0.0, null);
        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.listAll()).extracting(BirRegistry.Entry::id)
                .containsExactlyInAnyOrder("bir:a", "bir:b");
    }

    @Test
    void listenerNotifiedOnRegistration() {
        BirRegistry registry = new BirRegistry();
        List<BirRegistry.Entry> seen = new CopyOnWriteArrayList<>();
        registry.addListener(seen::add);
        registry.register("bir:a", tt("1000", "gen"), "a", 0.0, null);
        registry.register("bir:b", tt("1110", "gen"), "b", 0.0, null);
        assertThat(seen).extracting(BirRegistry.Entry::id)
                .containsExactly("bir:a", "bir:b");
    }

    @Test
    void clearRemovesEntriesAndHashIndex() {
        BirRegistry registry = new BirRegistry();
        TtForm bir = tt("1000", "gen");
        registry.register("bir:a", bir, "a", 0.0, null);
        registry.clear();
        assertThat(registry.size()).isZero();
        assertThat(registry.listAll()).isEmpty();
        assertThat(registry.get("bir:a")).isNull();
        assertThat(registry.containsHash(bir.contentHash())).isFalse();
    }
}
