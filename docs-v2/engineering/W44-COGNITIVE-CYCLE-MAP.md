# W44 — Cognitive cycle map

**Audit:** RECON-W44.0 · 2026-11-02
**Evidence:** `W44-CLASS-INVENTORY.csv`, `W44-CALL-GRAPH.csv` (bytecode), the stage
sources themselves, and the W44 end-to-end run.

This map answers one question: **what actually executes, in what order, when MATRIX is
asked something** — and which of those steps exist only on paper.

---

## 1. The five cycles

| cycle | FQN | module | served | stage pipeline | notes |
|---|---|---|---|---|---|
| **TrueMindCycle** | `io.matrix.brain.runtime.TrueMindCycle` | matrix-brain-runtime | yes | 10 stages | the HTTP-facing brain |
| **MindCycle** | `io.matrix.brain.runtime.MindCycle` | matrix-brain-runtime | yes | 2 phases | reached only from the CLI |
| **BirBrainCycle** | `io.matrix.brain.BirBrainCycle` | matrix-core | yes | BIR/HDC/Tsetlin/MCTS | produces `BrainCycle.CycleResult` |
| **ConsciousnessLoop** | `io.matrix.reasoning.ConsciousnessLoop` | matrix-core | yes | — | 1 caller |
| **BrainLoopService** | `io.matrix.consciousness.BrainLoopService` | matrix-core | yes | — | **25 callers** — the most-used class in the repository |

**None of the five delegates to another.** They are five answers to "what is a cycle",
each with its own result type: `MindResult`, `BrainCycle.CycleResult`,
`ConsciousnessLoop`'s own type, and `BrainLoopService.CycleResult`.

---

## 2. The TrueMindCycle pipeline, stage by stage

Read from `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/TrueMindCycle.java`.

| stage | what it runs | what it returns | verdict |
|---|---|---|---|
| 0 | empty-input guard | `"I cannot answer an empty input."`, confidence 0.10 | real |
| 1 | `ReflexStage` | a canned reply at confidence 0.85, **early return** | a keyword reflex, not cognition |
| 1b | `HarmfulIntentClassifier` | appends a refusal | real; it can only *append* a refusal, never authorise |
| 2 | `SignalStage.encode` | a per-token **FNV-1a** hash observation | real hashing, **not** semantic encoding |
| 3 | `SaliencyStage` | a salience score and a ranker | real |
| 4 | `ArithmeticStage` | regex-driven `BigInteger` composition | real, exact, narrow |
| 5 | `AnalogyStage` | a **seed table** lookup | a table, not an analogy mechanism |
| 5b | `RelationalReasoningStage` | relation extraction | real |
| 5c | `BilingualFactLookup` | a bilingual fact lookup | real |
| 6 | `BirInferenceStage` | a seeded Boolean table | real |
| 7 | `HdcRetrievalStage` | a similarity search over persistent HDC | real, **nearest-neighbour recall** |
| 8 | `TsetlinStage` | a learning-automaton classification | real |
| 10 | `ModulatorStage` + `SafetyMonitor` | modulator values and a safety decision | real |
| — | `finalize(...)` | composes the reply from the stage results | real |

**The stage numbering skips 9.** The audit could not find a stage 9 in the source; the
numbering is a fossil of an earlier design. It is recorded here as an observation, not as
a defect.

**What the pipeline does not contain:** no prediction of the world's future state, no
hypothesis space, no experiment, no error signal, no revision, no procedural memory
promotion. The W44 kernel contains all five; `TrueMindCycle` contains none.

---

## 3. The W44 cognitive kernel pipeline

Read from `matrix-core/src/main/java/io/matrix/cognitive/CognitiveKernel.java`, and
demonstrated by the end-to-end run of the packaged application.

| phase | actor | what it does | persists |
|---|---|---|---|
| PERCEIVE | `ChannelClassifier` | classifies the payload **by form** (TEXT/STRUCTURED/BINARY/TABULAR/TIME_SERIES) | — |
| RESTORE | `MicroWorldSession` + `MicroWorldCodec` | rebuilds the model from the recorded cases | reads `epistemic_case` |
| PREDICT | `MicroWorldSession.predict` | predicts, records the prediction **before** the outcome | `cognitive_prediction` |
| SELECT | `AlgorithmSelector` | admits only methods whose declared preconditions hold; scores them | `cognitive_event` |
| COMPUTE | the selected method | the answer, or a refusal | — |
| PROPOSE | `MicroWorldSession.proposeExperiment` | the discriminating experiment, by measured information gain | — |
| PROBE | `MicroWorldEnvironment` | runs it inside the sandbox | — |
| OBSERVE | `KnowledgeWorkbench.recordObservation` | writes the **case** with its conditions and the computed relation | `epistemic_case`, `evidence` |
| LEARN | `ProcedureValidator` | promotes a cheap procedure only after it agrees with the reference on held-out cases | `cognitive_procedure` |
| REGULATE | `RegulationController` | folds the cycle's events into the drives and computes a bounded bias | `cognitive_event` |
| ANSWER | the kernel | the answer and the trace | `cognitive_event` (9 steps) |

**Demonstrated**: `GET /v1/cognitive/trace/{episodeId}` returns the nine steps
PERCEIVE, RESTORE, PREDICT, SELECT, COMPUTE, PROPOSE, LEARN, REGULATE, ANSWER;
`GET /v1/cognitive/procedures` returns
`proc-microworld-settled-model` with `requiredFacts [MODEL_SETTLED]`,
`referenceAlgorithmId micro-world-hypothesis-enumeration`, `validationAgreement 1.0` over
4 cases.

---

## 4. How the two cycles relate — they do not

| | TrueMindCycle | the W44 kernel |
|---|---|---|
| process | `matrix-api-gateway` | `matrix-core` (Quarkus) |
| entry | `MinimalHttpServer` → `POST /v1/analyze` | `POST /v1/cognitive/observe` |
| world model | persistent HDC + episodic log | the knowledge store's claims and cases |
| prediction | none | yes, recorded before the outcome |
| experiment | none | yes, inside a sandbox |
| error | none | yes, with a disposition and provenance |
| procedure reuse | none | yes, validated against a reference |
| regulation | `ModulatorStage`, `NeuroModulatorHub` | `RegulationController`, 8 typed drives |
| trace | `BrcStep` | `LearningTrace`, 9 steps |

**There is no call between them.** The smallest integration that would join them is one
stage in the brain runtime's pipeline that asks the kernel over HTTP and reports the
outcome back — see `RECON-W44-ARCHAEOLOGY.md` §5, candidate 1.

---

## 5. What the cycle map says about the repository's own vocabulary

The repository's documents describe a brain with a thalamus, a hippocampus, a cerebellum,
a prefrontal cortex, a basal ganglia and an amygdala. The call graph shows **one**
functional cycle with ten sequential stages, one of which is a reflex table, plus a
separate knowledge store that answers questions from claims and evidence. The vocabulary
is richer than the mechanism by roughly an order of magnitude.

That is not a criticism of the code — the code does what it says. It is a warning to
anyone who plans an integration: **the architecture documents describe a brain; the call
graph describes a pipeline.** An integration planned against the documents will not find
the component it expects.
