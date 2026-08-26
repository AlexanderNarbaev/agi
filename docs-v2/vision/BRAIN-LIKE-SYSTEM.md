**Статус: normative · singleton** · пересмотр 2026-08-26 (brain wave v5).

# BRAIN-LIKE SYSTEM — архитектурный синтез MATRIX

Этот документ объединяет все ныне написанные small-docs в одну картину мозгоподобной системы. После него — линейное чтение через текстовые указатели «Next:».

## Что

MATRIX — детерминированная нейро-символическая система, организованная как мозг: три столпа (perception → consciousness/deliberation → action) лежат на одном субстрате памяти (short → long → subconscious); автономия обеспечивается внутренними импульсами (curiosity, consolidation, integrity, share); централизация в FROZEN-этическом ядре дополняется децентрализацией (neuron-блоки, обезличенные digest'ы в общий пул); ни одна часть не имеет права нарушать четыре запрета.

## Анатомия (три столпа)

- **Perception** (столп 1): сенсорный прокси → бинаризация → энкодеры. См. файл `specifications/SPEC-004-perception.md` для формального контракта sensor packet и `designs/DESIGN-16-perception-federation.md` для топологии federated-perception.
- **Consciousness / Deliberation** (столп 2): BRC chain + MCTS/LATS + AC-3 preconditions + top-down attention от autonomy-impulses и bottom-up saliency от perception. См. файлы `specifications/SPEC-006-consciousness-deliberation.md`, `designs/DESIGN-18-consciousness-loop.md`, и алгоритмы `algorithms/BrcChain.md` плюс `algorithms/Mcts-Lats.md`.
- **Action** (столп 3): иерархия effectors с ethics-гейтом FROZEN перед каждым действием. См. файлы `specifications/SPEC-005-action.md`, `designs/DESIGN-17-action-arena.md`, плюс `algorithms/Legal-Axioms.md` и `algorithms/HashChain-Audit.md`.

## Субстрат памяти (расширенный DESIGN-05)

Иерархия m0, M0, M1, M2, M3, M4, M5, M5+ с латентностями и критериями эвикции. См. `architecture/REQUEST-memory-hierarchy.md` + `specifications/SPEC-007-subconscious.md` + `designs/DESIGN-05-memory.md` (existing) + `designs/DRAFT-SDM-Recurrent.md` + `designs/DRAFT-MemoryM4.md` (drafts future).

## Центральное FROZEN-ядро

Четыре запрета реализованы в `ethics/frozen/FrozenEthicalFNL.java` (соответствует TLA+ `FrozenEthicalFNL` / `BotEthicsPipeline` формальным спек-каркасам; см. `architecture/FORMAL-CONTRACTS.md`). Никакая абстракция контура — ни восприятие, ни рефлексия, ни действие, ни автономный импульс, ни децентрализованный digest — не имеет права обойти FROZEN-ядро. Если этика запрещает — действие не происходит.

## Автономные импульсы

Четыре внутренних импульса: curiosity, consolidation, integrity-check, share-digest. Бюджетируются через `budgeter/ConjugateBudgeter` (см. `algorithms/MPDT-GA.md`, `designs/DESIGN-11-budgeter.md`). См. `architecture/REQUEST-autonomy-impulses.md`.

## Децентрализация

Каждый блок neuron-агентов накапливает локальный контекст у пользователя; только k-anonymous + DP-noised digest'ы (через `federation/Anonymizer`) идут в общий пул — `noosphere/MeshFederation` + подпись `federation/ElspChannelMlDsa` (постквант ML-DSA). См. `architecture/REQUEST-decentralized-digests.md`.

## Линейное чтение (без markdown-ссылок; следующий файл указан после каждого блока)

После этого общего синтеза продолжить чтение в порядке:

1. Каталоговая: `INDEX.md`.
2. Корень: `CONSTITUTION.md`, `AGENTS.md`.
3. Архитектура: `architecture/OVERVIEW.md`, `architecture/MODULES.md`, `architecture/RUNTIME-TOPOLOGY.md`, `architecture/FORMAL-CONTRACTS.md`, четыре REQUEST-документа.
4. Спецификации: каждая `specifications/SPEC-NNN-*.md` по очереди по INDEX.
5. Дизайны: каждый `designs/DESIGN-NN-*.md` по INDEX.
6. Алгоритмы: каждый `designs/drafts/Design-DRAFT-*.md` (будущее), и компактные `algorithms/*.md`.
7. Уровни L-вдохновения: каждый `levels/*.md` в нумерации.
8. Исследования: `research/HYPOTHESES.md`, `research/HYPOTHESES-NEW.md`, по протоколам.
9. Отчёты: `research/reports/EXP-*.md`, плюс `summaries/*.md`.
10. Инженерия: `engineering/{PLAN,INVARIANTS,STANDARDS-MATRIX,JMH-GATE-EVIDENCE,SDD-COVERAGE,RELEASE-NOTES}.md`.
11. Операции: `operations/{RUNBOOK,DEPLOYMENT}.md`, плюс `operations/drafts/*.md`.
12. Подсчёт состояния: `vision/FINALSUMMARY.md`.

**Next:** для следующего шага прочитать `INDEX.md` — единая карта docs-v2/.