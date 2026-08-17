# AGENTS.md — Инструкции для ИИ-агентов и разработчиков

Прочитай `CONSTITUTION.md` первым. При конфликте инструкций — конституция побеждает.

## Быстрые команды

```bash
./gradlew test                              # все тесты
./gradlew jacocoTestCoverageVerification    # гейт покрытия ≥82% — не понижать
./gradlew :benchmarks:jmh                   # бенчмарки (JMH), когда модуль появится
docker compose up -d                        # локальная инфраструктура (Kafka/Postgres/Redis)
```

## Жёсткие ограничения (нарушение = отклонение PR)

1. **K_MAX = 20 для TT-формы** — не увеличивать размер таблиц истинности; рантайм исполняет только BIR-артефакты (TT/CLAUSESET/BDD); «сырые» структуры обучения — вне рантайма.
2. **FROZEN-слой и FROZEN-документы** (`ethics/` фроузен-артефакты, CONSTITUTION.md, `avro/` схемы, workflows) — не изменять.
3. **Четыре запрета** — не ослаблять, не обходить, не «переинтерпретировать».
4. **Детерминизм рантайма** — никаких LLM-вызовов, случайности без seed и wall-clock в рантайм-контуре решений. Обучение может быть стохастичным — вне рантайма.
5. **Coverage gate** не понижать; новый код — с тестами (jqwik property-based для чистой логики, JUnit для интеграции, Testcontainers для инфраструктуры).

## Do not touch (без явного RFC владельца)

- `ethics/**` (FROZEN-слой), `avro/**` (только обратимо-совместимые изменения), `.github/workflows/**`, `docs/archive/**`, `CONSTITUTION.md`.

## Конвенции документов

- Статусы в шапке каждого .md: `FROZEN` (только RFC), `normative` (спеки; изменения — с changelog в шапке: дата, причина, суть), `living` (research/, обновляется экспериментами), `ephemeral` (WAL.md).
- Ссылки на секции — URI-формат: `SPEC-001#metrics`, `AGENTS.md#quick`. Запрещены ссылки «см. документ X» без секции.
- Спеки — это IPC между человеком и агентом. Изменение, не записанное в файл, не существует.

## Протокол сессии (WAL)

В начале сессии прочитай `WAL.md`. В конце — перепиши его по шаблону из самого файла: Активный фокус / Правила сессии / Что сделано / Следующее действие / Известные проблемы. WAL — checkpoint, не лог: детали реализации — в спеках и git-истории. Заверши сессию milestone-коммитом (`git commit -m "WAL: <что сделано>"`).

## Специализированные протоколы

При работе в соответствующих зонах обязательны файлы `docs/agents/`: `AGENTS-MODULES.md` (модули сигналов, DESIGN-06), `AGENTS-RESEARCH.md` (исследования и статьи). Карта и правила области действия — `docs/agents/README.md`. Ограничения платформы: `docs/engineering/JAVA_NATIVE.md` (GraalVM native, JMM).

## Правила работы

1. **Одна сессия — один SPEC.** Нашёл постороннюю проблему — запиши в WAL «Известные проблемы», не чини мимоходом (кроме блокеров).
2. **Малые правки** (≤ ~30 строк): WAL-запись + тест + короткий diff. **Фичи** — полный цикл: specify → clarify → plan → tasks → analyze → implement, гейт analyze перед implement.
3. **Каждый обучающий/эволюционный цикл** — с объявленным монотонным функционалом Φ (CONSTITUTION, Статья III). Нет Φ — не запускай.
4. **Эксперименты** — только через карточки в `docs/research/HYPOTHESES.md` (статусы proposed → running → accepted/rejected/superseded); гипотеза и метрики фиксируются до запуска; обязательная базовая линия.
5. **Запрещённые claims** (Статья VI): «AGI», «не лжёт», «не забывает», «не может быть использован во вред», числа без бенчмарка. Встретил такие строки в редактируемых файлах — исправляй на проверяемые формулировки.
6. **Продакшн-код — Java.** Python только в `docs/research/` и одноразовых `scripts/`, не в продакшн-пути и не в зависимостях сборки.
7. **Сессии короткие:** блоки ≤ 30–45 минут; лучше milestone-коммит и новая сессия, чем дрифт контекста.

## Куда что класть

| Артефакт | Путь |
|---|---|
| Спеки фич | `docs/spec/SPEC-NNN-*.md` |
| Проектные спецификации (алгоритмы, структуры данных) | `docs/design/DESIGN-NN-*.md` |
| Гипотезы и EXP-карточки (preregistration) | `docs/research/HYPOTHESES.md` |
| Отчёты экспериментов | `docs/research/reports/EXP-NNN-report.md` |
| Реестр метрик | `docs/research/METRICS.md` |
| Архитектурные решения | `docs/engineering/ADR-NNN-*.md` |
| Модули входящих/исходящих сигналов | `signal-modules/**` (спека DESIGN-06; правила docs/agents/AGENTS-MODULES.md) |
| Агентные протоколы зон | `docs/agents/AGENTS-<ZONE>.md` (+ строка в docs/agents/README.md) |
| Разовые исследовательские скрипты | `docs/research/` (Python допустим только здесь) |

## Agent System Prompt

The primary agent operates as a **Universal AI Coprocessor** (see `.opencode/skills/coprocessor/SKILL.md`).

### Core Protocols

| Protocol | Description |
|----------|-------------|
| **Dual-Process Reasoning** | System 1 (fast: edits, grep, fixes) / System 2 (slow: analysis, planning, multi-file refactors). Escalate after 2 failures or >3 files touched. |
| **Memory Hierarchy** | WAL (session journal) → Specs (persistent designs) → Artifacts (ground truth). Artifacts override stale specs. |
| **Shared State = IPC** | Files are the communication protocol. Read before action, verify after write. `.opencode/state/` for inter-agent coordination. |
| **Keyboard Correction** | Auto-detect RU↔EN layout mismatch. Silent for unambiguous, confirm for ambiguous. Log to WAL with `[KB]`. |
| **CO-STAR Output** | Context → Objective → Steps → Thinking → Answer → References. Skip for trivial outputs. |
| **Memory Anchor** | Every response starts with `[CTX: domain]`. Enables context resumption after compaction. |
| **Source Ladder** | Official docs > authoritative secondary > encyclopedias > model knowledge. Flag tier: `[L1]`–`[L4]`. |

### Hard Gates
- Never emit secrets. Redact with `***`.
- Never delete code you don't understand. `#S2` analyze first.
- Never skip WAL. Journal every consequential decision.
- Never speculate. Flag `[speculative]` when confidence < 80%.


---

## AI-Native Modules (from opencode_initializer)

Three context-aware modules are installed under `src/lib/` (create the directory if absent). They cut token/context overhead and route work to the right agent.

| Module | Purpose | Local config snapshot |
|--------|---------|-----------------------|
| `src/lib/52-context-selector.sh` | Selects only the MCP/LSP servers relevant to a task | `.opencode/context-selector/config.json` |
| `src/lib/53-auto-skills.sh` | Detects task type + file type, suggests skills to load | `.opencode/auto-skills/config.json` |
| `src/lib/54-task-distributor.sh` | Routes tasks to Commander / Planner / Worker / Reviewer | `.opencode/task-distributor/config.json` |

### Quick reference
- **Context:** `_select_mcp_for_task coding` · `_select_lsp_for_file foo.ts` · `_optimize_context coding foo.ts`
- **Skills:** `_detect_task_type "fix the bug"` · `_skill_suggest "review this"` · `_auto_load_skills "..."`
- **Distribution:** `_analyze_task "..."` · `_select_agent "..."` · `_distribute_tasks "..."` · `_parallel_execute "a" "b"`

Canonical source: the `opencode_initializer` repo. Keep these config snapshots in sync with upstream.
