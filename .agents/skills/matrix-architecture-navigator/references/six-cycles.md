# The six cycles

Six cycles are named in the codebase, and two more exist that **serve nobody**. The table
is from `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` §3, which measured the
production callers from the constant pool of the compiled classes.

| the cycle | the status | the production callers |
|---|---|---|
| `io.matrix.reasoning.BrainLoopService` | **SERVED (chain A)** — the only cycle behind the chat surface | 1 — `OpenAIChatResource` |
| `io.matrix.reasoning.ConsciousnessLoop` | **SERVED (chain A)** — the nine-stage loop | 1 — the reasoning `BrainLoopService` |
| `io.matrix.cognitive.CognitiveKernel` | **SERVED (chain A)** | 1 — `CognitiveService` |
| `io.matrix.brain.runtime.TrueMindCycle` | **SERVED (chain B)** | 3 — `ProductionBrainClient`, `MindRuntimeCli`, `ActuationStage` |
| `io.matrix.brain.BirBrainCycle` | **SERVED (chain B)**, plus its own `main` | 3 — `BrainHttpServer` (unmounted), `PersistentMind`, `TrueMindCycle` |
| `io.matrix.brain.runtime.MindCycle` | **experimental, CLI only** | 1 — `MindRuntimeCli`, which is in neither serving closure |

## The two that serve nobody

| the class | the status | the callers |
|---|---|---|
| `io.matrix.consciousness.BrainLoopService` | **inactive but tested** — a plain Java class, not a CDI bean | 25, none in a serving closure: profiling, checkpointing, replay, health and ablation tooling |
| `io.matrix.neuron.ConsciousBrain` | **inactive but tested** | 0 production callers |

## How to tell them apart

- **Same simple name, different package.** `io.matrix.reasoning.BrainLoopService` is served;
  `io.matrix.consciousness.BrainLoopService` is not. The package decides.
- **A `main` method is not a serving path.** `BirBrainCycle` has one and is served anyway —
  but through `PersistentMind` and `TrueMindCycle`, not through its `main`.
- **The unmounted facade.** The gateway's JAX-RS facade would silently replace the real
  brain with the stub if it were mounted; it is not mounted, and that is recorded in
  `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md`.
