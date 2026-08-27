# Project Context — SESSION CONTINUITY (compaction #61) — REVISION FIXES

## Mission
Apply minimal correction edits to align docs-v2/ with current architecture (BIR-primary, MPDT superseded as naming).

## Plan (5 file edits + 1 new)

1. docs-v2/levels/L1-MPDT-Neuron.md → переименовать в L1-BirUnit-Legacy.md (git mv) + переписать шапку: «LEGACY — BirUnit is current primary atomic compute element; this file preserves historical MPDT-Neuron definition for archive-completeness». Body остаётся историческим справочником.
2. docs-v2/levels/L5-DNA.md: заменить «GA on MPDT chromosomes» → «GA on BIR clause-set genomes (each chromosome is a ClauseSetForm)»; добавить ссылку-text на MpdtGaProducer.
3. docs-v2/levels/L3-Neurocluster-Arch.md: «MPDT neurons (L1)» → «BirUnit» + text-link к SPEC-002.
4. docs-v2/research/HYPOTHESES.md H-008: «MPDT proof memory batch mode» → «BIR proof memory batch mode».
5. docs-v2/vision/CONCEPT-CORRECTIONS.md (новый, singleton normative, ≤150 строк) — таблица «устаревший термин → актуальный термин» + статус «LEGACY» для некоторых файлов + явные text-only «Next:» pointer'ы.

## Hard requirements
- Без запрещённых формулировок.
- Без markdown-ссылок.
- Текстовые "Next:" pointer'ы.
- L1 rename: git mv сохраняет историю.
- Без изменений CONSTITUTION/AGENTS/FROZEN-файлов.