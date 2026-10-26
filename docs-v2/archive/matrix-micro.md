# Archived module: `matrix-micro`

**State:** DORMANT — deregistered from `settings.gradle`, contents retained.
**Deregistered:** TRUE-W0 (`docs-v2/research/TRUE-MIND-AUDIT-2026-09-26.md`, §4).
**Lifecycle registry:** `docs-v2/architecture/MODULE-LIFECYCLE.md`.

## What it was

A C implementation of the MPDT neuron, small enough to run on a
microcontroller, together with the tools that turn a logic-dependency network
into a C header.

## Why it was deregistered

The module contains **0 `.java` files**, so it could not participate in the
Gradle build. TRUE-W0 chose deregistration over writing minimal honest stubs.

## What it contains now

- `src/mpdt_neuron.c`, `src/mpdt_neuron.h` — the neuron
- `tests/test_mpdt.c` — the test suite
- `tools/ldn2h.py`, `tools/test_ldn2h.c`, `tools/test_sample.ldn` — the
  LDN-to-header converter and its fixture
- `firmware/`, `examples/`

## Verification state (measured 2026-10-26)

`scripts/verify-dormant-modules.sh matrix-micro` reports **PASS**: the suite
compiles with `gcc -std=c11` and all four tests pass (validation, table-size
calculation, describe function, input masking).

This is the one dormant module whose suite is verified green — but it is
verified **on demand only**; no CI job runs it, because the CI runners have
no reason to carry a C toolchain for a module that is not registered.

## How to re-register

1. Confirm `scripts/verify-dormant-modules.sh matrix-micro` still reports PASS.
2. Uncomment `include 'matrix-micro'` in `settings.gradle`.
3. Decide who runs the C suite: either a Gradle task that shells out to
   `gcc`, or a dedicated CI job.
