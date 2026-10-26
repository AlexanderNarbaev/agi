# Module lifecycle registry

**Status:** authoritative · 2026-10-26 · RECON-W38.4 (owner directive §16)

Every directory in this repository that is a *module* has exactly one
lifecycle state. The state says what the module is, what is actually
verified about it, and what would have to change for the state to change.
A module with no state is a module whose status is an assumption; this
registry removes the assumption.

**The states**

| State | Meaning |
|---|---|
| **ACTIVE** | Registered in the Gradle build. Built and tested by `./gradlew test` on every CI run. |
| **ACTIVE-EXTERNAL** | Not in Gradle, but it has its own toolchain, **its own test suite runs in CI**, and it is a supported part of the product. |
| **DORMANT** | Not in the Gradle build and not verified by any automated process. Contents retained. Re-registration is a deliberate future decision. |
| **PLANNED** | Declared but not implemented. |

**The rule that keeps this honest:** a module may only be described as
*verified* when a named, repeatable command actually verifies it. A skipped
check, a module with no test suite, and a suite that cannot start are all
recorded as what they are.

---

## ACTIVE — registered in the Gradle build

Every module below is included in `settings.gradle`, is compiled by
`./gradlew compileJava compileTestJava`, and its tests run by
`./gradlew test` in the `build-test` CI job. Test-class counts are the
number of `*Test.java` files under `src/test`.

| Module | Role | Test classes |
|---|---|---|
| `matrix-core` | The research core: BIR/TSETLIN/CLAUSESET/BDD, HDC, MCTS, transcoders, federation, life, scaling, recording. **FROZEN** (CONSTITUTION Art. III). | 1 046 |
| `matrix-brain-runtime` | The cognitive orchestration layer. See ADR-2026-10-26-002 for the single-conductor decision. | 110 |
| `matrix-api-gateway` | REST/GraphQL surface, JWT/OAuth2, OpenAPI; the containerised serving path. | 35 |
| `matrix-quality` | Quality gates: the reviewer gate, the prompt auditor, the compliance reporter. | 7 |
| `matrix-billing` | Stripe webhooks, credits, licence engine. | 6 |
| `matrix-audit` | Hash-chained logs and GDPR pruning. | 5 |
| `matrix-observability` | Prometheus / Grafana stack. | 4 |
| `matrix-operator` | Operator surface. | 4 |
| `matrix-spigot` | Spigot adapter. | 2 |
| `matrix-sdk-java` | Java SDK, published to Maven Central. | 2 |
| `matrix-tools-distill` | Offline distillation tooling (ONNX activation capture). | 1 |
| `pilots/smart-home-agent` | Pilot package. | 1 |
| `pilots/edu-assessor` | Pilot package. | 1 |
| `pilots/compliance-bot` | Pilot package. | 1 |

## ACTIVE-EXTERNAL — outside Gradle, but verified in CI

| Module | Toolchain | Verified by | State (2026-10-26) |
|---|---|---|---|
| `matrix-web-ui` | Next.js 16.3.5 / React 18 / jest 29 | CI job **`web-ui-tests`** in `.github/workflows/ci.yml`: `npm ci --legacy-peer-deps` then `npx jest --ci` | **8 suites, 50 tests, 0 failures** |

**This module's suite had never run.** The jest configuration could not
start: `setupFilesAfterEach` is not a Jest option (so `jest.setup.ts` was
never loaded and every `toBeInTheDocument()` assertion would have failed),
the transform named `ts-jest` which was not a declared dependency, and
`testEnvironment: 'jsdom'` needed `jest-environment-jsdom`, which Jest 28+
no longer ships. The configuration now uses `next/jest` — the transform
Next.js itself documents — and the two missing dependencies are declared.

Once the suite could run, **12 of 50 tests failed**: nine because
`jest.spyOn(global, 'fetch')` found no `fetch` in the jsdom environment, and
three because the queries were under-specified (`getByText(/FREE/i)` matched
both the plan name and its call to action). All twelve are fixed, and the
component behaviour they assert was not changed.

**Known defect, not fixed here:** `eslint-config-next@16.3.6` requires
`eslint >= 9`, while the project pins `eslint@8.57.1`, so a strict
`npm install` fails with `ERESOLVE`. CI therefore installs with
`--legacy-peer-deps`. Aligning the two is a **major dependency upgrade**,
which AGENTS.md forbids without an explicit RFC, so the conflict is recorded
here rather than silently resolved.

## DORMANT — retained, not built, not verified by any automated process

These three were deregistered from `settings.gradle` by TRUE-W0
(`docs-v2/research/TRUE-MIND-AUDIT-2026-09-26.md`, §4), which chose
deregistration over writing minimal honest stubs. The audit promised
"archived markers in `docs-v2/archive/` so future waves can resurrect them";
**those markers did not exist** and are written now.

| Module | Contents | Verification state | Archive marker |
|---|---|---|---|
| `matrix-micro` | C implementation of the MPDT neuron, the LDN→header converter, a firmware tree | **PASS** — `gcc -std=c11` builds `tests/test_mpdt.c` and all 4 tests pass. Verified **on demand only** (`scripts/verify-dormant-modules.sh matrix-micro`); no CI job runs it. | `docs-v2/archive/matrix-micro.md` |
| `matrix-fpga` | Xilinx synthesis script, constraints, LDN→Verilog, an Amaranth simulation of a k=4 MPDT neuron | **SKIPPED** — the `amaranth` package is not installed here, so the suite is *unverified*, not passing. | `docs-v2/archive/matrix-fpga.md` |
| `matrix-ros2` | ROS 2 matrix node, federation bridge, sensor fusion, launch files | **NO-SUITE** — the module declares no test suite at all. `setup.py` lists `tests_require=['pytest']`, which is a declaration, not a test. | `docs-v2/archive/matrix-ros2.md` |

`scripts/verify-dormant-modules.sh` runs all three on demand and reports
**PASS / FAIL / SKIPPED / NO-SUITE** separately. A skipped check is never
reported as a pass.

## PLANNED — declared, not implemented

| Item | Note |
|---|---|
| `matrix-sdk-python`, `matrix-sdk-js` | Not in Gradle (different toolchains). The polyglot SDKs are planned for a split to separate repositories (T-09). No implementation is present in this repository. |

---

## How this registry is kept true

- `settings.gradle` and this file are two views of the same fact, and
  `CognitiveConductorGuardTest` already demonstrates the pattern used for
  architectural decisions: a test that fails when the code and the
  documentation disagree.
- A module's state changes only with a **named command that verifies it**.
  Adding a module to ACTIVE requires that the command is in CI; adding it to
  ACTIVE-EXTERNAL requires that its own CI job runs; DORMANT modules are
  re-classified only when something verifies them.
- Nothing here is a plan or a promise. Where a module has no verification,
  the table says so.
