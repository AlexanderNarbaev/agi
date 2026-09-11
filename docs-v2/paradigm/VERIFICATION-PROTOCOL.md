# VERIFICATION-PROTOCOL — How Claims Become Evidence

**Status:** normative · **Version:** v1 · **Date:** 2026-09-07

> Mirrors CONSTITUTION VI ("no false claims"). Every numerical
> or behavioral claim must trace to a recorded EXP run. This doc
> is the operational protocol for that.

---

## 1. Claim Lifecycle

```
Hypothesis → EXP Setup → EXP Run → EXP Report → Claim Card → WAL
   (text)    (config)    (data)    (markdown)    (one page)  (log)
```

1. **Hypothesis Card** in `research/HYPOTHESES.md` or `HYPOTHESES-NEW.md`.
2. **EXP Setup**: explicit configuration (code SHA, inputs, environment).
3. **EXP Run**: produces raw data (CSV, JSON, log).
4. **EXP Report**: markdown template in `research/reports/EXP-<NNNN>-report.md`.
5. **Claim Card**: distilled version, used in PRs/docs/CHANGELOG.
6. **WAL**: entry pointing to claim card.

---

## 2. Hypothesis Card Format

```markdown
## H-NNN — <Title>

**Question:** <one-sentence falsifiable question>

**Variables:**
- Independent: <what we vary>
- Dependent: <what we measure>

**Success criterion:** <threshold>

**Status:** pending / accepted / refuted / inconclusive

**Date:** YYYY-MM-DD

**References:** <links to spec/design/tickets>
```

---

## 3. EXP Run Procedure

1. Fix code SHA in environment (`git rev-parse HEAD`).
2. Capture environment: CPU model, RAM, GPU if any, Java version.
3. Define input set explicitly.
4. Run N≥3 times; report median, p95, std-dev.
5. Capture artifact files (CSV, JSON, log).
6. Generate EXP report from template.

No numbers in reports without raw data alongside.

---

## 4. Acceptance and Refutation

- **ACCEPTED**: data meets success criterion in ≥3 runs.
- **REFUTED**: data contradicts criterion OR control experiment shows artifact.
- **INCONCLUSIVE**: insufficient data or environmental noise.

A claim is **only valid** when its hypothesis is ACCEPTED.

---

## 5. EXP Required Per Module

Every module added in a phase must have at least one EXP:

| Module | Required EXP |
|---|---|
| BirUnit | H-006 (boundary), H-007 (K_MAX) |
| BRC | BRC-Step TLA+ + EXP run |
| AttentionRouter | H-045 (top-down×bottom-up) |
| ActionGate | H-038 (4-cascade) |
| ConsolidationCycle | H-046 (TR/REM) |
| DistillCli | Distillation throughput EXP |
| Pilot #1 | Survival >80% by gen 200 |
| Pilot #2 | 100% prohibition block |

---

## 6. False Claims — CONSTITUTION VI

Forbidden phrases in code, docs, comments, reports:

- "AGI", "general intelligence", "superintelligence", "sentient"
- "never lies", "never forgets", "perfectly safe"
- "X times faster" without explicit EXP run with that exact number
- Any percentage or multiplier without an EXP

Replacement: always reference the EXP-report file path.

Example:
```
WRONG: "Our pipeline is 15× faster than CPU."
RIGHT: "Per EXP-0012-report, GPU pipeline achieves 15.40× speedup
        vs CPU baseline (median, 5 runs)."
```

---

## 7. Reviewer Audit

Every PR with behavioral claims MUST be reviewed by:
- `goal-reviewer` (correctness)
- `goal-test-reviewer` (test adequacy)
- `goal-verifier` (EXP numbers reproduced)

Goal Guard review gates run automatically; this protocol is the
operational layer behind them.

---

## 8. Audit Trail

Each EXP is appended to:
- `WAL.md` (one-liner)
- `research/reports/EXP-<NNNN>-report.md` (full)
- `HYPOTHESES.md` (status update)
- `TRACEABILITY.md` (mark acceptance)
- `FINALSUMMARY.md` (when phase closes)

This 5-way redundant trail makes any claim independently verifiable.
