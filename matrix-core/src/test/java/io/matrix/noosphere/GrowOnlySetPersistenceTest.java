package io.matrix.noosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W39 — persistence behaviour of {@link GrowOnlySet}.
 *
 * <p>The defect these tests exist for: {@code toJson()} used to write a bare
 * JSON array of the ELEMENTS only, and {@code fromJson()} read a list of
 * elements and created an EMPTY tombstone set. A key tombstoned before a
 * restart was therefore eligible for re-introduction afterwards — the deletion
 * history did not survive persistence, which is the one thing a tombstone is
 * for.
 */
class GrowOnlySetPersistenceTest {

    @Test
    @DisplayName("tombstones survive a round trip")
    void tombstonesSurviveARoundTrip() {
        GrowOnlySet original = new GrowOnlySet(Set.of("keep", "drop"))
                .tombstoneAt("drop", 1L);

        GrowOnlySet restored = GrowOnlySet.fromJson(original.toJson());

        assertThat(restored.tombstones()).containsExactly("drop");
        assertThat(restored.elements()).containsExactly("keep");
        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("a tombstoned key cannot be re-introduced after a restart")
    void aTombstonedKeyCannotBeReintroducedAfterARestart() {
        GrowOnlySet original = new GrowOnlySet(Set.of("drop")).tombstoneAt("drop", 1L);

        // A peer that still holds the key, as a replica that missed the
        // tombstone would.
        GrowOnlySet peer = new GrowOnlySet(Set.of("drop"));

        GrowOnlySet restored = GrowOnlySet.fromJson(original.toJson());
        GrowOnlySet merged = restored.merge(peer);

        assertThat(merged.elements()).doesNotContain("drop");
        assertThat(merged.tombstones()).containsExactly("drop");
    }

    @Test
    @DisplayName("the legacy array format is still readable")
    void theLegacyFormatIsStillReadable() {
        // The format written before RECON-W39: a bare JSON array of elements.
        GrowOnlySet restored = GrowOnlySet.fromJson("[\"a\",\"b\"]");

        assertThat(restored.elements()).containsExactlyInAnyOrder("a", "b");
        assertThat(restored.tombstones()).isEmpty();
    }

    @Test
    @DisplayName("an unknown schema version is rejected, not reinterpreted")
    void unknownSchemaVersionIsRejected() {
        assertThatThrownBy(() -> GrowOnlySet.fromJson("{\"schemaVersion\":99,\"elements\":[]}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");
        assertThatThrownBy(() -> GrowOnlySet.fromJson("{\"elements\":[\"a\"]}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");
    }

    @Test
    @DisplayName("a malformed payload is rejected")
    void malformedPayloadIsRejected() {
        assertThatThrownBy(() -> GrowOnlySet.fromJson("not json at all"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GrowOnlySet.fromJson("{\"schemaVersion\":2,\"elements\":\"nope\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("elements");
    }

    @Test
    @DisplayName("a contradictory payload is rejected")
    void contradictoryPayloadIsRejected() {
        String json = "{\"schemaVersion\":2,\"elements\":[\"k\"],\"tombstones\":[\"k\"]}";
        assertThatThrownBy(() -> GrowOnlySet.fromJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Contradictory");
    }

    @Test
    @DisplayName("the serialisation is deterministic")
    void serialisationIsDeterministic() {
        GrowOnlySet set = new GrowOnlySet(Set.of("z", "a", "m")).tombstoneAt("q", 1L);

        String first = set.toJson();
        String second = set.toJson();

        assertThat(first).isEqualTo(second);
        // Sorted, so the bytes are stable across runs and hashable.
        assertThat(first.indexOf("\"a\"")).isLessThan(first.indexOf("\"m\""));
        assertThat(first.indexOf("\"m\"")).isLessThan(first.indexOf("\"z\""));
    }

    @Test
    @DisplayName("the payload carries its schema version")
    void thePayloadCarriesItsSchemaVersion() {
        String json = new GrowOnlySet(Set.of("a")).toJson();
        assertThat(json).contains("\"schemaVersion\":2");
    }
}
