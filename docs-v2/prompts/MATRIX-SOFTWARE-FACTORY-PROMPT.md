# ПРОМПТ: MATRIX SOFTWARE FACTORY — v16.0.0 (волновая фабрика разработки)

> Запускать в OpenCode на `/home/alexandr-narbaev/Projects/agi`, ветка `main` (HEAD `ac036bc5`).
> Модель: MiniMax или эквивалент. Время на задачи НЕ фиксируем — критерий перехода от волны к волне только PASS чек-лист.

---

## 0. КОНТЕКСТ ПРОЕКТА (проверено, актуально)

**Что уже есть и работает:**
- `matrix-core` — FROZEN исследовательское ядро W1→W1500: BIR + HDC(256) + MCTS,
  FROZEN-модуляторы (ETHICAL_FILTER / SAFETY_MONITOR / LIE_DETECTOR / CONSISTENCY_CHECKER),
  liquid federation, omni-modal gateway, transcoders (AudioFFTEncoder, VisionEdgeEncoder,
  TranscoderComparator), классы life/scaling/recording/research. ~1119 тестов.
- Экосистема T-01..T-10: `matrix-api-gateway` (MinimalHttpServer :8765, JWT-lite токены,
  rate limiting FREE/PRO/ENTERPRISE, OpenAPI), `matrix-audit` (hash-chained log),
  `matrix-billing` (CreditLedger, LicenseValidator), `matrix-observability` (Prometheus metrics),
  `matrix-quality` (14 Goal Guard gates + GateOrchestrator + QualityCli), SDK java/python/js,
  pilots (smart-home-agent, edu-assessor, compliance-bot), `matrix-web-ui` (Next.js landing+dashboard).
- `ProductionBrainClient` (279 строк) — рефлексивно грузит BirBrainCycle из классов/jar core,
  режим `MATRIX_MODE=production`. Эндпоинты: /v1/auth/login, /v1/analyze, /v1/explain/{id},
  /v1/teach, /v1/learn, /v1/transcode/audio, /v1/transcode/image, /v1/federate, /v1/audit/logs,
  /metrics, /health/live|ready.
- Демо-скрипты: `demo-chat.sh`, `metrics-watch.sh`, `audit-stream.sh`.
- Двойной remote по плану: origin (github) + gitverse (gitverse.ru).

**Диагноз последней пользовательской сессии (почему «не прорыв»):**
Мозг отвечает через TF-IDF-подобный retrieval из SimpleKnowledgeBase (~12 документов).
Пользователь видит: заученные ответы; «I cannot answer that confidently» для всего нового;
обрывки контекста («md] Paris is the capital of France»); отсутствие рассуждений;
обучение только ручным teach(); отсутствие RU/EN мультиязычности; нет переноса знаний
(2+2 знает, 2+3 — нет). Это НЕ баг интеграции — это отсутствие продуктового слоя познания
поверх FROZEN ядра. Ядро умеет HDC binding/cosine, BIR-правила, MCTS-планирование,
SleepEngine-консолидацию, SynapticPruner, FederatedLearningEngine — но шлюз вызывает
только `cycle(String)` и ничего больше.

**Инфраструктурные аномалии (найти и подтвердить при аудите Wave A):**
- В текущем клоне отсутствует remote `gitverse` — восстановить
  (`git remote add gitverse git@gitverse.ru:AlexandrNarbaev/agi.git`) или задокументировать отказ.
- В истории коммиты вида `WAL: W<N>` соседствуют с conventional commits; единого ruleset нет.
- Версии рассинхронизированы: root `build.gradle` = `0.1.0-SNAPSHOT`, matrix-core = `1.0.0`,
  релиза v16 нигде нет.
- В локальном клоне был stash `local prompt drafts before sync` — проверить содержимое, не терять полезное.
- Упомянутые в отчётах ветка `feature/wave-transformation` и `docs/waves/PR_DESCRIPTION.md`
  отсутствуют в репозитории — создать как часть Фабрики (Wave A/G).

---

## 1. ЦЕЛЬ ФАБРИКИ

Превратить «один retrieval-мозок за API» в **Software Factory**: систему, где каждая волна —
воспроизводимый конвейер (план → реализация → тесты → демо-доказательство → метрики →
документация RU/EN → commit → push → PR → merge → SESSION-обновление), а продукт получает
четыре недостающие способности: **мышление, обучение, распознавание, мультиязычность**.

Итоговый KPI релиза v16.0.0: пользователь в чате задаёт вопрос, которого НЕТ в базе;
MATRIX рассуждает (BIR-правила + HDC-аналогия + MCTS-перебор), отвечает осмысленно,
объясняет шаги (XAI), запоминает ответ на будущее, принимает русский текст и понимает
его так же, как английский; всё это видно в Grafana/audit/metrics в реальном времени.

---

## 2. ПРИНЦИПЫ (несокрушимые)

1. **CONSTITUTION**: Article I — no LLM in runtime (символьные/HDC/BIR/ONNX-distilled методы);
   Article III — seeded Random(42L), воспроизводимость; Article IV — FROZEN-модуляторы
   не отключаются и не обходятся НИКОГДА; Article VI — никаких утверждений о сознании;
   Article VIII — Apache-2.0.
2. **FROZEN core**: `matrix-core` модифицируется только аддитивно (новые пакеты/классы);
   существующие публичные сигнатуры не ломаются; все ~1119 тестов остаются зелёными на КАЖДОЙ волне.
3. **Evidence over claims**: ни одна волна не считается выполненной без артефактов —
   логи тестов, MP4/HAR/CSV через BenchmarkCamera, вывод QualityCli, скриншоты дашборда.
4. **Каждая волна = отдельная ветка + PR**: `feature/wave-<slug>` → CI green → PR в `develop`
   → squash-merge. Формат коммита: `<type>(<scope>): WAVE <ID> — <desc>`.
5. **Тесты только через Gradle**: `./gradlew :matrix-core:test --tests "io.matrix.*"`
   (прямой `java -cp` падает с NoClassDefFoundError).
6. **Никаких заглушек в production-треке**: StubBrainCycle остаётся только для CI/тестов;
   всё под `MATRIX_MODE=production` обязано вызывать реальные компоненты ядра.

---

## 3. ВОЛНЫ РАЗРАБОТКИ

### Wave A — Аудит и санитария Фабрики (без новых фич)
- Полный аудит: сборка всех модулей (`./gradlew build`), прогон всех тестов, статус GitHub Pages,
  работоспособность всех эндпоинтов (создать `scripts/smoke-test.sh`).
- Проверить stash/локальные draft-промпты; полезное закоммитить в `docs-v2/prompts/`.
- Восстановить/задокументировать gitverse remote. Синхронизировать версии:
  `gradle.properties: matrixVersion=16.0.0`, применить ко всем модулям.
- Создать каталог `docs/waves/`: `TEMPLATE-WAVE.md`, `BACKLOG.md`, `STATUS-DASHBOARD.md`.
- Реализовать **Wave Runner**: `scripts/wave-runner.sh start|finish <wave-id> [slug]` —
  one-command pipeline: branch create → build → test → smoke → quality-cli run →
  artifacts to `output/waves/<id>/` → commit → push → gh pr create → merge.
  Все последующие волны выполняются ТОЛЬКО через него.
- **PASS**: полный build green; smoke-test все эндпоинты OK; wave-runner dry-run OK;
  QualityCli ≥ 100/100; `docs/waves/*` закоммичены и запушены.

### Wave B — Продуктивный слой познания (решает «не прорыв»)
Новый пакет `io.matrix.cognition` в matrix-core (аддитивно):
- **SymbolicMathEngine**: токенизатор + recursive descent; ответ для ЛЮБОГО `a+b`, `a*b`, `a^b`;
  правила как BIR-триплеты, результат биндится в HDC.
- **MultilingualLexicon**: RU/EN словарь концепт-токенов (кириллица/латиница → один HDC-концепт);
  определение языка по Unicode-range; RU-запрос матчится с EN-документом (HDC cosine по
  нормализованным векторам); структура расширяема до 5 языков.
- **AnalogyResolver**: для неизвестного запроса — поиск ближайших HDC-аналогов в памяти и
  перенос структуры ответа (2+2→4 ⇒ аналогией для 2+3, если правило движка отсутствует).
- **ReasoningTrace**: каждый шаг (parse → rule match → HDC recall → modulator check → answer)
  пишется в ExplainTrace — дашборд показывает реальный reasoning, а не «stub».
- **ConversationMemoryWriter**: успешные обмены автоматически пишутся в KB через teach()
  (обучение из диалога в реальном времени) с обязательной проверкой FROZEN-фильтрами.
- Обновить `ProductionBrainClient`: вместо одного `cycle(String)` — оркестрация
  MathEngine → Lexicon → AnalogyResolver → BirBrainCycle fallback; DTO-совместимость сохранить.
- Тесты: ≥ 40 новых (математика вне базы; RU-запрос к EN-базе; аналогия; полнота trace;
  ETHICAL_FILTER блокирует запись токсичного факта).
- **PASS**: в `demo-chat.sh` запросы `2+3`, `столица франции`, `how are you` дают разные
  осмысленные ответы с непустым ReasoningTrace; unknown-запрос после teach() recalled с
  confidence > 0.7; все старые тесты зелёные.

### Wave C — Распознавание и мультимодальность end-to-end
- Подключить AudioFFTEncoder/VisionEdgeEncoder к /v1/transcode/* так, чтобы HDC-результат
  попадал в память и участвовал в recall (не просто возвращать размерности).
- Добавить /v1/transcode/text-multilingual (RU/EN вход → HDC).
- BenchmarkCamera: записать MP4/HAR/CSV доказательства для 3 сценариев
  (голос→ответ; изображение края→описание; RU-вопрос→ответ). Артефакты в `output/waves/C/`.
- **PASS**: сквозной тест «encode image → ask about it → HDC-match > threshold»;
  транскодированный сигнал реально влияет на analyze.

### Wave D — Обучение, федерация, сон
- Активировать SleepEngine: фоновая консолидация (taught-факты → сжатые HDC-кластеры),
  SynapticPruner удаляет устаревшее по Utility*Recency. Endpoints: POST /v1/sleep/run,
  GET /v1/memory/stats.
- FederatedLearningEngine: две локальные ноды gateway federate друг с другом
  (docker-compose profile `federation`), обмен знаниями с DP-бюджетом; /v1/federate реально
  синхронизирует KB, а не возвращает пустой реестр.
- **PASS**: тест «нода A выучила факт → sleep → federate → нода B recalls факт»;
  метрики memory_size/consolidations видны в /metrics.

### Wave E — Экономическая обратная связь (самоокупаемость)
- Связать CreditLedger с analyze/teach/transcode: списание кредитов за вызовы; пополнение —
  Stripe webhook (mock-режим для демо). Free tier лимит реально возвращает 429.
- GET /v1/billing/usage (RU/EN). Пилоты перевести на SDK-java с биллинговым middleware.
- **PASS**: e2e-тест login(free) → превышение лимита = 429; pro-клиент видит debit-записи
  в ledger; audit-log содержит события биллинга.

### Wave F — Документация и витрина мирового уровня
- GitHub Pages (docs-v2): раздел «Product» (quickstart, API playground embed, XAI gallery со
  скриншотами дашборда, видео-демо Wave C) и раздел «Factory» (BRANCHING-STRATEGY,
  волновой процесс, BACKLOG, STATUS-DASHBOARD).
- README.md (EN) + README.ru.md: hero-секция, ASCII-диаграмма архитектуры, copy-paste запуск
  (5 команд от clone до chat), badge-статусы CI/tests/Pages.
- docs-validation.yml: RU/EN parity gate обязателен для merge.
- **PASS**: Pages-деплой green; каждая цифра в README подтверждена скриптом
  `scripts/verify-readme-claims.sh` (запускает smoke, парсит результаты).

### Wave G — Релиз v16.0.0 Software Factory
- Ветка `release/v16.0.0`: финальный прогон ВСЕХ тестов, QualityCli 14/14, smoke 100%,
  performance baseline (p95 latency analyze < 500ms local), security scan (no secrets, OWASP checklist).
- CHANGELOG v16.0.0 (RU/EN), миграционный гайд с T-экосистемы, tag `v16.0.0`,
  GitHub Release с артефактами (native binary checksum, docker images, SDK packages).
- `docs/waves/PR_DESCRIPTION.md` — описание трансформации для PR `feature/wave-transformation`
  (title: `feat: Software Factory Transformation v16.0.0`, base: develop/main).
- Финальный SESSION.md: таблица волн A–G с evidence-путями.
- **PASS**: tag v16.0.0 на origin (+gitverse); release published; all gates green;
  fresh clone → wave-runner dry-run → demo-chat даёт «прорыв»-сессию (записать MP4).

---

## 4. ПРОТОКОЛ ОДНОЙ ВОЛНЫ (шаблон исполнения)

```bash
./scripts/wave-runner.sh start <WAVE-ID> "<slug>"       # feat/wave-<slug>
# ...реализация строго по секции волны...
./gradlew build test                                    # всё зелёное
bash scripts/smoke-test.sh                              # все эндпоинты OK
./gradlew :matrix-quality:run --args="run ."            # Goal Guard 100/100
# артефакты -> output/waves/<ID>/; обновить docs/waves/STATUS-DASHBOARD.md
git add -A && git commit -m "feat(<scope>): WAVE <ID> — <desc>"
git push origin feature/wave-<slug>
gh pr create --base develop --title "feat: WAVE <ID> — <desc>" --body-file docs/waves/PR-<ID>.md
gh pr merge --squash && ./scripts/wave-runner.sh finish <WAVE-ID>
```

Если волна меняет контракт API — сначала аддитивный новый эндпоинт, @Deprecated на старый,
удаление только в следующем мажорном релизе.

## 5. ЧЕГО ДЕЛАТЬ НЕЛЬЗЯ

- Не включать LLM в runtime-путь (Article I). ONNX-distilled — можно; сырой генеративный LLM — нет.
- Не трогать FROZEN-модуляторы и не ослаблять ConfidenceFilter ради «умных ответов».
- Не объявлять волну выполненной без evidence-артефактов и зелёного CI.
- Не пушить напрямую в main (кроме hotfix по BRANCHING-STRATEGY).
- Не переписывать историю уже запушенных веток.

## 6. ПЕРВОЕ ДЕЙСТВИЕ АГЕНТА

Начать с Wave A: аудит текущего состояния (build/test/smoke), затем wave-runner.sh,
затем `docs/waves/BACKLOG.md` с уточнением объёмов B–G по итогам аудита.
По завершении каждой волны — краткий отчёт пользователю: таблица PASS/FAIL критериев
и пути к артефактам. Не останавливаться между волнами A–G без явного blocker'а.
