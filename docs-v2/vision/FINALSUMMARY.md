**Статус: normative · singleton** · пересмотр 2026-08-26 (brain wave v5).

# FINALSUMMARY — текущее состояние проекта

## Что реализовано (измеримое)

- **BIR-слой**: компилятор + 3 формы (TT / CLAUSESET / BDD) + JvmSimd-бэкенд + Fpga-бэкенд; 37 сайтов мигрированы с легаси-вычислений в BIR-путь; страж INV-1 в CI без внешних deps.
- **Продюсеры**: TsetlinTrainer (этап B SPEC-002 FR-B1/B2), WisardProducer, MpdtGaProducer (baseline).
- **Federation**: ElspChannel (Ed25519), ElspChannelMlDsa (ML-DSA, JEP 497 native — постквант без внешних deps).
- **Curriculum**: 12 классов в `devloop/` (CompetenceAssessor-EWMA, CurriculumEngine-ZPD, MaturityGateKeeper MA-0..MA-5).
- **Lifecycle**: CauldronProtocol, FnlGate (SHADOW→CANDIDATE→PROMOTED), ConsolidationCycle, PlanRunner Hoare, PlanPreprocessor AC-3.
- **Topology**: ktopo/ (Ricci curvature, drift fingerprint 24 bins, Wasserstein-1 closed-form, curriculum-ordering).
- **Knowledge**: BirClassifier, Distiller, OnnxActivationTeacher (ONNX Runtime 1.29.0).

## Эксперименты (с реальными цифрами)

- **H-010 accepted (synthetic-scope)**: WiSARD vs Tsetlin, 9 прогонов, median speedup 242.43×, WiSARD 9/9 по точности.
- **H-002 / H-003 refuted-toy**: GA в среднем ×5.5 быстрее Tsetlin, точнее +7.9 п.п., компактнее ×7500; 3 датасета × 3 seeds протокол сходимости GA to99 за 346 vs Tsetlin 673.
- **EXP-009B/C**: дистиллят BIR ×149 быстрее ORT-CPU при fidelity .999 на синтетическом FFN16; GPU нога (RTX 5070 Ti, torch cu130) GPU 0.02мс батч / 17.25µс per-call vs BIR ~62нс eval (MATRIX ×276 быстрее GPU на точечных).
- **JMH-гейт Batch***: 32–69M ops/s; решение «оставить как есть».

## Документация (docs-v2)

118 small-docs в следующих разделах:
- **Корень**: `README.md`, `CONSTITUTION.md` (singleton FROZEN), `AGENTS.md` (singleton FROZEN), `WAL.md`.
- **INDEX.md** — единая навигация.
- **architecture/**: OVERVIEW, MODULES, RUNTIME-TOPOLOGY, FORMAL-CONTRACTS + 4 REQUEST-документа brain-wave v1.
- **specifications/**: SPEC-000..007 (последние 4 — brain wave v1).
- **designs/**: DESIGN-01..19 + 5 design-drafts (brain wave v5).
- **research/**: HYPOTHESES, HYPOTHESES-NEW, PROTOCOL + 5 reports (002,003,005,006,009,010,011,015 protocols) + 5 summaries.
- **engineering/**: PLAN, INVARIANTS, STANDARDS-MATRIX, JMH-GATE-EVIDENCE, SDD-COVERAGE, RELEASE-NOTES.
- **operations/**: RUNBOOK, DEPLOYMENT + 5 ops-drafts (brain wave v5).
- **science/**: SUBSTRATE-MODELS, FOUNDATIONS, GOALS-REQUIREMENTS, OPEN-PROBLEMS, ALGORITHM-ATLAS-INDEX.
- **algorithms/**: 12 small-docs (Tsetlin, WiSARD, GA, Hansel, Ricci, FROZEN-EthicalFNL, BrcChain, ConversationProtocol, FederatedMesh, HashChain-Audit, Legal-Axioms, Mcts-Lats).
- **levels/**: 24 уровня L0..LONGTERM_PLAN.
- **vision/**: BRAIN-LIKE-SYSTEM.md, FINALSUMMARY.md (этот).

Все документы ≤ 120 строк, без markdown-ссылок между файлами, с текстовыми «Next:» pointer'ами.

## Стек (актуальный)

Java 25 · Quarkus 3.38.3 · GraalVM plugin 1.1.10 · Avro 1.12.2 · ONNX Runtime 1.29.0 · Kafka-clients 4.3.1 · Testcontainers 1.21.3 · Postquantum ML-DSA (JEP 497 native).

## Открытые фронты (next sessions)

| Категория | Задача | Блокер |
|---|---|---|
| Real-LLM | экспорт `.onnx` для EXP-009 H-009 | python-тулчейн + веса Qwen-0.6B FFN |
| Domain corpora | EXP-002/003 production verdict | данные |
| Energy gate | H-009 10⁴× гейт | wattmeter |
| TLA-спек-кандидаты | BRC-Step, Memory-M4-Causal, ConjugateBudgeter-DP, MCTS-LATS-Visit | отдельная SDD-волна |
| Полные цепи Ханселя | DESIGN-09 v2 | research wave |
| Квантовый FR-D3 | SPEC-002-quantum | субстрат |
| FPGA-синтез | yosys/nextpnr | инфраструктура |
| Audio-events этап 3 | DESIGN-06 | плановое |

## Документация и респективность

Проект полностью возобновляем через:
- `INDEX.md` (единая навигация `docs-v2/`).
- `vision/FINALSUMMARY.md` (этот документ).
- `vision/BRAIN-LIKE-SYSTEM.md` (архитектура-нарратив).
- `CONSTITUTION.md` / `AGENTS.md` (singleton FROZEN).
- `WAL.md` (текущий снапшот сессии).

Архив полностью сохранён в `docs-v2/archive/2026-08-pre-v2/` (315 файлов, `git mv` с историей).
