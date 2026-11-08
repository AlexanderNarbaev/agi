# W51 — Recon: Environment / micro-world / simulation inventory

**W51 §7 — "Learning through an environment"** · recon only, read-only · branch `develop`
**Method:** `grep` / `glob` / `read` only. No file in `src/` was modified. No test suite was run.

**Question this document answers:** what simulation / micro-world / environment code ALREADY
exists in the MATRIX monorepo, so that W51 §7 does not build a duplicate.

**Short answer:** a complete, deterministic, production-wired micro-world already exists —
`io.matrix.cognitive.microworld` — with a hidden rule, a coded disposition state machine, a
measurable success condition and an end-to-end serving path (`POST /v1/cognitive/observe`).
There is **no** MDP, **no** transition model `P(s'|s,a)`, **no** reward signal and **no**
seeded episode runner for it. Every other "environment" in the repo is a *separate* contour
(GridWorld GA pilots, Minecraft sandbox, SCADA, active inference) that is **not** connected to
the microworld and mostly not connected to any learner.

---

## 1. Inventory — every environment-like component in the monorepo

Path column is relative to the repository root; `matrix-core/src/main/java/` is abbreviated
as `core/`.

| # | Path | Purpose | Real usable env.? | Determinism | State-transition model | Hidden rule to learner | Measurable success | Seed / episode id |
|---|---|---|---|---|---|---|---|---|
| **A** | `core/io/matrix/cognitive/microworld/` (12 classes) | **The micro-world**: `LampEnvironment` (2 inputs → 1 outcome, hidden rule), `MicroWorldSession` (learner's hypothesis set, predictions, errors) | **YES — real, production-wired** | **Full.** No clock, no `Random`, no wall-clock in any decision path. Same probes ⇒ same observation ids, same survivors, same proposals | **Not MDP.** Static response function (assignment → outcome). The only real transition is `observation → narrowed hypothesis set` (`MicroWorldSession.reclassify()` :568) | **YES, structurally.** `applyHiddenRule` is `private` (:126), class is `final`; interface exposes only `probe`/`variables`/`outcomeVariable`. Guard test: `LampEnvironmentTest` :131-146 (reflection) | **YES.** `survivingHypotheses().size() == 1` **and** the survivor is `h-0001` (conjunction) | **No seed at all** (nothing random). Episode id is a **caller-supplied string**; obs ids `obs-N`, `obs-adv-N`; experiment ids `exp-<episode>-N`; error ids `err-<episode>-N` |
| **B** | `core/io/matrix/cognitive/{MicroWorldCodec,MicroWorldComputation,MicroWorldReferenceMethod,MicroWorldSettledModelMethod}.java` | Kernel-side adapters over the microworld: durable-case codec, computation payload, reference (enumerate) method, fast path (settled model) | **YES** | Deterministic; reference is O(2^v·m) exhaustive | Same as A | Same as A (the reference re-derives the model from *eligible* observations only) | `MODEL_SETTLED` fact + `MINIMUM_VALIDATION_CASES = 2` (`CognitiveKernel` :105) | n/a |
| **C** | `core/io/matrix/cognitive/CognitiveKernel.java` (1005 lines) | **The learning loop that consumes A**: perceive → restore → predict → select → compute → propose → probe → observe → learn → regulate → answer | **YES** | Deterministic given a fixed `IdSource` + fixed timestamp supplier + fixed `monotonicNanos` | The cycle is the transition model; durable state in SQLite | The kernel never sees the rule; it only sees observations | Model settled + trace has 9 phases; error/task generated on contradiction | `ep-<signalId>`; signal id = `sig-<UUID>` in production |
| **D** | `core/io/matrix/simulation/` (`World`, `Grid`, `AgentBody`, `AgentBrain`, `SimulationRunner`, `SimulationResult`, `CellType`, `Direction`, `Position`, `ClusterTopology`, `AgentClusterBrain`) | **GridWorld** — toroidal grid, walls/resources, energy, 18-bit sensors, 4-neuron brain | **Real, but a GA/evolution fixture**, not a learning loop. Serves `EvolutionLoop`, `FitnessFn`, `ParallelEvolution`, `CauldronProtocol` | Seeded (`Random` injected into `World`/`SimulationRunner`) | Explicit: `World.tick()` :46, `AgentBody.move()` :24, `SimulationRunner.run()` :34 | **NO** — the reward/energy model is fully readable | `SimulationResult(steps, foodCollected, wallCollisions, energyBefore, energyAfter)` | `Random` seed passed by the caller; no episode id |
| **E** | `core/io/matrix/pilot/PilotGridWorld.java` | 20-bit genome agent + GA (elitism/crossover/mutation) for a small grid | **Real, but GA-only** — no learning loop, no prediction | `randomGenome(long seed)` :32, `evolve(..., seed)` :88 — fully seeded | No environment transition: `decide(agent, sensors)` :62 + `fitness(rewards)` :81 | n/a | Fitness = sum of rewards | `long seed` argument |
| **F** | `core/io/matrix/federation/liquid/GridWorldBridge.java` | Grid world with reward/done/goal/hazard/energy, `step(Action) → StepResult(reward, done, observation, metrics)` | **Real, self-contained** — has a genuine reward signal and a done flag | `Random(seed)` injected :93 | **Yes** — `step()` :101 mutates the grid state, energy, done | **NO** — reward rules are readable | `StepResult.done`; goal = +10.0 reward | `long seed` ctor arg |
| **G** | `core/io/matrix/minecraft/` (`BlockWorld`, `BlockAgent`, `NeuralBrain`, `CraftingSystem`, `BotCoordinator`, `HeadlessBotRegistry`, `SurvivalRunner`, `HeadlessBotSnapshot`, `BlockType`) | **Minecraft-like sandbox**: 2D blocks, ores, caves, inventory, tools, health/hunger | **Real** — headless, in-process, no server needed | `Random` seeds in `HeadlessBotRegistry`/`SurvivalRunner` | **Yes** — agent moves/mines/crafts/eats; `SurvivalRunner.run()` :40 | **NO** — world rules fully readable | `SurvivalResult(score, milestones)` :112 | `long seed` in the registry |
| **H** | `core/io/matrix/pilot/minecraft/` (`MinecraftPilot`, `NaiveMinecraftPilot`, `LearnedMinecraftPilot`, `EpisodeHistory`, `AgentState`, `Observation`, `ActionResult`, `WorldConfig`) | Pilot contract for a Minecraft world; BIR-trained pilot over `EpisodeRecord` | **STUB / island.** `WorldConfig` has a seed but no world is wired to it; the pilots only consume `AgentState`/`Observation` records they are handed | `NaiveMinecraftPilot.initialize` seeds from `config.seed()` :27 | No — the environment is supplied by the caller | n/a | `EpisodeHistory` (:1) has `totalSteps/blocksPlaced/itemsCollected/distanceTraveled/deaths` | `WorldConfig.seed` |
| **I** | `core/io/matrix/embodied/ActiveInferenceEngine.java` | Free-energy action selection over a belief vector | **STUB.** No environment: it takes a list of candidate `Action`s and an `Observation` and picks the lowest expected free energy | `Random(seed)` :20 | **NO** | n/a | None — no success measure at all | `long seed` |
| **J** | `core/io/matrix/embodied/SensorimotorCycle.java` | Perceive→plan→act→feel cycle with a `Feeling(reward, surprise, energy)` | **STUB.** The "act" step is `rng.nextDouble() < 0.8` — a fake actuator :73-76; perception is gaussian noise | `Random(seed)` :33 | Fake | n/a | `CycleResult.success` is the coin flip, not an environment outcome | `long seed` |
| **K** | `core/io/matrix/neuron/SensorimotorLoop.java` | Piaget motor-babble loop; declares `interface Environment { long transition(long sensors, int action); }` :238-243 — **the closest thing to an MDP interface in the repo** | **Real algorithm, but only exercised by tests** | Caller-supplied `Random` | **Yes** — the `Environment.transition` function *is* a transition model | n/a | `EpisodeStats.predictionAccuracy` :77 | caller-supplied `Random` |
| **L** | `core/io/matrix/neuron/QLearning.java` | Tabular Q-learning; `train(nStates, nActions, nEpisodes, …, StepFn)` :70 — the env is a lambda `EnvStep(int nextState, double reward)` :91-96 | **Real algorithm, but the env is supplied by the caller** | `Random(seed)` :76 | **Yes** — `StepFn` is a transition model + reward | n/a | Returned Q-table; no episode metric | `long seed` |
| **M** | `core/io/matrix/pilot/scada/ScadaSimulator.java` | Industrial process simulator: drift, noise, fault injection, `tick() → List<SensorReading>` | **Real simulator, but a fixture for the safety monitor**, not an RL env | `Random(seed)` :43 | Yes (`tick()`) | n/a | `ProcessState` NORMAL→WARNING→CRITICAL→EMERGENCY | `long seed` |
| **N** | `core/io/matrix/life/SurvivalScenario.java` | "1000 Minecraft days" survival narrative (`GATHER/CRAFT/BUILD/…`) | **STUB.** Pure bookkeeping, no agent in a world | `Random(seed)` :31 | Rule-based narrative | n/a | `SurvivalState.health` / `.hunger` | `long seed` |
| **O** | `core/io/matrix/mcts/MctsTree.java` (:30) + `core/io/matrix/federation/liquid/MCTSPlanner.java` (:25) | Tree search with **random rollouts** as the simulator | **Real search, but the "simulation" is a random mutation** (`MctsTree.simulate` :198) | Seeded `Random` inside the tree | Ad-hoc | n/a | Tree value | seed inside the tree |
| **P** | `core/io/matrix/pilot/PyBulletBridge.java` (:20), `core/io/matrix/neuron/NcaBrainSimulator.java` (:33), `core/io/matrix/neuron/GillespieSimulator.java`, `GrayScottSimulator.java` | External/physical simulators (PyBullet IPC, cellular automata, stochastic kinetics) | **Out of scope for cognition**; `PyBulletBridge` needs an external Python process | n/a | Yes | n/a | n/a | n/a |
| **Q** | `core/io/matrix/neuron/CurriculumEngine.java` (:18) | ZPD task selector — `selectNext(scenarios, competence, seed)` :25 | **NOT an environment** — a pure task chooser over a competence map | Seeded | n/a | n/a | competence band 0.6–0.85 | `long seed` |

**Repo-wide negatives (searched, found nothing):**

- `(?i)\b(MDP|transitionModel|transition_model|markov)\b` over `matrix-core/src/main` → **0 matches.**
- No `GridWorld` class exists — the name is used by `PilotGridWorld`, `GridWorldBridge`,
  `GridWorldPilotCommand` and the `io.matrix.simulation` package, none of which is an MDP.
- `Reward` appears only in MCTS (`MctsNode`, `MctsTree`, `LatsNode`), RLHF/GRPO
  (`CognitiveRLHF`, `CognitiveGRPO`) and the Tsetlin trainer — **never as an environment contract**.
- No `Environment` interface other than `MicroWorldEnvironment` (`core/io/matrix/cognitive/microworld/MicroWorldEnvironment.java` :18)
  and `SensorimotorLoop.Environment` (`core/io/matrix/neuron/SensorimotorLoop.java` :238).
- `matrix-micro` is not in the build (`settings.gradle` :20 — commented out) and has no Java sources.

---

## 2. The microworld package, in full

`matrix-core/src/main/java/io/matrix/cognitive/microworld/` — 12 classes, 2021 lines of main
source; 4 test classes, 996 lines. Nothing outside this package references it except
`io.matrix.cognitive` (kernel + adapter classes) — the package is a genuine leaf module.

### 2.1 Class map

```
MicroWorldEnvironment  (interface)   ── the world as seen from outside; the rule is NOT in it
  └── LampEnvironment  (final class) ── power∧switch → lamp; rule is a private method

WorldInput   (record)   ── the assignment: Map<String,Boolean>; canonical key "power=0,switch=1"
Observation (record)   ── id, input, lamp, adversarial, observedAt, disposition

HypothesisSpace (final) ── 16 Boolean functions of 2 vars, sorted; information gain; counts
  └── RuleHypothesis (record) ── id, statement, truthTable, supportingCases, contradictingCases

MicroWorldSession (final) ── THE LEARNER: observations, surviving/rejected hypotheses,
│                           predictions, prediction errors, experiment proposals
  ├── interface Factory ── how a session is rebuilt from durable observations
  │     └── DefaultFactory
  ├── HypothesisPrediction (record) ── value + unanimous flag + disagreeing ids + supporting cases
  ├── Experiment (record) ── id, input, expectedInformationGain, rationale  (the only "action")
  ├── PredictionError (record) ── id, input, predicted, observed, responsibleHypothesisId, note
  ├── RejectedHypothesis (record) ── hypothesis + reason (never deleted)
  └── ObservationDisposition (enum) ── ELIGIBLE / UNRESOLVED / ACCEPTED / REJECTED + transition table
```

### 2.2 Exact public API

**`MicroWorldEnvironment`** (interface, :18)
```java
Observation probe(WorldInput input);   // :29
Set<String>  variables();              // :32
String        outcomeVariable();        // :35
```

**`LampEnvironment`** (final class implements `MicroWorldEnvironment`, :44)
```java
public static final String POWER_VARIABLE        = "power";                          // :47
public static final String SWITCH_VARIABLE       = "switch";                         // :50
public static final String LAMP_VARIABLE         = "lamp";                           // :53
public static final String DEFAULT_OBSERVED_AT   = "1970-01-01T00:00:00Z";            // :56
public static final String OBSERVATION_ID_PREFIX = "obs-";                           // :59
public static final String ADVERSARIAL_ID_PREFIX = "obs-adv-";                       // :62

public LampEnvironment();                                   // :68  fixed instant
public LampEnvironment(Supplier<String> observedAt);        // :78  caller-supplied instant
public Observation probe(WorldInput input);                 // :83  override
public Observation adversarialProbe(WorldInput input);      // :102 fault injection (NOT a 2nd rule)
public Set<String> variables();                             // :110 override
public String outcomeVariable();                           // :115 override

private boolean applyHiddenRule(WorldInput input);         // :126  ← the rule; power && switch
private void    requireKnownAssignment(WorldInput input);  // :136
private String  observedAt();                              // :143
```

**`WorldInput`** (record, :33)
```java
public record WorldInput(Map<String, Boolean> assignment)
public static final Set<String> KNOWN_VARIABLES = Set.of("power", "switch");   // :36
public static WorldInput of(boolean power, boolean toggle);                     // :76
public boolean get(String name);        // :89  throws IllegalArgumentException if absent
public boolean has(String name);        // :99
public Set<String> variables();         // :104 sorted
public String  key();                   // :116 "power=0,switch=1" (sorted, 1/0 digits)
```

**`Observation`** (record, :30-31)
```java
public record Observation(String id, WorldInput input, boolean lamp, boolean adversarial,
                          String observedAt, ObservationDisposition disposition)
public Observation(String id, WorldInput input, boolean lamp, boolean adversarial,
                   String observedAt);              // :61  disposition = ELIGIBLE
public Observation withDisposition(ObservationDisposition newDisposition);  // :67
public boolean constrainsModel();                    // :72
public String  assignmentKey();                      // :77
```

**`ObservationDisposition`** (enum, :83) — the **only coded state machine** in the environment layer
```java
ELIGIBLE, UNRESOLVED, ACCEPTED, REJECTED;                                  // :86-98
public boolean constrainsModel();                                        // :109  ELIGIBLE||ACCEPTED
public boolean isHeld();                                                  // :130  UNRESOLVED||REJECTED
public boolean awaitingAdjudication();                                    // :142  UNRESOLVED only
public boolean terminallyExcluded();                                      // :154  REJECTED only
public boolean canTransitionTo(ObservationDisposition next);              // :180  the transition table
public static boolean canBeInitialDisposition(ObservationDisposition d); // :211  ELIGIBLE||UNRESOLVED
public static ObservationDisposition conservativeDefault();             // :223  → UNRESOLVED
```

**`RuleHypothesis`** (record, :33)
```java
public record RuleHypothesis(String id, String statement, Map<String, Boolean> truthTable,
                              int supportingCases, int contradictingCases)
public String           keyFor(WorldInput input);          // :76
public Set<String>      variables();                       // :91  derived from the table
public Optional<Boolean> valueAt(WorldInput input);         // :111 empty = "no opinion"
public boolean          evaluate(WorldInput input);         // :129 throws if no table entry
public boolean          agreesWith(Observation observation);// :140
public RuleHypothesis   withCounts(int supporting, int contradicting); // :148
```

**`HypothesisSpace`** (final class, :38)
```java
public static final int VARIABLE_COUNT = 2;                                        // :41
public HypothesisSpace(List<String> variables, List<RuleHypothesis> candidates);  // :65
public static HypothesisSpace unbiased();                       // :80  all 16 Boolean fns of 2 vars
public List<String>          variables();                      // :85
public List<RuleHypothesis>  candidates();                     // :90  id order
public Optional<RuleHypothesis> forAssignment(WorldInput);    // :106
public List<RuleHypothesis>  consistentWith(List<Observation>);// :126
public static List<RuleHypothesis> allBooleanFunctionsOfTwoVariables(); // :157
public List<WorldInput>      allAssignments();                // :180
public static double informationGain(WorldInput, List<RuleHypothesis>);        // :201  ∈ [0, 0.5]
public static Optional<Boolean> unanimousValueAt(WorldInput, List<RuleHypothesis>); // :235
public static int  supportingCount(RuleHypothesis, List<Observation>);   // :256
public static int  contradictingCount(RuleHypothesis, List<Observation>); // :267
public static Optional<Observation> firstContradiction(RuleHypothesis, List<Observation>); // :279
```

**`MicroWorldSession`** (final class, :47) — the learner
```java
public MicroWorldSession(String episodeId, MicroWorldEnvironment environment);                 // :87
public MicroWorldSession(String episodeId, MicroWorldEnvironment environment,
                         HypothesisSpace space);                                                // :98

public interface Factory {                                                                     // :122
    MicroWorldSession create(String episodeId, List<Observation> observations);
}
public static final class DefaultFactory implements Factory {                                   // :138
    public DefaultFactory(MicroWorldEnvironment environment);
}

public void observe(Observation observation);      // :176  records; quarantines a contradiction
public void applyOutcome(Observation observation);  // :234  observe + marks the proposal answered
public List<Observation> observations();            // :245
public List<Observation> appliedObservations();     // :255  what the model was built from
public List<Observation> quarantinedObservations();// :269  held, durable, not model data
public boolean isApplied(Observation);             // :283
public ObservationDisposition dispositionOf(Observation); // :314
public List<RuleHypothesis> survivingHypotheses();// :328
public List<RejectedHypothesis> rejectedHypotheses();   // :333
public List<RuleHypothesis> hypothesesNeedingReview();   // :343
public HypothesisPrediction predict(WorldInput input);    // :363  records the prediction
public Experiment proposeExperiment();                    // :436  max-information-gain proposal
public List<Experiment> proposedExperiments();            // :467
public List<Experiment> answeredExperiments();            // :472
public List<PredictionError> predictionErrors();          // :477
public List<HypothesisPrediction> predictions();           // :482
public Set<String> observedAssignments();                 // :487
public boolean hasObserved(WorldInput);                   // :507
public Set<String> predictedButUnobservedAssignments();   // :520
public String statusLine();                              // :536
public String episodeId();                               // :543
public long   openErrorCount();                          // :548
```

**Value objects**
```java
HypothesisPrediction(String subject, boolean value, boolean unanimous,
                     List<String> disagreeingHypothesisIds, List<String> supportingCaseIds)  // :28
    + public boolean decided()                                                                 // :42
Experiment(String id, WorldInput input, double expectedInformationGain, String rationale)   // :41
    + public String assignmentKey()                                                            // :61
PredictionError(String id, WorldInput input, boolean predicted, boolean observed,
                String responsibleHypothesisId, String note)                                    // :25
    + public String assignmentKey()                                                            // :44
RejectedHypothesis(RuleHypothesis hypothesis, String reason)                                    // :19
    + public String hypothesisId()                                                             // :31
```

### 2.3 Is it deterministic? — **Yes, completely.**

| Source of non-determinism | Present? | Evidence |
|---|---|---|
| `java.util.Random` / `ThreadLocalRandom` / `UUID` | **No** | no import of `java.util.Random` anywhere in the package |
| Wall clock inside a decision path | **No** | `LampEnvironment` takes the instant from a `Supplier<String>` (default = the fixed constant `1970-01-01T00:00:00Z`, :56); `Observation` javadoc states the record "never reads one itself" (:12-15) |
| Iteration-order dependence | **No** | `HypothesisSpace` sorts variables (`TreeSet`, :72) and candidates by id (:74); `WorldInput.key()` uses sorted names (:104,:116); `MicroWorldSession.proposeExperiment` iterates assignments in canonical key order and keeps the earlier on a tie (strict `>` at :451) |
| Tie-breaking in prediction | **Deterministic but arbitrary** | `predict` takes the majority; on an exact tie it takes the first survivor in id order (`valueOfFirstSurvivor` :601). Documented as "a deterministic rule, chosen for reproducibility and for nothing else" (`HypothesisPrediction` :19-22) |
| Observation identity | **Deterministic** | monotonic counter: `obs-1`, `obs-2`, … / `obs-adv-N` (`LampEnvironment` :86,:105) — asserted by `LampEnvironmentTest` :38-48 |

**Caveat:** `LampEnvironment(Supplier<String>)` admits a non-deterministic instant supplier; the
*rule* stays deterministic, but the `observedAt` string on the observation then varies.

### 2.4 Is there a state-transition model? — **No, not in the MDP sense.** What there *is*:

1. **The world is a static response function.** `probe(assignment) → observation`. There is no
   `s'`, no action that changes the world, no time. The assignment *is* the state; the whole
   input domain is 4 points.
2. **The learner has a real state transition**, and it is the interesting one:
   ```
   observation ──► MicroWorldSession.observe()  :176
                    ├── disposition.isHeld()          → quarantined, model unchanged   :193-196
                    ├── contradicts a UNANIMOUS prediction → PredictionError recorded,
                    │                                    hypotheses flagged, quarantined :212-220
                    └── otherwise → applied, then reclassify()                        :221-222
                                 └── for each candidate: contradicting == 0 → survives;
                                     else → RejectedHypothesis(hypothesis, reason)     :568-585
   ```
   The transition is **monotone and total**: nothing is ever deleted, only reclassified.
3. **The disposition has an explicit transition table** (`ObservationDisposition.canTransitionTo`
   :180-198), enforced by the store. `REJECTED → ELIGIBLE` is refused; only a review can move a
   case.
4. **There is no reward, no discount, no value function, no policy.** `Experiment.expectedInformationGain`
   is *not* a reward — it is the expected **fraction of surviving hypotheses eliminated** by one
   probe, capped at **0.5** for a two-valued outcome (`Experiment` :10-39,
   `HypothesisSpace.informationGain` :201-225).

### 2.5 Are the hidden rules exposed to the learner? — **Structurally no, and that is tested.**

- The rule lives in `private boolean applyHiddenRule(WorldInput)` (`LampEnvironment` :126).
  The class is `final`, so it cannot be subclassed; the method is `private`, so it is not
  reachable from the same package, from a test, or through the interface.
- The public surface of `LampEnvironment` returns only `Observation`, `Set` and `String`
  (asserted reflectively at `LampEnvironmentTest` :120-129) and the rule method is asserted
  `private` at `LampEnvironmentTest` :131-146.
- The interface `MicroWorldEnvironment` exposes **vocabulary only** (`variables()`,
  `outcomeVariable()`) — the shape of the question, not its answer.
- **The rule is still reachable by inference — by design.** `HypothesisSpace.unbiased()` hands
  the learner **all sixteen** Boolean functions of two variables, and the true rule (the
  conjunction `h-0001`) is one of them. Narrowing to it is the learner's work, and the
  elimination history is kept (`rejectedHypotheses()`).
- **One intentional leak, after learning:** when the model settles, the promoted `Procedure`'s
  statement names the rule (`CognitiveKernel.promoteIfValidated` :861-869, `"the settled-model
  lookup for: " + session.survivingHypotheses().get(0).statement()`). The *derived* rule
  becomes readable in the store — that is the point of an episode, and it happens only after
  the learner has eliminated 15 alternatives.

### 2.6 Measurable success condition

**The primary, already-measured condition** (`docs-v2/engineering/W44-BENCHMARK.md` §4.1-4.3):

| step | observation | lamp | survivors | rejected |
|---|---|---|---|---|
| 0 | — | — | **16** | 0 |
| 1 | power=0,switch=1 | false | **8** | 8 |
| 2 | power=1,switch=1 | true | **4** | 12 |
| 3 | power=1,switch=0 | false | **2** | 14 |
| 4 | power=0,switch=0 | false | **1** | 15 |

**Success = exactly one surviving hypothesis, and it is `h-0001`** (the conjunction:
"lamp is true exactly when power=1,switch=1"). Secondary conditions that are also measurable
today:

- `HypothesisPrediction.unanimous() == true` — the model settles on the queried assignment.
- `Experiment.expectedInformationGain() > 0.0` while unsettled; `== 0.0` once settled.
- `session.openErrorCount()` — number of prediction errors whose responsible hypotheses still stand.
- `session.hypothesesNeedingReview()` — non-empty exactly after an adversarial/contradicting observation.
- `session.quarantinedObservations()` — observations recorded but excluded from the model.
- `session.statusLine()` — "episode X: N observations, M surviving hypotheses, E prediction errors, O open".

**What is NOT measurable today:** there is **no numeric reward**, **no episode-level termination
condition** (no max-steps, no goal, no "episode finished" flag), and **no comparison metric**
between two learners. An "episode" in the microworld is an *open-ended* stream of probes whose
only natural end is that the 4-point input domain is exhausted.

### 2.7 What the microworld does NOT have (the gap W51 §7 would fill)

| Missing | Consequence |
|---|---|
| Reward / value / utility | the learner cannot optimise anything; it can only *narrow* a hypothesis space |
| MDP tuple (S, A, P, R, γ) | no `P(s'|s,a)`, no discounting, no policy; `proposeExperiment` is a *diagnostic* policy (information gain), not a control policy |
| Episode boundary / termination | no "episode finished" signal, no step budget, no trajectory record |
| Seeded exploration | `ExploratoryActionSampler` (the repo's deterministic-seed helper) is **not** used here; the proposal is deterministic and *greedy* — so a second learner cannot explore differently |
| Multi-learner comparison | no notion of a learner's fitness; nothing to compare against a baseline |
| Continuous / richer observation space | the outcome is Boolean; `Experiment`'s javadoc states the gain metric "would understate the achievable gain" for a continuous outcome (W44-BENCHMARK §7.5) |

---

## 3. The kernel-side adapters (`io.matrix.cognitive`)

**`MicroWorldCodec`** (final, static utility, :30) — the boundary between the microworld and the
durable epistemic store.
```java
public static final String CASE_PREFIX = "case-";                    // :33
public static WorldInput parseAssignment(String text);               // :55  "power=1,switch=0" or {"power":1}
public static String  describe(WorldInput input);                    // :134 canonical description
public static String  describeOutcome(boolean lamp);                 // :147 "the lamp was on"/"the lamp was off"
public static String  caseIdFor(WorldInput input, boolean lamp);     // :157 case-<digest(conditions|outcome)>
public static Case    toCase(String workspaceId, Observation obs);   // :162
public static List<Observation> readCases(KnowledgeStore store, String workspaceId); // :188
public static Set<String> facts(WorldInput input, boolean alreadyObserved);         // :220
```
The `caseIdFor` digest is what makes a re-observation **idempotent** rather than duplicative.

**`MicroWorldComputation`** (record, :26) — the stateless payload the algorithm portfolio sees.
```java
public record MicroWorldComputation(WorldInput input, MicroWorldSession session,
                                    Set<String> facts, Procedure procedure)
public MicroWorldComputation(WorldInput, MicroWorldSession, Set<String>)  // :43  no promoted fast path
public boolean fact(String name);                                          // :48
```

**`MicroWorldReferenceMethod`** (final, :36) — the expensive, always-correct path.
```java
public static final String ID = "micro-world-hypothesis-enumeration";  // :39
public static final String VERSION = "v1";                             // :42
public static final int HYPOTHESIS_SPACE_SIZE = 16;                    // :45
public AlgorithmCapability capability();                              // :76
public boolean preconditionsHold(MicroWorldComputation input);         // :81
public Boolean execute(MicroWorldComputation computation, ComputeBudget budget); // :86
```
It re-derives the survivors from `session.appliedObservations()` (:96) — **model-eligible data
only** — and **refuses** (`IllegalStateException`) when the survivors disagree (:109-111).

**`MicroWorldSettledModelMethod`** (final, :36) — the fast path.
```java
public static final String ID = "micro-world-settled-model";           // :39
public static final String PRECONDITION_MODEL_SETTLED = "MODEL_SETTLED";// :45
public static final String FALLBACK_ID = MicroWorldReferenceMethod.ID;  // :48
public boolean preconditionsHold(MicroWorldComputation computation);   // :86
public Boolean execute(MicroWorldComputation computation, ComputeBudget budget); // :110
```
It answers only when exactly one hypothesis survives (:116-119) and the procedure, if any, is
active and applicable (:97-105).

---

## 4. The existing learning-loop call graph (production, with file:line)

### 4.1 Path A — HTTP → kernel → microworld (the real production loop)

```
POST /v1/cognitive/observe
 └─ CognitiveResource.observe(ObserveRequest)                        CognitiveResource.java:74
     └─ service.observe(assignment, idempotencyKey)                 CognitiveResource.java:80
         └─ CognitiveService.observe(String, String)                 CognitiveService.java:301
             ├─ [idempotency path] IdempotencyRegistry.claim(...)   CognitiveService.java:308
             └─ CognitiveService.runCycle(payload, listener)         CognitiveService.java:419
                 ├─ allocator.nextSignalId()      → "sig-<UUID>"    CognitiveService.java:421
                 ├─ allocator.nextSignalSequence()→ durable seq      CognitiveService.java:422
                 └─ CognitiveService.runCycle(payload, listener, signalId, sequence)
                                                                       CognitiveService.java:439
                     ├─ CognitiveSignal.structured(signalId, payload, …) CognitiveService.java:441
                     └─ CognitiveKernel.process(signal, listener)      CognitiveKernel.java:303  (synchronized)
                         ├─ PERCEIVE  ChannelClassifier.classify(signal)          :326
                         ├─ RESTORE   MicroWorldCodec.readCases(workbench.storeForValidation(),
                         │                        WORKSPACE_ID)                       :343
                         │           sessionFactory.create(episodeId, remembered)    :345
                         │             └─ MicroWorldSession.DefaultFactory.create  MicroWorldSession.java:150
                         ├─ READ      MicroWorldCodec.parseAssignment(signal.rawText()) :358
                         ├─ PREDICT   session.predict(requested)                    :369
                         │             └─ MicroWorldSession.predict                MicroWorldSession.java:363
                         │           store.recordPrediction(prediction)             :378
                         ├─ SELECT    MicroWorldCodec.facts(requested, alreadyObserved) :399
                         │           selector.select(new ProblemRequest(FAMILY_MICRO_WORLD, …),
                         │                         bias.costWeightMultiplier())       :410
                         ├─ COMPUTE   selection.method().execute(computation, budget)  :429
                         │             ├─ MicroWorldReferenceMethod.execute      MicroWorldReferenceMethod.java:86
                         │             └─ MicroWorldSettledModelMethod.execute   MicroWorldSettledModelMethod.java:110
                         ├─ PROPOSE   session.proposeExperiment()                   :440
                         │             └─ HypothesisSpace.informationGain(...)     MicroWorldSession.java:450
                         ├─ PROBE     environment.probe(probed)                      :497
                         │             └─ LampEnvironment.probe                    LampEnvironment.java:83
                         │                 └─ private applyHiddenRule               LampEnvironment.java:126
                         ├─ OBSERVE   session.applyOutcome(observed)                 :530
                         │             session.isApplied / dispositionOf            :531-532
                         │           workbench.recordObservation(…, disposition, …)  :552
                         │           store.recordError(error)                       :590
                         │           store.recordTask(task)                         :596
                         ├─ LEARN     promoteIfValidated(session, remembered)        :607
                         │             └─ store.recordProcedure(…)                  :870
                         ├─ REGULATE  regulator.evolve(state, events)               :642
                         │           store.recordRegulatoryState(…)                :657
                         └─ ANSWER    answerFor(forecast, computed, observed, error) :666
                                     store.closeEpisode(episodeId, now)              :668
                                     persist(trace, episodeId, now) → store.appendEvent(...)  :670 / :761
```

**Wiring** — `CognitiveService.build()` (`CognitiveService.java:241`):
```java
MicroWorldEnvironment environment = new LampEnvironment();                       // :242
MicroWorldSession.Factory sessionFactory = new MicroWorldSession.DefaultFactory(environment); // :243
AlgorithmRegistry registry = Portfolio.defaultRegistry()
        .register(new MicroWorldReferenceMethod())                               // :249
        .register(new MicroWorldSettledModelMethod());                           // :250
new CognitiveKernel(workbench, store(), sessionFactory, environment,
        new KernelSelector(registry, ledger), new KernelRegulator(), …)         // :252-260
```
**Storage** — `CognitiveService.store()` → `new CognitiveStore(Paths.get(workbench.databasePath()))`
(`CognitiveService.java:141`) → `DriverManager.getConnection("jdbc:sqlite:" + databaseFile)`
(`CognitiveStore.java:146`). **A SQLite file is mandatory for Path A.**

### 4.2 Path B — error-driven decay (knowledge core; a *different* contour)

```
KnowledgeWorkbench.applyErrorDrivenDecay(workspaceId)          KnowledgeWorkbench.java:1508
 └─ store().learningErrors(workspaceId) → verified only        :1509-1512
 └─ HebbianBindings.streamOf(errorsOfConcept)                   :1537-1538
 └─ ErrorDrivenLearner.totalActiveBindings(state)              KnowledgeWorkbench.java:1546
 └─ ErrorDrivenLearner.applyDecay(state, stream, eta, threshold) KnowledgeWorkbench.java:1547
      └─ io.matrix.cognitive.ErrorDrivenLearner.applyDecay(...) ErrorDrivenLearner.java:82
 └─ store().saveHebbianState(workspaceId, conceptId, state)     KnowledgeWorkbench.java:1549
```
`ErrorDrivenLearner` (DESIGN-64 §4) consumes a `CognitiveErrorStream` and applies **targeted
anti-Hebbian decay** with a floor at `-threshold` (`ErrorDrivenLearner.java:113-120`).
It is **not** connected to the microworld: the microworld's `ErrorSignal`s live in the
`cognitive_error` table, while the decay consumes the knowledge core's `LearningError`s.

### 4.3 Path C — continuous learning over an agent loop (a *third* contour)

```
MatrixLifecycleManager(…, ContinuousLearningLoop learningLoop, …)   MatrixLifecycleManager.java:108
 └─ learningLoop.run(tickCount)                                     MatrixLifecycleManager.java:256
 └─ computeAvgFitness(learningLoop)                                MatrixLifecycleManager.java:258, :433
 └─ ContinuousLearningLoop.tick()                                  ContinuousLearningLoop.java:75
     ├─ agentLoop.tick()                                            ContinuousLearningLoop.java:76
     ├─ brain.recordFeedback(sensorBits, success)                   ContinuousLearningLoop.java:81
     └─ brain.onlineTrain(onlineTrainIterations)                    ContinuousLearningLoop.java:93
```
`AgentLoop` takes the environment as two functional interfaces —
`Sensor { long read(); }` and `Effector { AgentAction.ActionResult execute(AgentAction); }`
(`io/matrix/agent/AgentLoop.java:61-70`). **This is the only place in the repo where an
environment is a first-class injection point for a learning loop** — and it is the agent
contour, not the microworld.

### 4.4 Present in the repo but **not wired into any production path** (tests only)

| Class | Where | Production callers |
|---|---|---|
| `ExploratoryActionSampler` (`core/io/matrix/cognitive/ExploratoryActionSampler.java:22`) — `sample` :35, `sampleK` :48, `softmax` :67, seed = `mix(stateHash, cycleCount)` :95 | deterministic seeded exploration | **none** — grep over the whole repo finds only `ExploratoryActionSamplerTest` |
| `SensorimotorLoop` (`core/io/matrix/neuron/SensorimotorLoop.java`) — `runEpisode(Environment, long, int)` :124, `interface Environment` :238 | Piaget motor babble, `EpisodeStats.predictionAccuracy` | **none** — tests only |
| `CurriculumEngine` (`core/io/matrix/neuron/CurriculumEngine.java:18`) — `selectNext(scenarios, competence, seed)` :25 | ZPD task selection | **none** found in main |

**This is the most reusable unused asset for W51 §7:** `ExploratoryActionSampler` is exactly the
"deterministic seed → same action sequence" primitive the microworld lacks, and it is already
CONSTITUTION-compliant and tested.

---

## 5. Success condition, seed and reproducible episode id — direct answers

**Q. Does the microworld have a measurable success condition?**
**Yes, and it is already measured.** `survivingHypotheses().size() == 1` with the survivor
being `h-0001`; 16 → 8 → 4 → 2 → 1 across the four probes
(`docs-v2/engineering/W44-BENCHMARK.md` §4.1). Secondary: `unanimous() == true`,
`expectedInformationGain() > 0` while unsettled, `openErrorCount()`, `hypothesesNeedingReview()`.
**No numeric reward and no episode termination condition exist.**

**Q. Does it have a deterministic seed / reproducible episode id?**

| Layer | Seed / id | Reproducible? |
|---|---|---|
| `LampEnvironment` | **none** — nothing random | **Yes**, fully. Identifiers are a monotonic counter (`obs-N`). A fresh `LampEnvironment` restarts at `obs-1`, so *the same probe sequence always yields the same ids* |
| `MicroWorldSession` | **none** — the episode id is a **caller-supplied string** (`MicroWorldSession` :87/:98) | **Yes**, if the caller fixes the episode id. Experiment ids are `exp-<episodeId>-N` (:460), error ids `err-<episodeId>-N` (:596) |
| `EpisodeIdAllocator` (`core/io/matrix/cognitive/EpisodeIdAllocator.java:36`) | `nextSignalId()` :69 = `"sig-" + idSource.nextId()`; `nextSignalSequence()` :81 = **durable DB sequence** `cognitive.signal`; `episodeIdFor(signalId)` :95 = `"ep-" + signalId` | **The sequence is durable; the id is not reproducible in production** — `IdSource.random()` = `UUID.randomUUID()` (`IdSource.java:40-42`) |
| `IdSource` (`core/io/matrix/cognitive/IdSource.java:23`) | a **name**, not a seed — "it carries no meaning, decides nothing, and is never compared for ordering" (:17-20) | **A deterministic `IdSource` can be injected** — that is precisely why the seam exists. `CognitiveService(IdSource, Supplier<String>)` :102 accepts one |
| The kernel's episode id | `CognitiveKernel.process` :305 computes `"ep-" + signal.id()` **inline** rather than via `EpisodeIdAllocator.episodeIdFor(signalId)` :95 — the two derivations are stated to agree, but only one of them is used | same as above |

**So: the microworld itself is seedless and perfectly reproducible; the *episode identity* in the
production path is random unless a deterministic `IdSource` is injected.** The id never influences
any decision — it is a name.

---

## 6. Running ONE repeatable episode end-to-end today

### 6.1 Option A — in-memory only (no DB, no file). **This is the recommended recipe.**

```java
import io.matrix.cognitive.microworld.*;

// 1. the world (deterministic: fixed instant, no clock, no randomness)
LampEnvironment environment = new LampEnvironment();

// 2. the learner over the unbiased 16-function hypothesis space
MicroWorldSession session = new MicroWorldSession("ep-w51-001", environment);

// 3. one loop iteration: propose → predict → probe → apply
Experiment experiment = session.proposeExperiment();          // MicroWorldSession.java:436
HypothesisPrediction forecast = session.predict(experiment.input());  // :363
Observation observed = environment.probe(experiment.input());  // LampEnvironment.java:83
session.applyOutcome(observed);                               // MicroWorldSession.java:234

// 4. the success condition
boolean settled = session.survivingHypotheses().size() == 1;  // 16 → 8 → 4 → 2 → 1
String rule = session.survivingHypotheses().get(0).id();       // "h-0001"
```

- **Args:** `new MicroWorldSession(String episodeId, MicroWorldEnvironment environment)` —
  two arguments, both under the caller's control.
- **Storage:** **none.** No DB, no file, no container, no HTTP.
- **Repeatability:** bit-identical across runs for the same episode id — the world has no
  randomness, the proposal is greedy and tie-broken by canonical key order, and every id is a
  counter. The `MicroWorldSessionTest` (368 lines) is the working reference.

### 6.2 Option B — through the kernel (needs a SQLite file)

```java
KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dbPath);           // KnowledgeWorkbench.java:135
CognitiveService service = new CognitiveService(deterministicIdSource, fixedClock); // :102
CognitiveOutcome outcome = service.observe("power=0,switch=1");          // :284
```
- **Storage:** **a SQLite file** — `CognitiveService.store()` :139-143 opens
  `jdbc:sqlite:<workbench.databasePath()>`; the `cognitive_*` and knowledge tables are created on
  open (`CognitiveStore` schema, :215-348).
- **Repeatability:** the *learning* is deterministic, but the **episode id is not**
  unless a deterministic `IdSource` is injected; the **timestamps are not** unless a fixed clock
  supplier is injected. With both injected, the cycle is reproducible.
- **Cost:** 4 cycles to settle the model (one per unobserved assignment); each cycle restores
  the session from the recorded cases (`CognitiveKernel` :343-345).

### 6.3 What is *not* needed today

No DB, no file and no external process are needed for the learning itself — only for
**durability across a restart**. Option A is a complete episode.

---

## 7. Recommendation

### **Reuse `io.matrix.cognitive.microworld` (the `LampEnvironment` + `MicroWorldSession` pair) — do NOT build a new environment.**

**Why:**

1. **The hard part is already built and correct.** The hidden rule is enforced by the language
   (`private` method in a `final` class) and guarded by a reflective test
   (`LampEnvironmentTest` :120-146). A new environment would have to re-earn that property.
2. **The learner's state model is already a real epistemic machine**: hypotheses survive or are
   rejected *with the observation that rejected them* (`RejectedHypothesis`), contradictions are
   **held, not silently absorbed** (`ObservationDisposition` + its transition table), and the
   disposition is **durable across a restart** (`MicroWorldCodec.readCases` :188 +
   `KnowledgeStore.caseDispositions` :3703). W51 §7 would otherwise rebuild this from scratch.
3. **The success condition is already measured** (16 → 1 survivors, survivor = `h-0001`) and
   published (`W44-BENCHMARK.md` §4). A new environment starts with no baseline.
4. **It is already served end-to-end** over `POST /v1/cognitive/observe`, with a durable trace,
   a durable episode and a durable epistemic record. The learning loop exists.
5. **Everything else in the repo is either a GA fixture or a stub.** `io.matrix.simulation` and
   `PilotGridWorld` have no prediction/learning loop; `io.matrix.embodied` (`ActiveInferenceEngine`,
   `SensorimotorCycle`) fabricates its own observations and has no success measure;
   `io.matrix.pilot.minecraft` is an island with no world behind it. Picking any of them would
   mean re-implementing the microworld's half of the work.
6. **The gap is narrow and additive.** What is missing is not an environment; it is
   *(a)* a reward/utility notion, *(b)* an episode boundary, *(c)* seeded exploration, and
   *(d)* a two-learner comparison metric. All four live **above** the microworld, not inside it.

### The minimal adapter needed

**Do not touch `io.matrix.cognitive.microworld`.** Add one small, separate seam. Sketch:

```java
// NEW: matrix-core/src/main/java/io/matrix/learning/env/…  (a NEW package, no change to microworld)

/** An episode over an existing micro-world: reward + termination + seed, bolted on top. */
public interface EnvironmentEpisode {
    String episodeId();
    int steps();                 // steps taken
    boolean done();              // termination
    double utility();            // the reward the microworld does not have
    List<Step> trajectory();     // (input, predicted, observed, applied?) per step
    String successCriterion();   // "survivingHypotheses == 1 && survivor == h-0001"
}

/** The ONE adapter: microworld → EnvironmentEpisode. */
public final class MicroWorldEpisodeRunner {
    public MicroWorldEpisodeRunner(MicroWorldEnvironment env, String episodeId, long seed) { … }
    public EnvironmentEpisode run();  // propose → predict → probe → applyOutcome, until settled
}
```

Concretely, the adapter needs exactly five things:

1. **A driver loop** over the existing pair: `session.proposeExperiment()` →
   `session.predict(input)` → `environment.probe(input)` → `session.applyOutcome(obs)`.
   No new environment logic — every one of these calls already exists.
2. **A termination rule**, derived from what the microworld already knows:
   `session.survivingHypotheses().size() == 1` **or** `session.observations().size() == 4`
   (the input domain is exhausted), plus a hard step cap.
3. **A utility function**, computed from the microworld's own numbers rather than invented:
   the natural one is **survivors eliminated per probe** —
   `utility = 1 - survivors_after / 16`, or the already-defined
   `Experiment.expectedInformationGain()` (`HypothesisSpace.informationGain` :201). This keeps the
   metric *comparable with the W44 benchmark* instead of introducing a second scale.
4. **A seed**, taken from the repo's existing compliant primitive:
   `ExploratoryActionSampler.sample(stateHash, candidates, cycleCount)`
   (`ExploratoryActionSampler.java:35`) — the deterministic-seed helper that is currently
   **unused in production** and is exactly the right tool. Its `mix(stateHash, cycleCount)` (:95)
   gives a reproducible action sequence from a fixed seed, CONSTITUTION-compliant
   (the class javadoc :17-20 states the rationale).
5. **A trajectory record** built from the values the session already returns:
   `session.predictions()` :482, `session.observations()` :245,
   `session.rejectedHypotheses()` :333, `session.survivingHypotheses()` :328.

**Explicitly NOT needed:** a new environment class, a new store, a new episode-id scheme, a new
prediction type, a new error type. The microworld already has all of them, and they are durable
(`CognitiveStore` + `KnowledgeStore`).

**One caveat to flag to the owner** (found while reading, not a blocker):
`docs-v2/engineering/W44-BENCHMARK.md` §5 and §6.1 state that a settled model **promotes** the
fast path with "agreement 1.0 over ≥ 2 held-out cases", but the **current W45.0 code refuses to
promote anything** in the lamp world, because the independent-validation set is always empty
once the model settles (`CognitiveKernel.promoteIfValidated` :815-831, and the javadoc :803-811
says so explicitly: *"In the lamp world that set is always empty, and that is a fact about the
world rather than a bug"*). The benchmark document predates that change. Any W51 §7 metric that
counts "procedures promoted" must read the **W45.0** behaviour, not the W44 benchmark table.

---

## 8. Evidence index (files read in full or in part)

**Microworld (read in full):**
`core/io/matrix/cognitive/microworld/` — `MicroWorldEnvironment.java` (36),
`LampEnvironment.java` (150), `WorldInput.java` (131), `Observation.java` (86),
`ObservationDisposition.java` (226), `RuleHypothesis.java` (157), `HypothesisSpace.java` (365),
`MicroWorldSession.java` (658), `HypothesisPrediction.java` (51), `Experiment.java` (69),
`PredictionError.java` (53), `RejectedHypothesis.java` (39).

**Kernel side:** `MicroWorldCodec.java` (231), `MicroWorldComputation.java` (51),
`MicroWorldReferenceMethod.java` (124), `MicroWorldSettledModelMethod.java` (122),
`CognitiveKernel.java` (1005), `CognitiveService.java` (456), `CognitiveResource.java` (229),
`CognitiveStore.java` (2121, schema + store methods), `EpisodeIdAllocator.java` (98),
`IdSource.java` (43), `Prediction.java` (56), `ErrorSignal.java` (66), `LearningTrace.java` (75),
`CognitiveOutcome.java` (54), `KnowledgeWorkbench.java` (2264, decay + `databasePath`),
`KnowledgeStore.java` (`caseDispositions` :3703).

**Learning loop:** `ContinuousLearningLoop.java` (176), `ErrorDrivenLearner.java` (166),
`ExploratoryActionSampler.java` (102), `SensorimotorLoop.java`, `QLearning.java` (105),
`CurriculumEngine.java`, `AgentLoop.java`, `MatrixLifecycleManager.java`.

**Other environments:** `io/matrix/simulation/*`, `io/matrix/pilot/*`,
`io/matrix/minecraft/*`, `io/matrix/embodied/*`, `io/matrix/federation/liquid/GridWorldBridge.java`,
`io/matrix/life/SurvivalScenario.java`, `io/matrix/mcts/MctsTree.java`,
`io/matrix/federation/liquid/MCTSPlanner.java`.

**Docs:** `docs-v2/engineering/W44-BENCHMARK.md` (§4 micro-world, §5 kernel, §6 HTTP, §7 limits),
`docs-v2/engineering/PLAN.md`, `docs-v2/INDEX.md`.
