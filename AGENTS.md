# AGENTS — процедуры сессий

**Статус: normative · singleton** · пересмотр 2026-08-26 (v2 rebuild).

## Роли

- **Координатор (main agent)**: владелец, стратегия, финальные синтезы, запись критических нормативов (CONSTITUTION, AGENTS, WAL).
- **Субагент (worker)**: ограниченная задача (файл, фикс, тест); может писать только в выделенную поддеревь; не меняет нормативы без явного мандата.

## Один заход — один значимый шаг

Каждая сессия — минимально-ценностная (митигация карго-культов и разрастания). Большие задачи разбиваются на волны (см. `docs-v2/engineering/PLAN.md` и каноническую дорожную карту `docs-v2/governance/ROADMAP.md`).

## Процедура сессии

1. Прочитать `docs-v2/governance/PROJECT-STATE.md`, `docs-v2/governance/ROADMAP.md`, `docs-v2/governance/DECISION-REGISTER.md`, `docs-v2/governance/OPEN-QUESTIONS.md`, `docs-v2/INDEX.md`.
2. Определить текущую волну и ответственного.
3. В конце — обновить отчёт о волне (`docs-v2/engineering/W<N>-STATUS.md`), канонические реестры и ПЛАН, закоммитить+запушить значимые изменения — если волна это допускает (см. `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md`, уровни автономии).
4. Инциденты (диск, удаления, несовместимости) фиксируются отдельной записью в журнале `wal/SESSION_WAL.md` и в отчёте о волне.

## Запреты

- Не редактировать FROZEN-зоны (`CONSTITUTION.md`, `AGENTS.md`, `matrix-core/src/main/java/io/matrix/ethics/frozen/`, avro схемы, `.github/workflows/**`) без явного RFC-мандата владельца в этой сессии.
- Не оставлять новый код без тестов.
- Не генерировать непроверенные числа в отчётах.
- Не использовать без явного RFC: Random/wall-clock в рантайм-путях решений, LLM-вызовы в детерминированном контуре, dependency upgrade мажорной версии.

## Коммиты

Формат: `WAL: <краткое описание изменения>`. Один коммит = одно значимое изменение. **Push после каждого — если волна это допускает; push без явного разрешения владельца — операция уровня 3** (см. `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §3). Существенные выводы закрепляются в отчёте о волне и в канонических реестрах (`docs-v2/governance/`), а не в чате. Журнал `wal/SESSION_WAL.md` — локальный (каталог `wal/` исключён `.gitignore`), поэтому он **не** заменяет собой отчёт о волне.

## Тестирование

- Все новые классы покрыты JUnit 5 / jqwik тестами перед коммитом.
- Targeted suites: `./gradlew :matrix-core:test --tests "io.matrix.<pkg>.*"`.
- Полный прогон — только по согласованию (риск OOM).
- Базовая линия — привязана к коммиту и живёт в `docs-v2/engineering/TEST-CATALOGUE.yml`; устаревшее число счётчиков не является базовой линией.
- Доказательство — JUnit XML (`<module>/build/test-results/test/*.xml`), а не сводка в консоли Gradle.
- JMH замеры через `--rerun-tasks -PjmhBenchmark=Name` для гейтов DESIGN-04/14.

## Команды пользователя

Пользователь может дать директиву, переопределяющую любые правила сессии. В таких случаях директива фиксируется в WAL как инцидент с датой/решением.

## Meta-rules многоотраслевых исследований (W31 doctrine)

**META-R1 (cross-disciplinary research).** При любом архитектурном изменении ядра (новый класс алгоритма, новая capability level, новая подсистема мозга) **параллельно поднимаются ≥3 research-волны** из разных школ мысли:

| Волна | Область |
|---|---|
| R-A | Современные SOTA ML / DL (Transformer/Mamba/BitNet/DPO) |
| R-B | Кибернетика / computational cognition (Anokhin, Bernstein, Ashby, Minsky, Simon) |
| R-C | Soviet / Russian / Asian schools (Glushkov, ICOT, Zadeh, Wu Wenjun, Nyaya) |
| R-D | Нейронаука раннего обучения (Spelke, Spitz-Sokolov, Kauffman) |
| R-E | Физика/химия/биология вычислений (DNA, memristors, neuromorphic) |
| R-F | Математика творчества (Gödel, Kolmogorov, L-системы, клеточные автоматы) |

Зачем: обеспечить, что алгоритмические решения **не локально-оптимальны**, а согласуются с историческими и междисциплинарными находками.

**META-R2 (knowledge persistence).** Любая cross-disciplinary находка **обязательно фиксируется** в `docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md` (доктрина), `docs-v2/designs/DESIGN-{NN}-*.md` (приложение) и соответствующие hypotheses в `docs-v2/research/HYPOTHESES-NEW.md`. Никакие research-выводы не остаются только в чате.

**META-R3 (anti-pattern: Wikipedia-only).** Каждое существенное утверждение должно быть привязано к **первоисточнику** (arXiv, оригинальная книга/статья, официальная документация). Агрегаторы общего уровня — не приемлемы для значимых утверждений. UNVERIFIED — помечать явно.

**META-R4 (deep-research-agent timeouts).** Подзадачи «параллельное глубокое исследование» (deep-researcher) имеют свойство зависать на >30 минут. Оптимум — **4 параллельных волны за раз**. Не запускать 5+ одновременно.

**META-R5 (capability-level commits).** Любое новое измерение «brain может X» должно привязываться к конкретному **DESIGN-58 capability level**. Это позволяет независимо верифицировать прогресс и не размазывать обязательства.

## Параллельные deep-research волны

После W31 допускается **до 4 одновременных** глубоких research-задач. Каждая — с явным скоупом и конкретным
output. Таймаут на research агента = 30 минут; долгие задачи необходимо делить на более узкие подзадачи.

## AI Dev Tools (via opencode_initializer)
- DevoxxGenie (JetBrains, Apache 2.0) — local LLMs, RAG, MCP
- Cline (VS Code+JetBrains, Apache 2.0) — AI agent, Ollama
- Tabby (VS Code+JetBrains, Apache 2.0) — self-hosted completion
- Aider (CLI, Apache 2.0) — git-aware edits
