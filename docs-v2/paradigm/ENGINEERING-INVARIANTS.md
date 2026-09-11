# ENGINEERING-INVARIANTS — Engineering Rules for MATRIX

**Status:** normative · **Version:** v1 · **Date:** 2026-09-07

> Companion to CONSTITUTION.md. Defines engineering rules that
> MUST be enforced by tooling (gradlew, CI, linters) where possible.

---

## 1. Code Style and Layout

### 1.1 File naming
- Class name = file name (Java standard).
- Tests: `<Class>Test.java` in `src/test/java/...`.
- Package: `io.matrix.<layer>` — see TRACEABILITY.md for layer list.

### 1.2 License header
- Required: SPDX-License-Identifier in every Java file (Apache-2.0 OR MIT).
- No other license markers.

### 1.3 Naming
- Classes: PascalCase, no abbreviations.
- Methods: camelCase, verb-led (`computeX`, `extractY`).
- Booleans: `is*`, `has*`, `can*`.
- Constants: SCREAMING_SNAKE_CASE.
- Acronyms of ≤3 chars: full caps (`BRC`, `K_MAX`, `ONNX`).
  Of 4+ chars: PascalCase (`Http`, `Token`, `Memory`).

### 1.4 Imports
- No wildcard imports (`*`).
- Sort by category: java.*, jakarta.*, io.matrix.*, third-party.
- Configure IDE to enforce.

### 1.5 Comments
- JavaDoc on every public class.
- Method JavaDoc on every public method if name not self-explanatory.
- No comments that just repeat code.
- Each comment that makes a *claim* must reference an EXP.

---

## 2. Testing Strategy

### 2.1 Test pyramid
- 70% unit tests (single class, deterministic, fast)
- 20% integration tests (multi-class, may touch disk)
- 10% EXP tests (full system, may take seconds)

### 2.2 Unit test rules
- One assertion focus per `@Test`.
- Test name documents behavior: `kMax_is_20_even_with_21_inputs_throws`.
- No shared mutable state between tests.
- All assertions include a message with the value.
- Random seed for property tests: **fixed** (e.g., `0x5A5A5A5A`).

### 2.3 Determinism requirements
- No `Math.random` in tests or production.
- No `Instant.now()` / `System.currentTimeMillis()` in production runtime
  (allowed only in offline tooling).
- No `SecureRandom` in production runtime.
- If a test must depend on time, use a `Clock` interface and inject.

### 2.4 Coverage gate
- JaCoCo minimum 82% method coverage on `matrix-core`.
- Lower only via RFC.
- Branch coverage also tracked; not gated yet.

### 2.5 EXP tests
- Live EXP tests tagged `@Tag("exp")`.
- Skipped in default test run.
- Run via `./gradlew expTest` (dedicated task).
- Each EXP test produces a markdown report in `research/reports/`.

---

## 3. Logging and Observability

### 3.1 Logging
- Use SLF4J (Quarkus default).
- Level INFO for normal flow, DEBUG only for explicit `--debug`.
- No PII / secrets logged.
- No `System.out.println` in production code.

### 3.2 Tracing
- `x-matrix-trace` mandatory for every decision.
- Trace fields: `step`, `inputHash`, `outputHash`, `prevHash`, `budgetUsed`.
- Trace storage: append-only json-lines or SQLite (decided by config).

### 3.3 Metrics
- Prometheus-compatible metrics via Micrometer.
- Required per module: rate, latency p50/p95, errors.
- Custom: `matrix_decisions_total`, `matrix_bir_eval_total`,
  `matrix_ethics_gate_total{verdict=...}`.

### 3.4 Health endpoints
- Quarkus `/q/health` mandatory.
- Custom `/v1/matrix/health` with component-level checks.

---

## 4. API Design

### 4.1 REST endpoints
- Resource suffix: `Resource` (e.g., `MatrixLoopResource`).
- Path prefix: `/v1/<area>/<resource>`.
- Versioned: bumps only via RFC.
- OpenAPI generator: required for new endpoints.

### 4.2 Error contract
- Standard error JSON: `{"error": "...", "code": "...", "details": {...}}`.
- HTTP status: 4xx for client, 5xx for server.
- 503 for budget-exceeded, 422 for unsatisfiable preconditions.

### 4.3 Idempotency
- POST endpoints accept `Idempotency-Key` header.
- Same key within 24h returns same response.

---

## 5. Build and CI

### 5.1 Build
- Gradle single root, multiple modules: matrix-core, matrix-app,
  matrix-tools (offline), matrix-formal (TLA+).
- Java 25 baseline. No older language features relied on.
- Build time budget: <10 min for matrix-core.

### 5.2 CI gates (must pass on PR)
- `./gradlew test` — must be green.
- `./gradlew expTest` — must be green (or skipped with reason).
- `./gradlew coverage` — ≥82% on matrix-core.
- `./gradlew formatCheck` — code style.
- `./gradlew tlaCheck` — TLA+ specs TLC-clean.
- `./gradlew xMatrixTrace --print` — sanity.
- `./gradlew decisionPathAudit` — no LLM/RNG in runtime path.

### 5.3 Dependencies
- Listed in `engineering/STANDARDS-MATRIX.md`.
- Major upgrades only via RFC with EXP evidence.
- No snapshot versions in `main`.

---

## 6. Tools Allowed Offline (NOT in runtime)

These are ALLOWED in `tools/distill/` and similar packages,
but MUST NOT be referenced from runtime decision paths:

- ONNX Runtime (`onnxruntime-*`)
- Qwen, LLaMA, Mistral, any LLM inference
- HuggingFace clients
- Any non-deterministic inference engine

Decision-path is defined as:
- `consciousness/`, `brain/`, `mediator/`, `reasoning/`,
  `actions/`, `ethics/`, `memory/` (when used inline),
  `lifecycle/` (when used inline).

These packages may import from other runtime packages,
but may NOT import from `tools/distill/**`.

---

## 7. Tooling Integrations

### 7.1 LSP
- All Java files opened in editor MUST have LSP diagnostics clean.
- `lsp_diagnostics` runs in pre-commit.

### 7.2 Codegraph
- Codebase knowledge graph auto-indexed.
- Decision-path changes MUST be paired with codegraph_explore
  to confirm blast radius.

### 7.3 Context7
- Library docs queries are required before adding new deps.

### 7.4 Memory Layer
- Working memory persists across sessions.
- Phase closure triggers `memory_session_commit`.

---

## 8. Documentation Style

### 8.1 Tiered docs
- **L0** docs (Manifesto, Constitution): rarely change, FROZEN-style.
- **SPEC-***: technical specs of subsystems, change on refactor.
- **DESIGN-***: design decisions, change on re-design.
- **REPORT-***: post-hoc analysis, FROZEN once published.
- **WAL**: append-only journal.
- **FINALSUMMARY**: rolling summary.

### 8.2 Markdown standards
- One H1 per file (filename as title).
- Tables: aligned, no line-wrapping in cells.
- Code blocks: fenced with language tag.
- Links: relative path within `docs-v2/`.

### 8.3 Diagrams
- Architecture diagrams in `architecture/` use Mermaid or Excalidraw.
- Each diagram has an associated `.md` explaining decisions.

---

## 9. FROZEN Zones (mirrored from CONSTITUTION III)

The following are off-limits without an explicit RFC:

- `CONSTITUTION.md` itself
- `AGENTS.md` itself
- `matrix-core/src/main/java/io/matrix/ethics/frozen/**`
- `matrix-core/src/main/resources/avro/**`
- `.github/workflows/**`
- Anything tagged `@FROZEN` in Java code

Any PR touching these without RFC will be auto-rejected.

---

## 10. Anti-patterns

- ❌ Adding wrapper for an external service without RFC
- ❌ "Quick fix" that bypasses ActionGate
- ❌ Magic numbers without EXP evidence
- ❌ Long methods (>50 lines without RFC)
- ❌ Cyclic package dependencies
- ❌ Static state (singletons) outside `Kernel`
- ❌ Hidden side effects in constructors
- ❌ Reflection (use compile-time types only)

---

## 11. Enforcement

Violations caught in:
- Pre-commit hooks
- CI gates (`./gradlew check`)
- Code review
- Goal Guard review gates (per session)
