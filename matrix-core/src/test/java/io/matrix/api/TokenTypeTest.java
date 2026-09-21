package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 135 — TokenType unit tests. */
class TokenTypeTest {

    @Test
    void specialTokensClassified() {
        assertThat(TokenType.classify("<|im_start|>"))
                .isEqualTo(TokenType.SPECIAL);
        assertThat(TokenType.classify("<|im_end|>"))
                .isEqualTo(TokenType.SPECIAL);
        assertThat(TokenType.classify("<|endoftext|>"))
                .isEqualTo(TokenType.SPECIAL);
    }

    @Test
    void punctuationClassified() {
        assertThat(TokenType.classify(".")).isEqualTo(TokenType.PUNCTUATION);
        assertThat(TokenType.classify(",")).isEqualTo(TokenType.PUNCTUATION);
        assertThat(TokenType.classify("!?")).isEqualTo(TokenType.PUNCTUATION);
    }

    @Test
    void numbersClassified() {
        assertThat(TokenType.classify("42")).isEqualTo(TokenType.NUMBER);
        assertThat(TokenType.classify("0")).isEqualTo(TokenType.NUMBER);
        assertThat(TokenType.classify("12345")).isEqualTo(TokenType.NUMBER);
    }

    @Test
    void whitespaceClassified() {
        assertThat(TokenType.classify(" ")).isEqualTo(TokenType.WHITESPACE);
        assertThat(TokenType.classify("\n")).isEqualTo(TokenType.WHITESPACE);
        assertThat(TokenType.classify("\t")).isEqualTo(TokenType.WHITESPACE);
    }

    @Test
    void wordsClassified() {
        assertThat(TokenType.classify("hello")).isEqualTo(TokenType.WORD);
        assertThat(TokenType.classify("Ġhello")).isEqualTo(TokenType.WORD);
        assertThat(TokenType.classify("Python")).isEqualTo(TokenType.WORD);
    }

    @Test
    void unknownCases() {
        assertThat(TokenType.classify(null)).isEqualTo(TokenType.UNKNOWN);
        assertThat(TokenType.classify("")).isEqualTo(TokenType.UNKNOWN);
    }

    @Test
    void enumValuesExist() {
        assertThat(TokenType.values()).hasSize(6);
        assertThat(TokenType.valueOf("WORD")).isNotNull();
        assertThat(TokenType.valueOf("SPECIAL")).isNotNull();
    }
}
