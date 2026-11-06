# W44 — Memory architecture map

**Audit:** RECON-W44.0 · 2026-11-02
**Evidence:** `W44-CLASS-INVENTORY.csv`, `W44-CALL-GRAPH.csv` (bytecode), the store
sources, and the W44 schema-16 migration.

---

## 1. The stores that exist

| store | FQN | persistence | served | what it holds |
|---|---|---|---|---|
| the knowledge store | `io.matrix.knowledge.KnowledgeStore` | SQLite, schema 16, WAL + `synchronous=FULL` | yes (12 callers) | sources, concepts, representations, claims, evidence, derivations, revisions, prerequisites, cases, goals |
| the cognitive store (W44) | `io.matrix.cognitive.CognitiveStore` | SQLite, own tables on the **same file** | yes | episodes, predictions, errors, procedures, tasks, events |
| the HDC store | `io.matrix.brain.runtime.PersistentHdcStore` | persistent | yes (9 callers) | hypervector memories |
| the episodic log | `io.matrix.brain.runtime.EpisodicLog` | persistent | yes (3 callers) | episodes of the brain runtime |
| hierarchical memory | `io.matrix.memory.HierarchicalMemory` | in-memory | yes | a tiered memory |
| persistent hierarchical memory | `io.matrix.memory.PersistentHierarchicalMemory` | persistent | yes | the same, persisted |
| SDM | `io.matrix.memory.Pattern.Sdm`, `SdmReader` | — | **dormant** | sparse distributed memory |
| the connectome | `io.matrix.brain.runtime.graph.Connectome` | in-memory graph | **island** | nodes and synapses |

**Two knowledge stores would be a violation; there are two *writers* to one file.** The
W44 `CognitiveStore` opens its own connection to the same SQLite file that
`KnowledgeStore` owns. The consequence is stated in `ADR-W44-01`: a write that spans both
is **not atomic**. The mitigation is ordering (cognitive first) plus content-derived ids
that make a repeated write idempotent.

---

## 2. The five taxonomies of memory

| taxonomy | defined in | levels | reconciled with the others? |
|---|---|---|---|
| `MemoryHierarchyTier` | `io.matrix.memory` | M0, M1, M2 | **no** |
| `HierarchicalMemory` | `io.matrix.memory` | L0-L4 | **no** |
| `MemoryHierarchy` | `io.matrix.memory` | L1-L5 | **no** |
| the M3 federation channels | `io.matrix.federation` | channel-based | **no** |
| the M4 noosphere | `io.matrix.noosphere` | — | **no** |
| the W44 mapping | `io.matrix.cognitive` | cases = episodic, procedures = procedural | **no** — it is a convention, not a schema |

**The audit found no class that maps one taxonomy onto another.** The audit also found no
class that reads a `HierarchicalMemory` tier and writes it into the knowledge store, or
the reverse. Two stores are written by two mechanisms and read by two others, and the
"memory" of MATRIX is five names for structures that do not share a key.

---

## 3. The three definitions of consolidation

| mechanism | FQN | what it consolidates | served | deletes? |
|---|---|---|---|---|
| `KnowledgeConsolidator` | `io.matrix.knowledge` | claims and questions: marks a claim consolidated, revives a question | yes | **never** — it marks |
| `SleepConsolidator` | `io.matrix.brain.runtime.graph` | the connectome, cross-validated | **no** — 0 production callers | it anneals edges |
| `TwoStageConsolidator` | `io.matrix.neuron` | neuron-level | yes | — |
| `ConsolidationCycle` | `io.matrix.sleep` | — | — | — |
| the W44 promotion | `io.matrix.cognitive.algorithms.ProcedureValidator` | a cheap procedure, against a reference | yes | no — it appends |

**Only one of them deletes anything, and it is not on a serving path.** The knowledge
consolidator — the one that *is* on a serving path — only marks. This is the behaviour
the W43 wave asserted and the W44 wave preserved.

**The risk that remains is the reload path, not the consolidator.**
`POST /v1/workbench/workspaces/{id}/load` calls `KnowledgeStore.deleteWorkspace`, which
deletes `contradiction`, `evidence`, `derivation` and `revision` for the workspace
(`KnowledgeStore.java:700-726`). The audit confirms it in the source: the consolidator
marks, the loader deletes. That asymmetry is the single most dangerous behaviour in the
memory architecture, and it is still open.

---

## 4. What learns, and what is written

| writer | writes | reachable from an entry point | outcome-level measurement |
|---|---|---|---|
| `KnowledgeConsolidator` | claim status, question revival | yes | **no** |
| the W44 `CognitiveStore` | cases, predictions, errors, procedures, tasks | yes | **yes** — the W44 benchmark measures survivor counts, error dispositions and promotion agreement |
| `SleepConsolidator` | connectome edges | **no** | no |
| `PersistentHdcStore` | hypervector memories | yes | no |
| `EpisodicLog` | episodes | yes | no |

**The W44 wave is the only place in the repository where a learning mechanism has a
measured outcome.** Everything else writes state that is never compared against a
baseline.

---

## 4bis. The promotion path — what may become VERIFIED, and what may not

This is the one question in the memory architecture that the repository answers **well**,
and the audit checked it line by line in
`io/matrix/knowledge/acquisition/ClaimValidator.java` and
`io/matrix/knowledge/acquisition/PromotionPolicy.java`.

**The rule.** A claim moves `CANDIDATE → SUPPORTED → VERIFIED` only when it is stated by
**independent** sources, where independence is the number of **distinct source content
hashes** — not the number of evidence records. The code says it in as many words:
*"One source, seen twice, is not a confirmation."* `VERIFIED` requires evidence **and**
`independentSources >= 2`. Every step is checked by `PromotionPolicy.mayPromote(...)`, and a
refusal is recorded with a reason a reader can check.

**Duplicates are superseded**, not deleted: the canonical claim keeps the evidence and the
duplicates move to `SUPERSEDED`. The claim is *rebuilt* with its new status — the code
records the bug that the first version of this path had (*"the evidence was written and the
promotion was not"*).

**What may enter that path:**

| input | can it promote a claim? | evidence |
|---|---|---|
| an imported source document | **yes** — it carries a content hash and a source id | `KnowledgeImporter` writes the evidence; `ClaimValidator` runs the promotion |
| a user assertion in the chat path | **no** | `KnowledgeWorkbench.answer(...)` produces an `Answer` and writes **no** evidence |
| a generated answer | **no** | same path — a generated answer is not a source |
| the W44 kernel's own observation | **yes**, as an observation with a case | `KnowledgeWorkbench.recordObservation` — **the only** method that writes evidence outside the importer, and it has exactly **one** production caller, `io.matrix.cognitive.CognitiveKernel` |

**The one caveat.** Independence is measured by **content hash**, and the hash says nothing
about *who* produced the content. Two different documents with identical text count as one
source, which is the right conservative behaviour; but if a generated answer were ever
ingested as a source document, it would carry its own hash and could count as a second
independent confirmation. **Nothing in the current code path does that** — the answer path
writes no evidence — and the audit records it as a property to preserve rather than a
defect to fix.

**The contrast.** Every other "learning" mechanism in the repository is looser than this
one: the consolidator only marks, `SleepCycle` promotes by importance, `RuleInductionEngine`
induces rules with a quarantine. **The epistemic promotion path is the only one with an
independence test and a policy gate, and it is the one that is on a serving path.**

## 5. How claims connect to episodes — and where they do not

| structure | connects a claim to | an observation to | an episode to |
|---|---|---|---|
| `Claim` | evidence, source, derivation, revision | no | **no** |
| `Evidence` | a claim | through `case_id` (schema 16) | **no** |
| `Case` (schema 16) | conditions, outcome, source, concept | **yes** | no — an episode id is not stored on a case |
| `cognitive_episode` | — | through `cognitive_prediction.episode_id` | yes |
| `cognitive_event` | — | — | yes |
| `EpisodicLog` | — | — | yes, in a separate store |

**The join is incomplete in both directions.** A case records the conditions and the
outcome of an observation but not the episode that produced it; an episode records its
events but the cases it produced are only reachable through the case id, not through the
episode. A reader who has an episode id cannot get its cases from the store without
scanning the case table.

---

## 6. The memory classes the directive names that do not exist

| name | status |
|---|---|
| `PersistentHdcStore` | exists, served |
| `HierarchicalMemory`, `PersistentHierarchicalMemory` | exist, served |
| `TwoStageConsolidator` | exists, served |
| `SleepConsolidator` | exists, **inactive** |
| `DreamReplayer` | exists (`io.matrix.noosphere`), served |
| `ErrorDrivenLearner` | exists (`io.matrix.cognitive`), served |
| `ReplayPhase` | exists, **inactive** |
| the "M0-M4 design" and the "L1-L3 SPEC-011 design" | documents, not code; the levels exist as constants in five incompatible taxonomies |
| a five-class memory taxonomy (working / episodic / semantic / procedural / meta) | **no code**; only prose |

---

## 7. The decisions this map forces

1. **Pick one memory taxonomy and make the others derive from it.** Five are live and one
   decision would retire four.
2. **Give the `Case` an episode id** and give the episode a case list. The join is one
   column each way, and the audit shows the structures already exist.
3. **Decide the reload path** — the only deletion path in the memory architecture.
4. **Either wire the connectome cluster or remove it from the build.** It is 159 tested
   types with a documented guard defect and no caller.
5. **Do not add a fifth memory store.** The W44 `CognitiveStore` is a table set on the
   existing file, and that is the model to copy.
