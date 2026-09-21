package io.matrix.dialog;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 194 — ConversationRecorder (multi-turn memory).
 *
 * <p>Records conversational turns between user and assistant,
 * producing an append-only ConversationTranscript. Uses M0
 * (working memory) for recent turns; older turns drop out.
 *
 * <p>Deterministic given input sequence.
 */
public final class ConversationRecorder {

    public record Turn(String role, String text, long turnIndex) {}

    public record ConversationTranscript(List<Turn> turns) {}

    private final List<Turn> turns = new ArrayList<>();
    private int maxTurns = 32;

    public ConversationRecorder withMaxTurns(int max) {
        this.maxTurns = max;
        return this;
    }

    public synchronized void record(String role, String text) {
        turns.add(new Turn(role, text, turns.size()));
        while (turns.size() > maxTurns) {
            turns.remove(0);
        }
    }

    public synchronized ConversationTranscript snapshot() {
        return new ConversationTranscript(new ArrayList<>(turns));
    }

    public synchronized int turnCount() {
        return turns.size();
    }

    public synchronized List<Turn> recent(int n) {
        int count = Math.min(n, turns.size());
        return new ArrayList<>(turns.subList(
                turns.size() - count, turns.size()));
    }

    public synchronized void clear() {
        turns.clear();
    }
}
