# W45.0 — CAPABILITY DELTA

**Status:** what the W45 wave actually changed, separated into what is genuinely implemented,
tested and reachable, and what is still only experimental, in-sample or unverified.
**Prepared:** 2026-11-03.

The rule of this document: **a capability is listed as implemented only when it is reachable
from a production path, exercised by an executed test, and semantically what its name
says.** Everything else is labelled.

---

## 1. The capabilities that are now genuinely implemented

| capability | what it does | where | how it is proven | reachable from |
|---|---|---|---|---|
| **A contradiction survives a restart** | an observation that contradicts a SETTLED belief is recorded, held out of the model, and stays held across cycles and restarts | `ObservationDisposition`, `observation_disposition` (schema v17), `MicroWorldSession.observe` | `CognitiveContradictionDurabilityTest` — 13 tests, all executed | `POST /v1/cognitive/observe` → `CognitiveKernel` → `MicroWorldCodec.readCases` |
| **A held observation is never deleted** | the case, its source, its evidence and the link survive; the disposition is an additional, adjudicable record | `KnowledgeStore.recordObservationAtomically`, `adjudicateObservation` | `CognitiveContradictionDurabilityTest` — "a review can adjudicate a held observation without deleting anything" | the epistemic store |
| **The epistemic write is atomic** | the source, the evidence, the case, the link and the disposition are written in ONE transaction | `KnowledgeStore.recordObservationAtomically` | the same test + `CognitiveConcurrencyTest` — "the epistemic records of an observation are linked, never half-linked" | the epistemic store |
| **The reference method is a reference for the model in production** | it reads the model-eligible observations, so agreement and disagreement are both meaningful | `MicroWorldReferenceMethod.execute` | `CognitiveContradictionDurabilityTest` — "the reference method reads the model-eligible observations" | the algorithm selector |
| **A repeated request cannot overwrite history** | no `INSERT OR REPLACE` remains in the cognitive store; a same-id/different-content write is an explicit conflict | `CognitiveStore` (every method), `CognitiveConflictException` | `CognitiveStoreIdentityTest`, `CognitiveServiceIdentityTest` | `POST /v1/cognitive/observe` |
| **Two identical payloads are two episodes** | the signal id comes from an injected id source; the arrival number lives in the database | `EpisodeIdAllocator`, `IdSource`, `cognitive_sequence` | `EpisodeIdAllocatorTest`, `CognitiveServiceIdentityTest` — "two requests with the same payload are two requests, and both survive" | the cognitive service |
| **A repeated delivery with the same key does no work** | the original outcome is replayed; a different payload under the same key is a 409 | `IdempotencyRegistry`, `cognitive_idempotency` | `IdempotencyRegistryTest`, `CognitiveServiceIdentityTest` — "one key with two payloads is a conflict, and the first outcome is kept" | `POST /v1/cognitive/observe` |
| **The cycle budget is enforced** | one monotonic deadline, checked at ten phase boundaries; a timeout is an explicit, persisted terminal state | `CycleDeadline`, `CycleTimeoutException`, `CognitiveKernel.process` | `CognitiveCycleBudgetTest` — 5 tests | every cognitive cycle |
| **A timeout does not lie about the world** | the notes record what had already happened, and the side effects are not rolled back | `CognitiveKernel.process` (the catch block) | `CognitiveCycleBudgetTest` — "work that had already happened is not rolled back" | every cognitive cycle |
| **The drives survive a restart** | the regulatory state and the provenance of every change are persisted together and restored at application start | `cognitive_regulatory_state`, `cognitive_regulatory_event` (schema v3), `CognitiveKernel.restoreRegulatoryState` | `CognitiveRegulatoryPersistenceTest` — "the regulatory state is persisted and restored after a real restart" | `CognitiveService.build` |
| **A drive cannot move twice for one event** | a repeated regulatory event id is refused and nothing is written | `CognitiveStore.recordRegulatoryState` | `CognitiveRegulatoryPersistenceTest` — "a repeated event is refused" | every cognitive cycle |
| **The concurrency contract holds** | one cycle at a time per kernel; every store write is serialised; the sequence never repeats; the regulatory version advances once per write | `synchronized CognitiveKernel.process`, `synchronized` store methods | `CognitiveConcurrencyTest` — 5 tests | every cognitive cycle |

---

## 2. The corrections to the archaeology

The W45 wave re-checked three of the archaeology's claims against the code and the evidence.
**None of the W44 artefacts was modified** (W45 decision D11); the corrections live here.

### 2.1 Eleven CLI entry points were missed by the class inventory

**The claim.** RECON-W44.0's §1.2 counts 211 entry points: 96 CDI, 79 CLI, 36 REST.

**What is wrong.** The audit's scanner attributes a `main` method to the **last type declared
in the file** rather than to the file's top-level type, because it tracks the brace depth only
on type-declaration lines. A `main` in a file that declares a nested type after its own
declaration is therefore attributed to the nested type — or to nothing.

**The consequence.** Eleven classes that ARE serving entry points were labelled dormant,
inactive-but-tested or island. The most consequential is
`io.matrix.api.MinimalHttpServer`, which the inventory records as `inactive-but-tested` with
no entry point, while the source has a `public static void main` at line 1674. **Chain B's
HTTP server is a serving entry point, and the archaeology's per-class label said it was
not.**

**The reproduction command** (read-only, no artefact is regenerated):

```bash
python3 - <<'EOF'
import csv, re, pathlib
inv = {r['fqn']: r for r in csv.DictReader(open('docs-v2/engineering/W44-CLASS-INVENTORY.csv'))}
main_re = re.compile(r'^\s*public\s+static\s+void\s+main\s*\(', re.M)
for p in pathlib.Path('.').glob('matrix-*/src/main/java/**/*.java'):
    text = p.read_text(errors='replace')
    if not main_re.search(text):
        continue
    m = re.search(r'public\s+(?:final\s+|abstract\s+|sealed\s+)*(?:class|record|enum|interface)\s+(\w+)', text)
    pm = re.search(r'package\s+([\w.]+);', text)
    if not m or not pm:
        continue
    fqn = f"{pm.group(1)}.{m.group(1)}"
    r = inv.get(fqn)
    if r and r.get('cli_main') != 'yes':
        print(f"{fqn:64} {r['module']:20} {r['lifecycle']}")
EOF
```

**The eleven.**

| entry point | module | the label the inventory gives it |
|---|---|---|
| `io.matrix.cli.ConversationTimeline` | matrix-core | dormant |
| `io.matrix.knowledge.SimpleKnowledgeBase` | matrix-core | served (already) |
| `io.matrix.brain.BrainTelemetry` | matrix-core | dormant |
| `io.matrix.federation.liquid.benchmark.BenchmarkRunner` | matrix-core | inactive-but-tested |
| `io.matrix.federation.liquid.simulation.SleepConsolidationStudy` | matrix-core | inactive-but-tested |
| `io.matrix.federation.liquid.simulation.FederationSimulation` | matrix-core | inactive-but-tested |
| `io.matrix.api.MinimalHttpServer` | matrix-api-gateway | inactive-but-tested |
| `io.matrix.api.llm_quarantine.BrainHttpServer` | matrix-api-gateway | served (already) |
| `io.matrix.api.llm_quarantine.BrainProduction` | matrix-api-gateway | island |
| `io.matrix.brain.runtime.BenchmarkRunner` | matrix-brain-runtime | inactive-but-tested |
| `io.matrix.brain.runtime.KnowledgeForgeIngest` | matrix-brain-runtime | dormant |

**What it changes.** Nine of the eleven move from dormant/inactive/island into the served
population — the census would read **869 served, 618 island, 476 inactive-but-tested, 444
dormant** instead of 860 / 619 / 482 / 452. **No architecture conclusion in the archaeology
changes** — the narrative already described chain B as a serving chain — but the per-class
labels were wrong, and the REST census was checked the same way and holds: 43 files carry
`@Path` and only two are not marked as REST resources, both explained (one is a CDI bean, one
is a nested resource).

**The audit script was not fixed**, because the W45 directive forbids regenerating the
archaeology's artefacts. The defect is recorded here and in `W45-TEST-EVIDENCE-RECONCILIATION.md`.

### 2.2 One of the 24 failures is not a native-image failure

**The claim.** RECON-W44.0's §1.1bis: "All 24 failures are in nine classes, and every one of
them needs a GraalVM native image".

**What is wrong.** Eight of the nine classes need the native image.
`io.matrix.consciousness.Exp228ComplianceTest` does not: it scans `matrix-core/src/main/java`
and fails on a wall-clock compliance rule (`random=63, wallClock=47, 21 unclaimed wall-clock
site`, `KnowledgeImporter.java:51`).

**The corrected split.** **8 native-image classes / 23 failures, plus 1 ordinary JVM failure.**
The evidence is in `W45-TEST-EVIDENCE-RECONCILIATION.md` §5.

### 2.3 The W44 benchmark's adversarial case needed a settled assignment

**The claim.** `W44-BENCHMARK.md` §4.4: "An adversarial observation (predict `false`, observe
`true`) produces one `PredictionError`... the survivors stay `[h-0001, h-1001]`".

**What is wrong.** The test behind that paragraph predicted an assignment where the two
survivors **disagree**, so the prediction was explicitly unsettled. Under the W45 rule — a
contradiction is a disagreement with a BELIEF — that observation is learning, not a conflict.
The benchmark's case is still a conflict, at an assignment where the survivors agree.

**What did not change.** The benchmark's survivor table (§4.1: 16 → 8 → 4 → 2 → 1) and its
information-gain table (§4.2) were produced by feeding observations **directly** to a session,
with no prediction in between, so they are unaffected. The W44 micro-world can still learn
the conjunction.

---

## 3. What is still experimental, in-sample or unverified

| thing | what it really is | why it is not more |
|---|---|---|
| the **fast path** (`MicroWorldSettledModelMethod`) | an **algebraic shortcut** of the reference over the same model-eligible data — the answer read off the single surviving hypothesis | it is **no longer a promoted procedure**, because the lamp world cannot produce independent evidence. Nothing calls it validated. Its correctness rests on the two computations being the same function of the same data, which the W45 reference-data rule now guarantees — **not** on a statistical validation. |
| the **promotion path** | implemented, policy-checked and reachable, but it **cannot fire** in the lamp world | the validation set is structurally empty (W45 decision D5). It will fire in a domain where a model can settle before its input domain is exhausted. |
| the **procedure invalidation/demotion** | `ProcedureStore.invalidate` and `Procedure.invalidatedAt` exist; the cognitive store's procedure version check refuses a conflicting redefinition | **nothing is promoted, so there is nothing to demote in the micro-world.** The mechanism is implemented and unit-tested, but the end-to-end demotion path has no live instance. |
| the **250 ms cycle budget** | a **configured convention**, enforced at the phase boundaries | it is **not calibrated**. The wave did not measure the cycle's latency distribution, and the number is not a promise. The enforcement boundary is documented in `W45-INTEGRITY-DESIGN.md` §6. |
| the **regulatory state** | durable, versioned, with the provenance of every change | the **CostLedger remains process-local**: a timing measurement without the environment it was measured in is not interpretable. **The selector never learns cost statistics across a restart, and no document may claim that it does.** |
| the **epistemic promotion policy** (the W43/W44 `ClaimValidator`) | the one mechanism in the repository with an independence test and a policy gate | unchanged by W45, and **unaffected** by it: the W45 disposition governs what may constrain the MODEL, while the promotion policy governs what may make a CLAIM more established. They are different questions and they do not interfere. |
| the **micro-world itself** | a two-variable truth table with one hidden rule | a designed world that declares itself to be a designed world. It is a **testbed**, not a world model, and the W45 wave did not change that. |

---

## 4. What the wave did NOT change

- **No new subsystem, no new module, no new application.** The wave touched
  `io.matrix.cognitive`, `io.matrix.cognitive.microworld`, `io.matrix.cognitive.algorithms`
  and two files of `io.matrix.knowledge`.
- **No merge of the two serving chains, and no new conductor.** The three owner decisions are
  documented in `W45-OWNER-DECISIONS.md` and **not enacted**.
- **No revival** of the connectome, the replay/sleep graph, the biochemical island or any
  other inactive subsystem. Their negative findings are preserved.
- **No claim of semantic understanding, general prediction, GPU execution or consciousness.**
  The archaeology's limits stand: `TextEncoder` is a SHA-256 digest, the semantic ONNX path
  needs a model that is not in the repository, the only prediction model in the tree has a
  fixed identity weight matrix, and the GPU provider cannot be discovered.
- **No commit and no push.** The work is local and reviewable, as the W45 directive requires.
