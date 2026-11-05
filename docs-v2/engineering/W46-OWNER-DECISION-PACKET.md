# W46.0 — OWNER DECISION PACKET

**The four architectural decisions D1–D4 of the W46 directive, reconciled against what the
repository actually contains.**
**Captured:** 2026-11-04. **Owner approval status is recorded per decision, and it is not the
same for all four.**

---

## 0. The finding that changes the packet

The W46 directive (Part IV §5) presents D1–D4 as *"the proposed target decisions, not
automatic permission to change runtime behaviour"*, and instructs that during W46 they be
marked **`OWNER_APPROVAL_PENDING`**, with the runtime behaviour left unchanged.

**That instruction is superseded by the owner's own answer, which the directive did not
know about.** The timeline:

| when | what |
|---|---|
| 2026-11-04, before W45.2 | the owner is asked the same four questions as a questionnaire, with every option the evidence supported |
| the **first** answer | the heavier options: `TrueMindCycle` as a global conductor, an authenticated write bridge, a non-destructive reload by versioning |
| the owner then **re-answered** | and took the **recommended** option on all four questions |
| W45.2 | implemented all four, proved them with executed checks, and committed them as `a6ded233` (pushed to `origin/develop`) |

The re-answer is the decision; it is recorded in `docs-v2/engineering/W45.2-OWNER-DECISIONS.md`
§0 and in the commit message of `a6ded233`. **So the decisions are not pending — they are
approved and implemented.** What remains is *not* the four decisions themselves but a short
list of **residual gaps** between the implemented contract and the target contract as the W46
directive words it. Those gaps are listed per decision below, and they are the only thing
that needs the owner's attention.

**Nothing was changed in W46.0.** This packet is read-only evidence.

---

## D1 — the canonical cognitive conductor

**The recommended option (identical to the owner's answer):** *Option 1 — name per
application, keep the specialist cycles.*

### The implementation that already exists

`io.matrix.api.CanonicalEntryPoint` declares, in code:

| the application | the canonical entry point | the routes | the cycles it runs |
|---|---|---|---|
| the epistemic and workbench application | `io.matrix.knowledge.LearningWorkbenchResource` | `/v1/workbench/**` | `io.matrix.cognitive.CognitiveKernel` |
| | `io.matrix.api.OpenAIChatResource` | `/v1/chat/completions` | `io.matrix.reasoning.BrainLoopService` |
| the brain runtime | `io.matrix.api.MinimalHttpServer` | `POST /v1/analyze` | `io.matrix.brain.runtime.TrueMindCycle` |

- the five cycles are **kept**; nothing was merged, no sixth cycle was created, no global
  meta-conductor was declared;
- the old ADR that named `TrueMindCycle` the conductor is reconciled by the **superseding**
  document `ADR-W45.2-01-canonical-entry-point.md`, and both historical ADRs carry a
  one-line status annotation — neither was rewritten;
- the naming is **checked**: `CanonicalEntryPointTest` (7 tests) and
  `CanonicalEntryPointChainBTest` (3 tests) — including that **no cycle is the cycle of two
  serving applications**, that every declared class that can be resolved **does** resolve, and
  that the gateway's `ProductionBrainClient` really holds the `TrueMindCycle` the declaration
  names.

### The status

**`APPROVED_AND_IMPLEMENTED`** (commit `a6ded233`).

### The residual gap

**None against the target contract.** One naming difference worth recording: the directive
names *Chain A: Quarkus Workbench + Cognitive Kernel*; the implementation additionally names
the **second** serving path inside chain A — `OpenAIChatResource` → `BrainLoopService` — which
the W44 cycle map did not name at all. The implementation is *more* specific than the target,
and the extra name was added because a five-cycle map was wrong: the code has six.

---

## D2 — the chain B security boundary

**The recommended option (identical to the owner's answer):** *Option 1 — keep the chains
isolated, fail closed.*

### The implementation that already exists

- **no bridge, no implicit write path, no automatic trust, no new network surface** — the
  contract is written down as ten requirements (BRIDGE-1…10) in
  `docs-v2/engineering/W45.2-CHAIN-BOUNDARY.md` §6: explicit, versioned, authenticated,
  provenance-carrying, safety-bounded, fail-closed, audited, replayable, bounded, gated;
- the isolation is **proved**, not asserted: `scripts/w45-chain-isolation-check.sh` resolves
  the compiled call edges from the constant pool over **2908 classes and 76 815 edges** and
  finds **0** classes of `io.matrix.cognitive.**`, **0** of the store itself, **0** of the
  classes that name the epistemic database and **0** of the chain A surface reachable from
  chain B's four entry points (the closure is 356 classes);
- at **runtime** the real chain B server answers **404** for `/v1/cognitive/summary`,
  `/v1/workbench/workspaces` and `POST /v1/cognitive/observe`, serves its own `/v1/status`
  with **200**, and leaves the owner's epistemic database **byte-identical** (the SHA-256 is
  the same before and after);
- the gate **can go red**: `scripts/w45-chain-isolation-selftest.sh` breaks the isolation in a
  private copy of the compiled classes and produces
  `FAIL 78 class(es) of the epistemic store are reachable from chain B` and
  `FAIL 1 route(s) of chain B belong to the chain A surface`.

### The status

**`APPROVED_AND_IMPLEMENTED`** (commit `a6ded233`).

### The residual gaps

Two honest limits, both **documented in the implementation** rather than hidden:

1. **The proof is static.** The constant pool does not resolve reflection, JNI or a native
   image built from another class set. The runtime part probes three representative chain A
   routes; the route census covers the surface statically. A future bridge would have to
   extend the gate (BRIDGE-10 requires exactly that).
2. **Three classes of `io.matrix.knowledge.**` are reachable from chain B** —
   `SimpleKnowledgeBase` and its two nested types. It is a **read-only RAG index** over the
   markdown files of `data/knowledge/`, it holds no connection, writes nothing, reaches no
   persistence stack (asserted by rule G3), and is **not** in the store's closure. The gate
   asserts on the *store*, not on the *package*, and prints the three classes so the exception
   stays visible.

---

## D3 — the destructive workspace reload

**The recommended option (identical to the owner's answer):** *Option 1 — fail closed +
recoverable snapshot.*

### The implementation that already exists

- replacing a non-empty workspace requires the explicit flag `?replace=true`; without it the
  endpoint answers **409** and **nothing is deleted** — the refusal happens *before* the
  transaction that erases anything;
- the snapshot is taken **in the same transaction** as the replacement, and its id is in the
  response (`snapshotId`), so the creation of a recovery point is **verifiable**;
- restoration is an explicit operation (`POST /v1/workbench/workspaces/{id}/restore/{id}`)
  and is **tested**;
- the replacement cannot proceed without a valid recovery point: the snapshot and the
  replacement are one atomic transaction;
- the API returns a clear result (`replaced`, `snapshotId`, and the content counts);
- the knowledge schema moved **v18 → v19** by *adding* one table (`workspace_snapshot`) and
  one index — no existing table changed shape;
- the mechanism is a **logical export** of the workspace's content inside the transaction, not
  a naive copy of a live database file, so the directive's "do not copy a live database file
  naively while WAL is active" is satisfied.

### The status

**`APPROVED_AND_IMPLEMENTED`** (commit `a6ded233`).

### The residual gaps — three, and one of them needs the owner

| # | the gap | why it matters | the proposed resolution |
|---|---|---|---|
| **D3-a** | the target contract names the flag **`replaceExisting: true`**; the implementation ships **`?replace=true`** (a query parameter) | the *intent* is met — the caller must say it explicitly — but the **shape** differs from the wording of the directive. A body field named `replaceExisting` would be a different API contract, and the directive says an API contract may not be changed silently | **owner decision**: keep `?replace=true`, or change the API to a `replaceExisting` body field. Changing it is a Level 2 change (an API change) and would need its own wave and a deprecation path for the existing parameter |
| **D3-b** | the target contract asks for **"a dry-run or preview mode identifies what will be replaced"** — **not implemented** | a caller cannot ask "what would this replace?" without doing it. The refusal message names the flag, and the snapshot is inspectable programmatically (`store.workspaceSnapshots(workspaceId)`), but there is **no preview endpoint** | **owner decision**: add a preview, or accept the refusal message plus the programmatic inspection as the answer. A preview is a new HTTP surface, which the directive restricts |
| **D3-c** | the **evidence of an observation does not survive a workspace load** | the erasure set of a workspace replacement (`revision, prerequisite, representation, evidence, derivation, contradiction, learning_goal, claim, source, concept`) predates the observation tables. A load therefore leaves an `epistemic_case` and its `observation_disposition` **and the W45.1 adjudication history** — but the `evidence` rows the observation wrote **are erased**. The contradiction history survives ✓; the evidence of the observations does not | **owner decision**: taking the observation tables out of the erasure set is the smaller fix, but it changes what "replacing a workspace" *means*, and that is the owner's call, not the agent's |

A fourth, smaller item: the **legacy in-process door** `KnowledgeWorkbench.loadBundledPackage(String)`
still replaces without the flag, because the existing `KnowledgeWorkbenchTest.reloadingIsIdempotent`
loads the package three times into one workspace and the decision requires a refusal on the
second. The HTTP surface — the door the owner named — fails closed ✓, and the legacy door is
**no longer a silent erase**: it leaves a recoverable snapshot. Deprecating the one-argument
method and moving its callers is a mechanical change that needs no decision.

---

## D4 — the recovery topology

**The recommended option (identical to the owner's answer):** *Option 1 — one writer process
per database file, enforced and documented.*

### The implementation that already exists

`io.matrix.cognitive.WriterLock`:

- a **lock file** next to the database (`<database>.writer.lock`) holding the owner's pid,
  the **start instant** of the process and an instance token, **plus the operating system's
  lock** on that file (`FileChannel.tryLock`);
- the lock is taken **before** the database is opened, so a second writer is refused before
  it can touch the data;
- the refusal is a named type (`CognitiveWriterConflictException`) whose message names the
  file, the owner and the contract;
- a **dead** owner is told from a **live** one with the same primitives `OwnershipToken`
  already carries — a pid that cannot exist is dead, a pid that started at a different instant
  is dead (a recycled pid is not the owner), and a liveness that cannot be disproved is
  **live** (the conservative rule of W45.1);
- a lock left behind by a **killed** process does not block the next one — the operating
  system releases its lock when the process dies, whatever it was doing;
- the lock belongs to the **PROCESS**, not to a store instance: the cognitive store and the
  epistemic store open the **same** file, so a second store of one process joins the lock it
  already holds, and what is refused is a second **process**;
- the epistemic store takes the same lock, so the contract covers the **file**;
- the supported topology is documented, including the honest limits (a per-host guarantee
  unless the shared filesystem coordinates cross-host locks; a container with its own pid
  namespace is protected by the shared inode's operating-system lock).

### The status

**`APPROVED_AND_IMPLEMENTED`** (commit `a6ded233`).

### The residual gap — one, and it is a real defect

**D4-a — path aliases and symlinks are not resolved.**
`WriterLock.java:145` and `:159` compute the lock path with
`databaseFile.toAbsolutePath().normalize()`. `normalize()` removes `.` and `..` segments; it
does **not** resolve symbolic links. Two writers that open the same file through **different
paths** — a symlink and its target, or two relative spellings that normalisation does not
collapse — compute **different lock files**, and the guard lets both in. The directive names
this requirement explicitly: *"account for path aliases and symlinks"*.

The fix is small and local: resolve the path to its real one (`Path.toRealPath()`) before the
lock file is derived, and fall back to the normalised path when the file does not exist yet
(the first writer of a new database). It is a **defect in the shipped guard**, not a design
choice, and it is the one item in this packet that is a code change rather than a decision.

---

## The summary the owner needs

| | D1 | D2 | D3 | D4 |
|---|---|---|---|---|
| the owner's answer | option 1 | option 1 | option 1 | option 1 |
| the recommended option | option 1 | option 1 | option 1 | option 1 |
| implemented | **yes** | **yes** | **yes** | **yes** |
| proved by an executed check | **yes** (10 tests) | **yes** (16 checks + a negative control) | **yes** (23 tests) | **yes** (8 tests, one with a second JVM) |
| the residual gap | none | two documented limits | **three** (one needs a decision) | **one** (a real defect) |

**The four decisions themselves need nothing from the owner.** What needs the owner is:
**D3-a** (the flag's shape), **D3-b** (whether a preview mode is wanted), **D3-c** (what a
workspace replacement is allowed to erase), and — separately, and not as a decision — the
repair of **D4-a** (the symlink alias), which is a defect fix inside an already-approved
contract.
