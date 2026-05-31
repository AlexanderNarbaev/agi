# МАТРИЦА (MATRIX)

Распределённая когнитивная архитектура на основе MPDT-нейронов (McCulloch-Pitts Decision Tree Neurons).

## Статус: v1.0.0

**411 тестов** | **82% покрытие** | **Java 25** | **Apache Pekko 1.6.0**

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
# Сборка и тесты
./gradlew :matrix-core:build

# Minecraft эксперимент
./gradlew :matrix-core:jar
java -cp "matrix-core/build/libs/*:$(./gradlew -q :matrix-core:dependencies --configuration runtimeClasspath | tr '\n' ':')" io.matrix.MinecraftExperiment

# Системное демо
java -cp ... io.matrix.SystemDemo

# Minecraft Spigot плагин
./gradlew :matrix-spigot:build
cp matrix-spigot/build/libs/MatrixSpigot-1.0.0.jar server/plugins/
```

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

## Аксиомы (L0)

1. **Дискретность** — только бинарная логика в ядре. Никаких float/double.
2. **Локальность** — K_MAX ≤ 20 входов на нейрон.
3. **Интерпретируемость** — каждое решение — цепочка булевых операций.
4. **Непрерывная эволюция** — система никогда не прекращает обучение.
5. **Неотчуждаемая безопасность** — FROZEN-нейроны неизменяемы.
6. **Иерархическая автономия** — Медиаторы с весовым принятием решений.

## Лицензия

Исследовательский проект. Запрещено использование в критических приложениях без надзора.
Запрещено применение в нарушение Трёх запретов (убийство, пытки, порабощение).
