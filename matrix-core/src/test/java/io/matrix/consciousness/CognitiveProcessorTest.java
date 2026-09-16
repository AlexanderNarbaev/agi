package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveProcessorTest {

    @Test
    void processReturnsValidEmbedding() {
        CognitiveProcessor p = new CognitiveProcessor();
        CognitiveProcessor.ProcessingResult r = p.process(makeProfile(0.5));
        assertThat(r.embedding()).isNotNull();
        assertThat(r.embedding().length).isEqualTo(64);
    }

    @Test
    void processFirstProfileHasNoAttention() {
        CognitiveProcessor p = new CognitiveProcessor();
        CognitiveProcessor.ProcessingResult r = p.process(makeProfile(0.5));
        assertThat(r.attentionWeights()).isNotNull();
        assertThat(r.attentionWeights().length).isEqualTo(1);
    }

    @Test
    void processMultipleProfilesBuildsHistory() {
        CognitiveProcessor p = new CognitiveProcessor();
        for (int i = 0; i < 5; i++) p.process(makeProfile(i / 5.0));
        assertThat(p.historySize()).isEqualTo(5);
        assertThat(p.kvCacheContents().size()).isEqualTo(5);
        assertThat(p.windowContents().size()).isLessThanOrEqualTo(16);
    }

    @Test
    void speculationEnabledAfter3Profiles() {
        CognitiveProcessor p = new CognitiveProcessor(CognitiveProcessor.Config.defaults());
        for (int i = 0; i < 5; i++) p.process(makeProfile(i / 5.0));
        // By now speculation should be evaluated
        CognitiveProcessor.ProcessingResult last = p.process(makeProfile(1.0));
        assertThat(last.speculationSimilarity()).isGreaterThan(0.0);
    }

    @Test
    void configRecordedCorrectly() {
        CognitiveProcessor.Config config = new CognitiveProcessor.Config(
            128, 7L, 32, 11L, 8, 4, 2, 8, 3, 0.7, false, 5, 0.3);
        CognitiveProcessor p = new CognitiveProcessor(config);
        CognitiveProcessor.ProcessingResult r = p.process(makeProfile(0.5));
        assertThat(r.embedding().length).isEqualTo(128);
    }

    @Test
    void defaultConfigValid() {
        CognitiveProcessor.Config c = CognitiveProcessor.Config.defaults();
        assertThat(c.embeddingDim()).isEqualTo(64);
        assertThat(c.windowSinkCount()).isEqualTo(4);
        assertThat(c.windowSize()).isEqualTo(16);
    }

    @Test
    void emptyProcessorValid() {
        CognitiveProcessor p = new CognitiveProcessor();
        assertThat(p.historySize()).isEqualTo(0);
        assertThat(p.kvCacheContents()).isEmpty();
        assertThat(p.windowContents()).isEmpty();
    }

    @Test
    void nullProcessReturnsEmptyResult() {
        CognitiveProcessor p = new CognitiveProcessor();
        CognitiveProcessor.ProcessingResult r = p.process(null);
        assertThat(r.embedding()).isNull();
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
