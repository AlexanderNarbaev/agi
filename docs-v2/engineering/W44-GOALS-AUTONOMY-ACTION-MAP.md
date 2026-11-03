# W44 — Goals, autonomy and action map

**Audit:** RECON-W44.0 · 2026-11-02
**Evidence:** `W44-CLASS-INVENTORY.csv`, `W44-CALL-GRAPH.csv` (bytecode), the goal and
action sources, and the formal specifications in `formal/`.

---

## 1. The goal models that coexist

| # | model | FQN | goal unit | persistence | served |
|---|---|---|---|---|---|
| 1 | the knowledge-store goals | `learning_goal` table in `KnowledgeStore` | a goal with subtasks and checkpoints | SQLite | yes |
| 2 | the goal manager | `io.matrix.knowledge.GoalManager` | the same goals, managed in memory | in-memory over the store's rows | yes |
| 3 | the goal tracker | `io.matrix.goals.GoalTracker` | tracked goals | — | yes |
| 4 | the task cell | `io.matrix.lifecycle.TaskCell`, `TaskCellV2` | a cell with a state machine | — | yes |
| 5 | the W44 cognitive task | `io.matrix.cognitive.CognitiveTask` | a bounded unit of internal work with a budget, a stop condition and an allow-list | SQLite (`cognitive_task`) | yes |
| 6 | the action-arena plan | `io.matrix.actions.ActionRegistry`, `PlanRunner` | a plan of actions with a contract | — | yes |

**No class reconciles them.** Models 1 and 2 are the same goals seen twice: the audit
verified in the bytecode that `KnowledgeWorkbench` calls **six** of `GoalManager`'s
methods — `checkpointGoal`, `completeSubtask`, `goals`, `integrity`, `resumableGoals`,
`startGoal` — so the manager is the workbench's view of the store's goal table, not a
second hierarchy. Models 3-6 are genuinely separate vocabularies.

**The W44 `CognitiveTask` is deliberately not a `LearningGoal`.** It carries `origin`,
`reason`, `servesGoal`, `expectedBenefit`, `costEstimate`, `completionCondition`,
`stopCondition`, `allowedActions`, `budgetWallClockMillis` and a status. It is generated
by a prediction error, it is bounded, and it never enters the goal hierarchy. That is the
only example in the repository of a task model designed *not* to compete with the others.

---

## 2. Do autonomous tasks have real operations, evidence, budgets, cancellation and completion?

Checked against the directive's list, from the sources:

| property | the W44 `CognitiveTask` | the older goal/task models |
|---|---|---|
| real operations | yes — one allowed action, `probe-microworld` | varies; `ActionRegistry` carries action contracts with `IoSchema` |
| evidence | yes — the error signal that generated it | `PlanRunner`/`ActionArena` carry an outcome |
| budget | yes — `budgetWallClockMillis`, honoured by the kernel's cycle budget | `ConjugateBudgeter` has a formal specification; the older loops have timeouts |
| cancellation | yes — a stop condition | **no general mechanism found** |
| persistence | yes — `cognitive_task` survives a restart | the goal table does; `TaskCell` does not |
| completion criteria | yes — a completion condition | `TaskCell`'s state machine |

**The W44 task is the only one with all six.** The older models have budgets without
cancellation, or completion without persistence.

---

## 3. The action layer

| capability | FQN | served | notes |
|---|---|---|---|
| the action arena | `io.matrix.actions.ActionArena` | yes (2 callers) | runs an action and returns an `Outcome` |
| the action registry | `io.matrix.actions.ActionRegistry` | yes | a list of action contracts with an `IoSchema` |
| the plan runner | `io.matrix.actions.PlanRunner` | yes | executes a plan |
| the plan preprocessor | `io.matrix.actions.PlanPreprocessor` | yes | — |
| the action gate | `io.matrix.consciousness.ActionGate` | yes | — |
| the gate policy | `io.matrix.ethics.ActionGatePolicy` | **inactive** | — |
| the FNL gate | `io.matrix.lifecycle.FnlGate` | **inactive** | `FnlGateV2` is served — the gate was replaced and the original kept |
| the budgeter | `io.matrix.budgeter.ConjugateBudgeter` | yes (6 callers) | formally specified: `formal/ConjugateBudgeterDP.tla` |
| the multi-budgeter | `io.matrix.budgeter.ConjugateBudgeterMulti` | **inactive** | — |
| the scheduler | `io.matrix.mediator.scheduler.TaskScheduler` | yes | — |

**The gates are duplicated and one of each pair is inactive.** `FnlGate` vs `FnlGateV2`,
`ActionGate` vs `ActionGatePolicy`, `ConjugateBudgeter` vs `ConjugateBudgeterMulti` — in
each case the older type has no production caller. The audit records this as
"replaced, not removed", which is a pattern: the repository keeps superseded mechanisms
alive with their tests.

---

## 3bis. Every budget and timeout claim, checked against its enforcement

The directive asks that every budget and timeout claim be checked against actual
enforcement. The audit checked the six budget mechanisms the repository has.

| budget | claim | actually enforced? | how |
|---|---|---|---|
| the W44 cycle budget | `CognitiveKernel.DEFAULT_CYCLE_BUDGET_MILLIS = 250` — "the budget a single cycle may spend" | **NO — declared only** | the kernel reads the wall clock once, for the timestamp string, and never compares it with the budget |
| `ComputeBudget` | `maxWallClockMillis`, `maxCpuMillis`, `maxMemoryBytes` | **NO — declared only** | its own javadoc: *"The budget is a DECLARATION, not an enforcement mechanism."* `fits(...)` compares a **declared estimate** at admission time |
| `ComputeBudget.maxSearchNodes` | a hard cap on the search | **YES** | `BoundedUniformCostSearchMethod` and `EntailmentMethod` count their nodes and throw `ResourceLimitException` |
| `ActionArena`'s per-cell budget | `maxBudgetMs` | **YES** | four distinct outcomes: `EXECUTED`, `REJECTED_OVER_BUDGET`, `REJECTED_QUEUE_FULL`, `TIMEOUT` |
| `KnowledgeConsolidator.Budget` | `maxForgotten` (100 by default), `ageWindowMs` (30 days) | **YES** | the candidate list is truncated to the budget; the report says how many were actually marked |
| `ConjugateBudgeter` | the per-period row-budget allocation | **YES, as an allocation** | it *computes* an allocation under `Σ c_i·x_i ≤ e`; it does not interrupt anything, and it has a formal specification (`formal/ConjugateBudgeterDP.tla`) |
| `CognitiveTask.budgetWallClockMillis` | the budget of a generated internal task | **NO** | the field is recorded and reported in the trace, and the task's allow-list bounds what it may do; the wall-clock figure itself is not checked anywhere |

**The finding.** Only **three** of the seven budgets are enforced at runtime, and the two
that the W44 documents lean on hardest — the cycle budget and the `ComputeBudget`'s
wall-clock limit — are declarations. The word "bounded" in those documents reads as
"enforced"; it is not.

**What this does and does not invalidate.** It does not invalidate the W44 kernel's
*behavioural* bounds: the task's allow-list (`probe-microworld` only), its stop condition
and its completion condition are real and do bound the work. It invalidates a **timing**
guarantee: nothing promises that a cycle returns within 250 ms, and nothing enforces it.

**The fix is small and worth doing before the next integration:** the kernel already
supplies a `Supplier<String>` for timestamps; a `Supplier<Long>` of nanoseconds would let
the cycle check its own budget at the phase boundaries and refuse to start a probe it
cannot afford. The repository already has the pattern — `ActionArena` does exactly this.

## 4. The autonomy loops

| capability | FQN | served | notes |
|---|---|---|---|
| the agent loop | `io.matrix.agent.AgentLoop` | yes | — |
| the multi-agent loop | `io.matrix.agent.MultiAgentLoop` | **inactive** | — |
| the ReAct loop | `io.matrix.agent.react.ReActAgentLoop` | yes (1 caller) | with a `ReasoningTrace` |
| the autonomy engine | `io.matrix.autonomy.AutonomyEngine` | yes (5 callers) | its launcher is in `io.matrix.api.llm_quarantine` |
| self-improvement | `io.matrix.autonomy.SelfImprovingEngine` | yes | the launcher next to it is **dormant** |
| the autonomy loop | `io.matrix.brain.runtime.AutonomyLoop` | yes | inside the brain runtime |
| the self-agent | `io.matrix.agent.SelfAgentResource` | yes (REST) | a REST entry point that exposes the agent loop |
| the proactive chat | `io.matrix.api.llm_quarantine.PilotProactiveChat` | — | dormant, in the quarantine package |

**`llm_quarantine` is a quarantine.** The package name marks what is inside it: an
LLM-backed brain loop service, an autonomy-engine launcher, a self-improving-engine
launcher and a proactive chat. **All of them are unreachable.** This is the repository
keeping an LLM path in the tree, isolated and disabled — consistent with CONSTITUTION
Article I.

---

## 5. What the map forces

1. **Retire the superseded gates.** `FnlGate`, `ActionGatePolicy`,
   `ConjugateBudgeterMulti` and `MultiAgentLoop` are inactive versions of served
   mechanisms. Each is a trap for the next reader.
2. **Do not add a fifth goal model.** The W44 `CognitiveTask` is the model to copy: it is
   bounded, it does not enter the hierarchy, and it has all six properties.
3. **Give the older task models a cancellation path**, or say plainly that they do not
   have one.
4. **Keep `llm_quarantine` quarantined.** The audit found no path from an entry point
   into it; that is the correct state and it should be asserted by a test rather than by
   convention.
