# MATRIX Stage Routing Table

- **Status:** normative reference, derived from code
- **Date:** 2026-09-29 (RECON-W28, B-9)
- **Scope:** `MindCycle.think(String)` — the serving path used by `/v1/analyze` and chat

This table replaces the comment-only routing documentation. Every row is read off the
code, not from intent: the **Priority** column is the literal order of the `if` chain
in `MindCycle.think`, and the **Engine** column names the class that produces the
answer. If code and this table ever disagree, the code is right and this file is a bug
— the companion test `MindCycleRoutingTableTest` fails when they drift.

## Pre-routing (before any stage)

| Check | Behaviour | Location |
|---|---|---|
| `input` null or blank | returns empty result, no stage runs | `MindCycle.java:130` |
| `ReflexStage.shortcut()` | immediate reply, all reasoning stages skipped | `MindCycle.java:138` |

## Resolution chain

Stages are evaluated in this order and the **first match wins**. There is no
confidence arbitration between stages: a later, higher-confidence stage cannot
override an earlier match. Confidence is only used *after* a match is chosen, to
report a score (see the confidence column below).

| # | Stage | Matches when | Engine | Confidence source |
|---|---|---|---|---|
| 1 | `ArithmeticStage` | ≥2 operators in the normalised input → compound path; else a single `<num> <op> <num>` pair matches | `PlanningStage` (MCTS) for compound; direct evaluation for a single pair | `max(arith.confidence, salience.score)` |
| 2 | `AnalogyStage` | "X is to Y as P is to ?" / `A:B::C:?` | `AnalogyStage` | `max(analogy.confidence, salience.score)` |
| 3 | `RelationalReasoningStage` | transitivity ("a is b, b is c → a is c") or unanimous attribute ("a and b are both red → a is red") | `RelationalReasoningStage` | not in the confidence chain; returns its own |
| 4 | `BilingualFactLookup` | capital/EN-RU country-capital lookup (`wantsCapital` gate) | `BilingualFactLookup` (61-entry table) | not in the confidence chain |
| 5 | `BirInferenceStage` | a registered BIR rule matches | `BirInferenceStage` + `TsetlinStage` | `max(bir.confidence, salience.score)` |
| 6 | `HdcRetrievalStage` | HDC nearest-neighbour exceeds threshold | `HdcRetrievalStage` | `max(hdc.confidence, salience.score)` |
| 7 | `PlanningStage` (MCTS) | `mctsReply` non-blank — reached only when no earlier stage matched | `PlanningStage` | `max(mctsConfidence, salience.score)` |
| 8 | `TsetlinStage` | matched and non-blank | `TsetlinStage` | not in the confidence chain |
| 9 | `ModulatorStage` | **always runs**, after a reply is chosen | `ModulatorStage` (FROZEN) | veto caps confidence at 0.30 |
| — | explicit refusal | no stage matched | `MindCycle` | — |

Two consequences worth stating, because they are easy to misread as bugs:

- **Row 4 is a lookup, not reasoning.** It answers only the 61 country-capital pairs
  it holds. It is not a general bilingual capability and must not be counted as one.
- **Row 7 is a fallback, not a planner-of-record.** `PlanningStage` produces the MCTS
  answer for compound arithmetic (row 1) and otherwise only runs when rows 1–6 all
  missed, so its contribution to the compound-arithmetic probes comes through row 1.

## Why arithmetic is row 1 and why that was a bug

`ArithmeticStage` runs before the analogy and relational stages because an expression
such as `2 + 3 * 4` must be evaluated, not pattern-matched as a relation. W23 found
that the ordering inside `ArithmeticStage` itself was inverted: a greedy binary regex
ran first and `find()` returned only the first operand pair, so

```
"2 + 3 * 4"  ->  matched "2 + 3"  ->  "2 + 3 = 5"   (expected 14)
"5 - 1 + 2"  ->  matched "5 - 1"  ->  "5 - 1 = 4"   (expected 6)
```

and the compound branch was therefore unreachable for exactly the inputs it was written
for. Compound detection now runs first, and the regex survives only as the fast path
for unambiguous single-operation input. See `ArithmeticStage.java:53-71`.

## Registry write path (not part of resolution)

`/v1/bir` registration is a separate path with its own gates, documented here because
it is a second place where a rule can enter the system:

| Order | Gate | On failure | Location |
|---|---|---|---|
| 1 | `ModulatorStage.gate(subject, answer)` — FROZEN ETHICAL_FILTER / SAFETY_MONITOR / CONSISTENCY_CHECKER | HTTP 403, `modulators_fired` returned | `MinimalHttpServer.java` `handleBir` |
| 2 | `BirKnowledgeBase.register` — contradiction precondition (subject+answer fingerprint) | quarantined, not merged | `BirKnowledgeBase.register` |

Gate 1 was added in RECON-W28. Until then a registration was gated only by
contradiction detection, so content the answer path refuses outright could be written
into the registry, where it would later be retrieved and served — the policy was not
one policy. The negative controls are in `BirWriteFROZENGateTest`.
