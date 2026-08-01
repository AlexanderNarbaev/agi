# Документация MATRIX — карта

## Нормативный каркас (корень)

| Документ | Статус | Назначение |
|---|---|---|
| [../CONSTITUTION.md](../CONSTITUTION.md) | FROZEN | Аксиомы, инварианты, governance |
| [../AGENTS.md](../AGENTS.md) | normative | Протокол работы агентов и разработчиков |
| [../WAL.md](../WAL.md) | ephemeral | Checkpoint сессий |
| [../README.md](../README.md) | normative | Обзор, быстрый старт, API |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | normative | Участие |

## Видение и архитектура

| Документ | Назначение |
|---|---|
| [vision/ARCHITECTURE.md](vision/ARCHITECTURE.md) | Целевая архитектура: BIR, Developmental Loop, MA-уровни, Noosphere, субстраты |
| [vision/OPEN_PROBLEMS.md](vision/OPEN_PROBLEMS.md) | Исследовательское видение без обещаний сроков |

## Научные основания

| Документ | Назначение |
|---|---|
| [science/FOUNDATIONS.md](science/FOUNDATIONS.md) | Что заимствовано из математики, физики, биологии, химии, медицины, психологии, педагогики, социологии — с механизмами и метриками; что отвергнуто и почему |

## Спецификации фич

| Спека | Компонент | Этап ROADMAP |
|---|---|---|
| [spec/SPEC-002-boolean-compute-layer.md](spec/SPEC-002-boolean-compute-layer.md) | **Keystone.** BIR: формы TT/CLAUSESET/BDD, компилятор, верификатор, producers, субстратные бэкенды | 1 |
| [spec/SPEC-001-weight-conversion.md](spec/SPEC-001-weight-conversion.md) | Карантин случайной конвертации → дистилляция по активациям в BIR | 0, 1 |
| [spec/SPEC-000-developmental-loop.md](spec/SPEC-000-developmental-loop.md) | Curriculum engine, MA-гейты, scaffolding с затуханием | 3 |
| [spec/SPEC-003-knowledge-topology.md](spec/SPEC-003-knowledge-topology.md) | Ricci-анализ графов знаний, drift-fingerprint, curriculum-ordering | 3 |

## Проектные спецификации (как реализовать)

Алгоритмический слой между спеками и кодом: структуры данных, псевдокод, сложности, бюджеты, приёмочные тесты. Спека говорит «что и зачем», design — «как именно».

| Документ | Уровень | Содержание |
|---|---|---|
| [design/DESIGN-01-units.md](design/DESIGN-01-units.md) | атом | Гибридная вычислительная единица: TT/CLAUSESET/BDD — представления, алгоритмы evaluate, компилятор форм, макет памяти |
| [design/DESIGN-02-composition.md](design/DESIGN-02-composition.md) | сеть → персона | BirNet (DAG), Capability, точка зрения (ансамбль+калибровка), профессионал (Persona + MA), разрешение конфликтов, карантин |
| [design/DESIGN-03-pipeline.md](design/DESIGN-03-pipeline.md) | сквозной контур | Ввод → биты (кодировщики), размышление (BRC-цикл с бюджетами), вывод (декодеры); OpenAI-фасад, прокси, MCP; изоляция внешних зависимостей |
| [design/DESIGN-04-learning.md](design/DESIGN-04-learning.md) | офлайн-обучение | TsetlinTrainer (формулы), дистилляция (конвейер TREPAN), интерактивное обучение с учителем, предобучение Persona, fading |
| [design/DESIGN-05-memory.md](design/DESIGN-05-memory.md) | состояние | Слои M0–M4, recall, консолидация («сон»), забывание/tombstone, журнал событий, дрейф, коллективная память |

## Исследования

| Документ | Назначение |
|---|---|
| [research/HYPOTHESES.md](research/HYPOTHESES.md) | Пререгистрированные гипотезы H-001…H-009 и карточки экспериментов EXP-001…009 |
| [research/METRICS.md](research/METRICS.md) | Реестр метрик: формула, источник, команда, порог; теоретические и прототипные значения |
| [research/ANALYSIS-laptop-feasibility.md](research/ANALYSIS-laptop-feasibility.md) | Пробелы/возможности + расчёт ноутбучной реализуемости vs локальные LLM |
| [research/prototype/](research/prototype/) | Воспроизводимый прототип BIR (код + результаты) |
| [research/papers/](research/papers/) | Научные публикации (автогенерация из артефактов) + протокол |
| research/reports/ | Отчёты экспериментов (включая отрицательные) |

## Инженерия

| Документ | Назначение |
|---|---|
| [engineering/ROADMAP.md](engineering/ROADMAP.md) | Цели G1–G6, этапы 0–5 с критериями выхода, стратегия миграции |
| [engineering/JAVA_STACK.md](engineering/JAVA_STACK.md) | Технологический стек: принято/отвергнуто, ONNX-контракт |
| [engineering/ADR-001-matrix-vs-rag-system-roles.md](engineering/ADR-001-matrix-vs-rag-system-roles.md) | rag-system как внешняя зависимость: заимствование принципов, интеграция по контракту |

## Операционные документы

[API.md](API.md) · [DEPLOYMENT.md](DEPLOYMENT.md) · [GLOSSARY.md](GLOSSARY.md)

## Архив

`archive/` — прежние спецификации (L0–L23), долгосрочные планы, синтезы исследований. Сохранены как история; нормативной силы не имеют. При противоречии действует текущий каркас.
