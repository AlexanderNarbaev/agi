# Project Context — RUN 6 final state (post-compaction + bug fixes)

## Mission
Build complete MATRIX cognitive system end-to-end (Waves H-O).

**User directives (verbatim, MUST respect)**:
- "Matrix just distill data and forget which model it came from"
- Chain layer count must be auto-discovered (not hardcoded)
- "The main activity is overall performance where all planned capabilities are set"
- System runs as single JVM
- **NO LLM calls in deterministic decision paths** (per AGENTS.md)

## Current Status (RUN 6, 2026-09-04 13:55)

### Branch / Git
- `origin/main` at `be7fa53a` (pushed)
- Working tree clean except `.opencode/context.md` (this file, from compaction)
- 24 files in the goal-guard changed-files list, all committed

### Critical bugs fixed this session
1. **AgentBrainService null-path crash** — preload split into 4 Throwable-safe steps. Server now starts in 83s with no path-null error.
2. **Panama wire-up infrastructure** — `BooleanChainRunner` setters, `TruthTableLayer.exportTablesForNative()`, `BooleanChainProducer.autoDetect()` builds tables via reflection and calls setters. **Live runtime caveat**: bridge init timing means `isLoaded()` returns false at producer call time; pure-Java path stays active.
3. **Training signal flip** — `BitLinearTrainer` handles k=0 edge case + new `trainWithTarget` method. `BitLinearTrainerTest` (7 tests, all PASS) verifies `flipped>0` and `accuracy>0.5`.

### Live verified
- Server starts in 83s, no errors
- chain-status: 24 layers / 21,960 neurons, non-empty
- state: chain_restored_from_disk=true, LTM L2_MODULE=11
- agent/plan: returns `fs.list` tool for "search files" goal
- benchmark (200 ops): chain_eval p50=174μs, p99=191μs
- bpe_encode: p50=6.8ms
- BitLinearTrainerTest: 7/7 PASS

### Architecture (delivered)
- `MultiModelLoader` scans `models/external/*/model.safetensors` → ONE `BooleanChainRunner`
- `BooleanChainProducer` (CDI) loads + builds native tables
- `BooleanChainRunner` has `setPanamaBridge`, `setNativeTables`, `setUseNative` setters + `useNative` fast path
- `PanamaNativeBridge` (FFM/JEP 424) wraps `libtruthy.so` (15KB, -O3)
- `TruthTableLayer.exportTablesForNative()` exposes packed long[] tables
- `ChainStateStore` persists to `data/chain_state.json` on shutdown
- `ChatDrivenTrainer`, `AutoTrainer`, `BitLinearTrainer` for online + batch training
- `ConversationRecorder` records chats
- `AgentEndpoint` (POST /plan + /tools), `StateEndpoint` (state + compact), `BenchmarkEndpoint` (p50/p95/p99)

## Pending Tasks (next session)

1. **Panama bridge runtime activation** (1h) — fix the CDI init timing so `isLoaded()` returns true when the producer runs. May need to add `@Startup` priority or move the load into the producer's `build()` rather than relying on `@PostConstruct` of the bridge bean.
2. **Re-benchmark after Panama activation** (15m) — expect <100μs p50 (vs 174μs pure-Java).
3. **HF token setup** (5m user action) — `huggingface-cli login` so gated models load. Not a code change.
4. **Native build retry** (1-2h, requires user RFC) — Mandrel container or drop Pekko.
5. **Goal Guard review cycles** — when this thread yields, the plugin will run the 13 review gates automatically. No code action needed.

## Honesty statement
- 1 commit this session (`be7fa53a`), 7 files, +401/-85 lines
- All committed and pushed to `origin/main`
- Server starts in 83s, all 5 endpoints respond, training tests pass
- Panama bridge is wired in code but does NOT activate at runtime (bean init timing)
- Chain eval p50 = 174μs (pure Java)
- BitLinearTrainerTest proves the training signal now flips neurons (7/7 PASS)
- HF token still missing (gated models unavailable)
- Goal Guard: 0 review cycles run; plugin auto-runs when main thread yields

## Next session start protocol
1. `cat .opencode/context.md` (this file)
2. `git log --oneline | head -3` (verify `be7fa53a`)
3. `cat docs-v2/vision/FINALSUMMARY.md` (Section VIII for RUN 6)
4. Check server is running: `ps aux | grep matrix-core-1.0.0-runner | grep -v grep`
5. Resume from Pending Task #1 (Panama runtime activation)
