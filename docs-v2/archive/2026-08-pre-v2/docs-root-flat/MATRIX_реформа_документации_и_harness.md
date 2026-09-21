# Реформа документации и harness MATRIX

**Дата:** 2026-07-31
**Назначение:** план полного переформатирования документации проекта, внедрение стандартов для ИИ-агентной разработки (AGENTS.md, constitution, spec-driven workflow) и итеративный исследовательский протокол с метриками, отчётами и артефактами.

**Основание:** состояние стандартов на середину 2026: AGENTS.md — открытый стандарт Linux Foundation (AAIF), 60 000+ репозиториев, читается 25+ агентами (Codex, Cursor, Copilot, Gemini CLI, Aider и др.) [^101^][^102^][^104^]; spec-driven development (GitHub Spec Kit: constitution → specify → plan → tasks → implement; Kiro EARS; OpenSpec для brownfield) [^85^][^88^][^89^]; эмпирика: AGENTS.md повышает эффективность агентов, но работает только с конкретными командами и границами, не с маркетингом [^103^][^112^]. Harness engineering: rules-файлы как постоянные ограничители поведения агентов, иерархическая композиция, 40–80 строк лучше простыней [^91^][^103^].

---

## 1. Диагноз текущей документации

- **50+ файлов**: L0–L23, MASTER_PLAN, LONGTERM_PLAN, аудиты, волны (Wave 35+), WAL-файлы, improvements/, research/, specs/, superpowers/. Заявка на «четвёртую волну ИИ» соседствует с runbook'ами; философия смешана с инженерией; версии рассинхронизированы (аудит v3.58 сам фиксирует ссылки на v1.3.0/v3.1/v3.35).
- **Нет разделения аудиторий**: человек-новичок, инженер, ИИ-агент, исследователь, ревьюер этики — все читают одни и те же файлы.
- **Нет нормативного ядра**: аксиомы L0 записаны как манифест, а не как проверяемая конституция с процедурой изменения.
- **Исследование неотличимо от продукта**: гипотезы (конвертация весов) выглядят как работающие фичи (см. аудит, п. 3.1).

## 2. Целевая структура (пять слоёв, разные аудитории)

```
/
├── AGENTS.md                  # единая точка входа для ИИ-агентов (см. артефакт)
├── README.md                  # точка входа для людей: что это, quickstart, статус, честные claims
├── constitution.md            # нормативное ядро: Три запрета, аксиомы L0, FROZEN-ограничения
│                              #   + процедура изменения (RFC в ThePath → консенсус → версия)
├── vision/                    # ThePath, манифесты, философия — отделены от инженерии
│   └── LINK.md                # явная трассировка: модуль ThePath → аксиома → FROZEN FNL
├── spec/                      # нормативные спецификации (нынешние L0–L23, реформированные)
│   ├── SPEC-001-mpdt-neuron.md    # статус: normative | experimental | deprecated
│   ├── SPEC-0XX-*.md              # каждая с: цель, интерфейс, инварианты, метрики, тесты, статус
│   └── RFC/                       # изменения спецификаций только через RFC
├── engineering/               # API.md, DEPLOYMENT.md, RUNBOOK.md, V3_CONFIGURATION, openapi.yaml
├── research/                  # исследовательский трек (см. §4)
│   ├── HYPOTHESES.md              # реестр гипотез: статус, метрика, решение
│   ├── experiments/EXP-XXX/       # карточка + код + отчёт каждого эксперимента
│   └── reports/YYYY-Qn/           # квартальные синтезы
├── benchmarks/                # воспроизводимый код измерений (одна команда запуска)
├── docs/archive/              # всё историческое (волны, старые планы) — с пометкой ARCHIVED
└── .github/ + workflows       # CI: coverage gate 82%, линтер claims, spec-дрейф чек
```

**Правила миграции:** каждый документ получает шапку со статусом (`normative` / `experimental` / `archived` / `vision`), датой и владельцем; всё, что не прошло аудит фактов, уходит в `archive/`; L-нумерация сохраняется как алиасы для обратной совместимости ссылок, но новые ссылки — на SPEC-XXX.

## 3. Harness: как агенты работают в репозитории

Три уровня ограничений (по образцу «rules as constraint harness» [^91^]):

1. **`AGENTS.md` (корень)** — команды сборки/тестов, границы, FROZEN-список, протокол сессии. ≤ 80 строк, команды первыми. Готовый артефакт прилагается (`AGENTS.md` рядом с этим файлом).
2. **Вложенные `AGENTS.md`** — в `matrix-core/`, `matrix-spigot/`, `scripts/`: ближайший к редактируемому файлу побеждает [^104^].
3. **`constitution.md`** — аналог constitution в Spec Kit [^89^]: неизменяемое (Три запрета, K_MAX=20, запрет менять FROZEN без RFC), проверяется чек-листом на каждом PR; CI-шаг «constitution check».

**Spec-driven workflow для изменений** (адаптация Spec Kit под проект):
```
/specify  → spec/RFC/NNN.md: что и зачем, критерии приёмки, влияние на инварианты
/plan     → технический план: затронутые SPEC-XXX, риски, метрики
/tasks    → атомарные задачи с трассировкой к критериям
/implement→ код + тесты; PR с чек-листом конституции
/converge → сверка кода со спецификацией (анти-дрейф) [^88^]
```
Для brownfield-итераций — дельта-спецификации в стиле OpenSpec (изменения как `ADDED/CHANGED/REMOVED requirements`) [^85^].

**WAL-протокол** (уже есть в проекте) — оформить как память агентных сессий: `wal/SESSION_WAL.md` = что сделано/что дальше/что защищено; AGENTS.md обязывает агента читать WAL в начале сессии и обновлять в конце. Это прямой аналог memory bank в Kiro/Spec Kit [^89^].

## 4. Итеративный исследовательский протокол

**Цикл:** гипотеза → эксперимент → метрика vs базовая линия → решение (принять/отклонить/переформулировать) → отчёт → артефакт в репозиторий.

**Карточка эксперимента** (`research/experiments/EXP-XXX/card.md`):
```
# EXP-XXX: <название>
Статус: proposed | running | accepted | rejected | superseded
Гипотеза: <фальсифицируемое утверждение>
Базовая линия: <обязательно: Tsetlin Machine / KAN / BNN / vanilla RAG / текущий ГА>
Метрика и порог решения: <число и условие>
Данные/среда: <golden-набор, сиды, железо>
Артефакты: code/, results/, report.md
Решение и дата: <что решили и почему>
```

**Реестр метрик** (`research/METRICS.md`): единая таблица «метрика → текущее значение → базовая линия → цель → дата замера». Обновляется CI или вручную после каждого эксперимента. Стартовые обязательные метрики:
- fidelity конвертации весов (vs QAT-подход);
- точность/энергия/latency MPDT vs Tsetlin Machine vs KAN на MNIST и на крафт-графе;
- выборка до сходимости: ГА vs автоматы Цетлина vs трёхфакторное правило (GridWorld);
- RAG: Recall@5, RAGAS faithfulness, citation precision (Boolean-слой vs rag-system baseline);
- Minecraft: тех-дерево/время vs ReAct-baseline;
- guardrail: FPR/FNR на красно-командном наборе, добавленная latency.

**Ритм:** итерации 2 недели; в конце каждой — обновление HYPOTHESES.md, METRICS.md и WAL; квартальный отчёт в `research/reports/` (синтез + что отклонено — отрицательные результаты публикуются обязательно, это главный антидот самообмана).

**Дисциплина артефактов:** никакой вывод не считается без кода в `benchmarks/` или `experiments/`; каждая заявленная в README цифра ссылается на воспроизводимый запуск.

## 5. Порядок работ (4 недели)

| Неделя | Действия |
|---|---|
| 1 | Разметка статусов всех 50+ документов; перенос истории в `archive/`; создание `constitution.md` и `vision/LINK.md` (трассировка ThePath) |
| 2 | Реформа L0–L23 → `spec/SPEC-XXX` с инвариантами и метриками; корневой и вложенные AGENTS.md; CI: constitution check + coverage gate |
| 3 | `research/`: HYPOTHESES.md (все текущие идеи из Приложений A/B/В как гипотезы), METRICS.md, первые 3 карточки (EXP-001 fidelity конвертации, EXP-002 MPDT vs Tsetlin Machine, EXP-003 ГА vs автоматы Цетлина) |
| 4 | `benchmarks/` skeleton с запуском одной командой; первый двухнедельный цикл по протоколу; переписанный README (честные claims из аудита, п. 3.5) |

## Источники

[^85^]: 6 Best Spec-Driven Development Tools for AI Coding in 2026 — https://www.augmentcode.com/tools/best-spec-driven-development-tools
[^88^]: GitHub Spec Kit — https://github.com/github/spec-kit
[^89^]: Understanding Spec-Driven-Development: Kiro, spec-kit, Tessl (M. Fowler) — https://martinfowler.com/articles/exploring-gen-ai/sdd-3-tools.html
[^91^]: Harness Engineering for AI Coding Agents — https://www.augmentcode.com/guides/harness-engineering-ai-coding-agents
[^101^]: AGENTS.md vs README.md for AI Agents — https://www.contextstudios.ai/comparisons/agents-md-vs-readme-for-ai-agents
[^102^]: AGENTS.md vs CLAUDE.md vs Cursor Rules: 2026 Guide — https://codersera.com/blog/agents-md-vs-claude-md-vs-cursor-rules-comparison-2026/
[^103^]: 6 AGENTS.md Examples From Real Production Repos — https://ssojet.com/blog/agents-md-examples
[^104^]: AGENTS.md in 2026: The One File 25+ AI Coding Agents Read — https://particula.tech/blog/agents-md-ai-coding-agent-configuration
[^112^]: On the Impact of AGENTS.md Files on the Efficiency of AI Coding Agents (arXiv) — https://arxiv.org/html/2601.20404v2

---

## 6. Дополнение (итерация 5): интеграция spec-kit актуального состояния, «Красной книги» и AI-native паттернов

### 6.1. Что меняется в workflow

1. **Feature 000.** Первая спека — `spec/SPEC-000-developmental-loop/`: базовая среда + Developmental Loop (curriculum engine, MA-гейты), от которой зависят остальные фичи. Паттерн заимствован из практики сообщества spec-kit [^139^].
2. **Acceptance-driven spec iteration.** Спеки НЕ заморожены: на этапе приёмки разрешены корректировки по обратной связи владельца, с обязательным changelog в шапке документа (дата, причина, суть) и инкрементом версии; критерий готовности фичи — совпадение намерения и реализации [^137^]. Коммиты кода ссылаются на версию спеки.
3. **Два режима изменений.** Полный SDD-цикл (`/speckit.*` или эквивалент) — только для фич уровня SPEC. Багфиксы и мелкие правки — упрощённый протокол: WAL-запись + тест + короткий diff-ревью. Полный цикл для мелочей — доказанный антипаттерн [^137^].
4. **Команды workflow** (актуальный состав spec-kit [^88^]): `constitution → specify → clarify → plan → tasks → analyze → checklist → implement → converge`. Команды `analyze` и `checklist` — обязательные гейты перед `implement` для SPEC-фич.

### 6.2. Уточнения по «Красной книге AI-инженера»

Принятые в разделах 2–4 артефакты (AGENTS.md, WAL, spec/) совместимы с моделью книги; добавляются четыре уточнения [^118^][^119^][^120^]:

1. **Спеки = IPC, не документация.** В заголовок каждой спеки добавить строку статуса: `Канал: человек ↔ AI (boundary object)`. Правило: если изменение не отражено в файле — оно не существует для системы.
2. **URI-адресация секций.** Конвенция ссылок `SPEC-003#verification.timeout`, `AGENTS.md#frozen` — во всех WAL-записях, карточках EXP и коммитах. Запрет ссылок «см. документ X» без секции.
3. **WAL — checkpoint, не лог.** Формат: Активный фокус / Правила сессии / Что сделано / Следующее действие / Известные проблемы. Детали реализации — в спеках и git, не в WAL.
4. **Еженедельный GC shared state.** Владелец раз в неделю перечитывает ключевые спеки целиком (устранение внутренних противоречий §2 vs §5) — внести в операционный календарь; короткие сессии предпочтительнее длинных (свежее контекстное окно) [^118^].
5. **Квартальный пересмотр harness.** Модель сопроцессоров — модель текущего уровня возможностей AI, не вечная; пересматривать вместе с квартальным отчётом протокола.

### 6.3. AI-native следствия для среды

По Jimmy Song (AI Native Infrastructure) [^122^]: системы проектируются «для неопределённости». В harness это закрепляется тремя правилами:

- Любой недетерминированный компонент (LLM-вызов) оборачивается детерминированным контрактом: вход/выход по схеме, валидация, запись в Event Sourcing (уже есть в стеке).
- Стоимость вычислений (токены, GPU-время) — метрика первого класса в METRICS.md рядом с качеством; бюджет на эксперимент — обязательное поле карточки EXP.
- Реестр инструментов ведётся по AI Native Landscape [^121^] — раз в квартал разведка новых проектов в категориях agents/runtimes/RAG/eval.

### 6.4. Обновлённый порядок работ (дельта к разделу 5)

| Неделя | Добавка |
|---|---|
| 1 | + Конвенция URI-адресации; changelog-шаблон в шапку спек |
| 2 | + SPEC-000 (Developmental Loop + MA-гейты) как feature 000 |
| 3 | + EXP-004: Ricci-flow fingerprint дрифта графа знаний (трек T7); бюджет-поле в карточках |
| 4 | + Первый еженедельный GC; квартальный слот пересмотра harness в календарь |

### Источники раздела 6

[^88^]: GitHub Spec Kit — https://github.com/github/spec-kit
[^117^]: Spec Kit Documentation — https://github.github.com/spec-kit/
[^118^]: Красная книга AI-инженера, гл. 1 — https://oleg.guru/redbook/ru/two-process-model
[^119^]: Красная книга AI-инженера, «Архитектура памяти» — https://oleg.guru/redbook/ru/memory-architecture
[^120^]: Красная книга AI-инженера, «Shared state как IPC» — https://oleg.guru/redbook/ru/shared-state-and-files
[^121^]: AI Native Landscape — https://jimmysong.io/ai/
[^122^]: AI Native Infrastructure (Jimmy Song) — https://jimmysong.io/book/ai-native-infra/
[^137^]: Evolving specs (spec-kit Discussion #152) — https://github.com/github/spec-kit/discussions/152
[^139^]: From PRD to Production: My spec-kit Workflow — https://steviee.medium.com/from-prd-to-production-my-spec-kit-workflow-for-structured-development-d9bf6631d647
