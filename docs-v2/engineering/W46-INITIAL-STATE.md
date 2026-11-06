# W46.0 — INITIAL STATE

**The state of the repository at the start of the W46 consolidation wave.**
**Captured:** 2026-11-04, before any W46 change. **Nothing was fetched, pulled, rebased,
reset, cleaned, switched, stashed or merged.** The commands and their exact output are the
evidence; this document records them.

---

## 1. The discrepancy the directive asked to resolve

The W46 directive states: *"The latest GitHub state observed before this wave: `509d23ba…`
This commit contains W45.1. The latest W45.1 report says that the work remained local and
uncommitted. The GitHub branch now contains a commit whose message describes W45.1
completion."*

**Resolved from the actual repository: the premise is one wave out of date.**

| | |
|---|---|
| `git rev-parse HEAD` | `a6ded23338e069b8bc8a3744c234be5a03995f43` |
| `git rev-parse origin/develop` | `a6ded23338e069b8bc8a3744c234be5a03995f43` |
| the divergence | **none** — the local branch and the remote branch are the same commit |

The commit `a6ded233` is **W45.2**, not W45.1:

```
a6ded233 (HEAD -> develop, origin/develop) WAL: W45.2 the owner's architecture decisions —
    a named entry point per application, an isolated chain boundary, a fail-closed reload
    and one writer per database
509d23ba WAL: W45.1 crash consistency — a request survives process death, and an
    adjudication survives a repeated observation
ae6c017b WAL: RECON-W44.0 repository archaeology — the bytecode-level map of what MATRIX
    actually is
```

**What this changes for W46.** The W46 directive was written against the state after W45.1.
Between then and now, a whole wave ran: **W45.2 implemented the four architectural decisions
D1–D4 that Part IV of the directive describes as "proposed target decisions … marked
`OWNER_APPROVAL_PENDING`"**. They are not pending — the owner answered the questionnaire
twice (the second answer took all four recommended options) and the wave was committed and
pushed as `a6ded233`.

The decision packet (`W46-OWNER-DECISION-PACKET.md`) therefore records each of D1–D4 as
**approved and implemented**, with the exact residual gaps against the D3 and D4 target
contracts. That is the honest reading of the repository, and the directive's instruction to
mark them pending is superseded by the owner's own answer.

---

## 2. The repository identity

| | |
|---|---|
| the repository | `AlexanderNarbaev/agi` |
| the branch | `develop` — **not switched** during the wave |
| HEAD | `a6ded23338e069b8bc8a3744c234be5a03995f43` |
| `origin/develop` | `a6ded23338e069b8bc8a3744c234be5a03995f43` — identical |
| the remotes | `origin` → `github.com:AlexanderNarbaev/agi.git` (the credential is in the git configuration and is **not** reproduced here) |
| the local branches | `develop` only, plus the remote-tracking refs; **no branch was deleted** |
| the divergence from `develop` | none — there is no second local branch to diverge |

---

## 3. The working tree — the owner's eleven entries

`git status --porcelain=v2` at the start of W46:

```
1 .M N... 100644 100644 100644 966fd11b... .gitignore
1 .M N... 100644 100644 100644 621309fa... .opencode/todo.md
1 .M N... 100644 100644 100644 04114f38... .opencode/w37-plan.md
1 .M N... 100644 100644 100644 927d3415... AGENTS.md
1 .M N... 100644 100644 100644 1bef9037... opencode.json
1 .D N... 100644 100644 000000 4e1d3b06... opencode.json.bak
1 .M N... 100644 100644 100644 2090d55c... wal/SESSION_WAL.md
? .codex/
? .opencode/goals/
? .opencode/opencode-swarm.json
? .opencode/tmp/
? .veai/
```

**Ten of the eleven are the owner's own work** — they are exactly the entries the previous
waves were told to preserve, and they are **protected** by the W46 directive
(`AGENTS.md`, `opencode.json`, `.gitignore`, `.codex/**`, `.veai/**`, the existing
`.opencode/**` content). The eleventh, `wal/SESSION_WAL.md`, is the **local journal** written
at the end of W45.2: the wave's own entry. The directory `wal/` is excluded by the
repository's own `.gitignore` (`/wal/`), so that file is tracked from history but ignored for
new content.

**What was NOT touched:** `git diff` and `git diff --cached` are empty for every path except
`wal/SESSION_WAL.md`, which carries the W45.2 journal entry and is deliberately not staged.

### The ignored paths that hold owner data or configuration

| path | what it is | the rule |
|---|---|---|
| `.codex/` | the owner's agent state | **protected** — never read into a commit, never modified |
| `.veai/` | the owner's agent state | **protected** |
| `.opencode/tmp/`, `.opencode/goals/` | the owner's working state | **protected** |
| `wal/` | the local journal (ignored by `/wal/`) | read-only for this wave |
| `data/` | the databases of the running system | **never modified** |

---

## 4. The environment

| | |
|---|---|
| the operating system | Ubuntu 26.04.1, Linux 7.0.0-42 (WSL2-compatible) |
| the JDK | `/home/alexandr-narbaev/jdk25` — **the system `java` is a JRE**, so `JAVA_HOME` must be exported |
| the build | Gradle 9.6.0 through the wrapper |
| the JDK requirement | Java 25 |
| the harness | DeepSeek Harness; the team roster holds 8 members, of which 4 were used for the W46.0 audit |

---

## 5. What the previous wave left behind

The wave that produced `a6ded233` (W45.2) is the reference for the state of the code:

| what | the evidence |
|---|---|
| the knowledge schema | **v19** (`KnowledgeStore.SCHEMA_VERSION = 19`, `KnowledgeStore.java:90`) |
| the cognitive schema | **v4** (`CognitiveStore.SCHEMA_VERSION = 4`, `CognitiveStore.java:97`) |
| the database | **one** SQLite file, `data/knowledge/workbench.sqlite` — the epistemic store and the cognitive store open the **same** file |
| the writer guard | `WriterLock` — one writer **process** per file, enforced at start-up |
| the chain boundary | `scripts/w45-chain-isolation-check.sh` — 16 checks, and a negative control that proves it can go red |
| the workspace reload | fails closed without `?replace=true`; takes a snapshot in the same transaction; restores through a new endpoint |
| the canonical entry points | `io.matrix.api.CanonicalEntryPoint` — a class, checked by tests |
| the last executed test evidence | cognitive+knowledge **128 classes / 910 tests / 0 failures**; the W43 scope **98 / 1036 / 0**; brain-runtime **119 / 914 / 0 / 2 skipped**; gateway **43 / 240 / 0**; the journey **37 checks**; the isolation gate **16 checks**; the quality gate **271 at the baseline of 271** |

**The full `matrix-core` suite was not run in W45.2** and **must not be run** to satisfy the
documentation consistency of W46 (the directive says so explicitly). The existing JUnit XML
and the wave reports are the evidence base.

---

## 6. The protection rules this wave works under

- **No commit and no push during W46** unless the owner explicitly authorises it after the
  final report. Everything this wave produces therefore stays in the working tree, uncommitted,
  until the owner says otherwise.
- **No production code change in W46.0.** This phase is read-only apart from the ten audit
  documents.
- `CONSTITUTION.md`, `AGENTS.md`, `ethics/frozen/**`, `avro/**`, `.github/workflows/**`,
  `.gitignore`, `opencode.json`, `.codex/**`, `.veai/**` and the existing `.opencode/**` are
  **not edited**; proposed amendments are prepared as separate files with the exact diff.
- `docs-v2/_raw/` is **not touched**.
- The owner's databases are **not touched**: every audit that needed a database made its own
  in a temporary directory.
