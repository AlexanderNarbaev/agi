# DESIGN-58 — Capability Levels Roadmap (Infant Cognition Milestones)

**Status:** v2 design. Wave W31/W32 implemented.

## 0. Implementation Status (2026-09-12)

| Level | Capability | Implementation | Tests | Status |
|-------|-----------|----------------|-------|--------|
| L0 | Fabric | HdcEncoding + HdcBinding + BitLinear + CodebookMemory + HebbianUpdater | 134 | ✅ DONE |
| L1 | Pavlov operant + Sokolov | HdcConditioning + SokolovHabituationExperiment | 22 | ✅ DONE |
| L2 | Spelke core knowledge | SpelkeCoreKnowledge (4 experiment types) | 11 | ✅ DONE |
| L3 | Cross-modal HDC | CrossModalPaired audio↔visual | 14 | ✅ DONE |
| L4 | Piaget sensorimotor | NcaBrainSimulator + SensorimotorLoop (motor babble + contingency) | 29 | ✅ DONE |
| L5 | Symbol grounding | HdcAsLlmPreprocessor + LlmOutputDecoder | 35 | ✅ DONE |
| L6 | Compositional reasoning | SyntheticGrammarExperiment + MpdtHdcBridge | 23 | ✅ DONE |
| **L7** | **Conscious Integration** | **ConsciousBrain** + BitLinearDreamer + TwoStageConsolidator + FreeEnergyLoss + SelfModel + WuWeiPolicy + StigmergicFederation + EmbodiedNcaCortex + HermeneuticLoop + PragmaticTest + **IntegrationMetrics** (Φ_binary + ΦF + C_N) | **85+** | **✅ DONE** |

**W31 Wave totals:** 16 brain classes + 1 integration bridge, 285 W31 unit/integration tests + 8 performance benchmarks, 0 failures. **All 7 Capability Levels implemented.**

**W31 Wave totals:** 15 brain classes + 1 integration bridge, 258 W31 unit/integration tests + 8 performance benchmarks, 0 failures.

**Architecture Decision Record:** ADR-2026-09-12-001 (docs-v2/architecture/)

## 1. Мотивация

Исторически cognitive наука дала **measurable milestones of cognition** для раннего развития. Если
наш brain simulator будет **упорядоченно переоткрывать** эти milestones, мы получим:

1. **Доказуемые claims** — каждый capability level = measurable benchmark.
2. **Acceptance criteria** — independent reviewers могут проверить.
3. **Comparisons to biological systems** — clearly defined behaviour differences to infants.
4. **Progress tracking** — каждый уровень занимает примерно одно и то же количество работы (1-3 недели).

## 2. Capability Levels

### Level 0 — Fabric (по сути есть)
**Цель:** 78 алгоритмических классов + 24-block brain simulator + native image C-extension.
**Benchmark:** все 78 классов имеют passing JUnit test. Brain simulator может пройти forward pass.
**Timeline:** done
**Real-data analog:** `--`

### Level 1 — Pavlov Operant Conditioning (~3 month infant)
**Цель:** brain simulator learns "stimulus-response" pairs.
**Базовый learning dynamic:** Hebbian update.
**Benchmark:**
- 1000 unique S-R pairings learned in 5 minutes.
- Novel S' (not in training) generalises to predicted R.
- Habituation curve matches Sokolov 1963 (exponential decay).
- Dishabituation: novel S reinstates response.
**Cognitive science sources:** Sokolov 1963 (Neuron model of the stimulus), Spitz 1957 (No and Yes).
**Math references:**
```
Δw = η · (s ⊗ a) - decay · w  (Hebbian with decay)
a(t+1) = a(t) · γ + (1-γ) · s(t)  (activation with habituation)
```
**See Spitz habituation chapter for what counts as "novel"**: significance = |a - a_baseline| > 3σ.

### Level 2 — Spelke Core Knowledge (~5 month infant)
**Цель:** brain simulator demonstrates 4 Spelke core knowledge behaviors.
**Базовый learning dynamic:** reinforcement from goal-attainment.
**Benchmark:**
- **Object permanence**: A-not-B task variant. Object is seen, hidden at location A. On trial 2-3, novel location B is used. If infant has object permanence, they look at A after multiple A-B trials — not B. Brain should make same prediction error.
- **Agent detection**: distinguish animate (self-propelled) from inanimate (inertia) agents from motion patterns. Movie-style dataset (Spelke 1995 Heider-Simmel inspired).
- **Numerosity (small count)**: count 1, 2, 3 items correctly (>chance for 1 vs 2 vs 3).
- **Goal attribution**: distinguish "agent acting on goal" from "random motion."
**Cognitive science sources:** Spelke & Kinzler 2007 (Topics in Cognitive Sciences), Spelke 1990 (Principles of object perception).

### Level 3 — Cross-Modal HDC Paired (~7 month infant)
**Цель:** audio + visual paired memory.
**Базовый learning dynamic:** HDC XOR binding, cleanup memory.
**Benchmark:**
- 1000 (audio embedding, visual embedding) pairs stored.
- Query with audio → retrieves correct visual with 95%+ top-1 accuracy.
- Query with visual → retrieves audio with 95%+ top-1 accuracy.
- 30% noise tolerance (audio XOR retrieval works at 30% bit flip).
**Cognitive science sources:** Mithen 1996 ("The Prehistory of the Mind") — multimodal integration origin.

### Level 4 — Piaget Sensorimotor Stage (~12 month infant)
**Цель:** action-outcome prediction with feedback.
**Базовый learning dynamic:** Hebbian (positive for matched predictions, negative for mismatches).
**Benchmark:**
- 100 patterns of (state, action) → next_state.
- Forward model predicts next_state in <100ms.
- Mismatch (predicted vs actual) triggers Hebbian update.
- After training, 90% on novel (state, action) pairs.
**Cognitive science sources:** Piaget 1954 "The Construction of Reality in the Child," Bernstein 1947 levels of construction.

### Level 5 — Symbol Grounding (~18 month infant)
**Цель:** symbolic labels ↔ referents.
**Базовый learning dynamic:** paired-associative binding with bidirectional HDC XOR.
**Benchmark:**
- 100 unique (symbol, referent) pairs.
- Symbol → referent with 95%+ accuracy.
- "What's this?" → referent.
- "Show me X" → referent.
- Storer 2005 paradigm: infant follows instruction using just one-word symbol.
**Cognitive science sources:** Bloom 2000 ("How Children Learn the Meanings of Words"), Pinker 2007 ("The Stuff of Thought").

### Level 6 — Compositional Reasoning (~24 month infant)
**Цель:** 2-hop chains: X → Y → Z. Predict Z from X.
**Базовый learning dynamic:** memory addressable via intermediate.
**Benchmark:**
- 1000 (X, Y, Z) chains.
- "Given X, retrieve from memory → output Z" with 80%+ accuracy.
- HDC binding used for chained composition.
**Cognitive science sources:** Tomasello 2003 ("Constructing a Language"), Vygotsky 1934 ("Thought and Language").

## 3. Как измерять

Каждый level имеет measurables:
- **% accuracy** (binary yes/no).
- **Latency** (ms per query).
- **Memory footprint** (MB).
- **Generalization gap** (train vs test split, target <10%).

```java
class BrainCapabilityTest {
    static void main(String[] args) {
        double level1_accuray = PavlovTest.run();
        double level2_object_perm = SpelkeTest.run();
        double level3_xmodal = CrossModalTest.run();
        // etc.
        System.out.printf("Level 1: %.1f%%\nLevel 2: %.1f%%\n", level1_accuray*100, level2_object_perm*100);
    }
}
```

## 4. Timeline Targets

| Level | Target weeks | Risks |
|---|---|---|
| 1 | 1-2 | Спокойный. Hebbian behavior — базовый. |
| 2 | 3-5 | Object permanence легко; agent detection требует motion vectors. |
| 3 | 6-8 | HDC known, cleanup memory tries известны. |
| 4 | 9-12 | Forward model впервые — нужны benchmarks. |
| 5 | 13-18 | Symbol grounding требует реляционной логики. |
| 6 | 19-26 | Compositional reasoning — first-order logic + HDC. |

**Total target:** 26 weeks = 6 months → publication-grade demo.

## 5. Что считать НЕ итогом

- **Не** test on MMLU/HellaSwag — это LLM benchmarks, не infant cognition.
- **Не** test on ImageNet classification — это supervised learning.
- **Не** test на ARC-AGI (Chollet's benchmark) — пока слишком ambitious.

## 6. Что считать хорошим результатом

- **Level 1**: 1000 habits learned in 5 minutes, 80% generalisation.
- **Level 2**: 90% on object permanence, 75% on numerosity, 70% on agent detection.
- **Level 3**: 95% audio↔visual retrieval at 0% noise, 80% at 30% noise.
- **Level 4**: 90% next-state prediction on novel (state, action).
- **Level 5**: 95% symbol↔referent.
- **Level 6**: 75% 2-hop retrieval.

These are not state-of-the-art but they are **demonstrably infant-class behaviors** that the
system can be tested for and improved iteratively.

## 6a. Capability registry — RECON-W41 (2026-11-01)

**What this section is.** META-R5 requires every new measurement of "the brain
can X" to name the capability level it belongs to. This wave added a learning
workbench with a reasoning engine and a learner-instrument. It did **not** add
a new level: the levels describe the cognition pipeline, and the workbench is
a measurement instrument for the levels it claims to reach, not a stage of its
own. The registry below says which level each new measurement touches, and
what the measurement does and does not establish.

| Capability measured | Level | What was measured | What it does NOT establish | Evidence |
|---|---|---|---|---|
| Evidence-grounded derivation over propositional logic | **L6 — compositional reasoning** | A question with a derivable answer is classified DERIVATION, the recorded rules are checked by an exact bounded entailment procedure, and the status of each is reported (`DERIVED_VALID`). The logic domain holds 3 recorded rules and 28 claims. | That the pipeline reaches L6's 75% two-hop retrieval target: that target is measured by `SyntheticGrammarExperiment`, not by the workbench. The workbench's result is about the reasoning engine, not about the HDC bridge. | `ReasoningEngineTest`, `LogicDomainTest`, live `POST /workspaces/logic/reason` |
| A claim's standing, separated into layer, warrant and derivation status | **L5 — symbol grounding** | A claim carries three independent facts; an established claim always keeps a warrant (a claim with no warrant was asserted, and asserting is not knowing). Modelled and mutation-tested. | That the cognition pipeline grounds symbols better than before: nothing in the cognition pipeline changed in this wave. | `KnowledgeAxes.tla` (model-checked, 4 mutants killed), `KnowledgeProvenanceTest` |
| The learner's retention, measured where it can fail | **L5 — symbol grounding** | A four-phase teach-back over disjoint item sets with a closed-book learner; the **delayed phase fails** (0/1). | Any claim about human learning, and any claim about learning in general: six measured items on one concept is a demonstration, not a generalisation. | `TeachBackExperimentTest`, live `POST .../teach-back-experiment` |
| A learning goal across a restart | **L5 — symbol grounding** | A goal with its subtasks, budget, checkpoint and recovery instructions is intact after `kill -9`, and a restarted process can ask what it was doing. | That the system plans: the manager resumes a goal, it does not choose the next subtask. | `GoalPersistenceTest`, live restart verification |
| Honest abstention | **L5 — symbol grounding** | A question outside the workspace is refused with the uncovered terms named; the reason is recorded. | That the pipeline refuses in general: the refusal is the workbench's, at the level of retrieval. | `KnowledgeWorkbenchTest`, live `POST .../ask` |

**The levels this wave does not reach.** The cognition pipeline's own L0–L7
targets are untouched by this wave, except that L5 and L6 now have an
instrument that can measure claims about them honestly. No number in §6 was
re-measured by this wave, and none is claimed to be.

**The recorded hole.** One mutant survives the gate — `Consensus-evaluate-without-quorum`
— and the registry's Consensus entry does not claim a checked quorum because
of it. See `docs-v2/engineering/MUTATION-REPORT.md`.

## 6b. Capability registry — CRJ-01 / ADAPTIVE-COMPETENCE (2026-11-27)

**What this section is.** META-R5 requires every new measurement of "the brain can X" to
name the capability level it belongs to, and to say what the measurement does **not**
establish. This milestone measured one thing: **that a learned competence knows the
difference between a question it can answer and a question it cannot** — and it did so on
the **real served route**, against the stateless reference, on two fresh synthetic
development domains.

**The level.** **L5 — symbol grounding.** The measurement is about a claim's standing
*relative to the evidence that answers the question*: the procedure's answer and the
reference's answer are compared claim by claim, and the competence is admitted only when it
can hold the workspace's own evidence for the question asked. That is a grounding claim —
"this answer stands on this evidence" — and not a planning, a reasoning or a motor claim.
**No level was added, and no level's target was re-measured.** The pipeline's own L0–L7
targets are untouched by this milestone.

| Capability measured | Level | What was measured | What it does NOT establish | Evidence |
|---|---|---|---|---|
| A learned competence's applicability, decided on the evidence for the question asked | **L5 — symbol grounding** | On the served route, a procedure whose support set cannot hold the claim the workspace's own reading would cite first is **not** selected; the stateless reference answers instead, with its own evidence; the refusal happens **before** any durable side effect; the reason is recorded with the evidence it was made from. The served path's grounding is at **parity** with the stateless reference — **30 of 30** cases on two synthetic DEV domains — where the previous milestone (H2) measured 10 of 12 against the reference's 12 of 12. A competence revision was promoted in both domains after a failure episode, validated on a separate DEV subset; one revision was first **refused** for a precise reason and the refusal was kept. The changed competence is measured on a **distinct subsequent task** — a question of the failure episode's own document that no other case uses: before the revision the first competence cannot hold it and the reference answers, after it the **exact revised procedure** answers it, grounded, with its effect verified. The revision runs in its own workspace, so no assertion depends on the order the tests ran in. The deliberate RED — the same suite with the gate removed by one expression — fails 6 of 16 tests. | That the pipeline reaches any level's target: no level's number was re-measured. That the competence is **cheaper** than the stateless reference: of the eight pre-registered grid cells only some separate at all, the separating ones are the wide corpora with a one-document support, and at least one cell changes verdict between runs. That the measurement is sealed: **the sealed evaluation is NOT EXECUTED**, because the host has one uid, no usable namespace, and a `0400` file was read by a same-uid process — the protocol's own "deny the implementer read access before the frozen run" cannot be met. That the cases are independent of the implementer: the independent case author did not deliver, and the questions were written by the implementer's deterministic generator. | `AdaptiveCompetenceServedTest` (15 tests) and the three corpus instruments (3 tests), `scripts/adaptive-competence-gate.sh` (29 checks) and its self-test (6 mutations, each caught by the check that belongs to it), the retained evidence under `docs-v2/engineering/evidence/crj01-adaptive-competence/` (manifest: 11 files OK), `docs-v2/engineering/CRJ-01-ADAPTIVE-COMPETENCE.md` |

**The honest limit of this measurement.** It establishes that a competence can be **refused**
on evidence, and refused before it does anything. It does not establish that refusing is
**worth it** — the paired interval contains zero in every cell the rule does not separate — and it does
not establish that the competence's **reach** is correctly bounded: the measurement exposed
that a procedure's support set is the union of the sources its learning episode cited, so
an episode whose answer reaches a second document widens the support (16 of 96 claims of
the DEV corpora reach a second document even under a focused question). That width is the
next open question, and it is a question about **what a competence is**, not about the
retrieval.

## 7. Where publishing fits

After reaching Level 6:
- Workshop on "Cognitive Model Evaluation" at NeurIPS/ICML (annual).
- ALIFE conference (foundational for emergent computations).
- BICA (Biologically Inspired Cognitive Architectures).
- Frontier of HDC + classical AI.

Title: "On the Acquisition of Infant-Class Capability Levels in an Edge-AI Boolean+HDC Brain Simulator."

