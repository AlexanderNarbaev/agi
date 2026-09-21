# EXP-MATRIX.57 — Context window management (RUN 124)

## Hypothesis

For long conversations, the message list should be trimmed to fit
within the model's context window while preserving the most
relevant content (system message + recent turns).

## Setup

- `ContextWindowManager`: trims message lists to fit token budget.
- Keeps system message (if first) + most recent messages.
- Estimates tokens via 4-char-per-token heuristic + 4 markers.

## Tests

9 tests verify:
- Empty/small lists pass through
- Large lists get trimmed correctly
- Most recent messages are preserved
- System messages are kept at the start
- Token estimation is reasonable

## Cross-references

- QwenChatTemplate: message structure
- QwenOnnxBridge.chatWithHistory: takes trimmed messages

## Verdict

Context window trimming works for the common case. For real LLM
contexts with thousands of tokens, you'd want a sliding window or
a summary-based compression strategy.
