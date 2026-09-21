# MATRIX — Formal Verification Specifications

This directory contains TLA+ specifications for the most security-critical and
consensus-critical components of the MATRIX system. Each spec is paired with
the corresponding Java implementation and is intended to be model-checked with
the [TLA+ Toolbox](https://lamport.azurewebsites.net/tla/tla.html) or
[TLC](https://github.com/tlaplus/tlaplus).

## Why formal verification?

Per L5 §5.4: *"Formal verification of FROZEN (model checking, TLA+)"* is a
mandatory part of the project. The specs in this directory cover:

| Spec file | Component verified | Key invariants |
|-----------|---------------------|----------------|
| `MPDTNeuron.tla` | `io.matrix.neuron.TruthTable` | K_MAX ceiling, deterministic output, idempotency |
| `Consensus.tla` | `io.matrix.consensus.ConsensusEngine` | Decision monotonicity, weighted-evaluation idempotency |
| `FrozenEthicalFNL.tla` | `io.matrix.ethics.frozen.FrozenEthicalFNL` | Neuron-set immutability, deterministic activation |
| `HashChain.tla` | `io.matrix.audit.HashChain` | Append-only, chain integrity, tamper detection, restore validity |
| `BotEthicsPipeline.tla` | `io.matrix.api.BotEthicsPipeline` | Bot monotonicity, audit-tombstone consistency, liveness |

## How to model-check

```bash
# Install TLA+ tools (requires Java 11+)
# https://github.com/tlaplus/tlaplus#getting-started

# Run TLC on a single spec:
java -jar tla2tools.jar -config MPDTNeuron.cfg MPDTNeuron.tla

# Or use the TLA+ Toolbox IDE for interactive model exploration.
```

A `*.cfg` file is recommended for each spec to fix concrete parameter sets
(e.g. bounded Agents, Proposals, Neurons) so TLC can explore the finite
state space within reasonable time.

## Authoring discipline

When you modify a verified Java class:

1. Update the corresponding `.tla` spec in this directory.
2. Add a new invariant for any new behaviour.
3. Re-run TLC (or CI step) and ensure no invariant is broken.
4. Commit spec changes in the same PR as the Java change.

## References

- L1 §3 — MPDT-neuron semantics
- L2 §3.1 — Consensus protocol
- L5 §5.4 — Formal verification requirements
- L7 §3.1 — FROZEN ethical FNL contract
- CRITICAL_GAPS.md GAP-021 (open until TLC passes)
