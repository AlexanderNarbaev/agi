# MATRIX — AGENT OPERATING PROTOCOL

**Status:** `CANONICAL` · **Subject:** how an agent works in this repository
**Snapshot of:** `a6ded23338e069b8bc8a3744c234be5a03995f43` · **Date:** 2026-11-04
**Companion documents:** `docs-v2/governance/PROJECT-STATE.md` (what MATRIX is today) ·
`docs-v2/governance/ROADMAP.md` (what comes next) ·
`docs-v2/governance/DECISION-REGISTER.md` (what was decided) ·
`docs-v2/governance/DOCUMENT-STATUS-POLICY.md` (the status vocabulary) ·
`docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` (this file — how to work)

**The purpose of this document, in one sentence:** *MATRIX must stop depending on the memory
of the previous agent.* Every fact a new session needs is in the repository, at a path this
document names.

**What this document is:** a procedure. Every section is a thing an agent does, with the
command or the path it uses.

**What this document is not:** a replacement for `CONSTITUTION.md` or `AGENTS.md`. Those two
are **normative** — they bind the work. This document is subordinate to them, and where they
disagree, they win and this document is wrong. See §9.

---

## 0. How to use this document

Read §1 once, at the start of a session. Then classify the task (§2), check the autonomy
level (§3), and follow the subagent rules if you delegate (§4). At the end, write the handoff
(§6).

**Three sentences that carry the whole document:**

1. **Read a few files, not the history.** A session reads the canonical documents, the
   subsystem it touches, and the current state — not eight thousand lines of wave reports.
2. **The classification decides the verification.** A documentation change does not need a
   matrix-core test run; a schema change does. §2 says which.
3. **Autonomy is not implied by a general instruction to "be autonomous".** Level 3 work
   needs the owner's explicit word, every time. §3.

---

## 1. The session initialisation — twelve steps

### 1.1 The rule

> **Do not read every historical document on every run. A full historical audit is required
> only when the task changes architectural assumptions or the current evidence is
> insufficient.**

The ordinary case needs **five files**: `docs-v2/governance/PROJECT-STATE.md`,
`docs-v2/governance/ROADMAP.md`, this document, the subsystem's own sources, and the
decision register when the task touches a decision. The historical record
(`docs-v2/waves/`, `docs-v2/engineering/W4*.md`, `wal/SESSION_WAL.md`) is **evidence to be
consulted when a question arises**, not a preamble.

**Why this is a rule and not a preference.** The W46.0 audit read 1 162 documents and
produced ten deliverables, and the W44 archaeology read 2 908 classes — because there was
no canonical place to look first. Those numbers are now in the repository. Repeating them is
not diligence; it is the failure mode this wave exists to remove.

### 1.2 The twelve steps

Each step names the **concrete command or path** for this repository. Run the command; do
not assume its result.

| # | the step | the command or path | what you are establishing |
|---|---|---|---|
| **1** | **The repository root** | `cd /home/alexandr-narbaev/Projects/agi` | Every path in this document is relative to it. `pwd` must print exactly that. |
| **2** | **The branch** | `git rev-parse --abbrev-ref HEAD` | Expected: `develop`. **Any other branch is a fact to report, not a state to change.** Do not switch branches (§3, Level 3). |
| **3** | **The HEAD** | `git rev-parse HEAD` | Compare with `docs-v2/governance/PROJECT-STATE.md` §2. If they differ, the state document is stale for that subject and the **state document loses** (PROJECT-STATE.md §7). |
| **4** | **The remote** | `git rev-parse origin/develop` | If it differs from the local HEAD, the tree has diverged — report it; do not pull, rebase, merge or reset (§3, Level 3). |
| **5** | **The working tree** | `git status --porcelain=v2` | See §1.3. |
| **6** | **The owner's own modifications** | the eleven entries listed in §1.3, compared against `git status --porcelain=v2` | **Ten are the owner's protected work. The eleventh, `wal/SESSION_WAL.md`, is the local journal.** A *different* set is not a failure — the owner worked in between. **Record the set; do not "restore" it.** |
| **7** | **The canonical project state** | `docs-v2/governance/PROJECT-STATE.md` | The branch, the commit, the wave, the test baseline, the blockers, the pending owner decisions, the next work. |
| **8** | **The active roadmap and wave** | `docs-v2/governance/ROADMAP.md` (§3 the active wave, §4 the ready tasks, §5 the blocked tasks) and, for the wave itself, `docs-v2/engineering/W46.0-FINAL-REPORT.md` | The task id you are working on, its prerequisites, and whether it is **ready** or **blocked**. |
| **9** | **The pending owner decisions** | `docs-v2/governance/OPEN-QUESTIONS.md` (the register) and `docs-v2/governance/PROJECT-STATE.md` §5 (the five that block) | If your task touches one of them, it is **blocked** until the owner answers. `docs-v2/engineering/W46-OPEN-QUESTIONS.csv` carries the competing explanations and the experiment that would resolve each. |
| **10** | **The architecture and capability records** | `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` (the serving chains, the routes, the persistence) · `docs-v2/engineering/W46-REPOSITORY-TOPOLOGY.md` (the module graph) · `docs-v2/architecture/CAPABILITY-REGISTRY.yml` (the four axes, 34 capabilities — the registry of record; the reconciled evidence it was built from is `docs-v2/engineering/W46-CAPABILITY-RECONCILIATION.csv`) | Whether the thing you are changing is on a serving path at all. **Most of this repository is implemented and unserved.** |
| **11** | **The subsystem to change** | the module's own `build.gradle` and `src/main/java/**` | The actual code, not the description of it. |
| **12** | **The test, the verification command and the resource availability** | §7 and §8 of this document, plus the task's row in `docs-v2/governance/ROADMAP.md` (it names the risk, the resource class, the acceptance criteria, the required evidence and the test scope) | Whether you may run the verification at all, and what it costs. |

**Step 12 has three sub-questions, and all three must be answered before the first build
command:**

- **the test scope** — the task's row names it; `docs-v2/engineering/TEST-CATALOGUE.yml` is
  the per-scope registry with the last verified commit.
- **the resource availability** — is another agent already running a build? Ask the
  coordinator. **One matrix-core test run at a time** (§8).
- **the bounded task** — what exactly you may change, and what you may not. If the task
  names a bounded write scope, the scope is the boundary: a file outside it is not yours,
  even when it is obviously wrong.

### 1.3 The eleven owner entries — protected, never modified

At the snapshot commit the working tree carries these, and **no agent may touch, discard,
stage or commit them** (source: `docs-v2/governance/PROJECT-STATE.md` §8):

```
.gitignore   .opencode/todo.md   .opencode/w37-plan.md   AGENTS.md   opencode.json
the deletion of opencode.json.bak   .codex/   .opencode/goals/
.opencode/opencode-swarm.json   .opencode/tmp/   .veai/
```

Plus the local journal `wal/SESSION_WAL.md`, which is **outside git** (`.gitignore` line 127
ignores `/wal/`) and therefore local-only.

**The owner's databases and data are not on that list and are not on yours.**
`data/` is ignored (`.gitignore` line 77) and holds the live databases. **Never modify
`data/`.** Any audit that needs a database makes its own in a temporary directory — this is
what `scripts/w43-counterexample-journey.sh` does (it creates a fresh `mktemp` store per
run, precisely so it never writes into the owner's file).

### 1.4 The ordinary reading set — the five files

| the file | when it is enough |
|---|---|
| `docs-v2/governance/PROJECT-STATE.md` | "what is MATRIX today, and what is blocked" |
| `docs-v2/governance/ROADMAP.md` | "what is the next task, and what does it need" |
| `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` (this file) | "how do I work" |
| the subsystem's own `build.gradle` + `src/main/java/**` | "what does the code actually do" |
| `docs-v2/governance/DECISION-REGISTER.md` | "was this already decided, and by whom" |

**Add a sixth only when the question demands it:** the wave reports
(`docs-v2/engineering/W46*.md`), the historical waves (`docs-v2/waves/`), or the local
journal (`wal/SESSION_WAL.md`).

---

## 2. The task classification

**The classification determines three things: which tests you may run, which agents you may
use, and which resources you may consume.** A task that is classified wrongly will either
run a verification it cannot afford, or skip one it needed.

### 2.1 The twelve task classes

**These are the wave directive's classes, verbatim.** The directive is the normative source
for them; the "what it is" and "the tell" columns are this repository's operational reading
of each, and they are what makes the classification decidable in practice.

| # | the class | what it is | the tell |
|---|---|---|---|
| **1** | **Documentation-only** | prose, a table, a status annotation, a registry row; no executable behaviour changes | the diff touches only `.md`, `.yml` or `.csv` |
| **2** | **Research-only** | an investigation whose output is evidence, not code | the output is a report, a register row or a hypothesis — and no production source changes |
| **3** | **Bug fix** | behaviour changes to be correct | a test that fails before the change and passes after it |
| **4** | **Refactor** | code moves; the behaviour must not change | the diff moves code and adds no behaviour; the same tests stay green and unchanged |
| **5** | **Capability implementation** | new behaviour on an existing surface — a new route, a new command, a new record | a new test and a new production path |
| **6** | **Integration** | two subsystems are wired together, or a module boundary moves | a `project(…)` edge, an `application {}` block, a `@Path` root, a runtime wiring |
| **7** | **Performance** | a number about speed, memory or throughput is asserted | the diff contains a number that someone could quote |
| **8** | **Reliability** | crash safety, concurrency, ordering, recovery, idempotence | the words *lock*, *owner*, *transaction*, *SIGKILL*, *idempoten* |
| **9** | **Security** | what is reachable, what is exposed, what is refused | an authorisation filter, a bind address, a firewall rule, a token |
| **10** | **Migration** | the shape of a persistent store changes | a `SCHEMA_VERSION` bump, a `CREATE TABLE`, an `ALTER TABLE` |
| **11** | **Operations** | a container, a manifest, a secret, a port, a deployment | `docker-compose.yml`, `deploy/`, `Dockerfile`, a port |
| **12** | **Release** | a versioned artifact is produced and published | a tag, a release note, a push |

**Two sub-cases the directive does not name, kept inside its classes:**

- **documentation with a tool** is class 1 with a tool: the script that checks the document
  is part of the change, and it must have its own self-test.
- **a native or a build-toolchain change** is class 6 or 11 depending on whether it moves a
  boundary or a deployment — and on this host it is **impossible** either way (§2.3).

### 2.2 The four risk levels

**The directive's four levels, verbatim:** low · medium · high · critical.

| the level | the meaning | who may authorise more than the level allows |
|---|---|---|
| **low** | nothing outside the working tree changes; no data is written; the change is reversible by discarding the diff | the coordinator |
| **medium** | the change reaches a serving path, a persistent store, or a process boundary | the coordinator **with** the owner's approval for the specific item |
| **high** | the change can destroy data, alter the owner's configuration, expose a network surface, or rewrite history | **the owner only** |
| **critical** | the change can lose the owner's work irreversibly, or change a normative document or a security boundary | **the owner only**, and never by an agent's own initiative |

### 2.3 The six resource classes

**The directive's six classes, verbatim:** light · medium · heavy · GPU · native-build ·
external-service-dependent.

| the class | what it costs | the ceiling on this host |
|---|---|---|
| **light** | reading, grep, one small edit, one short script | seconds; no build |
| **medium** | one targeted compile, one targeted test scope, one gate script | one Gradle invocation, under 45 minutes |
| **heavy** | the full module suite, a long integration journey, a benchmark series | **one at a time, and never two matrix-core test runs** — the test JVM asks for 16 g against ~38.7 GiB available (§8) |
| **GPU** | the CUDA provider, the model in VRAM | one model (3.25 GiB) and ≤ 8 GiB of the 12 227 MiB, keeping ≥ 4 GiB of headroom |
| **native-build** | the GraalVM image | **impossible on this host** — GraalVM and Mandrel are absent; the native-image classes are **never counted as passing** |
| **external-service-dependent** | PostgreSQL, Redis, Kafka, an OTLP collector, the Docker stack | the services that are running: postgres 17, redis 7, qdrant, grafana, memorylayer. **The PostgreSQL the application declares is on port 5433 and the machine's is on 5432** — which is why `/q/health/ready` answers 503 |

### 2.4 The mapping — classification to allowed verification

| the class | the risk | the resource | **the verification you may run** | **the verification you may NOT run** |
|---|---|---|---|---|
| **1** documentation-only | low | light | `bash scripts/validate-docs.sh` (exists today) · the link checker `scripts/docs-link-check.py` **once W46.3 lands — until then, no link verification exists, and the handoff must say so** | any Gradle invocation |
| **2** research-only | low | light | the sources, cited; a measurement with its environment recorded; **the hypothesis register updated, not a parallel register created** | a production source change; a claim without a source |
| **3** bug fix | low | medium | the **narrowest** scope that contains the fix: `--tests "io.matrix.<pkg>.*"` · the new test must fail before the fix and pass after | the full module suite |
| **4** refactor | low | medium | the **same** targeted scope as before the refactor, so the count is comparable · `--rerun-tasks` | the full module suite |
| **5** capability implementation | low → medium | medium | the targeted scope of the touched package **plus** the package's existing tests · `bash scripts/quality-gate.sh` if the diff adds Java or shell literals | the full module suite |
| **6** integration | medium | medium | the declaration tests (`CanonicalEntryPointTest`, `CanonicalEntryPointChainBTest`) · `bash scripts/w45-chain-isolation-check.sh` and its negative control | changing a module edge without the owner |
| **7** performance | medium | heavy | a benchmark **with a recorded environment**: the command, the machine, the date, the result artefact — `./gradlew :matrix-core:jmh --rerun-tasks -PjmhBenchmark=Name` · **an estimate is not a measurement** | quoting a number that no run produced |
| **8** reliability | medium | heavy | `bash scripts/w45-cognitive-recovery-journey.sh` (37 checks, real SIGKILLs) · the gate must be shown going **red** on a deliberate violation | running two writers against the owner's database |
| **9** security | medium → high | medium | `bash scripts/security-boundary-check.sh` · **a gate that cannot fail is not a gate** — show it red on a deliberate violation | changing the boundary without the owner |
| **10** migration | medium | medium | the **migration fixtures**: `SchemaMigrationTest`, `KnowledgeStoreMigrationTest`, `WorkspaceSnapshotMigrationTest` — a migration that only passes on a fresh database is a migration nobody tested | the full module suite · any run against the owner's `data/` |
| **11** operations | high | heavy | a compose file's own syntax check · a dry run against a temporary container | anything against the owner's live containers, ports or secrets |
| **12** release | high | external-service-dependent | the full verification set of the release, with its denominators | tagging, pushing or publishing **without the owner's authorisation** |

### 2.5 The two rules that come with the classification

1. **A classification is written down before the work starts,** in the handoff (§6). A task
   whose class changes mid-flight is re-classified, and the verification is re-derived.
2. **The class does not raise the autonomy level.** A documentation task (class 1) is Level 0
   or 1 even though it touches a canonical document. A capability implementation (class 5)
   that writes to the owner's database is Level 3 because of **what it touches**, not because
   of its class.

---

## 3. The autonomy levels

### 3.1 The four levels

| the level | what it is | what it needs |
|---|---|---|
| **Level 0 — read-only** | reading, grepping, listing, measuring. Nothing in the repository changes. | nothing |
| **Level 1 — reversible local work** | editing files in the working tree; running a targeted scope; adding a test. Everything is reversible by discarding the diff. | the coordinator's assignment and a bounded write scope |
| **Level 2 — controlled integration** | a change that reaches a serving path, a persistent store, or a process boundary; a change that alters the build configuration; a change to a canonical document's subject. | the coordinator's assignment **plus** the owner's approval for the named item |
| **Level 3 — owner-controlled operations** | §3.2. | **the owner, explicitly, for the named operation, every time** |

### 3.2 What is Level 3 in this repository

Drawn from the real protected paths and the real destructive operations:

| the Level 3 operation | where it is | the rule |
|---|---|---|
| editing `CONSTITUTION.md` | the repository root | **never edited** — a proposed amendment is prepared as a separate file with the exact diff |
| editing `AGENTS.md` | the repository root | **never edited** — same rule as above |
| editing `ethics/frozen/**` | `matrix-core/src/main/java/io/matrix/ethics/frozen/` | FROZEN. `AGENTS.md` forbids editing it without an explicit RFC mandate from the owner in that session |
| editing `avro/**` | `matrix-core/src/main/resources/avro/` | the schema is frozen |
| editing `.github/workflows/**` | the repository root | frozen |
| editing `.gitignore` | the repository root | one of the eleven owner entries |
| editing `opencode.json` | the repository root | one of the eleven owner entries |
| editing `.codex/**`, `.veai/**`, the existing `.opencode/**` | the repository root | one of the eleven owner entries |
| **committing and pushing** | the repository | **no commit and no push without the owner's explicit authorisation.** Everything stays in the working tree, uncommitted, until the owner says otherwise |
| **rewriting history** | the repository | `reset`, `rebase`, `filter-branch`, force-push — never |
| **a dependency upgrade**, major version | every `build.gradle` | `AGENTS.md` forbids it without an explicit RFC |
| **the owner's databases and `data/`** | `data/` | never modified; an audit makes its own copy in a temporary directory |
| **`docs-v2/_raw/**`** | `docs-v2/_raw/` | never touched |
| **the security boundary** | `io.matrix.security.NetworkExposure`, `AuthorizationFilter`, `StartupGuard` | a change to what is exposed or what is refused is the owner's |
| **a destructive workspace operation** | `POST /v1/workbench/workspaces/{id}/load?replace=true` | the consent parameter's shape is an open owner decision (Q-W46-01) |

### 3.3 The rule that matters most

> **Do not interpret an agent's general instruction to be autonomous as permission for Level 3
> actions.**

"Be autonomous", "work independently", "you have full authority to fix this" — none of these
is a Level 3 authorisation. Level 3 requires the owner to name the operation. An agent that
acts on a general instruction and then reports the action as done has done two wrong things:
it acted without authority, and it hid that it did.

**Two real cases from this repository, and what each one should look like.**

**Case 1 — the `AGENTS.md` amendment.** `AGENTS.md` has five non-existent paths, and it is a
**protected owner entry**. The amendment is therefore **prepared, not applied**: a separate
proposal file carries the exact diff, and the file itself is untouched. `docs-v2/governance/ROADMAP.md`
records the owner approval as required for TASK-W46-3 for exactly this reason. An agent that
"helpfully" edits `AGENTS.md` has destroyed the owner's file and bypassed the owner — and
because `AGENTS.md` is one of the eleven owner entries, the damage is visible in
`git status` but may not be noticed.

**Case 2 — the writer-lock change (D4-a).** The writer lock does not resolve a symlinked path
alias, so two writers can open the same database file through different paths. The fix is
one defect fix and a test — but it lives **inside the database-ownership boundary**, which
the W46 directive protects until the owner approves. So: the agent prepares the fix and the
test, states the exact diff, and puts it in front of the owner. It does not apply it because
the change is "obviously correct". The rule is not about the size of the change; it is about
**where the change lands.**

---

## 4. The subagent protocol

### 4.1 The roles

| the role | what it does | what it does not do |
|---|---|---|
| **coordinator** | decomposes the task, assigns the bounded write scopes, reviews the final diff, owns the conclusions, decides the classification and the autonomy level | delegate the conclusion |
| **implementer** | writes code inside its assigned write scope, runs its assigned verification | widen its own scope; make an architecture decision |
| **auditor** | reads and reports; writes nothing except its report | fix what it finds |
| **researcher** | reads across the tree and answers a question; produces evidence, not changes | change anything |
| **verifier** | runs the named verification and reports the evidence, including what was **not** run | interpret the result as a pass when it is not one |

### 4.2 The coordinator owns the conclusions

**A subagent's finding is a proposal until the coordinator has checked it.** The coordinator
owns: the classification, the autonomy level, the final diff, the evidence, and the report.
The subagent's job is to produce material the coordinator can check — with the file path and
the line number attached to every claim.

### 4.3 The return structure every subagent must use

Paste this into the task brief. A subagent that returns prose instead has not reported.

```text
1. WHAT I DID            — one paragraph: the scope, the files touched, the commands run.
2. THE EVIDENCE          — every claim with its path:line. Every command with its exact
                           output line. "I did not run X" is a required item, not an omission.
3. WHAT I CHANGED        — the write scope I was given, and the files I wrote.
                           Anything outside the scope must be listed explicitly as an
                           observation, not a change.
4. WHAT I DID NOT DO      — the verification I was not allowed to run; the question I could
                           not settle; the tool that does not exist yet.
5. THE UNCERTAINTY       — what I could not establish, and why. An honest gap is worth
                           more than a confident guess.
6. THE QUESTIONS         — what needs a decision, and by whom.
7. THE NUMBERS           — every number I wrote, with its source. A number I could not
                           source is not in my report.
```

### 4.4 The rules

- **Subagents must not create competing architecture decisions.** A subagent that finds an
  architectural question **records it and stops**. It does not decide, it does not write the
  ADR, and it does not open a second document for the same subject. One subject, one place
  (`docs-v2/governance/DOCUMENT-STATUS-POLICY.md` §1).
- **Use no more than four concurrent research tasks.** `AGENTS.md`'s META-R4: a
  deep-research subagent has a >30-minute timeout, and the optimum is four waves in
  parallel, not five.
- **Do not run multiple heavy Gradle builds concurrently on the owner's machine.** One
  matrix-core test run at a time (§8). A compile may run alongside a test; a second test JVM
  may not.
- **A subagent never commits and never pushes.** Level 3, always.

### 4.5 The two rules this repository has learned the hard way

**Rule A — the write scopes must be disjoint, and the coordinator reviews the final diff.**

During the W46.0 audit, two agents edited the same file. The result was not a merge
conflict: it was **one agent's edit silently overwritten by the other's**, with both agents
reporting success. *(This is the coordinator's first-hand observation of the wave; it is not
yet written down anywhere in the repository, which is one of the reasons this protocol
exists.)* The protocol is therefore: the coordinator assigns **explicit, disjoint
file or directory prefixes**; every agent states in its return whether it stayed inside its
scope; and **the coordinator reads the final diff before it is reported**. An agent that
found an out-of-scope problem records it in section 4 of its return (above) and does not
edit.

**Rule B — the compiled tree moves under you.**

During the same audit, one worker's build **rewrote the class files while another worker was
reading them**. A second worker then found **stale `.class` files in the repository root** —
`./io/matrix/neuron/NeuralMemoryResponse.class`, `./io/matrix/neuron/NeuralTextGenerator.class`
and `./io/matrix/api/OpenAIChatResource.class` — and **3 435 stale class files in
`matrix-core/bin/`**. These are ignored by `.gitignore` (line 3, `*.class`; line 22, `bin/`),
so they never appear in `git status`, and a `-cp .` invocation resolves them **instead of**
the current build. The root copies carry a 1970 timestamp and predate their sources by
months.

The consequences, and the rules:

- **Verify that the classes you read are newer than the sources.** Compare the mtime of
  `build/classes/java/main/**` against `src/main/java/**`. If the classes are older, the
  bytecode you are reading is not the code in the tree.
- **Never resolve classes through a relative classpath.** Not `-cp .`, not
  `-cp matrix-core/bin`. Use the module's own `build/classes/java/main` and
  `build/resources/main`, or the Gradle runtime classpath.
- **A build by another agent invalidates a bytecode reading.** If you read the classes, and
  then someone builds, your reading is stale — re-read or say so.

---

## 5. The reuse rule

### 5.1 The search list

Before writing anything, search **these seven places** — in this order:

| # | what | where |
|---|---|---|
| **1** | **the work log** | `wal/SESSION_WAL.md` (local, gitignored — `/wal/` in `.gitignore` line 127) and the wave reports `docs-v2/engineering/W4*.md`, `docs-v2/waves/` |
| **2** | **the capability registry** | `docs-v2/architecture/CAPABILITY-REGISTRY.yml` (canonical, the four axes, 34 capabilities) · the reconciled evidence it was built from: `docs-v2/engineering/W46-CAPABILITY-RECONCILIATION.csv` |
| **3** | **the hypothesis register** | `docs-v2/research/HYPOTHESES-NEW.md` · `docs-v2/research/HYPOTHESES.md` |
| **4** | **the historical waves** | `docs-v2/waves/` (320 files) · `docs-v2/engineering/W4*.md` |
| **5** | **the benchmark results** | `docs-v2/operations/bench/` (the JMH result artefacts) · `docs-v2/research/BENCHMARK-REPORT-*.md` |
| **6** | **the existing tests** | `matrix-*/src/test/**` — the test is the specification of the behaviour that already exists |
| **7** | **the open and rejected decisions** | `docs-v2/governance/OPEN-QUESTIONS.md` · `docs-v2/governance/DECISION-REGISTER.md` · `docs-v2/governance/ROADMAP.md` §8 (the rejected directions) |

**A negative result is a result.** The negative-evidence index is TASK-W46-7 and does not
exist yet; until it does, the refuted findings live in the hypothesis registers and in
`docs-v2/architecture/CAPABILITY-REGISTRY.md` (the `CORRECTED` and `UNSOURCED` rows).

### 5.2 The reuse decision

The session records **one** of five, with a reason:

| the decision | when it applies |
|---|---|
| **reused** | the existing implementation and its evidence are used as they are |
| **extended** | the existing implementation is used and something is added to it — say what |
| **repeated for a stated reason** | the work is done again — say **why** the earlier result does not answer it (a different environment, a different commit, a fixed defect, a wider scope) |
| **invalidated** | the existing result no longer holds — say what superseded it and where the new evidence is |
| **new work justified** | there is nothing to reuse — say what you searched and what you found |

> **A task is not new merely because a new agent has not seen it.**

That sentence is the whole rule. The W46.0 audit exists because the previous wave's numbers
were not findable; the W44 archaeology exists because the module graph was not written down.
**Both were "new work" that had already been done.** Before you write anything, the reuse
decision is a sentence, and the sentence names what you searched.

---

## 6. The end-of-session handoff

### 6.1 The template — paste this into the final report

```text
## HANDOFF

**The task:** <the task id from the roadmap, or the coordinator's brief>
**The classification:** <class 1..12> · **risk** <low|medium|high|critical> ·
**resource** <light|medium|heavy|GPU|native-build|external-service-dependent>
**The autonomy level:** <0..3> · **the owner approval:** <yes/no — and whose>

### 1. What was done
<three sentences. What changed, in what order, and why that order.>

### 2. The evidence
| the claim | the evidence (path:line, or the command and its output) |
|---|---|
| | |

### 3. The files changed
| the file | what changed | the write scope it was in |
|---|---|---|
| | | |

### 4. The verification that was run
| the command | the result | the evidence artefact |
|---|---|---|
| | | |

### 5. The verification that was NOT run
| the command | why not | what it would have proved |
|---|---|---|
| | | |

### 6. The numbers
| the number | where it came from |
|---|---|
| | |

### 7. The reuse decision
<reused | extended | repeated for <reason> | invalidated | new work justified — and what was searched>

### 8. The open items for the coordinator
<the questions, each with what it blocks and who decides it>

### 9. The uncertainty
<what could not be established, and why>
```

### 6.2 The three prohibitions — verbatim

1. **Never report a test as green if it was not executed.**
2. **Never report a benchmark as measured if it was estimated.**
3. **Never report a capability as integrated if it has no real production path.**

**How each one is applied here:**

- A test that was **not run** is reported in §5, not in §4. The console summary is not the
  evidence — the JUnit XML is (§7.6). A skipped test is not a passing test.
- A benchmark that was **not run** is not quoted as a measurement. If a number appears in the
  report, §6 names where it came from: a run I made, with its environment, or a stored
  artefact with its path and its commit. An estimate is labelled an estimate.
- A capability with **no production path** is reported at its real level. The four axes are
  separate: **implementation**, **integration**, **verification**, **operational status**
  (`docs-v2/engineering/W46-CAPABILITY-RECONCILIATION.csv`). "Implemented" is not
  "integrated"; "integrated" is not "served"; "served" is not "operational".

---

## 7. The verification commands this repository actually has

**Every command below was checked to exist.** Where a command does **not** exist yet, it is
marked as such and the working alternative is named.

### 7.1 The JDK — export it first

```bash
export JAVA_HOME=/home/alexandr-narbaev/jdk25
export PATH="$JAVA_HOME/bin:$PATH"
```

**The system `java` is a JRE and the system `javac` is a different JDK.** Measured on this
host: `/usr/lib/jvm/java-25-openjdk-amd64/bin/java` is 25.0.4.1 and that directory holds
**only** `java jpackage keytool rmiregistry` — **no `javac`**; the `javac` on PATH is
**21.0.12.1**; the real JDK 25 with `javac 25.0.4.1` is at `/home/alexandr-narbaev/jdk25`
(`docs-v2/engineering/W46-HARDWARE-AND-RESOURCE-REPORT.md` §1). Without the export, a build
either fails or compiles with the wrong compiler.

### 7.2 The targeted test scopes

```bash
./gradlew :matrix-core:test --tests "io.matrix.<pkg>.*" --rerun-tasks
```

`AGENTS.md` §Тестирование names this form exactly. The scopes with a recorded baseline on
the snapshot commit (`docs-v2/governance/PROJECT-STATE.md` §3):

| the scope | the command | the recorded result | the commit |
|---|---|---|---|
| the cognitive kernel and the epistemic store | `./gradlew :matrix-core:test --tests "io.matrix.cognitive.*" --tests "io.matrix.knowledge.*" --rerun-tasks` | **128 classes, 910 tests, 0 failures, 0 skipped** | `a6ded233` |
| the W43 regression scope | `./gradlew :matrix-core:test --tests "io.matrix.memory.*" --tests "io.matrix.brain.*" --tests "io.matrix.api.*" --tests "io.matrix.rag.*" --rerun-tasks` | **98 classes, 1036 tests, 0 failures** | `a6ded233` |
| the brain runtime | `./gradlew :matrix-brain-runtime:test --rerun-tasks` | **119 classes, 914 tests, 0 failures, 2 skipped** | `a6ded233` |
| the API gateway | `./gradlew :matrix-api-gateway:test --rerun-tasks` | **43 classes, 240 tests, 0 failures** | `a6ded233` |

**The full `matrix-core` suite needs the owner's approval, and the reason is the memory.**
`AGENTS.md` §Тестирование: *«Полный прогон — только по согласованию (риск OOM)»*. The test
JVM asks for **16 g** (`matrix-core/build.gradle:119` — `maxHeapSize = '16g'`; line 121 —
`jvmArgs '-Xmx16g'`) against **38–40 GiB available**; two concurrent matrix-core test JVMs
plus two Gradle daemons would exceed `MemAvailable`. The full suite is **TASK-W46-11** and
is blocked on the owner's approval and a resource estimate.

### 7.3 The gates

```bash
bash scripts/quality-gate.sh            # the literal baseline: 271 candidates at a baseline of 271
bash scripts/security-boundary-check.sh # 0 violations
bash scripts/formal-gate.sh             # the TLA+ specifications under formal/
bash scripts/validate-docs.sh           # the documentation validation that exists today
```

**What each proves and what it does not:**

| the gate | it proves | it does **not** prove |
|---|---|---|
| `quality-gate.sh` | the number of numeric literals of two or more digits in Java and shell files has not **increased** over the accepted baseline of **271** (`docs-v2/quality/quality-baseline.txt`, line 1) · it also compares the five frozen zones against a historical baseline ref | that the code is correct, that the tests pass, or that the literals are *good* — it counts them. A markdown file is **outside** its scope, so this document's numbers are not in its count |
| `security-boundary-check.sh` | the network exposure boundary holds: what binds, where, and what is refused | that the application is correct |
| `formal-gate.sh` | every `.tla` specification under `formal/` parses and holds its configured properties · it **exits 1** when one fails — `-continue` is deliberately not passed | that the Java implementation matches the specification |
| `validate-docs.sh` | the required files and directories exist, and no Liquid syntax error is in the published documents | that the links resolve — the real link checker is `scripts/docs-link-check.py`, which **does not exist yet** (TASK-W46-3) |

**A gate that cannot go red is not a gate.** `scripts/w45-chain-isolation-selftest.sh` is the
negative control that proves the isolation gate can fail: it breaks the isolation on purpose
and the gate must report it. When you add a gate, you owe its negative control.

### 7.4 The journeys and the probe

```bash
bash scripts/w45-chain-isolation-check.sh      # 16 checks, 0 failed — the two chains stay apart
bash scripts/w45-chain-isolation-selftest.sh  # 6 checks — the negative control: the isolation broken on purpose
bash scripts/w45-cognitive-recovery-journey.sh # 37 checks — real SIGKILLs, the durable recovery
bash scripts/hardware-probe.sh --stdout       # print the machine profile, WRITE NOTHING
```

**`scripts/hardware-probe.sh` without `--stdout` writes into the repository.** Its default
mode emits `docs-v2/operations/HARDWARE-PROFILE.md` (`scripts/hardware-probe.sh:4,29,36`).
**Always use `--stdout` for a read-only session.** `--json` emits the machine-readable
inventory instead.

`scripts/w45-chain-isolation-reachability.py` is **not** a standalone command — it requires
`--root` and `--json` and is invoked by `w45-chain-isolation-check.sh`.

### 7.5 The other verifications that exist

```bash
bash scripts/count-tests.sh                        # the source test-method count, per module
bash scripts/verify-dormant-modules.sh [module...] # matrix-micro | matrix-fpga | matrix-ros2
./gradlew :matrix-core:jmh --rerun-tasks -PjmhBenchmark=Name   # a JMH measurement (AGENTS.md)
```

### 7.6 The test-evidence rule

> **The JUnit XML under `build/test-results/test/*.xml` is the primary evidence. The console
> summary is not.**

The XML is what the machine wrote per class; the console line is a roll-up the agent
retypes. At the snapshot commit the tree carries **98** XML files for `matrix-core`,
**119** for `matrix-brain-runtime` and **43** for `matrix-api-gateway` — matching the class
counts in the recorded baseline, which is the cross-check that makes the numbers believable.

**When you report a test result, distinguish all seven numbers:**

| the number | what it is | the trap |
|---|---|---|
| **classes discovered** | the classes the runner found | the number a filtered run reports; it is not the module's population |
| **classes executed** | the classes that actually ran | with `--tests` filters this is far below the discovered count |
| **methods executed** | the test methods that ran | `@ParameterizedTest` expands into several executions |
| **successes** | the passing executions | |
| **failures** | the failing assertions | |
| **errors** | the errors (an exception, not an assertion) | an error is not a failure and is not a success |
| **skips** | the skipped executions | **a skip is not a pass** |

**Plus the two that are easy to forget:**

- **filtered** — the executions the filter excluded. The filtered count is what a scope
  deliberately leaves out; report it, so the number is not mistaken for the total.
- **not executed** — the classes the run never reached. The **native-image classes are
  neither passing nor failing** on this host (GraalVM is absent), and the true W44 split was
  8 native-image classes / 23 failures **plus 1 ordinary JVM failure**. They are **never
  counted as passing**.

> **A baseline is commit-specific. A stale count is not a baseline.**

Every baseline names its commit. The per-scope registry is
`docs-v2/engineering/TEST-CATALOGUE.yml` (W46.1). **Do not compare a number from one commit
against a number from another and call the difference a regression.** Compare like with like,
or re-run.

---

## 8. The resource discipline

Every number below is measured or a conservative default, and the class is stated. Source:
`docs-v2/engineering/W46-HARDWARE-AND-RESOURCE-REPORT.md` §1 and §3.

> **The canonical budget is `docs-v2/operations/RESOURCE-BUDGETS.md`** (`CANONICAL`, measured
> on 2026-11-04 17:57–18:04). **This section is the short form an agent needs at the moment
> it decides whether to run something** — it does not replace the budget document, and where
> the two differ, the budget document wins for its subject.

| the resource | the value | the class | the evidence |
|---|---|---|---|
| **the machine** | 32 logical CPUs (16 cores × 2 threads), 59.5 GiB RAM, **38–40 GiB available**, 37.8 GiB swap unused, 198 GiB free on `/` | measured (the availability is a moving sample: it moved 38.2 → 40.3 → 38.7 GiB within five minutes while another agent was working) | the report §1 |
| **one matrix-core test run at a time** | the test JVM asks for **16 g**; two test JVMs (32 g) plus two Gradle daemons (8.2 g) exceed `MemAvailable` | conservative default | `matrix-core/build.gradle:119,121`; the report §3 |
| **never two heavy builds in one module** | W45.1 produced **16 spurious failures** (`NoClassDefFoundError` / `ClassNotFoundException`) from exactly that; the W43 scope then passed cleanly on a solo re-run — **182 classes, 1563 tests, 0 failures**. It looked like a regression and was not one | measured (a failure mode) | `docs-v2/governance/RISK-REGISTER.md` R-W46-12; `docs-v2/operations/RESOURCE-BUDGETS.md` §3.1 |
| **the Gradle daemon** | `-Xmx4g` (`gradle.properties:2`); the measured peak RSS during a compile was **4.08 GiB**; **no** `workers.max`, **no** `maxParallelForks` anywhere in the repository | measured | `gradle.properties`; the report §1, §5 |
| **the `/tmp` quota** | a tmpfs with a **hard 30 GiB quota** | measured | `df -h /tmp`; the report §1 |
| **the ONNX Runtime leak** | **583 MB per JVM** extracted into `java.io.tmpdir` and never cleaned. The W46.0 probe found **two orphan directories of 583 MB each** still sitting in `/tmp` from earlier sessions that day. `matrix-core/build.gradle` redirects the **test** JVM's `java.io.tmpdir` to `data/tmp`; **the launcher and the journey scripts do not** | measured | the report §3 |
| **the repository scratch** | `data/tmp` under **5 GiB** | conservative default | the report §3 |
| **the free-disk floor** | **30 GiB** on `/` — the build directories alone are 7.5 GB and `models/` is 14 GB | conservative default | the report §3 |
| **the native image** | **impossible on this host** — no `native-image`, no GraalVM or Mandrel directory, and the mandrel builder image is not in the local Docker | measured (an absence) | the report §1, §3 |
| **the GPU** | an RTX 5070 Ti with 12 227 MiB VRAM, **compiled-in** CUDA and TensorRT providers, and a measured **12.6×** for a transformer forward pass — **provided the launcher exports `LD_LIBRARY_PATH`**. The Java path today silently uses the CPU, and the diagnostic that reports the active provider is a **hardcoded string** | measured | the report §6 |

**The rules that follow from it:**

1. **One matrix-core test run at a time.** A compile may run alongside a test. A second test
   JVM may not.
2. **Never two heavy Gradle builds in one module.** The 16 spurious failures are the price.
3. **Watch `/tmp`.** If a session loads an ONNX model, the 583 MB lands in `java.io.tmpdir`.
   The quota is 30 GiB and it is **hard**. The launcher and the journey scripts do not
   redirect it — check `df -h /tmp` after a model-loading session, and clean up your own
   artefacts.
4. **The native-image classes are not counted as passing.** GraalVM is absent; a native
   build cannot be run here, and a class that needs it is neither passing nor failing.
5. **The Gradle build cost is incremental, not cold.** The measured **6.29 s** and the
   4.08 GiB peak are for **one incremental compile with a warm daemon**. The cold
   `--rerun-tasks` cost of ~3 000 sources and the peak RSS of a matrix-core **test** JVM
   were **not measured** — the second is the OOM-risk action the owner gates.

---

## 9. What this protocol does NOT do

1. **It does not replace the Constitution or `AGENTS.md`.** Those two are **normative** and
   they bind. This document is subordinate: where it contradicts them, they win and this
   document is wrong. Nothing here authorises anything they prohibit.
2. **It does not authorise anything the owner has not.** Every Level 3 operation needs the
   owner's explicit word, for the named operation, at the time. A general instruction to be
   autonomous is not that word (§3.3).
3. **It does not make a capability served.** Implementing a class does not put it on a
   serving path. The distance between "implemented" and "served" is the single most
   important fact about this repository, and it is measured in
   `docs-v2/architecture/CAPABILITY-REGISTRY.yml` on four separate axes. This document
   governs *how an agent works*; it says nothing about *what MATRIX can do*.
4. **It does not create a framework.** No new plugin, no new orchestrator, no new agent
   runtime, no new database, no new external service for the project's metadata. The W46
   wave's own constraint applies to this document: the tools are plain scripts, they are
   deterministic, they need no network, and they have a report-only mode.
5. **It does not replace the wave records.** `docs-v2/waves/` and the `W4*.md` reports are
   the evidence of what was done; they are **immutable**. This protocol tells a session how
   to work; it never rewrites what was already done.
6. **It is not the whole governance set.** The canonical documents of `docs-v2/governance/`
   are: `PROJECT-STATE.md` (the state), `ROADMAP.md` (the plan), `DECISION-REGISTER.md` (the
   decisions), `OPEN-QUESTIONS.md` (the open questions), `RISK-REGISTER.md` (the risks),
   `DOCUMENT-STATUS-POLICY.md` (the status vocabulary) and this file. **If two of them
   disagree about their own subject, the one that owns the subject wins**
   (`docs-v2/governance/PROJECT-STATE.md` §7).

---

**The one-line version:** *read `PROJECT-STATE.md` and `ROADMAP.md`, classify the task, check
the autonomy level, stay inside your write scope, verify with the commands in §7, and write
the handoff in §6 — including what you did not run.*
