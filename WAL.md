# WAL

**Статус: ephemeral.** Переписывается в конце каждой сессии.

## Активный фокус

Полная пересборка документации (v2 rebuild 2026-08-26): новая структура `docs-v2/` (SpecDriven, без историчности), singleton FROZEN CONSTITUTION/AGENTS, INDEX на docs-v2/. Старые документы идут в `docs-v2/archive/2026-08-pre-v2/`.

## Правила сессии

- Singleton FROZEN: `CONSTITUTION.md`, `AGENTS.md` (singleton normative, перезаписываются вместе).
- Все ссылки в новых документах — только на docs-v2/.
- Без историчности и запрещённых формулировок (AGI и т.п.).

## Что сделано (v2 rebuild 2026-08-26)

- `README.md`, `CONSTITUTION.md`, `AGENTS.md` — переписаны singleton.
- `docs-v2/INDEX.md` — единая навигация.
- `docs-v2/architecture/{OVERVIEW,MODULES,RUNTIME-TOPOLOGY,FORMAL-CONTRACTS}.md` — 4 файла.
- `docs-v2/specifications/{INDEX,SPEC-000,001,002,002-quantum,003}.md` — 6 файлов.
- `docs-v2/designs/DESIGN-{01..15}.md` — 15 файлов.
- `docs-v2/research/{HYPOTHESES,PROTOCOL}.md` + `reports/{EXP-002,003,009,010}-report.md` — 6 файлов.
- `docs-v2/engineering/{PLAN,INVARIANTS,STANDARDS-MATRIX,JMH-GATE-EVIDENCE,SDD-COVERAGE,RELEASE-NOTES}.md` — 6 файлов.
- `docs-v2/operations/{RUNBOOK,DEPLOYMENT}.md` — 2 файла.

Итого: 1 README + 2 singleton + 1 INDEX + 4 architecture + 6 specs + 15 designs + 6 research + 6 engineering + 2 operations = **43 файла** в новой структуре.

## Следующее действие

- Архивировать старые документы через `git mv` → `docs-v2/archive/2026-08-pre-v2/`.
- SDD-свип: спеки для топ-`needs-spec` (`reasoning/`, `mediator/`, `hades/`, `memory/`, `rag/`).
- Эксперименты на доменных корпусах (восстановить `models/training_data/` из git-истории точечно).

## Известные проблемы

- GPU EP: «Failed to find CUDA shared provider» в Java-ONNX; нужен системный CUDA 12 + cuDNN9 для onnxruntime_gpu.
- Kafka integration test: флейк метаданных брокера на медленном хосте.
- TLA+-спеки отсутствуют для топ-пакетов (`reasoning`, `mediator`, `hades`, `memory`, `rag`) — в `architecture/FORMAL-CONTRACTS.md` next-format-contracts.