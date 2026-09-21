# EXP-MATRIX.19 — Continuous LM head training via chat feedback (RUN 19)

## Hypothesis

After every chat completion, users can submit feedback (rating 0.0..1.0)
via `POST /v1/chat/feedback`. The system should:
1. For positive feedback (rating ≥ 0.7): increment LM head weights for
   the chain-output × answer-token pairs.
2. For negative feedback (rating < 0.3): decrement weights (best-effort).
3. Persist updated weights to disk on server shutdown.

## Implementation

- `LmHeadFeedbackTrainer` (new, ApplicationScoped): consumes feedback
  events, looks up the question/answer in `ConversationMemory`,
  computes chain features via `ChainFeatureCache` (RUN 15), tokenizes
  the answer, and applies signed updates to `LmHead`.
- `ConversationFeedbackResource`: now invokes the trainer after each
  feedback submission. Response includes `lmHeadUpdates` count.
- `onStop` event: persists updated weights to `data/lm_head_weights.bin`.

## Honest caveats (CONSTITUTION VI)

1. **`LmHead.update(boolean[], int, int)` is sign-positive only.**
   Negative feedback currently DOES NOT decrement weights (the
   `decrementForToken` method is a no-op). The signal is preserved
   in `ConversationFeedbackStore` for future re-training. To make
   negative updates real, `LmHead` would need a signed update API
   (`updateWithSign(features, token, +1)` / `-1`).

2. **Tokenisation is a byte-level fallback** — no BPE because the
   trainer doesn't depend on `BpeTokenizerProvider`. This is fine
   for the prototype; production should use BPE.

3. **Persist-on-shutdown only.** A crash mid-feedback-event loses
   the in-memory updates. Production should add a periodic
   checkpoint (every N updates or every T seconds).

## Tests

- `LmHeadFeedbackTrainerTest`: 7/7 pass. Covers positive feedback,
  negative feedback (no-op tracking), neutral skip, null/empty IDs,
  conversation-without-turns, monotonic counter accumulation.

## Cross-references

- [SPEC-006-consciousness-deliberation.md](../specifications/SPEC-006-consciousness-deliberation.md)
  (gate feedback)
- [CHAT-DRIVEN-TRAINER](../../matrix-core/src/main/java/io/matrix/chat/ChatDrivenTrainer.java)
  (existing background trainer)
- `ConversationFeedbackResource` — feedback endpoint
