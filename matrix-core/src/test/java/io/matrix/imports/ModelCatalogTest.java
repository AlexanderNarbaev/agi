package io.matrix.imports;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelCatalogTest {

    @Test
    void allTiersShouldHaveEntries() {
        assertThat(ModelCatalog.TINY).isNotEmpty();
        assertThat(ModelCatalog.MEDIUM).isNotEmpty();
        assertThat(ModelCatalog.LARGE).isNotEmpty();
        assertThat(ModelCatalog.HEAVY).isNotEmpty();
    }

    @Test
    void findByIdShouldReturnEntry() {
        ModelCatalog.Entry e = ModelCatalog.findById("Qwen/Qwen3-0.6B");
        assertThat(e).isNotNull();
        assertThat(e.tier()).isEqualTo(ModelCatalog.Tier.TINY);
        assertThat(e.estimatedBytes()).isPositive();
        assertThat(e.sizeHuman()).contains("MB");
    }

    @Test
    void findByIdShouldReturnNullForUnknown() {
        assertThat(ModelCatalog.findById("totally/not-a-real-model")).isNull();
    }

    @Test
    void summaryShouldCoverAllTiers() {
        String s = ModelCatalog.summary();
        assertThat(s).contains("TINY").contains("MEDIUM").contains("LARGE").contains("HEAVY");
    }

    @Test
    void totalBytesShouldAggregate() {
        long t = ModelCatalog.totalBytes(ModelCatalog.TINY);
        assertThat(t).isPositive();
        assertThat(t).isEqualTo(
                ModelCatalog.TINY.stream().mapToLong(ModelCatalog.Entry::estimatedBytes).sum());
    }

    @Test
    void sizeHumanShouldFormatGbAndMb() {
        ModelCatalog.Entry tiny = ModelCatalog.findById("Qwen/Qwen3-0.6B");
        ModelCatalog.Entry heavy = ModelCatalog.findById("meta-llama/Llama-3.1-70B-Instruct");
        assertThat(tiny.sizeHuman()).contains("MB");
        assertThat(heavy.sizeHuman()).contains("GB");
    }

    @Test
    void approxNeuronsShouldBePositive() {
        for (ModelCatalog.Entry e : ModelCatalog.allEntries()) {
            assertThat(e.approxNeurons()).isPositive();
        }
    }
}
