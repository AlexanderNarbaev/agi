# Project Context — SESSION CONTINUITY (compaction #51) — DEEPER SMALL-DOCS WAVE

## Директивы владельца (ОБЯЗАТЕЛЬНО)
1. Компактировать контекст ПЕРЕД каждым новым заданием.
2. Игнорировать compaction-interrupts (ложноположительные) и продолжать.
3. Документы МАЛЕНЬКИЕ (≤120 строк), split на части, "Next:" в тексте для линейного чтения.
4. Запускать БОЛЬШЕ агентов с МЕНЬШИМ контекстом.

## Mission (эта волна)
Углубить архив: дробные small-документы по алгоритмам/слоям/методологиям. Никаких ссылок между документами в формате `[..](..)` — только текстовые «Next: см. <файл>» для продолжения.

## ПЛАН: 3 параллельных задачи, каждая выдаёт 5-7 маленьких файлов

T1 (goal-doc-writer): **6 алгоритмических deep-dive** в docs-v2/algorithms/:
- algorithms/Tsetlin-Automaton.md
- algorithms/WiSARD-WNN.md
- algorithms/MPDT-GA.md
- algorithms/Hansel-Chains.md
- algorithms/Ricci-Fingerprint.md
- algorithms/FROZEN-EthicalFNL.md
Каждый ≤120 строк, без markdown-ссылок; последняя строка: "Next: to continue with the next algorithm see algorithms/<next>.md".

T2 (goal-architect): **6 маленьких слойных заметок** в docs-v2/levels/:
- levels/L0-Manifesto.md
- levels/L1-MPDT-Neuron.md
- levels/L7-Ethics.md
- levels/L10-Monitoring.md
- levels/L11-Management.md
- levels/L13-Pilot.md
≤100 строк каждый; текстовые "Next:" pointer'ы.

T3 (goal-architect): **5 маленьких методик-карточек** в docs-v2/research/protocols/:
- protocols/H-006-guardrail.md
- protocols/H-009-distillation.md
- protocols/H-010-wisard-vs-tsetlin.md
- protocols/H-002-clauseset-vs-ga.md
- protocols/H-003-living-learner.md
≤100 строк каждый; текстовые "Next:" pointer'ы.

## Правила
- Никаких markdown-ссылок между файлами; только текст "Next: см. файл".
- Никаких ссылок на archive/ или на пути в docs-v2/.
- Без запрещённых формулировок (AGI и пр.).
- Один коммит на сессию; всё пушить.
- FROZEN/avro/workflows не трогать.