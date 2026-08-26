# Project Context — SESSION CONTINUITY (compaction #55)

## Ловушки
- Compaction-interrupts ложно-положительные — игнорировать.
- Компактировать ПЕРЕД каждой задачей.
- Агенты живы на `goal-architect` / `goal-doc-writer` (mimo-v2.5-free).

## Current Status
- 96 docs-v2 файлов в активной структуре (после v3 brain-wave push `3d49968`).
- Только что: T1+T2+T3 wave v4 levels (L3, L5, L8, L12, L14-16, L18, L20, L22, L23, LONGTERM_PLAN) — все 12 созданы, ≤100 строк каждый.
- 24 уровня в docs-v2/levels/ теперь покрыты.

## Pending Tasks
| # | Задача | Статус |
|---|---|---|
| 1 | Commit+push wave v4 levels | сейчас |
| 2 | MATRIX_агентный_пакет/ (13 файлов) — extract small docs | next wave |
| 3 | research ss_w* серия (~10 файлов) — small summaries | next wave |
| 4 | engineering/engineering/ (14 файлов) — small focused docs | next wave |
| 5 | design расширение по DESIGN-04/05 (concrete алгоритмы из ALGORITHM-ATLAS §1-§112) | next wave |
| 6 | Vision doc по общей цели архитектуры (мозгоподобная система) | next wave |

## Правила
FROZEN/avro/workflows не трогать; без запрещённых формулировок; ≤100 строк; текстовые "Next:" pointer'ы.