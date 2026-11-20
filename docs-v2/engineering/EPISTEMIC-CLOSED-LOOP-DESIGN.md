# EPISTEMIC CLOSED-LOOP COGNITIVE ACTION — the design note (P2)

**Status:** `PROPOSED` — a design that the owner selected the target of (OD-055); the
implementation is **not** in this document and **not** in the tree.
**Date:** 2026-11-15 · **Initiative:** EPISTEMIC-INTEGRITY, workstream P2.

## Why this document exists

Chain A now serves a real bounded action over HTTP:
`POST /v1/workbench/workspaces/{id}/reports` — opt-in, local-only, consent-bound, digest-verified,
idempotent, concurrency-safe and restart-safe (the demonstration is
`docs-v2/engineering/C2A-HTTP-DEMONSTRATION.json`). What it does **not** do is pass through the
cognitive kernel: the route asks the workbench directly, so **nothing in the product chooses a
method, records why, or consults what happened last time**. A route that always behaves the same
is a service, not a cognitive loop.

P2 asks for one reachable chain:

```
user goal
  → the kernel's plan / method choice
    → a permissioned ActionRegistry / ArtifactAction
      → an external observed effect
        → an independently verified observation
          → a persisted trace
            → a later analogous task consults that trace and chooses differently
```

The owner selected the target of that chain on 2026-11-15 (OD-055): **the report journey the
product already has**. It is the one journey with a real user, a real route, a real artifact and
a real verification — so the loop can be closed on the path that already works, without a new
authority and without a new store.

## The design, in five parts

### 1. The kernel enters the path — without becoming a new engine

The route keeps its contract. What changes is that **before** the workbench is asked, the
cognitive kernel selects a method for the request (the method registry it already owns) and
records the choice. The kernel's plan is a **precondition**, not a new authority: the consent
flag, the output-root confinement and the action's own lease remain exactly as they are, and a
plan can never widen them. If the kernel has no method for the request, the route says so and
stops — it does not fall back to "no method chosen" silently.

### 2. The effect is observed independently of the system's assertion

The action's own report of success is **not** the observation. The observation is read from the
file system by an independent reader: the artifact exists, its size and its SHA-256 match the
digest the response reported, and the append-only index holds exactly one `STAGED` and one
`COMMITTED` record for the identity. A disagreement between the action's report and the
observation is a **failed effect**, recorded as such — never smoothed over.

### 3. The trace is persisted where the next task can read it

The trace records: the request, the method the kernel chose, the reason it chose it, the action
that ran, the observed effect, and the verification. It is written through the existing durable
append-only mechanisms (the action's index and the kernel's own store) — **no new database**,
and no new authority over the file system.

### 4. The next analogous task consults it, and the gain is measured

The acceptance test is behavioural, not structural: run the same kind of task twice — once
without a prior trace, once with — and measure whether the second run chooses differently and
whether the user-visible outcome improves (fewer refusals, a better-supported answer, a lower
cost). The comparison is against a **no-learning baseline** (the same journey with the trace
consult disabled), and the result is reported either way: if there is no measurable gain, that is
the finding, and the mechanism is investigated rather than hidden.

### 5. Replay and counterfactuals are explicit

A replay of the same trace must show **what changed and what did not**: the method chosen, the
effect, the verification and the inputs that differ. A counterfactual (the same request with a
different method forced) must show the difference in the observed effect. A trace that cannot be
replayed is not evidence of anything.

## What is deliberately not in scope

- No new network-facing capability, no shell, no arbitrary paths, no outbound requests.
- No change to the consent model, the output-root confinement or the writer's lease.
- No change to `K_MAX`, the trainer width or the fold — all owner-reserved.
- No change to the chain A / chain B boundary, and nothing that depends on the peer payload
  contract (also owner-reserved).

## Preconditions before implementation

| the precondition | why |
|---|---|
| the P0 epistemic semantics are stable and measured | the loop records the method's choice and the outcome; a wrong premise verdict would be learned as a good method — the loop must not be built on a path whose verdicts are still being defined |
| the P0.3 sealed evaluation exists | the loop's gain is measured against a frozen set with negative controls, not against the route's own convenience |
| the P0.4 isolation is in place | the loop's traces are learning input; they must not be able to contaminate the durable organic learning log |

## The honest risks

1. **The measured gain may be zero.** A route that always asks the workbench may be optimal for
   this workload, and the loop could add latency for nothing. The baseline comparison is what
   decides this, and a zero gain is a legitimate, publishable result.
2. **Method choice can be a new failure mode.** A wrong method choice produces a worse outcome
   than no choice at all. The refusal path ("no method for this request") must be safe and
   measurable.
3. **The trace is an attack surface.** It records the request and the method; it must not become
   a channel through which a caller influences a later task. The trace's contents are bounded by
   construction, and this is a review item, not an afterthought.

## Status and the next executable step

`PROPOSED`. The next executable step is a **read-only design probe**: measure, on the existing
route, how often a second analogous request would even consult a trace — i.e. whether the
journey repeats at all in a realistic sequence of requests. If the journey does not repeat, the
loop's premise is false and the honest conclusion is that P2 needs a different target; the
probe is cheap, touches no production behaviour, and it can start as soon as P0.1 lands.
