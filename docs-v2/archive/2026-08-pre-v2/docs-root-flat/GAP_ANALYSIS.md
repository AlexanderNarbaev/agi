# GAP ANALYSIS: Contradictions, Critical Gaps, and Resolution Plan

**Date:** 2026-08-08
**Branch:** docs/matrix-rebuild
**Status:** Active analysis

---

## 1. CONTRADICTIONS

### C1: Coverage floor 82% vs unmeasurable coverage

**Contradiction:** AGENTS.md requires `./gradlew jacocoTestCoverageVerification` (≥82% coverage), but the JaCoCo agent is filtered by Quarkus native-image plugin, making coverage measurement impossible in this environment.

**Impact:** Cannot verify the constitutional requirement (CONSTITUTION VI).

**Resolution options:**
1. Fix Quarkus plugin exclusion (add jacoco agent to allowed list in build.gradle)
2. Use JaCoCo OfflineInstrumentTask (instrument classes at build time, run tests without agent)
3. Use JaCoCo CLI directly on exec file (requires compatible ASM version)
4. Document as known limitation and set coverage floor to "unmeasurable in current env"

**Recommended:** Option 1 or 2. The plugin exclusion is the root cause.

### C2: SPEC-002 FR-A1 requires Avro schema for BIR, but none exists

**Contradiction:** SPEC-002 FR-A1 says "Модуль `matrix-bir`: Avro-схема, три формы, компилятор..." — but the BIR module has no Avro schema.

**Impact:** BIR artifacts cannot be serialized/deserialized for storage or federation.

**Resolution:** Create `matrix-bir/src/main/avro/bir.avdl` with BirArtifact schema.

### C3: SPEC-002 FR-A4 requires TruthTable/DecisionTree migration to BIR, but no migration path

**Contradiction:** SPEC-002 FR-A4 says "TruthTable/DecisionTree заворачиваются в BIR как TT-формы (адаптеры, не переписывание)" — but there's no adapter for DecisionTree, and no deprecation markers on legacy classes.

**Impact:** Legacy code and new BIR code coexist without clear migration path.

**Resolution:** Create TruthTableAdapter, DecisionTreeAdapter, mark legacy classes @Deprecated.

### C4: TsetlinTrainer has no property tests for automata monotonicity

**Contradiction:** SPEC-002 FR-B1 requires "jqwik-свойства автоматов (границы состояний, монотонность feedback)" — but TsetlinAutomaton has no property tests.

**Impact:** Automata behavior not formally verified.

**Resolution:** Add jqwik property tests for state bounds and feedback monotonicity.

---

## 2. CRITICAL GAPS

### G1: No Avro schema for BIR (SPEC-002 FR-A1)

**Gap:** BIR artifacts (TtForm, ClauseSetForm, BddForm) have no Avro schema for serialization.

**Required:**
```avro
record BirArtifact {
  string id;
  string form;           // "tt", "clauseset", "bdd"
  int inputBits;
  int outputBits;
  string provenance;
  double fidelity;
  bytes payload;         // form-specific data
  bytes contentHash;     // SHA3-256
  long createdAt;
}
```

**Files to create:**
- `matrix-core/src/main/avro/bir.avdl`

### G2: No JMH benchmarks for BooleanRuntime (SPEC-002 FR-A3)

**Gap:** FR-A3 requires "JMH-публикация нс/вызов по формам" — no benchmarks exist.

**Required:** JMH benchmark class comparing TT/CLAUSESET/BDD eval performance.

**Files to create:**
- `matrix-core/src/jmh/java/io/matrix/bir/BirEvaluateBenchmark.java`

### G3: No SubstrateBackend interface (SPEC-002 FR-D1)

**Gap:** FR-D1 requires `SubstrateBackend` interface with `evaluate(BIR, batch)`, `compile(BIR)→artifact`, `capabilities()`.

**Required:** Interface + JvmSimdBackend reference implementation.

**Files to create:**
- `matrix-core/src/main/java/io/matrix/bir/SubstrateBackend.java`
- `matrix-core/src/main/java/io/matrix/bir/JvmSimdBackend.java`

### G4: No EXP-002 comparison (SPEC-002 FR-B3)

**Gap:** FR-B3 requires comparing Tsetlin vs BNN vs MPDT-GA on identical inputs.

**Required:** Comparison harness that runs all three on same data.

**Files to create:**
- `matrix-core/src/test/java/io/matrix/tsetlin/Exp002ComparisonTest.java`

### G5: No EXP-003 producer comparison (SPEC-002 FR-C1)

**Gap:** FR-C1 requires comparing Tsetlin vs GA vs ThreeFactorRule as BIR producers.

**Required:** Comparison harness for producer evaluation.

**Files to create:**
- `matrix-core/src/test/java/io/matrix/tsetlin/Exp003ProducerComparisonTest.java`

### G6: HierarchicalMemory has no persistence backend

**Gap:** HierarchicalMemory is in-memory only; lost on restart.

**Required:** SQLite or RocksDB backend for persistence.

**Files to create:**
- `matrix-core/src/main/java/io/matrix/memory/SqliteMemoryBackend.java`

### G7: No real multi-modal input (image/audio decoding)

**Gap:** ImageSignalModule and AudioSignalModule return hash-based summaries, not real features.

**Required:** Real image decoding (PNG/JPEG) and audio decoding (WAV).

**Files to modify:**
- `matrix-core/src/main/java/io/matrix/signals/ImageSignalModule.java`
- `matrix-core/src/main/java/io/matrix/signals/AudioSignalModule.java`

### G8: SubAgent has no process isolation

**Gap:** SubAgent runs in same JVM, no sandboxing.

**Required:** Process isolation or security manager for SubAgent.

**Files to modify:**
- `matrix-core/src/main/java/io/matrix/agent/SubAgent.java`

---

## 3. RESOLUTION PLAN

### Phase A: Fix contradictions (1 day)

1. **C1 (coverage):** Add jacoco agent exclusion fix to build.gradle
2. **C2 (Avro schema):** Create bir.avdl
3. **C3 (migration):** Create TruthTableAdapter + DecisionTreeAdapter
4. **C4 (property tests):** Add jqwik tests for TsetlinAutomaton

### Phase B: Close critical gaps (2 days)

5. **G1:** Avro schema for BIR
6. **G2:** JMH benchmarks for BooleanRuntime
7. **G3:** SubstrateBackend interface + JvmSimdBackend
8. **G4:** EXP-002 comparison harness
9. **G5:** EXP-003 producer comparison harness
10. **G6:** SQLite backend for HierarchicalMemory
11. **G7:** Real image/audio decoding
12. **G8:** SubAgent process isolation

### Phase C: Integration + verification (1 day)

13. Run full test suite with all new tests
14. Verify 82% coverage floor is measurable
15. Run K8s integration test
16. Update WAL to v3.61

---

## 4. FILES TO CREATE/MODIFY

| File | Action | Priority |
|------|--------|----------|
| `matrix-core/src/main/avro/bir.avdl` | CREATE | HIGH |
| `matrix-core/src/main/java/io/matrix/bir/TruthTableAdapter.java` | CREATE | HIGH |
| `matrix-core/src/main/java/io/matrix/bir/DecisionTreeAdapter.java` | CREATE | HIGH |
| `matrix-core/src/main/java/io/matrix/bir/SubstrateBackend.java` | CREATE | HIGH |
| `matrix-core/src/main/java/io/matrix/bir/JvmSimdBackend.java` | CREATE | HIGH |
| `matrix-core/src/jmh/java/io/matrix/bir/BirEvaluateBenchmark.java` | CREATE | MEDIUM |
| `matrix-core/src/test/java/io/matrix/tsetlin/TsetlinAutomatonPropertyTest.java` | CREATE | MEDIUM |
| `matrix-core/src/test/java/io/matrix/tsetlin/Exp002ComparisonTest.java` | CREATE | MEDIUM |
| `matrix-core/src/test/java/io/matrix/tsetlin/Exp003ProducerComparisonTest.java` | CREATE | MEDIUM |
| `matrix-core/src/main/java/io/matrix/memory/SqliteMemoryBackend.java` | CREATE | HIGH |
| `matrix-core/src/main/java/io/matrix/signals/ImageSignalModule.java` | MODIFY | MEDIUM |
| `matrix-core/src/main/java/io/matrix/signals/AudioSignalModule.java` | MODIFY | MEDIUM |
| `matrix-core/src/main/java/io/matrix/agent/SubAgent.java` | MODIFY | MEDIUM |
| `matrix-core/build.gradle` | MODIFY (jacoco fix) | HIGH |

---

## 5. ESTIMATED EFFORT

| Phase | Tasks | Effort |
|-------|-------|--------|
| A: Fix contradictions | 4 tasks | 1 day |
| B: Close critical gaps | 8 tasks | 2 days |
| C: Integration + verification | 4 tasks | 1 day |
| **Total** | **16 tasks** | **4 days** |

---

**End of GAP_ANALYSIS.md**
