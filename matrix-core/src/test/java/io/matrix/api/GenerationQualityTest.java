package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 138 — GenerationQuality unit tests. */
class GenerationQualityTest {

    @Test
    void emptyTextMetrics() {
        GenerationQuality q = new GenerationQuality();
        var m = q.evaluate(null);
        assertThat(m.charCount()).isZero();
        assertThat(m.wordCount()).isZero();
        assertThat(m.uniqueWords()).isZero();
        assertThat(m.hasRepetition()).isFalse();
    }

    @Test
    void blankTextMetrics() {
        GenerationQuality q = new GenerationQuality();
        var m = q.evaluate("");
        assertThat(m.charCount()).isZero();
        assertThat(m.wordCount()).isZero();
    }

    @Test
    void simpleSentence() {
        GenerationQuality q = new GenerationQuality();
        var m = q.evaluate("Hello world");
        assertThat(m.charCount()).isEqualTo(11);
        assertThat(m.wordCount()).isEqualTo(2);
        assertThat(m.uniqueWords()).isEqualTo(2);
        assertThat(m.uniqueRatio()).isEqualTo(1.0);
        assertThat(m.hasRepetition()).isFalse();
    }

    @Test
    void uniqueRatioCalculation() {
        GenerationQuality q = new GenerationQuality();
        var m = q.evaluate("the the the cat");
        assertThat(m.wordCount()).isEqualTo(4);
        assertThat(m.uniqueWords()).isEqualTo(2);
        assertThat(m.uniqueRatio()).isEqualTo(0.5);
    }

    @Test
    void lineCount() {
        GenerationQuality q = new GenerationQuality();
        var m = q.evaluate("line1\nline2\nline3");
        assertThat(m.lines()).isEqualTo(3);
    }

    @Test
    void repetitionDetected() {
        GenerationQuality q = new GenerationQuality();
        String text = "hello world hello world hello world hello world hello world";
        assertThat(q.detectRepetition(text)).isTrue();
    }

    @Test
    void noRepetitionForShortText() {
        GenerationQuality q = new GenerationQuality();
        assertThat(q.detectRepetition("hi")).isFalse();
    }

    @Test
    void uniqueRatioZeroForEmptyWords() {
        GenerationQuality q = new GenerationQuality();
        var m = q.evaluate(" ");
        assertThat(m.uniqueRatio()).isZero();
    }
}
