# DESIGN-69 — The ONNX Runtime Boundary

**Status:** normative design · RECON-W38.4 · 2026-10-26
**Author:** Coordinator, RECON-W38
**Supersedes:** nothing. **Amends:** the rationale comment in `matrix-tools-distill/build.gradle`.
**Builds on:** CONSTITUTION Article I, `docs-v2/security/LLM-QUARANTINE.md`, `docs-v2/architecture/FORMAL-CONTRACTS.md`

---

## 0. What this document is

A reconciliation of a stated constraint with the actual tree, and a narrower
constraint that is both true and enforced.

It is **not** a claim that the ONNX dependency was removed. It was not, and
the reasons are recorded below.

## 1. The stated constraint was false

`matrix-tools-distill/build.gradle` justified its isolation with:

> CONSTITUTION I: matrix-core runtime classpath must NOT include ONNX runtime.

That statement is **factually false of this repository**. Verified
2026-10-26:

```
$ grep -n "onnxruntime" matrix-core/build.gradle
71:    implementation 'com.microsoft.onnxruntime:onnxruntime_gpu:1.29.0'
72:    implementation 'com.microsoft.onnxruntime:onnxruntime:1.29.0'
```

Five classes in `matrix-core` import the ONNX runtime API
(`ai.onnxruntime`):

| Class | Package | Role |
|---|---|---|
| `io.matrix.api.OnnxRuntimeAdapter` | `io.matrix.api` | quarantined LLM-serving |
| `io.matrix.api.QwenOnnxBridge` | `io.matrix.api` | quarantined LLM-serving |
| `io.matrix.api.TextEmbedder` | `io.matrix.api` | quarantined LLM-serving |
| `io.matrix.rag.FloatEmbeddingIndex` | `io.matrix.rag` | optional float-embedding index; has a pure-Java fallback (`fromValue`) |
| `io.matrix.distill.OnnxActivationTeacher` | `io.matrix.distill` | offline distillation teacher |

No normative document — not `CONSTITUTION.md`, not `docs-v2/architecture/*`,
not `docs-v2/designs/*` — states the constraint. It existed only as a
comment in one build file, and it had been relied on as if it were
architecture.

The isolation of `matrix-tools-distill` as a separate Gradle project is
**kept**: it keeps the offline distillation CLI out of the production runtime
and out of the core module graph. That value is real and independent of the
false sentence.

## 2. The constraint that actually holds

ONNX-backed inference must not participate in a **decision**. That is
enforceable, and it is enforced in two independent places:

### 2.1 Decision-path packages (source scan)

`io.matrix.auditor.DecisionPathAuditor` treats these packages as
decision-path:

```
io/matrix/consciousness/  io/matrix/brain/       io/matrix/mediator/
io/matrix/reasoning/      io/matrix/actions/     io/matrix/ethics/
io/matrix/imports/        io/matrix/neuron/
```

Any `import` of a forbidden symbol in such a file is a blocking VIOLATION.

### 2.2 The runtime mind path (reachability guard)

`io.matrix.brain.runtime.RuntimeLlmGuardTest` asserts that the quarantined
LLM-serving classes — including the three ONNX ones — are not reachable from
the runtime mind path. This is a **reachability** property, not a classpath
property, and it is the property that actually matters for production.

## 3. Two defects found in the enforcement itself

Fixing the false constraint exposed that the enforcement had gaps. Both are
now closed and both are covered by tests.

### 3.1 The forbidden-symbol list named the wrong package

The ONNX **runtime API** lives in `ai.onnxruntime`. The
`com.microsoft.onnxruntime` package holds the generated **protobuf** classes.
The auditor forbade only `com.microsoft.onnxruntime.`, so a decision-path
file importing the runtime API — the import that actually performs
inference — was **not flagged**.

`ai.onnxruntime.` is now also forbidden
(`DecisionPathAuditor.FORBIDDEN_SYMBOLS`).

### 3.2 One rule could never fire

The rule `io.matrix.distill.onnx.` names a package that **does not exist**:

```
$ ls -d matrix-core/src/main/java/io/matrix/distill/onnx
ls: cannot access '...': No such file or directory
```

The class that actually performs ONNX inference for distillation is
`io.matrix.distill.OnnxActivationTeacher`. The rule now names the real
class.

Both defects are covered by new tests in `DecisionPathAuditorTest`
(13 tests, up from 10) and verified against the real tree: **zero**
decision-path files import ONNX.

## 4. What was NOT done, and why

- **The ONNX dependency was not removed from `matrix-core`.** Two of its five
  consumers are genuinely used production code paths
  (`io.matrix.rag.FloatEmbeddingIndex`, `io.matrix.distill.OnnxActivationTeacher`),
  and removing the artifact would break them. The directive is explicit:
  *do not delete a dependency merely because a comment says it should not
  exist*.
- **No class was moved to a new module.** A `matrix-inference-onnx` module
  would be the cleaner end state, but it is a multi-package refactor whose
  value depends on a decision this document does not make: whether the
  optional float-embedding index belongs in the core retrieval path at all.
  That decision needs the ARCHITECTURE-level work in §6, not a
  documentation change.
- **The native-image configuration was not changed.** The GraalVM build has
  its own `--initialize-at-build-time` lists; whether ONNX belongs in a
  native image is a separate question with a separate answer.

## 5. The statement, corrected

The new text in `matrix-tools-distill/build.gradle`:

> No ONNX runtime on the PRODUCTION RUNTIME classpath.
>
> The ONNX runtime may be present on the core classpath for optional
> float-embedding inference and for the offline distillation teacher. It must
> not appear in a decision-path package, and the LLM-serving ONNX classes
> must stay unreachable from the runtime mind path.

## 6. Open question, carried forward

**Should the optional float-embedding index stay in the core retrieval path?**

`io.matrix.rag.HybridBooleanRag` holds a `FloatEmbeddingIndex` and offers a
pure-Java fallback via `FloatEmbeddingIndex.fromValue(int, dim)`. The
`rag` package is currently referenced only from within itself and from
`BooleanRagBenchmark` — it is **not** on the serving path. If the float
index is not going to be served, the honest end state is to move it behind
an optional adapter (or out of the core), which would let the ONNX runtime
leave the core classpath entirely and make the original constraint true by
construction rather than by comment.

That is a design decision with real consequences for retrieval quality. It
is deliberately **not** made here.

## 7. Verification

```bash
./gradlew :matrix-core:test --tests "io.matrix.auditor.*" \
                           --tests "io.matrix.consciousness.BrainLoopCompliance*" --no-daemon
```

- `DecisionPathAuditorTest` — 13 tests, 0 failures (3 new)
- `BrainLoopComplianceTest` — 4 tests, 0 failures
- The real decision-path packages contain **0** ONNX imports.

## 8. Related

- `docs-v2/security/LLM-QUARANTINE.md` — the quarantined class list
- `docs-v2/designs/DESIGN-64-controlled-stochasticity.md` — Article I
- `matrix-core/src/main/java/io/matrix/auditor/DecisionPathAuditor.java`
- `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/RuntimeLlmGuardTest.java`
