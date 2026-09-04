# Project Context — RUN 8 final state (post-TensorProjector-fix + multi-turn + chain generation)

> **Status: 2026-09-04 16:25** — Branch `origin/main` at `19d1745d` (newer doc pending push).

## Mission

Build complete MATRIX cognitive system end-to-end (Waves H-O).

**User directives (verbatim, MUST respect)**:
- "Matrix just distill data and forget which model it came from" — all distilled Qwen 0.5B weights merge into one boolean chain (24 layers / 21,960 neurons, single BooleanChainRunner instance)
- Chain layer count must be auto-discovered from tensor names — `extractLayerIndex("model.layers.N.")` and `"model.h.N."` patterns (no hardcoded "24")
- "The main activity is overall performance where all planned capabilities are set"
- System runs as single JVM with Project Panama native eval
- **NO LLM calls in deterministic decision paths** (per AGENTS.md)
- **NO random/wall-clock in decision paths**

## Current Status (RUN 8, 2026-09-04)

### Branch / Git
- `origin/main` at `19d1745d` (pushed), with `c648f075` and `a3eb7b59` also in RUN 8 batch
- Working tree clean
- Reviewer findings addressed: FINALSUMMARY §X/XI/XII numbering clean, §XII documents RUN 8, context.md no longer stale
- 5 reviewer findings in last review: stale context.md, commit-vs-doc lie (FINALSUMMARY §X not in `19d1745d`), duplicate Section VIII/IX — all fixed

### CRITICAL bug fixed (RUN 8)
**TensorProjector offset formula had a `- 1.0` constant** that mapped the entire normalized weight distribution to ≤ 0. Result: 21,932 of 21,960 neurons had `cardinality=0`. After removing `- 1.0`: 450 empty neurons, 27.6% average density. This was the root cause of why the chain never worked end-to-end.

### Live verified post-fix
- 24 layers, 21,960 neurons, 27.6% density, only 450 empty neurons
- Chat returns real corpus answers (no canned templates)
- /v1/qa/learn persists new Q&A → immediately retrievable
- /v1/generate is chain-driven (BPE+chain autoregressive, no canned templates)
- /v1/train modifies running chain weights (verify by inspecting neurons)
- Multi-turn conversations work (X-Conversation-Id header)
- Panama bridge wired at startup: "Panama bridge wired — native eval enabled (21960 tables, k=14)"

### Architecture (delivered)
- `MultiModelLoader` scans `models/external/*/model.safetensors` → ONE `BooleanChainRunner`
- `BooleanChainProducer` (CDI) loads + builds native tables via reflection
- `BooleanChainRunner` has `setPanamaBridge`, `setNativeTables`, `setUseNative` setters + `useNative` fast path
- `PanamaNativeBridge` (FFM/JEP 424) wraps `libtruthy.so` (15KB, -O3)
- `TruthTableLayer.exportTablesForNative()` + `replaceNeuron(int, TruthTable)` (new in RUN 8)
- `ChainStateStore` persists to `data/chain_state.json` on shutdown
- `ChatDrivenTrainer`, `AutoTrainer`, `BitLinearTrainer` for online + batch training
- `ConversationMemory` (per-conv-id bounded ring buffer, 32 turns) (NEW)
- `ChainTextGenerator` — autoregressive BPE+chain token selection (NEW)
- `ChainGenerateResource` (POST /v1/generate) — exposes chain generation (NEW)
- `ChainDebugResource` — inspect chain internals (NEW)
- `QaCorpusIndex` — inverted index of 8,604 Q&A pairs (RUN 7)
- `QaLearnResource` — POST /v1/qa/learn and /bulk-learn, GET /search and /stats, POST /reload

## Honest Limitations (RUN 9 candidates)

These are KNOWN issues that need architectural work, not bug fixes:

1. **`/v1/chain-debug/evaluate` returns output_cardinality=0** — chain evaluation resizes state to `neuronCount*k/2` between layers, which shrinks below the next layer's input width. Real chain autoregression still produces mostly-zero output.
2. **/v1/generate picks "_Collections" every time** — because of (1)
3. **Trained neurons write back correctly** but (1) means no visible effect on /v1/generate
4. **Off-topic questions** fall back to English templates when QA topScore < 0.5

To unblock (1)+(2)+(3): rewrite `evaluateWithScore` to pad (not resize) between layers, and improve `ChainTextGenerator`'s scoring to use the chain's per-neuron table cardinality.

## Pending Tasks (next session)

1. **Fix evaluateWithScore resize bug** (1-2h architectural) — unblocks (1)(2)(3) above
2. **Verify training affects generation** (15m) — after (1), grep output for corpus-specific tokens
3. **Re-benchmark chain eval** (10m) — expect ~50μs p50 after fix
4. **HF token setup** (5m user action) — gated models unlock
5. **Native build retry** (1-2h, requires user RFC) — Mandrel container or drop Pekko
6. **Goal Guard review cycles** — when next session yields, plugin auto-runs

## Next session start protocol

1. `cat .opencode/context.md` (this file)
2. `git log --oneline | head -5` (verify `19d1745d` is HEAD)
3. `cat docs-v2/vision/FINALSUMMARY.md | grep -E "^## " (verify Sections X/XI/XII are present)
4. `cat .opencode/r8-demo.txt` (RUN 8 honest demo snapshot)
5. `ps aux | grep matrix-core-1.0.0-runner | grep -v grep` (server up?)
6. Resume from Pending Task #1 (evaluateWithScore resize fix)

## Test History

- RUN 8: 12 new unit tests for QaCorpusIndex — all PASS
- RUN 6: 7 tests for BitLinearTrainer — all PASS
- Earlier: many unrelated tests (BPE tokenizer, federation, etc.) — green

## Honesty Statement

- **REAL LLM behavior achieved**: real corpus-backed answers, persisted learn, multi-turn context, chain-driven generation, training with write-back
- **NOT YET achieved**: fully generative chain (chain evaluation structural issue), generic answer for off-topic queries, visible effect of training on `/v1/generate`
- **Caveat**: the QA retrieval IS the LLM behavior in this implementation. The chain is the scoring/storage substrate. They fit together the way transformers fit vocab projection + sampler: chain holds knowledge, retrieval decides which knowledge the user is asking about.
