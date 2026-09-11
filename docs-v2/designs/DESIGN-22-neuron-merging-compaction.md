# DESIGN-22 — Neuron Merging + Compaction

> Phase P.3 — RUN 338. Решает пробел: в docs-v2 нет механизма слияния или
> компрессии нейронов. Также фиксирует принцип **«одна унифицированная
> матрица»** для всей дистилляции.

## 1. Пробел

Из аудита (subagent-2):

> ⛔ Neuron merging / compaction — не существует как design feature.
> Grep на `merg.{0,30}neuron|neuron.{0,30}merg|compaction` — zero matches.

Ближайшие понятия (всё не то):
- `CRDT merge` в `noosphere/Crdt` — для фактов M4, не нейронов
- `consciousness/CycleMerger.java` — pipeline-merge между компонентами
- `L3 ARCHIVE` в S-011 — TTL=∞ dedup памяти по hash
- `BooleanMinimizer.java` — minimization (упрощение), не merge
- `DESIGN-05` упоминает domainHash для action-контрактов

Вывод: ни BIR-нейроны, ни TT/CLAUSESET/BDD-артефакты не имеют
спроектированной операции merge или compaction. Этот design закрывает.

## 2. **ПРИНЦИП: ОДНА УНИФИЦИРОВАННАЯ МАТРИЦА** (INV-FNL-ONE)

> **Все дистиллированные нейроны — независимо от исходной модели
> (Qwen2.5-0.5B, Llama-3.2-1B, Mistral-7B, GPT-2, etc.) — добавляются
> в ОДНУ FNL-матрицу (`noosphere/FnlPackage` pool).**
>
> **Никаких отдельных «model-like» файлов или архивов под каждую
> дистилляцию. Provenance (имя исходной модели) — metadata, не файл.**

Это CONSTITUTIONAL-level принцип:

```java
// io.matrix.noosphere.FnlRegistry
public final class FnlRegistry {
    /** SINGLE source of truth for all distilled neurons. */
    private static final Map<UUID, FnlEntry> POOL = new ConcurrentHashMap<>();

    /** INV-FNL-ONE: every distillation appends to POOL. No per-model files. */
    public static UUID append(FnlEntry entry) {
        if (entry.provenance == null || entry.provenance.isBlank()) {
            throw new IllegalArgumentException("provenance must be set");
        }
        UUID id = UUID.randomUUID();
        POOL.put(id, entry);
        return id;
    }

    public static List<FnlEntry> byProvenance(String modelName) {
        return POOL.values().stream()
            .filter(e -> modelName.equals(e.provenance))
            .toList();
    }

    public static List<FnlEntry> all() {
        return new ArrayList<>(POOL.values());
    }
}
```

**Контракт:**
- `FNLMetadata` (RUN 13) уже содержит `fnlId`, `name`, `generation`,
  `accuracy` — добавляем `provenance` (String)
- `Distiller.synthesize(provenance)` уже принимает provenance string —
  теперь он ОБЯЗАН идти через `FnlRegistry.append()` после синтеза
- Нет `models/llama-3.2-1b/...` директорий — всё в одном POOL

**Преимущества:**
- Все нейроны кооперируются в `BooleanChainRunner` (один chain может
  содержать нейроны из разных моделей)
- Cross-model consensus (DESIGN-21) возможен без merge файлов
- Compaction (ниже) работает над ВСЕМИ нейронами сразу
- Memory footprint: один `chain-j.bin` вместо N файлов

## 3. NeuronMerger (операция слияния)

### 3.1 Что такое merge

Два `EnrichedNeuron` (a, b) могут быть объединены в один `EnrichedNeuron c`,
если:

```
HammingDistance(a.table, b.table) / 2^k < ε      (битовое расстояние)
| a.magnitude − b.magnitude | < δ_m               (magnitude distance)
| chemicalDistance(a, b) | < δ_c                  (химическое расстояние)
```

Если все три метрики ниже порогов — нейроны считаются дубликатами.
Merge создаёт новый нейрон с усреднёнными параметрами.

### 3.2 API

```java
public final class NeuronMerger {
    public static final double DEFAULT_EPSILON = 0.05;        // 5% Hamming
    public static final double DEFAULT_DELTA_MAGNITUDE = 0.1;
    public static final double DEFAULT_DELTA_CHEMICAL = 0.15;

    /**
     * Pure function. Merges two neurons if they are near-duplicates.
     * Returns Optional.empty() if not mergeable.
     */
    public static Optional<EnrichedNeuron> tryMerge(
        EnrichedNeuron a,
        EnrichedNeuron b,
        double epsilon,
        double deltaMagnitude,
        double deltaChemical);

    /** Convenience: use defaults. */
    public static Optional<EnrichedNeuron> tryMerge(
        EnrichedNeuron a, EnrichedNeuron b);
}
```

### 3.3 Когда применять

- **После дистилляции**: новые нейроны проверяются против POOL на
  near-duplicates. Если нашли дубликат — пропускаем append.
- **Периодический compaction** (DESIGN-22 §4): batch scan POOL,
  mergeable пары объединяются.
- **При загрузке**: если два источника дали одинаковые нейроны
  (например, Qwen и Llama оба обучились на «is-capital») — merge.

### 3.4 Без потерь: lineage запись

```java
public record FnlEntry(
    UUID id,
    TruthTable table,
    double magnitude,
    double[] chemical,
    Neurotransmitter tag,
    String provenance,           // "Qwen2.5-0.5B" / "Llama-3.2-1B"
    List<UUID> parents,          // если merged — ID исходных
    long createdTimestamp,
    int generation
) {}
```

После merge создаётся новый entry с `parents = [idA, idB]`. Lineage
сохраняется для аудита (CONSTITUTION VII).

## 4. NeuronCompactor (компрессия)

### 4.1 Что такое compaction

Компактное представление `FnlRegistry.POOL` для записи на диск:

**Формат `chain-j.bin` v2** (расширение RUN 325):
```
Header:
  MAGIC   = "BLN\2"     (4 bytes; v2 format)
  VERSION = 2            (int)
  TOTAL_NEURONS          (int)
  BLOCK_SIZE = 256       (int; neurons per compaction block)

Per block:
  REFERENCE_NEURON       (full EnrichedNeuron, base)
  DELTA_NEURONS          (255 neurons encoded as XOR against reference)

Per delta neuron:
  XOR_MASK               (long[k], XOR с reference's table)
  MAGNITUDE_DELTA        (float)
  CHEMICAL_DELTA         (4 floats)
```

**Цель**: 24-block Qwen chain (45 МБ raw) → после compaction → <20 МБ
(измерим в EXP-MATRIX.61).

### 4.2 API

```java
public final class NeuronCompactor {
    /**
     * Compacts a list of enriched neurons into a byte[].
     * Pure function: same input → same output.
     */
    public static byte[] compact(List<EnrichedNeuron> neurons, int blockSize);

    /**
     * Loads compacted neurons back. Inverse of compact.
     */
    public static List<EnrichedNeuron> decompact(byte[] data);

    /**
     * Measures compression ratio: 1.0 = no compression, 0.5 = 2x smaller.
     */
    public static double compressionRatio(int rawBytes, int compactedBytes);
}
```

### 4.3 Reference selection

Выбор reference внутри блока — критичен. Алгоритм:

```
1. Compute pairwise Hamming distances within block
2. Pick the neuron with smallest sum of distances to all others (centroid)
3. Other neurons encoded as delta vs centroid
```

CONSTITUTION I: детерминированный, без Random.

## 5. CONSTITUTION compliance

| Article | Compliance |
|---|---|
| I (determinism) | ✅ tryMerge и compact — pure functions |
| II (K_MAX=20) | ✅ работает над существующими TT |
| III (FROZEN) | ✅ не трогаем |
| IV (prohibitions) | ✅ merge не ослабляет этику |
| V (coverage) | требует тесты |
| VI (no forbidden claims) | ✅ не претендуем на «оптимальный» merge |
| VII (audit) | ✅ lineage (parents) сохраняется |
| VIII (substrate-neutrality) | ✅ JVM |

## 6. Acceptance Criteria

| # | Criterion | Evidence |
|---|---|---|
| 1 | `FnlRegistry` singleton + append + byProvenance + all | unit tests |
| 2 | **INV-FNL-ONE enforced**: distiller не может создать файл вне POOL | `DistillerTest` |
| 3 | `NeuronMerger.tryMerge` с дефолтами | merge/skip property tests |
| 4 | Merge сохраняет lineage (parents list) | lineage test |
| 5 | `NeuronCompactor.compact` round-trip lossless | roundtrip test |
| 6 | Compaction ratio: 45 МБ → <20 МБ | EXP-MATRIX.61 |
| 7 | Merged neuron evaluate → identical output to both inputs (within tolerance) | accuracy test |
| 8 | All operations deterministic | determinism test (10 runs same input) |

## 7. Implementation Plan (Phase V.1-V.4, RUN 355-358)

```
io/matrix/neuron/
  EnrichedNeuron.java (DESIGN-20)
  NeuronMerger.java
  NeuronCompactor.java
  EnrichedSerialization.java (extend BLN v2)

io/matrix/noosphere/
  FnlRegistry.java (singleton POOL)
  FnlEntry.java (with parents lineage)
  FnlProvenance.java (model source metadata)
```

## 8. Что НЕ входит

- ❌ Online обучение merge parameters (offline only)
- ❌ Cross-process merge (отдельный design)
- ❌ Federated merge (DESIGN-08 federation)
- ❌ «Идеальное» сжатие через QBF/SAT (другая тема)

## 9. References

- CONSTITUTION.md Art. I, VII, VIII
- DESIGN-01-units.md (BirUnit canonical)
- DESIGN-05-memory.md (M0-M4 hierarchy)
- DESIGN-20-enriched-neurons.md (this design builds on enriched neurons)
- DESIGN-21-chain-triggering.md (compaction affects cross-chain)
- SPEC-002-boolean-compute-layer.md (K_MAX)
- FOUNDATIONS.md §1 (Banach fixed-point — compaction convergence)
