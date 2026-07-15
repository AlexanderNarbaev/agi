# Команда разработки AGI-системы MATRIX

**Дата:** 2026-07-16
**Версия:** v1.0
**Статус:** Активно
**Активация:** Каждая сессия начинается с загрузки этого файла и запуска многоагентной оркестрации.

---

## 1. Состав команды (14 ролей)

| Функция | Роль | Основные обязанности |
|---------|------|----------------------|
| **Стратегия** | AGI Product Manager | Формирует roadmap, приоритизирует фазы, управляет бэклогом |
| | AGI Research Scientist | Исследует SOTA-подходы, оценивает модели (Qwen, DeepSeek), пишет research-синтезы |
| | System Architect | Проектирует модульную архитектуру (Core / Nerve / Noosphere), определяет интерфейсы |
| **Разработка** | Core Developer (x2) | Реализует MPDT-нейроны, DecisionTree, генетические алгоритмы, MCTS |
| | Nerve System Developer | Разрабатывает NeuronClusterActor, EventJournal, EthicalFilter, InstanceMediator |
| | Noosphere Developer | Реализует NoosphereRegistry, KnowledgeIndex, Boolean RAG, GlobalMediator |
| | Agent & Tools Developer | Разрабатывает AgentBrain, Agent Loop, BRC, MCP-сервер, интеграции с инструментами |
| | LLM Integration Engineer | Настраивает интеграцию с OpenAI API, подготавливает пайплайн конвертации весов |
| **Данные и ML** | ML Engineer | Настраивает VQ-VAE, эмбеддинги, претрейн моделей, Boolean RAG |
| | Data Engineer | Строит пайплайны загрузки данных, датасеты для оценки качества |
| **Контроль** | QA Automation Engineer | Разрабатывает и поддерживает 2315+ тестов, обеспечивает покрытие ≥82% |
| | Safety & Ethics Engineer | Аудирует этический фильтр, Structural Safety Guard, защиту от вредоносных действий |
| **Инфраструктура** | DevOps Engineer | Настраивает CI/CD, Docker Compose, Minikube K8s, мониторинг (Prometheus/Grafana) |
| **Документация** | Technical Writer | Ведёт документацию API, архитектуру, руководства пользователя |

---

## 2. Примеры задач для каждой роли

- **AGI Product Manager**: Определить приоритеты на ближайшие 2 спринта (например, «Завершить реализацию Boolean RAG»).
- **AGI Research Scientist**: Исследовать методы конвертации весов трансформеров в таблицы истинности для Qwen3-1.7B.
- **System Architect**: Спроектировать интерфейс между BRC и Boolean RAG, зафиксировать в `system_design.md`.
- **Core Developer**: Реализовать MCTS-дерево с 100 итерациями, оптимизировать генетический алгоритм.
- **Nerve System Developer**: Доработать InstanceMediator с драйверами Energy/Curiosity/Safety.
- **Noosphere Developer**: Реализовать KnowledgeIndex с RRF-слиянием для Boolean RAG.
- **Agent & Tools Developer**: Разработать MCP-сервер для экспозации agent-способностей как MCP-инструментов.
- **LLM Integration Engineer**: Настроить пайплайн конвертации Qwen3-1.7B в Avro-формат.
- **ML Engineer**: Настроить VQ-VAE Proxy с codebook_size=256, проверить качество энкодинга.
- **Data Engineer**: Собрать датасеты для оценки Boolean RAG (золотые Q&A-пары).
- **QA Automation Engineer**: Написать интеграционные тесты для Agent Loop (1000 итераций).
- **Safety & Ethics Engineer**: Проверить EthicalFilter на 100+ проблемных запросах.
- **DevOps Engineer**: Настроить Minikube K8s с 9 подами, настроить Metrics Server.
- **Technical Writer**: Обновить API-документацию для v3.1, написать гайд по MCP-серверу.

---

## 3. Принципы взаимодействия

1. **Артефактная синхронизация**: вся коммуникация ведётся через файлы-артефакты (спецификации, код, тесты, документация). Чат используется только для кратких уточнений. Это предотвращает потерю контекста и галлюцинации.

2. **Pull Request-процесс**: любое изменение кода или документации идёт через PR с обязательным указанием связанного артефакта (задачи, спецификации). PR должен проходить все проверки CI (линтеры, unit-тесты, интеграционные тесты).

3. **WAL (Write-Ahead Log)**: файл `wal/SESSION_WAL.md` содержит 3 строки:
   - `📍 Статус: <что завершено>`
   - `🚀 Активная: <текущая задача волны>`
   - `🛑 Защита: <критические ограничения, fragile-зоны>`
   Обновляется после завершения каждой волны.

4. **Стратегическая сверка с пользователем**: если в документации (roadmap, архитектурных ADR, спецификациях) отсутствует ответ на критический вопрос, влияющий на направление разработки, команда **обязана** запросить у пользователя стратегическое решение. Это делается через создание issue или комментарий в WAL с пометкой `[STRATEGIC_NEEDED]`. Без получения ответа волна не может переходить к следующим фазам.

5. **Волновой принцип**: каждая волна — это сквозной цикл от инициации до приёмки. Волны запускаются последовательно, без длительных пауз. После завершения одной волны PM инициирует следующую на основе бэклога.

---

## 4. Механика запуска волн

**Инициация волны (PM + Architect + Research Scientist):**
- PM формулирует цель волны на основе бэклога.
- Architect оценивает влияние на архитектуру, записывает ADR.
- Research Scientist (если нужно) исследует новые подходы.
- **Если цель не описана в документации** — PM запрашивает стратегическое решение у пользователя с пометкой `[STRATEGIC_NEEDED]`.

**Выполнение волны (все разработчики + QA + DevOps):**
- Разработка ведётся параллельно по модулям, синхронизация через артефакты.
- QA пишет тесты параллельно с разработкой (TDD).
- DevOps настраивает окружение и CI/CD.
- Каждый PR проходит автоматические проверки.

**Приёмка волны (PM + Tech Lead + QA):**
- QA прогоняет все тесты, фиксирует покрытие.
- Tech Lead проводит код-ревью ключевых изменений.
- PM проверяет выполнение критериев успеха.
- Если критерии не достигнуты — волна перезапускается с корректировками.
- При успехе — обновляется WAL, волна считается завершённой.

**Типичный цикл волны:**
1. PM ставит задачу.
2. Architect/Research Scientist уточняют и фиксируют ADR.
3. Разработчики реализуют, QA тестирует, DevOps деплоит.
4. Интеграционный менеджер собирает модули.
5. PM принимает работу.
6. Обновление WAL.

---

## 5. Состояние аудита (последняя проверка: 2026-07-16)

**Результаты аудита Wave 0 — Analysis:**

| Категория | Находка | Приоритет |
|-----------|---------|-----------|
| WAL | `WAL.md` (root) v3.10 stale — 12 версий позади SESSION_WAL | HIGH |
| MASTER_PLAN | `rag/RagResult.java` — referenced but does not exist | MEDIUM |
| MASTER_PLAN | `benchmark/CompressionBenchmark.java` — referenced but does not exist | MEDIUM |
| AGENTS.md | Quarkus 3.35.4 vs actual 3.36.1 | MEDIUM |
| INDEX.md | ~14 docs files missing from index | LOW |
| INDEX.md | L21/L22 filename offset (L21.md missing, L22.md/L23.md exist) | LOW |
| Код | 75/226 files (33%) без тестов | LOW (многие — DTO/demo) |
| Код | `explain` vs `explainability` — overlapping packages | LOW |
| Код | 5 files >500 строк (max: ExplanationGenerator 608) | LOW |

**Приоритеты на следующую волну:**
1. **HIGH**: Синхронизировать WAL-файлы, поправить MASTER_PLAN references
2. **MEDIUM**: Обновить AGENTS.md Quarkus version, INDEX.md дополнения
3. **LOW**: Разобраться с `explain`/`explainability` overlap

---

## 6. Карта делегирования (для Opencode Goal Mode)

При запуске в многоагентском режиме:

| Роль | Под-агент | Тип |
|------|-----------|-----|
| AGI Product Manager | Main agent (координатор) | — |
| AGI Research Scientist | `goal-deep-researcher` + `goal-web-researcher` | research |
| System Architect | `goal-architect` | design |
| Core Developer | `goal-implementer` | implement |
| Nerve/Noosphere/Agent Developer | `goal-implementer` (parallel) | implement |
| ML Engineer | `goal-implementer` | implement |
| QA Automation Engineer | `goal-verifier` + `goal-test-reviewer` | verify |
| Safety & Ethics Engineer | `goal-security-reviewer` | review |
| DevOps Engineer | `goal-ops-reviewer` | review |
| Technical Writer | `goal-doc-writer` | write |
| Code Review | `goal-reviewer` + `goal-diff-reviewer` | review |
| Completion Guard | `goal-completion-guard` + `goal-final-auditor` | gate |

---

## 7. Критические правила

- **Запрос стратегического решения обязателен**, если цель или путь её достижения не описаны в документации. Пометка `[STRATEGIC_NEEDED]` в issue или WAL запускает процесс консультации с пользователем.
- **Без утверждённого ADR** для архитектурных изменений волна не может переходить к реализации.
- **Никаких устных договорённостей** — все решения фиксируются в артефактах.
- **Покрытие тестами не ниже 82%** — обязательное условие завершения волны (контролирует QA).
- **Каждый шаг = коммит + пуш** — после любой значимой группы изменений.
- **Пуш в оба remote:** `origin` (github.com/AlexanderNarbaev/agi) и `gitverse` (gitverse.ru/AlexandrNarbaev/agi).

---

## 8. Запуск в следующей сессии

**Стартовый чеклист для следующей сессии:**
1. Прочитать `wal/SESSION_WAL.md` — узнать текущую волну и статус
2. Прочитать `docs/MATRIX_TEAM.md` (этот файл) — загрузить структуру команды
3. Прочитать `docs/MASTER_PLAN.md` — актуальный бэклог и приоритеты
4. Запустить `git status` + `git log --oneline -5` — состояние репозитория
5. Запустить `./gradlew test` — убедиться что тесты проходят
6. Загрузить память из `memorylayer` — предыдущие решения
7. Продолжить с текущей волны согласно WAL

**Для оркестратора (Goal Mode):**
- `goal_contract` с названием волны
- Параллельный запуск под-агентов для research/implement/verify
- После каждой волны: commit + push + WAL update

---

*Конец MATRIX_TEAM.md — v1.0, 2026-07-16*
