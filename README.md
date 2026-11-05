# MATRIX — детерминированное нейро-символическое ядро

> Каждое решение — проверяемая булева цепочка. Этика — FROZEN-слой, верифицируемый TLA+.
> Одинаковые состояние и вход → одинаковый вывод.

## 1. Что это — и что это не

MATRIX — **детерминированное нейро-символическое ядро**: знание представлено таблицами истинности и
их компилируемыми формами (TT / CLAUSESET / BDD), а обучение отделено от рантайма — рантайм
исполняет только скомпилированные артефакты, без вызовов LLM и без случайности. **Что это не:** не
универсальный LLM-агент, не один когнитивный цикл и не система, в которой обслуживается каждый
реализованный класс. Расстояние между «реализовано» и «обслуживается» измерено в
[docs-v2/architecture/CAPABILITY-REGISTRY.yml](docs-v2/architecture/CAPABILITY-REGISTRY.yml).

## 2. Состояние

Ветка `develop`, коммит `a6ded23338e069b8bc8a3744c234be5a03995f43`, последняя завершённая волна
**W45.2**, активная — **W46** (консолидация). Модулей в Gradle 14 (3 закомментированы, 2 вне
Gradle). Релизной версии **нет**: теги `v1.0.0` и `v16.0.0-mind` есть, процесса релиза нет.
**Каноническое состояние — [PROJECT-STATE.md](docs-v2/governance/PROJECT-STATE.md)** (снимок:
заменяется, а не дополняется). Планируемая работа — [ROADMAP.md](docs-v2/governance/ROADMAP.md).

## 3. Два обслуживающих приложения — они не встречаются

| | **цепочка A** — эпистемическое приложение | **цепочка B** — brain runtime |
|---|---|---|
| процесс | Quarkus, `io.matrix.MatrixApplication` | JDK HttpServer, `io.matrix.api.MinimalHttpServer` |
| порт / маршрутов | **9091** / **210** | **8765** / **28** |
| что обслуживает | workbench (57), cognitive kernel (11), chat (6) | `/v1/analyze`, `/v1/explain`, `/v1/learn`, `/v1/teach`, `/v1/sleep`, `/v1/goals`, health, метрики |

Изоляция измерена, а не предположена: **179 общих классов** (библиотека, не путь к данным) и
**ноль** пересечений с эпистемическим стором — карта в
[W46-ARCHITECTURE-TRUTH-MAP.md](docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md).

> **Режим гейтвея.** `MATRIX_MODE` по умолчанию **`stub`** — и тогда `/v1/analyze` отвечает из 87-строчной заглушки `io.matrix.api.brain.StubBrainCycle`, а не из настоящего мозга. Настоящий цикл (`TrueMindCycle` → `BirBrainCycle`) включается только `MATRIX_MODE=production`, который выставляет `scripts/start-mind.sh`. Текущий режим видно в `GET /health/live`, поле `"mode"`.

## 4. Быстрый старт

```bash
# 0. JDK 25 обязателен: в PATH лежит JRE, а javac — JDK 21
export JAVA_HOME=/home/alexandr-narbaev/jdk25

# 1. Собрать модули
./gradlew :matrix-api-gateway:jar :matrix-brain-runtime:jar :matrix-core:jar --no-daemon --console=plain

# 2. Runtime-classpath: пишется в matrix-api-gateway/build/runtime-classpath.txt (НЕ в корень)
#    и содержит только jar-ы; свои classes-каталоги дописывать руками НЕ нужно — это делает
#    scripts/start-mind.sh.
./gradlew :matrix-api-gateway:writeRuntimeClasspath --no-daemon --console=plain

# 3. Запустить гейтвей (production-режим, порт 8765)
bash scripts/start-mind.sh

# 4. Спросить
TOKEN=$(curl -s -X POST http://localhost:8765/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"pro@test.com"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')
curl -X POST http://localhost:8765/v1/analyze -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"input":"What is 2+3?"}'
```

Эпистемическое приложение (цепочка A) — отдельно: `./gradlew :matrix-core:quarkusDev`, затем
`http://localhost:9091`. Sanity check из свежей копии: `./scripts/fresh-clone-smoke.sh`.

> Альтернатива: dev-режим с тяжёлой инфраструктурой — `docker compose -f docker-compose.dev.yml up -d`, затем `./gradlew :matrix-core:quarkusDev`. `./gradlew test` целиком **не запускать без явного согласия владельца** — см. §7.

## 5. Что может система

[docs-v2/architecture/CAPABILITY-REGISTRY.yml](docs-v2/architecture/CAPABILITY-REGISTRY.yml) —
единственный машинно-читаемый источник: **34 строки, четыре оси** (реализовано / встроено /
проверено / работает сейчас); «SERVED» допустимо только при названном `serving_path`. Опорные
строки: `W44-COG-KERNEL` (SERVED, chain-a, `POST /v1/cognitive/observe|solve`), `W44-EPISTEMIC`
(SERVED, chain-a, `GET`/`POST /v1/workbench/**`), `W44-BRAIN-LOOP` и `W46-CYCLE-CONSCIOUSNESS`
(SERVED, chain-a, `POST /v1/chat/completions`), `W46-CYCLE-TRUEMIND` и `W46-CYCLE-BIR` (SERVED,
chain-b, `POST /v1/analyze` **только в production-режиме**), `W44-STUB` (ACTIVE, chain-b, тот же
`POST /v1/analyze` в режиме по умолчанию — **не** SERVED), `W44-BIOCHEM` и `W44-CONNECTOME`
(DORMANT, serving path — `none`).

## 6. Открытые решения

Реестр — [OPEN-QUESTIONS.md](docs-v2/governance/OPEN-QUESTIONS.md). Блокируют работу сейчас пять:
**Q-W46-01** (параметр согласия деструктивной перезагрузки), **Q-W46-02** (dry-run), **Q-W46-03**
(что может стереть перезагрузка), **Q-W46-07** (`LD_LIBRARY_PATH` и GPU — измеренная разница ×12.6),
**Q-W46-13** (политика хранения журналов). Принятые решения —
[DECISION-REGISTER.md](docs-v2/governance/DECISION-REGISTER.md), риски —
[RISK-REGISTER.md](docs-v2/governance/RISK-REGISTER.md).

## 7. Тесты — только целевые области

Полный прогон `matrix-core` здесь **не запускается** (риск OOM; нужно явное согласие владельца,
`AGENTS.md` §Тестирование). База — области с собственным коммитом, в
[TEST-CATALOGUE.yml](docs-v2/engineering/TEST-CATALOGUE.yml):

```bash
export JAVA_HOME=/home/alexandr-narbaev/jdk25
./gradlew :matrix-core:test --tests "io.matrix.cognitive.*" --tests "io.matrix.knowledge.*" --rerun-tasks
./gradlew :matrix-brain-runtime:test --rerun-tasks
./gradlew :matrix-api-gateway:test --rerun-tasks
bash scripts/quality-gate.sh              # 271 литерал-кандидат на базе 271
bash scripts/security-boundary-check.sh   # 0 нарушений
```

Лимиты машины — [RESOURCE-BUDGETS.md](docs-v2/operations/RESOURCE-BUDGETS.md): 16 Gradle-воркеров,
heap демона 4 g, heap тестового JVM `matrix-core` — 16 g, **два одновременных прогона `matrix-core`
в память не помещаются**, квота `/tmp` — 30 GiB.

## 8. Как работать здесь и где искать историю

Протокол агента (уровни автономии, восстановление после потери контекста, передача смены) —
[AGENT-OPERATING-PROTOCOL.md](docs-v2/governance/AGENT-OPERATING-PROTOCOL.md); нормы —
[CONSTITUTION.md](CONSTITUTION.md) и [AGENTS.md](AGENTS.md). Волны —
[docs-v2/waves/INDEX.md](docs-v2/waves/INDEX.md) (хронологический журнал: 320 записей волн
плюс сам индекс; навигация — по номеру волны). Гипотезы — [HYPOTHESES.md](docs-v2/research/HYPOTHESES.md) и
[HYPOTHESES-NEW.md](docs-v2/research/HYPOTHESES-NEW.md). Раскопки «что MATRIX есть на самом деле» —
[RECON-W44-ARCHAEOLOGY.md](docs-v2/engineering/RECON-W44-ARCHAEOLOGY.md). Реестр документов —
[W46-DOCUMENT-AUDIT.csv](docs-v2/engineering/W46-DOCUMENT-AUDIT.csv) (880 строк). Карта документации
— [docs-v2/INDEX.md](docs-v2/INDEX.md). `WAL.md` в корне **не существует** <!-- no-link: the file was renamed in W40 --> — журналы это
[wal/GLOBAL_WAL.md](wal/GLOBAL_WAL.md) (в git) и [wal/SESSION_WAL.md](wal/SESSION_WAL.md)
(локальный, вне git).

**Стек:** Java 25 · Quarkus 3.38.3 · Avro 1.12.2 · ONNX Runtime 1.29.0 · Testcontainers 1.21.3 ·
ML-DSA (JEP 497) — полная матрица в [STANDARDS-MATRIX.md](docs-v2/engineering/STANDARDS-MATRIX.md).
