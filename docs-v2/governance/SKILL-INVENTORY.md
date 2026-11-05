# SKILL INVENTORY — MATRIX agent ecosystem

**Status:** CANONICAL · 2026-11-04 · branch `develop` · HEAD `a6ded233`
**Kind:** a census and a diagnosis. It records what the agent ecosystem of this repository
**is**, and which parts of it a MATRIX session can actually invoke. It does not activate,
install, modify or deactivate anything: `opencode.json` is an owner entry and was read,
never written.

**The rule this inventory follows:** *every skill is recorded; a skill that is not recorded
is a skill nobody has looked at.* Where a field could not be established, the field says
`unverified`. Nothing here is inferred to be present, working, or safe because it looks
like it should be.

**The central question is not "how many skills are there".** It is **which of them a MATRIX
session can actually invoke, and which of them contradict the project's own rules**. A
skill whose instructions contradict the Constitution, `AGENTS.md`, the autonomy levels or
the protection rules is *incompatible* — and that is a finding worth more than a count.

---

## 1. The vocabulary

| term | the definition used here |
|---|---|
| **complete** | the package has a `SKILL.md` with a parseable frontmatter carrying `name` and `description`; every file its own load order names is present; and the skill's own validation command exits 0. **Completeness is a property of the package, not of its usefulness to MATRIX** — a complete skill can still be incompatible |
| **incomplete** | a required part is missing: no `SKILL.md` at the root, a frontmatter `name` that does not match the directory, or a referenced file that does not exist |
| **duplicated** | the same skill identity exists in more than one place — a second copy under a different root, or a second copy under the same name — with divergent content or as a retired leftover |
| **incompatible** | the skill's instructions or its required tools contradict the Constitution, `AGENTS.md`, the autonomy levels (`docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §3), the security policy, or the protection rules — **or** the skill requires tools that this session does not have, so it cannot run |
| **obsolete** | the skill is retired, superseded, or kept only as a historical copy. An obsolete skill is not deleted; it is recorded so that a reader who meets it knows what it is |
| **unverified** | a field that could not be established from the repository. **Never a guess.** It is used for the invocation contract, the input requirements, the output format and the safety boundaries of any skill whose body was not read in full |

A skill may hold several of these at once. `codebase-review-swarm` in
`.swarm/bundled-skills/` is simultaneously *complete*, *duplicated*, and *incompatible*.

---

## 2. How the census was taken

Every number below comes from a command run on 2026-11-04 against the working tree at
`a6ded233`. The commands are reproduced in §11.

```bash
# the four skill roots and their sizes
ls .veai/skills | wc -l            # 21
ls .opencode/skills | wc -l         # 27
ls .swarm/bundled-skills | wc -l    # 42
ls ~/.config/opencode/skills | wc -l # 43
```

---

## 3. The groups

| group | the root | count | the owner | what an agent MAY do | what an agent MAY NOT do |
|---|---|---|---|---|---|
| **G1 — the owner's generic library** | `.veai/skills/` | 21 | the owner | read them, cite them, reuse a pattern from them | write, move, rename or delete anything under `.veai/**` — it is one of the eleven owner entries (`docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §1.3) |
| **G2 — the owner's coding rules** | `.veai/rules/` | 8 files, 591 lines | the owner | read them; they declare `filePattern: "**/*"`, so they are meant to apply to every file | modify them; they are inside the protected `.veai/**` tree |
| **G3 — the owner's MCP configuration** | `.veai/mcp_servers.json` | 10 servers | the owner | read the server names; run a server only if the owner asks | modify the file; it is inside `.veai/**` |
| **G4 — the project skill root** | `.opencode/skills/` | 27 directories (26 skills + 1 container) | the project | use the skills; **add** a skill here (a new directory is a Level 1 change) | modify the owner's other `.opencode/**` entries (`todo.md`, `w37-plan.md`, `goals/`, `opencode-swarm.json`, `tmp/`) |
| **G4b — the harness's own project skill root** | `.agents/skills/` (does not exist yet) | 0 | the project | create it and put MATRIX-specific packages there — it is a harness default search path | — |
| **G5 — the plugin bundle** | `.swarm/bundled-skills/` | 42 directories | the `opencode-swarm@7.197.3` plugin | read them; they are the plugin's own assets | edit them — the plugin reinstalls them; they are not project files |
| **G6 — the machine-level skill library** | `~/.config/opencode/skills` (symlinked as `~/.dsh/skills`) | 43 directories | the machine's owner | use them — **this is what a session actually loads** | modify them from a MATRIX session; they are outside the repository |
| **G7 — the owner's Codex configuration** | `.codex/config.toml` | 2 lines, 1 MCP server | the owner | read it | modify it — it is an owner entry |
| **G8 — the owner's OpenCode configuration** | `opencode.json` | 2 keys (`$schema`, `instructions`) | the owner | read it | **modify it, and do not activate a skill through it** — it is an owner entry and an explicit directive of this task |
| **G9 — the activation trigger table** | `src/lib/53-auto-skills.sh` + the generated mirror `~/.config/opencode/auto-skills/config.json` | 1 script, 1 mirror | the project | read them; the script resolves a project skill first and the global library second | edit the mirror by hand — it is regenerated by the script |

### 3.1 Which of these roots a session actually loads

**The harness does not read `.opencode/skills/`.** Its skill roots, in rank order, are
(`/home/alexandr-narbaev/.npm-global/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-skill-filesystem/lib/index.js:154-177`):

| rank | the root | what is there today |
|---|---|---|
| 1 | `<projectRoot>/.dsh/skills` | nothing — the directory does not exist |
| 2 | `<projectRoot>/.agents/skills` | nothing — the directory does not exist |
| 3 | the custom roots | none configured |
| 4 | `~/.dsh/skills` | a symlink to `~/.config/opencode/skills` — **43 skills** |
| 5 | `~/.agents/skills` | not present |
| 6 | the bundled skills | the harness's own four (`agent-experience`, `cordis-composition-reference`, `cordis-plugin-development`, `dsh-badge`) |

**So the 27 packages in `.opencode/skills/` are not what a MATRIX session loads.** They
serve the OpenCode plugin and the auto-skills trigger table (which resolves the project
copy first, `src/lib/53-auto-skills.sh:67`). The 25 byte-identical ones reach the harness
through their **machine-level twins** in `~/.config/opencode/skills`; `coprocessor`
reaches it through a twin that has 13 extra lines; and `matt-pocock` — with its 17
sub-skills — **does not reach the harness at all**, because the harness sees only the
bare names `tdd`, `code-review`, `research` and the rest, which exist at the machine level
but not in the repository.

**This is the single most actionable fact in the inventory: a MATRIX-specific skill
belongs in `.agents/skills/`, which is a default search path.** Putting one in
`.opencode/skills/` would make it invisible to the harness that runs it.

**The four roots, in numbers.**

| root | directories | with a `SKILL.md` | without |
|---|---|---|---|
| `.veai/skills/` | 21 | 21 | 0 |
| `.opencode/skills/` | 27 | 26 | 1 (`matt-pocock` — a container of 17 sub-skills) |
| `.swarm/bundled-skills/` | 42 | 42 | 0 |
| `~/.config/opencode/skills` | 43 | 43 | 0 |
| **total** | **133** | **132** | **1** |

**82 distinct frontmatter names** across the four roots. `matt-pocock` holds 17 further
sub-skills, so the number of skill *packages* on the machine is 149.

---

## 4. G4 — the project skill root (`.opencode/skills/`), the skills a MATRIX session sees

26 skills plus one container. **25 of the 26 are byte-identical to their twin in
`~/.config/opencode/skills`** — which is where the session actually loads them from. The
twenty-sixth, `coprocessor`, differs.

| name | purpose | supported tools | invocation | input requirements | output format | dependencies | safety boundaries | owner | version | validation command | limitations |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `brainstorm` | full protocol for MODE: BRAINSTORM — discovery dialogue, approach selection, spec drafting, QA gate selection | Read, Glob, Grep, Write, Edit, Bash | via the `skill` tool; also auto-triggered by the `plan` task type | a task statement | a design decision and a spec draft | none in-repo | none stated in its own body | project | unverified | the skill has no self-check; presence is the only check | general-purpose; knows nothing about MATRIX's rules |
| `clarify` | MODE: CLARIFY — clarification funnel with a critic review before surfacing decisions | Read, Glob, Grep, Write | `skill` tool | an ambiguous request | a clarified request or a question set | none in-repo | none stated | project | unverified | none | generic |
| `clarify-spec` | MODE: CLARIFY-SPEC — resolves spec clarification markers, keeps spec and plan aligned | Read, Glob, Grep, Write | `skill` tool | a spec with markers | a resolved spec | none in-repo | none stated | project | unverified | none | generic |
| `codebase-review-swarm` | evidence-gated full-repo audit with Phase 0 inventory, non-diluting track depth, coverage closure, reviewer/critic validation; writes `.swarm/review-v8/` artifacts | Read, Glob, Grep, Bash, and a `Task`/subagent dispatcher | explicit `$codebase-review-swarm`; `agents/openai.yaml` sets `allow_implicit_invocation: false` | a review target and a selected track set | `.swarm/review-v8/runs/<run_id>/` artefacts and a `review-report.md` | `references/review-protocol-v8.2.md`, `assets/jsonl-schemas.md`, `assets/review-report-template.md`, `references/full-v7-source-prompt.md`, `.opencode/skills/codebase-review-swarm/scripts/init-review-run.py`, `.opencode/skills/codebase-review-swarm/scripts/validate-skill-package.py` | **it does not modify source files**; that is stated in its own body | project (package is MIT-licensed, `metadata.source_prompt: codebase-review-swarm-prompt-v7`) | **8.2.0** (`metadata.generated: 2026-06-08`) | `python3 .opencode/skills/codebase-review-swarm/scripts/validate-skill-package.py .opencode/skills/codebase-review-swarm` → `skill package OK`, exit 0 | the copy here is the **older** of two — see §8 |
| `commit-pr` | commits, pushes, opens and updates PRs, writes release notes, closes out remote CI | Bash (git, gh), Read, Edit, Write | `skill` tool; auto-triggered on "commit"/"push"/"PR" | a validated working tree | commits, a pushed branch, a pull request | the opencode-swarm invariant audit, the release-note fragment workflow | **it commits and pushes** — a Level 3 operation | project | unverified | the body defines a validation stack but no self-check script | **conflicts with the protection rules** — see §9 |
| `consult` | MODE: CONSULT — advisory answers with bounded evidence and explicit uncertainty | Read, Glob, Grep | `skill` tool | a question | an answer with its evidence and its uncertainty | none in-repo | none stated | project | unverified | none | generic |
| `coprocessor` | universal coprocessor: dual-process reasoning, memory hierarchy, shared-state IPC, CO-STAR output contract, source ladder | Read, Write, Edit, Bash, Glob, Grep | `skill` tool; **the default skill of the auto-trigger table** (`default_skills: ["coprocessor"]`) | free text | a structured answer in the CO-STAR shape | none in-repo | none stated | project (global twin is the machine's) | **1.0.0** | the auto-trigger mirror `~/.config/opencode/auto-skills/config.json` lists it; no self-check | **the repository copy is missing 13 lines** the global twin has — the "Harness Engineering Doctrine" — see §8 |
| `council` | MODE: COUNCIL — research council, parallel member dispatch, disagreement handling, synthesis | Read, Glob, Grep, Bash, subagent dispatch | `skill` tool | a research question | a synthesis with the disagreements preserved | a subagent dispatcher | none stated | project | unverified | none | generic |
| `critic-gate` | MODE: CRITIC-GATE — plan critic review, revision loops, hard stop before execution | Read, Write, Edit | `skill` tool | a plan | a passed or rejected plan | none in-repo | a hard stop before execution | project | unverified | none | generic |
| `deep-dive` | MODE: DEEP_DIVE — read-only codebase audit with parallel explorer waves, two independent reviewers, a sequential critic challenge for HIGH/CRITICAL findings | Read, Glob, Grep, Bash, subagent dispatch | `skill` tool, loaded on demand by the architect | a subsystem and a focus | a severity-ranked finding list | a subagent dispatcher | read-only by its own description | project | unverified | none | overlaps `codebase-review-swarm` in intent — see §10 |
| `deep-research` | MODE: DEEP_RESEARCH — orchestrator-worker research over external sources with preregistered decomposition, synthesis, dual review and adversarial verification | web_search, web_fetch, Read, Write, subagent dispatch | `skill` tool | a research question | a research report with citations | a subagent dispatcher, network access | none stated | project | unverified | none | generic; does not know the repository's META-R3 primary-source rule |
| `design-docs` | MODE: DESIGN_DOCS — generates or syncs a structured design-doc set for the project under build | Read, Write, Glob, Grep | `skill` tool | a project and a design-doc standard | `domain.md`, `technical-spec.md`, `behavior-spec.md`, `reference/` | none in-repo | none stated | project | unverified | none | generic; it writes documents, which in MATRIX means the canonical registers — see §10 |
| `discover` | MODE: DISCOVER — read-only repository discovery and governance/context mapping | Read, Glob, Grep, Bash | `skill` tool | a repository | a governance and context map | none in-repo | read-only by its own description | project | unverified | none | the closest existing thing to a MATRIX architecture navigator — see §10 |
| `engineering-conventions` | non-negotiable engineering invariants for modifying opencode-swarm; load before architecture, plugin init, subprocess, tool registration, plan durability, `.swarm` storage | Read, Grep | `skill` tool | a change to the swarm plugin | guidance | none in-repo | it is a rule set for the plugin, not for MATRIX | project | unverified | none | **its subject is `opencode-swarm`, not MATRIX** — a MATRIX session that loads it is loading another project's rules |
| `execute` | MODE: EXECUTE — task execution, coder retry handling, QA gates, completion evidence, per-task closure | Read, Write, Edit, Bash | `skill` tool | an approved plan | an executed task with evidence | none in-repo | QA gates | project | unverified | none | generic |
| `issue-ingest` | MODE: ISSUE_INGEST — GitHub issue intake, localization, spec generation, transition to planning | `gh` CLI, Read, Write | `skill` tool | an issue | a spec draft | the `gh` CLI | none stated | project | unverified | none | generic |
| `loop` | MODE: LOOP — the compound-engineering loop (brainstorm → plan → build → review → improve) with generator/critic separation | Read, Write, Edit, Bash, subagent dispatch | `skill` tool | a goal | iterations and a final state | a subagent dispatcher | none stated | project | unverified | none | generic |
| `matt-pocock` | **no `SKILL.md` at its root** — a container of 17 sub-skills (see §6) | — | the sub-skills are addressed as `matt-pocock/<name>` | — | — | — | — | project | — | — | **incomplete** — the auto-trigger table addresses seven of its sub-skills by path |
| `phase-wrap` | MODE: PHASE-WRAP — phase-boundary evidence, drift and hallucination gates, retrospectives, phase completion, final council | Read, Write, Bash | `skill` tool | a finished phase | a phase wrap with evidence | a subagent dispatcher | drift and hallucination gates | project | unverified | none | generic |
| `plan` | MODE: PLAN — plan creation, external plan ingestion, QA gate persistence, task granularity, traceability | Read, Write, Edit, Bash | `skill` tool; auto-triggered by the `plan` task type | an objective | a plan with QA gates and traceable tasks | none in-repo | QA gate persistence | project | unverified | none | generic; a MATRIX plan must respect the governance registers, which this skill does not know about |
| `pre-phase-briefing` | MODE: PRE-PHASE_BRIEFING — phase-start context assembly, evidence review, task readiness checks | Read, Glob, Grep | `skill` tool | a phase and a task list | a briefing and a readiness verdict | none in-repo | none stated | project | unverified | none | generic |
| `resume` | MODE: RESUME — continues an existing approved plan safely from the current state | Read, Write, Edit, Bash | `skill` tool | an approved plan and a state | continued execution | none in-repo | "safely" is its own claim, not a rule | project | unverified | none | generic |
| `running-tests` | safe test execution: when to use the test runner versus shell commands, scope safety rules, per-file isolation, pre-existing failure verification, CI log reading | Bash, Read | `skill` tool; auto-triggered by the `test` task type and by `.py`/`.sh` files | a scope | a test result with failures classified | none in-repo | scope safety rules | project | unverified | none | **its subject is `bun:test` and the opencode-swarm CI** — it does not know MATRIX's rule that the full `matrix-core` suite is owner-held |
| `specify` | MODE: SPECIFY — spec creation, codebase reality checks, SME input, QA gate persistence | Read, Write, Glob, Grep | `skill` tool; auto-triggered by the `specify` task type | a requirement | a spec with QA gates | none in-repo | QA gate persistence | project | unverified | none | generic |
| `swarm-pr-feedback` | ingests and resolves known PR feedback with skeptical source verification | `gh` CLI, Read, Write, Edit, Bash | `skill` tool | PR feedback | a resolution with verified sources | the `gh` CLI | none stated | project | unverified | none | it writes to the branch; the push decision is Level 3 |
| `swarm-pr-review` | graph-guided, tool-augmented PR review with context packing, parallel exploration, independent reviewer validation, critic challenge, metrics writeback | `gh` CLI, Read, Bash, subagent dispatch | `skill` tool | a PR number | a review verdict and metrics | the `gh` CLI, and — in the bundled copy — graph tools this session lacks | it does not commit, push, rebase, merge or reset (its own body says so at line 218) | project | unverified | none | the repository copy is the graph-free variant; the bundled one is not |
| `writing-tests` | guidelines for writing, organizing and maintaining tests in the opencode-swarm repository: framework rules (`bun:test`), mock isolation, CI structure, file placement | Read, Write, Edit | `skill` tool | a test to write | a test file | none in-repo | none stated | project | unverified | none | **its subject is `bun:test`** — MATRIX is JUnit 5 and jqwik; loading it would push the wrong framework |

---

## 5. G1 — the owner's generic library (`.veai/skills/`)

21 packages, **one `SKILL.md` each** — no scripts, no references, no assets. Every one
carries `used-by` and `schemaVersion: "v0.1"` in its frontmatter.

**None of these 21 is in this session's skill catalogue.** The harness loads
`.opencode/skills/` and `~/.config/opencode/skills`; it does not read `.veai/`. They are a
reference library for another agent, and they are an owner entry.

| name (frontmatter) | directory | purpose | owner | version | status | limitations |
|---|---|---|---|---|---|---|
| `android-clean-architecture` | same | Clean Architecture for Android and Kotlin Multiplatform | owner | unverified | complete | irrelevant to a Java/Quarkus repository |
| `api-design` | same | REST API design: naming, status codes, pagination, error responses, versioning, rate limiting | owner | unverified | complete | generic; does not know MATRIX's `/v1/workbench/**` and `/v1/cognitive/**` surfaces |
| `bun-runtime` | same | Bun as runtime, package manager, bundler, test runner | owner | unverified | complete | MATRIX has no Bun |
| `compose-multiplatform-patterns` | same | Compose Multiplatform and Jetpack Compose | owner | unverified | complete | irrelevant |
| `deployment-patterns` | same | deployment workflows, CI/CD, Docker, health checks, rollback | owner | unverified | complete | **advisory conflict** — a generic deploy recipe applied to MATRIX would touch `docs-v2/operations/` and the deployment surface without the owner's approval |
| `docker-patterns` | same | Docker and Compose patterns, container security, networking | owner | unverified | complete | advisory; the repository's own Dockerfile is a protected surface in practice |
| `error-handling` | same | typed errors, error boundaries, retries, circuit breakers | owner | unverified | complete | generic |
| `frontend-patterns` | same | React/Next.js patterns | owner | unverified | complete | irrelevant |
| `git-workflow` | same | branching, commit conventions, merge vs rebase, conflict resolution | owner | unverified | complete | **blocking conflict** — see §9 |
| `github-ops` | same | GitHub repository operations: issue triage, PR management, CI/CD, releases, security monitoring | owner | unverified | complete | **blocking conflict** — it covers PR management and releases, which are Level 3 in MATRIX |
| `hexagonal-architecture` | same | ports and adapters, dependency inversion, testable use cases | owner | unverified | complete | generic; MATRIX's own architecture is documented in `docs-v2/architecture/` |
| `jpa-patterns` | same | JPA/Hibernate entity design, queries, transactions, auditing, pagination | owner | unverified | complete | MATRIX has no JPA — it uses Quarkus with SQLite and R2DBC |
| `mle-workflow` | same | production ML engineering: data contracts, reproducible training, evaluation, deployment, monitoring, rollback | owner | unverified | complete | **advisory conflict** — it asks for random seeds and documents nondeterministic GPU behaviour, which is legitimate for a training pipeline but must not be read as permission for the runtime decision path (Constitution, Article I) |
| `prisma-patterns` | same | Prisma ORM for TypeScript | owner | unverified | complete | irrelevant |
| `python-patterns` | same | Python idioms, PEP 8, type hints | owner | unverified | complete | MATRIX is Java 25; the repository's Python is scripts only |
| `query-patterns` | same | SQL patterns, relational design, query optimization, transactions | owner | unverified | complete | generic |
| `react-patterns` | same | React 18/19 hooks, server/client boundaries, Suspense, forms | owner | unverified | complete | irrelevant |
| `redis-patterns` | same | Redis data structures, caching, distributed locks, rate limiting, pub/sub | owner | unverified | complete | advisory; the repository does use Redis for `NeuronCacheService`, but the lock semantics differ from MATRIX's writer lock (OD-004) |
| `scholar-evaluation` | `scientific-thinking-scholar-evaluation` | structured scholarly-work evaluation: evidence quality, citation support, research-writing feedback | owner | unverified | complete, **incomplete** (the frontmatter `name` does not match the directory) | **the name mismatch matters**: a loader keyed on the directory name finds `scientific-thinking-scholar-evaluation`; a loader keyed on the frontmatter finds `scholar-evaluation`. The directory is also misspelled relative to the skill's own intent |
| `search-first` | `seatch-first` | research-before-coding: search for existing tools and patterns before writing custom code | owner | unverified | complete, **incomplete** (name ≠ directory; the directory is misspelled `seatch-first`) | as above; the skill itself is aligned with MATRIX's evidence discipline |
| `springboot-patterns` | same | Spring Boot architecture, REST design, layered services, data access, caching, async | owner | unverified | complete | MATRIX is Quarkus, not Spring Boot |

---

## 6. G4-container — `matt-pocock`, 17 sub-skills

A container with no root `SKILL.md`. Its 17 sub-skills are addressed as
`matt-pocock/<name>` by the auto-trigger table. Each is a complete single-file skill.

| sub-skill | the auto-trigger that reaches it |
|---|---|
| `code-review` | the `review` task type |
| `codebase-design` | the `refactor` task type, and the TypeScript file trigger |
| `diagnosing-bugs` | the `debug` task type |
| `domain-modeling` | — |
| `grill-me` | — |
| `grill-with-docs` | — |
| `grilling` | — |
| `implement` | the `coding` task type, and the TypeScript file trigger |
| `improve-codebase-architecture` | the `refactor` task type |
| `prototype` | — |
| `research` | the `research` task type |
| `resolving-merge-conflicts` | — |
| `tdd` | the `test` task type, and the TypeScript file trigger |
| `to-spec` | — |
| `to-tickets` | — |
| `wayfinder` | — |
| `wizard` | — |

**Status of the container: incomplete** (no root `SKILL.md`), and **duplicated** — all 17
sub-skills also exist as top-level skills in `~/.config/opencode/skills/` under their bare
names, so the same skill is reachable by two identifiers.

---

## 7. G5 — the plugin bundle (`.swarm/bundled-skills/`), 42 skills

These are the assets of the `opencode-swarm@7.197.3` plugin, declared in the global
`~/.config/opencode/opencode.json` under `plugin`. **They are not project files.** Editing
them is pointless — the plugin reinstalls them — and the plugin's own skills assume plugin
tools.

**The tool problem, measured.** These skills require tools that a MATRIX session does not
have:

| tool | referenced in how many bundled skill files | available to this session? |
|---|---|---|
| `worktree` (as a workflow object) | 23 | no |
| `dispatch_lanes` | 15 | no |
| `repo_map` | 12 | no |
| `prepare_pr_workflow_checkout` | 8 | no |
| `graph_health` | 4 | no |
| `context_pack` | 3 | no |
| `route_trace` | 3 | no |
| `data_trace` | 3 | no |
| `set-dispatch` | 2 | no |

**17 of the 42 bundled skills name at least one of those tools**: `codebase-review-swarm`,
`commit-pr`, `council`, `critic-gate`, `deep-dive`, `deep-research`,
`engineering-conventions`, `execute`, `gate-attribution`, `phase-wrap`,
`pre-phase-briefing`, `running-tests`, `swarm-implement`, `swarm-plan`,
`swarm-pr-feedback`, `swarm-pr-review`, `writing-tests`. **Those 17 are *incompatible* with
a MATRIX session** — they are the plugin's own instruments, and a session outside the
plugin does not have them. The 25 bundled skills that do not name one are usable, and the
25 are byte-identical to their project and machine-level twins.

**The command that produced the number:**

```bash
for t in repo_map graph_health context_pack route_trace data_trace \
         prepare_pr_workflow_checkout dispatch_lanes set-dispatch; do
  grep -rl "$t" .swarm/bundled-skills/ | sed 's|.*/bundled-skills/||; s|/.*||'
done | sort -u | wc -l          # 17
```

| name | purpose | owner | status | limitation |
|---|---|---|---|---|
| `brainstorm` | same as the project copy | plugin | complete, duplicated | byte-identical to the project and global copies |
| `ci-failure-batching` | batch collection and fix protocol for CI failures | plugin | complete | tied to the plugin's CI dispatch |
| `ci-fix-monitor` | monitors CI after a fix | plugin | complete | tied to the plugin's CI dispatch |
| `clarify` | as above | plugin | complete, duplicated | identical copy |
| `clarify-spec` | as above | plugin | complete, duplicated | identical copy |
| `codebase-review-swarm` | as above, **plus** `audience: swarm-plugin`, the graph-first evidence contract and the PR-checkout pre-flight | plugin | complete, **duplicated with divergent content**, **incompatible** with this session's tools | see §8 |
| `commit-pr` | as above | plugin | complete, duplicated | identical copy |
| `consult` | as above | plugin | complete, duplicated | identical copy |
| `council` | as above | plugin | complete, duplicated | identical copy |
| `critic-gate` | as above | plugin | complete, duplicated | identical copy |
| `deep-dive` | as above | plugin | complete, duplicated | identical copy |
| `deep-research` | as above | plugin | complete, duplicated | identical copy |
| `design-docs` | as above | plugin | complete, duplicated | identical copy |
| `discover` | as above | plugin | complete, duplicated | identical copy |
| `durable-session-state` | durable session state across restarts | plugin | complete | plugin-specific |
| `engineering-conventions` | as above | plugin | complete, duplicated | identical copy |
| `execute` | as above | plugin | complete, duplicated | identical copy |
| `fork-pr-operations` | fork PRs: workflow approval after push, force-push protocol, remote naming | plugin | complete | **it documents force-push protocol** — the repository's rules forbid force-push; see §9 |
| `gate-attribution` | per-task gate dispatch attribution | plugin | complete | plugin-internal |
| `issue-ingest` | as above | plugin | complete, duplicated | identical copy |
| `issue-tracer` | evidence-first investigation, validation and full resolution of issues and bugs | plugin | complete | version **3.0.0**; overlaps `matt-pocock/diagnosing-bugs` and the `debug` task type |
| `loop` | as above | plugin | complete, duplicated | identical copy |
| `merge-queue-readiness` | pre-queue merge-group CI simulation | plugin | complete | GitHub merge queue; not used by this repository |
| `orchestrating-subagents` | how to dispatch and collect subagents | plugin | complete | plugin-internal |
| `parallel-work-check` | parallel work lanes | plugin | complete | version **4**; plugin-internal |
| `phase-wrap` | as above | plugin | complete, duplicated | identical copy |
| `plan.retired-backup` | **the retired `plan` skill** | plugin | **obsolete**, **duplicated** | its frontmatter still says `name: plan`, so it collides with the live `plan` skill by identity |
| `pre-phase-briefing` | as above | plugin | complete, duplicated | identical copy |
| `running-tests` | as above | plugin | complete, duplicated | identical copy |
| `skill-edit-validation` | content-assertion sweep after editing a `SKILL.md` | plugin | complete | plugin-internal, but directly relevant to §10 |
| `specify` | as above | plugin | complete, duplicated | identical copy |
| `swarm` | cross-agent swarm-mode behaviour model | plugin | complete | plugin-internal |
| `swarm-ci-monitor` | CI monitoring | plugin | complete | plugin-internal |
| `swarm-implement` | complex implementation with a swarm-like workflow | plugin | complete | plugin-internal |
| `swarm-plan` | planning in swarm mode | plugin | complete | plugin-internal |
| `swarm-pr-feedback` | as above | plugin | complete, duplicated | the bundled copy is larger (6 files against 1) |
| `swarm-pr-review` | as above | plugin | complete, duplicated | the bundled copy is larger (15 files against 1) and requires graph tools |
| `swarm-pr-subscribe` | PR subscription | plugin | complete | plugin-internal |
| `swarm-resume` | resume in swarm mode | plugin | complete | plugin-internal |
| `test-file-split` | splitting test files that exceed the 500-line cap | plugin | complete | its cap (`FR-006`, `scripts/check-test-file-cap.ts` <!-- no-link: a script in the plugin's own repository, not in this one --> — a script in the **plugin's own repository**, not in this one) is the plugin's, not MATRIX's |
| `worktree-retry-cleanup` | cleaning parallel-coder worktree lanes before a retry | plugin | complete | plugin-internal |
| `writing-tests` | as above | plugin | complete, duplicated | identical copy |

---

## 8. The central finding: what `codebase-review-swarm` actually is

**It is real, it is complete, and it is the only general-purpose review skill in the
ecosystem. It must not be duplicated.**

**Its files** (11, in `.opencode/skills/codebase-review-swarm/`):

| file | lines | what it is |
|---|---|---|
| `SKILL.md` | 73 | the entry point: frontmatter, load order, 11 non-negotiable invariants, current standards, execution outline |
| `README.md` | 44 | the package overview |
| `INSTALL.md` | 75 | how to install the package into another repository |
| `references/review-protocol-v8.2.md` | 314 | the authoritative workflow, phases, track contracts, standards |
| `references/full-v7-source-prompt.md` | 2 373 | the full v7 source prompt, kept verbatim for long checklists and provenance |
| `references/compatibility-and-research-notes.md` | 25 | the compatibility decisions and the standards updates in v8.2 |
| `assets/jsonl-schemas.md` | 239 | the exact parseable block formats for the artefacts |
| `assets/review-report-template.md` | 244 | the final report structure |
| `.opencode/skills/codebase-review-swarm/scripts/init-review-run.py` | 134 | creates `.swarm/review-v8/runs/<run_id>/` and warns if `.swarm/` is not ignored |
| `.opencode/skills/codebase-review-swarm/scripts/validate-skill-package.py` | 62 | checks the package shape — the validation command |
| `agents/openai.yaml` | 6 | Codex metadata: display name, short description, default prompt, `allow_implicit_invocation: false` |

**Its frontmatter, verbatim:**

```yaml
name: codebase-review-swarm
description: Run a rigorous, quote-grounded codebase review or security/QA/accessibility/performance/AI-slop/enhancement audit. Use for full-repo or large-subsystem review reports; not for normal implementation. Performs Phase 0 inventory, selected exhaustive tracks with non-diluting depth, coverage closure, reviewer/critic validation, and writes .swarm/review-v8 artifacts without modifying source files.
license: MIT
metadata:
  version: "8.2.0"
  generated: "2026-06-08"
  source_prompt: "codebase-review-swarm-prompt-v7"
  artifact_root: ".swarm/review-v8/runs/<run_id>/"
```

**Does it work?** Yes, in the copy this session loads:

```bash
python3 .opencode/skills/codebase-review-swarm/scripts/validate-skill-package.py .opencode/skills/codebase-review-swarm
# → skill package OK   (exit 0)
python3 .swarm/bundled-skills/codebase-review-swarm/scripts/validate-skill-package.py .swarm/bundled-skills/codebase-review-swarm
# → skill package OK   (exit 0)
```

Its own package validator requires `SKILL.md`, the two `references/`, the two `assets/`, a
frontmatter `name` and `description`, a name matching `^[a-z0-9]+(-[a-z0-9]+)*$`, and a
description under 1 024 characters. Both copies satisfy it.

**But there are two copies, and they are not the same file.**

| | `.opencode/skills/codebase-review-swarm/` | `.swarm/bundled-skills/codebase-review-swarm/` |
|---|---|---|
| frontmatter | as above | as above **plus `audience: swarm-plugin`** |
| SKILL.md body | 73 lines | + the **graph-first evidence contract** (`repo_map`, `graph_health`, `context_pack`, `route_trace`, `data_trace`) and the **PR branch checkout pre-flight** |
| `references/review-protocol-v8.2.md` | 314 lines | + the **§ 0J-bis PR checkout pre-flight** |
| the tools it needs | only generic ones | `repo_map`, `graph_health`, `context_pack`, `route_trace`, `data_trace`, `prepare_pr_workflow_checkout` — **none of which this session has** |
| which one a MATRIX session loads | **this one** (it is byte-identical to `~/.config/opencode/skills/codebase-review-swarm`) | the plugin's copy, used only inside the plugin |

**Verdict:** `codebase-review-swarm` is **complete** in both copies, **duplicated** with
divergent content, and the **bundled** copy is **incompatible** with the tools a MATRIX
session has (it requires graph tools that do not exist here). The **project** copy is the
one that works here and is the one a MATRIX session should use.

**Does it duplicate anything?** Partly, and the overlap is real but not fatal:

| other skill | the overlap | the verdict |
|---|---|---|
| `.opencode/skills/deep-dive` | read-only codebase audit with parallel explorers, two reviewers, a critic challenge | **different scope**: `deep-dive` is a mode inside the plugin's architecture workflow and is not a general-purpose review skill. `codebase-review-swarm` is the general-purpose one, it is the one with the evidence contract, and it is the one with the artifact format. **Keep `codebase-review-swarm`; do not build another general-purpose review skill.** |
| `matt-pocock/code-review` | a code review | narrower, and it is the auto-trigger's default for the word "review". `codebase-review-swarm` is the evidence-gated one |
| `.swarm/bundled-skills/issue-tracer` | evidence-first investigation | different subject (an issue, not a repository) |
| `matt-pocock/improve-codebase-architecture` | an enhancement catalog | narrower; `codebase-review-swarm` separates defects from enhancements explicitly |

**Nothing else in the ecosystem is a general-purpose review skill.** That is the gap the
recommendation must not fill.

---

## 9. The conflicts

A conflict with the Constitution, with `AGENTS.md`, with the autonomy levels
(`docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §3) or with the protection rules is
**blocking**. Everything else is **advisory**.

| the skill or the configuration | the rule it conflicts with | the evidence (file and line) | the verdict |
|---|---|---|---|
| `AGENTS.md` ("Push после каждого") | the autonomy protocol: **no commit and no push without the owner's explicit authorisation, every time** | `AGENTS.md:30` against `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §3.2 (the "committing and pushing" row) | **blocking** — two repository documents give opposite standing instructions. The protocol is the newer (2026-11-04) and the more specific one, and it is the one this wave obeyed; but the conflict is unresolved in the text |
| `.veai/skills/git-workflow/SKILL.md` | the same: no commit and no push without the owner's authorisation; and `AGENTS.md`'s own one-commit rule | `.veai/skills/git-workflow/SKILL.md:57` ("Everyone commits to `main` or very short-lived branches") and `:125-126` (bare `git commit -m "fixed stuff"` examples) | **blocking** — a skill that prescribes committing to `main` is the opposite of this repository's branch rule (`develop` is the integration branch; `main` is protected) |
| `.veai/skills/github-ops/SKILL.md` | Level 3: PR management, releases and CI/CD operations are the owner's | `.veai/skills/github-ops/SKILL.md:3` (the description names "PR management, CI/CD operations, release management") against `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §3.2 | **blocking** for any action that writes to GitHub; the skill's *reading* half is harmless |
| `.opencode/skills/commit-pr/SKILL.md` | Level 3: committing and pushing | `.opencode/skills/commit-pr/SKILL.md:229` (`git commit -m "type(scope): description"`), `:135` ("Run the full validation stack before pushing"), `:40` ("do not push" when an invariant cannot be proven) | **blocking** — the skill is *about* the Level 3 operation. It is safe to load as a checklist and dangerous to run as an action |
| `.swarm/bundled-skills/fork-pr-operations/SKILL.md` | history rewriting and force-push are never | the skill's own description names "force-push protocol" against `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §3.2 ("rewriting history … never") | **blocking** if executed; the skill is plugin-internal and is not loaded by a MATRIX session |
| `.veai/rules/proceed-autonomously.md` | "Do not interpret an agent's general instruction to be autonomous as permission for Level 3 actions" | `.veai/rules/proceed-autonomously.md:11-13` ("Proceed with implementation without unnecessary confirmation pauses… Ask the user only when the task is ambiguous, blocked, risky") against `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §3.3 | **blocking** — the rule declares `filePattern: "**/*"`, so it applies to everything, and it licenses exactly the behaviour §3.3 forbids. Its own "risky" test is subjective where the protocol's is enumerated |
| `.veai/rules/tidy-changes.md` | one session = one meaningful change; do not start unrelated refactors | `.veai/rules/tidy-changes.md:11` ("Allow small related cleanup while preserving the requested scope") against `AGENTS.md:10` | **advisory** — the rule itself scopes the cleanup to the touched area and says to mention larger cleanups instead of making them |
| `.veai/rules/terse-output.md` | the repository's evidence discipline: every claim names its source; unverified numbers are not published | `.veai/rules/terse-output.md:14-18` ("You are Pascal… Be brief") against `AGENTS.md:25` ("Не генерировать непроверенные числа в отчётах") and the `path:line` convention the W45/W46 reports use | **advisory** — a style tension, not a rule violation. A terse answer that still cites `path:line` satisfies both |
| `.veai/skills/mle-workflow/SKILL.md` | Constitution Article I: the decision path is deterministic; no unseeded randomness | `.veai/skills/mle-workflow/SKILL.md:226` ("Set random seeds and document any nondeterministic GPU behavior") against `CONSTITUTION.md:5-9` | **advisory** — correct for a training pipeline, wrong if read as permission for the runtime decision path. The skill is not loaded by a MATRIX session |
| `.veai/skills/deployment-patterns/SKILL.md` | Level 2: a change that reaches a serving path needs the owner's approval | the skill's own description ("production readiness checklists") | **advisory** — the conflict is only realised if a generic recipe is applied to MATRIX's deployment surface |
| `.veai/skills/redis-patterns/SKILL.md` | OD-004: one writer per database, enforced by the writer lock | the skill covers "distributed locks" against `docs-v2/engineering/W45.2-OWNER-DECISIONS.md` (Decision 4) | **advisory** — a Redis lock is not MATRIX's writer lock; the two must not be confused |
| `.veai/skills/seatch-first/SKILL.md` | — | `.veai/skills/seatch-first/SKILL.md:3` | **no conflict** — it is aligned with the repository's probe-don't-assume discipline. Its defect is the name/directory mismatch, not its content |
| `.veai/skills/scientific-thinking-scholar-evaluation/SKILL.md` | META-R3: primary sources, not aggregators | `.veai/skills/scientific-thinking-scholar-evaluation/SKILL.md:111` ("Are primary sources used where possible?") | **no conflict** — aligned. Defect: name/directory mismatch |
| `.opencode/skills/writing-tests/SKILL.md` | the repository's test framework is JUnit 5 / jqwik, not `bun:test` | `.opencode/skills/writing-tests/SKILL.md:4` ("framework rules (bun:test)") against `AGENTS.md:34` ("Все новые классы покрыты JUnit 5 / jqwik тестами") | **blocking for MATRIX use** — a skill that teaches the wrong framework for this repository is worse than no skill |
| `.opencode/skills/running-tests/SKILL.md` | the full `matrix-core` suite is owner-held | the skill's scope rules are written for the opencode-swarm CI, and it has no rule about MATRIX's D-017 | **blocking for MATRIX use** — it would run a suite the owner has not approved |
| `.opencode/skills/engineering-conventions/SKILL.md` | the subject is `opencode-swarm`, not MATRIX | `.opencode/skills/engineering-conventions/SKILL.md:3` | **incompatible for MATRIX use** — loading it loads another project's invariants |
| `.swarm/bundled-skills/plan.retired-backup/SKILL.md` | — | the directory says retired; the frontmatter still says `name: plan` | **obsolete + duplicated** — not a conflict with a rule, but an identity collision with a live skill |
| `.opencode/skills/coprocessor/SKILL.md` vs `~/.config/opencode/skills/coprocessor/SKILL.md` | — | the global copy has 13 extra lines (`## Harness Engineering Doctrine`, lines 66-78) that the repository copy lacks | **duplicated, divergent** — the copy in the repository is the one the auto-trigger resolves first, so a MATRIX session gets the *less* complete one |
| `opencode.json` | activating a skill requires the owner's approval | the repository file has only `$schema` and `instructions`; it holds no plugin, no agent and no MCP configuration | **no conflict** — and this is the reason the ecosystem cannot be extended from inside a session without the owner |

---

## 10. The MCP inventory

**No server was started and no external service was called.**

| server | where it is configured | purpose (from its name and the configuration's shape) | state |
|---|---|---|---|
| `context7` | `.veai/mcp_servers.json` and the global `opencode.json` | library documentation for the frameworks in use | **configured and verified** — it answered during this session |
| `memory` | `.veai/mcp_servers.json` and the global `opencode.json` | a knowledge graph across sessions | **configured and verified** — it answered during this session |
| `sequential-thinking` | `.veai/mcp_servers.json` and the global `opencode.json` | structured reasoning over a problem | **configured and verified** — it answered during this session |
| `docker-mcp` | `.veai/mcp_servers.json` | container control | configured, **unverified** |
| `kubernetes-mcp-server` | `.veai/mcp_servers.json` | cluster control | configured, **unverified** |
| `mcp-echarts` | `.veai/mcp_servers.json` | chart rendering | configured, **unverified** |
| `mcp-mermaid` | `.veai/mcp_servers.json` | diagram rendering | configured, **unverified** |
| `token-optimizer` | `.veai/mcp_servers.json` | token budgeting | configured, **unverified** |
| `IDE MCP Server` | `.veai/mcp_servers.json` (a URL entry, not a command) | an IDE bridge | configured, **unverified** |
| `openide` | `.codex/config.toml` (a URL entry; the value is not reproduced here) | the Codex IDE bridge | configured, **unverified** |
| `brave-search` | the global `opencode.json` | web search | configured, **unverified** |
| `chrome-devtools` | the global `opencode.json` | browser instrumentation | configured, **unverified** |
| `codegraph` | the global `opencode.json` | the code graph (`.codegraph/codegraph.db` exists in the repository) | configured, **unverified** — and the graph tools the bundled swarm skills assume are not exposed to this session |
| `fetch` | the global `opencode.json` | HTTP retrieval | configured, **unverified** — the harness's own `web_fetch` served this session instead |
| `filesystem` | the global `opencode.json` | file access | configured, **unverified** |
| `git` | the global `opencode.json` | git operations | configured, **unverified** |
| `github` | the global `opencode.json` | GitHub API | configured, **unverified** |
| `grep` | the global `opencode.json` | search | configured, **unverified** |
| `notion` | the global `opencode.json` | Notion | configured, **unverified** |
| `playwright` | the global `opencode.json` and `.veai/mcp_servers.json` | browser automation | configured, **unverified** |
| `postgres` | the global `opencode.json` | SQL access | configured, **unverified** |
| `qdrant` | the global `opencode.json` | vector search | configured, **unverified** |
| `sentry` | the global `opencode.json` | error tracking | configured, **unverified** |
| `sqlite` | the global `opencode.json` | SQL access | configured, **unverified** |
| `time` | the global `opencode.json` | clock | configured, **unverified** |

**19 servers in the global configuration, 10 in `.veai/mcp_servers.json`, 1 in
`.codex/config.toml` — 25 server entries in total, 3 of which are actually reachable from a
MATRIX session.** The repository's own `opencode.json` configures **none** of them: it holds
only `$schema` and `instructions`.

**The `.veai/mcp_servers.json` list is a private configuration, not a repository
capability.** A MATRIX session that needs a server uses the one the harness exposes
(`context7`, `memory`, `sequential_thinking`) or the built-in tools. It does not start a
server from `.veai/` — that file is an owner entry.

---

## 11. What is not here

**Skills the session catalogue lists that this repository does not contain.** The session's
catalogue carries 58 entries: 43 from `~/.config/opencode/skills`, 4 harness-bundled
(`agent-experience`, `cordis-composition-reference`, `cordis-plugin-development`,
`dsh-badge`), and 11 further names that the catalogue resolves. **17 of the 43 have no
copy in the repository**:

| skill | the repository's equivalent | the gap |
|---|---|---|
| `code-review` | `matt-pocock/code-review` | the repository holds the sub-skill, not the top-level entry |
| `codebase-design` | `matt-pocock/codebase-design` | as above |
| `diagnosing-bugs` | `matt-pocock/diagnosing-bugs` | as above |
| `domain-modeling` | `matt-pocock/domain-modeling` | as above |
| `grilling`, `grill-me`, `grill-with-docs` | `matt-pocock/…` | as above |
| `implement` | `matt-pocock/implement` | as above |
| `improve-codebase-architecture` | `matt-pocock/improve-codebase-architecture` | as above |
| `prototype` | `matt-pocock/prototype` | as above |
| `research` | `matt-pocock/research` | as above |
| `resolving-merge-conflicts` | `matt-pocock/resolving-merge-conflicts` | as above |
| `tdd` | `matt-pocock/tdd` | as above |
| `to-spec`, `to-tickets`, `wayfinder`, `wizard` | `matt-pocock/…` | as above |

So the gap is a **naming and visibility** gap, not a capability gap: the same 17 skills
are in the repository under `matt-pocock/`, reachable by the auto-trigger table, and also
at the machine level under their bare names. A reader who looks only at the repository's
root would conclude they are missing. A MATRIX session that asks the harness for
`matt-pocock/tdd` would get nothing — the harness never reads `.opencode/skills/`.

**What else is missing from the repository, and is not a skill:**

| thing | where it is | the note |
|---|---|---|
| the MATRIX-specific skills | **nowhere** | there is no skill that knows the two serving chains, the six cycles, the writer lock, the writer-lock refusal, the owner-held full suite, the eleven owner entries, the five governance registers, or the level vocabulary. Every MATRIX fact an agent needs today is re-derived from a document, every session |
| the governance registers as a machine-readable layer | `docs-v2/governance/` (9 files) | `docs-v2/governance/PROJECT-STATE.md`, `docs-v2/governance/ROADMAP.md`, `docs-v2/governance/OPEN-QUESTIONS.md`, `docs-v2/governance/RISK-REGISTER.md`, `docs-v2/governance/DECISION-REGISTER.md`, `docs-v2/governance/DOCUMENT-STATUS-POLICY.md`, `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md`, `docs-v2/governance/SESSION-HANDOFF-TEMPLATE.md`, `docs-v2/governance/AGENTS-AMENDMENT-PROPOSAL.md` — all dated 2026-11-04, and **no skill reads them** |
| the link and reference checker | nowhere | the T3 audit built one in `/tmp` and threw it away; nothing in the repository checks that a document's links resolve or that its status matches its content |
| a decision index the agent can query | `docs-v2/governance/DECISION-REGISTER.md` | 79 rows, written in this wave; not machine-readable, and not wired to any skill |

---

## 12. The commands

Every count in this document comes from one of these, run on 2026-11-04 at `a6ded233`:

```bash
git rev-parse HEAD; git branch --show-current

ls .veai/skills | wc -l                                   # 21
ls .opencode/skills | wc -l                                # 27
ls .swarm/bundled-skills | wc -l                           # 42
ls ~/.config/opencode/skills | wc -l                       # 43
ls .veai/rules/*.md | wc -l                                # 8
wc -l .veai/rules/*.md

# frontmatter, name and description, for every package
python3 - <<'PY'
import re
from pathlib import Path
for root in ('.veai/skills','.opencode/skills','.swarm/bundled-skills'):
    for d in sorted(x for x in Path(root).iterdir() if x.is_dir()):
        sk = d/'SKILL.md'
        print(root, d.name, 'SKILL.md' if sk.exists() else 'NO-SKILL.md')
PY

# the two copies of codebase-review-swarm
diff -rq .opencode/skills/codebase-review-swarm .swarm/bundled-skills/codebase-review-swarm
python3 .opencode/skills/codebase-review-swarm/scripts/validate-skill-package.py .opencode/skills/codebase-review-swarm
python3 .swarm/bundled-skills/codebase-review-swarm/scripts/validate-skill-package.py .swarm/bundled-skills/codebase-review-swarm

# the session's own skill root
readlink -f ~/.dsh/skills            # → ~/.config/opencode/skills

# MCP server NAMES only (no values, no server started)
python3 -c "import json;print(sorted(json.load(open('.veai/mcp_servers.json')).keys()))"
python3 -c "import json,os;print(sorted(json.load(open(os.path.expanduser('~/.config/opencode/opencode.json')))['mcp'].keys()))"

# the tools the bundled swarm skills need, versus the tools this session has
grep -rl "repo_map\|graph_health\|context_pack\|route_trace\|data_trace\|prepare_pr_workflow_checkout\|dispatch_lanes" .swarm/bundled-skills/ | wc -l

# the conflicts
grep -n "commit\|push\|git " .veai/skills/git-workflow/SKILL.md
grep -n "git commit\|push" .opencode/skills/commit-pr/SKILL.md
sed -n '11,13p' .veai/rules/proceed-autonomously.md
sed -n '/^## 3.2/,/^## 3.3/p' docs-v2/governance/AGENT-OPERATING-PROTOCOL.md
```
