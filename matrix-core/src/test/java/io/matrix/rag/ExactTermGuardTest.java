package io.matrix.rag;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExactTermGuardTest {

    // --- Extraction ---

    @Nested
    class ExtractTechnicalTerms {

        @Test
        void shouldExtractCamelCase() {
            List<String> terms = ExactTermGuard.extractTechnicalTerms(
                    "How to use getUserId and UserProfile?");

            assertThat(terms).contains("getUserId", "UserProfile");
        }

        @Test
        void shouldExtractUpperCaseConstant() {
            List<String> terms = ExactTermGuard.extractTechnicalTerms(
                    "Configure MAX_RETRY_COUNT");

            assertThat(terms).contains("MAX_RETRY_COUNT");
        }

        @Test
        void shouldExtractSnakeCase() {
            List<String> terms = ExactTermGuard.extractTechnicalTerms(
                    "The user_service config");

            assertThat(terms).contains("user_service");
        }

        @Test
        void shouldExtractQuotedStrings() {
            List<String> terms = ExactTermGuard.extractTechnicalTerms(
                    "Search for \"vector database\" and 'neural net'");

            assertThat(terms).contains("vector database", "neural net");
        }

        @Test
        void shouldExtractEndpoints() {
            List<String> terms = ExactTermGuard.extractTechnicalTerms(
                    "Call /api/v1/users and /health");

            assertThat(terms).contains("/api/v1/users", "/health");
        }

        @Test
        void shouldReturnEmptyForNullQuery() {
            List<String> terms = ExactTermGuard.extractTechnicalTerms(null);
            assertThat(terms).isEmpty();
        }

        @Test
        void shouldReturnEmptyForBlankQuery() {
            List<String> terms = ExactTermGuard.extractTechnicalTerms("   ");
            assertThat(terms).isEmpty();
        }

        @Test
        void shouldReturnEmptyForPlainEnglish() {
            List<String> terms = ExactTermGuard.extractTechnicalTerms(
                    "what is the best way to do this");
            assertThat(terms).isEmpty();
        }

        @Test
        void shouldNotExtractSentenceCapitalizedWords() {
            List<String> terms = ExactTermGuard.extractTechnicalTerms(
                    "Use the TokenManager to handle auth");

            assertThat(terms).contains("TokenManager");
            assertThat(terms).doesNotContain("Use", "the", "to", "handle", "auth");
        }

        @Test
        void shouldNotMatchSubstringInOtherWords() {
            // "Use" as a standalone word should not match inside "getUserId"
            var result = ExactTermGuard.check(
                    "Use the getUserId method",
                    List.of("getUserId is defined here"));

            // "Use" is not a technical token (sentence-capital), so no terms to guard
            assertThat(result.allowed()).isTrue();
        }
    }

    // --- Guard Check ---

    @Nested
    class Check {

        @Test
        void shouldAllowGenerationWhenAllTermsMatch() {
            var result = ExactTermGuard.check(
                    "Use getUserId with MAX_RETRY_COUNT",
                    List.of("getUserId is defined in AuthService",
                            "MAX_RETRY_COUNT controls retry logic"));

            assertThat(result.allowed()).isTrue();
            assertThat(result.matchedTerms()).contains("getUserId", "MAX_RETRY_COUNT");
            assertThat(result.missingTerms()).isEmpty();
            assertThat(result.confidence()).isEqualTo(1.0);
        }

        @Test
        void shouldRefuseWhenCriticalTermsMissing() {
            var result = ExactTermGuard.check(
                    "Use getUserId with MAX_RETRY_COUNT",
                    List.of("Some unrelated text...",
                            "getUserId is defined in AuthService"));

            assertThat(result.allowed()).isFalse();
            assertThat(result.matchedTerms()).contains("getUserId");
            assertThat(result.missingTerms()).contains("MAX_RETRY_COUNT");
            assertThat(result.confidence()).isLessThan(1.0);
        }

        @Test
        void shouldAllowEmptyQuery() {
            var result = ExactTermGuard.check("", List.of("anything"));
            assertThat(result.allowed()).isTrue();
            assertThat(result.confidence()).isEqualTo(1.0);
        }

        @Test
        void shouldAllowNullQuery() {
            var result = ExactTermGuard.check(null, List.of("anything"));
            assertThat(result.allowed()).isTrue();
            assertThat(result.confidence()).isEqualTo(1.0);
        }

        @Test
        void shouldHandleNullContextChunks() {
            var result = ExactTermGuard.check("test CamelCase", null);
            assertThat(result.allowed()).isFalse();
            assertThat(result.missingTerms()).contains("CamelCase");
        }

        @Test
        void shouldHandleEmptyContextChunks() {
            var result = ExactTermGuard.check("test CamelCase",
                    Collections.emptyList());
            assertThat(result.allowed()).isFalse();
        }

        @Test
        void shouldReturnConfidenceAsFraction() {
            var result = ExactTermGuard.check(
                    "Use getUserId with MAX_RETRY_COUNT and user_service",
                    List.of("getUserId is defined", "user_service module"));

            assertThat(result.matchedTerms()).hasSize(2);
            assertThat(result.missingTerms()).hasSize(1);
            assertThat(result.confidence()).isEqualTo(2.0 / 3.0);
        }

        @Test
        void guardResultShouldBeImmutable() {
            var result = ExactTermGuard.check("CamelCase", List.of("CamelCase example"));
            assertThatThrownBy(() -> result.matchedTerms().add("new"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> result.missingTerms().add("new"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
