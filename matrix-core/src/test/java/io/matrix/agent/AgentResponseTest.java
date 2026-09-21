package io.matrix.agent;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentResponseTest {

    // ── Builder ──

    @Test
    void builderShouldGenerateUuid() {
        var response = AgentResponse.builder()
                .answer("test")
                .build(0);

        assertThat(response.requestId()).isNotNull();
        assertThat(response.requestId()).isInstanceOf(UUID.class);
    }

    @Test
    void builderShouldAcceptCustomUuid() {
        var customId = UUID.randomUUID();
        var response = AgentResponse.builder()
                .requestId(customId)
                .answer("test")
                .build(0);

        assertThat(response.requestId()).isEqualTo(customId);
    }

    @Test
    void builderShouldSetAnswer() {
        var response = AgentResponse.builder()
                .answer("The answer is 42")
                .build(0);

        assertThat(response.answer()).isEqualTo("The answer is 42");
    }

    @Test
    void builderShouldDefaultAnswerToEmpty() {
        var response = AgentResponse.builder().build(0);
        assertThat(response.answer()).isEmpty();
    }

    @Test
    void builderShouldRejectNullUuid() {
        assertThatThrownBy(() -> AgentResponse.builder().requestId(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builderShouldRejectNullAnswer() {
        assertThatThrownBy(() -> AgentResponse.builder().answer(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── Sources ──

    @Test
    void sourcesShouldBeStoredInOrder() {
        var s1 = new AgentResponse.SourceInfo("doc1", "/a/b", 0, 0.95, "chunk-a");
        var s2 = new AgentResponse.SourceInfo("doc2", "/c/d", 1, 0.82, "chunk-b");

        var response = AgentResponse.builder()
                .answer("answer")
                .addSource(s1)
                .addSource(s2)
                .build(0);

        assertThat(response.sources()).hasSize(2);
        assertThat(response.sources().get(0).document()).isEqualTo("doc1");
        assertThat(response.sources().get(1).document()).isEqualTo("doc2");
    }

    @Test
    void sourcesShouldBeImmutableCopy() {
        var builder = AgentResponse.builder()
                .answer("answer")
                .addSource(new AgentResponse.SourceInfo("d", "/p", 0, 0.9, "c"));
        var response = builder.build(0);

        assertThatThrownBy(() -> response.sources().add(
                new AgentResponse.SourceInfo("d2", "/p2", 0, 0.5, "c2")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void addSourceWithFieldsShouldWork() {
        var response = AgentResponse.builder()
                .answer("x")
                .addSource("doc", "/path", 3, 0.75, "hello world")
                .build(0);

        var s = response.sources().get(0);
        assertThat(s.document()).isEqualTo("doc");
        assertThat(s.path()).isEqualTo("/path");
        assertThat(s.chunkIndex()).isEqualTo(3);
        assertThat(s.score()).isEqualTo(0.75);
        assertThat(s.chunk()).isEqualTo("hello world");
    }

    @Test
    void sourceInfoShouldRejectNullFields() {
        assertThatThrownBy(() ->
                new AgentResponse.SourceInfo(null, "/p", 0, 0.5, "c"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                new AgentResponse.SourceInfo("d", null, 0, 0.5, "c"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                new AgentResponse.SourceInfo("d", "/p", 0, 0.5, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sourceInfoShouldRejectNegativeChunkIndex() {
        assertThatThrownBy(() ->
                new AgentResponse.SourceInfo("d", "/p", -1, 0.5, "c"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Timing ──

    @Test
    void timingBreakdownShouldBeConsistent() {
        var response = AgentResponse.builder()
                .answer("test")
                .recordPhase("retrieval", 100)
                .recordPhase("filtering", 50)
                .recordPhase("reasoning", 200)
                .recordPhase("generation", 80)
                .build(430);

        var t = response.timings();
        assertThat(t.retrievalMs()).isEqualTo(100);
        assertThat(t.filteringMs()).isEqualTo(50);
        assertThat(t.reasoningMs()).isEqualTo(200);
        assertThat(t.generationMs()).isEqualTo(80);
        assertThat(t.totalMs()).isEqualTo(430);
        assertThat(t.sumOfPhases()).isEqualTo(430);
    }

    @Test
    void totalShouldBeAtLeastSumOfPhases() {
        var response = AgentResponse.builder()
                .answer("test")
                .recordPhase("retrieval", 100)
                .recordPhase("filtering", 50)
                .recordPhase("reasoning", 200)
                .recordPhase("generation", 80)
                .build(500);

        var t = response.timings();
        assertThat(t.totalMs()).isGreaterThanOrEqualTo(t.sumOfPhases());
    }

    @Test
    void timingInfoShouldRejectNegativeValues() {
        assertThatThrownBy(() ->
                new AgentResponse.TimingInfo(-1, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordPhaseShouldRejectNegativeElapsed() {
        var builder = AgentResponse.builder().answer("x");
        assertThatThrownBy(() -> builder.recordPhase("retrieval", -5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordPhaseShouldRejectUnknownPhase() {
        var builder = AgentResponse.builder().answer("x");
        assertThatThrownBy(() -> builder.recordPhase("unknown", 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startFinishShouldTrackWallClockDuration() throws InterruptedException {
        var builder = AgentResponse.builder()
                .answer("timed")
                .startTiming();
        Thread.sleep(100);
        var response = builder.finish();

        assertThat(response.durationMs()).isGreaterThanOrEqualTo(50);
        assertThat(response.timings().totalMs()).isEqualTo(response.durationMs());
    }

    @Test
    void finishWithoutStartTimingShouldThrow() {
        assertThatThrownBy(() -> AgentResponse.builder().answer("x").finish())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void durationShouldMatchTimingTotal() {
        var response = AgentResponse.builder()
                .answer("test")
                .recordPhase("retrieval", 100)
                .recordPhase("reasoning", 50)
                .build(150);

        assertThat(response.durationMs()).isEqualTo(150);
        assertThat(response.timings().totalMs()).isEqualTo(150);
    }

    // ── JSON roundtrip ──

    @Test
    void jsonRoundtripShouldPreserveData() {
        var original = AgentResponse.builder()
                .answer("42 is the answer")
                .addSource("doc.md", "/docs/doc.md", 0, 0.99, "Life, the Universe, and Everything")
                .addSource("ref.md", "/docs/ref.md", 2, 0.85, "Deep Thought calculations")
                .recordPhase("retrieval", 120)
                .recordPhase("filtering", 45)
                .recordPhase("reasoning", 300)
                .recordPhase("generation", 90)
                .build(555);

        String json = original.toJson();
        var restored = AgentResponse.fromJson(json);

        assertThat(restored.requestId()).isEqualTo(original.requestId());
        assertThat(restored.answer()).isEqualTo(original.answer());
        assertThat(restored.sources()).hasSize(original.sources().size());
        for (int i = 0; i < original.sources().size(); i++) {
            assertThat(restored.sources().get(i).document())
                    .isEqualTo(original.sources().get(i).document());
            assertThat(restored.sources().get(i).score())
                    .isEqualTo(original.sources().get(i).score());
            assertThat(restored.sources().get(i).chunk())
                    .isEqualTo(original.sources().get(i).chunk());
        }
        assertThat(restored.timings().retrievalMs()).isEqualTo(120);
        assertThat(restored.timings().totalMs()).isEqualTo(555);
        assertThat(restored.durationMs()).isEqualTo(555);
    }

    @Test
    void jsonShouldBeValidFormat() {
        var response = AgentResponse.builder()
                .answer("hello")
                .build(10);

        String json = response.toJson();

        assertThat(json).contains("\"requestId\"");
        assertThat(json).contains("\"answer\"");
        assertThat(json).contains("\"sources\"");
        assertThat(json).contains("\"timings\"");
        assertThat(json).contains("\"durationMs\"");
    }

    @Test
    void emptySourcesShouldRoundtripAsEmptyArray() {
        var original = AgentResponse.builder().answer("no sources").build(5);
        var restored = AgentResponse.fromJson(original.toJson());

        assertThat(restored.sources()).isEmpty();
        assertThat(restored.answer()).isEqualTo("no sources");
    }

    // ── record invariant ──

    @Test
    void recordShouldGuardInvariants() {
        assertThatThrownBy(() ->
                new AgentResponse(null, "a", List.of(), new AgentResponse.TimingInfo(0, 0, 0, 0, 0), 0))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() ->
                new AgentResponse(UUID.randomUUID(), null, List.of(), new AgentResponse.TimingInfo(0, 0, 0, 0, 0), 0))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() ->
                new AgentResponse(UUID.randomUUID(), "a", null, new AgentResponse.TimingInfo(0, 0, 0, 0, 0), 0))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() ->
                new AgentResponse(UUID.randomUUID(), "a", List.of(), null, 0))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() ->
                new AgentResponse(UUID.randomUUID(), "a", List.of(), new AgentResponse.TimingInfo(0, 0, 0, 0, 0), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
