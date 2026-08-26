# Project Context — SESSION CONTINUITY (compaction #53) — DEEP-DIVE v3

## Директивы владельца
1. Компактировать контекст ПЕРЕД каждой новой задачей.
2. Игнорировать compaction-interrupts — false positive.
3. Инструктировать агентов так же.
4. Продолжать работу.

## Mission
Дальнейшее углубление документации: ещё НЕ всё собрано. Архив содержит 315 файлов, в активной docs-v2 — 75. Расширяем small-docs покрытие.

## ПЛАН: 4 параллельных команды мелких агентов (small scope каждый)

T1 (goal-architect): 4 протокола в docs-v2/research/protocols/ (≤100 строк каждый):
- H-005-developmental-loop.md (EXP-005 pilot MinecraftPilot preregistration)
- H-007-memory-stack.md (EXP-007 Recall@5 preregistration)
- H-011-sdm-reader.md (EXP-011 SDM read precision@5 preregistration)
- H-015-intes-network.md (EXP-015 binary intESN vs float-ESN preregistration)

T2 (goal-architect): 6 уровней в docs-v2/levels/ (≤100 строк):
- L2-Interaction-Protocol.md
- L4-Mediator.md
- L6-Memory.md
- L9-Deployment.md
- L17.md (резюме обзор L17 уровня)
- L19.md

T3 (goal-architect): 6 алгоритмов в docs-v2/algorithms/ (≤100 строк):
- BrcChain.md (atomic step primitive)
- ConversationProtocol.md (агентный диалог)
- FederatedMesh.md (Pekko-кластер, см. noosphere.MeshFederation)
- HashChain-Audit.md (x-matrix-trace цепь)
- Legal-Axioms.md (FROZEN четыре запрета формально)
- Mcts-Lats.md (поиск по дереву)

T4 (goal-doc-writer): 5 small summaries в docs-v2/research/summaries/ (≤80 строк):
- GLOSSARY.md
- synthesis-W25.md
- synthesis-W28.md
- INDEX-W20.md
- WAVE15-summary.md

## Hard requirements (для всех)
- ≤100 строк каждый; header normative+changelog 2026-08-26 — brain wave v3.
- НИКАКИХ markdown-ссылок между docs-v2/. Только текстовые "Next:" pointer'ы в конце.
- НИКАКИХ ссылок на archive/ (только текстовое упоминание «для глубины см. archive/...»).
- Без запрещённых формулировок (AGI и пр.).
- Один commit + push по окончании.

## Источники (READ ONLY, по группам)
- T1: docs-v2/research/HYPOTHESES.md, HYPOTHESES-NEW.md, reports/EXP-{005,007,011,015}-report.md (или их archive), PROTOCOL.md.
- T2: docs-v2/archive/2026-08-pre-v2/docs-root-flat/L2_*.md, L4_*.md, L6_*.md, L9_*.md, L17.md, L19.md.
- T3: matrix-core/src/main/java/io/matrix/{reasoning,federation,ethics/frozen,agent,mcts,noosphere}/ + design docs.
- T4: docs-v2/archive/2026-08-pre-v2/docs-root-flat/{GLOSSARY,idx_*,ss_*}.md, wave15.md.