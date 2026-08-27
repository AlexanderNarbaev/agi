# SPEC-002 — Boolean Compute Layer (BIR)

**Статус: normative** · пересмотр (v2 rebuild) · keystone-спека.

## Что

Ядро MATRIX. Любое булеvo вычисление представлено в одной из трёх эквивалентных форм: TT (truth table; K ≤ 20), CLAUSESET (unlimited clauses), BDD (canonical). Формы взаимно-компилируемы; рантайм-контур исполняет только BIR.

FR: `Bir`, `BirForm` (sealed: TtForm/ClauseSetForm/BddForm), `BooleanRuntime`, `BirCompiler`, `BirRegistry`, `LineageLedger`, бэкенды `JvmSimdBackend`/`FpgaBackend`. K_MAX=20. Инварианты см. ниже.

## Инварианты

- Детерминизм eval (см. `CONSTITUTION.md` I).
- K_MAX=20 (§II).
- Extrinsic-метрики и lineage через `LineageLedger` для аудита.
- FROZEN `ethics/frozen/`.

## Этапы (с точки зрения кода)

- **Этап A — единственная точка исполнения** (DESIGN-14 Критерий A): `BooleanRuntime.evaluate(Bir, long[])`. Migration waves 1-6 завершены; 37 sites мигрировано. Source-scan страж `bir.Inv1SourceGuardTest` без ArchUnit-dep.
- **Этап B — Tsetlin-producer** завершён (`io.matrix.tsetlin.TsetlinTrainer`); 15 тестов зелёные.
- **Этап C — выбор producer** (GA vs Tsetlin): prelim exp ⇒ H-002/H-003 refuted-toy (GA победил на синтетике).
- **Этап D — субстратные бэкенды**: JvmSimd (CPU) готов; FPGA BLOCKED-EXT (нет yosys).

## Реализация

- `bir/BooleanRuntime.java`, `bir/{TtForm,ClauseSetForm,BddForm}.java`, `bir/BirCompiler.java`, `bir/BirRegistry.java`, `bir/LineageLedger.java`, `bir/{JvmSimd,Fpga}Backend.java`, `bir/TruthTableAdapter.java`, `bir/DecisionTreeAdapter.java`.

Тесты: `bir/*` (юнит + property), integration через `BirMigrationEquivalenceTest`.

## EXP-009

BIR-инференс использован как дистиллированный артефакт (FFN→BIR через TsetlinTrainer); см. `research/reports/EXP-009-report.md` — latency 62 нс/eval CPU, ×149 vs ORT-CPU.

## BLOCKED-EXT / отложено

- Квантовый бэкенд (FR-D1 — спека см. `SPEC-002-quantum-bir-mps.md`; код — ждёт субстрата).
- FPGA-бэкенд железный синтез (нет yosys).