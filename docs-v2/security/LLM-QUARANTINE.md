# LLM-Serving Class Quarantine Policy (TRUE-W9)

**Status:** Active — RuntimeLlmGuardTest enforces

The following classes in `matrix-core/src/main/java/io/matrix/api/` are
**legacy LLM-serving infrastructure** and must NEVER be reachable from
the runtime mind path:

```
io.matrix.api.QwenModelAdapter
io.matrix.api.QwenOnnxBridge
io.matrix.api.OnnxModelRegistry
io.matrix.api.OnnxChatResource
io.matrix.api.OpenAIChatResource
io.matrix.api.BeamSearchGenerator
io.matrix.api.BpeTokenizer
io.matrix.api.ChainTextGenerator
io.matrix.api.ContinuousBatchScheduler
io.matrix.api.AdaptiveModelRouter
io.matrix.api.HuggingFaceFetcher
io.matrix.api.PromptTemplates
io.matrix.api.ContextWindowManager
io.matrix.api.TextEmbedder
io.matrix.api.OnnxChainEnsemble
io.matrix.api.OnnxRuntimeAdapter
```

These classes were the original LLM-serving stack (Qwen-7B + ONNX
Runtime + BPE tokenizer + continuous batching + adaptive routing). They
are FROZEN to OFFLINE-ONLY use per **CONSTITUTION Article I** ("no LLM in
runtime").

The TRUE-MIND realization (TRUE-W1+) replaced LLM inference with the
real matrix-core engine stack:
- `BirBrainCycle` (BIR inference + HDC + SafetyMonitor)
- `HdcBrain` (10k-bit HDC)
- `AdvancedTsetlinMachine` (Tsetlin)
- `ReflexEngine` (fast-path reflexes)

The legacy LLM classes remain in the source tree for backward
compatibility with the OLD gateway surface (`/v1/chat/completions`,
etc.) but **must never be called from the runtime mind path**.

## Enforcement

The test `io.matrix.brain.runtime.RuntimeLlmGuardTest` scans:
- `matrix-brain-runtime/src/main/java`
- `matrix-api-gateway/src/main/java`

For any `import io.matrix.api.<FORBIDDEN>` or `Class.forName("io.matrix.api.<FORBIDDEN>")`
and FAILS the build if found. Currently **PASSING** (no violations).

## Future work

In a future release, the legacy LLM classes can be moved to a separate
`matrix-legacy-llm-tools` module excluded from the runtime classpath.
For now, the RuntimeLlmGuardTest ensures they're dead code at runtime.
