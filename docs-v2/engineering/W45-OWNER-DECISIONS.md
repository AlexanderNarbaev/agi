# W45.0 — OWNER DECISIONS

**Status:** prepared for the owner. **Nothing here is enacted.**
**Prepared:** 2026-11-03, in the W45.0 integrity wave.
**Evidence base:** the RECON-W44.0 bytecode archaeology (`W44-CLASS-INVENTORY.csv`,
`W44-CALL-GRAPH.csv`) and the source of the modules named below.

The RECON-W44.0 report's §2.20 identified three questions that are the owner's to answer and
not an implementation detail. This document states what the compiled code actually does for
each of them, what the options cost, and what the audit recommends — and it does **not**
implement any of them. Each decision needs an explicit approval.

---

## Decision 1 — the canonical conductor

### What the bytecode says

There is no single canonical cycle. Five cycles coexist and none delegates to the others. The
counts below are the production callers recorded in `W44-CLASS-INVENTORY.csv`, which the
audit derived from `javap -p -c` — the constant pool, not the text.

| cycle | fully qualified name | module | lifecycle | production callers | entry point |
|---|---|---|---|---|---|
| `BrainLoopService` | `io.matrix.consciousness.BrainLoopService` | matrix-core | served | **25** | — |
| `TrueMindCycle` | `io.matrix.brain.runtime.TrueMindCycle` | matrix-brain-runtime | served | 3 | — |
| `BirBrainCycle` | `io.matrix.brain.BirBrainCycle` | matrix-core | served | 3 | **CLI** |
| `MindCycle` | `io.matrix.brain.runtime.MindCycle` | matrix-brain-runtime | served | 1 | — |
| `ConsciousnessLoop` | `io.matrix.reasoning.ConsciousnessLoop` | matrix-core | served | 1 | — |
| `ConsciousBrain` | `io.matrix.neuron.ConsciousBrain` | matrix-core | **inactive-but-tested** | **0** | — |
| `BrainLoopServiceV2` | `io.matrix.consciousness.BrainLoopServiceV2` | matrix-core | inactive-but-tested | 0 | — |
| `BrainLoopTopology` | `io.matrix.consciousness.BrainLoopTopology` | matrix-core | inactive-but-tested | 0 | — |

The 25 callers of `BrainLoopService` are **not** 25 cognitive cycles. They are the calls of
the class that owns it, `io.matrix.consciousness`, which is 297 types of which **11 are
served**; the rest — the metrics cluster, the profile builders, `CrossLevelPhi`,
`BrainLoopServiceV2`, `BrainLoopTopology` — hang off `ConsciousBrain`, a class with **zero**
production callers and 19 test references.

### What that means for the earlier ADR

The ADR that named `TrueMindCycle` the canonical conductor was written before the bytecode was
examined. It is not wrong about `TrueMindCycle` — it is served, and it is the cycle the brain
runtime actually runs — but it was written as if one cycle governed the whole system, and the
compiled code says otherwise: the epistemic application and the brain runtime are **two
non-intersecting serving chains**, and a third set of cycles lives inside `matrix-core` beside
the workbench.

### The distinction the decision has to draw

| kind | what it is | in the tree |
|---|---|---|
| the user-facing serving path of chain A | the Quarkus application: `/v1/workbench/**` and `/v1/cognitive/**` | `io.matrix.MatrixApplication` → `KnowledgeWorkbench` |
| the user-facing serving path of chain B | the hand-written HTTP server → the brain runtime | `io.matrix.api.MinimalHttpServer` → `ProductionBrainClient` → `TrueMindCycle` |
| internal specialist loops | the cycles that do one job inside a subsystem | `io.matrix.reasoning.ConsciousnessLoop`, `io.matrix.brain.BirBrainCycle` |
| the epistemic/cognitive kernel | the W44 learning loop | `io.matrix.cognitive.CognitiveKernel` |
| research and simulation | the loops that are measured rather than served | `io.matrix.neuron.ConsciousBrain` and its cluster |

### Options

| option | what it buys | what it costs | risk |
|---|---|---|---|
| **A. Keep the five cycles; name the canonical ENTRY POINT of each serving application** | the architecture becomes truthful without moving any code | a document and a decision, no migration | **low** |
| B. Merge the cycles into one conductor | one story, one place to look | a large refactor of 297 types, of which 11 are served | **high** — it would break every production caller of `BrainLoopService` |
| C. Declare `TrueMindCycle` the global conductor | the ADR stays as written | the ADR's claim would be false for chain A, and the kernel would have to become a client of the brain runtime | **high** — it is the merge, in the other direction |
| D. Declare `BrainLoopService` the global conductor | the most-connected class wins | it is an island-adjacent class in `io.matrix.consciousness`, its own metrics cluster is unreachable, and its 25 "callers" are mostly internal | **high** |

### Recommendation

**Option A.** Retain the specialist loops where they have distinct responsibilities, define
the canonical entry point of each *actual serving application* — chain A's is the Quarkus
workbench and the cognitive kernel; chain B's is `MinimalHttpServer` — and **do not merge
until a concrete end-to-end use case and a safety contract require it**. The reason is
measurable rather than aesthetic: `io.matrix.consciousness` holds 297 types and 11 served
ones; merging on the strength of a caller count would drag 286 unreachable types into the
serving path.

**The decision the owner still has to make:** whether chain A and chain B are ever to be one
system — and if so, which of them is the edge. The W45 wave does not touch this and does not
need to.

### Risks of doing nothing

The ADR and the code disagree, and a disagreement that is documented in only one place is
resolved by whoever reads it last. The concrete cost is that a future wave may "fix" the
kernel by making it call `TrueMindCycle`, which would put the epistemic store behind an
HTTP hop it does not need. **Recommendation: amend the ADR to state the two chains and their
entry points, and keep the five cycles.** That is a documentation change plus one decision.

---

## Decision 2 — the security boundary for the second serving chain

### What the bytecode says

| component | fully qualified name | lifecycle | production callers |
|---|---|---|---|
| the hand-written server | `io.matrix.api.MinimalHttpServer` | **CLI entry point** (see the correction below) | — |
| its brain client | `io.matrix.api.brain.ProductionBrainClient` | island | 1 (`MinimalHttpServer`) |
| the cycle it runs | `io.matrix.brain.runtime.TrueMindCycle` | served | 3 |
| the stub cycle | `io.matrix.api.brain.StubBrainCycle` | served | 3 |
| the JAX-RS resource | `io.matrix.api.resource.AnalyzeResource` | **served, REST** | — |

`StubBrainCycle` is called by **three** production callers: `MinimalHttpServer`,
`AnalyzeResource` and `ExplainResource`. The gateway's Quarkus module applies no Quarkus
plugin, so the JAX-RS facade is not mounted by the Quarkus build — but `AnalyzeResource` is
a REST resource in the compiled artifact, and the question "does the running gateway serve
the stub or the brain?" cannot be answered from the source alone. It can be answered at
runtime, and that is the experiment to run before the boundary is fixed.

**Correction to the archaeology:** RECON-W44.0's class inventory recorded
`MinimalHttpServer` as `inactive-but-tested` with no entry point. It has a
`public static void main`. The audit's scanner attributes a `main` to the last type declared
in the file rather than to the file's top-level type, so **11 of the 79 CLI entry points were
missed** — the full list and the reproduction command are in `W45-CAPABILITY-DELTA.md` §2.
The architecture narrative in the archaeology was right; the per-class label was wrong.

### The question

What may cross between the brain runtime (chain B) and the epistemic/cognitive application
(chain A)?

### What the audit recommends

**Preserve isolation and fail closed. There is no implicit trust and no automatic write path
across the boundary today, and none should be introduced.** Concretely:

| question | recommended answer | why |
|---|---|---|
| local-only vs network-exposed | **local-only by default**; the port is configurable and the server binds what it is told to | the brain runtime reads and writes the world's state; exposing it is a capability decision |
| authentication | **required before any network exposure**; none exists today | an unauthenticated endpoint that can drive a cognitive cycle is a remote-control surface |
| allowed operations | read-only inspection of the brain's state; no write into the epistemic store | an observation whose provenance is unknown cannot be promoted |
| may the brain runtime write to the epistemic store? | **no** | the epistemic store's promotion policy rests on source independence; an unprovenanced write would break it |
| how do observations acquire provenance? | through the explicit `Source` and `Evidence` records, with a source id and a content hash — never as a bare case | the W44 promotion rule counts distinct content hashes |
| may untrusted output change a claim, a confidence, a goal or a safety policy? | **no** | `DriveSignal.Effect` already restricts the regulator to two effects, none of which is truth or permission; a bridge must not widen that |
| failure and timeout behaviour | fail closed: an unreachable brain runtime is a refusal, not an empty answer | a fallback that invents a plausible answer is worse than a refusal |
| audit logging and replay | every cycle is already an append-only episode with a trace; a bridge must write into the same record | the trace is the only place the system's reasoning is inspectable |

**The shape of any future bridge:** an explicit, versioned, authenticated contract with
provenance and an independent safety boundary — not a shared database, not a shared in-process
object, and not a "temporary" direct call.

### Risks of doing nothing

The gap is not that the boundary is crossed today — it is that it is **undefined**. An
undefined boundary is crossed by whoever needs a feature. The concrete risk: a future wave
adds "let the brain runtime tell the knowledge store what it learned", and the natural
implementation is a direct call into `KnowledgeWorkbench`, which would bypass the
independence check that the promotion policy depends on. **The cost of the decision now is
one document; the cost of the silence is a promotion policy that can be bypassed.**

---

## Decision 3 — the destructive workspace reload

### What the code does

`POST /v1/workbench/workspaces/{id}/load` deletes the workspace's prior records before it
loads the new ones. The deletion is in `KnowledgeStore.deleteWorkspace`
(`matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java:700-726`), and the
archaeology recorded that it removes `contradiction`, `evidence`, `derivation` and `revision`.

| table | what it holds | lost by the reload |
|---|---|---|
| `contradiction` | the recorded conflicts between claims | **yes** |
| `evidence` | the evidence behind every claim | **yes** |
| `derivation`, `derivation_premise` | how a claim was derived | **yes** |
| `revision` | the claim's revision history | **yes** |
| `epistemic_case` | the observations | **yes** |
| `source` | the sources | **yes** |
| `claim` | the claims | **yes** |
| `observation_disposition` (W45) | what each observation may be believed for | **yes** |

The endpoint's own name says "load", and the operation it performs is "replace". The history
of what the workspace used to believe — including the disagreements it had not resolved — is
destroyed by a request whose name does not say so.

### Options

| option | what it does | compatibility | risk |
|---|---|---|---|
| **A. Fail closed on a non-empty workspace unless the caller explicitly asks for destruction** | the default becomes a refusal with the count of the records that would be lost | a new optional request field; the existing call still works | **low** |
| B. Take a recoverable snapshot before replacing | the old records stay, in a versioned copy | new storage, new retention rules | **medium** |
| C. Non-destructive workspace versioning | every load creates a new revision; the old one is marked, never deleted | the largest change; every read has to learn about versions | **medium-high** |
| D. Leave it as it is | — | — | **the contradiction history is silently erased** |

### Recommendation

**Option A now, Option B as the follow-up.** The exact API change:

```jsonc
POST /v1/workbench/workspaces/{id}/load
{
  "document": "...",            // as today
  "replaceExisting": false      // NEW, default false
}
```

- `replaceExisting` absent or `false`, and the workspace is **not** empty → **409 Conflict**,
  with a body that names how many claims, evidence records, contradictions and revisions
  would be lost, and the id of the newest record so a reader can see what is at stake.
- `replaceExisting: true` → the current behaviour, unchanged, and the refusal is explicit
  rather than implied by the absence of a flag.
- An **empty** workspace loads without any flag — the common case is untouched.

Before any replacement, the workspace's snapshot is taken (Option B): the cases, the
evidence, the contradictions and the revisions are copied into a versioned snapshot row, so
the destructive path remains recoverable even when it is explicitly requested.

### Migration and compatibility

- The existing clients keep working: a first load into an empty workspace needs no flag.
- A client that loads into a non-empty workspace starts receiving a 409 — **this is a
  behaviour change and it is the point**. The change is announced in the API's own error
  body, and the transition is: add `replaceExisting` to the client, read the 409, decide.
- No stored data is migrated: the change is in the endpoint's behaviour, not in the schema.

### Tests to write when the owner approves

1. A load into a non-empty workspace without the flag is refused, and nothing is deleted.
2. A load into a non-empty workspace with `replaceExisting: true` replaces, and the snapshot
   holds the old contradiction history.
3. A load into an empty workspace works without any flag.
4. The 409 body names the counts of what would be lost.
5. A load with the flag refused leaves the workspace byte-for-byte as it was.

### Risks of doing nothing

The system can be made to forget, on request, that it ever disagreed with itself — and the
W45 wave has just made the disagreement durable, per-observation, with its provenance. The
reload path is the one place that can delete it. **This is the highest-severity finding of the
archaeology that the W45 wave could not fix without the owner's approval, and it is why the
recommendation is to fail closed rather than to keep the current behaviour.**

---

## What the audit did NOT do

- It did not merge the chains, and it did not declare a canonical conductor.
- It did not change the reload endpoint's behaviour, expose the second chain, or write a
  bridge between them.
- It did not touch `AGENTS.md`, `.gitignore`, `opencode.json`, `.codex/`, `.veai/` or
  `.opencode/`, and it did not commit or push anything.

**Each of the three decisions needs an explicit approval before it is implemented.**
