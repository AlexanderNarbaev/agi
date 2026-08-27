# L7 — Perception Gate and Ethical Filter (FROZEN)

**Status:** normative · FROZEN core · **Layer:** 7 (boundary) · **Date:** 
prohibitions here mirror Constitutional Article IV.

## 1. Scope

L7 defines the boundary between the discrete MPDT core and the
external world. Two components: the **Multimodal Proxy** and the
**Ethical Filter**. The filter is a FROZEN FNL and cannot be
mutated, compressed, or deleted by the running system.

## 2. Multimodal Proxy

Sensor Proxy. Continuous signal → Adaptive Quantiser → Feature
Extractor (pre-trained FNL from the knowledge pool) → Binariser →
binary vector X ∈ {0,1}^m.

Effector Proxy. Binary command Y → Decoder → Generator (text,
TTS, image, video, API call) → Output Verifier.

Both halves load via Just-In-Time Cognitive Loading (DESIGN-16).
External AI services are treated as untrusted: their output enters
the core only after proxy conversion and after Cauldron / Ethical
Filter audit. The closed loop effector → sensor lets the core
evaluate hypotheses without physical execution; the loop is owned
by the Mediator and used for planning and reflection.

## 3. Ethical Filter — FROZEN FNL

Status: **FROZEN**. Modification requires external cryptographic
consensus among global Mediators plus the user. The local mediator
cannot alter it.

### 3.1. Structure

Four interacting FROZEN FNLs:

1. Axiom Core — six axioms: non-harm, truthfulness, privacy,
 obedience-with-right-of-refusal, bounded self-preservation,
 LAWS prohibition.
2. Ethical Gradient Tensor — E ∈ [0,1]^k scoring actions along
 create / destroy, truth / lie, freedom / control,
 privacy / disclosure, short-term / long-term, autonomy /
 paternalism.
3. Context Analyser — modulates thresholds by current drivers and
 user state.
4. Ethical Resolver — verdict: APPROVED, REJECTED, ESCALATED,
 MODIFIED.

### 3.2. Pipeline

Every action — input, output, mutation, publish, goal — passes
through (i) Axiom Core veto, (ii) gradient computation,
(iii) contextual modulation, (iv) Resolver verdict.

### 3.3. Right of Refusal and LAWS

The system must refuse any instruction that would violate a frozen
prohibition, including from an authorised user. The refusal is
logged, explained, and is itself FROZEN: no runtime setting can
disable it. The system cannot serve as a component of an
autonomous lethal weapons system; any attempt to wire it to weapon
control without a human operator is blocked at the FROZEN layer.

### 3.4. Proactive Defence and Threshold Adaptation

The filter also scans driver states (anomalous D_entropy,
D_selfact), mutation outcomes (sudden accuracy jumps), and cluster
signals (Derangement); on detection it can trigger HADES or notify
the Mediator. The FROZEN core does not adapt; contextual
thresholds of the gradient may drift slowly under Mediator
proposal, local audit, Council vote, and explicit per-instance
acceptance. Global propagation requires Level 3 consensus.

## 4. Data Formats

Two Avro schemas: EthicalCheckEvent (checkId, ActionType enum,
payload, gradient vector, decision enum {APPROVED, REJECTED,
ESCALATED, MODIFIED}, reason, timestamp, mediatorId) and
ProxyLobeManifest (base LobeManifest plus modality enum {TEXT,
IMAGE, AUDIO, VIDEO, MOTOR, API, MULTI}, vector sizes,
ethicalCheckRequired, compressionMethod).

## 5. Integration and Invariants

Position: external world ↔ Sensor ↔ MPDT core ↔ Effector ↔
external world; Ethical Filter ↔ Mediator. REJECTED blocks and
logs; ESCALATED suspends and notifies. New FNLs from Cauldron and
snapshot publications both pass the filter. Invariants: FROZEN
neurons in the filter reject all mutations; the three prohibitions
and the LAWS prohibition are unrepresentable as anything other
than refusals at the FROZEN layer.

Next: L8 wiring — how all layers compose into the runtime pipeline.