# Archived module: `matrix-fpga`

**State:** DORMANT — deregistered from `settings.gradle`, contents retained.
**Deregistered:** TRUE-W0 (`docs-v2/research/TRUE-MIND-AUDIT-2026-09-26.md`, §4).
**Lifecycle registry:** `docs-v2/architecture/MODULE-LIFECYCLE.md`.

## What it was

A hardware-accelerated MPDT neuron: a synthesizable Verilog core plus an
Amaranth HDL simulation used to check the neuron against a reference truth
table before it reaches a board.

## Why it was deregistered

The module contains **0 `.java` files**, so it could not participate in the
Gradle build, and registering it broke `./gradlew build` and polluted the
module list. TRUE-W0 chose deregistration over writing minimal honest stubs.

## What it contains now

- `synth_xilinx.tcl` — Xilinx synthesis script
- `constraints/` — timing constraints
- `ldn2v.py` — the LDN (logic dependency network) to Verilog converter
- `test_mpdt_fpga.py` — Amaranth simulation of a k=4 MPDT neuron
- `Makefile`, `Makefile.improvements`

## Verification state (measured 2026-10-26)

`scripts/verify-dormant-modules.sh matrix-fpga` reports **SKIPPED**: the
`amaranth` package is not installed in this environment. The suite is
therefore **unverified**, not passing. Install it with `pip install amaranth`
and re-run the script to obtain a real result.

## How to re-register

1. `pip install amaranth` and confirm `scripts/verify-dormant-modules.sh matrix-fpga` reports PASS.
2. Uncomment `include 'matrix-fpga'` in `settings.gradle`.
3. Give the module a Gradle build file, or accept that it is built by its own
   `Makefile` and keep it out of Gradle deliberately — but then say so in the
   lifecycle registry rather than leaving it ambiguous.
