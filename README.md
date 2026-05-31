# МАТРИЦА (MATRIX)

Распределённая когнитивная архитектура на основе MPDT-нейронов (McCulloch-Pitts Decision Tree Neurons).

## Статус: v1.1.0

**414 тестов** | **82% покрытие** | **Java 25** | **Quarkus 3.35.4** | **Apache Pekko 1.6.0**

### Observability Stack

| Слой | Технология | Эндпоинт |
|------|-----------|----------|
| Метрики | Micrometer + Prometheus | `:9091/metrics` |
| Трейсы | OpenTelemetry (OTLP) | Jaeger `:16686` |
| Логи | JSON (Quarkus) | `logs/matrix.json` |
| Health | SmallRye Health | `:9091/q/health` |
| Дашборды | Grafana | `:3000` |

## Архитектура

```
Ядро (Core)           Нервная система (Nerve)    Ноосфера (Noosphere)
┌──────────────┐    ┌──────────────────────┐    ┌──────────────────────┐
│ TruthTable   │    │ NeuronClusterActor   │    │ NoosphereRegistry    │
│ DecisionTree │───▶│ EventJournal         │───▶│ KnowledgeIndex       │
│ MPDTNeuron   │    │ InstanceMediator     │    │ CreditModel          │
│ EvolutionLoop│    │ ClusterMediator      │    │ GlobalMediator       │
│ GridWorld    │    │ EthicalFilter        │    │ DigitalShadow        │
│ MinecraftSim │    │ ConsensusEngine      │    │ CivilizationCouncil  │
└──────────────┘    │ HADES + Cauldron     │    │ RegenerativeEconomics│
                     └──────────────────────┘    └──────────────────────┘
```

## Быстрый старт

```bash
# Инфраструктура мониторинга
cd infra && docker-compose up -d

# Сборка и тесты
./gradlew :matrix-core:build

# Quarkus CLI команды
java -jar matrix-core/build/quarkus-app/quarkus-run.jar demo
java -jar matrix-core/build/quarkus-app/quarkus-run.jar simulate -g 100 -p 20
java -jar matrix-core/build/quarkus-app/quarkus-run.jar evolution -g 200 -p 50 -s 500

# Нативная компиляция (требуется GraalVM)
./gradlew :matrix-core:build -Dquarkus.package.jar.type=native

# Minecraft Spigot плагин
./gradlew :matrix-spigot:build
cp matrix-spigot/build/libs/matrix-spigot-1.0.0.jar server/plugins/
```

## Метрики (MatrixMetrics)

| Категория | Метрики |
|-----------|---------|
| Нейроны | `matrix_neurons_active`, `matrix_neurons_frozen` |
| Эволюция | `matrix_evolution_generations_total`, `matrix_evolution_fitness_best`, `matrix_evolution_fitness_avg` |
| Акторы | `matrix_actor_messages_total`, `matrix_actor_errors_total`, `matrix_actor_processing_seconds` |
| HADES | `matrix_hades_alerts_total`, `matrix_hades_isolations_total` |
| Драйверы | `matrix_driver_energy`, `matrix_driver_curiosity`, `matrix_driver_safety` |
| Выживание | `matrix_survival_runs_total`, `matrix_survival_run_seconds` |

## Фазы разработки

| Фаза | Статус | Ключевой результат |
|------|--------|-------------------|
| 0: Искра | ✅ | MPDT-нейрон + GridWorld + ГА |
| 1: Клетка | ✅ | Кластер 1000 нейронов + Медиатор + Event Sourcing |
| 2: Организм | ✅ | Иерархия Медиаторов + Этика + Консенсус + Чат-бот |
| 3: Ноосфера | ✅ | Реестр + Индекс + Кредиты + Глобальный Медиатор |
| 3.5: Психика | ✅ | Cauldron + HADES + Eleutheria |
| 4: Цифровая Тень | ✅ | Анти-допамин + Эко-аудит + Объяснитель |
| 6: Цивилизация | ✅ | Плетение знаний + Мультиязычность + Совет |
| 7: Экономика | ✅ | Аудит + Сертификация + Кооперативный пул |
| Observability | ✅ | Quarkus 3.35.4 + Micrometer + OTEL + JSON + Grafana |
| Minecraft | ✅ | Sandbox + Spigot Plugin (Paper API 1.20.4) |

## Аксиомы (L0)

1. **Дискретность** — только бинарная логика в ядре. Никаких float/double.
2. **Локальность** — K_MAX ≤ 20 входов на нейрон.
3. **Интерпретируемость** — каждое решение — цепочка булевых операций.
4. **Непрерывная эволюция** — система никогда не прекращает обучение.
5. **Неотчуждаемая безопасность** — FROZEN-нейроны неизменяемы.
6. **Иерархическая автономия** — Медиаторы с весовым принятием решений.

## Правила модели (Model Fallback)

1. DeepSeek V4 Pro Max — основная (reasoning, код, верификация)
2. DeepSeek V4 Flash — бюджетная (рутина)
3. OpenCode Go (GLM-5.1) — резерв при недоступности DeepSeek
4. OpenCode Go (GPT-5-Nano) — фоновые операции
5. Ответы строго на русском языке, технические термины — на английском

## Лицензия

Исследовательский проект. Запрещено использование в критических приложениях без надзора.
Запрещено применение в нарушение Трёх запретов (убийство, пытки, порабощение).
