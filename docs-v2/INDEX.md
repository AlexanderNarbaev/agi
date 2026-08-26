# INDEX — MATRIX Docs v2

Структура пересобрана 2026-08-26 (см. `engineering/RELEASE-NOTES.md`). Только актуальные ссылки; исторические документы — `archive/2026-08-pre-v2/`.

| Раздел | Где |
|---|---|
| **Корень** | `README.md`, `CONSTITUTION.md`, `AGENTS.md`, `WAL.md` |
| **Архитектура** | `architecture/{OVERVIEW,MODULES,RUNTIME-TOPOLOGY,FORMAL-CONTRACTS}.md` |
| **Спецификации** | `specifications/{INDEX,SPEC-000..003,SPEC-002-quantum}.md` |
| **Дизайны** | `designs/DESIGN-01..15.md` |
| **Исследования** | `research/{HYPOTHESES,PROTOCOL}.md` · `research/reports/EXP-{002,003,009,010}-report.md` |
| **Наука** | `science/{SUBSTRATE-MODELS,FOUNDATIONS,GOALS-REQUIREMENTS,OPEN-PROBLEMS,ALGORITHM-ATLAS-INDEX}.md` · полные ALGORITHM-ATLAS §1-§112 в архиве |
| **Инженерия** | `engineering/{PLAN,INVARIANTS,STANDARDS-MATRIX,JMH-GATE-EVIDENCE,SDD-COVERAGE,RELEASE-NOTES}.md` |
| **Операции** | `operations/{RUNBOOK,DEPLOYMENT}.md` |

## Ключевые цифры (см. отчёты)

- **H-010 accepted**: WiSARD быстрее Tsetlin в **242×** (медиана, 9 прогонов), точность 9/9.
- **H-002/H-003 refuted-toy**: GA быстрее ×5–10, точнее на синтетике.
- **EXP-009C GPU нога**: дистиллят BIR ×149 быстрее ONNX-CPU при fidelity 0.999; GPU per-call ×276 медленнее BIR на точечных решениях.
- **JMH-гейт Batch\***: 32–69M ops/s → решение «оставить как есть».
