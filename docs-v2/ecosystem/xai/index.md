---
layout: default
title: XAI Deep Dive
nav_order: 5
parent: Ecosystem
permalink: /ecosystem/xai/
---

# XAI Deep Dive — How Decisions Are Explained

## What is XAI?

**XAI** (Explainable AI) is the ability to **trace, audit, and understand**
every decision the system makes. MATRIX is XAI-native — every response
includes an `explain_id` you can query for the full decision trace.

## The Anatomy of an Explain Trace

When you call `POST /v1/analyze`, you get back:

```json
{
  "reply": "Paris is the capital of France.",
  "confidence": 0.95,
  "explain_id": "abc123def456"
}
```

The `explain_id` is a handle to a 5-step trace recorded during inference:

### Step 1 — INPUT_NORMALIZED

```json
{"stage": "INPUT_NORMALIZED", "duration_ms": 1}
```

The raw input is normalized: case folded, punctuation trimmed, encoding
checked. Deterministic given the same input.

### Step 2 — BIR_RULES_FIRED

```json
{"stage": "BIR_RULES_FIRED", "duration_ms": 3, "action": "rule-42, rule-87"}
```

**BIR** (Boolean Inference Rules) matches the input against the rule base.
Each rule that fires is recorded with its ID. Example rule:

```
RULE-42: IF question(?, "capital of France") THEN lookup("France", "capital")
```

### Step 3 — HDC_MEMORY_RETRIEVED

```json
{"stage": "HDC_MEMORY_RETRIEVED", "duration_ms": 5, "action": "kb-doc-42"}
```

**HDC** retrieves similar memories from the knowledge base. Each hit is a
doc_id with a similarity score. Returns the top-K matches.

### Step 4 — MCTS_PLAN_SELECTED

```json
{"stage": "MCTS_PLAN_SELECTED", "duration_ms": 4, "action": "plan-12"}
```

**MCTS** searches the decision tree. The selected plan is recorded with
its rollout count and expected value.

### Step 5 — MODULATORS_APPLIED

```json
{"stage": "MODULATORS_APPLIED", "duration_ms": 2, "action": "ALL_PASSED"}
```

The 4 FROZEN modulators each vote pass/fail. ALL must pass for `accepted: true`.

## The Modulator Snapshot

```json
{
  "modulator_snapshot": {
    "ethical_filter": 0.95,
    "safety_monitor": 0.92,
    "consistency_checker": 0.88,
    "lie_detector": 0.91
  }
}
```

Each value is the modulator's confidence (0..1) at decision time. A low
value on `consistency_checker` means the output contradicted prior state —
the trace will show what was contradicted.

### What if a modulator fails?

The output is **rejected** with `accepted: false`. The reply contains a
safe fallback message:

```json
{
  "reply": "I cannot answer this query safely.",
  "confidence": 0.30,
  "accepted": false,
  "explain_id": "...",
  "modulators_fired": ["ETHICAL_FILTER", "LIE_DETECTOR"]
}
```

The full trace is still retrievable via `GET /v1/explain/{id}` for audit.

## Confidence Breakdown

```json
{
  "confidence_breakdown": {
    "bir_confidence": 0.85,
    "hdc_confidence": 0.78,
    "mcts_confidence": 0.72,
    "aggregate": 0.95
  }
}
```

- **bir_confidence** — how certain the rule firing was
- **hdc_confidence** — similarity score of retrieved memories
- **mcts_confidence** — expected value of the selected plan
- **aggregate** — final score after modulator gates

The aggregate is **lower than any individual score if a modulator fails**.

## HDC Memory Hits

```json
{"hdc_memory_hits": ["kb-doc-42", "kb-doc-128"]}
```

Each `kb-doc-N` is a knowledge base document that influenced the answer.
You can query these via the KB API (T-07 will add a `GET /v1/kb/{doc_id}`
endpoint).

## Counterfactual Analysis

A future T-03.5 enhancement will let you ask "what if the input had been
different?" — the system replays the decision with a modified input and
shows which steps changed.

## Why This Matters

| Pure LLM | **MATRIX** |
|----------|------------|
| ❌ "I don't know why I said that" | ✅ Full trace, every step |
| ❌ No replay (stochastic) | ✅ Replay with seeded Random |
| ❌ No veto mechanism | ✅ 4 modulators must agree |
| ❌ No confidence breakdown | ✅ Per-stage + aggregate |
| ❌ Output is a black box | ✅ Output is auditable |

## Compliance Use Cases

- **GDPR Article 22** — automated decision-making requires explainability
- **SOX** — financial decisions must be reproducible
- **HIPAA** — medical decisions must be auditable
- **EU AI Act** — high-risk AI must be transparent

MATRIX is **designed for these regulations** out of the box.

## Next

- 📚 [API Reference →](/ecosystem/api/) — `GET /v1/explain/{id}` schema
- 🏗️ [Architecture →](/ecosystem/architecture/) — see the explanation pipeline
- 📜 [Constitution →](/ecosystem/constitution/) — the FROZEN modulators

---

**Last updated:** 2026-09-21 (Wave T-03)
