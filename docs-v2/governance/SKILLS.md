# SKILLS — the minimum useful MATRIX skill set

**Status:** RECOMMENDATION · 2026-11-04 · branch `develop` · HEAD `a6ded233`
**Kind:** a proposal. It recommends four skill packages and **creates none of them**. The
only files this wave produced are this document and
`docs-v2/governance/SKILL-INVENTORY.md`. **No skill was installed, no configuration was
changed, and `opencode.json` was read and never written** — activating a skill requires
the owner's approval (`docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §3.2).

**What this is:** the routing file, the recommended set, and the reasoning. **Every path
named in this document either exists today or is explicitly marked as a path this
recommendation would create** — the four `.agents/skills/<name>/` package directories do
not exist yet, and this wave created nothing but these two files. **What it is
not:** an activation, a change to `opencode.json`, a change to the auto-skills trigger
table, or a copy of the repository into a skill.

---

## 1. The routing file

Short on purpose. The detail lives in each package's own `references/`.

| the situation | the skill | what it reads | what it must not do |
|---|---|---|---|
| a session starts: what is the wave, what is blocked, which rules are in force | **`matrix-session-state`** (bootstrap mode) | `AGENTS.md`, `CONSTITUTION.md`, the five ordinary reading files, the nine files of `docs-v2/governance/`, the current wave record | modify any of them; run the `matrix-core` suite; treat a chat answer as a decision |
| a session ends: write the handoff | **`matrix-session-state`** (handoff mode) | the same set plus the working-tree diff, filled into `docs-v2/governance/SESSION-HANDOFF-TEMPLATE.md` | write a normative file; commit; push; leave a finding only in the chat |
| "where does X live, which chain, is it served, what is protected" | **`matrix-architecture-navigator`** | `docs-v2/architecture/**`, the module tree, `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` | edit anything; assert "served" without a `path:line`; read a document's title as its status |
| "is this number / this status true — publish it" | **`matrix-evidence`** (test mode) | the JUnit XML corpus, the W44/W45 audit CSVs, the retained test reports | **run the test suite**; publish a number no retained artefact supports; write into `docs-v2/_raw/` |
| "is this research claim supported by a primary source" | **`matrix-evidence`** (research mode) | `docs-v2/research/HYPOTHESES*.md`, the EXP reports, the primary sources | cite an aggregator as a source; leave a finding in the chat only (META-R2) |
| "do the documents still agree with each other" | **`matrix-document-integrity`** | the `docs-v2/` tree | rewrite a document to make the check pass; touch `docs-v2/_raw/**`; touch an owner entry |
| "review the codebase end to end, with evidence" | **`codebase-review-swarm`** — *existing, not a MATRIX skill* | the repository; writes `.swarm/review-v8/` artefacts | modify source files; be duplicated by another review skill |
| "decide something" | **the owner** — no skill may decide | — | a decision may never be delegated to a skill or inferred from a precedent |

---

## 2. The recommended set — four, not six

The directive names six candidate roles. **Four is the better answer**, for one reason in
each case: two of the six carry the *same* contract over the *same* documents, and one of
them turns out to be a checker rather than a writer.

| the directive's candidate | the recommendation | why |
|---|---|---|
| `matrix-session-bootstrap` + `matrix-session-handoff` | **merged into `matrix-session-state`** | both are "read the state, then act on it" over the *same* reading set: the five ordinary files, the eleven owner entries, the governance registers, the WAL journal. Two packages would carry that set twice, and the second copy would drift from the first. One package with two modes carries it once, and the handoff cannot contradict the bootstrap |
| `matrix-architecture-navigator` | **kept as one skill** | it has a distinct subject: MATRIX's own boundaries — the two serving chains, the six cognitive cycles, the module map, the frozen and protected paths. Nothing else in the ecosystem knows any of that |
| `matrix-test-evidence` + `matrix-research-evidence` | **merged into `matrix-evidence`** | the two differ in their *artefacts*, not in their *contract*: a claim is only as strong as the artefact that proves it, an artefact nobody can re-read is not evidence, and a number without a re-derivable command is not published. The artefacts differ enough to deserve two references; the contract is identical enough that one skill holds it |
| `matrix-documentation-maintainer` | **narrowed to `matrix-document-integrity`** and turned into a **checker, not a writer** | the writers already exist — the governance registers are the writers, and they were written this wave. What the audits actually found broken was never a missing document: it was a broken reference, a stale status, a duplicated identifier, a prose path that does not resolve. A writer risks creating a second source of truth; a checker cannot |

**Recommended home: `.agents/skills/<name>/`.** Three reasons, all measured:

1. `.agents/skills` is a **harness default search path** — the harness resolves
   `<projectRoot>/.dsh/skills`, then `<projectRoot>/.agents/skills`, then the user roots
   (`/home/alexandr-narbaev/.npm-global/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-skill-filesystem/lib/index.js:154-177`). Adding a package there needs **no
   configuration change at all**, so `opencode.json` — an owner entry — is never touched.
2. `.opencode/skills/` is **not** a harness search path. The 27 packages there serve the
   OpenCode plugin and the auto-skills trigger table, not the DSH harness. Putting MATRIX
   skills there would make them invisible to the harness that runs them.
3. `.agents/` does not exist yet. Creating it is a **Level 1** change — a new directory, no
   protected entry, reversible by deleting the directory — under
   `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §3.1.

---

## 3. The four packages

Each package is a directory with a `SKILL.md` and its references. **The repository is not
copied into a skill.** Every package resolves its facts from the live tree at run time.

### 3.1 `matrix-session-state`

`.agents/skills/matrix-session-state/` — `SKILL.md`, `references/reading-set.md`,
`references/handoff-protocol.md`.

**Frontmatter, exactly as it would be written:**

```yaml
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
```

| | |
|---|---|
| **inputs** | the task statement; the working tree; the five ordinary reading files; the nine governance files; the current wave record |
| **outputs** | *bootstrap*: a state card — the wave, the autonomy level for this task, the owner entries the task touches, the open owner questions that block it, the owner decisions in force. *handoff*: the end-of-session section of the wave record, in the shape of `docs-v2/governance/SESSION-HANDOFF-TEMPLATE.md` — what was done, what was measured and by which command, what is open, what is next — plus the exact diff |
| **tools** | `read`, `grep`, `glob`, `bash` (read-only git commands only), `todo_write` |
| **safety rules** | 1. Never modify the eleven owner entries (`docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §1.3). 2. Never write a normative file — the handoff is a note, not a register entry. 3. Never run the `matrix-core` suite; the full run is owner-held (OD-024). 4. No git write command: no `commit`, `push`, `stash`, `reset`, `checkout`, `switch`, `clean`. 5. A decision is never inferred from a precedent — the register records decisions, it does not create them. 6. If the reading set contradicts itself, report the contradiction; do not pick a winner |
| **references** | `references/reading-set.md` — the five ordinary files, the eleven owner entries, the local journal, and the order to read them. `references/handoff-protocol.md` — **which template to fill, and the rules for a claim that names its command**. The handoff note's shape is not invented here: it is `docs-v2/governance/SESSION-HANDOFF-TEMPLATE.md` (170 lines, `CANONICAL`, 2026-11-04), and the skill points at it rather than copying it |
| **validation command** | the shared package check, below, run from the repository root |

### 3.2 `matrix-architecture-navigator`

`.agents/skills/matrix-architecture-navigator/` — `SKILL.md`, `references/two-chains.md`,
`references/six-cycles.md`, `references/protected-paths.md`.

```yaml
---
name: matrix-architecture-navigator
description: Answer where a behaviour lives in MATRIX and whether it is served. Use when new work must be placed in the right module, when a task must decide which serving chain a change belongs to, when the six cognitive cycles must be told apart, or when a change approaches a frozen or protected path. Read-only, and every answer names the file and line it came from.
license: project
metadata:
  version: "1.0.0"
  owner: coordinator
  authority: docs-v2/architecture/ + docs-v2/governance/AGENT-OPERATING-PROTOCOL.md
---
```

| | |
|---|---|
| **inputs** | a question about a subsystem, a class or package name, or a proposed change |
| **outputs** | a placement answer: the module, the serving chain (A: `matrix-core` Quarkus workbench/epistemic app; B: `matrix-brain-runtime` / `matrix-api-gateway`), the cognitive cycle if one applies, whether the behaviour is served, which protected boundary it touches, and the `path:line` for each claim |
| **tools** | `read`, `grep`, `glob`, `bash` (read-only) |
| **safety rules** | 1. Read-only — the skill never writes. 2. It must not assert that a behaviour is *served* without a citation to the serving path. 3. It must not read a document's title as its status: the status is the explicit line, and a document can be historical and still be the best available source. 4. The two chains stay isolated: a question about one is never answered with evidence from the other. 5. `docs-v2/architecture/**` is read-only to it. 6. It does not invent a capability: if the registry says `UNKNOWN`, the answer says `UNKNOWN` |
| **references** | `references/two-chains.md` — the chains, the isolation rule, what each serves. `references/six-cycles.md` — the six cycles and how they differ. `references/protected-paths.md` — the frozen zones, the eleven owner entries, the security boundary, and the writer lock |
| **validation command** | the shared package check, below |

### 3.3 `matrix-evidence`

`.agents/skills/matrix-evidence/` — `SKILL.md`, `references/test-evidence.md`,
`references/research-evidence.md`.

```yaml
---
name: matrix-evidence
description: Reconcile a number, a status or a claim against the artefact that proves it, and say plainly what the artefact does not prove. Use before publishing any test result, capability number, or research finding — and before saying a claim is false. In test mode it reads the retained JUnit XML and the audit CSVs; in research mode it reads the hypothesis register and the EXP reports against primary sources. It never runs the test suite.
license: project
metadata:
  version: "1.0.0"
  owner: coordinator
  modes: [test, research]
  authority: AGENTS.md + docs-v2/engineering/W45-TEST-EVIDENCE-RECONCILIATION.md
---
```

| | |
|---|---|
| **inputs** | the claim or number under test; the artefact set; the command that produced it |
| **outputs** | a reconciliation: the value the artefact supports, the exact command that re-derives it, the denominator it belongs to, the residual gap, and — where the artefact cannot answer — an explicit `unverified` |
| **tools** | `read`, `grep`, `glob`, `bash` (read-only plus the documented measurement commands) |
| **safety rules** | 1. **Never run the `matrix-core` test suite.** The full run is owner-held (OD-024 / D-017); a targeted suite is run only when the task says so. 2. Never publish a number that no retained artefact supports, and never restate a CSV column as a count without re-deriving it. 3. Three denominators are three denominators: classes, executions and files are not interchangeable, and the register names which is which. 4. In research mode, every substantial claim is tied to a primary source (META-R3); an aggregator is not a source, and an unverifiable claim is marked `UNVERIFIED` rather than dropped. 5. `docs-v2/_raw/**` is never written. 6. A test failure is separated from an environment failure before either is reported |
| **references** | `references/test-evidence.md` — the artefact families, the three denominators, the failure taxonomy (native-image infrastructure versus ordinary JVM regression), and the evidence-retention rules. `references/research-evidence.md` — the hypothesis register, the EXP report shape, the preregistration rule, and the primary-source rule |
| **validation command** | the shared package check, below |

### 3.4 `matrix-document-integrity`

`.agents/skills/matrix-document-integrity/` — `SKILL.md`, `references/checklist.md`,
`references/status-vocabulary.md`.

```yaml
---
name: matrix-document-integrity
description: Check that the MATRIX document set is internally consistent — that every cross-document reference resolves, that every path a document cites exists, that the governance registers agree with each other and with the tree, and that a document's stated status matches its content. Use after a documentation change and before a wave closes. It reports defects; it does not rewrite documents.
license: project
metadata:
  version: "1.0.0"
  owner: coordinator
  authority: docs-v2/governance/DOCUMENT-STATUS-POLICY.md
---
```

| | |
|---|---|
| **inputs** | the changed documents, or the whole `docs-v2/` tree |
| **outputs** | a defect list, each item with `path:line`, its class, and its severity: broken relative link · broken anchor · missing prose-referenced file · duplicate stable identifier · a current document citing a superseded one · a status that does not match the content · a count that does not match the tree |
| **tools** | `read`, `grep`, `glob`, `bash` (read-only) |
| **safety rules** | 1. **It reports; it does not fix.** A rewrite is a separate task with its own write scope. 2. `docs-v2/_raw/**` is never read for content and never written. 3. The eleven owner entries are never modified. 4. An external link is reported `UNVERIFIED` — never fetched, because fetching is an outbound call the owner has not authorised for a checker. 5. A file's absence is a finding only when a document claims it exists — not when it simply does not. 6. It does not change a status line; it reports that the status and the content disagree |
| **references** | `references/checklist.md` — the seven defect classes and the command that finds each. `references/status-vocabulary.md` — the status vocabulary of the document-status policy and the rule that a status is an explicit line, not a title |
| **validation command** | the shared package check, below |

### The shared package check

One command validates all four packages. It proves three things: the frontmatter parses
and carries `name` and `description`; the directory name equals the frontmatter `name`;
and every path the package cites — in `references/` and in the repository — exists.

```bash
# usage: bash -c 'check <skill-dir>'
python3 - "$1" <<'PY'
import re, sys
from pathlib import Path

root = Path(sys.argv[1])
text = (root / "SKILL.md").read_text(encoding="utf-8")
assert text.startswith("---"), "no frontmatter"
fm = text[3:text.find("\n---", 3)]
name = re.search(r"^name:\s*(.+)$", fm, re.M).group(1).strip()
assert re.search(r"^description:\s*\S", fm, re.M), "no description"
assert name == root.name, f"name {name!r} != directory {root.name!r}"
cited = re.findall(r"`([^`\n]+)`", text)
missing = []
for c in cited:
    if c.startswith(("references/", "assets/", "scripts/")):
        p = root / c
    elif c.startswith(("docs-v2/", "AGENTS.md", "CONSTITUTION.md", "matrix-core/")):
        p = Path(c)
    else:
        continue
    if not p.exists():
        missing.append(c)
print("OK" if not missing else "MISSING: " + ", ".join(missing))
sys.exit(1 if missing else 0)
PY
```

Run it four times:

```bash
for s in matrix-session-state matrix-architecture-navigator matrix-evidence matrix-document-integrity; do
  bash -c 'python3 - "$1" <<PY
import re, sys
from pathlib import Path
root = Path(".agents/skills/$s")
text = (root / "SKILL.md").read_text(encoding="utf-8")
assert text.startswith("---"), "no frontmatter"
fm = text[3:text.find("\n---", 3)]
name = re.search(r"^name:\s*(.+)$", fm, re.M).group(1).strip()
assert re.search(r"^description:\s*\S", fm, re.M), "no description"
assert name == root.name, "name != directory"
missing = [c for c in re.findall(r"`([^`\n]+)`", text)
           if (c.startswith(("references/","assets/","scripts/")) and not (root/c).exists())
           or (c.startswith(("docs-v2/","AGENTS.md","CONSTITUTION.md")) and not Path(c).exists())]
print("OK" if not missing else "MISSING: " + ", ".join(missing))
sys.exit(1 if missing else 0)
PY' _ $s
done
```

**A functional check, once the packages exist.** Each package must survive the session it
claims to serve: bootstrap the state, place a class, reconcile a number, and check a
document set — and the outputs must cite `path:line` for every claim. The proof is the
four outputs, not a test suite: this repository has no test framework for skills, and
inventing one would be a new dependency for a new artefact.

---

## 4. Progressive disclosure

The layering is the point, and it is why the set stays small:

| layer | what it holds | size |
|---|---|---|
| **this routing file** | the situation → skill → what it reads → what it must not do table | the only thing a session reads before choosing |
| **each `SKILL.md`** | the frontmatter, the decision procedure, the safety rules | 40–80 lines each |
| **`references/`** | the detail: the reading set, the two chains, the six cycles, the artefact families, the defect classes | one file per subject |
| **the repository** | the facts themselves — the governance registers, the architecture documents, the audit CSVs | never copied |

**No skill holds a copy of the repository, of the registers, or of a wave record.** A skill
that carries a copy will drift from the tree it describes; the drift is invisible and it
is exactly the defect this wave's audits kept finding.

---

## 5. The conflicts the recommended set would have with the existing ecosystem

| the conflict | the verdict |
|---|---|
| **`codebase-review-swarm` vs `matrix-architecture-navigator`** | **not a duplicate, and the line between them is worth writing down.** The swarm skill is a general-purpose, evidence-gated audit with eleven non-negotiable invariants and its own artefact format; it produces a report about the whole repository. The navigator answers a *placement* question about MATRIX's own boundaries and reads the governance registers. The decisive difference: **the swarm skill does not know the two chains, the six cycles, the writer lock or the eleven owner entries.** A MATRIX agent needs those, so the navigator earns its place. **The rule for the future: no new skill may produce a general review report.** If a MATRIX skill needs to notice something, it notices it *inside its own subject* and cites it |
| **`deep-dive` vs `matrix-architecture-navigator`** | closer, and the reason the navigator is scoped narrowly: `deep-dive` is a read-only audit mode with parallel explorers and a critic chain. The navigator must **not** become an audit. It answers placement questions and cites the registers; it does not produce a severity-ranked finding list |
| **the auto-skills trigger table (`src/lib/53-auto-skills.sh` and its generated mirror `~/.config/opencode/auto-skills/config.json`)** | the four slugs must be added to the trigger tables or the skills are never auto-suggested. **That is a source change and it is outside this task's write scope** — it is recommended as a Level 1 follow-up, together with the mirror regeneration the script's own comment requires ("keep in sync with the bash trigger tables in that module") |
| **`coprocessor` is the trigger table's default skill, and the repository's copy of it is missing 13 lines** the machine's copy has (`## Harness Engineering Doctrine`) | not a conflict with the recommended set, but a defect to fix: the trigger table resolves the *project* copy first (`src/lib/53-auto-skills.sh:67`), so a session gets the less complete one. Also a copy-sync policy is needed, or the project copies will keep drifting |
| **`opencode.json`** | **not touched.** The four packages live in `.agents/skills/`, a harness default search path, so they are discovered without any configuration change. Editing `opencode.json` would be a Level 3 operation on an owner entry, and it is not needed |
| **the generic skills (`.veai/skills/`) and the plugin modes** | **not touched, not replaced, not recommended for MATRIX use.** They are either invisible to this session (`.veai/`) or built for another repository (`bun:test`, `opencode-swarm`, the plugin's 500-line test cap). They are recorded in the inventory; they are not part of the MATRIX set |
| **the swarm plugin's tools** | the four recommended skills need **only generic tools** — `read`, `grep`, `glob`, `bash`, `todo_write`. None of them needs `repo_map`, `graph_health`, `context_pack`, `route_trace`, `data_trace`, `dispatch_lanes` or `prepare_pr_workflow_checkout`. This is deliberate: a skill that needs the plugin's tools stops working outside the plugin, which is how the bundled `codebase-review-swarm` copy became unusable here |

---

## 6. What is recommended **not** to be built

A recommendation to build nothing is a legitimate answer when the evidence supports it.
Six things are recommended against, with the reason each time:

| not to build | why |
|---|---|
| **a general-purpose code review skill** | `codebase-review-swarm` exists, is complete, validates (`skill package OK`, exit 0), is the only general-purpose review skill in the ecosystem, and carries the evidence contract the others lack. The directive forbids the duplication and the evidence agrees |
| **six separate packages** | two of the six share a contract over the same documents. Six packages would mean two copies of the reading set and two places to drift |
| **a `matrix-commit` / `matrix-git-workflow` skill** | committing and pushing are Level 3 — the owner's, every time. A skill that makes them routine would automate the one operation the autonomy protocol reserves for the owner. It would also inherit the `git-workflow` conflict (`commit to main`) documented in the inventory |
| **a wrapper around the MCP servers** | `context7`, `memory` and `sequential_thinking` are already tools in a session. A wrapper adds a layer, a failure mode, and nothing else — and the other 22 configured servers are not reachable from a session at all, so a wrapper could only ever wrap the three that are already direct |
| **a knowledge-graph / wiki skill** | the knowledge *is* the documents. A second representation would be a second source of truth, and the decision register and the five governance registers are already the machine-facing layer a session needs |
| **a skill that copies the repository into itself** | explicitly excluded by the progressive-disclosure rule, and it is the mechanism by which the audits keep finding stale numbers: a copied fact is a fact that stopped being true the day it was copied |

**One thing is recommended *now*, before any skill is written:** the four packages' facts
about MATRIX live in the governance registers — `docs-v2/governance/PROJECT-STATE.md`,
`docs-v2/governance/ROADMAP.md`, `docs-v2/governance/OPEN-QUESTIONS.md`,
`docs-v2/governance/RISK-REGISTER.md`, `docs-v2/governance/DECISION-REGISTER.md`,
`docs-v2/governance/DOCUMENT-STATUS-POLICY.md`,
`docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` and
`docs-v2/governance/SESSION-HANDOFF-TEMPLATE.md` — all written on 2026-11-04, all
`CANONICAL`, and never exercised by a tool. **The first deliverable of `matrix-document-integrity` is
the check that the registers agree with the tree** — because every other package trusts
them.

---

## 7. The uncertainty

* **The skill catalogue changed during this task.** The harness's available-skills list was
  replaced mid-session and now carries 58 entries, including four (`agent-experience`,
  `cordis-composition-reference`, `cordis-plugin-development`, `dsh-badge`) that are
  harness-bundled rather than repository-held. The inventory's "session-visible" statements
  are about the catalogue **as of 2026-11-04, after that change**; the four harness
  additions were not individually inspected, because they are not the repository's skills.
* **`matt-pocock`'s 17 sub-skills were recorded by name and by the trigger that reaches
  them, not by reading all 17 bodies.** Their purposes come from their names and from the
  trigger table.
* **The invocation contract of every skill in the table is `unverified`** except
  `codebase-review-swarm`, whose `agents/openai.yaml` states
  `allow_implicit_invocation: false`, and the trigger table's own entries. The other
  packages do not declare one, and **a declaration that is absent is not evidence of
  either behaviour**.
* **No skill was executed.** The validation command proves the package *shape*; it does not
  prove that a skill produces a good answer. The functional check in §3 is a proposal, not
  a result.
* **The 22 unreachable MCP servers were not tested** — starting a server and calling an
  external service were both outside the task's scope.

---

## 8. The coordinator's answers

**The six questions, answered.**

| the question | the answer |
|---|---|
| **four or six?** | **four**, with the two merges documented above. The directive says *"merge responsibilities when it reduces duplication"*, and the halves being merged read the same five files, hold the same eleven owner entries and check the same registers. Two packages that read the same state are two copies of that state, and the copies drift. |
| **is `.agents/skills/` acceptable?** | **yes** — it is the only location the harness reads by default, and it touches **no owner-owned file**: not `opencode.json`, not `.opencode/**`, not `.veai/**`, not `.codex/**`. `.opencode/skills/` is **not read** by the harness, so a skill placed there would be invisible — and the finding that it is invisible is itself the reason the recommendation is `.agents/skills/`. |
| **who approves the trigger-table edit?** | **nobody yet — it is out of this wave.** The trigger table (`src/lib/53-auto-skills.sh`) and its mirror (`~/.config/opencode/auto-skills/config.json`) belong to the harness, not to this repository. The recommendation stands without the edit: the four packages are installed by their **directory**, and the trigger is an optimisation, not a requirement. It is recorded as a follow-up in `docs-v2/governance/OPEN-QUESTIONS.md` (**Q-W46-22**). |
| **write the copy policy?** | **recorded, not enforced.** The policy is written in `docs-v2/governance/SKILL-INVENTORY.md` §9 — the project's copy is the one a session loads, the mirror is the fallback, and a difference between them is a finding — but **no owner's copy is touched**. The difference that exists (the `coprocessor` copy carries thirteen extra lines) is recorded as a finding, not corrected: correcting it would rewrite a file the directive protects. |
| **fix the identifier collisions?** | **no — the owner's decision.** `plan.retired-backup` declares `name: plan`, and three `.veai` skills do not match their directory. The fixes belong to the plugin bundle and to the owner's library, which the directive protects. They are recorded as **Q-W46-23**. |
| **who resolves the `AGENTS.md:30` conflict?** | **the owner — and the amendment proposal already carries the resolution.** `AGENTS.md` says *"Push после каждого"*, and the operating protocol says that a push is a Level 3 action that needs the owner's explicit approval. **Two owner-owned documents give opposite standing instructions**, and an agent may not resolve that. The proposal in `docs-v2/governance/AGENTS-AMENDMENT-PROPOSAL.md` qualifies the push rule with the autonomy level — so applying the proposal **is** the resolution. It is the owner's to apply. |

---

## 8. The questions for the coordinator

1. **Four or six?** The recommendation is four, with two documented merges. If the
   coordinator wants `matrix-test-evidence` and `matrix-research-evidence` as separate
   packages, that is a defensible call — but the reading-set copy and the drift risk come
   back with it.
2. **Is `.agents/skills/` acceptable as the home?** It is the only location that both the
   harness discovers by default and that touches no owner entry. The alternative —
   `.opencode/skills/` — would serve the OpenCode plugin and the trigger table, and would
   be **invisible to the harness**.
3. **Who approves the trigger-table change?** Adding the four slugs to
   `src/lib/53-auto-skills.sh` and regenerating `~/.config/opencode/auto-skills/config.json`
   is a Level 1 change to a source file, outside this task's write scope. It also raises a
   policy question: `src/lib/53-auto-skills.sh` is the project's own installer, and the
   mirror it writes lives in the user's home directory.
4. **Should the copy-sync policy be written down?** Twenty-five of the twenty-six project
   skills are byte-identical to their machine twins and one has drifted; `coprocessor` in
   the project copy is the *less* complete one, and the trigger table resolves the project
   copy first. Nothing states which copy wins.
5. **Does the owner want the two `D`-name collisions and the `plan.retired-backup` identity
   collision fixed at all?** They are recorded in the inventory and in
   `docs-v2/governance/DECISION-REGISTER.md` §4, but fixing them means touching the
   plugin's bundle and the owner's library.
6. **Should `AGENTS.md`'s "Push после каждого" be reconciled with the autonomy protocol's
   "no commit and no push without the owner's explicit authorisation"?** The two documents
   disagree, and the conflict is recorded as blocking in the inventory. Both are owner
   entries, so an agent cannot settle it — it needs a decision.
