---
name: matrix-session-state
description: Establish where the MATRIX project actually is, and close a session with a verifiable handoff. Use at the start of a task (which wave, which rules are in force, which owner questions block it) or at the end (what was done, what was measured, what is still open). The bootstrap mode is read-only; the handoff mode writes one note and nothing normative.
license: project
metadata:
  version: "1.0.0"
  owner: coordinator
  modes: [bootstrap, handoff]
  authority: docs-v2/governance/AGENT-OPERATING-PROTOCOL.md
---

# matrix-session-state

Two modes over one reading set. **Bootstrap** says where the project is; **handoff** says
where it was left. The reading set is carried once, in `references/reading-set.md`, and the
handoff shape is `docs-v2/governance/SESSION-HANDOFF-TEMPLATE.md`. Nothing here is a copy of
the repository: every fact is resolved from the live tree at run time.

## When to use

- **bootstrap** — at the start of any task that touches the rules, the state or the
  registers. It answers: which wave, which rules are in force, which owner entries the task
  touches, which open owner questions block it.
- **handoff** — at the end of a task, when the next session must be able to resume from the
  record alone.

## Mode: bootstrap

1. Read the five ordinary files in the order `references/reading-set.md` gives. Do not jump
   to the wave report: the wave report states what is **planned**, the operating protocol
   states what is **allowed**.
2. Read `docs-v2/governance/PROJECT-STATE.md` — the branch, the commit, the wave, the test
   baseline, the pending owner decisions, the next work.
3. Read `docs-v2/governance/ROADMAP.md` and find the task's row. It names the risk, the
   resource class, the acceptance criteria, the required evidence and the test scope. **If
   the row names a bounded write scope, that scope is the boundary.**
4. Read `docs-v2/governance/OPEN-QUESTIONS.md`. If the task touches a pending owner
   decision, the task is **blocked** until the owner answers; the competing explanations and
   the resolving experiment for each question are in
   `docs-v2/engineering/W46-OPEN-QUESTIONS.csv`.
5. Compare `git status --porcelain=v2` with the eleven owner entries listed in
   `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §1.3. **Record the set; do not restore
   it.** The owner works between sessions, and a different set is not a failure.
6. Answer three sub-questions before the first build command: the **test scope** (the task's
   row names it, and `docs-v2/engineering/TEST-CATALOGUE.yml` is the per-scope registry),
   the **resource availability** (one `matrix-core` test run at a time — ask the
   coordinator), and the **bounded task** (what you may change and what you may not).

**Output — the state card:**

| | |
|---|---|
| the wave and the task id | |
| the branch and the commit | |
| the rules in force | |
| the owner entries this task touches | |
| the open owner questions that block it | |
| the test scope and the resource class | |

## Mode: handoff

1. Take the shape from `docs-v2/governance/SESSION-HANDOFF-TEMPLATE.md`. Do not invent one.
2. Fill the identity block from `git rev-parse HEAD` and `git status --porcelain=v2`.
3. List the files changed, each with the reason.
4. Put **the exact command** beside every number, and the path of the artefact that holds
   it. A number without a re-derivable command is not published.
5. Record the tests that were **not** run, and why.
6. Say plainly what the session's work does **not** establish.
7. Put it in the wave record under `docs-v2/engineering/`. The local journal
   `wal/SESSION_WAL.md` is a running log, not a wave record; a handoff there alone is not
   enough.

**Output:** the filled handoff section, and the exact diff.

## The safety rules

1. **Never modify the eleven owner entries** — the list in
   `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §1.3. Not to fix them, not to restore
   them, not to stage them.
2. **Never write a normative file in handoff mode.** The handoff is a note; a normative
   file belongs to the wave that owns it.
3. **Never run the `matrix-core` suite.** The full run is owner-held; a targeted suite runs
   only when the task says so.
4. **No git write command.** No `commit`, `push`, `stash`, `reset`, `checkout`, `switch`,
   `clean`.
5. **A decision is never inferred from a precedent.** The register records decisions, it
   does not create them — `docs-v2/governance/DECISION-REGISTER.md` holds what was decided
   and by whom.
6. **If the reading set contradicts itself, report the contradiction; do not pick a
   winner.** The conflicts live in `docs-v2/governance/RISK-REGISTER.md`.

## The references

- `references/reading-set.md` — the five ordinary files, the eleven owner entries, the local
  journal, and the order to read them.
- `references/handoff-protocol.md` — which template to fill, and the rules for a claim that
  names its command.
