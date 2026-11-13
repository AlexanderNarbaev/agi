# INDEX — MATRIX Docs v2

**Статус:** `ACTIVE` · **Дата:** 2026-11-04 · **Волна:** W46.2 (задача T2)
**Что это:** оглавление **разделов** дерева `docs-v2`, а не второй реестр документов. Перечисление
всех 849 документов здесь было бы вторым реестром — и таким, который разойдётся с деревом.
Поэтому здесь разделы, а документы перечислены там, где они нужны поштучно.

> **Полный реестр — [docs-v2/engineering/W46-DOCUMENT-AUDIT.csv](engineering/W46-DOCUMENT-AUDIT.csv):**
> 893 строки, по одной на документ, с колонками `path`, `category`, `status`,
> `current_or_historical`, `wave`, `last_meaningful_update`, `known_stale_reference`,
> `successor`. Из них **862** — под `docs-v2/`. Реестр сделан в W46.0; волна W53 (распределённое
> когнитивное исполнение) внесла в него **свои 12 документов и новую строку
> `docs-v2/waves/INDEX.md`** (`CANONICAL`) — 13 строк, — и переклассифицировала `WAL-021` из `ACTIVE` в
> `HISTORICAL` — это отчёт о завершённой волне W32, как и все 319 его соседей.
> **Оставшийся долг — 69 markdown-документов** под `docs-v2/`, которых нет в реестре
> (реестр ведёт **markdown-документы**; машинно-читаемые артефакты волны — JSON — в него не
> входят по его же соглашению): governance-набор
> (11), отчёты инженерных волн W46.0–W52 (41), исследования (6), спецификации W47 (3),
> операции (2), демонстрация (4), архитектура (1), дизайн W47 (1). Это **унаследованный** долг:
> обязательство «реестр документов остаётся согласованным» стоит в ROADMAP §9, и ни одна из
> волн, создавших эти документы, его не закрыла. Волна W53 закрыла **свою** часть и назвала
> остальное.
> **Каноническая навигация — [governance/ROADMAP.md](governance/ROADMAP.md) плюс
> governance-набор ниже.**

---

## 1. Канонические документы — читать первыми

| тема | документ |
|---|---|
| текущее состояние проекта | [governance/PROJECT-STATE.md](governance/PROJECT-STATE.md) |
| дорожная карта и задачи | [governance/ROADMAP.md](governance/ROADMAP.md) |
| принятые решения | [governance/DECISION-REGISTER.md](governance/DECISION-REGISTER.md) |
| риски | [governance/RISK-REGISTER.md](governance/RISK-REGISTER.md) |
| открытые вопросы | [governance/OPEN-QUESTIONS.md](governance/OPEN-QUESTIONS.md) |
| протокол агента | [governance/AGENT-OPERATING-PROTOCOL.md](governance/AGENT-OPERATING-PROTOCOL.md) |
| статусы документов (словарь) | [governance/DOCUMENT-STATUS-POLICY.md](governance/DOCUMENT-STATUS-POLICY.md) |
| возможности (машиночитаемо) | [architecture/CAPABILITY-REGISTRY.yml](architecture/CAPABILITY-REGISTRY.yml) |
| области тестов и база | [engineering/TEST-CATALOGUE.yml](engineering/TEST-CATALOGUE.yml) |
| лимиты машины и ресурсов | [operations/RESOURCE-BUDGETS.md](operations/RESOURCE-BUDGETS.md) |

---

## 2. Как навигировать

1. **Состояние и что делать дальше** — `PROJECT-STATE.md`, затем `ROADMAP.md`.
2. **Что система умеет на самом деле** — `architecture/CAPABILITY-REGISTRY.yml`: четыре оси
   (реализовано / встроено / проверено / работает сейчас). `SERVED` допустимо только при
   названном `serving_path`.
3. **Что было сделано** — `docs-v2/waves/INDEX.md` (хронологический журнал волн).
4. **Что изучалось** — `docs-v2/research/RESEARCH-INDEX.md` и
   `docs-v2/research/NEGATIVE-RESULTS-INDEX.md`.
5. **Полный перечень** — реестр `W46-DOCUMENT-AUDIT.csv` (см. врезку выше).
6. **Формальные контракты и карта реализации** — `formal/spec-implementation-map.yml`
   и `architecture/FORMAL-CONTRACTS.md`.

---

## 3. Разделы `docs-v2`

| Раздел | Где | Что внутри | Документов | Статусы |
|---|---|---|---|---|
| Governance | `governance/` | канонические документы проекта | 11 | CANONICAL |
| Архитектура | `architecture/` | карта возможностей, топология, ADR, заявки на изменение | 21 | 19 ACTIVE, 2 CANONICAL |
| Инженерия | `engineering/` | карты W44–W53, отчёты волн, инварианты, план | 82 | 79 ACTIVE, 2 CANONICAL, 1 NORMATIVE |
| Операции | `operations/` | раннбук, развёртывание, лимиты, параметры настройки | 14 | ACTIVE |
| Спецификации | `specifications/` | SPEC-000… и её наследники | 17 | 10 NORMATIVE, 7 ACTIVE |
| Дизайны | `designs/` | проектные решения DESIGN-01…84 (+ `designs/drafts/`) | 89 | 19 NORMATIVE, 70 ACTIVE |
| Исследования | `research/` | гипотезы, отчёты, протоколы, сводки (+ `reports/`, `protocols/`, `summaries/`) | 182 | 169 RESEARCH, 6 ACTIVE, 5 NORMATIVE, 1 REFERENCE, 1 UNVERIFIED |
| Волны | `waves/` | хронологический журнал волн | 321 | 320 HISTORICAL, 1 CANONICAL |
| Уровни | `levels/` | нормативные документы по уровням | 24 | NORMATIVE |
| Парадигма | `paradigm/` | рамка проекта | 9 | 8 NORMATIVE, 1 ACTIVE |
| Наука | `science/`, `science-v2/` | субстратные модели, основания, открытые проблемы | 9 | 5 RESEARCH, 3 ACTIVE, 1 NORMATIVE |
| Экосистема | `ecosystem/`, `ru/ecosystem/` | внешний вид MATRIX: API, SDK, архитектура, xAI | 36 | ACTIVE |
| Алгоритмы | `algorithms/` | описания алгоритмов | 12 | ACTIVE |
| Прочее | `quality/`, `releases/`, `archive/`, `story/`, `guide/`, `templates/`, `vision/`, `proposals/`, `security/`, `specs/` | качество, релизы, архив, нарратив, гайды, предложения | 40 | 25 ACTIVE, 8 HISTORICAL, 4 ARCHIVED, 2 UNVERIFIED, 1 NORMATIVE |
| Корневые файлы `docs-v2/` | `SUMMARY.md`, `STYLE-GUIDE.md`, `TRANSFORMATION-PLAN.md`, `DEPLOYMENT-REPORT.md`, `index.md` | сводка, стиль, план трансформации, отчёт о развёртывании, страница сайта | 6 | ACTIVE |

Счётчики — из `W46-DOCUMENT-AUDIT.csv`; `governance/` (11 документов) в реестр ещё не попал
(см. врезку в шапке) — это унаследованный долг, а не ошибка этого реестра.

---

## 4. Крупные каталоги

| Каталог | Что это | Файлов | Статус | Свой индекс |
|---|---|---|---|---|
| `docs-v2/waves/` | журнал волн: по файлу на волну, от WAL-001 до W46 | 321 | 320 HISTORICAL, 1 CANONICAL | [waves/INDEX.md](waves/INDEX.md) |
| `docs-v2/research/` | тело исследований: регистры гипотез, отчёты экспериментов, протоколы, сводки | 200 | 169 RESEARCH (в реестре 182 документа) | [research/RESEARCH-INDEX.md](research/RESEARCH-INDEX.md), [research/NEGATIVE-RESULTS-INDEX.md](research/NEGATIVE-RESULTS-INDEX.md) |
| `docs-v2/designs/` | проектные решения DESIGN-01…84, включая черновики | 90 | 19 NORMATIVE, 70 ACTIVE (в реестре 89 документов) | по имени файла `DESIGN-NN-…` |
| `docs-v2/levels/` | нормативные документы по уровням | 24 | NORMATIVE | по имени файла |
| `docs-v2/_raw/` | архив pre-v2 дерева; **в реестр не входит** (аудит исключает pre-v2 архив) | 316 | ARCHIVED | только для исторической справки |

---

## 5. Исторические волны — ссылки, которые остаются

Семейства волн и документы, к которым они относятся. Каталоги `waves/` и `research/`
перечислены поштучно в своих индексах; здесь — только те, что нужны для чтения.

| семейство | документы |
|---|---|
| W31 — доктрина междисциплинарных исследований | [research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md](research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md), [designs/DESIGN-54-hdc-bitnet-hybrid-brain.md](designs/DESIGN-54-hdc-bitnet-hybrid-brain.md), [DESIGN-55-russian-asian-cybernetics-integration.md](designs/DESIGN-55-russian-asian-cybernetics-integration.md), [DESIGN-56-fuzzy-bit-continuous-relaxation.md](designs/DESIGN-56-fuzzy-bit-continuous-relaxation.md), [DESIGN-57-nyaya-4-logic.md](designs/DESIGN-57-nyaya-4-logic.md), [DESIGN-58-capability-levels-roadmap.md](designs/DESIGN-58-capability-levels-roadmap.md), [DESIGN-59-nca-brain.md](designs/DESIGN-59-nca-brain.md), [research/summaries/W31-CROSS-DISCIPLINARY-SYNTHESIS.md](research/summaries/W31-CROSS-DISCIPLINARY-SYNTHESIS.md) |
| W41 — проверка реальности и доказательное рассуждение | [engineering/W41-REALITY-AUDIT.md](engineering/W41-REALITY-AUDIT.md), [engineering/W41-LOCAL-ALPHA-V0.2.md](engineering/W41-LOCAL-ALPHA-V0.2.md), [designs/DESIGN-70-evidence-grounded-reasoning.md](designs/DESIGN-70-evidence-grounded-reasoning.md), [research/W41-RESEARCH-A-LEARNING-SCIENCE.md](research/W41-RESEARCH-A-LEARNING-SCIENCE.md), [W41-RESEARCH-B-COGNITIVE-SYSTEMS.md](research/W41-RESEARCH-B-COGNITIVE-SYSTEMS.md), [W41-RESEARCH-C-KNOWLEDGE-ENGINEERING.md](research/W41-RESEARCH-C-KNOWLEDGE-ENGINEERING.md) |
| W51 — когнитивный рантайм, мост между цепями, плагины, граница Java/C | [engineering/W51-AUDIT.md](engineering/W51-AUDIT.md), [engineering/W51-ARCHITECTURE-MAP.md](engineering/W51-ARCHITECTURE-MAP.md), [engineering/W51-STATUS.md](engineering/W51-STATUS.md), [engineering/W51-JAVA-C-BOUNDARY.md](engineering/W51-JAVA-C-BOUNDARY.md),
       [engineering/W51-PERFORMANCE.md](engineering/W51-PERFORMANCE.md),
       [engineering/W51-EPISTEMIC-EVALUATION.md](engineering/W51-EPISTEMIC-EVALUATION.md), [engineering/ADR-W51-01-cognitive-runtime-and-bridge.md](engineering/ADR-W51-01-cognitive-runtime-and-bridge.md) |
| W51–W80 — метрики интеграции и сводки | [research/W51-W74-COMPREHENSIVE-SUMMARY.md](research/W51-W74-COMPREHENSIVE-SUMMARY.md), [research/W60-W64-DEEP-RESEARCH-SYNTHESIS.md](research/W60-W64-DEEP-RESEARCH-SYNTHESIS.md), [research/W69-W72-INTEGRATION-METRICS-SYNTHESIS.md](research/W69-W72-INTEGRATION-METRICS-SYNTHESIS.md), [research/W80-FINAL-SYNTHESIS-REPORT.md](research/W80-FINAL-SYNTHESIS-REPORT.md) |
| W87–W103 — Φ-семейство, кибернетика, советская/азиатская школы | [research/W87-W91-FINAL-SYNTHESIS-REPORT.md](research/W87-W91-FINAL-SYNTHESIS-REPORT.md), [research/W87-W99-FINAL-SYNTHESIS-REPORT.md](research/W87-W99-FINAL-SYNTHESIS-REPORT.md), [research/W95-CYBERNETIC-RESEARCH-REPORT.md](research/W95-CYBERNETIC-RESEARCH-REPORT.md), [research/W103-SOVIET-ASIAN-RESEARCH-REPORT.md](research/W103-SOVIET-ASIAN-RESEARCH-REPORT.md) |
| W92–W94 — контролируемая стохастичность и когнитивные ошибки | [designs/DESIGN-64-controlled-stochasticity.md](designs/DESIGN-64-controlled-stochasticity.md), [designs/DESIGN-60-deep-research-wave.md](designs/DESIGN-60-deep-research-wave.md), [DESIGN-61-integration-metrics.md](designs/DESIGN-61-integration-metrics.md), [DESIGN-62-pattern-generator.md](designs/DESIGN-62-pattern-generator.md), [DESIGN-63-tickling-noise.md](designs/DESIGN-63-tickling-noise.md) |
| Архитектура и контракты | [architecture/OVERVIEW.md](architecture/OVERVIEW.md), [architecture/MODULES.md](architecture/MODULES.md), [architecture/RUNTIME-TOPOLOGY.md](architecture/RUNTIME-TOPOLOGY.md), [architecture/FORMAL-CONTRACTS.md](architecture/FORMAL-CONTRACTS.md), [architecture/REQUEST-autonomy-impulses.md](architecture/REQUEST-autonomy-impulses.md), [REQUEST-brain-overview.md](architecture/REQUEST-brain-overview.md), [REQUEST-decentralized-digests.md](architecture/REQUEST-decentralized-digests.md), [REQUEST-memory-hierarchy.md](architecture/REQUEST-memory-hierarchy.md) |
| Инженерные нормы | [engineering/INVARIANTS.md](engineering/INVARIANTS.md), [engineering/PLAN.md](engineering/PLAN.md), [engineering/STANDARDS-MATRIX.md](engineering/STANDARDS-MATRIX.md), [engineering/JMH-GATE-EVIDENCE.md](engineering/JMH-GATE-EVIDENCE.md), [engineering/SDD-COVERAGE.md](engineering/SDD-COVERAGE.md), [engineering/MUTATION-REPORT.md](engineering/MUTATION-REPORT.md), [engineering/TLA-STATUS.md](engineering/TLA-STATUS.md), [engineering/RELEASE-NOTES.md](engineering/RELEASE-NOTES.md), [engineering/RISK-REGISTER.md](engineering/RISK-REGISTER.md) |
| Операции | [operations/RUNBOOK.md](operations/RUNBOOK.md), [operations/DEPLOYMENT.md](operations/DEPLOYMENT.md) |
| Спецификации и наука | [specifications/INDEX.md](specifications/INDEX.md), [specifications/SPEC-004-perception.md](specifications/SPEC-004-perception.md), [science/SUBSTRATE-MODELS.md](science/SUBSTRATE-MODELS.md), [science/FOUNDATIONS.md](science/FOUNDATIONS.md), [science/GOALS-REQUIREMENTS.md](science/GOALS-REQUIREMENTS.md), [science/OPEN-PROBLEMS.md](science/OPEN-PROBLEMS.md), [science/ALGORITHM-ATLAS-INDEX.md](science/ALGORITHM-ATLAS-INDEX.md) |
| W53 — распределённое когнитивное исполнение | [engineering/W53-STATUS.md](engineering/W53-STATUS.md), [engineering/W53-AUDIT.md](engineering/W53-AUDIT.md), [engineering/W53-ARCHITECTURE-MAP.md](engineering/W53-ARCHITECTURE-MAP.md), [engineering/ADR-W53-01-cognitive-task-bridge.md](engineering/ADR-W53-01-cognitive-task-bridge.md), [engineering/W53-EPISTEMIC-COMPARISON.md](engineering/W53-EPISTEMIC-COMPARISON.md), [engineering/W53-PERFORMANCE.md](engineering/W53-PERFORMANCE.md), [designs/DESIGN-84-distributed-cognitive-bridge.md](designs/DESIGN-84-distributed-cognitive-bridge.md), [research/W53-RESEARCH-A-DURABLE-EXECUTION.md](research/W53-RESEARCH-A-DURABLE-EXECUTION.md), [research/W53-RESEARCH-B-CYBERNETICS.md](research/W53-RESEARCH-B-CYBERNETICS.md), [research/W53-RESEARCH-C-COOPERATIVE-REASONING.md](research/W53-RESEARCH-C-COOPERATIVE-REASONING.md), [research/W53-RESEARCH-D-RELIABILITY-CALIBRATION.md](research/W53-RESEARCH-D-RELIABILITY-CALIBRATION.md), [engineering/W53-PERFORMANCE-VERIFICATION.json](engineering/W53-PERFORMANCE-VERIFICATION.json) |
| W53 — волна процедурной памяти (первая волна с этим номером) | [engineering/W53-PROCEDURE-MEMORY-STATUS.md](engineering/W53-PROCEDURE-MEMORY-STATUS.md) |
| W47 — четыре предмета, потерявшие документы (OD-031) | [specifications/SPEC-015-autonomy-impulses.md](specifications/SPEC-015-autonomy-impulses.md), [specifications/SPEC-016-decentralized-digests.md](specifications/SPEC-016-decentralized-digests.md), [specifications/SPEC-017-hierarchical-memory.md](specifications/SPEC-017-hierarchical-memory.md), [designs/DESIGN-73-consolidation-cycle.md](designs/DESIGN-73-consolidation-cycle.md), [engineering/W47-WORKSPACE-REPLACEMENT-CONTRACT.md](engineering/W47-WORKSPACE-REPLACEMENT-CONTRACT.md) |

---

## 6. Ключевые числа — только с источником

| число | источник |
|---|---|
| 58 734 913 ops/s на `evaluateAll32` (~17 нс/элемент) — JMH-гейт, 20 итераций, режим `thrpt` | [engineering/JMH-GATE-EVIDENCE.md](engineering/JMH-GATE-EVIDENCE.md) |
| H-010 (WiSARD против Tsetlin): **accepted (synthetic-scope)**, median 242×, 9/9 по точности | [research/HYPOTHESES.md](research/HYPOTHESES.md) |
| H-002 и H-003: **refuted-toy** | [research/HYPOTHESES.md](research/HYPOTHESES.md) |
| EXP-009C воспроизведён 2026-11-04 в пределах 1.4 % — но на **torch**; на ONNX-пути CUDA-провайдер не загружался, и эти числа не переносятся на текущий рантайм | [research/RESEARCH-INDEX.md](research/RESEARCH-INDEX.md) |
| «GPU ускоряет MATRIX» — **не подтверждено**: строка `W46-GPU` в реестре имеет статус `DORMANT` | [architecture/CAPABILITY-REGISTRY.yml](architecture/CAPABILITY-REGISTRY.yml) |
| тестовая база — по областям, не одним числом; полный прогон `matrix-core` требует разрешения владельца | [engineering/TEST-CATALOGUE.yml](engineering/TEST-CATALOGUE.yml) |

---

## 7. Документы вне `docs-v2`

| | |
|---|---|
| вход в проект | [README.md](../README.md) |
| нормы | [../CONSTITUTION.md](../CONSTITUTION.md), [../AGENTS.md](../AGENTS.md) |
| журналы работ | [../wal/GLOBAL_WAL.md](../wal/GLOBAL_WAL.md) (в git), [../wal/SESSION_WAL.md](../wal/SESSION_WAL.md) (локальный, вне git) |
| формальные спецификации | [../formal/](../formal/) |
| скрипты | [../scripts/](../scripts/) |

> **`WAL.md` в корне репозитория не существует** <!-- no-link: the file was renamed in W40 --> — файл был переименован (W40). Журналы это
> `wal/GLOBAL_WAL.md` и `wal/SESSION_WAL.md`.
