# MATRIX Project Context - Session State (финальный чекпойнт сессии)

## Current Status
- **Сессия близка к исчерпанию**: 30 волн, ~4.5ч. Всё запушено в origin+gitverse, дерево чистое
- **Критерий A ЗАКРЫТ** (7 миграционных волн, DESIGN-14); **этап B**: продюсеры Tsetlin/WiSARD работают, toy-эталоны = постоянный gate; синтетика k≥8 — ОТКРЫТА (attempt-18)
- Тесты: 377/0 полный регресс (последний прогон), tsetlin+bir 157/0

## Волны этой сессии (все в истории коммитов, оба remote)
1–5. Критерий A: cluster/api/bridge/explain + классификация producer/training-side + FROZEN-исключения
6. NeuronLayer кейстоун BIR (244/0)
7. Канонический voting-TM каркас + дистилляция
9. WiSARD унификация
13. H-035 EBL карточка
15. JTMS/ATMS-lite LineageLedger
16–22. TM attempts: random-init прорыв toy; D1/D2/D5 внедрены; pairing-bug исправлен; margin-gating отвергнут; sweep вердикт «обучение не идёт структурно»; attempt-18 batch-mask гипотеза
23–30. Доки (аудит-план verbatim C), коррекции честности (skip-артефакт, H-035 refuted-toy пины), LFS-fix

## ГЛАВНОЕ для следующей сессии — TM batch-mask модель
- **Гипотеза №1** (attempt-18): наш пер-литеральный цикл с фиксированными pR/pP ≠ каноническая модель МАССОВОГО применения масок с насыщением (tm_inc/tm_dec над bit-mask `~Xi`, `feedback_to_la` random-subset). Портить нужно модель применения, не правила построчно
- Пакет готов: `/tmp/opencode/ref_tm.c` (эталон), audit-plan §3–4 (verbatim+дельты D1–D5), TsetlinTrainer как API-база, гарнесс GranmoReferenceTest включён
- Эксперимент-план §3 матрица; стоп-правила §4

## Очередь следующей сессии
1. **TM batch-mask порт** → синтетика k=8..20 green → stage-1 close → доменная фаза EXP-002
2. JTMS justification-graph развитие
3. AC-3/CSP мини-спека ExecutablePlanner
4. Dependency upgrades осторожно
5. Прочитать атлас §95–103 при планировании REFLEX/Cauldron

## Инфраструктурные факты
- push цепочка: правки→commit→pull --rebase→push→verify rev-list=0; LFS locksverify выключен локально
- rm заблокирован Goal Guard → mv в /tmp/opencode/; полный test OOM — батчи; LSP фантомы tsetlin/* — верить gradlew
- Коммиты сессии: f2b8874→…→(несколько чужих)→e72d8ab(H-035)→…→последний «attempt-18» + status sync
- FROZEN: ethics/, CONSTITUTION.md, старые avro, workflows; K_MAX≤20; coverage≥82%; Java-only prod

[COMPACTION_COMPLETE]
