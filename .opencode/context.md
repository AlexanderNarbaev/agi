# MATRIX Project Context - Session State

## Current Status
- **ПРОРЫВ**: EXP-002 предэтап ВОСПРОИЗВЕДЁН (wave 16, attempt-9): корень фиаско попыток ≤8 = синхронный комплементарный init клауз (все — идентичные специалисты full-true minterm; TypeIa reward 0.75 перевешивал Ib growth 0.25). Фикс: **random per-automaton init** (reference-style). GranmoReferenceTest 5/5 ВКЛЮЧЁН ПОСТОЯННО как regression-gate. Полный регресс 5 пакетов **375/0**
- Синхронизация: за rebase влита чужая волна; push origin ✅ + gitverse ✅ (grep=1); дерево чистое
- Всё зафиксировано: HYPOTHESES карточка (attempt-9 ✅ + причина), WAL «Прорыв», todo T6.16 [x]

## Этап B теперь разблокирован полностью. Очередь следующей сессии:
1. **EXP-002 доменный этап** по протоколу карточки: эталоны ✓ → synthetic k∈{8,12,16,20} → Minecraft-перцепт; сравнение Tsetlin vs MPDT-ГА vs BNN-ориентир; метрики из карточки; 5 сидов
2. EBL реализация (H-035): приоритизация выборки контрфактическими минтермами → examples-to-99%
3. JTMS/ATMS развитие LineageLedger (RETRACT есть; дальше — justification-graph)
4. AC-3 → ExecutablePlanner (атлас §101); атлас §95–103 прочитать целиком при планировании REFLEX/Cauldron
5. Dependency upgrades осторожно

## Ключевые факты TM (для будущих правок)
- TsetlinTrainer: canonical voting ±1, пулы НЕ разделены по классам; random init автоматов (rng.nextInt(2N)); S=4.0; TypeIa consistency⇒reward/mismatch⇒penalty; Ib growth true-in/false-out (+includeSafe guard от x∧¬x); TypeII minimal repair (один литерал); shuffle по эпохам; predict=score>0; дистилляция toDecisionClauseSet/toDecisionBir точная (exhaustive 2^k ≤20)
- Гарнесс конфиги: AND/OR c=24,N=10,e=1200; XOR c=32,N=12,e=1500; MUX c=48,N=12,e=2000 seed42; noisyXor mean≥0.75
- LSP фантом tsetlin/* дубли — верить gradlew; rm→mv в /tmp/opencode/; полный test OOM — батчи; компактные ответы
- FROZEN: ethics/, CONSTITUTION.md, старые avro, workflows; K_MAX≤20; coverage≥82%; Java-only prod

[COMPACTION_COMPLETE]
