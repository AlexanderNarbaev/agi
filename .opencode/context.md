# Project Context — SESSION CONTINUITY (compaction #48) — DOC REBUILD SOLO

## Ловушки
- Целевые прогоны --tests; LSP ложные; guard блокирует rm/find-delete/git rm. git mv OK.
- Субагенты через Task tool недоступны (модельный резолв падает «not found» несмотря на конфиг) → ВСЁ СОЛО.
- pages.yml публикует narrow set: docs/index*.md, docs/docs*.html, docs/algorithms*.html, docs/benchmarks*.html, docs/research*.html, docs/sandbox.html.
- Последний коммит eb8e028 (запушен).

## Mission
Полная пересборка документации: новая структура в `docs-v2/` + перезапись CONSTITUTION/AGENTS/README (singleton FROZEN), перенос старого в `docs-v2/archive/2026-08-pre-v2/` через `git mv`. Без ссылок на старые пути. Без историчности. Без запрещённых формулировок (AGI и пр.). Inlay rag-system в architecture только без ссылок.

## Структура docs-v2/
INDEX.md
CONSTITUTION.md (singleton FROZEN, переписать как единственный норматив)
AGENTS.md (процедуры сессий)
WAL.md (текущий journal)
README.md → переписать НАВЕРХУ в корне
architecture/{OVERVIEW,MODULES,RUNTIME-TOPOLOGY,FORMAL-CONTRACTS}.md
specifications/{INDEX,SPEC-000..003,SPEC-002-quantum}.md
designs/{DESIGN-01..15}.md (компактно)
research/{HYPOTHESES,PROTOCOL}.md + reports/{EXP-002,003,009,010}-report.md
engineering/{PLAN,INVARIANTS,STANDARDS-MATRIX,JMH-GATE-EVIDENCE,SDD-COVERAGE,RELEASE-NOTES}.md
operations/{RUNBOOK,DEPLOYMENT}.md
archive/2026-08-pre-v2/ — СТАРЫЕ docs/spec, docs/design, docs/engineering, docs/research, docs/science, docs/blog, docs/papers, docs/w32, docs/MATRIX_docs, docs/MATRIX_агентный_пакет, docs/improvements, docs/staging-wave4, docs/vision, docs/agents, docs/superpowers, docs/superpowers/*, AGENTS.md, CONSTITUTION.md (старые), README.md (старый), WAL.md (старый), .opencode/context.md (старый), docs-v2-старый, scripts/.

## Порядок исполнения (минимальные файлы, max строк ~180)
1. README.md (root), CONSTITUTION.md (root), AGENTS.md (root) — ПЕРЕПИСАТЬ; INDEX.md (внутри docs-v2/) — навигация.
2. architecture/* (4 файла).
3. research/* + reports/* (6 файлов).
4. engineering/* (6 файлов).
5. specifications/* (5 файлов).
6. designs/ — 15 файлов КОМПАКТНО (≤ 180 строк каждый) — структура: Что / Почему / Архитектура / Метрики / Реализация (→ docs-v2/architecture/MODULES) / Отложено.
7. operations/* (2 файла).
8. git add -A → commit «WAL: docs-v2 rebuild (SpecDriven, без историчности)».
9. git mv docs/{spec,design,engineering,research,science,blog,papers,w32,vision,agents,superpowers,improvements,staging-wave4,MATRIX_docs,MATRIX_агентный_пакет} docs-v2/archive/2026-08-pre-v2/ — каждый dir отдельно. Commit «archive old docs».
10. git mv {AGENTS.md,CONSTITUTION.md,README.md,WAL.md} docs-v2/archive/2026-08-pre-v2/root/ ТОЛЬКО старые копии? Нет — root файлы уже ПЕРЕЗАПИСАНЫ; старые версии уже в HEAD; если хотим хранить как archive — git checkout HEAD~<последний> -- file? Сложно; проще: оставить только новые в корне, в docs-v2/archive поместить быстрое подмножество через mv свежей копии? Не делать — пусть HEAD-история хранит старые версии.

## Правила
FROZEN-маркеры в СТАРЫХ уступили директиве владельца в этой сессии; новые CONSTITUTION/AGENTS — singleton normative.
Запрещённые claims (AGI и т.п.) избегать; forbidden claims из CONSTITUTION сохранить в новой CONSTITUTION.md.
