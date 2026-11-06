---
layout: default
title: Algorithms
nav_order: 8
parent: Ecosystem
permalink: /ecosystem/algorithms/
---

**Status:** ACTIVE · **Updated:** 2026-11-06

# Algorithms — deep dives

Each page here covers the mathematical foundation, the history, the implementation and the
trade-offs of one algorithm of the stack. The in-code source of the same material is
`matrix-core/src/main/java/io/matrix/research/AlgorithmEncyclopedia.java`.

> **A correction to this index.** It used to say that the seven algorithms below "power
> every `/v1/analyze` call". They do not: the gateway's **default mode is `stub`**, and the
> real cycle runs only under `MATRIX_MODE=production`. Even then, not every stage fires on
> every input — each stage either fires with its evidence or is skipped. The pages also
> named classes that do not exist in this repository; those names have been corrected
> against the code.

## The engines of the cycle

| the algorithm | where it runs | the page |
|---|---|---|
| **BIR** — boolean inference | stage 6 of the ten-stage cycle; the logic substrate of the whole system | [BIR](/ecosystem/algorithms/bir/) |
| **HDC** — hyperdimensional computing | stage 7; the pattern memory, `HdcEncoding.DIM = 1024` | [HDC](/ecosystem/algorithms/hdc/) |
| **MCTS** — Monte Carlo tree search | stage 9; the planning stage | [MCTS](/ecosystem/algorithms/mcts/) |
| **Sobel** — edge detection | the vision transcoder, `io.matrix.transcoders.VisionEdgeEncoder` | [Sobel](/ecosystem/algorithms/sobel/) |
| **FFT** — discrete Fourier transform | the audio transcoder, `io.matrix.transcoders.AudioFFTEncoder` | [FFT](/ecosystem/algorithms/fft/) |
| **Biochemical network** | the modulator dynamics, `io.matrix.federation.liquid.biochemistry` | [Biochemical](/ecosystem/algorithms/biochemical/) |
| **Stigmergy** | the indirect coordination of the federation | [Stigmergy](/ecosystem/algorithms/stigmergy/) |

## The secondary algorithms

Twelve more, with their own pages under `docs-v2/algorithms/`:

| the algorithm | the page |
|---|---|
| BrcChain — the block-referenced chain for state consensus | [BrcChain](/algorithms/BrcChain) |
| ConversationProtocol — the multi-party conversation state machine | [ConversationProtocol](/algorithms/ConversationProtocol) |
| FROZEN-EthicalFNL — the frozen ethical layer | [FROZEN-EthicalFNL](/algorithms/FROZEN-EthicalFNL) |
| FederatedMesh — the peer-to-peer federation topology | [FederatedMesh](/algorithms/FederatedMesh) |
| Hansel-Chains — the breadcrumb audit chains | [Hansel-Chains](/algorithms/Hansel-Chains) |
| HashChain-Audit — the tamper-evident audit log | [HashChain-Audit](/algorithms/HashChain-Audit) |
| Legal-Axioms — the legal reasoning primitives | [Legal-Axioms](/algorithms/Legal-Axioms) |
| MPDT-GA — the multi-population drift-tracking genetic algorithm | [MPDT-GA](/algorithms/MPDT-GA) |
| Mcts-Lats — MCTS with Language Agent Tree Search | [Mcts-Lats](/algorithms/Mcts-Lats) |
| Ricci-Fingerprint — the curvature-based topology fingerprint | [Ricci-Fingerprint](/algorithms/Ricci-Fingerprint) |
| Tsetlin-Automaton — the Tsetlin machine | [Tsetlin-Automaton](/algorithms/Tsetlin-Automaton) |
| WiSARD-WNN — the weightless neural network | [WiSARD-WNN](/algorithms/WiSARD-WNN) |

**Some of these are measured, and some are not.** The capability registry states each one
on four axes; a measured result carries its benchmark, and an unimplemented idea carries
`PROPOSED`. Where a hypothesis about one of them was refuted, the refutation is indexed in
[`docs-v2/research/NEGATIVE-RESULTS-INDEX.md`](../../research/NEGATIVE-RESULTS-INDEX.md) —
for instance the MPDT-GA comparison (NR-001), whose preregistered gate was **not** met.

## A note on the numbers in these pages

The performance figures that were quoted on these pages without a measurement — "~3 ms per
cycle", "~50 ms", "10 000 iterations ≈ 500 ms", a "100 000× speed-up" — have been removed.
A benchmark a claim depends on is a **JMH run**, not a timer:

```bash
./gradlew :matrix-core:jmh -PjmhBenchmark=Name
```

The method is `docs-v2/research/PROTOCOL.md`; the gate evidence is
`docs-v2/engineering/JMH-GATE-EVIDENCE.md`. The constitution's Article VI requires it:
**any number comes only from a real run with an explicit methodological protocol.**
