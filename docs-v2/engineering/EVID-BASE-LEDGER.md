# EVID-BASE — the initiative ledger

**Status:** `ACTIVE` · **Date:** 2026-11-14 · **Branch:** `feature/evid-base-evidence-to-action`
**What this is:** the persistent evidence ledger of the EVID-BASE & Evidence-to-Action
initiative. Each stage records the command that produced its number and the artifact that
holds it, so nothing in this initiative depends on an agent's chat memory.

---

## Stage 0 — baseline (done, `8050de55`)

| Fact | Value | Command |
|---|---|---|
| `origin/main` | `1dc01537` · tree `da7edd36` | `git rev-parse origin/main origin/main^{tree}` |
| `origin/develop` | `343299b1` · tree `da7edd36` | `git rev-parse origin/develop origin/develop^{tree}` |
| `main` ancestor of `develop` | yes | `git merge-base --is-ancestor origin/main origin/develop` |
| ahead / behind | `0 444` | `git rev-list --left-right --count origin/main...origin/develop` |
| hardware | 32 logical CPUs · 59 GiB RAM · JDK 25.0.5 · 186 GiB free | `nproc`, `free -g`, `java -version`, `df -h` |
| evaluation | **18/29**, the documented eleven failing ids | `python3 scripts/w51-epistemic-eval.py` |

The artifact is `docs-v2/engineering/EVID-BASE-BASELINE.json`. The failing set reproduced
exactly (`A-02, A-04, A-05, A-06, F-02, M-01, M-03, P-01, P-02, R-04, R-05`), so the E2
workstream starts from a verified baseline rather than a quoted one.

## Stage 1 — canonical corrections (in progress)

### 1a. The verified contradictions, corrected (`1687de91`)

| Document | Contradiction | Correction |
|---|---|---|
| `PROJECT-STATE.md` | the header said pull request #50 was open and the release pending; the snapshot named `0bd5b574`, `origin/main` `c4cee8ab`, "442 ahead" | the header states the release (PR #50 `bcdd1a0a`, #51 `c4cee8ab`, #52 `1dc01537`); the rows name `origin/develop 343299b1`, `origin/main 1dc01537`, `0 444` |
| `PROJECT-STATE.md` | the "active wave" row said W53 awaited the release decision | the row says W53 is released and the active cycle is the EVID-BASE initiative |
| `ROADMAP.md` | "the reconciliation … waits for the owner's word"; the rehearsal described the merge as future | the reconciliation is recorded as done (`e41cd3c1`, `0bd5b574`, `097ffeec`, `1dc01537`, `343299b1`) and the rehearsal's prediction recorded as held |
| `RUNTIME-TOPOLOGY.md` | "They do not meet" with no crossing | no shared data ownership **and** exactly one governed crossing (the bridge) |
| `W53-STATUS.md`, `W53-AUDIT.md` | top-level status `ACTIVE` | `RELEASED`; the wave narrative preserved; a release addendum (§12) added |
| `README.md`, `README.ru.md` | §4 "two applications that do not meet" | §4 "two applications, one governed crossing" + the bridge paragraph |
| `RISK-REGISTER.md` | R-W46-03 cited only the old state | evidence enriched: W53's `04b7c5de` fixed a *different* guard (`DistributedTaskResource`), `WriterLock` still admits aliases, the risk **stays OPEN** |

`scripts/validate-project-state.py --strict` reports **0 defects** after the corrections
(one snapshot-lag observation, by the document's own convention).

### 1b. The consistency check (this commit)

`scripts/validate-current-state.py` — report-only, deterministic, offline. It cross-checks a
small set of invariants between the current-state documents and facts already in the tree:

| Check | The contradiction it catches | The repository fact it compares against |
|---|---|---|
| `RELEASE_CLAIM` | a current-state document says a pull request is open / the release is not done | the main line carries that pull request's squash (`(#N)` in the commit subjects) |
| `TOPOLOGY_CROSSING` | the topology says the chains do not meet | the bridge contract file exists in the tree |
| `STALE_EVIDENCE` | an OPEN register entry cites a file or line that no longer resolves | the file exists (by path, or by a unique basename) and has that many lines |

Usage:

    python3 scripts/validate-current-state.py            # report-only
    python3 scripts/validate-current-state.py --strict   # exit 1 on a contradiction
    python3 scripts/validate-current-state.py --json /tmp/state.json

The negative control is `scripts/validate-current-state-selftest.sh`: it plants a stale
release claim, a topology denial, an unresolved citation and a line beyond EOF into
temporary copies and requires the checker to notice all four, stay silent on the clean copy,
and exit 1/0 correctly in strict mode. It also runs the checker on the real repository and
requires 0 contradictions.

**What the checker deliberately does not do:** it does not parse prose for meaning, does not
check network state (the main-line squash is read from local git), does not rewrite any
document, and does not treat a historical document as a current snapshot — it reads only the
documents named as current state.

## Stages B1/B2/B3 — not started

E2 (retrieval quality), E1 (rule-learning evidence base) and Evidence-to-Action remain
ahead. Their baselines, hypotheses and acceptance criteria are fixed in the directive and
will be recorded here when each stage begins, with the command that produced each number.
