package io.matrix.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.chat.feedback.FeedbackAggregator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatTrainingPairGeneratorTest {

    @Test
    void extractsSingleTurnPair(@TempDir Path tmp) throws Exception {
        ConversationRecorder recorder = new ConversationRecorder();
        recorder.storageDir = tmp.toString();
        recorder.enabled = true;
        recorder.flushIntervalSeconds = 60;
        recorder.onStart(null);

        ConversationFeedbackStore feedback = new ConversationFeedbackStore();
        feedback.storageDir = tmp.toString();
        feedback.onStart(null);

        FeedbackAggregator agg = new FeedbackAggregator();
        // Inject the store via reflection since it's normally CDI-injected
        java.lang.reflect.Field f = FeedbackAggregator.class.getDeclaredField("store");
        f.setAccessible(true);
        f.set(agg, feedback);

        Path output = tmp.resolve("pairs.jsonl");
        ChatTrainingPairGenerator gen = new ChatTrainingPairGenerator();
        gen.outputFile = output.toString();
        gen.recorder = recorder;
        gen.feedback = agg;
        gen.minInputLength = 1;
        gen.maxInputLength = 10000;
        gen.minOutputLength = 1;
        gen.maxOutputLength = 10000;
        gen.requiredRatingFloor = 0.0;
        gen.minInputLength = 1;
        gen.maxInputLength = 10000;
        gen.minOutputLength = 1;
        gen.maxOutputLength = 10000;
        gen.onStart(null);

        List<ConversationRecord> records = List.of(
                ConversationRecord.user("c1", "u", "M", "What is 2+2?"),
                ConversationRecord.assistant("c1", "u", "M",
                        "It is 4.", "APPROVED", 0L, 0.1, 3)
        );
        recorder.recordAll(records);
        recorder.flushForTest();

        List<ChatTrainingPairGenerator.TrainingPair> pairs = gen.generateFromRecords(records);
        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).input()).isEqualTo("What is 2+2?");
        assertThat(pairs.get(0).output()).isEqualTo("It is 4.");

        int written = gen.generateAndAppend();
        assertThat(written).isEqualTo(1);

        List<String> lines = Files.readAllLines(output);
        assertThat(lines).hasSize(1);
        var node = new ObjectMapper().readTree(lines.get(0));
        assertThat(node.path("input").asText()).isEqualTo("What is 2+2?");
        assertThat(node.path("output").asText()).isEqualTo("It is 4.");
        assertThat(node.path("source").asText()).isEqualTo("conversation");

        recorder.onStop(null);
    }

    @Test
    void rejectedEthicalVerdictBlocksPair(@TempDir Path tmp) {
        ConversationRecorder recorder = new ConversationRecorder();
        recorder.storageDir = tmp.toString();
        recorder.enabled = true;
        recorder.flushIntervalSeconds = 60;
        recorder.onStart(null);

        ConversationFeedbackStore feedback = new ConversationFeedbackStore();
        feedback.storageDir = tmp.toString();
        feedback.onStart(null);

        FeedbackAggregator agg = new FeedbackAggregator();
        try {
            java.lang.reflect.Field f = FeedbackAggregator.class.getDeclaredField("store");
            f.setAccessible(true);
            f.set(agg, feedback);
        } catch (Exception e) { /* noop */ }

        Path output = tmp.resolve("pairs.jsonl");
        ChatTrainingPairGenerator gen = new ChatTrainingPairGenerator();
        gen.outputFile = output.toString();
        gen.recorder = recorder;
        gen.feedback = agg;
        gen.minInputLength = 1;
        gen.maxInputLength = 10000;
        gen.minOutputLength = 1;
        gen.maxOutputLength = 10000;
        gen.onStart(null);

        List<ConversationRecord> records = List.of(
                ConversationRecord.user("c1", "u", "M", "Bad request"),
                ConversationRecord.assistant("c1", "u", "M",
                        "I won't.", "REJECTED", 0L, 0.1, 2)
        );
        List<ChatTrainingPairGenerator.TrainingPair> pairs = gen.generateFromRecords(records);
        assertThat(pairs).isEmpty();

        recorder.onStop(null);
    }

    @Test
    void negativeFeedbackBlocksConversation(@TempDir Path tmp) {
        ConversationRecorder recorder = new ConversationRecorder();
        recorder.storageDir = tmp.toString();
        recorder.enabled = true;
        recorder.flushIntervalSeconds = 60;
        recorder.onStart(null);

        ConversationFeedbackStore feedback = new ConversationFeedbackStore();
        feedback.storageDir = tmp.toString();
        feedback.onStart(null);

        FeedbackAggregator agg = new FeedbackAggregator();
        try {
            java.lang.reflect.Field f = FeedbackAggregator.class.getDeclaredField("store");
            f.setAccessible(true);
            f.set(agg, feedback);
        } catch (Exception e) { /* noop */ }

        Path output = tmp.resolve("pairs.jsonl");
        ChatTrainingPairGenerator gen = new ChatTrainingPairGenerator();
        gen.outputFile = output.toString();
        gen.recorder = recorder;
        gen.feedback = agg;
        gen.minInputLength = 1;
        gen.maxInputLength = 10000;
        gen.minOutputLength = 1;
        gen.maxOutputLength = 10000;
        gen.onStart(null);

        feedback.submit(ConversationFeedback.thumbsDown("c2", "u", "wrong"));
        List<ConversationRecord> records = List.of(
                ConversationRecord.user("c2", "u", "M", "Hi"),
                ConversationRecord.assistant("c2", "u", "M",
                        "Hello!", "APPROVED", 0L, 0.1, 1)
        );
        List<ChatTrainingPairGenerator.TrainingPair> pairs = gen.generateFromRecords(records);
        assertThat(pairs).isEmpty();

        recorder.onStop(null);
    }

    @Test
    void idempotentOnSecondRun(@TempDir Path tmp) throws Exception {
        ConversationRecorder recorder = new ConversationRecorder();
        recorder.storageDir = tmp.toString();
        recorder.enabled = true;
        recorder.flushIntervalSeconds = 60;
        recorder.onStart(null);

        ConversationFeedbackStore feedback = new ConversationFeedbackStore();
        feedback.storageDir = tmp.toString();
        feedback.onStart(null);

        FeedbackAggregator agg = new FeedbackAggregator();
        try {
            java.lang.reflect.Field f = FeedbackAggregator.class.getDeclaredField("store");
            f.setAccessible(true);
            f.set(agg, feedback);
        } catch (Exception e) { /* noop */ }

        Path output = tmp.resolve("pairs.jsonl");
        ChatTrainingPairGenerator gen = new ChatTrainingPairGenerator();
        gen.outputFile = output.toString();
        gen.recorder = recorder;
        gen.feedback = agg;
        gen.requiredRatingFloor = 0.0;
        gen.minInputLength = 1;
        gen.maxInputLength = 10000;
        gen.minOutputLength = 1;
        gen.maxOutputLength = 10000;
        gen.onStart(null);

        List<ConversationRecord> records = List.of(
                ConversationRecord.user("c3", "u", "M", "Question?"),
                ConversationRecord.assistant("c3", "u", "M", "Answer.", "APPROVED",
                        0L, 0.1, 1)
        );
        recorder.recordAll(records);
        recorder.flushForTest();

        int first = gen.generateAndAppend();
        int second = gen.generateAndAppend();
        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(0);
        assertThat(Files.readAllLines(output)).hasSize(1);

        recorder.onStop(null);
    }
}