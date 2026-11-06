# Mission: RECON-W38 — Operational Integrity & Connectome Activation

Operator: binding decisions §0 (Ce3 local-only subagents, attention neutral, D7 CI approved,
GpuKernelEngine.deprecate runKernel, opencode.json ignored, native image report-unsupported,
hdc_kb tracked). Missing items are carry-over from the 2026-10-12 session: hdc_kb.ndjson
tracked via commit 11249775, lost sleep test recovered as EpisodicLogPersistenceTest + preserved
text in docs-v2/quality/lost-capability-tests/, data/smoke already absent from disk.

## M2: RECON-W38.0 Corrections | status: completed
### T2.1: Verify + commit uncommitted W38.0 code changes | status: completed 7210484f
- [x] S2.1.1: :matrix-brain-runtime:test full module — BUILD SUCCESSFUL (incl. RetrievalFloorAppliedTest 3/0)
- [x] S2.1.2: commit 7210484f 'WAL: RECON-W38.0 — retrieval floor…' (KnownFailures.md + HdcRetrievalStage + stages/RetrievalFloorAppliedTest; pushed gitverse develop)
### T2.2: Documentation drift | status: completed
- [x] S2.2.1: KnownFailures.md Exp228 → "RESOLVED in W36.1 via Article I classification" (commit 7210484f)
- [x] S2.2.2: OPEN-DECISIONS.md CLOSED decisions section D1/D2/D3/D5/D6/D7 (commit 04893c7f)
- [x] S2.2.3: .opencode/w37-plan.md → completed 2026-10-16, final 9324/0, W37.5 pointers
- [x] S2.2.4: DESIGN-68 → "Neuromodulation→retrieval partially verified (W38.0)" with measured limits (commit 6f42212c)
- [x] S2.2.5: V18 report → close-out 9,324/0 + cross-validation note (commit 6f42212c)
### T2.3: DISK-LEDGER | status: completed
- [x] S2.3.1: data/smoke verified ABSENT; nothing reclaimed this session → no ledger append needed
### T2.4: Meta-R1 compliance | status: completed
- [x] S2.4.1: docs-v2/research/RECON-W37-MCMC-SAMPLING.md created (no MCMC in shipped path; hill-climb; Gibbs gated on W38.3) (commit 04893c7f)
### T2.5: D7 CI implementation | status: completed
- [x] S2.5.1: .github/workflows/ci.yml weekly schedule + research-suites job, guards on build-test/security; structural check rc=0; limitation: actionlint binary absent locally (commit 6a15f178)

## M3: RECON-W38.1 Runtime Wiring (Neutral Graph) | status: completed 72a447b1
- [x] S3.1: Audit done — TrueMindCycle L363-371 builds stage directly (linear only); connectome NOT wired into serving path (confirms V18 'NOT WIRED')
- [x] S3.2: HdcNode retrieval overload added — process(input, query, obs, trace, floor) → stage().retrieve(); signal = confidence*weight; tags hdc.matched / hdc.confidence
- [x] S3.3: Connectome.propagateWithRetrieval(input, query, obs, trace, floor, topK) mirrors propagate (topK, |weight| desc, NodeId tie-break, prune filter); propagate untouched
- [x] S3.4: HdcNodeServingPathTest 4/0 BDD green (neutral hit/miss equality, floor-0.35 divergence, decay-weighted scaling)
- [x] S3.5: Mutation campaign rc=0 — M1_weight_ignored KILLED (1 fail), M2_const_signal KILLED (4 fails), M3_query_ignored KILLED (3 fails); canonical files restored (md5 verified); targeted gradle green

## M4: RECON-W38.2 Live Neuromodulation Aggregation | status: pending
- [ ] S4.1: Fatigue (query volume) + Uncertainty (similarity variance) aggregators
- [ ] S4.2: Feedback loops + attention safety floor
- [ ] S4.3: Simulation test: noisy stream → fatigue rise → sleep demand signal

## M5: RECON-W38.3 True Stochastic Exploration (MCMC) | status: pending
- [ ] S5.1: Gibbs sampler for Tsetlin clauses (seeded)
- [ ] S5.2: Temperature cooling in SleepConsolidator
- [ ] S5.3: Parity k≥7 convergence test vs hill-climb baseline

## M6: RECON-W38.4 Active Sleep Consolidation | status: pending
- [ ] S6.1: RealSleepScheduler ← Fatigue_Index hook
- [ ] S6.2: Replay episodes through Graph + Hebbian weight update
- [ ] S6.3: CV-validated pruning execution during sleep + logging
- [ ] S6.4: End-to-end cycle test: train → fatigue → auto-sleep → wake → stability

## M7: RECON-W38.5 Federation Weight Diffusion | status: pending
- [ ] S7.1: SynapseDeltaMessage (top-K weight changes)
- [ ] S7.2: Periodic exchange + conservative merge policy
- [ ] S7.3: Two-node sim test: A learns → sync → B retrieves faster

## End-of-session ritual (AGENTS.md)
- [ ] S8.1: SESSION.md append-only entry; WAL wave doc; commit+push
