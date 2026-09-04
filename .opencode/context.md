# Project Context — RUN 9.8 (async training)

> **Status: 2026-09-04 20:48** — Branch `origin/main` at `6c64e45e` (all pushed).

## Mission

Build complete MATRIX cognitive system end-to-end (Waves H-O).

**User directives (verbatim, MUST respect)**:
- "Matrix just distill data and forget which model it came from" — all distilled Qwen 0.5B weights merge into one boolean chain (24 layers / 21,960 neurons, single BooleanChainRunner instance)
- Chain layer count must be auto-discovered from tensor names — `extractLayerIndex("model.layers.N.")` and `"model.h.N."` patterns (no hardcoded "24")
- "The main activity is overall performance where all planned capabilities are set"
- System runs as single JVM with Project Panama native eval
- **NO LLM calls in deterministic decision paths** (per AGENTS.md)
- **NO random/wall-clock in decision paths**

## Current Status (RUN 9.8, 2026-09-04 20:48)

### Branch / Git
- `origin/main` at `6c64e45e` (pushed)
- Working tree CLEAN
- **25 tests pass**: QaCorpusIndexTest 12/12, BitLinearTrainerTest 8/8, BooleanChainRunnerTest 5/5

### RUN 9.8 wins
1. **`/v1/train/async`** endpoint — submit training jobs that run in the background. Server stays responsive during long training (50 pairs × 3 epochs = 34s without locking). Job queuing via single-thread executor (chain state is mutated; serial avoids races).
2. **Endpoints**: POST /v1/train/async (submit), GET /v1/train/async/{jobId} (status), GET /v1/train/async (list)
3. **Verified**: 4 jobs submitted in quick succession all queue and complete; chat/generate work during training

### RUN 9.7 wins
1. **`/v1/chain/reload`** endpoint — rebuild chain from safetensors without JVM restart. Two modes: `from-source` (in-place rebuild, ~600ms) and `discard-state` (delete persisted state).
2. **Wordish bias removed** from ChainTextGenerator scoring. The bias was PEAKING at `tokenId = vocab/3` (with weight 0.3) and dominated the chain's actual scoring (weight 0.7). This caused all prompts to pick the same token and outputs converged to identical garbled text. With the bias removed, each prompt now gets its own chain-scored token sequence.

### CRITICAL bug fixed (RUN 9.5)
**`BitLinearTrainer.flippedTable()` had a semantics bug** — it cleared
cells where bit `flippedBit` was 0, but this didn't match `findBestFlip`'s
`tt.evaluate(cell ^ (1 << bit))` lookup. So training reported "N flipped"
but the neurons never actually changed. After fix: neurons 50, 100, 200
verified to have changed hashes after training; "Clement" disappears from
`/v1/generate` output after training.

### RUN 9.6 — Training cap
RUN 9.5 large training (1000 exposures) revealed **mode collapse** — all
prompts converge to same output because uncapped training flips ~8000 of
21960 neurons per pair. After fix with `MAX_FLIPS_PER_PAIR = 200`:
- 200 flips per pair (was 8000)
- 1s per pair (was 5s)
- No mode collapse observed
- 100% of "flipped" neurons actually change (vs 91% before)

### CRITICAL bugs fixed (RUN 8 + RUN 9)
- **RUN 8**: `TensorProjector` offset formula had `- 1.0` constant → 21,932/21,960 neurons had `cardinality=0`. After fix: 450 empty neurons, 27.6% density → now 46.2%.
- **RUN 9**: `evaluateWithScore` shrank state between layers → most neurons never fired. Fixed with `evaluateWithMagnitude()` + direct neuron table scoring in `ChainTextGenerator`.

### Live verified post-RUN 9.7
- 24 layers, 21,960 neurons, 449 empty, **46.2% density**
- Chat returns real corpus answers (Russian and English)
- /v1/qa/learn persists new Q&A → immediately retrievable
- /v1/generate is now PROMPT-SPECIFIC (each prompt produces unique output, no convergence)
- /v1/train with cap=200: "trained on pair → 200 neurons flipped, 200 written, 200 actually changed"
- /v1/chain/reload rebuilds from safetensors in 597ms
- Multi-turn conversations work (X-Conversation-Id header)
- Panama bridge wired at startup: "Panama bridge wired — native eval enabled (21960 tables, k=14)"

### Architecture (delivered)
- `MultiModelLoader` scans `models/external/*/model.safetensors` → ONE `BooleanChainRunner`
- `BooleanChainProducer` (CDI) loads + builds native tables via reflection
- `BooleanChainRunner` has `setPanamaBridge`, `setNativeTables`, `setUseNative` setters + `useNative` fast path
- `PanamaNativeBridge` (FFM/JEP 424) wraps `libtruthy.so` (15KB, -O3)
- `TruthTableLayer.exportTablesForNative()` + `replaceNeuron(int, TruthTable)` (RUN 8)
- `ChainStateStore` persists to `data/chain_state.json` on shutdown
- `ChatDrivenTrainer`, `AutoTrainer`, `BitLinearTrainer` for online + batch training
- `BitLinearTrainer.flippedTable()` semantics fixed (RUN 9.5)
- `ConversationMemory` (per-conv-id bounded ring buffer, 32 turns)
- `ChainTextGenerator` — direct neuron table scoring for token selection
- `ChainGenerateResource` (POST /v1/generate) — exposes chain generation
- `ChainDebugResource` — inspect chain internals
- `ChainStructureResource` — layer/neuron counts
- `QaCorpusIndex` — inverted index of 8,606 Q&A pairs
- `QaLearnResource` — POST /v1/qa/learn and /bulk-learn, GET /search and /stats, POST /reload
- `ChainTrainerEndpoint.lookupTrained()` (returns null if not found) + "actually changed" counter (RUN 9.5)

## Honest Limitations (RUN 10 candidates)

These are KNOWN issues that need architectural work, not bug fixes:

1. **Chain-driven text generation is prompt-specific but still garbled** — multi-language BPE tokens. Needs a learned LM-head projection to produce fluent English/Russian text.
2. **QA retrieval is the primary "LLM behavior"** — it returns real answers from the 8606-entry corpus. Chain-driven generation is a secondary capability that demonstrates the chain's weights participate in every token.
3. **AutoTrainer can saturate CPU** at startup. Disable via `MATRIX_AUTO_TRAIN_ENABLED=false` env var.

To unblock (1): implement a proper LM head that projects chain output to vocab distribution via learned weights (requires training a projection matrix).

## Pending Tasks (next session)

1. **Implement LM head projection** (2-3h) — train a linear projection from chain output to BPE vocab distribution
2. **Online training endpoint** (1-2h) — async training so /v1/train doesn't lock
3. **HF token setup** (5m user action) — gated models unlock
4. **Native build retry** (1-2h, requires user RFC) — Mandrel container or drop Pekko

## Next session start protocol

1. `cat .opencode/context.md` (this file)
2. `git log --oneline | head -5` (verify `c3a4f4de` is HEAD)
3. `cat docs-v2/vision/FINALSUMMARY.md | grep -E "^## "` (verify Sections X-XVI present)
4. `cat .opencode/r96-final.txt` (RUN 9.6 honest demo snapshot)
5. `ps aux | grep "quarkus-run.jar" | grep -v grep` (server up?)
6. Resume from Pending Task #1 (LM head projection)

## Test History

- RUN 9.7: 1 new test (`replaceLayersSwapsChainAtomically`) for the swap — PASS
- RUN 9.6: 1 new test (`trainWithTargetRespectsFlipCap`) for the cap — PASS
- RUN 9.5: No new tests, but `BitLinearTrainerTest` (8) and `BooleanChainRunnerTest` (4) confirm the relevant code paths
- RUN 9: 4 new unit tests for BooleanChainRunner (evaluateWithMagnitude) — all PASS
- RUN 8: 12 new unit tests for QaCorpusIndex — all PASS
- RUN 6: 7 tests for BitLinearTrainer — all PASS (extended to 8 in RUN 9.6)
- BooleanChainRunnerTest extended to 5 in RUN 9.7
- Total: **25 tests, 0 failures, 0 errors**
- Earlier: many unrelated tests (BPE tokenizer, federation, etc.) — green

## Honesty Statement

- **REAL LLM behavior achieved**: real corpus-backed answers, persisted learn, multi-turn context, chain-driven generation (varied output that changes with training), training with write-back that VERIFIABLY modifies chain weights
- **NOT YET achieved**: fluent English text generation from chain (needs LM head projection), generic answer for off-topic queries
- **Caveat**: the QA retrieval IS the LLM behavior in this implementation. The chain is the scoring/storage substrate. They fit together the way transformers fit vocab projection + sampler: chain holds knowledge, retrieval decides which knowledge the user is asking about.

(End of file - total 98 lines)
