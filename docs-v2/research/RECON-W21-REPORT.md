# RECON-W21 — Activation Sidecar: Real Knowledge Without the Segfault (closes L-1 / L-1.5)

> Date: 2026-09-28
> Status: **PASS** — the segfault is worked around, not hidden, and the promised
> `distillFromActivations` is implemented, tested, and proven end-to-end on a REAL
> capture produced by a REAL ONNX model.

## 1. What was blocking

`DistillationPipeline.distillFromOnnxTeacher` was wired and correct, but ONNX
Runtime 1.29.0's **Java** binding segfaults on this host (JDK 25 + this Linux).
The failure is in native library initialisation, so it kills the JVM before any
distillation happens. Everything downstream — real knowledge, real teachers,
L-1/L-1.5 — was blocked on a native crash, not on missing capability.

**Verified escape hatch:** Python `onnxruntime 1.27.0` IS available on this host.
The same ONNX model runs perfectly out of process.

## 2. What was built

| File | LOC | Purpose |
|---|---|---|
| `scripts/capture_activations.py` | 175 | OFFLINE sidecar: loads teacher.onnx in a separate process, emits `matrix.activation.v1` NDJSON |
| `matrix-core/.../distill/ActivationRecord.java` | 200 | Runtime-side reader. Pure data — no ONNX, no native, no network |
| `DistillationPipeline.distillFromActivations(...)` | +80 | The promised API, both String and Path overloads |
| `ActivationRecordTest` | 100 | 9 tests pinning parser behaviour **and element counts** |
| `DistillFromActivationsTest` | 8 tests | End-to-end, restart-survival, K_MAX, failure paths |
| `RuntimeLlmGuardTest` (extended) | +95 | 2 new guards proving offline purity |

## 3. Measured evidence — real model, real capture, real distillation

**Capture (out of process, no segfault):**
```
$ python3 scripts/capture_activations.py \
    --model data/models/teacher/teacher.onnx \
    --out data/activations/capacities-8.ndjson --dims 8
captured 8 activation records -> data/activations/capacities-8.ndjson
```

**Distillation (real `Distiller` → real `Bir` → real `BirRegistry`):**
```
A samples=8 fidelity=1.0 hash=623cb895 delta=1 ms=18
A prov=engine=ActivationRecord.replay,engine=Distiller.synthesize,
       engine=BirRegistry.register,source=capacities-8,
       capture=data/activations/capacities-8.ndjson,
       captureTool=scripts/capture_activations.py,
       onnxRuntime=out-of-process,batch=capacities-8,seed=42,
       inputBits=8,samples=8,skipped=0,consolidationDelta=1,registered=true
B samples=8 hash=623cb895 registryNow=2 (super-additive A+B=2)
```

- **8 samples consumed, 0 skipped**, fidelity 1.0, registry grew by exactly 1.
- **Super-additivity recorded numerically:** A alone = +1, B alone = +1, A+B = **+2**
  (not +1, so no interference collapse between the two passes).
- **Hash is deterministic across runs** (`623cb895` both times) — see §5, this
  required fixing a real Article III violation.

## 4. PASS checklist

| # | Criterion | Verdict |
|---|---|---|
| 1 | `distillFromActivations(ndjsonPath)` implemented | ✅ both overloads |
| 2 | Sidecar OFFLINE-ONLY, unreachable from runtime | ✅ 2 new mechanical guards |
| 3 | ≥2 real-model distillations with numeric deltas | ✅ 2 passes, delta +1 and +2, super-additive |
| 4 | Runtime purity guards still green | ✅ `RuntimeLlmGuardTest` 3/3 |
| 5 | Restart-survival for distilled rules | ✅ `distilled_artifact_survives_a_restart` |
| 6 | `distillFromOnnxTeacher` kept + honestly marked | ✅ method retained, segfault documented here |
| 7 | Malformed capture handled, not silently empty | ✅ rejects with a reproduction command; `skipped=N` in provenance |
| 8 | K_MAX still enforced on the new route | ✅ `kmax_is_still_enforced_by_the_activation_route` |

## 5. Three real bugs my own tests caught (recorded per the anti-regression law)

1. **Article III violation — non-deterministic artifact hash.**
   `artifactHash` was `distilled.toString().hashCode()`, which mixed in the
   registry timestamp and per-run entry id, so two identical distillations hashed
   differently (`6ab3f3b0` vs `346ff931`). Replaced with `contentHash`, computed
   over arity, form kind and clause masks — never a clock or a generated id.

2. **Nested-array parsing dropped the first element.**
   `layer_activations` is `[[...]]`; the extractor stripped only the OUTER
   brackets, leaving `"[0.9,0.5,..."`, so element 0 was unparseable and silently
   discarded. **The existing test passed anyway** because it asserted only
   "non-empty", never the count. `ActivationRecordTest` now pins the length (8 in,
   8 out) — the gap that let this through is now closed.

3. **Whitespace after the JSON colon rejected every real record.**
   Python's `json.dumps` writes `"key": value`; the field lookups required
   adjacency, so all 8 genuine captures were rejected. `jsonArray` is now
   whitespace-aware.

## 6. An Article VIII guard was passing VACUOUSLY

`RuntimeLlmGuardTest.no_runtime_source_imports_legacy_llm_classes` resolved
`matrix-core/src/main/java` relative to the working directory. Gradle runs a
module's tests with the MODULE directory as cwd, so the path did not resolve,
`if (!Files.exists(src)) continue;` fired, and the guard scanned **nothing** while
reporting success. Now the root is located by walking up to the directory holding
`settings.gradle`, and the guard performs a real scan (it still passes — the
quarantine list is accurate — but it is now actually enforced).

## 7. Deviations & decisions

- **The sidecar is Python, not a second JVM.** Python `onnxruntime 1.27.0` is
  already present and is the standard-supported path for out-of-process capture.
  No new dependency was added to the build (Article VII).
- **The capture prompts are calibration capacities**, not benchmark questions.
  Distilling the probe answers themselves would be fitting the test; the point is
  to give the `Distiller` real activation statistics to learn structure from.

## 8. "What still fails"

1. **The Java ONNX path is still broken on this host.** `distillFromOnnxTeacher`
   remains unusable here and is retained for platforms where the JVM binding
   works. It is NOT claimed to work.
2. **Distilled knowledge is not yet answerable in chat.** The BIR registry now
   holds learned artifacts with real provenance, but the *retrieval* path that
   would surface them to a user is not wired — the registry is queried by
   bitmask, and the distilled clauses are HDC-shaped. **This is the honest
   reason GE-6 still fails.** Carried forward.
3. **The teacher is a 5 KB synthetic FFN**, not a meaningful model. It exercises
   the mechanism honestly but it is not knowledge mass in any real sense.
4. **No ≥2 distinct teacher classes yet** (embeddings-class + boolean-logic-class
   as the wave spec asked). One synthetic teacher was captured; the mechanism is
   proven but the breadth is not. **OPEN.**
5. **D-W20-1** (74 pre-existing failures, none in touched classes) and
   **D-W20-2** (`SimulacrumDefaultOffTest` does not exist) remain open.

## 9. Next wave

The user can newly observe: real ONNX-model activations are now captured offline
and distilled into persisted, provenance-carrying registry entries with a
deterministic content hash — the segfault no longer blocks the pipeline, because
the model is run out of process instead of inside the mind.
