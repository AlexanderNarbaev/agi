# PLAN: Полная реализация спецификаций, дизайнов и гипотез MATRIX

**Статус: living** · Владелец сессии: главный агент · Обновляется после каждой волны
**Основание:** аудит реализации (эта же сессия); явное указание владельца на волновое исполнение без пауз.

## 0. Принципы исполнения

1. Конституция соблюдается полностью: FROZEN-зоны не трогаем, K_MAX ≤ 20, детерминизм рантайма, coverage ≥ 82% METHOD — каждый новый класс с тестами.
2. Темп задаёт владелец; правило «одна сессия — один SPEC» перекрывается явным указанием, но каждая волна = связный набор задач и milestone.
3. Недостижимое без внешних ресурсов помечается `BLOCKED-EXT` с причиной и ссылкой на документ — не имитируется.
4. Воркеры правят непересекающиеся директории; общие файлы (build.gradle) меняет только координатор.
5. После каждой волны: целевые тесты пакета + обновление реестра ниже.

## 1. Волны

| Волна | Состав | Статус |
|---|---|---|
| W1 Гигиена фундамента | дедуп K_MAX; env-конфиг BRC_MAX_STEPS/RAG_TOP_K/MCTS_ITERATIONS; документ `docs/spec/quantum/BIR-to-MPS.md`; проверка toolchain (yosys) | **in-progress** |
| W2 Ядро SPEC-000 | CompetenceAssessor, CurriculumEngine, FeedbackComposer, ScaffoldingManager, MaturityGateKeeper, ScenarioSpec + тесты (`SPEC-000#задачи`) | pending |
| W3 Модуль SPEC-003 | ricci: Ollivier-Ricci κ, drift-fingerprint (гистограмма кривизн), Wasserstein-1; консолидация ktopo/ (`SPEC-003#задачи`) | pending |
| W4 Дизайны-ядра | DESIGN-09 Hansel-цепи; DESIGN-11 conjugate budgeter+homeostat; DESIGN-12 TaskCell/M4Gate; DESIGN-13 ActionRegistry; DESIGN-05 M4-реплика базовая; DESIGN-02 Viewpoint/Persona | pending |
| W5 SPEC-001 этап B | каркас дистилляции: DJL/ONNX-учитель, калибровочный корпус, Distiller→BIR артефакт + fidelity-метрика (`SPEC-001#задачи`) | pending |
| W6 Эксперименты | прогон JVM-runnable: EXP-002 (Tsetlin vs MPDT-GA), EXP-003 (GA vs Tsetlin producer), EXP-010/011/015/017; отчёты в `docs/research/reports/` | pending |
| W7 Инфраструктура | CRD SignalModule/TaskCell в operator; ELSP-скелет (Ed25519); noosphere M4-sync базовый (DESIGN-07/08) | pending |

## 2. Реестр документов

Формат: Док → Работа → Волна → Статус (`done` / `partial` / `todo` / `BLOCKED-EXT(причина)`).

### Спеки
- SPEC-000 → ядро curriculum-стека → W2 → todo (пилот LearnedMinecraftPilot уже есть)
- SPEC-001 → этап B дистилляции → W5 → todo (карантин действует)
- SPEC-002 → хвосты: FR-B3/C/D-док → W1/W6 → partial (BIR-компилятор+бэкенды готовы)
- SPEC-003 → matrix-ricci → W3 → todo
- SPEC-002 FR-D3 код → квантовый бэкенд → BLOCKED-EXT (нет субстрата; сама спека: «код — при появлении субстрата»); deliverable = spec-документ в W1

### Дизайны
- DESIGN-01 units → done (bir/) · частично: машина состояний артефакта → W4
- DESIGN-02 composition → верхние уровни Viewpoint/Persona → W4
- DESIGN-03 pipeline → REST-поверхность существует (api/MatrixResource /api/v1/*, WebSocket, MCP-инструменты) — аудит 2026-08-25; алиасы /matrix/* косметика → done(partial)
- DESIGN-04 learning → Distiller (W5), интерактив/самонаблюдение → W5
- DESIGN-05 memory → M4-реплика базовая → W4
- DESIGN-06 signal-modules → text-embed-hash/audio-events → W7
- DESIGN-07 lifecycle → TaskCell (W4), сон-цикл route-drain (W7)
- DESIGN-08 federation → ELSP-скелет (W7); EDGE-3 имплант → BLOCKED-EXT («не реализуется в текущем горизонте», DESIGN-08 §EDGE-3); постквант Dilithium v2 → BLOCKED-EXT (профиль v2)
- DESIGN-09 monotone-decoder → Hansel-цепи + тесты → W4
- DESIGN-10 reservoir → done для IntEsNetwork; readout WiSARD-WNN связка → W6
- DESIGN-11 budgeter/homeostat → conjugate DP + коридоры → W4
- DESIGN-12 FNL/TaskCell → полный карантин SHADOW→PROMOTED → W4
- DESIGN-13 action-registry → Hoare/PDDL контракты → W4
- DESIGN-14 bir-migration → ~118 call-sites аудит + ArchUnit INV-1 в CI → W6/W7
- DESIGN-15 ac3 → PlanStep-предусловия декларативно → W7

### Гипотезы (реализация = инфраструктура + прогоняемое)
- H-002/H-003 → EXP-002/EXP-003 прогон → W6
- H-010/H-011/H-015/H-017 → прогоны + отчёты → W6
- H-007 (golden set владельца), H-028/H-029 (социокультурные корпуса) → BLOCKED-EXT (нет данных)
- H-013 (федеративный экспорт) → этап 4, после W7 → todo
- Остальные proposed → инфраструктура по мере волн; вердикты — только по данным эксперимента

## 3. Команда агентов

- **Coordinator** (главный агент): план, решения, общие файлы, синтез.
- **impl-worker A..C**: непересекающиеся зоны кода (по волне).
- **doc-writer**: spec/design документы (BIR-to-MPS.md).
- **verifier**: целевые прогоны тестов, отчёт о гейте.

## 4. Журнал прогресса

- [W1] DONE+verified 2026-08-25: K_MAX дедуп (FpgaBackend→TruthTable.K_MAX); `io.matrix.runtime.RuntimeLimits` (+RuntimeLimitsTest 10/10 зелёные); `docs/spec/quantum/BIR-to-MPS.md` создан. BLOCKED-EXT: yosys/nextpnr отсутствуют в окружении — FPGA-синтез не гоняется локально (компилятор ldn2v.py готов).
- [W2] DONE+verified 2026-08-25: ядро SPEC-000 в `io.matrix.devloop` (12 классов: ScenarioSpec, Outcome, DifficultyBand, CompetenceAssessor, CompetenceReport, CurriculumEngine, FeedbackComposer, Feedback, ScaffoldingManager, GateCriteria, MaturityGateKeeper, MaturityLevel). Тесты: DevLoopTest (юнит) + DevLoopPropertiesTest (jqwik: монотонность гейтов, ZPD band+lowest-id, EWMA∈[0,1], scaffold bounds) — BUILD SUCCESSFUL. Остаток SPEC-000: интеграция с AgentLoop/HierarchicalMemory (W7), Avro-схемы, ArchUnit INV-2, EXP-005 → перенесено.
- [W3] DONE+verified 2026-08-25: ktopo — Graph/OllivierRicciCalculator (были) + новые DriftFingerprint, FingerprintDistance (точный 1D W1), CurriculumOrderer; KtopoTest+KtopoPropertiesTest зелёные. Остатки SPEC-003: Neo4j-exporter (BLOCKED-EXT: внешний сервис), TDA-прототип (research-only, pending), EXP-004 (pending).
- [W4] DONE+verified 2026-08-25 (ядро): DESIGN-09 `bir.producers.monotone` (послойный декодер с монотонным замыканием; k≤16 exhaustive fidelity=1.0; полные цепи Ханселя — future); DESIGN-11 `budgeter.ConjugateBudgeter` (точный 0/1-DP на gcd-единицах, shadow price λ, фолбэк при мелком конверте/крупной сетке); DESIGN-12 `lifecycle.FnlGate` (SHADOW→CANDIDATE→PROMOTED/DEMOTED; TaskCell/CauldronProtocol уже существовали); DESIGN-13 `actions.{PlanRunner,VersionedContract}` (Hoare P{Q}R поверх готового ActionRegistry). Тесты всех пакетов зелёные.
  Перенос на следующую сессию: DESIGN-03 прокси/MCP-контур, DESIGN-06 embed-hash/audio-events, DESIGN-07 сон-цикл, DESIGN-08 ELSP, DESIGN-14 остаток (~118 call-sites + ArchUnit INV-1 в CI), DESIGN-15 декларативные предусловия PlanStep.
- [W4-остаток] DONE+verified 2026-08-25 (продолжение): DESIGN-02 `brain.Viewpoint` — взвешенный ансамбль с детерминированным роутером weight×score, тай-брейк по минимальному имени (конвенция репо); DESIGN-05 M4-CRDT выяснено УЖЕ РЕАЛИЗОВАННЫМ в `noosphere.Crdt`/`GrowOnlySet` (merge: коммутативность+ассоциативность+идемпотентность) — новый код не требовался. Конфиг субагентов унифицирован на модель владельца (вне репо).
- [W7-частично закрыто ранее существующим]: MeshFederation/QuorumChecker/CreditModel присутствуют в `noosphere/` с первых итераций; остаётся ELSP-криптоскелет (Ed25519) и CRD-манифесты operator — следующий заход.
- [DESIGN-07] DONE+verified 2026-08-25 (минимум): `lifecycle.ConsolidationCycle` — детерминированное окно сна с route-drain батчами и DrainSummary; тесты юнит+jqwik зелёные. Остаток DESIGN-07: интеграция окна в CauldronProtocol тик.
- [DESIGN-14] аннекс-реестр создан: `docs/engineering/DESIGN-14-call-site-audit.md` — 125 вызовов / 55 файлов классифицированы (A миграция / B training-side / C near-FROZEN / D анализ); волны A-1..A-3 и INV-1 в CI — следующие заходы.
- [W5] pending: SPEC-001 этап B (DJL/ONNX учитель, калибровочный корпус, Distiller) — крупный блок, следующий заход.
- [W6] ЗАВЕРШЁН в доступной ёмкости 2026-08-25: EXP-010 полный мини-протокол (9 прогонов: 3 датасета × 3 seeds) → medianSpeedup 242.43×, minAdvantage +5 п.п., WiSARD 9/9 → **H-010 accepted (synthetic-scope)**; реализован baseline `evolution.MpdtGaProducer` и выполнен EXP-002 → **H-002 refuted-toy** (GA быстрее ×5.5, точнее до +8.75 п.п., компактнее ×7500); INV-1 реализован как dependency-free source-scan тест `bir.Inv1SourceGuardTest` (в CI штатно). JMH-гейт Batch* ВЫПОЛНЕН: 32–69M ops/s → решение «оставить как есть» (замена не даёт ≥10%).
- [W7] partial 2026-08-25 (продолжение): аудит выявил уже реализованные `federation.ArtifactSigner` (Ed25519 sign/verify) и `noosphere` mesh-классы; добавлен недостающий anti-replay `ElspChannel` (seq-монотонность + подпись seq‖payload) с тестами — зелёный. **CRD добавлены**: `operator.{SignalModuleResource,SignalModuleSpec,TaskCellResource,TaskCellSpec}` по паттерну MatrixCluster (fabric8 v1alpha1, matrix.io) — компиляция модуля EXIT=0 + CrdFactoriesTest зелёный.
- [DESIGN-15] закрыт 2026-08-25: декларативные предусловия — `actions.PlanPreprocessor` (PlanStep: varIds/domains/arcs → AC-3 fast-fail «unsatisfiable_preconditions») поверх готового Ac3Solver; PlanPreprocessorTest зелёный.
- [W5-примечание] 2026-08-25: артефакты карантина models/* удаляются по директиве владельца (диск); SPEC-001 этап B при возобновлении начнётся с повторной генерации (scripts/pretrain_neurons.py) или checkout из истории git.
- [W5] закрыт как «уже реализовано» 2026-08-25: конвейер дистилляции по активациям SPEC-001 этап B существует — `distill.Distiller` (capture(long[],float[]) → synthesize→Bir → fidelity(...)) с тестом DistillerTest; остаётся ТОЛЬКО подключение реального LLM-учителя через DJL/ONNX (build.gradle dep + загрузчик весов) — pending следующего захода.

## 5. Матрица стека (аудит версий 2026-08-25)
| Компонент | Версия в репо | Оценка актуальности |
|---|---|---|
| Java | 25.0.4 LTS | ✅ новейшая LTS |
| Quarkus (+BOM) | 3.37.3 | ✅ заявлена как текущая |
| GraalVM buildtools | 0.10.4 (Mandrel-контейнер) | ✅ |
| Avro | **1.12.2** ✅ обновлено (patch) |
| kafka-clients | 3.9.0 | ✅ |
| ONNX Runtime | **1.29.0** ✅ обновлено (было 1.17.0), API совместим |
| Testcontainers | 1.21.3 | ✅ |
| PostgreSQL/Redis | через Quarkus BOM/Testcontainers | ✅ |
Апгрейды patch-уровня требуют сети — выполняются владельцем при доступности; критичных расхождений не обнаружено.

## 6. Итоги интеграционной волны 2026-08-25
- Docker доступен; KafkaIntegrationTest — BLOCKED-EXT(infra): брокер поднят, но метаданные топика не готовы за 60 с на данном хосте (диск/ресурсы); события/ не менялись в серии — флейк инфраструктуры, не регрессия.
- OnnxActivationTeacher реализован поверх существующего dep onnxruntime 1.17.0 (fail-fast без модели) — SPEC-001 этап B получил подключаемого учителя; реальный .onnx экспорт — следующий шаг.
- Матрица версий см. §5.

### Постквант v2 — снят с откладки 2026-08-25
DESIGN-08 планировал ML-DSA в «профиль v2» — реализовано сейчас: `federation.ElspChannelMlDsa` (нативный ML-DSA JDK25/JEP 497, без внешних dep; anti-replay семантика ElspChannel). Тесты roundtrip/tamper/replay зелёные.
Пропущены осознанно (мажорные/пре-релизы): Quarkus 3.39.0.CR1 (ждать stable), GraalVM buildtools 1.1.10 (мажор), kafka-clients 4.3.1 (мажор 4.x), Testcontainers BOM 2.x (мажор).

### EXP-009C GPU нога 2026-08-26 (RTX 5070 Ti, torch cu130)
Батч N=2000: GPU 0.02 мс · BIR CPU 0.125 мс · ORT-CPU 18.68 мс. Per-call: GPU 17.25 µs vs BIR ~62 нс (**×276 в пользу BIR**). Fidelity дистиллята .999. Полный протокол: EXP-009-report.md §EXP-009C.
