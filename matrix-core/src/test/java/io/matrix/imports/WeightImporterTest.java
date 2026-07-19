package io.matrix.imports;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeightImporterTest {

    @Test
    void humanBytesShouldMatchAdaptiveSelector() {
        // Cross-checking the delegating helper used in reports.
        assertThat(WeightImporter.humanBytes(1024)).isEqualTo(AdaptiveSelector.humanBytes(1024));
        assertThat(WeightImporter.humanBytes(0)).isEqualTo("0 B");
    }

    @Test
    void importerShouldBeConstructibleWithAllDefaults(@org.junit.jupiter.api.io.TempDir Path tmp) {
        // Smoke test: nothing is downloaded — we just exercise the constructors.
        WeightImporter imp = new WeightImporter(tmp);
        assertThat(imp).isNotNull();
        // Adapter sanity: default source = HuggingFaceHubSource
        assertThat(((HuggingFaceHubSource) ensureField(imp, "source")).sourceId()).isEqualTo("huggingface");
    }

    @Test
    void shouldConstructWithCustomCollaborators(@org.junit.jupiter.api.io.TempDir Path tmp) {
        WeightSource mockSrc = new WeightSource() {
            @Override public String sourceId() { return "test"; }
            @Override public ProbeResult probe(String modelId) {
                return new ProbeResult(modelId, true, 0L);
            }
            @Override public DownloadResult download(String modelId, Path target) {
                throw new WeightSource.WeightSourceException("mock refuses");
            }
        };
        AdaptiveSelector sel = new AdaptiveSelector(0);  // 0 = no budget (will yield 0 picks)
        WeightImporter imp = new WeightImporter(mockSrc, sel, new SafetensorsReader(),
                new TensorProjector(), tmp);
        assertThatThrownBy(imp::ingestAll)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("budget");
    }

    /** Reflective helper that leniently reads a private field for assertions. */
    private static Object ensureField(WeightImporter imp, String name) {
        try {
            java.lang.reflect.Field f = WeightImporter.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(imp);
        } catch (Exception e) {
            throw new AssertionError("Field " + name + " not found", e);
        }
    }
}
