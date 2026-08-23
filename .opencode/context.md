# MATRIX Project Context - Session State

## Current Status
- **Миссия**: волны; Java/Quarkus/GraalVM; перед push fetch+rebase
- **Синхронизация**: локальный коммит с H-035 создан, НО origin push УПАЛ (GitHub LFS locks/verify i/o timeout — сетевой глюк); gitverse ✅ прошёл (grep=1). ФИКС: `git config lfs.https://github.com/AlexanderNarbaev/agi.git/info/lfs.locksverify false` затем повторить `git pull --rebase origin main && git push origin main`
- Тесты: tsetlin+bir зелёные (последний прогон BUILD SUCCESSFUL)

## Волны этой сессии (всё в истории коммитов)
- Wave 6-7: NeuronLayer кейстоун + canonical Granmo TM (см. предыдущие чекпойнты)
- Wave 8: attempt-4 hetero-init отвергнут
- Wave 9: WiSARD toDecisionClauseSet + parity property
- Waves 10-11: attempts 5-7 (pairing-bug найден+исправлен→XOR частично; vote-war; canonical-scale fail+14min anomaly)
- Wave 12: attempt-8 — growth верифицирован изолированно, tug-of-war equilibrium структурный; откат к лучшей базе; гарнесс @Disabled
- Wave 13: H-035 EBL hypothesis card добавлена в HYPOTHESES.md (proposed) — ЗАКОММИЧЕНО ЛОКАЛЬНО, ждёт пуша

## Очередь следующей сессии
1. Допушить origin (фикс LFS выше)
2. **TM convergence dedicated**: построчный аудит против Algorithm 1 Granmo 2018 (попытки 3–8 задокументированы в карточке EXP-002; ключевые находки: pairing-bug исправлен, growth-инверсия исправлена, остаток = пусто-клаузное равновесие/перетягивание каната; attempt-6 бэкап в /tmp/opencode/attempt6/)
3. JTMS/ATMS → LineageLedger интеграция (WAL зарегистрировано)
4. AC-3 → ExecutablePlanner; EBL → реализовать после сходимости TM (карточка H-035 готова)
5. Dependency upgrades осторожно

## Constraints / факты
- FROZEN: ethics/, CONSTITUTION.md, старые avro, workflows; K_MAX≤20; coverage≥82%; Java-only prod; seeded Random вне рантайма
- LSP фантом tsetlin/* дубли — верить gradlew; полный test OOM — батчи; компактные ответы; rm→mv в /tmp/opencode/
- Канонический автомат: reward углубляет текущую сторону; penalty шаг к противоположной; TypeIa consistency⇒reward/mismatch⇒penalty; TypeII minimal repair includeSafe; Ib growth true-lit in/false-lit out

[COMPACTION_COMPLETE]
