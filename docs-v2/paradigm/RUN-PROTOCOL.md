# RUN-PROTOCOL — How Individual RUNs Are Executed

**Status:** normative · **Version:** v1 · **Date:** 2026-09-07

> Defines the standard pattern for each RUN. Each RUN is a
> minimal value unit. Multiple RUNs form a wave; multiple waves
> form a phase.

---

## 1. RUN Lifecycle

```
PLAN → IMPL → VERIFY → REVIEW → COMMIT → LOG
```

1. **PLAN** — pick smallest next actionable thing. Write intent to WAL.
2. **IMPL** — implement with minimal diff.
3. **VERIFY** — run tests, types, lints; capture evidence.
4. **REVIEW** — sub-agent or self-review against criteria.
5. **COMMIT** — atomic commit, message references RUN number.
6. **LOG** — WAL entry + (if phase boundary) FINALSUMMARY update.

---

## 2. RUN Numbering

- Each RUN has a unique global integer.
- WAL entries: `RUN N` (without prefix if used as section).
- Git commits: `WAL: RUN N — <short summary>` (≤72 chars in title).

---

## 3. RUN Plan Format

Before starting a RUN, the WAL entry should contain:

```
RUN NNN — <title>

Plan:
- Goal: <one sentence>
- Files: <list>
- Tests: <expected new count>
- Risk: <known issues>

Verification:
- Command: <gradle invocation>
- Expected: <outcome>
```

---

## 4. RUN Acceptance

A RUN is DONE when:
- All planned files are modified.
- All planned tests pass.
- Coverage maintained or improved.
- `git status` clean except RUN output.
- Commit created.

A RUN is BLOCKED when:
- External dependency missing (record in WAL).
- Tests fail in unexpected way (debug first, then plan again).
- RFC needed (record in WAL, stop RUN).

---

## 5. Wave Composition

A wave = 5-10 RUNs typically.

Wave lifecycle:
1. Pick theme (often = one module, e.g., "Memory M0").
2. Plan RUNs (write to PHASES.md if not yet there).
3. Execute RUNs.
4. Wave-EXP: end-to-end test of wave outputs.
5. Wave review (sub-agent or self).
6. Wave commit (1 summary commit).
7. WAL entry.

---

## 6. Subagent Usage

Subagents are used for:
- Bounded implementation subtasks (e.g., "implement TreeMap with
  these specs in this file")
- Codebase exploration (MCP codegraph)
- External research (MCP context7, web search)
- Test review, security review (Goal Guard)

Subagents are NOT used for:
- Architectural decisions (main agent only)
- Decisions that touch FROZEN zones
- Decisions that change CONSTITUTION

---

## 7. Skills Usage

Skills applicable:
- `superpowers:test-driven-development` — for any new feature/bug
- `superpowers:verification-before-completion` — before claiming done
- `superpowers:requesting-code-review` — at RUN boundaries
- `matt-pocok:codebase-design` — at module boundaries
- `matt-pocok:domain-modeling` — when terms evolve

---

## 8. MCP and Tool Usage

Priority order (per Goal Mode spec):
1. **codegraph_explore** — first call for any code question
2. **context7** — for library docs / API references
3. **browser/chrome-devtools** — for UI verification if needed
4. **delegate_task** — for parallel subagent work
5. **filesystem** — for file ops
6. **bash** — for shell ops (gradle, git, model serving)
7. **lsp_diagnostics** — before claiming done

---

## 9. Instrumentation

Each RUN SHOULD add at least one **observable**:

- Counter (Micrometer): e.g., `matrix_decoder_total`
- Gauge (Micrometer): e.g., `matrix_bir_units_active`
- Trace span (x-matrix-trace): every decision
- Log line: at INFO level for transitions

This instrumentation requirement ensures the system is observable
end-to-end before pilots.

---

## 10. Anti-patterns in RUNs

- ❌ RUN that touches multiple unrelated modules
- ❌ RUN that adds a feature without a test
- ❌ RUN that commits without running tests
- ❌ RUN that claims a number without EXP
- ❌ RUN that bypasses ActionGate to "fix something fast"
- ❌ RUN longer than ~30 min of agent wall time without subagent
    delegation

---

## 11. Run Report Template

After RUN closes, append to WAL:

```
## RUN NNN — <title> (YYYY-MM-DD HH:MM)

- <bullet 1: what was done>
- <bullet 2: what was tested>
- <bullet 3: what evidence captured>

Evidence:
- /path/to/test-results.xml
- /path/to/EXP-XXX-report.md
- /path/to/commit
```
