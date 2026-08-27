**Статус: normative · singleton** · пересмотр.

# L1 — BirUnit (atomic compute element)

## Что

**BirUnit** — атомарная вычислительная единица MATRIX. Вычисление, кодируемое в одной из трёх BIR-форм (`TT` / `CLAUSESET` / `BDD`), исполняется через `BooleanRuntime.evaluate(Bir, long[]) → long[]`. Всякая логика в рантайм-контуре проходит через BirUnit; альтернативной вычислительной единицы не предусмотрено (CONSTITUTION I, II, V).

## Кортеж

`BirUnit = (id, k, F, S, meta)` где:

- `id`: `UUID v7` + generation counter (immutable через `bir/BirRegistry`).
- `k`: 1 ≤ k ≤ 20 (CONSTITUTION II).
- `F`: одна из трёх форм — `TtForm` / `ClauseSetForm` / `BddForm`. Иммутабельный артефакт.
- `S`: стадия жизненного цикла `DRAFT → CANDIDATE → ACTIVE → FROZEN` (`lifecycle/FnlGate.java`).
- `meta`: provenance, lineage (`bir/LineageLedger`), эмпирическая `fidelity`, training-hash.

## Контракт исполнения

- `BooleanRuntime.evaluate(Bir, long[]) → long[]` — детерминированный побитовый переход.
- `BooleanRuntime.eval(long[], long[])` — packed execution (K_MAX=20, один long).
- `BooleanRuntime.evalBatch(long[][], long[][])` — SIMD-батч.
- Прямые вызовы легаси-вычислений вне BirUnit запрещены (`bir/Inv1SourceGuardTest` в CI).

## Формы

- `TtForm` — truth table (packed, K_MAX=20).
- `ClauseSetForm` — clause-set с эмиттером FCR F1/F2-минимизации; экспорт из `TssetlinTrainer` / `MpdtGaProducer` (последний — baseline-сравнение).
- `BddForm` — каноническая BDD-редукция; целевой канонический вид.

## Жизненный цикл

- `DRAFT` → производитель (Tsetlin / WiSARD / MpdtGaProducer) синтезирует BirUnit.
- `CANDIDATE` → shadow-run scoring по двум порогам (`lifecycle/FnlGate.advance`).
- `ACTIVE` → публикуется в реестре `bir/BirRegistry`; доступен для исполнения.
- `FROZEN` → артефакт нельзя модифицировать; требует RFC-процедуры (см. `operations/drafts/DRAFT-RFC-Procedure.md`).

## Метрики (per BirUnit)

- `fidelity` — измеренная согласованность с учителем на holdout (для DRAFT/CANDIDATE).
- `latency` (per-call) — замеряется JMH-протоколом; целевая ≤ 1.5 мкс на packed word.
- `literal_count` — для CLAUSESET; критерий лаконичности.
- `lineage_hash` — tamper-evident через `audit/HashChain`.

## Где смотреть дальше

- `specifications/SPEC-002-boolean-compute-layer.md` — общая спецификация BIR-слоя.
- `designs/DESIGN-01-units.md` — текущий дизайн.
- `algorithms/Tsetlin-Automaton.md`, `algorithms/MPDT-GA.md`, `algorithms/WiSARD-WNN.md` — производители BirUnit.
- `engineering/INVARIANTS.md` — что защищается, а что нет.

Next: для следующего шага чтения обратитесь к `INDEX.md` (единая навигация) или к `vision/BRAIN-LIKE-SYSTEM.md` (синтез мозгоподобной системы).