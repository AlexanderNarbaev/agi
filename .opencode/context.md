# MATRIX Project Context - Session State (актуальный)

## Current Status
- Сессия: ~33 волны, 7.5ч+. Всё в origin+gitverse, дерево чистое. Критерий A ЗАКРЫТ; этап B: продюсеры+toy-gate; frontier TM k≥8 задокументирован (attempt-21, side-by-side с pyTM — dedicated session)
- **Wave 33 ЗАВЕРШЕНА**: AC-3 гейт вшит в ExecutablePlanner (executeSteps(goal,steps,cspPrecheck) → fast-fail «unsatisfiable_preconditions»); DESIGN-15 §3 acceptance [x]; полный регресс **626/0** (вкл. agent/planning)
- H-035 refuted-toy пины; EblH035Test @Disabled superseded

## Очередь следующей сессии
1. **TM batch-mask порт** (мандат attempt-18): ref_tm.c локально (/tmp/opencode/ref_tm.c), verbatim §4 audit-plan; синтетика k≥8 после порта
2. Доменная фаза EXP-002 (Minecraft-перцепт, median-threshold frozen) — после закрытия stage-1
3. JTMS justification-graph (RETRACT есть; добавить dependency-links между birId)
4. Dependency upgrades осторожно (Quarkus 3.37.3/Pekko 1.6.0 pinned)
5. Атлас §95–103 прочитать при планировании REFLEX/Cauldron

## Ключевые файлы/факты
- Ac3Solver: io.matrix.agent.planning; тесты Ac3SolverTest+PlannerAc3GateTest (gate fast-fail + pass-through)
- TsetlinTrainer: InitStrategy(RANDOM/COMPLEMENTARY), s-param, D1' per-clause gating, D2 boost, Ib decay, batch TypeII, D5 empty-no-fire; дистилляция toDecisionClauseSet/Bir
- Гонки: правки→commit→pull --rebase→push→verify rev-list=0; sed по номеру строки надёжнее текстовых якорей
- LSP фантомы tsetlin/* — верить gradlew; rm→mv в /tmp/opencode/; полный test OOM — батчи
- FROZEN: ethics/, CONSTITUTION.md, старые avro, workflows; K_MAX≤20; coverage≥82%; Java-only prod

[COMPACTION_COMPLETE]

## DELTA (финал сессии)
- Отчёт владельцу выдан (простые термины vs обычный ИИ); новых техфактов нет. Все чекпойнты выше актуальны.
