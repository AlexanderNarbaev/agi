package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W3 Part B Step 1 — EpisodeFeatureExtractor tests.
 *
 * <p>10 sample episodes encoded; verify:
 * - same text ⇒ same bit-vector (determinism)
 * - different text ⇒ different bit-vector (uniqueness)
 * - dimensionality matches constructor argument
 * - packed length = ceil(dim / 64)
 * - hamming/cosine distance computations are correct
 * - integration with EpisodicLog (real persistence)</p>
 */
class EpisodeFeatureExtractorTest {

    private static final List<String> SAMPLE_EPISODES = List.of(
        "Paris is the capital of France",
        "London is the capital of England",
        "Tokyo is the capital of Japan",
        "Moscow is the capital of Russia",
        "Berlin is the capital of Germany",
        "Paris is the capital of France",  // duplicate #1
        "Rome is the capital of Italy",
        "Madrid is the capital of Spain",
        "Beijing is the capital of China",
        "Paris is the capital of France"   // duplicate #2
    );

    @Test
    void dimensionality_matches_constructor_argument() {
        for (int d : new int[]{64, 128, 256, 512, 1024}) {
            EpisodeFeatureExtractor efe = new EpisodeFeatureExtractor(d);
            long[] vec = efe.encode("any text");
            assertThat(efe.dim()).isEqualTo(d);
            assertThat(vec.length).isEqualTo((d + 63) / 64);
        }
    }

    @Test
    void deterministic_encoding_same_input_same_vector() {
        EpisodeFeatureExtractor efe = new EpisodeFeatureExtractor();
        long[] a = efe.encode("Paris is the capital of France");
        long[] b = efe.encode("Paris is the capital of France");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void different_inputs_produce_different_vectors() {
        EpisodeFeatureExtractor efe = new EpisodeFeatureExtractor();
        long[] a = efe.encode("Paris is the capital of France");
        long[] b = efe.encode("London is the capital of England");
        // Hamming distance must be > 0 (some bits differ)
        assertThat(EpisodeFeatureExtractor.hammingDistance(a, b)).isGreaterThan(0);
    }

    @Test
    void duplicates_produce_identical_vectors() {
        // Episodes 1, 6, 10 are duplicates (same text)
        EpisodeFeatureExtractor efe = new EpisodeFeatureExtractor();
        long[] v1  = efe.encode(SAMPLE_EPISODES.get(0));
        long[] v6  = efe.encode(SAMPLE_EPISODES.get(5));
        long[] v10 = efe.encode(SAMPLE_EPISODES.get(9));
        assertThat(v1).isEqualTo(v6);
        assertThat(v6).isEqualTo(v10);
        // Hamming distance between duplicates = 0
        assertThat(EpisodeFeatureExtractor.hammingDistance(v1, v6)).isZero();
    }

    @Test
    void cosine_similarity_is_1_for_identical_vectors() {
        EpisodeFeatureExtractor efe = new EpisodeFeatureExtractor();
        long[] a = efe.encode("Hello world");
        long[] b = efe.encode("Hello world");
        assertThat(EpisodeFeatureExtractor.cosine(a, b)).isEqualTo(1.0);
    }

    @Test
    void cosine_similarity_is_0_for_disjoint_vectors() {
        // Build two vectors that share no bits
        long[] a = {1L};        // bit 0 set
        long[] b = {2L};        // bit 1 set
        assertThat(EpisodeFeatureExtractor.cosine(a, b)).isEqualTo(0.0);
    }

    @Test
    void hamming_distance_is_zero_for_identical_vectors() {
        long[] a = {0xFFFFFFFFFFFFFFFFL};
        long[] b = {0xFFFFFFFFFFFFFFFFL};
        assertThat(EpisodeFeatureExtractor.hammingDistance(a, b)).isZero();
    }

    @Test
    void hamming_distance_counts_all_set_bits() {
        long[] a = {0xFFFFFFFFFFFFFFFFL};   // 64 bits
        long[] b = {0x0000000000000000L};
        assertThat(EpisodeFeatureExtractor.hammingDistance(a, b)).isEqualTo(64);
    }

    @Test
    void integration_with_real_EpisodicLog(@TempDir Path tmp) throws Exception {
        // Real EpisodicLog → real EpisodeFeatureExtractor (integration, not mocks).
        Path dbPath = tmp.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(dbPath);
        log.append("Paris is the capital of France",
                   "Paris is the capital of France",
                   0.95, true, java.util.List.of());
        log.append("London is the capital of England",
                   "London is the capital of England",
                   0.93, true, java.util.List.of());

        EpisodeFeatureExtractor efe = new EpisodeFeatureExtractor();
        List<EpisodicLog.Entry> entries = log.readAll();
        assertThat(entries).hasSize(2);

        long[] f0 = efe.encode(entries.get(0));
        long[] f1 = efe.encode(entries.get(1));
        // Different entries ⇒ different features
        assertThat(EpisodeFeatureExtractor.hammingDistance(f0, f1)).isGreaterThan(0);
    }

    @Test
    void packed_length_is_ceil_dim_over_64() {
        assertThat(new EpisodeFeatureExtractor(64).packedLength()).isEqualTo(1);
        assertThat(new EpisodeFeatureExtractor(65).packedLength()).isEqualTo(2);
        assertThat(new EpisodeFeatureExtractor(128).packedLength()).isEqualTo(2);
        assertThat(new EpisodeFeatureExtractor(129).packedLength()).isEqualTo(3);
        assertThat(new EpisodeFeatureExtractor(256).packedLength()).isEqualTo(4);
    }

    @Test
    void invalid_dim_rejected() {
        assertThat(catchThrowable(() -> new EpisodeFeatureExtractor(0)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> new EpisodeFeatureExtractor(-1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> new EpisodeFeatureExtractor(100_000)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_entry_safe() {
        EpisodeFeatureExtractor efe = new EpisodeFeatureExtractor();
        long[] vec = efe.encode((EpisodicLog.Entry) null);
        assertThat(vec.length).isEqualTo(efe.packedLength());
        // All zeros (empty text)
        for (long l : vec) assertThat(l).isZero();
    }

    private static Throwable catchThrowable(Runnable r) {
        try { r.run(); return null; } catch (Throwable t) { return t; }
    }
}
