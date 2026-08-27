# L4 — Mediator (hierarchy, drivers, proactive engagement)

**Status:** normative · **Layer:** 4 (coordination) · **Date:** 
/scientific tone; archive reference added.

## 1. Scope

Layer 4 specifies the Mediator hierarchy, the driver control
loop, goal generation, the priority scheduler, and the proactive-
engagement protocol. The Mediator does not own inference; it
schedules and gates it. Truth-table semantics remain in L1;
consensus rules in L2; the ethics gate in L7.

## 2. Hierarchy

Four levels, each with a weight w ∈ [0, 1]:

- LobeMediator (per FNL, w ≈ 0.2): local optimisation, executes
 commands from the cluster mediator.
- ClusterMediator (per cluster actor, w ≈ 0.5): load balancing,
 metrics aggregation, instance escalation.
- InstanceMediator (Pekko singleton per deployment, w ≈ 0.8):
 cross-cluster coordination, driver dynamics, user dialogue.
- GlobalMediator (council of instances, w = 1.0): protocol
 evolution, large mutation acceptance.

Weights shift over time according to the same function used in
Proof-of-Accuracy (L2). Communication uses protected Kafka topics
`mediator.control.{level}.{id}` and `mediator.metrics.{level}.{id}`.

## 3. Driver System

A driver is a homeostatic variable dᵢ ∈ [0, 1] where 0 = satisfied
and 1 = maximal demand. Drivers update as
`dᵢ(t+1) = clamp(dᵢ + αᵢ (targetᵢ − dᵢ) + noiseᵢ)`.

Eight drivers: Energy, Safety, Curiosity, Entropy, Social,
Self-Actualisation, Attention, Ubuntu. Threshold band:
THRESHOLD_HIGH = 0.7 generates a goal; THRESHOLD_LOW = 0.1 marks
the driver as dormant. Allocation across drivers targets a
~61.8 / 38.2 split between foundational and developmental
drivers; the ratio is a target, not a hard rule.

## 4. Goal Generation

A driver crossing THRESHOLD_HIGH emits a Goal: a boolean predicate
the planner must satisfy. Goals carry priority, deadline, and
status (PENDING / ACTIVE / SATISFIED / FAILED). Every new goal
enters the Meta-Goal Validator (sandboxed simulation) which
checks axiom consistency, conflict with active goals,
irreversibility, and long-term user interests. Rejected goals are
escalated to the user with a written rationale.

## 5. Priority Scheduler

Tasks derive from goals. Per cycle, priority is recomputed as
`base * age_factor * resource_factor`, where age_factor is an
exponential function of wait time (anti-procrastination). The head
of the queue is passed through EthicalFilter (L7). Tasks without
sufficient budget return to the queue with growing age_factor.
Resource budgets are dynamic; safety tasks are exempt from the cap.

## 6. Proactive Engagement

Conditions for unsolicited dialogue: high D_social after silence;
high D_curiosity plus a noteworthy internal event; anomaly
detection even at low D_safety; evolutionary milestone reached;
explicit user request. Default is OFF; the user selects a level
from "on demand" to "initiating partner". Monotonic adaptation
reduces D_social weight when initiatives are repeatedly ignored.

## 7. Ethical Mechanisms

- Ethical Moment: pre-action self-check against the three
 prohibitions and the ethical gradient. Logged; blocks on
 failure.
- Reconciliation Circle: neutral facilitator; talking-token;
 decision by consent, not majority. Skipped on prohibition
 violations.
- Council Protocol: PoA-weighted vote, 2/3 threshold, distributed
 ledger entry. Falls back to local-only when council unreachable.
- Right of Refusal (Eleutheria): any instance may veto an action
 that violates its local policy; the veto requires external
 cryptographic consensus to override. Frozen rule.

## 8. Cross-Layer Hooks

The Mediator commands clusters via control topics; it does not
intercept local inference. State is event-sourced; cold reboot
replays the journal. Snapshot schema: MediatorSnapshot (record:
mediatorId, level, weight, generation, drivers, goals,
taskQueue, timestamp).

> framed the Mediator as a "hormonal and motivational subsystem".
> The v3 text reframes it as a weighted scheduler with a driver
> control loop and a consent-based dispute protocol. Archive

Next: L5 Cauldron & Evolution — mutation acceptance pipeline.