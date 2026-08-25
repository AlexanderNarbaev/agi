# Project Context — SESSION CONTINUITY (compaction #31)

## Ловушки
- Целевые прогоны `--tests "io.matrix.<pkg>.*"`; LSP FpgaBackend.java:150 ложная + Exp002ComparisonTest 107/117/134 устаревшие (фиксы на месте, BUILD зелёный); субагенты «Insufficient Balance» — всё сам.
- heredoc python PYEOF; zsh кавычить --include="*.java"; Math.floorMod.
- Пушить после каждого значимого коммита. Последний коммит c6b8120 (запушен).

## Mission: волны по docs/engineering/PLAN-FULL-IMPLEMENTATION.md

## DONE (всё зелёное, запушено)
- W1-W5,W7 ядро: K_MAX/RuntimeLimits/BIR-to-MPS.md; devloop SPEC-000 (12 кл.+свойства); ktopo ricci-тройка; DESIGN-09 MonotoneDecoder; DESIGN-11 ConjugateBudgeter; DESIGN-12 FnlGate+ConsolidationCycle; DESIGN-13 PlanRunner/VersionedContract/PlanPreprocessor(AC-3); DESIGN-02 Viewpoint; W7 CRD×2+ElspChannel.
- DESIGN-14: A-1(24)+A-2(9)+A-3c(4 SchemaDescriptor) мигрировано=37; A-3 классификация (13 транзитивно, SIMD→JMH-гейт); INV-1 source-scan guard Inv1SourceGuardTest (без dep, работает в CI).
- W6: EXP-010 протокол 9 прогонов → **H-010 accepted** (median 242×, WiSARD 9/9); EXP-002 + MpdtGaProducer baseline → **H-002 refuted-toy**; EXP-003 skeleton running.
- Аудиты закрыли: DESIGN-03 (REST/MCP уже есть), DESIGN-06 (прод-signals уже есть; embed-hash BLOCKED-EXT, audio-events этап 3), карантин models восстановлен (42 файла).
- Коммиты серия: ed42fd1…c6b8120.

## ТЕКУЩИЙ ШАГ: финальный сквозной прогон + закрытие серии
1. Полный sweep всех затронутых пакетов одной командой (runtime devloop ktopo bir.producers.monotone budgeter actions lifecycle brain federation distill tsetlin evolution minecraft dialog api explainability neuron) → BUILD SUCCESSFUL.
2. WAL.md: дополнить «Что сделано» строками про INV-1 guard, DESIGN-03/06 аудиты, A-3c; «Следующее действие» = DJL/ONNX учитель (dep) / доменные корпуса MPDT-GA / JMH-гейт Batch*.
3. Журнал плана: проверить актуальность (последние правки были при A-3c/DESIGN-06? — да, журнал ok).
4. git add -A; commit «WAL: финальная синхронизация серии»; push.
5. Итоговый отчёт пользователю: сводка всей серии (что реализовано по волнам с путями, вердикты гипотез, BLOCKED-EXT реестр, очередь).

## Остатки (все честно задокументированы)
- DJL/ONNX учитель — нужна зависимость (build.gradle) и веса.
- Доменные корпуса для MPDT-GA полного вердикта.
- audio-events этап 3; алиасы /matrix/* косметика; постквант v2; EDGE-3 вне горизонта.
- Квантовый FR-D3 код — ждёт субстрата (spec готов).

## Правила
FROZEN/avro/workflows не трогать; forbidden claims избегать; числа только реальные.
