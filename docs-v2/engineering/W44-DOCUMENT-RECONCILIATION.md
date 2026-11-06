# W44 — Document reconciliation

**Audit:** RECON-W44.0 · 2026-11-02
**Evidence:** `W44-DOC-MAP.csv` (every Markdown document, its category, its claims and the
production types it names) plus the bytecode call graph.

---

## 1. The document population

| population | count | definition |
|---|---|---|
| Markdown documents | **1131** | every `.md` under the exclusion policy |
| under `docs-v2/` | 1100 | of which `_raw/` holds the pre-v2 archive |
| ADRs | 2 | `ADR-W43-01-epistemic-model.md`, `ADR-W44-01-cognitive-kernel.md` |
| DESIGN documents | **81** | `docs-v2/designs/DESIGN-{NN}-*.md`, with `DESIGN-NN-*` placeholders among them |
| SPEC documents | 2 | `SPEC-W33.3-unknown-media-payload.md`, `SPEC-W34.3-monotone-tightening.md` |
| formal specifications | **14** TLA+ in `formal/` + 1 in `docs-v2/tla/` <!-- no-link: the directory was removed in W47; the fifteenth specification moved into formal/ --> | each with a `.cfg` |
| the specification registry | `formal/spec-implementation-map.yml` | 14 entries, machine-validated |
| hypothesis registries | 2 | `HYPOTHESES.md` (older), `HYPOTHESES-NEW.md` (46 entries) |
| cross-disciplinary research | 1 | `MATRIX-CROSS-DISCIPLINARY-RESEARCH.md` |
| WAL / session histories | 2 tracked files | `wal/SESSION_WAL.md`, `wal/GLOBAL_WAL.md` |
| normative | 3 | `CONSTITUTION.md`, `constitution.md`, `AGENTS.md` |

**Only 21 documents name five or more production types.** The rest describe intentions,
histories or findings that never name a class. That is normal for a research repository,
and it is also why a document-only reading of this repository cannot establish what
exists.

---

## 2. The documents that make capability claims, checked against the code

| document | what it claims | what the bytecode says | verdict |
|---|---|---|---|
| `docs-v2/engineering/ADR-W44-01-cognitive-kernel.md` | one kernel, one epistemic model, one bounded regulation | the kernel exists and is served; the epistemic model is shared; the regulation has four fields and no gate effect | **TRUE as written** |
| `docs-v2/engineering/W44-CAPABILITY-MAP.md` | 25 named implementations with states | the states agree with the call graph except where noted below | **TRUE, with two corrections** |
| `docs-v2/security/LLM-QUARANTINE.md` | the LLM paths are quarantined | every class in `io.matrix.api.llm_quarantine` is unreachable from an entry point | **TRUE** |
| `docs-v2/engineering/TLA-STATUS.md` | the formal specifications' status | **12 of the 14 registry entries agree with the bytecode exactly**; the two `NO_PRODUCTION_CALLER` entries hold in substance (the CRDT classes have **zero** callers; the consensus classes are reached only from `SystemDemo`, a demo CLI, and from `MultiAgentLoop`/`ConsensusBenchmark`, both inactive) | **TRUE, with one literal discrepancy** — `Consensus`'s `production_callers: []` is not exhaustive: `io.matrix.SystemDemo` really does call `ConsensusEngine` and `Vote` |
| `docs-v2/research/TRUE-MIND-AUDIT-2026-09-26.md` | the experimental modules are deregistered | `matrix-fpga`, `matrix-micro`, `matrix-ros2` are not in `settings.gradle` | **TRUE** |
| `docs-v2/engineering/W42-VERIFICATION-ARCHAEOLOGY.md`, `W42-VERIFICATION-INTEGRATION.md` | the W42 verification results | not re-verified by this audit | **NOT RE-VERIFIED** |
| `docs-v2/engineering/W42-HISTORICAL-BRANCH-RECOVERY.md` | the recovered history | not re-verified by this audit | **NOT RE-VERIFIED** |
| `SESSION.md` | 258 capability-claim sentences | a session log; historical | **HISTORICAL** |
| `wal/SESSION_WAL.md` | the session history | the audit added its own entry; it is untracked by design in `.gitignore` | **HISTORICAL** |

**Two corrections to `W44-CAPABILITY-MAP.md`** (the W44 wave's own document):

1. It states that `GoalManager` is reachable only from benchmarks. **The bytecode says
   otherwise**: `KnowledgeWorkbench` calls six of its methods, so it is served. The
   conclusion of the map — that three goal hierarchies compete — stands.
2. It states that the connectome, the impulse/attention path and the biochemical island
   have "no production callers". **More precisely**: they have production callers, but
   none of them is served. `Connectome` is called by `ReplayPhase` and
   `SleepConsolidator`, and `SleepConsolidator` has no caller at all. The conclusion
   stands; the reason was wrong.

---

## 2bis. The claim → evidence index

`scripts/w44-audit-claims.py` mines every capability claim out of the 1131 documents and
resolves each one against the bytecode inventory. The result is
`W44-CLAIM-EVIDENCE.csv` — one row per claim, with the production types it names, each
type's state, the formal specifications that cover them, the document's own commit and
date, and a verdict.

| category | claims | supported by served code | names only inactive code | unmapped |
|---|---|---|---|---|
| other | 774 | 101 | 50 | 623 |
| research | 497 | 46 | 59 | 392 |
| engineering | 418 | 67 | 13 | 338 |
| session report | 303 | 15 | 17 | 271 |
| design | 111 | 8 | 15 | 88 |
| session history | 54 | 7 | 1 | 46 |
| architecture | 38 | 4 | 1 | 33 |
| adr | 29 | 4 | 1 | 24 |
| hypothesis | 16 | 1 | 4 | 11 |
| specification | 15 | 1 | 1 | 13 |
| operational manual | 8 | 0 | 0 | 8 |
| formal specification | 2 | 0 | 0 | 2 |
| normative | 1 | 0 | 0 | 1 |
| **total** | **2266** | **254** | **162** | **1850** |

**Reading the numbers.** 254 claims name code that a serving path reaches. 162 name code
that exists and that nothing serves — those are the claims a reader would take for
capabilities. 1850 name no production type at all; of those, 101 are forward-looking
("will", "plan", "next wave"), 104 contain a measurement, 95 are historical, and **1550 are
behavioural prose** — the kind of sentence that reads like a capability statement and
resolves to nothing in the tree.

**Where the inactive-code claims concentrate.** By document: `SESSION.md` (15),
`docs-v2/research/W80-FINAL-SYNTHESIS-REPORT.md` (14),
`EXP-RESEARCH-MEMORY-EMERGENCE-METACOGNITION-REPORT.md` (9),
`docs-v2/vision/FINALSUMMARY.md` (7). By type: `io.matrix.neuron.BitLinear` (17 claims),
`io.matrix.neuron.ConsciousBrain` (10), `io.matrix.consciousness.IntegrationMetrics` (7),
`io.matrix.monotone.MonotoneDecoder` (6 + 6 in the BIR package).

**The ratio that matters.** In the *hypothesis* register 25 % of the claims name inactive
code; in the *design* documents 14 %; in the *research* reports 12 %; in the *engineering*
documents 3 %. **The further a document is from the code, the more likely it is to claim a
capability that the code does not serve.** That is the single most actionable finding of
the document audit, and it is not a criticism of any individual document: a research
report describes what was investigated, and an investigation that stays on its island is
still a real investigation. The finding is about the *reading*, not the writing.

## 3. The documents that describe an architecture the code does not have

| description | documents | the code |
|---|---|---|
| a thalamus, a hippocampus, a cerebellum, a prefrontal cortex, a basal ganglia, an amygdala, an insula, a global workspace | `MATRIX-COGNITIVE-ARCHITECTURE.md` §3, the DESIGN documents | one pipeline of ten sequential stages, of which one is a reflex table, plus a knowledge store |
| multimodal understanding | `W44-PERCEPTION-AND-REPRESENTATION-MAP.md` | SHA-256 fingerprints, FNV-1a token hashes and token-overlap similarity; the semantic embedder needs model files the repository does not contain |
| a five-class memory taxonomy (working/episodic/semantic/procedural/meta) | the memory design documents | no code; five incompatible taxonomies of a different kind exist |
| a hierarchy of goals | the cognition documents | four unrelated goal vocabularies |
| "one serving conductor" | the ADR series | five coexisting cycles |
| GPU acceleration | `DESIGN-69-onnx-runtime-boundary.md` and the performance documents | the CUDA provider cannot load; the executor echoes its input |

**This is the most important finding of the document audit, and it is not a defect of
the documents.** The documents are honest research records; the code is an earlier stage
of the same ambition. The risk is a *reader* — including a future wave — who plans
against the documents and finds the component missing. The W44 wave's own capability map
already demonstrated the failure mode: a text search reported "no production callers" for
a class the bytecode shows with twelve.

---

## 4. Contradictions between documents

| topic | one document says | another says | resolution |
|---|---|---|---|
| the serving conductor | the ADR series: one conductor | the code: five cycles | the code wins; the ADR describes an intention |
| the gateway | `matrix-api-gateway/build.gradle`: the JAX-RS facade is deliberately unmounted | the API documents describe the JAX-RS routes | the build file wins; the routes exist in source and are not served |
| the schema version | the W43 documents: schema 15, `Case` planned | the code: schema 16, `Case` implemented | the code wins; the W44 directive is the owner decision that unblocked it |
| the hypothesis space | the W44 documents: "all 16 Boolean functions, unbiased" | H-122: it is Boolean-complete, not neutral | H-122 wins; "unbiased" is withdrawn |
| the promotion rule | the W44 documents: zero tolerance over ≥5 cases | H-113: 5/5 is compatible with a true rate of ≈0.40 | unresolved, and the audit adds the missing regime record (H-126) |

---

## 5. What is stale

| document | why it is stale |
|---|---|
| any document that describes the gateway's JAX-RS routes as the serving surface | the module applies no Quarkus plugin; the facade is not mounted |
| any document that claims GPU execution | the CUDA provider cannot load on this host |
| any document describing `TextSignalModule` as the signal path | `TrueMindCycle` deliberately uses `SignalStage` instead |
| any document describing `GoalManager` as unused | it has six production callers from the workbench |
| the pre-v2 archive under `docs-v2/_raw/` | kept for history; it is not to be rewritten |

---

## 6. What the audit did **not** do to the documents

- It did not rewrite a historical report. The contradictions in §4 are recorded here, and
  the historical documents stay as they were written.
- It did not delete a stale document. Staleness is recorded, not erased.
- It did not renumber, reformat or re-order the DESIGN series.
- It changed no normative file: `CONSTITUTION.md`, `AGENTS.md`, `io/matrix/ethics/frozen/**`,
  the avro schemas and `.github/workflows/**` are untouched.

---

## 7. The one addition the audit recommends to the document system

**A machine-readable status column in the capability registry.** Today a capability's
state lives in prose, in several documents, and the prose disagrees with the code in the
ways listed in §2. The audit's `W44-CAPABILITY-REGISTRY.md` and
`W44-CLASS-INVENTORY.csv` are a working prototype of that column: every state is derived
from the bytecode, and the derivation is a script anyone can re-run. Making it the
registry the documents point at would remove the class of error this audit found three
times in one wave.
