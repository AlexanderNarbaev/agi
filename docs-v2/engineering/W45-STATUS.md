# W45.0 — STATUS

**Wave:** W45.0 — Cognitive Kernel Integrity, Bounded Execution and Recovery.
**Prepared:** 2026-11-03.
**State:** implemented and verified locally. **Nothing is committed and nothing is pushed.**

---

## 1. The initial state

| | |
|---|---|
| repository | `AlexanderNarbaev/agi` |
| branch | `develop` — **not switched** |
| HEAD at the start of the wave | `81f08478a5a2386196475ca7fd6497967e237cba` — **exactly the expected reference point**, so no reconciliation was needed |
| the W44 wave's commits | `9eb0d4b1`, `471bfdd9`, `81f08478` — present and untouched |
| the working tree at the start | 6 modified owner files (`.gitignore`, `.opencode/todo.md`, `.opencode/w37-plan.md`, `AGENTS.md`, `opencode.json`, and the deletion of `opencode.json.bak`), 5 untracked owner paths (`.codex/`, `.opencode/goals/`, `.opencode/opencode-swarm.json`, `.opencode/tmp/`, `.veai/`), and 21 untracked RECON-W44.0 artefacts (17 documents + 4 audit scripts) |
| the W44 artefacts | **preserved byte-for-byte** — not overwritten, not regenerated in place, not staged, not moved, not deleted |
| the four audit scripts | **preserved** and not re-run over the changed sources |

**The archaeology's baseline** was read before any source change: the twenty answers, the
capability registry, the eight family maps, the class inventory, the call graph, the test
map, the document map, the claim index, the file census and the subsystem evidence.

---

## 2. What was completed

### Priority A — contradiction durability, restoration semantics, reference data, atomic writes

- **A new durable concept**: `ObservationDisposition` with four states (`ELIGIBLE`,
  `UNRESOLVED`, `ACCEPTED`, `REJECTED`), persisted in a **new table `observation_disposition`**
  (knowledge schema **v16 → v17**, additive, backfilled).
- **The restoration now honours the durable disposition** instead of re-deciding from a
  prediction history it does not have. That was the defect: a contradiction survived a
  restart as a record and was lost as a decision.
- **The reference method reads the model-eligible observations**, so it is a genuine reference
  for the model in production rather than a second implementation over a different dataset.
- **The epistemic write is one transaction**: the source, the evidence, the case, the
  evidence-to-case link and the disposition are written together.
- **One authoritative rule** (`ObservationDisposition.constrainsModel()`) is asked by the
  codec, the session, the reference method and the promotion check.
- **A second, necessary rule**: a contradiction is a disagreement with a **belief** — the
  session holds an observation only when the prediction it contradicts was unanimous. The W44
  rule, combined with the fixed restoration, was a learning deadlock (see the decision log,
  D3).

### Priority B — episode identity, idempotency, immutable history

- `String.hashCode()` is **no longer an identity**. The signal id comes from an injectable
  id source; the arrival number lives in the database (`cognitive_sequence`), so a restart
  cannot reset it and two concurrent callers cannot be handed the same number.
- **Four identities** are now distinct: request id, signal id, episode id, event id.
- **No `INSERT OR REPLACE` remains** in the cognitive store. The events, predictions, errors
  and tasks are insert-only; the episode is mutable in one direction (open, then close); the
  procedure is a version.
- **An explicit idempotency policy**: no key → a fresh episode every time; a key → the
  original outcome is replayed with no work; the same key with a different payload → a 409.
  The mapping is durable and survives a restart.
- The cognitive store's schema is **v1 → v2**.

### Priority C — the cycle budget is real

- **One monotonic deadline** at cycle entry, checked at ten phase boundaries, measured
  against an injected monotonic clock that a test can control.
- **A timeout is an explicit terminal outcome**: the status is `RESOURCE_LIMITED`, the trace
  carries a `TIMEOUT` step, the episode is closed, and the notes record whether an
  experiment ran, whether an observation was committed, whether the procedure state changed
  and that the side effects already happened are not rolled back.
- **The operations that cannot be interrupted are classified as such** and the design
  document states that there is no hard wall-clock bound on any single operation.

### Priority D — in-sample agreement is no longer called validation

- **Nothing is promoted without independent evidence**, and in the lamp world there is none:
  the input domain is four assignments, a model that has settled has seen all four, so the
  set of observations the model was not built from is empty.
- The fast path is **reclassified as an in-sample derived shortcut** of the reference over the
  same model-eligible data. Its capability text, its accuracy profile and its verification
  strategy now say exactly that.
- `Procedure` gained a `validationKind` (`IN_SAMPLE` / `INDEPENDENT`) and a `validated()`
  predicate; the cognitive store persists the kind.
- Every "held-out" claim about the fast path is gone from the code.

### Priority E — the regulatory state is durable

- **The drives are persisted** (`cognitive_regulatory_state`, `cognitive_regulatory_event`;
  cognitive store schema **v2 → v3**) with the **provenance of every change** in the same
  transaction, and **restored when the application starts**.
- **A repeated event is refused**: a drive must not move twice for one event.
- The version is allocated **inside** the transaction, so two concurrent writers cannot claim
  the same one.
- The `CostLedger` stays **process-local**, and the test report says so explicitly: the
  selector does **not** learn cost statistics across a restart.

### Priority F — concurrency

- **One cognitive cycle runs at a time per kernel** (`synchronized process`). Every store
  write is serialised on the store's own monitor, and the store holds one JDBC connection
  that is only ever used inside a synchronised method.
- **Five concurrency tests** cover: eight concurrent observations producing eight independent
  episodes; reads during writes; the sequence never repeating; the regulatory version
  advancing once per write; and the epistemic records never being half-linked.
- **The limits are documented**: the system does not scale, and the guarantee is "one cycle
  at a time", not "the system scales".

### The §1.1 test-evidence reconciliation

- The three denominators (1320/9816, 8817, 1179) are **reconciled against the retained
  evidence**, and it is established that they are **three different measures of three
  different populations**, not three versions of one number.
- **The archaeology's claim that all 24 failures need a GraalVM native image is corrected**:
  eight classes need it (23 failures); `io.matrix.consciousness.Exp228ComplianceTest` is an
  ordinary JVM failure of a wall-clock compliance rule.
- The full accounting is in `W45-TEST-EVIDENCE-RECONCILIATION.md`.

### The owner decisions

- **Investigated, recommended, and NOT enacted.** The evidence from the compiled call graph,
  the alternatives, the security and operational consequences, the migration implications and
  the risks of doing nothing are in `W45-OWNER-DECISIONS.md`.

---

## 3. The exact tests and their results

| scope | command | classes | executions | failures | skipped |
|---|---|---|---|---|---|
| the cognitive kernel | `./gradlew :matrix-core:test --tests "io.matrix.cognitive.*" --rerun-tasks` | 38 | **332** | **0** | 0 |
| the W43 regression | `./gradlew :matrix-core:test --tests "io.matrix.knowledge.*" --tests "io.matrix.memory.*" --tests "io.matrix.brain.*" --tests "io.matrix.api.*" --tests "io.matrix.rag.*" --rerun-tasks` | 177 | **1518** | **0** | 0 |
| the brain runtime | `./gradlew :matrix-brain-runtime:test --rerun-tasks` | 119 | **914** | **0** | 2 |
| the API gateway | `./gradlew :matrix-api-gateway:test --rerun-tasks` | 42 | **237** | **0** | 0 |

**The full matrix-core suite was NOT run** — the directive forbids it, and the W44 evidence
of that run is retained. **No native-image test was executed**: the GraalVM tooling is not
on this host, and the eight native-image classes are **not counted as passing**.

The exact filters, the environment, the per-class breakdown and the comparison with the W44
evidence are in `W45-TEST-REPORT.md`.

---

## 4. The unresolved work

| what | why it is unresolved |
|---|---|
| **the destructive workspace reload** | it is one of the three owner decisions. The recommendation, the exact API change, the compatibility plan and the tests are prepared in `W45-OWNER-DECISIONS.md` §3; the behaviour is unchanged until the owner approves. |
| **the canonical conductor** | an owner decision. The evidence, the four options and the recommendation are in `W45-OWNER-DECISIONS.md` §1; nothing was merged and no cycle was declared canonical. |
| **the security boundary for chain B** | an owner decision. The recommendation is in `W45-OWNER-DECISIONS.md` §2; no bridge, no authentication and no network exposure was introduced. |
| **the demotion of a promoted procedure** | the mechanism exists and is unit-tested, but **nothing is promoted** in the lamp world, so the end-to-end path has no live instance. It will fire in a domain where a model can settle before its input domain is exhausted. |
| **the calibration of the 250 ms budget** | the budget is enforced, but the figure remains a **configured convention**. The wave did not measure the cycle's latency distribution, and no latency claim is made. |
| **the legacy v16 backfill's one residual gap** | a v16 database cannot distinguish "contradicted a prediction" from "unexplained by any hypothesis", so a contradiction whose cause was a wrong prediction is backfilled as eligible. The direction is conservative (hold rather than trust) everywhere else. **A workspace with known pre-W45 conflicts should be adjudicated once.** |
| **the W44 archaeology's entry-point census** | the audit scanner's defect (eleven missed CLI mains) is **documented with its reproduction command**, not fixed, because the directive forbids regenerating the archaeology's artefacts. `W45-CAPABILITY-DELTA.md` §2.1. |
| **the audit scripts against the changed tree** | the W44 CSV describes the tree as it was at the archaeology. Re-running the scripts is a separate, explicitly authorised step; the directive forbids regenerating the artefacts in place. |

---

## 5. The deviations from the prompt

| the prompt says | what was done | why |
|---|---|---|
| "Introduce an explicit durable distinction between … eligible / unresolved / accepted / rejected" | done, with four states and a new table | — |
| "Do not overload `Case.Origin`" | honoured: a new enum and a new table | — |
| "The exact names and representation must be chosen after inspecting the existing model" | `ObservationDisposition` in the micro-world's package, persisted in the epistemic store's own table | the disposition is part of the session's semantics and part of the store's durability; splitting it across packages would have made the dependency worse |
| "A reference computation must use exactly the same epistemic inclusion policy as the model it is supposed to verify" | done — and it forced the second rule (D3): the inclusion policy had to distinguish a BELIEF from an unsettled prediction, or the model could not learn at all | the rule was not in the prompt; it follows from the prompt's own acceptance criterion that the restored session must not apply an unresolved contradiction |
| "Prefer one transactional operation rather than several independently committed calls" | done for the epistemic write and for the regulatory state | — |
| "If the current micro-world cannot support a valid independent validation set, the correct result is to suspend new promotions **or** explicitly classify the existing fast path as an in-sample cache/derived shortcut" | **both** halves of the alternative: no promotion fires, and the fast path is explicitly relabelled as an in-sample derived shortcut | suspending the promotion alone would have removed the fast path and with it the only O(1) path in the portfolio; the relabelling keeps the behaviour and removes the false claim |
| "Implement a traceable invalidation/demotion path" | the mechanism is implemented and unit-tested, but there is no live instance because nothing is promoted | the directive also says "if it can be done without introducing an unsafe shortcut" |
| "Use narrowly targeted test commands and document their exact filters and denominators" | done: three scopes with their exact filters, and the per-class numbers | — |
| "Run existing targeted W43 and W44 regression scopes where practical" | done: the W43 scope (1518) and the W44 cognitive scope (332) | — |
| "Do not claim the full repository is green based on a selected package run" | no such claim is made anywhere | — |

---

## 6. The repository protection

| rule | how it was kept |
|---|---|
| the owner changes are preserved byte-for-byte | the 6 modified files and the 5 untracked paths are **unmodified by this wave**; `git status` shows the same set as at the start |
| the RECON-W44.0 deliverables and the four audit scripts are preserved | untouched, untracked, not staged, not regenerated |
| no `git reset`, `clean`, `checkout`, `switch` or `stash` | none was run |
| no branch switch | the branch is `develop`, as at the start |
| no commit, no push | none |
| no production change outside the authorised scope | the changes are in `io.matrix.cognitive`, `io.matrix.cognitive.microworld`, `io.matrix.cognitive.algorithms`, and **two** files of `io.matrix.knowledge` (`KnowledgeStore`, `KnowledgeWorkbench`) — the two the directive names as the relevant implementation for Priority A |
| `AGENTS.md`, `.gitignore`, `opencode.json`, `.codex/`, `.veai/`, `.opencode/` | untouched |
| no temporary-file cleanup pattern that could touch owner data | the temporary files created by this wave were its own (`/tmp/w45-*`); no cleanup pattern was run against the repository |

**The wave is local and reviewable. It awaits the owner's authorisation to commit.**
