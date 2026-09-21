package io.matrix.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationRecorderTest {

    @Test
    void recordAndFlushWritesNdjson(@TempDir Path tmp) throws Exception {
        ConversationRecorder r = new ConversationRecorder();
        r.storageDir = tmp.toString();
        r.flushIntervalSeconds = 60;
        r.enabled = true;
        r.onStart(null);

        String convId = "conv-test-1";
        r.record(ConversationRecord.user(convId, "alice", "M.A.T.R.I.X.", "hello"));
        r.record(ConversationRecord.assistant(convId, "alice", "M.A.T.R.I.X.",
                "world!", "APPROVED", 42L, 1.5, 1));

        int written = r.flushForTest();
        assertThat(written).isEqualTo(2);
        assertThat(r.totalFlushed()).isEqualTo(2);

        Path file = r.currentLogFile();
        assertThat(Files.exists(file)).isTrue();
        List<String> lines = Files.readAllLines(file);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).contains("\"role\":\"user\"");
        assertThat(lines.get(1)).contains("\"role\":\"assistant\"");
        assertThat(lines.get(1)).contains("\"ethicalVerdict\":\"APPROVED\"");

        r.onStop(null);
    }

    @Test
    void readAllReturnsRecordsInChronologicalOrder(@TempDir Path tmp) throws Exception {
        ConversationRecorder r = new ConversationRecorder();
        r.storageDir = tmp.toString();
        r.flushIntervalSeconds = 60;
        r.enabled = true;
        r.onStart(null);

        List<ConversationRecord> toRecord = new ArrayList<>();
        toRecord.add(ConversationRecord.user("c1", "u", "M.A.T.R.I.X.", "u1"));
        toRecord.add(ConversationRecord.assistant("c1", "u", "M.A.T.R.I.X.",
                "a1", "APPROVED", 0L, 0.1, 1));
        toRecord.add(ConversationRecord.user("c1", "u", "M.A.T.R.I.X.", "u2"));
        toRecord.add(ConversationRecord.assistant("c1", "u", "M.A.T.R.I.X.",
                "a2", "APPROVED", 0L, 0.1, 1));
        r.recordAll(toRecord);
        r.flushForTest();

        List<ConversationRecord> read = r.readAll(100);
        assertThat(read).hasSize(4);
        assertThat(read.get(0).role()).isEqualTo("user");
        assertThat(read.get(3).content()).isEqualTo("a2");

        r.onStop(null);
    }

    @Test
    void disabledRecorderIsNoOp(@TempDir Path tmp) throws Exception {
        ConversationRecorder r = new ConversationRecorder();
        r.storageDir = tmp.toString();
        r.enabled = false;
        r.onStart(null);

        r.record(ConversationRecord.user("c", "u", "M", "x"));
        assertThat(r.totalRecorded()).isEqualTo(0);
        assertThat(r.flushForTest()).isEqualTo(0);
    }
}