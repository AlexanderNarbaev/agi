# MATRIX — the checkout consolidation report

**Status:** `CANONICAL` · **Date:** 2026-11-23/24 · **Scope:** two local checkouts → one

**The decision:** `~/Projects/agi` is the canonical repository. The Codex checkout
`~/Projects/matrix-codex-research` is **quarantined**, not deleted, and its path no longer
exists. Both tools now work against the same repository, each in its own **disposable
worktree**.

---

## 1. The outcome in one paragraph

The audit found that the Codex checkout was **not a second repository** — it was the same
GitHub repository at a commit **five commits behind**, with **zero** objects of its own, and
it owned **ten registered worktrees** whose working directories were sitting *inside*
`~/Projects/agi`. Those ten worktrees are the real consolidation problem: they were
registered in one repository and lived in the other, so renaming either checkout alone
would have broken them. They were re-registered in the canonical repository, each verified
to resolve to the correct commit with a clean tree. The one artifact that existed **solely**
in the Codex checkout — the owner's own Codex commission — is now in Git.

---

## 2. The audit's findings

### 2.1 Remote and local heads

| | |
|---|---|
| remote `origin/develop` | **`3765280c`** — *"the project state names round 24 — the project is portable, and the review gate changed (#81)"* |
| `~/Projects/agi` | `3765280c` — **identical to the remote** |
| the Codex checkout | `f4ad32d8` — **five commits behind**, the commit before PR #77 |

Both checkouts have the **same** remote (`https://github.com/AlexanderNarbaev/agi.git`).

### 2.2 What the Codex checkout actually contained

| | finding |
|---|---|
| local-only commits | **none** — `git log origin/develop..HEAD` is empty |
| objects the remote lacks | **zero** — the two object sets are identical |
| commits reachable only from the reflog | **zero** |
| stash | empty |
| unreachable objects | 3 blobs, all **older versions of files that exist in `develop`** |
| working tree | **clean** — identical to its own `HEAD` |
| tracked content | a **strict subset** of `develop`: every file it has is an older version of a file `develop` has |
| untracked files | **exactly one** — the owner's Codex commission |
| ignored files | **none** |
| branches | 6, of which **5** are identical to their remote counterparts; `fix/crj-01-scoring-recovery` is *older* locally than remotely, and its local-only file (`Crj01ProvenanceTest.java`) **is in `develop`** — the local branch is the older one, not the newer one |

**So nothing in the Codex checkout was lost by removing it.** Every byte of it is either in
`develop` on GitHub or in the one untracked file that is now in Git.

### 2.3 The real problem — ten worktrees registered in the wrong repository

The Codex checkout owned **ten registered worktrees**, and **every one of their working
directories was inside `~/Projects/agi`**:

| worktree | head | state |
|---|---|---|
| `matrix-crj-governance-20261122` | `e0d004ad` | clean |
| `matrix-crj-negative-20261123` | `84b165ec` | clean |
| `matrix-revision-measurement-20261123` | `c1c0f5d7` | clean |
| `matrix-source-revision-20261123` | `f68ec498` | clean |
| `matrix-revision-evaluator.teFBiFtH/baseline` | `3fcbfdc1` | clean |
| `…/candidate-green-03ca971` | `03ca971c` | clean |
| `…/candidate-green-2ffa2cb` | `2ffa2cb8` | clean |
| `…/candidate-green-916a471` | `916a4719` | clean |
| `…/candidate-green-de3cb24` | `de3cb24caa24` | clean |
| `…/candidate-green-f68ec49` | `f68ec498` | clean |

Each worktree's `.git` file pointed into the **Codex** repository's administrative
directory. Renaming the Codex checkout alone would have left ten worktrees pointing at a
path that no longer exists — and the evaluator's frozen cases live in them.

### 2.4 The other checkout's shared state

| artifact | state |
|---|---|
| `.sinv_session` (a session credential) | **byte-identical** in both checkouts — already in `agi` |
| `.codex/config.toml` (the MCP server) | **byte-identical** in both |
| `SESSION.md`, `wal/SESSION_WAL.md`, `wal/GLOBAL_WAL.md` | **byte-identical** in both |
| `.agents/`, `.opencode/`, `.veai/` | present in both |

**Nothing was unique to the Codex checkout except the commission.**

---

## 3. What was done

### 3.1 The unique artifact was preserved

`MATRIX_CODEX_CLEAN_RESEARCH_COMMISSION_2026-11-22.md` — the owner's 252-line commission
to a new Codex session — was the **only** file that existed solely in the Codex checkout. It
is now:

- in **Git**, at `OpeAi/MATRIX_CODEX_CLEAN_RESEARCH_COMMISSION_2026-11-22.md`, beside the
  owner's other directives (digest `5ca27e9f0666d91df0725bdfe9313941315c9698f510fa24de938fe51a34041d`);
- in the **backup**, at `~/Projects/matrix-consolidation-backup/owner-commissions/`.

### 3.2 The ten worktrees were re-registered in the canonical repository

Each registration was copied into `~/Projects/agi/.git/worktrees/`, and each worktree's
`.git` file was repointed at it. The registrations are **relocatable** by construction: the
`commondir` entry is relative (`../..`), so it resolves against the new repository
automatically, while `HEAD` stays in the worktree's own administrative directory and the
**refs live in the common directory** — which is where a linked worktree's refs actually are.

The four worktrees attached to a branch needed their **branch refs** created in the
canonical repository as well; they were created at the commits the Codex repository held,
which were confirmed against `origin/` before being written.

**All ten were then verified**: correct `HEAD`, **clean** working tree, `git` functional.

### 3.3 Both tools were pointed at the canonical repository

| | before | after |
|---|---|---|
| **DeepSeek Harness** | the session's working directory is `~/Projects/agi` | **unchanged** — already canonical |
| **Codex** — project roots | `[matrix-codex-research, agi]` | **`[agi, matrix-worktrees/codex-writer]`** |
| **Codex** — trust | both paths trusted | `agi`, `matrix-worktrees/codex-writer` and `matrix-worktrees/dsh-writer` trusted; the old path's entry **kept**, so nothing is silently untrusted |

### 3.4 Two disposable worktrees for concurrent writers

```
~/Projects/matrix-worktrees/codex-writer    3765280c (detached HEAD)
~/Projects/matrix-worktrees/dsh-writer      3765280c (detached HEAD)
```

Both are **linked worktrees of the canonical repository**, so they share its object store
and its refs, and a commit in one is visible in the other. Neither is a clone: there is
**one** repository, and two writing surfaces.

---

## 4. Two defects the consolidation exposed — and their fixes

Both were found by *using* the configuration the owner asked for, not by reading the code.

### 4.1 The freeze record could not be taken in a worktree — FIXED

`Crj01FreezeRecord.headSha` read `.git/HEAD` as a **directory**. In a linked worktree
`.git` is a **file** naming the worktree's administrative directory, and the refs live in
the **common** directory. So the first run of the freeze record from a disposable worktree
recorded **no commit at all**, and the record's most important field was silently empty:

```
expected: "3765280c4222ec3e0cbaf9640a75560175904c48"
 but was: "UNKNOWN"
```

The resolution now handles all three shapes — `.git` as a directory, `.git` as a file with a
`commondir`, and a detached `HEAD`. The new test is a **negative control**: it builds the
exact shape a real linked worktree has, and the old resolver answered `"UNKNOWN"` for it —
confirmed **RED before** the fix and **GREEN after**. The `green-f68ec49` run's record is
unaffected, because it was taken in the main checkout.

**This is a test instrument, not production code.** No production path was touched.

### 4.2 The retained evidence was incomplete — FIXED

The retained evidence's **build logs** were excluded by the repository's `*.log` rule, which
exists for the machine's own logs. So **32 evidence files** were in the working tree and
**not in Git** — including the `gradle.log` of **every** retained run, whose digests are
**published** in the `CHECKSUMS` manifests. A reader on another machine would have found
manifests naming files that were not there.

The `.gitignore` now excepts `docs-v2/engineering/evidence/**/*.log` explicitly: **these
logs are the record of the run that produced the result, not machine state.** With the
exception, every manifest that names only retained files **verifies in full** in a clean
checkout.

### 4.3 And every manifest now says what it is relative to

`MANIFESTS.md` in each evidence directory classifies every manifest as **RETAINED** (every
named file is in the repository, with the exact command), **HELD BACK** (the digests are
published; the evaluator's cases and gold are not, because the mandate forbids publishing
hidden gold), or **POINT-IN-TIME** (it records a payload as it was proposed — of the nine
files in `PROPOSED-PAYLOAD.sha256`, seven still match `develop` byte for byte, one has
changed since, and one never landed).

---

## 5. Verification

| what | result |
|---|---|
| remote `develop` | `3765280c`, and the canonical repo is **identical** to it |
| the ten re-registered worktrees | **10 of 10** resolve to the correct commit, **clean** tree, `git` functional |
| **Git operations** | fetch, worktree list, branch refs, status — all correct in the canonical repo and in both disposable worktrees |
| **test execution** | the CRJ-01 suite is **green** in the Codex writer's disposable worktree (`BUILD SUCCESSFUL`, 42 s); the wide regression in the canonical repository is **260 suites / 1476 tests / 0 failures / 0 errors / 1 skipped** (12 min 25 s, alone under the lock) |
| **the retained evidence** | every manifest that names only retained files verifies **in a clean checkout**; the premise set verifies **11 of 11**, the runner **2 of 2**, the five green runs **9 of 9** each, the baseline **7 of 7** |
| **MiniMax execution** | **verified** — this session runs on `minimax/MiniMax-M3.1-Flash`, the route the owner configured as the default |
| **Codex** | its project roots and trust entries now name the canonical repository and the disposable worktrees; the state file was rewritten with the **same 850 keys** — none lost, none added |
| the quarantine | `~/Projects/matrix-codex-research.quarantine-20261123` — a **valid, inert** repository at `f4ad32d8`, with the owner's commission still in it |

---

## 6. What is preserved, what can be regenerated, what remains unique

### 6.1 Preserved — and where it is

| | where |
|---|---|
| the whole source tree and its history | the repository itself (2 749 commits on `develop`) |
| every canonical document | `docs-v2/` |
| **the owner's Codex commission** | `OpeAi/MATRIX_CODEX_CLEAN_RESEARCH_COMMISSION_2026-11-22.md` |
| the owner's other directives | `OpeAi/` (7 files) |
| both retained evidence sets, **now complete** | `docs-v2/engineering/evidence/` — with the manifests classified |
| the session's commission, checkpoints, patches and logs | `docs-v2/engineering/evidence/crj01-premise-negative/commission/` |
| the ten worktrees, their heads and their indexes | the canonical repository's `.git/worktrees/` |
| the evaluator's **held-back** cases and gold | the owner's own copy, **and** `~/Projects/matrix-consolidation-backup/evaluation-gold/` |
| the Codex checkout's administrative state | `~/Projects/matrix-consolidation-backup/codex-checkout-meta/` |

### 6.2 Can be regenerated

| | how |
|---|---|
| the build output | Gradle — but **never** with `--rerun`; delete `matrix-core/build/test-results` and re-run the filtered command |
| the disposable worktrees | `git worktree add --detach <path> origin/develop` |
| the test evidence (JUnit XML) | the filtered Gradle commands in `docs-v2/engineering/CRJ-01-DEVELOPMENT-RECORD.md` §5 |
| the evaluator's working copies | the evaluator's runner, whose command line is retained in each run's `command.txt` |
| the branches | `git fetch origin` — all six are on the remote |

### 6.3 Remains unique — **not reproducible from this machine**

| | why | where the owner holds it |
|---|---|---|
| the **evaluator's gold** and its frozen cases | the mandate forbids publishing hidden gold; a blind evaluation that publishes its answers is not blind | the evaluator's session directory, and the backup's `evaluation-gold/` |
| the **baseline run's** `FrozenPremiseJourneyTest.java` (digest `6b7c3779…`) | the evaluator **replaced** it when it applied its correction, and the original bytes were not kept anywhere on this machine | **only the digest survives** — in `evidence-baseline/RUNNER.sha256` and in the canonical document. The baseline's *result* is fully retained |
| the sessions' scratch directories (~4.7 GB) | machine state — working copies and Gradle output | on this machine; the content that matters is either merged or retained above |
| the `.sinv_session` credential | a session credential | already identical in both checkouts; **not** committed, as it must not be |

---

## 7. The deletion — **APPROVED AND EXECUTED**

The owner approved the deletion on 2026-11-24. Before it ran, four things were checked
again, because a deletion is irreversible:

| the check | the result |
|---|---|
| the files in the checkout that exist **nowhere else** | **exactly one** — the owner's Codex commission, which is in **Git** and in the **backup** |
| the commits **not published** on the remote | **zero** — the 64 commits that `--not --remotes` reports are reachable through the **45 tags**, and every one of those tags is on the remote with an **identical target** |
| the objects the checkout held that the canonical repository lacked | **two** — and neither was lost: the annotated tag object is now **in the canonical repository** (see below), and the second was an **orphan tree** left by an index operation, whose **5 477 blobs are all present** in the canonical repository |
| a process with the directory open or as its working directory | **none** |

**Then, and only then:**

```
~/Projects/matrix-codex-research.quarantine-20261123        211 MB    DELETED
```

### The one thing the deletion audit found, and fixed

**The canonical repository's local `v17.2.0-mind` tag did not match the published one.**
Its local message read *"This tag is retained unchanged because a published tag is
immutable and force-pushing a correction is denied by policy"* — but the tag had in fact
been **overwritten locally**, with a later tagger timestamp and a short note in place of
the release message. **GitHub held the original**: the full *"MATRIX v17.2.0-mind — Truthfully
Fixed"* message, with the benchmark headline, the nine fixes and the named open items.

So the local repository misrepresented a published release tag, and would have reported a
different release record than the one the owner's readers see. The local tag was
**restored to the published object** (`3ff54201…`), and it now matches the remote
**exactly**. Both messages — the published one and the replaced one — are kept in
`~/Projects/matrix-consolidation-backup/tags/`.

**The commit is unaffected:** both tags point at `a8322a12…`, and only the annotated tag's
*wrapper* differed.

### The deletion scope, as executed

```
~/Projects/matrix-codex-research.quarantine-20261123        211 MB    DELETED
```

**and nothing else.** Specifically **not** touched:

- the canonical repository `~/Projects/agi` — untouched;
- the ten worktrees, their heads and their indexes — **migrated, not deleted**;
- the sessions' scratch directories in `~/Projects/agi` (`matrix-crj-*`, `matrix-dsh-*`,
  `matrix-revision-*`, `matrix-source-*`, ~4.7 GB) — **not** in scope;
- `~/Projects/matrix-consolidation-backup/` — **not** in scope;
- `~/Projects/matrix-worktrees/` — **not** in scope;
- the evaluator's gold, the databases (`data/`, `matrix-api-gateway/data/`), and every
  session state directory — **not** in scope.

**The verification after the deletion.** The canonical repository is at `f683c32e` and
identical to the remote; **all thirteen** worktrees resolve — the ten migrated ones at
their own commits with clean trees, and the two disposable writers at the merged head; the
CRJ-01 suites are **green in both** disposable worktrees; and the retained evidence still
verifies from them (**11 of 11** and **9 of 9**).

---

## 8. The standing configuration

```
ONE canonical repository:   ~/Projects/agi          (origin/develop = f683c32e)
  ├── the main checkout    — the canonical state, the integrations, the merges
  ├── codex-writer         — a DISPOSABLE worktree, Codex writes here
  ├── dsh-writer           — a DISPOSABLE worktree, DeepSeek Harness writes here
  └── ten retained worktrees — the evaluator's cases and the session's measurements

A backup:                  ~/Projects/matrix-consolidation-backup/
```

**The second checkout is gone.** It was quarantined, audited against the canonical
repository and the remote, and deleted on the owner's approval (§7).

**The rules that follow from it:**

1. **One repository.** A second clone of MATRIX is a second set of refs and a second
   version of the truth. A *worktree* of the canonical repository is not — it shares the
   object store, the refs and the history.
2. **A disposable worktree per concurrent writer.** Two writers in one working tree corrupt
   each other's index; two worktrees of one repository cannot.
3. **Never delete a worktree to clean up** — remove it deliberately, with
   `git worktree remove`, after checking it is clean.
4. **The evaluator's gold stays with the evaluator.** The repository publishes the digests
   and the verifiable record; the answers stay with their owner.
5. **The evidence's logs are evidence.** `*.log` is machine state *except* under
   `docs-v2/engineering/evidence/`, where the log is the record of the run that produced
   the result.

---

## 9. The decision

**CONTINUE.** The consolidation is complete and verified: **one** canonical repository,
both tools configured against it with disposable worktrees, the ten worktrees migrated and
verified, the one unique artifact preserved in Git, the redundant checkout audited and
deleted on approval, and **three** real defects found and fixed by *using* the
configuration rather than reading about it.

**The owner approved the deletion on 2026-11-24**, and the standing configuration in §8 is
confirmed: the evaluator's gold stays with the evaluator, and the ten retained worktrees
stay until the evaluations that need them are closed.

**What remains open is not the consolidation.** It is the CRJ-01 programme itself: the
sealed split's author, the freezing reviewer's name, the storage bound, the review rule
that the new ruleset has now replaced, and the next capability.
