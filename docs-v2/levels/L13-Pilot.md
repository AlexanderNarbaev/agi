# L13 — Pilot Deployments

**Status:** normative · **Layer:** 13 (pilots) · **Date:** 2026-08-26
**Changelog:** 2026-08-26 — brain wave v2; densified from archive copy
archive/2026-08-pre-v2/docs-root-flat/L13_Pilot.md.

## 1. Purpose

Pilots turn specs into measured artefacts. Each pilot exercises
several layers under load and produces a reproducible result
(binary, dataset, runbook) plus a metrics report. A pilot fails if
any SLO / acceptance number is missed; it iterates until green or
is descoped.

## 2. Pilot Roster

| # | Name | Layers | Headline metric |
|---|---|---|---|
| P1 | Gridworld agent | L1, L4, L5 | survival ≥ 80 % after 200 generations |
| P2 | Proactive chat | L1, L4, L5, L7 | response p95 < 100 ms; 100 % prohibition block rate |
| P3 | Smart home | L1, L4, L7, HADES | preference recall ≥ 80 % at day 7; recovery < 5 s |
| P4 | Robot arm | L1, L5, L7 | grasp ≥ 90 % after 100 generations; control loop < 1 ms on MCU |
| P5 | Cauldron new FNL | L1, L4, L5, L7 | FNL creation < 1 h on 1 k examples; new FNL acc ≥ 90 % |
| P6 | HADES recovery | L2, L3, L5, L6 | detect-to-isolate < 5 s; restore < 1 min; 0 lost Kafka signals |
| P7 | Noosphere FNL transfer | L5, L6, L7 | import + integrate < 1 min; ≥ 5× training-time speed-up for the receiver |

## 3. P1 — Gridworld Agent

An agent with 4–8 MPDT neurons learns survival via a genetic
algorithm. Inputs are binary environment features (food proximity,
danger, day / night); outputs are primitive actions. Logs show
chaotic behaviour at generation 0, food seeking by ~50 generations,
shelter building by ~200. Deliverable: video timeline; readable
decision tree of the best agent; per-action energy comparison vs a
small neural controller.

## 4. P2 — Proactive Chat

A chat surface (Telegram or web) using the MPDT core, proactively
opening dialogue under driver D_social, accepting new facts
("remember: my favourite colour is blue"), refusing prohibition-
violating requests with a logged explanation. Each reply carries an
interpretable rule chain the user can request.

## 5. P3 — Smart Home

An instance wired to ESP32 sensors via the Multimodal Proxy,
learning user preferences for lighting / temperature / quiet hours.
A simulated sensor failure must trigger HADES: safe mode, user
notification, recovery after swap. Deliverable: accuracy-over-time
curve, recovery video, readable decision tree of the current policy.

## 6. P4 — Robot Arm

A simulated or real arm with several DOF; train a motor FNL for
pick-and-place via the genetic algorithm; compress the resulting
tree to run on an ESP32-class MCU. Comparison: size and energy vs
a PID baseline and a small neural controller.

## 7. P5 — Cauldron New FNL

The instance detects an accuracy gap (e.g. two new image classes),
Cauldron produces a new FNL under governance, the filter clears it,
the user is notified, and on acceptance the FNL is registered
locally and (optionally) published. Time and accuracy are reported.

## 8. P6 — HADES Recovery

Induce Derangement in one cluster; demonstrate detection, isolation,
traffic re-route to a reserve, rollback to the last STABLE snapshot,
full restore. Report: detection-to-isolation latency, restoration
time, queue loss, postmortem.

## 9. P7 — Noosphere FNL Transfer

Instance A solves a task and publishes the resulting FNL; instance B
imports it, runs the Ethical Filter, adapts locally, and starts
solving the same task markedly faster than training from zero.
Speed-up factor and import latency are recorded.

## 10. Phasing

P0 Spark → P1; P1 Cell → P3 + P2 (no proactivity); P2 Organism →
P2 (full), P6, P4; P3 Noosphere → P5, P7; P4+ → full Minecraft
agent and physical robots. Phases are gates: each closes only on
green SLO for the prior phase.

## 11. Outputs and Proposal Path

For each pilot: a screencast, a runnable notebook where applicable,
a metrics report, and a postmortem after iteration. Artefacts land
under `demos/`; reproductions must be byte-stable. New pilots open
as `pilot-proposal` issues with idea, demonstrated properties,
layers used, and expected metrics; accepted proposals may qualify
for community funding.

Next: L14 long-horizon — sustainability and capacity planning.
