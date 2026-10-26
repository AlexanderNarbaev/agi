# Archived module: `matrix-ros2`

**State:** DORMANT — deregistered from `settings.gradle`, contents retained.
**Deregistered:** TRUE-W0 (`docs-v2/research/TRUE-MIND-AUDIT-2026-09-26.md`, §4).
**Lifecycle registry:** `docs-v2/architecture/MODULE-LIFECYCLE.md`.

## What it was

ROS 2 nodes exposing the MATRIX brain to a robot: a matrix node, a bridge to
the federation protocol, and a sensor-fusion node.

## Why it was deregistered

The module contains **0 `.java` files**, so it could not participate in the
Gradle build. TRUE-W0 chose deregistration over writing minimal honest stubs.

## What it contains now

- `matrix_node.py` — the ROS 2 node
- `ros2_bridge.py` — the federation bridge
- `sensor_fusion.py` — sensor fusion
- `launch/` — launch files
- `setup.py`, `package.xml` — packaging

## Verification state (measured 2026-10-26)

`scripts/verify-dormant-modules.sh matrix-ros2` reports **NO-SUITE**: the
module declares **no test suite at all**. `setup.py` lists
`tests_require=['pytest']`, which is a *declaration* that pytest is needed —
it is not a test, and running pytest finds nothing.

So this module is not merely unverified; it has nothing to verify.

## How to re-register

1. Write the missing tests, or state explicitly that the module ships
   untested and why.
2. Uncomment `include 'matrix-ros2'` in `settings.gradle`.
3. Decide who runs them: ROS 2 needs a sourced ROS installation, which the CI
   runners do not currently have.
