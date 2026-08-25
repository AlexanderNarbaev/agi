# WAL

**Статус: ephemeral.** Переписывается в конце каждой сессии.

## Активный фокус
Волновая реализация плана `docs/engineering/PLAN-FULL-IMPLEMENTATION.md`: волны W1–W7 закрыты в доступной ёмкости; DESIGN-14 миграция A-1/A-2 выполнена, A-3 классифицирована; W6 — EXP-010 доведён до **H-010 accepted**, EXP-002 — baseline реализован и **H-002 refuted-toy**.

## Правила сессии
- НЕ ТРОГАТЬ: ethics/**, CONSTITUTION.md, существующие avro/**, .github/workflows/**
- models/pretrained|training_data — карантин: не удалять и не менять (инцидент удаления 42 файлов устранён восстановлением из HEAD)
- Coverage gate ≥82% METHOD не понижен; каждый новый класс с тестами
- Значимые изменения → commit + push (директива владельца)

## Что сделано (серия 2026-08-25, коммиты ed42fd1…9a10d08)
### Фундамент и спеки
- Аудит «спеки↔код» → план `docs/engineering/PLAN-FULL-IMPLEMENTATION.md`
- K_MAX дедуп (`FpgaBackend`→`TruthTable.K_MAX`); `runtime.RuntimeLimits` (+10 тестов) — env-конфиг реально читается
- `docs/spec/quantum/BIR-to-MPS.md` создан (FR-D3 spec-only)

### Волны реализации
- W2 SPEC-000: `devloop` 12 классов + jqwik-свойства (гейты MA-0..5 монотонны)
- W3 SPEC-003: ktopo DriftFingerprint / FingerprintDistance / CurriculumOrderer
- DESIGN-09 `bir.producers.monotone.MonotoneDecoder` (замыкание по монотонности, exhaustive fidelity)
- DESIGN-11 `budgeter.ConjugateBudgeter` (точный 0/1-DP на gcd-единицах, λ shadow price)
- DESIGN-12 `lifecycle.FnlGate` (SHADOW→CANDIDATE→PROMOTED/DEMOTED) + `ConsolidationCycle`
- DESIGN-13 `actions.{PlanRunner,VersionedContract}` + DESIGN-15 `PlanPreprocessor` (AC-3 fast-fail)
- DESIGN-02 `brain.Viewpoint`; W7 CRD SignalModule/TaskCell в operator + `federation.ElspChannel` (anti-replay)

### Миграция DESIGN-14 (волны A)
- A-1: ExplanationGenerator 24 сайта → BIR (TtForm-кэш); A-2: NeuralBrain 9 сайтов (DecisionTreeAdapter); A-3: классификация neuron/* — 13 транзитивно-BIR, Batch* = SIMD-утилиты под JMH-гейт
- Аннекс-реестр: `docs/engineering/DESIGN-14-call-site-audit.md`

### Эксперименты W6 — вердикты протокола
- **H-010 accepted (synthetic-scope)**: протокол 9 прогонов (3 датасета × 3 seeds), median speedup 242.43×, WiSARD выигрывает точность 9/9 (minAdvantage +5 п.п.) — `Exp010ComparisonTest`, отчёт EXP-010-report.md
- **H-002 refuted-toy**: реализован baseline `evolution.MpdtGaProducer`; GA быстрее ×5.5, точнее до +8.75 п.п., компактнее в тысячи раз — `Exp002ComparisonTest`, отчёт EXP-002-report.md

### Дополнительные закрытия (конец серии)
- INV-1 реализован как source-scan страж `bir.Inv1SourceGuardTest` (без deps; выполняется штатным test-таском → действует в CI)
- A-3c: `neuron/SchemaDescriptor` 4 сайта → BIR
- Аудиты: DESIGN-03 REST/MCP поверхность уже существует; DESIGN-06 прод-сигналы `signals/*` уже в ядре (embed-hash BLOCKED-EXT)

## Следующее действие
DJL/ONNX учитель для Distiller (нужна зависимость) · MPDT-GA на доменных корпусах (нужны данные) · JMH-гейт Batch*→evalBatch · алиасы /matrix/* и audio-events (этап 3) — по мере доступности ресурсов.

## Известные проблемы
- yosys/nextpnr отсутствуют — FPGA-синтез локально BLOCKED
- Субагенты «Insufficient Balance» — делегация недоступна
- LSP ложная ошибка bir/FpgaBackend.java:150 (компиляция чистая)
- MonotoneDecoder: граница ≤n запросов (полные цепи Ханселя — future)
- Инцидент: внешне удалён карантин models/{pretrained,training_data} (42 файла) — восстановлен из HEAD в этой сессии; источник удаления не установлен
