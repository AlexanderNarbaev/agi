# W46 — FINAL REPORT

**MATRIX — Project Consolidation & Autonomous Engineering System.**
**Prepared:** 2026-11-04 · **Branch:** `develop` · **HEAD:** `a6ded233`
**Nothing is committed and nothing is pushed.** The W46 directive reserves that for the owner's
explicit authorisation after this report.

**The four phases, and their reports:**

| the phase | the report |
|---|---|
| W46.0 — the read-only audit | `docs-v2/engineering/W46.0-FINAL-REPORT.md` + the ten audit artefacts |
| W46.1 — the canonical governance structure | `docs-v2/engineering/W46.1-STATUS.md` |
| W46.2 — the reconciliation | `docs-v2/engineering/W46.2-STATUS.md` |
| W46.3 — the autonomous engineering infrastructure | this document, §4 and §5 |

---

## A. The initial state

| | |
|---|---|
| the repository | `AlexanderNarbaev/agi` |
| the branch | `develop` |
| the HEAD | `a6ded23338e069b8bc8a3744c234be5a03995f43` — **W45.2**, the owner's four architecture decisions |
| the remote | `origin/develop` — **the same commit**; the local and the remote do not diverge |
| the working tree | **11 owner-owned entries**, preserved byte-for-byte |
| the environment | Ubuntu 26.04.1, kernel 7.0.0-42, Java 25 at `/home/alexandr-narbaev/jdk25`, Gradle 9.6.0, Python 3.14.4 |
| the resources | 16 cores / 32 threads, 59.5 GiB RAM, a 30 GiB `/tmp` quota, an RTX 5070 Ti (12 GiB) |

**The owner's eleven entries — none of them was touched:**

`.gitignore` · `AGENTS.md` · `opencode.json` (+ the deletion of `opencode.json.bak`) ·
`.opencode/todo.md` · `.opencode/w37-plan.md` · `.codex/` · `.opencode/goals/` ·
`.opencode/opencode-swarm.json` · `.opencode/tmp/` · `.veai/`

**The directive's premise was one wave out of date, and the resolution matters.** The
directive assumed HEAD = `509d23ba` (W45.1) with W45.1 uncommitted. The reality: HEAD =
`origin/develop` = `a6ded233` = **W45.2**, committed and pushed. **The four decisions D1–D4
were therefore already approved and implemented** — the owner's second answer to the
questionnaire superseded the directive's instruction to mark them `OWNER_APPROVAL_PENDING`.
This is recorded in `docs-v2/engineering/W46-OWNER-DECISION-PACKET.md`.

---

## B. What was discovered

### The architecture — and it is not what the documents said

| the finding | the evidence |
|---|---|
| **chain A is ONE Quarkus process with THREE serving paths** — workbench (57 routes), cognitive (11), chat (6), plus 136 over 36 resources: **210** | the constant-pool census over the mounted route classes |
| **chain B is one JDK `HttpServer` on 8765** with **28** routes and **9 declared but unmounted** JAX-RS routes | `MinimalHttpServer.registerRoutes()` `:370-412` |
| **the gateway's default mode is `stub`** — `/v1/analyze` answers from an **87-line** `StubBrainCycle` unless `MATRIX_MODE=production` | `MinimalHttpServer.java:127-128`, `:300-304` |
| **the chains do not meet** — chain B reaches **0** of `io.matrix.cognitive.**`, 0 of the stores, 0 of the chain A surface; they share **179** substrate classes | the isolation gate: 16 checks, 0 failed |
| **the cycles are six, not five** — and the two `BrainLoopService` classes are not the same thing: the one with **25** callers is unserved, the one with **1** is the serving path | the constant pool |
| **chain A opens three ports and two are undeclared** — 9094 TCP and 9093 multicast, bound by `P2PNetwork` with no configuration property | `P2PNetwork.java:28-29, 37, 108, 121` |
| **one canonical database** — `data/knowledge/workbench.sqlite`, knowledge **v19** (27 tables) + cognitive **v4** (12 tables) over **two connections**; the kernel's write crosses both and is **not** one transaction | `KnowledgeStore.java:170`, `CognitiveStore.java:146` |
| **no second store duplicates the knowledge store's responsibility** | the census of every JDBC connection |
| **no file-level backup exists** — `BackupManager` and `VACUUM INTO` appear nowhere in the production sources | the grep over the sources |
| **79 CLI entry points**; 14 modules in Gradle, 3 commented out, 2 outside; **no module-level cycle**, 2 class-level cycles, a 42-package SCC | the source scan and an independent `javap` scan, which agree |

### The GPU — the truth, measured from a real run

```
ldconfig -p        →  libcudart.so.12 => /usr/lib/x86_64-linux-gnu/libcudart.so.12  (CUDA 12.4)
that file exports cudaLibraryGetKernel → 0 times
OrtEnvironment.getAvailableProviders()  →  [CPU, CUDA, TENSOR_RT]
addCUDA() WITHOUT LD_LIBRARY_PATH      →  FAILED OrtException   ← MATRIX's own configuration
addCUDA() WITH    LD_LIBRARY_PATH      →  OK
```

`matrix-core/build.gradle:135-155` sets `java.library.path` — a **JVM property** — while a
native `dlopen` consults **`LD_LIBRARY_PATH`**. The loader therefore finds the distro's
CUDA-12.4 library, which lacks the symbol, and `OnnxRuntimeAdapter.java:104-114` swallows
the exception. **With `LD_LIBRARY_PATH` exported: 191.367 ms → 15.190 ms per forward (12.6×)**.
`OnnxRuntimeAdapter.java:118-123` sets `activeProviders = {"CPUExecutionProvider"}`
**unconditionally** — a diagnostic that cannot report the truth.

### The documentation — 1162 documents, and the numbers did not reproduce

- the W44 "1131" does not reproduce: **1162** Markdown, **880** outside the archive;
- **51** broken links → the checker found **360 strict failures** over the wider scope;
- **42 duplicate identifiers**, and one is a real collision: **`SPEC-002` is two different
  documents**;
- `D1`/`D-001` means **five different decisions** in five documents;
- `AGENTS.md` points at **five paths that do not exist**;
- `docs-v2/INDEX.md` had **zero** clickable links and reached 1 document by clicking;
- the hypothesis registers and `docs-v2/science-v2/` carry **twelve identifiers under one
  number with opposite verdicts** — the register's **H-002 is `refuted-toy`**, science-v2's
  **H-002 is "HDC Shows Superior Sample Efficiency ✅"**;
- **101** documents carry a status outside the policy's vocabulary.

---

## C. The four decisions

**All four are the owner's, already made, and already implemented.** The wave documented
them, measured their residuals and wrote down what is still owed.

| the decision | the recommendation | the state | the residual |
|---|---|---|---|
| **D1 — the canonical conductor** | Option 1: name the entry point per application, keep the specialist cycles | **ACCEPTED and implemented** — `ADR-W45.2-01`, `io/matrix/api/CanonicalEntryPoint.java` | the six cycles and the two `BrainLoopService` classes are now distinguished in `docs-v2/architecture/RUNTIME-TOPOLOGY.md` |
| **D2 — the chain boundary** | Option 1: keep the chains isolated, fail closed | **ACCEPTED and implemented** — the gate proves it (16 checks), the self-test is the negative control | the bridge contract is written down; no bridge exists |
| **D3 — the destructive reload** | Option 1: fail closed + a recoverable snapshot | **ACCEPTED and implemented** | **D3-a**: the flag is `?replace=true`, the directive names `replaceExisting: true`; **D3-b**: no preview; **D3-c**: the erasure set erases `evidence` but not `epistemic_case` / `observation_occurrence` / `observation_disposition` / `observation_adjudication` |
| **D4 — the recovery topology** | Option 1: one writer process per database, enforced and documented | **ACCEPTED and implemented** — the `WriterLock` | **D4-a**: the lock resolves the path with `normalize()`, not `realPath()`, so **two symlinked aliases yield two locks and two writers** (TASK-W46-4) |

---

## D. The changes

**112 modified tracked files and 27 new ones. The owner's 11 entries are untouched.**

### The canonical set — 21 documents, one subject each

| the group | the documents |
|---|---|
| the governance set | `PROJECT-STATE.md` · `ROADMAP.md` · `DECISION-REGISTER.md` · `RISK-REGISTER.md` · `OPEN-QUESTIONS.md` · `AGENT-OPERATING-PROTOCOL.md` · `DOCUMENT-STATUS-POLICY.md` · `SESSION-HANDOFF-TEMPLATE.md` · `SKILL-INVENTORY.md` · `SKILLS.md` · `AGENTS-AMENDMENT-PROPOSAL.md` |
| the architecture | `architecture/CAPABILITY-REGISTRY.yml` · `architecture/RUNTIME-TOPOLOGY.md` · `architecture/DATA-AND-MEMORY-ARCHITECTURE.md` |
| the operations | `operations/RESOURCE-BUDGETS.md` · `operations/RECOVERY-RUNBOOK.md` · `operations/HARDWARE-PROFILE.json` |
| the engineering | `engineering/TEST-CATALOGUE.yml` · `engineering/W46*.md` · `engineering/W46*.csv` |
| the research | `research/RESEARCH-INDEX.md` · `research/NEGATIVE-RESULTS-INDEX.md` |
| the work log | `waves/INDEX.md` |

### The scripts — 5 new, 1 extended, 1 wired

| the script | the change |
|---|---|
| `scripts/docs-link-check.py` | **new**, ~1400 lines, the standard library only: the links, the anchors, the identifiers, the duplicate declarations, the unreachable documents, and `--self-test` |
| `scripts/validate-project-registry.py` | **new**: the registry against its own rules — 34 capabilities, 24 fields, the statuses, the paths |
| `scripts/validate-project-state.py` | **new**: the state against the repository — the branch, the commit, the canonical set, the registers, the catalogue |
| `scripts/validate-test-catalogue.py` | **new**: the test evidence catalogue against the JUnit XML that the last run left behind |
| `scripts/generate-project-report.py` | **new**: the project report, generated from the canonical set |
| `scripts/hardware-probe.sh` | **extended**: 58 measurements in the five categories, `--markdown <path>`, `--json <path>`, `set -euo pipefail` |
| `scripts/quality-gate.sh` | **DOCS-1 and TEST-1 added**: the link checker and the catalogue validator now run inside the gate |

### The compatibility pointers — 10 documents, one status block each

`engineering/PLAN.md` · `architecture/CAPABILITY-REGISTRY.md` ·
`engineering/W44-CAPABILITY-REGISTRY.md` · `architecture/OVERVIEW.md` ·
`architecture/MATRIX-COGNITIVE-ARCHITECTURE.md` · `engineering/STANDARDS-MATRIX.md` ·
`operations/HARDWARE-PROFILE.md` · `INDEX.md` · `quality/OPEN-DECISIONS.md` ·
`engineering/RISK-REGISTER.md`

**Each is a status block at the head. No historical content was rewritten.**

### The one production change

`matrix-core/src/main/java/io/matrix/api/CanonicalEntryPoint.java` — `BirBrainCycle` was
attributed to the epistemic application; the constant pool places it in **chain B**, and no
chain A route reaches it. The attribution is corrected and a test asserts it.
`CanonicalEntryPointTest` gained `aSpecialistIsAttributedToTheChainThatReachesIt`.

### The link repairs — 360 → 0

| the class | before | after | the repair |
|---|---|---|---|
| `BROKEN_LINK` | 75 | **0** | the renumbered series repaired **by subject**, with the evidence named; the site routes recognised as routes; the references with no counterpart marked with a reason |
| `MISSING_PATH` | 124 | **0** | the v1-tree references marked; the runtime artefacts and the placeholders marked; **the missing scripts and documents written** |
| `DUPLICATE_IDENTIFIER` | 127 | **0** | the resolution rule rewritten: **the register of a family defines, the reports reference** |
| `UNDEFINED_IDENTIFIER` | 34 | **0** | the eleven genuine gaps recorded as **Q-W46-19** and marked; the patterns marked as patterns |

**Two exemption markers, both auditable** — the reason is on the line:
`<!-- no-link: the reason -->` (65 references) and `<!-- no-id: the reason -->` (25).

### The status lines that were applied

`HISTORICAL`: `INSTALL.md` · `SESSION.md` · `MIGRATION.md` · `docs-v2/TRANSFORMATION-PLAN.md` ·
`docs-v2/engineering/RELEASE-NOTES.md` · `docs-v2/quality/RECON-W33.1-runbook-D3-D5.md` ·
`docs-v2/research/summaries/INDEX-W20.md` — `ARCHIVED`: `docs-v2/archive/*` — `RESEARCH`:
the hypothesis registers and the research reports.

### What was NOT changed

`CONSTITUTION.md` · `AGENTS.md` (the proposal is prepared; the file is untouched) ·
`constitution.md` (an unfilled template duplicate — the decision is the owner's) ·
`matrix-core/src/main/java/io/matrix/ethics/frozen/**` · `matrix-core/src/main/resources/avro/**` ·
`.github/workflows/**` · `.gitignore` · `opencode.json` · `.codex/**` · `.veai/**` ·
`.opencode/**` · **every production database** · `docs-v2/_raw/**`.

---

## E. The verification

**Every command below was executed on this machine on 2026-11-04.**

| the command | the exit code | the result |
|---|---|---|
| `python3 scripts/docs-link-check.py --root . --strict` | **0** | **0 strict failures** over 1187 documents; the archive (2048) and the waves (79) are reported separately and never fail the gate |
| `python3 scripts/docs-link-check.py --self-test` | **0** | **PASSED** in 94 ms — the five classes found, the strict mode red on the fixture and green on the clean one |
| `python3 scripts/validate-project-registry.py --strict` | **0** | **0 defects** — 34 capabilities, 24 fields each, every status in its vocabulary, no `SERVED` without a serving path, no `MEASURED` without a benchmark |
| `python3 scripts/validate-project-state.py --strict` | **0** | **0 defects** — the branch, the commit, the canonical set, the registers and the catalogue agree with the repository |
| `python3 scripts/validate-test-catalogue.py --strict` | **0** | **0 defects** — 9 scopes and 2 that cannot run here; every commit the catalogue names is a commit the repository has, and every counter it claims is the counter the JUnit XML holds |
| `python3 scripts/generate-project-report.py` | **0** | the report is generated from the canonical set; every number counted from the document it names |
| `./gradlew :matrix-core:test --tests "io.matrix.api.*" --tests "io.matrix.cognitive.*" --tests "io.matrix.knowledge.*" --rerun-tasks` | **0** | **195 classes, 1543 tests, 0 failures, 0 errors, 0 skipped** |
| `bash scripts/quality-gate.sh` | **0** | **271 candidates at the baseline of 271** (shell 78, java 193); **DOCS-1: the documentation links resolve** |
| `bash scripts/security-boundary-check.sh` | **0** | **0 violations** |
| `bash scripts/w45-chain-isolation-check.sh` | **0** | **16 checks, 0 failed** |
| `bash scripts/w45-chain-isolation-selftest.sh` | **0** | **6 checks, 0 failed** |
| `bash scripts/hardware-probe.sh` | **0** | the profile regenerated; **58 measurements**, 0 unavailable |

**The earlier scopes, executed in W46.0 and W46.1:** `io.matrix.cognitive.*` +
`io.matrix.knowledge.*` → 128 classes / 910 tests / 0 failures · the W43 scope
(memory/brain/api/rag) → 98 classes / 1036 tests / 0 failures · `matrix-brain-runtime` →
119 / 914 / 0 / 2 skipped · `matrix-api-gateway` → 43 / 240 / 0 · the SIGKILL journey →
37 checks / 0 failed.

**Not run, and why:** the **full `matrix-core` suite** (the directive forbids it without the
owner's agreement; the catalogue records it as not runnable here); the **native-image tests**
(GraalVM is absent on this host — they cannot run); the **benchmarks** (the probe records the
12.6× as `reported-by-the-system`, not as a fresh measurement).

---

## F. The unresolved questions

**23 records in `docs-v2/governance/OPEN-QUESTIONS.md`. The ten that block the work:**

| the id | the question | the owner | the answer that closes it |
|---|---|---|---|
| **Q-W46-01** | the reload's consent flag: `?replace=true` or `replaceExisting: true`? | **yes** | the owner's ruling, and then the endpoint and its test |
| **Q-W46-02** | the preview mode — what does it return? | **yes** | the owner's ruling |
| **Q-W46-03** | the erasure scope: does the reload erase the case and its decision history? | **yes** | the owner's ruling — **this is a data-loss decision** |
| **Q-W46-07** | the GPU path: `LD_LIBRARY_PATH` in the launcher, and the hardcoded `activeProviders()` diagnostic | **yes** | the owner's ruling; the measurement is already in hand |
| **Q-W46-13** | the retention policy — how long, where, who restores | **yes** | the owner's ruling |
| **Q-W46-16** | the fifteenth TLA+ specification: gate it or withdraw it? | **yes** | the owner's ruling — a change to the gate's scope |
| **Q-W46-18** | PR-002 and PR-006: a deadline or a rejection? | **yes** | the owner's ruling |
| **Q-W46-20** | the renumbered series: rewrite the eleven references, or write the four missing subjects? | **yes** | the owner's ruling per subject |
| **Q-W46-22** | the harness's skill roots — the trigger table or the directory alone? | **yes** | the owner's ruling, if the harness must change |
| **Q-W46-23** | the four skill packages whose declared name differs from their directory | **yes** | the owner's ruling — they are the owner's files |

**And the ones the coordinator can close:** Q-W46-17 (the `OPEN-DECISIONS.md` contradiction),
Q-W46-19 (the eleven undeclared hypotheses), Q-W46-21 (the 41 references to the removed v1
tree), Q-W46-07's measurement part, and the twelve ids that carry opposite verdicts.

**And the two decisions that are the owner's by the directive's own words:**
**the `AGENTS.md` amendment** (`docs-v2/governance/AGENTS-AMENDMENT-PROPOSAL.md` — the exact
diff, verified to apply) **and the `constitution.md` duplicate** (an unfilled template
duplicate of `CONSTITUTION.md`; the proposal is to mark it, not to delete it).

---

## G. The next five tasks

| # | the task id | the objective | the scope | the prerequisites | the files to read | the files likely to change | the tests | the acceptance criteria | the resource class | the autonomy | the owner |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | **TASK-W46-4** | the writer lock must resolve the **real** path | the `WriterLock` uses `toAbsolutePath().normalize()`; two symlinked aliases of one file yield two locks and two writers. Change it to `toRealPath()` and add a test with a symlink | the owner's ruling (the database-ownership boundary is protected) | `io/matrix/cognitive/WriterLock.java`, the decision D4 in the register | `WriterLock.java`, `CognitiveWriterLockTest.java` | the cognitive scope | a symlinked alias is refused by the **same** lock, proven by an executed test | medium | **2 — controlled integration** | **yes** |
| 2 | **TASK-W46-6** | the whole-database restore, **executed** | `VACUUM INTO` → restore → verify, against an **isolated temporary database** and an **isolated process** — never the owner's database | the recovery runbook exists ✓ | `docs-v2/operations/RECOVERY-RUNBOOK.md`, `scripts/docs-link-check.py` for the pattern | a new test, the runbook's evidence section | the new test, in isolation | the restore produces a database whose `integrity_check` is `ok`, whose schema version is right, and whose row counts match — **executed and recorded** | medium | 1 | no |
| 3 | **TASK-W46-3** *(the skill packages)* | install the four recommended skills | `matrix-session-state` · `matrix-architecture-navigator` · `matrix-evidence` · `matrix-document-integrity` under `.agents/skills/` — the only root the harness reads by default, and no owner-owned file is touched | Q-W46-22 if the trigger table must change | `docs-v2/governance/SKILLS.md`, `docs-v2/governance/SKILL-INVENTORY.md` | four new directories | the package validator | a session finds the skills by their frontmatter, and the validation command passes | light | 1 | no |
| 4 | **TASK-W46-8** | the GPU launcher and the honest diagnostic | export `LD_LIBRARY_PATH` in `scripts/start-mind.sh`; replace the hardcoded `activeProviders` with the real provider list | Q-W46-07 | `matrix-core/src/main/java/io/matrix/.../OnnxRuntimeAdapter.java:104-123`, `scripts/start-mind.sh` | the adapter, the launcher, its test | the adapter's scope | a run reports the **actual** provider, and the 12.6× is reproduced by a command in the report | medium (GPU) | 2 | **yes** |
| 5 | **TASK-W46-7** | the negative-results index, and the classifications | the 261 documents whose status is outside the vocabulary; the twelve identifiers with opposite verdicts; the eleven undeclared hypotheses | Q-W46-19, Q-W46-20 | `docs-v2/governance/SKILL-INVENTORY.md` §9, the link checker's classification report | the documents' status lines; the registers | the link checker in strict mode | **0** documents with an out-of-vocabulary status in the current group, and every remaining duplicate explained | light | 1 | no |

---

## H. The next-session bootstrap

```text
MATRIX SESSION BOOTSTRAP

Repository: /home/alexandr-narbaev/Projects/agi  (AlexanderNarbaev/agi)
Branch: develop
HEAD: a6ded23338e069b8bc8a3744c234be5a03995f43  (= origin/develop; they do not diverge)
Working tree: NOT COMMITTED. W46 is complete in the tree: 112 modified files, 27 new files.
              The owner's 11 entries are untouched (.gitignore, AGENTS.md, opencode.json,
              opencode.json.bak deleted, .opencode/todo.md, .opencode/w37-plan.md, .codex/,
              .opencode/goals/, .opencode/opencode-swarm.json, .opencode/tmp/, .veai/).
              Do NOT commit and do NOT push — the owner authorises that after the review.

Latest completed wave: W46 (W46.0 audit, W46.1 structure, W46.2 reconciliation,
                         W46.3 infrastructure) — uncommitted.
Active task: none — the owner reviews the final report and answers the ten questions.

Canonical project-state document: docs-v2/governance/PROJECT-STATE.md
Canonical roadmap:               docs-v2/governance/ROADMAP.md
Canonical work log:              docs-v2/waves/INDEX.md
Architecture overview:           docs-v2/architecture/RUNTIME-TOPOLOGY.md
                              + docs-v2/architecture/DATA-AND-MEMORY-ARCHITECTURE.md
Capability registry:             docs-v2/architecture/CAPABILITY-REGISTRY.yml  (34 capabilities)
Decisions:                       docs-v2/governance/DECISION-REGISTER.md
Open owner decisions:            docs-v2/governance/OPEN-QUESTIONS.md — 23 records; the ten that
                                 block the work: Q-W46-01, 02, 03, 07, 13, 16, 18, 20, 22, 23
Current hardware profile:        docs-v2/operations/HARDWARE-PROFILE.md  (+ .json, 58 measurements)
Resource constraints:            docs-v2/operations/RESOURCE-BUDGETS.md
                                 - JDK 25 only: export JAVA_HOME=/home/alexandr-narbaev/jdk25
                                 - the PATH java is a JRE and the PATH javac is JDK 21
                                 - one matrix-core test run at a time; the test JVM asks for 16 g
                                 - the /tmp quota is 30 GiB; the models are 14 GB
                                 - the full matrix-core suite needs the owner's agreement

Required first action:
  export JAVA_HOME=/home/alexandr-narbaev/jdk25
  cd /home/alexandr-narbaev/Projects/agi
  git status --porcelain=v2          # confirm the 11 owner entries are still there
  python3 scripts/docs-link-check.py --root . --strict   # must exit 0
  python3 scripts/validate-project-state.py --strict     # must exit 0
  python3 scripts/validate-test-catalogue.py --strict  # must exit 0

Exact files to read (five — the ordinary reading set):
  1. docs-v2/governance/PROJECT-STATE.md
  2. docs-v2/governance/ROADMAP.md
  3. docs-v2/governance/DECISION-REGISTER.md
  4. docs-v2/governance/OPEN-QUESTIONS.md
  5. the subsystem you are about to change — and its tests
  (then, only if the task changes an architectural assumption: the history)

Exact verification commands:
  ./gradlew :matrix-core:test --tests "io.matrix.cognitive.*" --tests "io.matrix.knowledge.*" --rerun-tasks
  ./gradlew :matrix-core:test --tests "io.matrix.api.*" --rerun-tasks
  bash scripts/quality-gate.sh              # 271 at the baseline; DOCS-1 must stay green
  bash scripts/security-boundary-check.sh   # 0 violations
  bash scripts/w45-chain-isolation-check.sh # 16 checks
  bash scripts/w45-chain-isolation-selftest.sh
  python3 scripts/docs-link-check.py --root . --strict
  python3 scripts/validate-project-registry.py --strict
  python3 scripts/validate-project-state.py --strict
  python3 scripts/validate-test-catalogue.py --strict

Do not repeat:
  - the W44 bytecode archaeology: docs-v2/engineering/RECON-W44-ARCHAEOLOGY.md and the eleven
    maps beside it hold it; the W46.0 audit re-derived what changed since
  - the W45 reliability work: the crash consistency, the request recovery, the writer lock,
    the chain boundary — all implemented, all tested, all recorded
  - the W46 audit: the truth map, the topology, the capability reconciliation, the hardware
    report and the four registers hold every measurement
  - the classification of the historical research: docs-v2/research/RESEARCH-INDEX.md and
    NEGATIVE-RESULTS-INDEX.md hold it
  - the link repair: the tree is green, and the checker tells you where a new break is

Do not modify:
  CONSTITUTION.md · AGENTS.md (the amendment is PROPOSED in
    docs-v2/governance/AGENTS-AMENDMENT-PROPOSAL.md — the owner applies it) · constitution.md ·
  matrix-core/src/main/java/io/matrix/ethics/frozen/** · matrix-core/src/main/resources/avro/** ·
  .github/workflows/** · .gitignore · opencode.json · .codex/** · .veai/** · .opencode/** ·
  docs-v2/_raw/** · every production database and every user file ·
  the workspace-reload contract, the chain boundary, the serving topology and the database
  ownership — all four are the owner's decisions and three of them have open residuals
```

---

## The last word

**W46 is complete as a wave of work, and uncommitted as a matter of policy.**

What the project gained: **one** current-state document, **one** roadmap, **one** work log,
**one** capability registry that a validator checks, **one** decision register that
separates the owner's decisions from the agent's, **one** risk register, **one** open-questions
register with **23** records, **one** agent protocol with the autonomy levels and the
handoff template, **one** skill inventory and **four** recommended skills, **four** tools that
can go red, and a documentation tree whose links all resolve.

What it did not gain: **no commit and no push**, **no change to a runtime behaviour**, **no
edit to a protected file**, **no deletion**, and **no new store, kernel, conductor or
framework**.

**And what it found, which is the real product of the wave:** the project's claims were
mostly *true and mostly unreachable*. The gap between "implemented" and "served" is now a
number in a machine-readable registry rather than an impression in a document — and the
number is **34 capabilities, of which 15 are `SERVED`**.
