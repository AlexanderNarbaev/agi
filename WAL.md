# WAL

**Статус: ephemeral.** Переписывается в конце каждой сессии.

## Активный фокус
Волновая реализация плана `docs/engineering/PLAN-FULL-IMPLEMENTATION.md`: волны W1–W4 закрыты и верифицированы; впереди W5 (SPEC-001 этап B), W6-прогоны, W7-инфраструктура.

## Правила сессии
- НЕ ТРОГАТЬ: ethics/**, CONSTITUTION.md, существующие avro/**, .github/workflows/**
- Python только в docs/research/ и scripts/ (CONSTITUTION VII.1)
- Coverage gate ≥82% METHOD — не понижен (все новые классы с тестами)
- Субагенты недоступны («Insufficient Balance») — реализация выполнялась координатором напрямую

## Что сделано (сессия 2026-08-25)
### Аудит → план
- Полный аудит «спеки/дизайны/гипотезы ↔ код» → мастер-план `docs/engineering/PLAN-FULL-IMPLEMENTATION.md` (волны W1–W7 + реестр BLOCKED-EXT)

### Волна 1: гигиена фундамента
- [x] K_MAX дедуп: `bir/FpgaBackend` → единый источник `TruthTable.K_MAX`
- [x] `io.matrix.runtime.RuntimeLimits` — env-переменные BRC_MAX_STEPS/RAG_TOP_K/MCTS_ITERATIONS реально читаются кодом (+RuntimeLimitsTest 10 green)
- [x] `docs/spec/quantum/BIR-to-MPS.md` создан — закрыт отсутствующий артефакт FR-D3 (спецификация; код квантового бэкенда — BLOCKED: нет субстрата)

### Волна 2: ядро SPEC-000 (`io.matrix.devloop`, 12 классов)
- ScenarioSpec/DifficultyBand/Outcome/CompetenceAssessor(EWMA α=0.3)/CurriculumEngine(ZPD, детерминированный tie-break)/FeedbackComposer(COUNTEREXAMPLE/PARTIAL/CORRECT)/ScaffoldingManager/GateCriteria/MaturityGateKeeper(MA-0..5, только вперёд)/MaturityLevel
- DevLoopTest + DevLoopPropertiesTest (jqwik: монотонность гейтов, ZPD-полоса+lowest-id, EWMA∈[0,1], scaffold bounds) — зелёные

### Волна 3: SPEC-003 (`io.matrix.ktopo`)
- DriftFingerprint (24 bins κ∈[-2,1], L1-норма), FingerprintDistance (точный 1D W1, closed form Σ|ΔCDF|·binWidth), CurriculumOrderer (union-find, density dense→periphery)
- KtopoTest + KtopoPropertiesTest — зелёные. Остатки SPEC-003: Neo4j-exporter (BLOCKED-EXT), TDA-прототип, EXP-004

### Волна 4: дизайн-ядра
- DESIGN-09 `bir.producers.monotone`: MonotoneDecoder — послойное декодирование с замыканием вверх/вниз и детекцией немонотонного оракула; k≤16 exhaustive → fidelity 1.0 (константа-0 = противоречивый дизъюнкт). Пометка: граница ≤n запросов; точные цепи Ханселя C(k,⌊k/2⌋) — future
- DESIGN-11 `budgeter.ConjugateBudgeter`: точный 0/1-DP на gcd-единицах, shadow price λ=V(U)−V(U−1), фолбэк при envelope<minCost или сетке>250k (H-021 интерфейс готов к EXP-021)
- DESIGN-12 `lifecycle.FnlGate`: SHADOW→CANDIDATE→PROMOTED/DEMOTED (TaskCell/CauldronProtocol уже были в ядре)
- DESIGN-13 `actions.{PlanRunner,VersionedContract}`: Hoare P{Q}R поверх существующего ActionRegistry; атомарный своп version+1 с сохранением domainHash
- Все тесты пакетов зелёные (сквозной прогон BUILD SUCCESSFUL)

### Волна 6 (честно)
- EXP-002/003-report.md созданы как preregistration-skeletons со статусом running; числовые вердикты — только после реальных JVM/JMH прогонов

## Следующее действие
W5: каркас дистилляции SPEC-001 этап B (DJL/ONNX учитель → калибровочный корпус → Distiller→BIR, fidelity-метрика). Затем W6-прогоны EXP-002/003 и остатки W4 (Viewpoint/Persona, M4-CRDT).

## Известные проблемы
- yosys/nextpnr отсутствуют в окружении — FPGA-синтез локально не гоняется (компилятор ldn2v.py готов)
- Субагенты: «Insufficient Balance» — делегация недоступна
- LSP стабильно даёт ложную синтаксическую ошибку на bir/FpgaBackend.java:150 (компиляция чистая)
- Env-переменные BRC_MAX_STEPS/RAG_TOP_K/MCTS_ITERATIONS подключены точечно через RuntimeLimits; полная проводка во все call-sites — при рефакторинге соответствующих подсистем
- Послойный MonotoneDecoder: гарантия ≤n запросов; апгрейд до полных цепей Ханселя — отдельная задача DESIGN-09
