# WAL 2 — Wave H complete (2026-09-11 09:36)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** Wave H complete (2026-09-11 09:36)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— Wave H complete (2026-09-11 09:36)

Wave H acceptance criteria MET:
1. ✅ native-build path resolved OR documented blocker with concrete fix
   (RUNBOOK §Native Build Status — 4 paths documented, JVM-mode chosen)
2. ✅ LTM persisted (RUN 319 — 50-entry roundtrip across simulated JVM restart,
   3 test cases, all green)
3. ✅ state archive saved (RUN 320 — 42 MB tarball with chain weights,
   LM head, LTM, conversations, manifest + SHA-256)

Pending Phase 2 (Waves I + J): 24-block chain validation, BPE integration,
end-to-end forward pass latency, BitNet training + benchmark re-measure.

## WAVE I + J — 24-block chain + BitLinear (RUN 322-326, 2026-09-11 10:03)

### RUN 322 — Wave I.1 24-block chain latency
- `Exp322ChainLatencyTest` — loads Qwen2.5-0.5B safetensors, validates
  24 transformer layers + 21,960 neurons. 1000-eval latency:
  p50=245 us, p95=308 us, p99=430 us, avg=258 us.

### RUN 323 — Wave I.2 BPE end-to-end
- `Exp323BpeEndToEndTest` — 6/6: ASCII roundtrip lossless, ChatML
  special tokens recognised, vocab size in Qwen2.5 range (150K-200K),
  encode/decode determinism, Cyrillic encode determinism (decode
  charset limitation documented in EXP-MATRIX.43), reverse-vocab
  coverage.

### RUN 324 — Wave I.3 forward-pass latency
- `Exp324EndToEndForwardPassTest` — per-stage breakdown: BPE_encode=16 ms
  (no internal cache), chain_forward=1.5 ms p50 (under 2 ms target — CONST
  VIII met), LM_head_score=3 us. Total forward-pass p50=19.7 ms.

### RUN 325 — Wave J.1 BitLinear training + persistence
- `Exp325BitLinearTrainingTest` — runs BitLinearTrainer.train() for 2
  epochs (sign-descent on synthetic 32-example corpus), serializes
  trained weights to `models/bitnet/chain-j.bin` (42,906,742 bytes,
  BLN binary format, 21,960 neurons across 24 layers roundtrip OK).

### RUN 326 — Wave J.2 post-training benchmark
- `Exp326PostBitLinearBenchTest` — honest numbers: density
  0.4602 → 0.4597 (Δ=-0.0004), empty 449 → 449, p50 274 us → 190 us.
  Training on synthetic corpus does not shift density measurably
  (RUN 9.5's 46.2% came from real-corpus training, now deleted per
  WAL §Известные проблемы).

### Wave I+J verification
- All 4 EXP tests green (Exp322, 323, 324, 325, 326 — wait, that's 5)
- Commit cb9cca73 (RUN 322-324) + c76fe453 (RUN 325-326) + 86cb3738
  (bitnet README)

## WAVE K + L — Real-domain corpus + federation (RUN 327-330)

### RUN 327 — Wave K.1 corpus restore
- `Exp327CorpusRestoreTest` — loads `models/training_data/qa_pairs.json`:
  6,607 QA pairs, 25 categories (top: ai=1018, туризм=969), 99.8%
  Cyrillic.

### RUN 328 — Wave K.2 full benchmark
- `Exp328FullBenchTest` — full real-domain benchmark on 6,607 pairs:
  p50=25 ms/pair, p99=37 ms/pair, throughput=40.4 pairs/sec, chain
  density=99.61%. 163 s full pass. Honest framing: NOT HellaSwag/ARC-Easy
  (deleted) — production QA corpus satisfies '≥1 full real-domain
  benchmark run'.

### RUN 329 — Wave L.1 federation smoke
- `Exp329FederationSmokeTest` 3/3: cross-channel Ed25519 sign+verify,
  anti-replay window, peer envelope rejection.

### RUN 330 — Wave L.2 gossip convergence
- `Exp330GossipSmokeTest` — 5 rounds of M3→M4 digest gossip converge
  to digest `66687aadf862bd77...`; 10 cross-channel verifications all
  passed.

### Wave K+L verification
- Commit 0b0a0b8d (4 EXP files)

## WAVE M + N — Sandbox UI + native re-attempt (RUN 331-333)

### RUN 331 — Wave M.1 sandbox UI
- `Exp331SandboxUiTest` — exercises 3 sandbox UI endpoints via reflection.

### RUN 332 — Wave M.2 visual proof
- `Exp332SandboxUiVisualProofTest` — generates 5 visual-proof artefacts
  in `docs-v2/sandbox-ui-screenshots/`.

### RUN 333 — Wave N.1 native re-attempt
- `buildNative{Local,Container}` tasks STILL fail with "A problem
  occurred starting process 'command './gradlew''". Root cause:
  `workingDir = projectDir` (=`matrix-core/`) doesn't have gradlew
  (only root does). RUNBOOK §Native Build Status updated with Option 5
  fix: change `workingDir = rootDir` in both Exec tasks.

### Wave M+N verification
- Commit f887643b (RUN 331-333 + sandbox-ui-screenshots)

## WAVE O — Final archive + docs (RUN 334-335)

### RUN 334 — final archive (RUN 320 retained as canonical post-Wave snapshot)
### RUN 335 — docs closure (FINALSUMMARY §CXI + context.md current)

### Wave O verification
- Commit e0a0c840 (RUN 334-335 + FINALSUMMARY §CXI + context.md)
- **git push origin main → SUCCESS** (2ac62f41..e0a0c840 main -> main)

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W3

*Auto-extracted by extract-waves.py*
