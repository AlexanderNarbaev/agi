# MASTER PLAN — MATRIX v3.1
# Полный план реализации L0–L22

**Дата:** 2026-07-16
**Версия:** v3.30
**Статус:** Phase A ✅ 5/5, Phase B ✅ 5/5, Phase C ✅ 5/5

---

## Сводка: 23 документа (L0–L22)

| Слой | Документ | Тема | Статус кода | Статус спецификации |
|------|----------|------|-------------|-------------------|
| L0 | Манифест | Аксиомы, миссия | N/A (документ) | ✅ Утверждён |
| L1 | MPDT-нейрон | TruthTable, DecisionTree | ✅ Реализован | ✅ Утверждён |
| L2 | Протоколы | Сигналы, консенсус, HADES | ✅ Реализован | ✅ Утверждён |
| L3 | Нейрокластеры | FNL, Cauldron, шардирование | ✅ Реализован | ✅ Утверждён |
| L4 | Медиаторы | Драйверы, иерархия | ✅ Реализован | ✅ Утверждён |
| L5 | Ген. алгоритм/DNA | Сжатие, Cauldron, FROZEN | ✅ Реализован | ✅ Утверждён |
| L6 | Память/EventSrc | Kafka, снапшоты, Ноосфера | ✅ Реализован | ✅ Утверждён |
| L7 | Этика | Фильтр, прокси, ЭЛЕВТЕРИЯ | ✅ Реализован | ✅ Утверждён |
| L8 | Дорожная карта | Фазы, метрики | N/A (план) | ✅ Утверждён |
| L9 | Deployment/K8s | Docker, K8s, Operator | ✅ Реализован | ✅ Утверждён |
| L10 | Monitoring/SRE | Prometheus, Jaeger, Loki | ✅ Реализован | ✅ Утверждён |
| L11 | Сообщество | Роли, консенсус, CoC | ⚠️ Частично | ✅ Утверждён |
| L12 | Юридическая | AGPLv3, GDPR, патенты | ⚠️ Частично | ✅ Утверждён |
| L13 | Пилоты | 7 пилотных проектов | ⚠️ Частично | ✅ Утверждён |
| L14 | Бизнес-модель | Open Core, кооператив | 📋 Spec only | ✅ Утверждён |
| L15 | Образование | Курсы, песочница | 📋 Spec only | ✅ Утверждён |
| L16 | Физ. интерфейсы | ESP32, FPGA, ROS2 | 📋 Spec only | ✅ Утверждён |
| L17 | Онбординг | CONTRIBUTING, шаблоны | ✅ Реализован | ✅ Утверждён |
| L18 | CI/CD | Actions, этические тесты | ✅ Реализован | ✅ Утверждён |
| L19 | Глоб. сообщество | Языки, часовые пояса | 📋 Spec only | ✅ Утверждён |
| L20 | Медиа/PR | Сайт, блог, соцсети | ⚠️ Частично | ✅ Утверждён |
| L21 | Университеты | Исследования, гранты | 📋 Spec only | ✅ Утверждён |
| L22 | Корпорации | Спонсорство, аудит | 📋 Spec only | ✅ Утверждён |

**Легенда:** ✅ = реализовано, ⚠️ = частично, 📋 = только спецификация

---

# БЛОК A: ЯДРО (L1–L8) — ПОЛНОСТЬЮ РЕАЛИЗОВАНО

## L1 — MPDT-нейрон ✅
**Статус:** TruthTable, DecisionTree, сериализация Avro — реализованы.
**Файлы:** `neuron/TruthTable.java`, `neuron/DecisionTree.java`
**Тесты:** TruthTableTest, DecisionTreeTest (59 файлов)
**Дальнейшие шаги:** Поддержка, багфиксы.

## L2 — Протоколы взаимодействия ✅
**Статус:** Signal, ConsensusEngine, HADES — реализованы.
**Файлы:** `consensus/`, `hades/`, `cluster/Signal.java`
**Тесты:** ConsensusEngineTest, WeightCalculatorTest, HadesProtocolTest, DerangementDetectorTest, EleutheriaTest
**Дальнейшие шаги:** Поддержка.

## L3 — Нейронные кластеры ✅
**Статус:** NeuronClusterActor (Pekko), ClusterTopology, FNL — реализованы.
**Файлы:** `cluster/NeuronClusterActor.java`, `simulation/ClusterTopology.java`
**Тесты:** NeuronClusterActorTest, ClusterTopologyTest, AgentClusterBrainTest
**Дальнейшие шаги:** Поддержка.

## L4 — Иерархия Медиаторов ✅
**Статус:** InstanceMediator, ClusterMediator, LobeMediator, драйверы Energy/Curiosity/Safety — реализованы.
**Файлы:** `mediator/InstanceMediator.java`, `mediator/hierarchy/`
**Тесты:** InstanceMediatorTest (13), ClusterMediatorTest, LobeMediatorTest, MediatorMessageTest, DriverStateTest, GoalTest, TaskTest, TaskSchedulerTest
**Дальнейшие шаги:** Поддержка.

## L5 — Генетический алгоритм/DNA ✅
**Статус:** EvolutionLoop, Population, Chromosome, GeneticOperators, TreeWalker, Cauldron — реализованы.
**Файлы:** `evolution/`, `cauldron/CauldronProtocol.java`
**Тесты:** EvolutionLoopTest, GeneticOperatorsTest, ChromosomeTest, PopulationTest, CauldronProtocolTest, CauldronPilotTest
**Дальнейшие шаги:** Поддержка.

## L6 — Память и Event Sourcing ✅
**Статус:** EventJournal, InMemoryEventJournal, KafkaEventJournal (Avro), R2dbcEventJournal (PostgreSQL), SnapshotStore — реализованы.
**Файлы:** `events/`, `snapshot/SnapshotStore.java`
**Исправлено:** KafkaEventJournal Avro-десериализация (SpecificDatumReader→GenericDatumReader)
**K8s:** Strimzi Kafka 4.2.0, MinIO Tenant — в манифестах
**Тесты:** InMemoryEventJournalTest, KafkaEventJournalTest, SnapshotStoreTest, NoosphereRegistryTest, KnowledgeIndexTest, CreditModelTest, FnlPackageTest, GlobalMediatorTest, NoospherePilotTest
**Дальнейшие шаги:** Поддержка.

## L7 — Этика и мультимодальность ✅
**Статус:** EthicalFilter, StructuralSafetyGuard, прокси, ЭЛЕВТЕРИЯ — реализованы.
**Файлы:** `ethics/EthicalFilter.java`, `ethics/StructuralSafetyGuard.java`, `hades/Eleutheria.java`
**Тесты:** EthicalFilterTest, StructuralSafetyGuardTest, EleutheriaTest
**Дальнейшие шаги:** Поддержка.

## L8 — Дорожная карта ✅
**Статус:** Документ-план, обновлён.
**Дальнейшие шаги:** Актуализация по мере выполнения фаз.

---

# БЛОК B: ИНФРАСТРУКТУРА (L9, L10, L18) — РЕАЛИЗОВАНО

## L9 — Развёртывание и K8s ✅
**Реализовано:** 20 K8s-манифестов, Operator + CRD, Dockerfile multi-stage, docker-compose
**Docker:**
- `Dockerfile.dev` — multi-stage сборка (JDK 25 → JRE 25)
- `docker-compose.yml` — полный стек: PostgreSQL + Redis + Kafka + matrix-core + Minecraft
- `docker-compose.dev.yml` — только инфраструктура для локальной разработки
**K8s-манифесты:** namespace, configmap, deployment, service, hpa, vpa, pvc, rbac, servicemonitor, prometheusrule, jaeger, loki, strimzi-kafka, minio-tenant, crd, cr-example, + kustomization
**Minikube:** 9 pods Running (matrix-core, paper-server, postgres, redis, kafka, minio, prometheus, grafana, jaeger)
**NodePorts:** matrix-core :30091, grafana :30300, prometheus :30090, jaeger :31686, minio :30900, paper :32565
**v3.0 Updates:** resource limits (CPU:2, Memory:2Gi), liveness/readiness probes enhanced, pretrained weights PVC, v3.0 env vars
**Дальнейшие шаги:** Деплой в реальный кластер, GPU passthrough для ML-инференса, GraalVM Native Image.

## L10 — Мониторинг и SRE ✅
**Реализовано:** Prometheus, Jaeger, Grafana (docker-compose запущен), Loki+FluentBit (манифест), runbooks (8 процедур)
**Дальнейшие шаги:** Chaos Engineering (Chaos Mesh), Thanos/VictoriaMetrics, Alertmanager→Slack.

## L18 — CI/CD и тестирование ✅
**Реализовано:** GitHub Actions workflow, JaCoCo (82%), 970+ тестов, 127 test files
**Дальнейшие шаги:** Интеграция с K8s (деплой в staging), автоматические релизы.

---

# БЛОК B2: PRETRAINED MODELS & OPENAI API — РЕАЛИЗОВАНО

## Pretrained Models Integration ✅
**Реализовано:** Конвертация весов трансформеров в Avro-таблицы истинности
**Модели:** SmolLM2-135M (180 нейронов, 6 слоёв, k=12), Qwen2.5-0.5B (720 нейронов, 24 слоя, k=16)
**Пайплайн:** `scripts/pretrain_neurons.py` (safetensors → Avro), `scripts/pretrain_large.py` (>10GB модели)
**K8s:** Pretrained weights в PersistentVolumeClaim (10Gi), ReadOnlyMany access
**Дальнейшие шаги:** Интеграция Qwen3-1.7B, DeepSeek-R1-Distill-Qwen-1.5B (см. MODEL_RECOMMENDATIONS.md)

## OpenAI-Compatible API ✅
**Реализовано:** REST API совместимый с OpenAI форматом
**Эндпоинты:** `/v1/chat/completions`, `/v1/models`, `/v1/embeddings`
**Модели:** `M.A.T.R.I.X.` (unified neural system)
**Streaming:** Поддержка `stream: true` (SSE)
**Дальнейшие шаги:** Rate limiting, API keys, больше моделей.

## Minecraft Docker Integration ✅
**Реализовано:** Paper сервер в Docker (itzg/minecraft-server:java21)
**Docker-compose:** matrix-core → paper-server dependency chain
**WebSocket:** Auto-reconnect к matrix-core
**Дальнейшие шаги:** GPU passthrough для ML-инференса.

---

# БЛОК B3: v3.0 COMPONENTS — РЕАЛИЗОВАНО

## Phase 1: Boolean Reasoning Chain (BRC) ✅
**Статус:** Multi-step boolean reasoning с convergence detection
**Файлы:** `reasoning/BrcChain.java`, `reasoning/BrcStep.java`, `reasoning/BrcState.java`
**Тесты:** BrcChainTest
**K8s:** `BRC_MAX_STEPS=5`, `BRC_CONVERGENCE_THRESHOLD=2`

## Phase 2: Boolean RAG ✅
**Статус:** Knowledge retrieval с Top-K expansion
**Файлы:** `rag/BooleanRag.java`, `rag/BooleanIndex.java`, `rag/QueryExpander.java`
**Тесты:** BooleanRagTest
**K8s:** `RAG_TOP_K=5`

## Phase 3: VQ-VAE Proxy ✅
**Статус:** Sensor/effector encoding через codebook
**Файлы:** `vqvae/VqVaeProxy.java`, `vqvae/CodeBook.java`
**Тесты:** VqVaeProxyTest
**K8s:** `VQVAE_CODEBOOK_SIZE=256`

## Phase 4: MCTS-Guided Evolution ✅
**Статус:** Monte Carlo tree search для directed mutation
**Файлы:** `mcts/MctsTree.java`, `mcts/MctsNode.java`, `mcts/MctsAction.java`
**Тесты:** MctsTreeTest, MctsNodeTest
**K8s:** `MCTS_ITERATIONS=100`

## Phase 5: Agent Loop ✅
**Статус:** Observe→Think→Act цикл с convergence detection
**Файлы:** `agent/AgentLoop.java`, `agent/AgentBrainService.java`, `agent/AgentState.java`, `agent/AgentAction.java`
**Тесты:** AgentLoopTest
**K8s:** `AGENT_MAX_ITERATIONS=1000`

## Phase 6: Compression ✅
**Статус:** Boolean vector compression benchmarks
**Файлы:** `benchmark/CompressionBenchmark.java` (JMH)
**Тесты:** CompressionBenchmarkTest

## Phase 7: Native Compilation 📋 Planned
**Статус:** GraalVM native-image — blocked on Quarkus 3.37
**Дальнейшие шаги:** Ждём Quarkus 3.37 с Java 25 native support

## Phase 8: Multithreading ✅
**Статус:** Virtual threads (JEP 444) интегрированы — миграция завершена.
**Файлы:** `agent/AgentLoop.java`, `concurrent/AsyncAgentLoop.java`, `concurrent/ParallelEvolution.java`, `concurrent/ThreadSafeNeuronLayer.java`, `evolution/EvolutionLoop.java`, `agent/AgentBrainService.java`, `nas/ArchitectureEvaluator.java`, `training/MatrixTrainingEngine.java`
**Изменения:**
- ForkJoinPool → Executors.newVirtualThreadPerTaskExecutor() во всех компонентах
- ReentrantReadWriteLock → StampedLock (ThreadSafeNeuronLayer)
- synchronized → ReentrantLock (AgentBrainService)
- Random → ThreadLocalRandom (AgentBrainService.act)
- EvolutionLoop.runParallel() — параллельная оценка 4 популяций на virtual threads
**Тесты:** Все concurrent/evolution/agent/NAS/RAG/MCTS/VQ-VAE/compression тесты проходят.
**Дальнейшие шаги:** StructuredTaskScope при выходе из preview в Java.

## Phase 9: Integration Testing ✅
**Статус:** Интеграционные тесты v3.0 компонентов расширены.
**Новые файлы:** `integration/BrcMctsIntegrationTest.java` (5 тестов), `integration/RagAgentLoopIntegrationTest.java` (5 тестов)
**Покрытие:** 14 integration/E2E файлов, ~82 тестовых метода.
**Дальнейшие шаги:** ConcurrentAgentLoopTest, multi-instance кластерные тесты.

## Phase 10: Deployment & Documentation ✅
**Статус:** K8s manifests обновлены, документация v3.0 создана, Docker Compose создан
**Обновлено:**
- `Dockerfile.dev` — multi-stage сборка
- `docker-compose.yml` — полный стек
- `docker-compose.dev.yml` — инфраструктура только
- `infra/k8s/minikube/matrix-core.yaml` — v3.0 env vars, resource limits, probes, pretrained PVC
- `scripts/matrix-minikube.sh` — v3.0 config, pretrained weights mount
- `README.md` — v3.1 architecture, components, API docs, config reference
- `docs/INDEX.md` — v3.0 documents added
- `docs/MASTER_PLAN.md` — Phases 1–15 complete
- `docs/API.md` — полная документация API
- `docs/DEPLOYMENT.md` — гайд по деплою
- `docs/V3_CONFIGURATION.md` — full configuration reference
- `wal/GLOBAL_WAL.md` — v3.1 status
- `wal/SESSION_WAL.md` — v3.1 status

---

# БЛОК B4: v3.1 RESEARCH SYNTHESIS & NEW COMPONENTS — РЕАЛИЗОВАНО

## Phase 11: Deep Research Synthesis ✅
**Статус:** Comprehensive research analysis completed
**Исследования:**
- 20+ Habr articles analyzed (RAG, knowledge graphs, agent architectures, LLM optimization)
- 17,835 SINV forum ideas reviewed (2,781 AI/ML-related)
- Full codebase audit (168 Java files, 41 packages)
**Документы:**
- `docs/research/RESEARCH_SYNTHESIS_2026_Q3.md` — полный исследовательский отчёт
- `docs/research/SINV_ANALYSIS_REPORT.md` — анализ форума СИНВ
- `docs/research/AI_AGENT_SYSTEMS_RESEARCH_2026_Q3.md` — исследование AI агентов
- `docs/research/2026-07-10_AI_ML_Architectures_vs_MPDT.md` — сравнение архитектур
**Ключевые находки:**
- Genome-based evolution for agent parameters
- Hybrid RAG with RRF fusion and knee-point pruning
- Structural safety (process-based guardrails)
- Hierarchical memory with drift detection
- KAG-style reasoning loop (Planner→Reasoner→Judge→Generator)

## Phase 12: AgentGenome ✅
**Статус:** Genome-based evolution for agent configuration
**Файлы:** `agent/AgentGenome.java`
**Тесты:** AgentGenomeTest
**Возможности:**
- Structured genome: prompt patches, stage order, tool sets, memory config, RAG config, safety constraints
- Lifecycle: candidate → evaluated → pending_approval → active
- Multi-objective fitness: quality, robustness, latency, complexity
- Mutation: prompt patches, stage reordering

## Phase 13: Hybrid Boolean RAG ✅
**Статус:** Enhanced retrieval with RRF fusion and adaptive context
**Файлы:** `rag/HybridBooleanRag.java`, `rag/RrfFusion.java`
**Тесты:** RrfFusionTest
**Возможности:**
- Hybrid search: dense (boolean vector) + sparse (keyword) retrieval
- RRF fusion: merges results without weight tuning
- Adaptive context: knee-point pruning replaces static top-K
- Two-level filtering: strong vs borderline matches
- Exact-term guard: refuses to answer if no strong matches

## Phase 14: Structural Safety Guard ✅
**Статус:** Process-based safety enforcement
**Файлы:** `ethics/StructuralSafetyGuard.java`
**Тесты:** StructuralSafetyGuardTest
**Возможности:**
- Pattern 1: Remove tools (structurally unavailable)
- Pattern 2: Gate operations (require human approval)
- Pattern 3: Risk-based autonomy dialing
- Risk table: LOW → MEDIUM → HIGH → CRITICAL

## Phase 15: Hierarchical Memory ✅
**Статус:** Multi-layer memory model with drift detection
**Файлы:** `memory/HierarchicalMemory.java`, `memory/MemoryHierarchy.java`
**Тесты:** HierarchicalMemoryTest
**Возможности:**
- 5-level hierarchy: L0 Artifacts → L1 Patterns → L2 Modules → L3 Quanta → L4 Kernels
- Drift detection: warns when content diverges from domain
- Auto-promotion: entries gain levels through references
- Importance-based retention: low-importance entries decay

---

# БЛОК C: ПИЛОТНЫЕ ПРОЕКТЫ (L13) — В РАБОТЕ

## Пилот №1: Minecraft/GridWorld-агент ✅ Завершён

### Задача 1.3.1: Запуск GridWorld-симуляции с визуализацией ✅ (2d)
- `GridWorldPilotCommand` — Quarkus Picocli subcommand
- 200 поколений, 30 популяция, k=18, seed=42
- **Результат:** Fitness 746 → 940 (+194), 13с, 15.3 gen/s

### Задача 1.3.2: Обучение агента до целевых метрик ✅ (2d)
- Survival estimate: 100%
- Demo: 5 food, score 360, 144 steps
- **Критерий:** выживаемость > 80% ✅

### Задача 1.3.3: Интерпретируемость лучшего агента ✅ (1d)
- Экспорт дерева решений: north(256 leaves/13 depth), south(127/12), west(239/12), east(258/12)
- JSON-экспорт всех путей принятия решений
- **Критерий:** человек может понять логику агента ✅

### Задача 1.3.4: Визуализация ✅ (1d)
- `scripts/gridworld_visualize.py`: график фитнеса + сложности деревьев
- `output/gridworld_evolution.png`, `output/gridworld_trees.png`

## Пилот №2: Проактивный чат-бот ✅ Завершён
**Статус:** Telegram-бот с интеграцией, проактивность, этика, интерпретируемость
**Файлы:** `dialog/TelegramBotService.java`, `dialog/ChatBot.java`, `dialog/ProactiveInterface.java`

### Задача 1.4.1: Telegram-бот (MVP) ✅ (2d)
- Полная интеграция с Telegram Bot API (polling)
- Конфигурация через `TELEGRAM_BOT_TOKEN`
- **Критерий:** бот отвечает на сообщения ✅

### Задача 1.4.2: Проактивность ✅ (1d)
- D_curiosity/D_social драйверы через ProactiveInterface
- Авто-инициация: curiosity findings, anomalies, milestones, idle timer
- Адаптивность: отключение после 3 игнорирований
- **Критерий:** бот пишет первым ✅

### Задача 1.4.3: Этический фильтр в действии ✅ (2d)
- EthicalFilter проверяет ВСЕ сообщения
- Блокировка: kill, torture, enslave, autonomous weapons
- Объяснение причины блокировки
- **Критерий:** 100% блокировка вредоносных запросов ✅

### Задача 1.4.4: Интерпретируемость (/why) ✅ (1d)
- Команда `/why` показывает цепочку reasoning
- Объяснение: Input parsing → Decision Tree → Ethical Filter → Response
- **Критерий:** пользователь видит читаемую причину ответа ✅

### Задача 1.4.5: Обучение новым фактам (/learn) ✅ (1d)
- Команда `/learn <fact>` — запись через MPDT-мутации
- Объяснение механизма: создание/мутация нейронов
- **Критерий:** бот запоминает факты ✅

## Пилот №3: Умный дом (ESP32) 📋 Spec only
**Что нужно:** `matrix-micro` C-библиотека, ESP32-прошивка, Home Assistant интеграция

### Задача 3.1.1: Библиотека matrix-micro для ESP32 ✅ (3d)
- C-реализация MPDT-нейрона (TruthTable во flash)
- Пример: XOR-нейрон на ESP32
- **Критерий:** нейрон выполняет evaluate() за < 100 нс

### Задача 3.1.2: Прошивка умного выключателя ⬜ (2d)
- Обучение на сервере (Java), прошивка в ESP32
- Адаптация к паттернам использования
- **Критерий:** выключатель адаптируется за 3 дня без облака

## Пилот №4: Робо-рука 📋 Spec only

### Задача 3.2.1: PyBullet-симуляция ✅ (3d)
- Симуляция робо-руки в PyBullet
- MPDT-контроллер (Java→Python bridge)
- **Критерий:** рука захватывает объект через 100 поколений

### Задача 3.2.2: ROS2-шлюз ✅ (5d)
- `matrix-ros2-bridge`: Java/Python шлюз
- Публикация/подписка на ROS2-топики
- **Критерий:** нейрон управляет реальной рукой через ROS2

## Пилоты №5–7: Cauldron, HADES, Ноосфера ⚠️ Частично
**Что есть:** Тесты: CauldronPilotTest, HadesPilotTest, NoospherePilotTest (14 тестов)
**Что нужно доделать:**

### Задача 3.3.1: Автономное рождение FNL (Cauldron) ✅ (3d)
- Демонстрационный сценарий: система сама создаёт новый FNL
- Визуализация процесса сжатия
- **Критерий:** новый FNL рождается без ручного вмешательства

### Задача 3.4.1: Восстановление после повреждения (HADES) ✅ (2d)
- Демонстрация: убить 30% нейронов → система восстанавливается
- Визуализация процесса
- **Критерий:** восстановление < 1 минуты, точность возвращается к исходной

### Задача 3.5.1: Обмен FNL между инстансами (Ноосфера) ✅ (3d)
- Запустить 2+ инстанса, обмен FNL через Kafka/Noosphere
- Демонстрация: инстанс A обучился → инстанс B получил знания
- **Критерий:** FNL успешно передаётся и применяется

---

# БЛОК D: СООБЩЕСТВО (L11, L17, L19) — ЧАСТИЧНО

## L11 — Сообщество и управление ⚠️
**Сделано:** Code of Conduct, CONTRIBUTING, SECURITY, GitHub-шаблоны
**Осталось:**

### Задача 5.1: GitHub Discussions ⬜ (0.5d)
- Настроить категории: Ideas, Q&A, RFC, Showcase
- Активировать на github.com/AlexanderNarbaev/agi

### Задача 5.2: Чат сообщества ⬜ (1d)
- Discord-сервер с базовыми каналами
- Matrix-комната (#matrix-ai:matrix.org)
- Модерация и правила

### Задача 5.3: Ротация встреч ⬜ (—)
- Регулярные созвоны с учётом часовых поясов (L19 §4.2)
- Асинхронные RFC-обсуждения

### Задача 5.4: Open Collective / GitHub Sponsors ⬜ (1d)
- Регистрация для приёма пожертвований
- Прозрачный отчёт о расходах

## L17 — Онбординг ✅
**Сделано:** CONTRIBUTING.md, GitHub Issue/PR templates, .github/
**Дальнейшие шаги:** Поддержка, улучшение по обратной связи.

## L19 — Глобальное сообщество 📋
**Что нужно:**

### Задача 19.1: Перевод документации ✅ (5d)
- Weblate-интеграция
- Приоритет: README, CONTRIBUTING, L0 (Манифест)
- Языки: RU, EN, ZH, ES, AR

### Задача 19.2: Мультиязычный интерфейс ✅ (3d)
- i18n в CLI-командах
- Локализация сообщений об ошибках

---

# БЛОК E: ЮРИДИЧЕСКАЯ СТРАТЕГИЯ (L12) — ЧАСТИЧНО

## L12 — Юридическая стратегия ⚠️
**Сделано:** LICENSE (AGPLv3 + этические ограничения)
**Осталось:**

### Задача 12.1: GDPR-политика ⬜ (2d)
- PRIVACY.md: какие данные собираются, как хранятся
- Процедура удаления данных пользователя
- Data Processing Agreement (DPA)

### Задача 12.2: Патентная стратегия ✅ (3d)
- Поиск prior art для MPDT-нейрона
- Решение: патентовать или публиковать как defensive disclosure
- PATENTS.md

### Задача 12.3: Contributor License Agreement (CLA) ⬜ (1d)
- Шаблон CLA для контрибьюторов
- Автоматическая проверка через GitHub bot

---

# БЛОК F: БИЗНЕС-МОДЕЛЬ (L14) — SPEC ONLY

### Задача 14.1: Managed Matrix (MVP) ⬜ (10d)
- Хостинг инстансов для организаций
- Веб-интерфейс управления
- Биллинг (Stripe/Paddle)

### Задача 14.2: Сертификация «Спираль-совместимости» ✅ (5d)
- Процесс проверки FNL на этичность
- Реестр сертифицированных нейронов
- Знак «Спираль-совместимости»

### Задача 14.3: Грантовые заявки ⬜ (R)
- NLNet, EU Horizon, Mozilla Open Source
- Подготовка заявок с научным обоснованием

---

# БЛОК G: ОБРАЗОВАНИЕ (L15) — SPEC ONLY

### Задача 15.1: Web Playground (WASM-песочница) ✅ (5d)
- Интерактивная визуализация MPDT-нейрона в браузере
- Пользователь задаёт входы — видит выходы и дерево решений
- Базовая версия: `docs/sandbox.html` уже существует — расширить

### Задача 15.2: Видео «Что такое МАТРИЦА?» (5 мин) ⬜ (2d)
- Объяснение концепции MPDT-нейрона
- Демонстрация GridWorld-агента
- Публикация на YouTube

### Задача 15.3: Jupyter-ноутбуки ⬜ (2d)
- Ноутбук 1: XOR-нейрон (уже частично)
- Ноутбук 2: GridWorld-агент (уже есть)
- Ноутбук 3: Этический фильтр в действии
- Ноутбук 4: HADES — самовосстановление

### Задача 15.4: Базовый видео-курс (7 модулей) ⬜ (10d)
- Модуль 1: Что такое MPDT-нейрон
- Модуль 2: Генетический алгоритм без градиентов
- Модуль 3: Медиаторы и драйверы
- Модуль 4: Память и события
- Модуль 5: Этика и безопасность
- Модуль 6: Пилотный проект (GridWorld)
- Модуль 7: Контрибьюция в проект

---

# БЛОК H: ФИЗИЧЕСКИЕ ИНТЕРФЕЙСЫ (L16) — SPEC ONLY

### Задача 16.1: matrix-micro (C-библиотека для ESP32) ✅ (3d)
- Реализация TruthTable на C (flash-память)
- evaluate() через битовые операции
- Сериализация .ldn → C-header
- Пример: XOR-нейрон

### Задача 16.2: matrix-ros2-bridge ✅ (5d)
- Java/Python-шлюз для ROS2
- Публикация сенсоров → MPDT-нейрон
- Действия нейрона → ROS2-команды

### Задача 16.3: matrix-fpga-compiler ✅ (5d)
- Компилятор `.ldn` → Verilog/VHDL
- Отображение TruthTable → LUT (Look-Up Table)
- Прототип на TinyFPGA BX / Lattice iCE40

### Задача 16.4: Исследование нейроморфных чипов ⬜ (R)
- Анализ применимости Loihi 2, TrueNorth
- Сравнение энергоэффективности: GPU vs MPDT vs Neuromorphic
- Публикация отчёта

---

# БЛОК I: МЕДИА И PR (L20) — ЧАСТИЧНО

## L20 — Медиа-стратегия ⚠️
**Сделано:** GitHub Pages landing page (`docs/index.html`)
**Осталось:**

### Задача 20.1: Логотип и брендбук ✅ (3d)
- Логотип МАТРИЦЫ (спираль, сетка, нейрон)
- Цветовая схема, типографика
- Использование в соцсетях, на сайте, в видео

### Задача 20.2: Блог ⬜ (1d/пост)
- Первый пост: «Что такое MPDT-нейрон и почему он не галлюцинирует»
- Второй пост: «GridWorld: как агент учится выживать без градиентов»
- Третий пост: «Этический фильтр: 100% блокировка вредоносных запросов»

### Задача 20.3: Социальные сети ⬜ (1d)
- Mastodon (fosstodon.org/@matrix-ai)
- Twitter/X (@matrix_ai)
- LinkedIn (страница проекта)

### Задача 20.4: YouTube-канал ⬜ (1d)
- Видео демо GridWorld
- Видео «MPDT за 5 минут»

### Задача 20.5: Пресс-кит ⬜ (1d)
- Логотип, скриншоты, key messages
- Контакты для прессы
- `/press` страница на сайте

---

# БЛОК J: УНИВЕРСИТЕТЫ (L21) — SPEC ONLY

### Задача 21.1: Список открытых проблем ⬜ (1d)
- OPEN_PROBLEMS.md с 10+ темами для диссертаций
- Сходимость генетического алгоритма для MPDT
- Выразительность сетей MPDT (сравнение с нейросетями)
- Формальная верификация Этического фильтра

### Задача 21.2: Препринт «MPDT: Formal Model» ⬜ (10d)
- Научная публикация на ArXiv
- Соавторство открыто для академических партнёров

### Задача 21.3: Реестр партнёрств ⬜ (0.5d)
- PARTNERSHIPS.md
- Список университетов-партнёров, совместных проектов

### Задача 21.4: Пилотный университетский курс ⬜ (20d)
- Один семестр в одном университете
- Обратная связь → улучшение материалов

---

# БЛОК K: КОРПОРАЦИИ (L22) — SPEC ONLY

### Задача 22.1: Форма заявки для компаний ⬜ (1d)
- Веб-форма на сайте
- Типы участия: спонсор, партнёр, пользователь

### Задача 22.2: Этическая анкета ⬜ (1d)
- Опросник для аудита компаний
- Критерии соответствия Трём запретам
- Процедура рассмотрения

### Задача 22.3: Реестр корпоративных участников ⬜ (0.5d)
- CORPORATE_PARTICIPANTS.md
- Прозрачный список компаний и их вклада

---

# БЛОК L: НОВЫЕ ИССЛЕДОВАТЕЛЬСКИЕ ЗАДАЧИ

### Задача L.1: Knowledge Graph Integration ✅ (5d)
- Интеграция Knowledge Graph с Boolean RAG
- Structured knowledge storage и retrieval
- Graph-based reasoning для сложных запросов

### Задача L.2: Multi-Agent Collaboration ✅ (5d)
- Протокол обмена знаниями между агентами
- Collaborative learning через Noosphere
- Conflict resolution при противоречивых знаниях

### Задача L.3: Continuous Learning Pipeline ✅ (5d)
- Online learning с feedback loop
- Automatic retraining при drift detection
- A/B testing для разных конфигураций агента

### Задача L.4: Explainability Dashboard ✅ (3d)
- Веб-интерфейс для визуализации reasoning chains
- Interactive decision tree explorer
- Real-time neuron activity monitoring

### Задача L.5: Performance Optimization ✅ (3d)
- Boolean vector SIMD optimization
- Parallel evolution with virtual threads
- Cache optimization для frequently accessed neurons

---

# ДОРОЖНАЯ КАРТА ПО КВАРТАЛАМ

```
2026 Q2 (июнь) — ЗАВЕРШЁН:
├── ✅ Пилот №1: GridWorld — 200 gens, fitness 746→940, визуализация
├── ✅ Пилот №2: Чат-бот — Telegram, проактивность, этика, /why, /learn
├── ✅ Пилот №3: Умный дом (ESP32) — matrix-micro C library
├── ✅ Логотип + брендбук
├── ✅ Блог: первый пост (MPDT-нейрон)
├── ✅ CLA + PRIVACY + OPEN_PROBLEMS (14 тем)
├── ✅ Weblate-переводы + ArXiv preprint + Discord
├── ✅ Community infrastructure + партнёрства
└── ✅ v1.2.0–v1.3.0: 524→545 тестов

2026 Q2–Q3 (июнь–июль) — ЗАВЕРШЁН:
├── ✅ Пилот №4: Робо-рука (PyBullet + ROS2) + Java bridge + CLI
├── ✅ Пилоты №5–7: Cauldron, HADES, Noosphere — CLI демо
├── ✅ FPGA-компилятор: ldn2v .ldn→Verilog
├── ✅ CI расширен: 10 jobs, matrix-micro C тесты
├── ✅ JMH benchmarks + GraalVM native-image конфигурация
├── ✅ Jupyter-ноутбуки: ethical filter, HADES recovery, Noosphere
├── ✅ Видео-курс: 7 modules outline
├── ✅ Spigot Plugin тесты
├── ✅ ROS2 bridge (sim+real), K8s deploy script
├── ✅ Dockerfile Mandrel-native, Quarkus REST API 8 endpoints
├── ✅ E2E+Chaos+Integration тесты, OpenAPI спецификация
├── ✅ ESP32 firmware, K8s 17 manifests
└── ✅ v2.0.0 FINAL: 570 тестов, 84% coverage, CI 10 jobs, 23 docs

2026 Q3 (июль) — ЗАВЕРШЁН:
├── ✅ Phase 1: BRC (Boolean Reasoning Chain) — multi-step reasoning
├── ✅ Phase 2: Boolean RAG — knowledge retrieval с Top-K
├── ✅ Phase 3: VQ-VAE Proxy — sensor/effector encoding
├── ✅ Phase 4: MCTS-Guided Evolution — directed mutation
├── ✅ Phase 5: Agent Loop — Observe→Think→Act cycle
├── ✅ Phase 6: Compression — JMH benchmarks
├── ✅ Phase 10: Deployment & Documentation — K8s v3.0 + docs
└── ✅ v3.0: 920 тестов, 82% coverage, K8s v3.0 manifests

2026 Q3+ (июль) — ЗАВЕРШЁН:
├── ✅ Phase 11: Deep Research Synthesis — 20+ Habr articles, 17,835 SINV ideas
├── ✅ Phase 12: AgentGenome — genome-based evolution, multi-objective fitness
├── ✅ Phase 13: Hybrid Boolean RAG — RRF fusion, knee-pruning, two-level filtering
├── ✅ Phase 14: Structural Safety Guard — process-based guardrails, risk table
├── ✅ Phase 15: Hierarchical Memory — 5-level hierarchy, drift detection
├── ✅ Docker Compose — full stack (PostgreSQL + Redis + Kafka + matrix-core + Minecraft)
└── ✅ v3.1: 970+ тестов, research synthesis report, 5 new components, Docker

2026 Q3+ (в работе):
├── ✅ Phase 8: Virtual Threads — ForkJoinPool→VT, StampedLock, 8 files
├── ✅ Phase 9: Integration Testing — 16 files, ~100 integration tests
├── ✅ Phase B: Research-Driven Components — 21 articles, 3 new components
│   ├── ✅ ExactTermGuard — exact-term verification for RAG (18 tests)
│   ├── ✅ AgentResponse — observable timing+source response (23 tests)
│   ├── ✅ ParetoFitness — multi-objective evolution (20 tests)
│   ├── ✅ GuardedHybridRag — ExactTermGuard integration (9 tests)
│   ├── ✅ AgentTrajectoryRecorder — Record/Replay trajectories (36 tests)
│   ├── ✅ AgentLoop.runWithTiming() — observable agent execution
│   └── ✅ PipelineIntegrationTest — full pipeline E2E (5 tests)
├── ✅ L.1: Knowledge Graph — KnowledgeGraphStore integrated into RAG (22+4 tests)
├── ✅ L.2: Multi-Agent Consensus — MultiAgentLoop (16 tests)
├── ✅ L.3: Continuous Learning — ContinuousLearningLoop (14 tests)
├── ✅ L.4: Boolean Explainability — SHAP-style feature importance (8 tests)
├── ✅ L.5: BooleanCompressor SIMD — Vector API pack/unpack (19 tests)
├── ✅ Phase C: Meta-harness — MetaHarnessOptimizer (18) + MatrixLifecycleManager (18) + MCP server (26)
├── ✅ Managed Matrix MVP — MatrixLifecycleManager: init→train→deploy→monitor→retrain
├── ⏸️ Phase 7: GraalVM 25 native — configs готовы (1173 строк reflect-config), блокирован на Quarkus 3.37
├── 🔲 Сертификация «Спираль-совместимости»
```

---

## КЛЮЧЕВЫЕ МЕТРИКИ (v3.16)

| Метрика | v2.5.0 | v3.0 | v3.1 | v3.16 | Цель |
|---------|--------|------|------|-------|------|
| Тесты | 920 | 920 | 970+ | 2315+ | ✓ Достигнуто |
| Test files | — | — | 127 | 194 | ✓ |
| Prod files | — | — | 168 | 225 | ✓ |
| Покрытие JaCoCo | 82% | 82% | 82% | 82% | ≥ 82% ✓ |
| Компоненты | — | 5 | 10 | 33 | ✓ |
| L.1-L.5 | — | — | — | ✅ Все 5 | ✓ |
| Phase A+B+C | — | — | — | ✅ A+B+C, 15/15 | ✓ |
| BRC max steps | — | 5 | 5 | ✓ |
| RAG top-K | — | 5 | 5 (adaptive) | ✓ |
| VQ-VAE codebook | — | 256 | 256 | ✓ |
| MCTS iterations | — | 100 | 100 | ✓ |
| Agent max iterations | — | 1000 | 1000 | ✓ |
| Docker services | — | — | 5 | ✓ |
| K8s resource limits | — | CPU:2, Mem:2Gi | CPU:2, Mem:2Gi | ✓ |
| K8s probes | — | liveness + readiness | liveness + readiness | ✓ |
| Pretrained weights | Docker image | PVC (10Gi) | PVC (10Gi) | ✓ |
| Этических нарушений | 0 | 0 | 0 | ✓ |
| Исследовано статей | — | — | 20+ | ✓ |
| SINV идей проанализировано | — | — | 17,835 | ✓ |
| Новых компонентов | — | — | 5 | ✓ |
| Research docs | — | — | 4 | ✓ |

---

## ПРОТОКОЛ ОБНОВЛЕНИЯ

- **Каждая сессия:** обновление SESSION_WAL.md
- **При завершении фазы:** обновление GLOBAL_WAL.md, этого плана
- **Приоритеты:** корректируются голосованием в GitHub Discussions
- **Критические решения:** через RFC (L11 §4.2)

---

*Конец MASTER_PLAN.md — v3.1, 2026-07-13 — Phase 15 Hierarchical Memory complete, Docker Compose added*
