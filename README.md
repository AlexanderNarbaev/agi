# MATRIX (MENTAT)

Открытая когнитивная архитектура на основе MPDT-нейронов (McCulloch-Pitts Decision Tree Neurons).

Не лжёт. Не забывает. Не может быть использована во вред.

## Статус: v2.2.0

**582 тестов** | **84% покрытие** | **Java 25** | **Quarkus 3.36.1** | **Apache Pekko 1.6.0**

### Ссылки

| Ресурс | URL |
|--------|-----|
| Сайт | https://alexandernarbaev.github.io/agi/ |
| Репозиторий | https://gitverse.ru/AlexandrNarbaev/agi |
| MPDT-песочница | https://alexandernarbaev.github.io/agi/sandbox.html |
| Спецификации | [docs/](docs/) (L0–L22) |
| Гайд по Minecraft | [docs/PLAYER_GUIDE.md](docs/PLAYER_GUIDE.md) |
| Долгосрочный план | [docs/LONGTERM_PLAN.md](docs/LONGTERM_PLAN.md) |
| Лицензия | [LICENSE](LICENSE) (AGPLv3 + этические ограничения) |
| Как помочь | [CONTRIBUTING](CONTRIBUTING) |

### Observability Stack

| Слой | Технология | Эндпоинт |
|------|-----------|----------|
| Метрики | Micrometer + Prometheus | `:9091/metrics` |
| Трейсы | OpenTelemetry (OTLP) | Jaeger `:16686` |
| Логи | JSON (Quarkus) | stdout |
| Health | SmallRye Health | `:9091/q/health` |
| Дашборды | Grafana | `:3000` |

## Архитектура

```
Ядро (Core)           Нервная система (Nerve)    Ноосфера (Noosphere)
┌──────────────┐    ┌──────────────────────┐    ┌──────────────────────┐
│ TruthTable   │    │ NeuronClusterActor   │    │ NoosphereRegistry    │
│ DecisionTree │───▶│ EventJournal         │───▶│ KnowledgeIndex       │
│ EvolutionLoop│    │ InstanceMediator     │    │ CreditModel          │
│ GeneticOper. │    │ EthicalFilter        │    │ GlobalMediator       │
│ Cauldron     │    │ ConsensusEngine      │    │ DigitalShadow        │
│ HADES        │    │ TaskScheduler        │    │ CivilizationCouncil  │
│ Eleutheria   │    │ ChatBot / Proactive  │    │ RegenerativeEconomics│
└──────────────┘    └──────────────────────┘    └──────────────────────┘

Инфраструктура:
┌──────────────────────────────────────────────────────┐
│ K8s Operator (MatrixCluster CRD) + Helm chart        │
│ Docker Compose (Prometheus, Jaeger, Grafana)         │
│ CI/CD (GitHub Actions) + JaCoCo + SpotBugs           │
└──────────────────────────────────────────────────────┘
```

## Быстрый старт

```bash
# Инфраструктура мониторинга
./scripts/dev.sh up

# Сборка и тесты
./gradlew test

# Системное демо
./gradlew :matrix-core:quarkusBuild -Dquarkus.package.jar.type=uber-jar
java -jar matrix-core/build/matrix-core-*-runner.jar demo

# GridWorld симуляция
java -jar matrix-core/build/matrix-core-*-runner.jar simulate -g 100 -p 20 -k 16 --seed 42

# Docker
docker build -t ghcr.io/matrix-ai/matrix-core:latest .
```

## CLI команды

| Команда | Описание |
|---------|----------|
| `demo` | Полное системное демо (все 8 фаз) |
| `simulate` | GridWorld: эволюция агента |
| `evolution` | Minecraft: survival-эксперимент |

## Метрики (MatrixMetrics)

| Категория | Метрики |
|-----------|---------|
| Нейроны | `matrix_neurons_active`, `matrix_neurons_frozen` |
| Эволюция | `matrix_evolution_generations_total`, `matrix_evolution_fitness_best`, `matrix_evolution_fitness_avg` |
| Акторы | `matrix_actor_messages_total`, `matrix_actor_errors_total` |
| HADES | `matrix_hades_alerts_total`, `matrix_hades_isolations_total` |
| Драйверы | `matrix_driver_energy`, `matrix_driver_curiosity`, `matrix_driver_safety` |
| Выживание | `matrix_survival_runs_total` |

## Фазы разработки

| Фаза | Статус | Ключевой результат |
|------|--------|-------------------|
| 0: Искра | ✅ | MPDT-нейрон + GridWorld + ГА |
| 1: Клетка | ✅ | Кластер + Медиатор + Event Sourcing |
| 2: Организм | ✅ | Иерархия + Этика + Консенсус + Чат-бот |
| 3: Ноосфера | ✅ | Реестр + Индекс + Кредиты |
| 3.5: Психика | ✅ | Cauldron + HADES + Eleutheria |
| 4: Цифровая Тень | ✅ | AntiDopamine + EcoAudit + BlackBoxExplainer |
| 6: Цивилизация | ✅ | KnowledgeWeaving + Multilingual + Council |
| 7: Экономика | ✅ | RegenerativeEconomics + Audit + Certification |
| Observability | ✅ | Micrometer + OTEL + JSON + Grafana |
| Minecraft | ✅ | Sandbox + Spigot Plugin (Paper 1.20.4) |
| PvP-бота | ✅ | Multi-tenancy API + R2DBC + Neuro-Symbolic Bridge |
| K8s + Operator | ✅ | Manifests + CRD + Reconciler (L9) |
| Пилоты #1-7 | ✅ | GridWorld + ChatBot + Cauldron + HADES + Noosphere |
| Сайт + песочница | ✅ | GitHub Pages + MPDT Sandbox (L15 §4.1) |
| Онбординг | ✅ | CONTRIBUTING + CoC + SECURITY + шаблоны |

## Спецификации (L0–L22)

Все 22 документа в `docs/`. См. [INDEX.md](docs/INDEX.md).

## Аксиомы (L0)

1. **Дискретность** — только бинарная логика в ядре
2. **Локальность** — K_MAX ≤ 20 входов на нейрон
3. **Интерпретируемость** — каждое решение — цепочка булевых операций
4. **Непрерывная эволюция** — система никогда не прекращает обучение
5. **Неотчуждаемая безопасность** — FROZEN-нейроны неизменяемы
6. **Иерархическая автономия** — Медиаторы с весовым принятием решений

## Три запрета

1. Не убий
2. Не пытай
3. Не порабощай

## Лицензия

AGPLv3 с этическими ограничениями. Запрещено использование в нарушение Трёх запретов.
См. [LICENSE](LICENSE) и [L12](docs/L12_Legal.md).
