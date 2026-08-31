# Project Context — RUN 4 final state

## Mission
Build complete MATRIX cognitive system end-to-end (Waves H-O, last user request).

## Run outcome (RUN 4)

### Verifier findings resolved
- ✅ **BLOCKING-1**: Wave K full-bench artifact (`docs-v2/research/reports/EXP-MATRIX.13-full-bench-10k.json`) committed and pushed to `origin/main` at `e8ce8c09`
- ✅ **BLOCKING-2**: `matrix-core/models/training_data/auto_generated.jsonl` restored to HEAD content (2 conversation records preserved)

### Pushed commits (this session)
- `e8ce8c09` Wave K full-bench artifact committed
- `2cd9b44e` Phase 1+3 multi-model loader (MultiModelLoader combines all safetensors into one BooleanChainRunner; layer count auto-discovered)
- `1307de47` Shutdown false alarm (bash timeout SIGTERM, not JVM); chat + corpus + training pipeline work; modular split deferred
- `0ec542b7` Chat uses Tsetlin-trained BIR before PureBir template (real corpus knowledge); TsetlinTrainer fixed (inputBits 64→20)
- `0fec8c09` OpenAIChatResource uses magnitude-aware chain scoring
- (prior commits in earlier sessions)

## Current state

### System works end-to-end
- `nohup java -jar matrix-core/build/matrix-core-1.0.0-runner.jar &` (port 9091, stable, daemon-threaded)
- Chat through boolean substrate: `/v1/sandbox/chat` and `/v1/chat/completions` both use `chain_used: true`
- 13,416 training pairs loaded (corpus + world knowledge)
- 24 layers × 21,960 neurons combined from Qwen2.5-0.5B-Instruct into ONE matrix instance
- ConversationRecorder records every chat to `data/conversations/`
- ChatDrivenTrainer runs online training cycles

### Verified live (latest test)
```
$ curl http://localhost:9091/v1/chain-status
{"model":"MultiModel[qwen2.5-0.5b]","layers":24,"totalNeurons":21960,"empty":false}
```

### Architecture (delivered)
- `MultiModelLoader` scans `models/external/*/model.safetensors`, projects each to TruthTable neurons, concatenates into ONE `BooleanChainRunner`
- Layer count is auto-discovered (NOT hardcoded "24")
- `BooleanChainProducer` (CDI bean) loads via `MultiModelLoader.loadFromDirectory()` at startup
- `BooleanChainRunner` has magnitude-aware scoring (`ChainResult.weightedScore`)
- `ExpandedTextToBitsService` 896-bit encoder; `BpeTokenizerProvider` real Qwen BPE
- `PersistentHierarchicalMemory` JSONL store with auto-restore on `StartupEvent`
- `KnowledgeShare` pluggable `MessageBus` (file-system prod, in-memory tests)

## Pending (next session)

1. **Phase 2: Persist chain state** (1h) — auto-save/load loaded chain to `data/chain_state.json` so restarts resume from prior state
2. **Modular split** (1h + user Java cleanup) — `matrix-sim` for Pekko-using packages
3. **Phase 5: Performance** (2h) — ZGC + lazy loading + batch inference → <50ms chat latency
4. **Phase 6: Agent mode** (1h) — `/v1/agent` endpoint
5. **Phase 7: Knowledge UI** (30m) — `POST /v1/knowledge`, `GET /v1/state`
6. **Real training** (2-3h) — BitLinear trainer on real corpus so chain fires meaningfully

## Honesty statement
- Server stable (false alarm shutdown was bash `timeout`)
- Chat responds with `chain_used: true` but outputs are zero-density (chain neurons don't match BPE bit patterns for benign text) — needs training
- All session commits pushed to `origin/main` (latest: `e8ce8c09`)
- Working tree clean
