# MATRIX Project Context - Session State

## Current Status
- **Сессия**: непрерывная, ~25+ волн. Критерий A ЗАКРЫТ; этап B: toy-эталон воспроизведён (гарнесс green gate), синтетика k≥8 ОТКРЫТА
- ⚠️ **В origin/gitverse уходит битый коммит**: последний `gradlew test` упал на compileTestJava (740ms) ДО пуша, но цепочка с `;` запушила. Тренер чист (grep: только sParam) — виноват какой-то ТЕСТ-файл. ПЕРВОЕ ДЕЛО: `gradlew :matrix-core:compileTestJava` → найти error → починить → commit+push оба
- Финальный вердикт попытки 17: полный sweep s×N×c×e — trainAcc≈0.5 везде на k=8 random-DNF ⇒ обучение структурно не идёт; мандат следующей сессии = ДОСЛОВНЫЙ порт строк 336–400 ref_tm.c (уже в audit-plan §4) без творчества

## Немедленные шаги
1. compileTestJava → фикс → tsetlin suite зелёный → commit "fix(test): repair compile after S-parametrization" → push origin+gitverse
2. Записать attempt-17 вердикт в карточку EXP-002 + audit-plan §1.9 (уже частично сделано — проверить)
3. status.md финал

## Ключевые файлы/факты
- TsetlinTrainer: DEFAULT_S=4.0, ctor(...,InitStrategy,double s), typeOne D2-boost unconditional consistency-reward + pP mismatch; Ib pure decay pP; TypeII batch includeNow; D1' per-clause p=(T±vote)/2T; distillation exact
- Sweep результаты (k=8): все конфиги trainAcc 0.41–0.56 — см. /tmp/opencode/Sweep.java вывод в истории
- ref C: /tmp/opencode/ref_tm.c строки 331–400; audit-plan §4 verbatim
- LSP фантомы tsetlin/* — верить gradlew; rm→mv; полный test OOM — батчи
- FROZEN: ethics/, CONSTITUTION.md, старые avro, workflows

## Очередь после фикса компиляции
1. TM verbatim port (мандат attempt-17) → синтетика k=8..20 → stage-1 close
2. Доменная фаза EXP-002; AC-3 спека; justification-graph; dep upgrades

[COMPACTION_COMPLETE]
