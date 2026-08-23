# MATRIX Project Context - Session State

## Current Status
- **Миссия**: волны; Java/Quarkus/GraalVM; fetch→rebase→push перед каждым пушем
- **Синхронизировано**: main=origin/main (за rebase влита чужая волна), gitverse тоже прошёл (grep=1). Дерево чистое
- **Критерий A закрыт**; tsetlin+bir зелёные; TM convergence открыт (attempts 3–8 в карточке EXP-002)

## Волны этой сессии (все запушены)
- Wave 6–7: NeuronLayer кейстоун BIR; canonical Granmo TM каркас
- Wave 9: WiSARD toDecisionClauseSet + parity
- Waves 10–12: TM attempts 5–8 (pairing-bug исправлен; growth-инверсия исправлена; вердикт: tug-of-war equilibrium структурный → dedicated session; attempt-6 бэкап /tmp/opencode/attempt6/)
- Wave 13: H-035 EBL карточка (proposed)
- Wave 15: **JTMS/ATMS-lite в LineageLedger**: Operation.RETRACT + retract() (justification = последний contentHash артефакта) + latestStatus()/isRetracted() (ATMS label поверх append-only цепи); тесты jtmsRetractionKeepsChainAndLabels / retractUnknownIdUsesZeroJustification зелёные

## Очередь следующей сессии (приоритет)
1. **TM convergence dedicated-session**: построчный трассировочный аудит typeOne/typeTwo/Ib против Algorithm 1 Granmo 2018; попытки 3–8 и кандидаты — в карточке EXP-002 HYPOTHESES.md; бэкап attempt-6: /tmp/opencode/attempt6/TsetlinTrainer.attempt6.java; гарнесс TsetlinGranmoReferenceTest @Disabled (включить снятием аннотации); конфиги уже канонические масштабы (AND/OR c=24,N=10,e=1200; XOR c=32,N=12,e=1500; MUX c=48,N=12,e=2000); S=4.0 в тренере
2. AC-3 → ExecutablePlanner (атлас §101)
3. EBL реализация после сходимости TM (H-035 готова)
4. Dependency upgrades осторожно (Quarkus/Pekko pinned)
5. Прочитать атлас §95–103 полностью при планировании REFLEX/Cauldron

## Constraints / факты
- FROZEN: ethics/, CONSTITUTION.md, старые avro, workflows; K_MAX≤20; coverage≥82%; Java-only prod; seeded Random вне рантайма
- LSP фантом tsetlin/* — верить gradlew; полный test OOM — батчи; компактные ответы; rm заблокирован → mv в /tmp/opencode/
- Вложенный enum: писать LineageLedger.Operation.XXX в тестах (sed по голому Operation.* задваивает префикс — осторожно)
- Каноника автомата: reward углубляет текущую сторону; penalty шаг к противоположной; includeNow=n+1; TypeIa consistency⇒reward/mismatch⇒penalty; Ib growth true-in/false-out; TypeII minimal repair includeSafe
- Гонки: правки→commit→pull --rebase→push→verify rev-list=0

[COMPACTION_COMPLETE]
