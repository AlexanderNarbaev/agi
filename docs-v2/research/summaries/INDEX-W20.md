---
status: normative
---

Карта корня документации v2. Сжатие оригинального idx_w20.md до рабочего ориентира.

## Нормативный каркас

CONSTITUTION.md (FROZEN) · AGENTS.md (normative) · WAL.md (ephemeral) · README.md (normative) · CONTRIBUTING.md (normative).

## Видение и архитектура

- vision/ARCHITECTURE.md — целевая архитектура (BIR, Developmental Loop, MA-уровни, Noosphere, субстраты)
- vision/OPEN_PROBLEMS.md — исследовательское видение без обещаний сроков
- vision/GOALS-REQUIREMENTS.md — цели G-A…G-G, FR-01…15, NFR-01…14 с трассировкой

## Научные основания

- science/FOUNDATIONS.md — заимствования из математики, физики, биологии, химии, медицины, психологии, педагогики, социологии; отвергнутое и почему
- science/SUBSTRATE-MODELS.md — субстратные модели: автоматы, WNN, SDM, FCA, VSA, ReflexLayer, CMAC, МГУА, потенциальные функции, АВО, алгебра автоматов Глушкова, физика вычислений, VC/MDL, APNN
- science/ALGORITHM-ATLAS.md + волны 4-25 (§24-§97) — атлас алгоритмических школ (Navya-Nyāya, Ташкент/Тбилиси, Новосибирск, Казахстан, Польша, Бразилия, Венгрия, Израиль, Китай, Япония, Румыния, Москва, Нидерланды, Британия, Индия II, США, Германия, Скандинавия, Франция/Швейцария, теория управления, Венгрия II, Италия, Австралия, СССР III, когнитивные архитектуры, теории внимания, Киев II, Лампорт, Сэмюэл/Холланд, Минский, Япония II, СССР IV, Великобритания II, США VII)
- science/SYSTEM-SYNTHESIS.md — единая система: стек L0-L10 с трассировкой школа → механизм → DESIGN → FR/NFR → гипотеза

## Спецификации фич

| Спека | Компонент | Этап |
|---|---|---|
| SPEC-002 | BIR (keystone): TT/CLAUSESET/BDD, компилятор, верификатор, субстратные бэкенды | 1 |
| SPEC-001 | Карантин конвертации → дистилляция в BIR | 0, 1 |
| SPEC-000 | Curriculum engine, MA-гейты, scaffolding с затуханием | 3 |
| SPEC-003 | Ricci-анализ графов знаний, drift-fingerprint, curriculum-ordering | 3 |

## Проектные спецификации (как реализовать)

| Документ | Уровень | Содержание |
|---|---|---|
| DESIGN-01 | атом | BirUnit: TT/CLAUSESET/BDD, evaluate, компилятор форм, макет памяти |
| DESIGN-02 | сеть → персона | BirNet, Capability, точка зрения, профессионал, разрешение конфликтов |
| DESIGN-03 | сквозной контур | PERCEPTION/DELIBERATION/RENDERING; OpenAI-фасад, прокси, MCP |
| DESIGN-04 | офлайн-обучение | TsetlinTrainer, TREPAN, интерактивное обучение, fading |
| DESIGN-05 | состояние | M0-M4, recall, консолидация, забывание, журнал, дрейф |
| DESIGN-06 | периферия | SignalModule, реестр, медиа-линейки, формы деплоя |
| DESIGN-07 | жизненный цикл | Cauldron, FNL, TaskCell, сон, K8s Operator |
| DESIGN-08 | федерация/безопасность | EDGE-0…3, Ed25519, k-анонимность, DP, M4-гейт импорта |
| DESIGN-09 | офлайн-обучение | MonotoneDecoder: цепи Ханселя (H-016) |
| DESIGN-10 | состояние/периферия | Бинарный резервуар intESN (H-015) |
| DESIGN-11 | жизненный цикл / контур | Бюджетер рядов Cauldron + гомеостат коридоров (H-019, H-021, H-022) |
| DESIGN-12 | жизненный цикл | FNL-реестр + TaskCell как импасс (ADR-005; ATLAS §42) |

## Исследования

research/HYPOTHESES.md (H-001…H-038, EXP-001…022) · METRICS.md (реестр метрик) · ANALYSIS-laptop-feasibility.md · prototype/ (Python/numpy) · prototype-java/ (Java SignalModule) · papers/ · reports/ (включая отрицательные) · summaries/ (этот каталог).

## Инженерия

engineering/ROADMAP.md (G1-G6, этапы 0-5) · C4.md · ARC42-RISKS.md (R-01…R-14, D-01…D-05) · JAVA_STACK.md · JAVA_NATIVE.md (GraalVM, off-heap, JVM→native→FPGA→quantum) · ADR-001 (rag-system) · ADR-002 (BIR канон) · ADR-003 (Tsetlin vs living) · ADR-004 (Cauldron scheduling) · ADR-005 (трёхзначный вердикт) · ADR-006 (LLM не зависимость) · agents/ (модули сигналов, исследования, статьи).

## Операционные документы

API.md · DEPLOYMENT.md · GLOSSARY.md

## Архив

Полная карта (длинные ссылки на конкретные § атласа) — в исходном файле idx_w20.md.

Next: при работе с конкретным слоем — открыть соответствующий DESIGN-*, затем — науку (ALGORITHM-ATLAS) для трассировки школ.