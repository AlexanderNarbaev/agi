# Context — AGI Project Status

## Current Status (2026-09-20)
- **351 tests pass, 0 fail** (87 brain + 216 federation + 48 CLI)
- **Latest wave**: W565, hash `028665c8`
- **Model**: Qwen2.5-0.5B via ONNX at `models/onnx/qwen05b/`
- **Native binary**: 126MB, all CLI commands work
- **Dual remotes**: origin (github) + gitverse — both synced

## Brain Stack (complete, verified)
| Component | Class | What it does |
|-----------|-------|-------------|
| Real LLM | LlmBrainLoopService | Qwen ONNX inference, ~2-3s |
| RAG | LlmBrainLoopRag | SimpleKnowledgeBase (125 docs) |
| Anti-hallucination | ConfidenceFilter | Min 30% confidence gate |
| Self-initiation | AutonomyEngine | Cycles every 30s/120s/300s |
| Learning | ConversationLearner | Learns from NDJSON history |
| Self-improvement | BrainImprover | Continuous KB growth |
| Interactive | BrainRunner | Chat + learn + stats commands |
| Sensor input | BrainSensorBridge | stdin + files + polling |
| HTTP | BrainHttpServer | 6 endpoints + web UI |
| Quarkus | BrainQuarkusResource | /v1/brain/* |
| Telemetry | BrainTelemetry | Prometheus metrics |
| Startup | BrainServerStartup | Auto-start on Quarkus boot |
| Launcher | matrix-brain-start.sh | Production startup script |
| Streaming | ConversationStreamServer | SSE streaming |

## Goal Guard State
- **Goal Contract** (Sept 14): "Multi-timestep integration metrics W87-W91" — STALE, doesn't match actual work
- **Actual work**: W461-W565 built complete brain stack (different direction)
- **12 required review gates**: ALL missing/stale
- **6 open blocking findings**: goal-prompt-auditor, goal-doc-reviewer, goal-diff-reviewer, goal-perf-reviewer, goal-quality-gate, goal-api-reviewer
- **Completion BLOCKED** by guard

## Pending Decisions
1. Goal contract needs updating to match actual work (brain stack, not Φ metrics)
2. Review gates need resolution or contract rewrite
3. Next priorities: KV-cache, multi-modal, larger model, or something else

## Key Files
- `SESSION.md` — global pointer
- `docs-v2/waves/WAL-*.md` — 100+ wave docs
- `matrix-core/src/main/java/io/matrix/brain/` — 15+ brain classes
- `matrix-core/src/main/java/io/matrix/knowledge/SimpleKnowledgeBase.java` — RAG
- `matrix-core/src/main/java/io/matrix/learning/ConversationLearner.java` — learning
- `matrix-core/src/main/java/io/matrix/api/QwenOnnxBridge.java` — LLM bridge
- `matrix-core/src/main/scripts/matrix-brain-start.sh` — production launcher
- `matrix-core/src/main/resources/web/index.html` — web UI
- `data/knowledge/` — 5 knowledge docs
- `data/conversations/` — 14+ NDJSON sessions

## Commands
```bash
# Run all tests
./gradlew :matrix-core:test --tests "io.matrix.brain.*"
./gradlew :matrix-core:test --tests "io.matrix.federation.*"
./gradlew :matrix-core:test --tests "io.matrix.cli.*"

# Quick brain test
java -cp $(./gradlew -q printClasspath) io.matrix.brain.LlmBrainLoopService "question" models/onnx/qwen05b

# Commit rule
git add -A && git commit -m "WAL: WNNN — desc" && git push origin main && git push gitverse main
```
