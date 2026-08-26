# SDD Coverage Map — код ↔ спецификации

**Статус: living** · Обновлено 2026-08-26 · Принцип SpecDrivenDevelopment: каждому значимому классу соответствует раздел спеки/дизайна; каждой спеке — реализация или честный BLOCKED-EXT в [PLAN](PLAN-FULL-IMPLEMENTATION.md).

## Методика полной инвентаризации

Полный sweep выполнен 2026-08-26: `ls matrix-core/src/main/java/io/matrix/` → **69 пакетов** (450 `.java`) + **5 корневых демо-файлов** (MatrixApplication, MatrixSimulation, MatrixTopCommand, MinecraftExperiment, SystemDemo), всего 455 файлов. Каждая связь проверена по источникам: ссылки `SPEC-*/DESIGN-*/EXP-*` в javadoc кода, шапки `formal/*.tla`, карточки `docs/research/HYPOTHESES.md`. Ничего не выдумано: пакеты без найденной связи помечены честно.

## Спеки → реализация

| Спека | Классы/пакеты | Тесты |
|---|---|---|
| SPEC-000 Developmental Loop | `io.matrix.devloop.*` (12 классов) | DevLoopTest, DevLoopPropertiesTest |
| SPEC-001 Этап B | `io.matrix.distill.{Distiller,OnnxActivationTeacher}` | DistillerTest, OnnxActivationTeacherTest |
| SPEC-002 BIR/продюсеры | `io.matrix.bir.*`, `io.matrix.tsetlin.*`, `bir.producers.monotone.*` | bir+ tsetlin + monotone тесты |
| SPEC-002 FR-D3 | `docs/spec/quantum/BIR-to-MPS.md` (spec-only) | — |
| SPEC-003 Топология | `io.matrix.ktopo.*` | KtopoTest, KtopoPropertiesTest |
| INV-1 (Критерий A) | `bir.Inv1SourceGuardTest` (source-scan страж) | сам тест |

## Дизайны → реализация

| Дизайн | Классы |
|---|---|
| DESIGN-02 Viewpoint | `brain.Viewpoint` |
| DESIGN-05 M4 | `noosphere.Crdt/GrowOnlySet` |
| DESIGN-06 Сигналы | `signals.{TextSignalModule,AudioSignalModule,ImageSignalModule,SignalModuleRegistry}` + прототип prototype-java |
| DESIGN-07 ЖЦ | `lifecycle.{CauldronProtocol,TaskCell,ConsolidationCycle}` + operator CRD×2 |
| DESIGN-08 ELSP | `federation.{ElspChannel(Ed25519),ElspChannelMlDsa(v2 PQ),ArtifactSigner,Anonymizer}` |
| DESIGN-09 Монотонный декодер | `monotone.MonotoneDecoder` + EXP-016 |
| DESIGN-10 Бинарный резервуар | `reservoir.{BinaryReservoir,IntEsNetwork}` + EXP-015 |
| DESIGN-11 Бюджетер | `budgeter.ConjugateBudgeter` + `cauldron.LevinSchedule` |
| DESIGN-12 TaskCell/FNL | `lifecycle.FnlGate` (+TaskCell) |
| DESIGN-13 Реестр действий | `actions.{ActionRegistry,PlanRunner,VersionedContract,PlanPreprocessor}` |
| DESIGN-14 Миграция | аннекс call-site-audit + волны A-1/A-2/A-3c |
| DESIGN-15 AC-3 | `agent.planning.Ac3Solver` + `actions.PlanPreprocessor` |

---

## 1. Mapped — пакет ↔ спека/дизайн/формальная модель/гипотеза (22 пакета, 187 классов ≈ 41%)

| Пакет | Кл. | Артефакт | Тип связи |
|---|---|---|---|
| `bir` | 26 | SPEC-002 BIR/продюсеры + DESIGN-14 миграция | SPEC+DESIGN |
| `ethics` | 21 | `formal/BotEthicsPipeline.tla`, `formal/FrozenEthicalFNL.tla`; FROZEN-слой — `ethics/frozen/` | TLA+ |
| `agent` | 21 | DESIGN-15 AC-3 (`planning.Ac3Solver` подтверждён) + AgentLoop Observe→Think→Act | DESIGN |
| `noosphere` | 18 | DESIGN-05 (`Crdt/GrowOnlySet`) | DESIGN |
| `neuron` | 18 | `formal/MPDTNeuron.tla` + SPEC-002 FR-A4 (legacy strangler-fig, `@deprecated`) | TLA+/SPEC |
| `consensus` | 13 | `formal/Consensus.tla` — шапка спеки прямо ссылается на `ConsensusEngine.java` | TLA+ |
| `devloop` | 12 | SPEC-000 Developmental Loop | SPEC |
| `verification` | 10 | EXP-017 (LTL-верификация); `LtlModelChecker` формализует инварианты DESIGN-07/08 | EXP+DESIGN |
| `ktopo` | 7 | SPEC-003 + EXP-004 (Ricci-fingerprint: `RicciFlow/DriftFingerprint/OllivierRicciCalculator`) | SPEC+EXP |
| `tsetlin` | 5 | SPEC-002 (продюсеры) + EXP-016, EXP-038 (`EblCurriculum`) | SPEC+EXP |
| `signals` | 5 | DESIGN-06 Сигналы | DESIGN |
| `lifecycle` | 5 | DESIGN-07 ЖЦ + DESIGN-12 (`FnlGate`) | DESIGN |
| `federation` | 4 | DESIGN-08 ELSP | DESIGN |
| `actions` | 4 | DESIGN-13 + DESIGN-15 (`PlanPreprocessor`) | DESIGN |
| `cauldron` | 3 | DESIGN-11 (`LevinSchedule`) + EXP-018 GUHA (`GuhaCandidateGenerator`), EXP-019 Левин | DESIGN+EXP |
| `brain` | 3 | DESIGN-02 Viewpoint (§Level-3, ссылка в коде) | DESIGN |
| `audit` | 3 | `formal/HashChain.tla` (`HashChain/HashLink/FrozenFNLHashChain`) | TLA+ |
| `guardrail` | 2 | EXP-006 (Guardrail из BIR-композиций) | EXP |
| `distill` | 2 | SPEC-001 Этап B | SPEC |
| `budgeter` | 2 | DESIGN-11 + EXP-021/EXP-022 (гомеостат коридоров) | DESIGN+EXP |
| `reservoir` | 2 | DESIGN-10 + EXP-015 (intESN) | DESIGN+EXP |
| `monotone` | 1 | SPEC-002 + DESIGN-09 + EXP-016 | SPEC+DESIGN+EXP |

## 2. Research-experimental — эксперименты прошлых сессий (10 пакетов, 69 классов ≈ 15%)

| Пакет | Кл. | Связанный док/эксперимент |
|---|---|---|
| `privacy` | 14 | Tombstone/Cascade — приватность ноосферного p2p-контура; прямой карты нет |
| `pilot` | 12 | **EXP-005 / H-005**: `MinecraftPilot` ссылается на «H-005, SPEC-000» в коде; также `PyBulletBridge`, `scada/` |
| `simulation` | 11 | клеточная среда агентов (вход через корневой `MatrixSimulation.java`); карты нет |
| `imports` | 8 | карантин конвертации весов HF/Safetensors — «замена — SPEC-001» (AGENTS.md, раздел безопасности) |
| `federated` | 7 | федеративное обучение (`SecureAggregator/PrivacyMechanism`); имя пересекается с DESIGN-08 `federation`, но контур другой; карты нет |
| `nas` | 4 | Neural Architecture Search (`LlmArchitectureOptimizer` — вне рантайма); карты нет |
| `economy` | 4 | RegenerativeEconomics/SpiralCertification; карты нет |
| `shadow` | 4 | DigitalShadow/AntiDopamine/EcoAudit/BlackBoxExplainer; карты нет |
| `civilization` | 3 | CivilizationCouncil/KnowledgeWeaving/MultilingualSupport; карты нет |
| `vqvae` | 2 | VQ-VAE codebook-эксперимент; карты нет |

## 3. Needs-spec и utility-infra (37 пакетов, 194 класса ≈ 43%)

### 3a. Needs-spec — доменная логика без спеки (15 пакетов, 92 класса ≈ 20%) — ценные находки для бэклога

| Пакет | Кл. | Что это | Приоритет бэклога |
|---|---|---|---|
| `mediator` | 14 | Драйверы Energy/Curiosity/Safety, GoldenRatioAllocator, иерархия целей | **высокий** — нервная система без дизайна |
| `rag` | 13 | Boolean/Hybrid RAG (`ExactTermGuard`, персистентный индекс) | **высокий** — значимая подсистема |
| `evolution` | 13 | ГА-циклы, `MpdtGaProducer` (смежно с продюсерами SPEC-002), ProtectedSelfRewrite | высокий — эволюция требует Φ-контур |
| `multimodal` | 9 | Кросс-модальные фичи, UnifiedRepresentation | средний |
| `chat` | 9 | Chat-driven обучение, feedback-контур | средний |
| `mcts` | 6 | MCTS/LATS-планирование | средний |
| `memory` | 6 | Иерархическая память (SDM/SQLite backend) | **высокий** — 5-уровневая память без дизайна (DESIGN-05 покрывает только noosphere-CRDT) |
| `safety` | 5 | SafetyMonitor/ConfidenceCalibrator/LieDetector | высокий — смежно с `formal/BotEthicsPipeline.tla`, границы не зафиксированы |
| `explainability` | 5 | AuditLogger, DecisionProvenance, ExplanationGenerator | средний — дублируется с `explain` (консолидировать) |
| `hades` | 4 | Обнаружение повреждений, Eleutheria, HadesProtocol | **высокий** — критичный контур без спеки |
| `reasoning` | 3 | **BrcChain (max 5 шагов)** — ядро верифицируемых решений | **максимальный** — сердце системы без спеки |
| `knowledge` | 2 | KnowledgeGraphStore/CommunityDetector — пересечение с `ktopo`/SPEC-003, консолидировать | низкий |
| `bridge` | 1 | NeuroSymbolicBridge (BIR execution path) | средний |
| `learning` | 1 | ContinuousLearningLoop | средний |
| `explain` | 1 | BooleanExplainability — кандидат на слияние с `explainability` | низкий |

### 3b. Utility-infra — утилиты и интеграции (22 пакета, 102 класса ≈ 22%)

| Пакет | Кл. | Роль |
|---|---|---|
| `api` | 21 | REST `/matrix/*`, OpenAI-совместимый `/v1/*` (исполняет через BIR — SPEC-002 Критерий A упомянут в коде) |
| `cli` | 12 | CLI (без тестов) |
| `cluster` | 11 | Pekko NeuronClusterActor (BIR-консьюмер волны DESIGN-14; топология кластера без дизайна) |
| `minecraft` | 9 | Spigot/Paper-интеграция ботов (BIR-кэш волны A-2) |
| `events` | 8 | Kafka/R2DBC event journal |
| `io` | 6 | Сенсоры Sensor/SensorBus (IoT/Minecraft/Chat); зона matrix-io по DESIGN-06 пока planned |
| `training` | 5 | Обучающий пайплайн (offline, вне рантайм-детерминизма) |
| `compression` | 5 | SIMD-оценка/компрессия TT |
| `dialog` | 3 | Telegram-бот |
| `concurrent` | 3 | AsyncAgentLoop/ParallelEvolution |
| `protocol` | 2 | CircuitBreaker, NeuronBatch |
| `proxy` | 2 | Sensor/Effector прокси |
| `redis` | 2 | Кэш нейронов |
| `snapshot` | 2 | ClusterSnapshot/SnapshotStore |
| `security` | 2 | NeuronIdentityLedger, SnapshotSigner |
| `tools` | 2 | Tool registry (MCP tools) |
| `ingest` | 2 | MultimodalIngestor |
| `weights` | 1 | WeightsConsolidator (offline-консолидация) |
| `runtime` | 1 | RuntimeLimits |
| `observability` | 1 | MatrixMetrics |
| `mcp` | 1 | MatrixMcpServer |
| `benchmark` | 1 | PerformanceReport (без тестов) |

## Выводы (полный sweep 2026-08-26)

- **Кодовая площадь**: 455 `.java` = 450 в 69 пакетах + 5 корневых демо-файлов.
- **Покрыто спеками/дизайнами/формальными моделями/гипотезами**: 187 классов ≈ **41%**.
- Research-experimental: 69 ≈ 15% (легаси прошлых сессий — кандидаты на архивацию или оформление карточками).
- Utility-infra: 102 ≈ 22% (спека по природе не критична).
- **Needs-spec: 92 класса ≈ 20%** — доменная логика без спеки. Топ бэклога: `reasoning` (BrcChain), `mediator`, `hades`, `memory`, `rag`.

## Пробелы покрытия (честно)
- `cli/`, `benchmark/` — без тестов (утилитарные).
- `explain/` vs `explainability/`, `knowledge/` vs `ktopo/` — дублирование зон, консолидировать при случае.
- `safety/` частично пересекается с `formal/BotEthicsPipeline.tla` — границы уточнить при написании спеки.
- Корневые демо-файлы (SystemDemo, MinecraftExperiment и др.) — одноразовые входные точки, маппинг не требуется.
