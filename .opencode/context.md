# MATRIX Project Context - Session State

## Current Status
- **Mission**: булева машина (BIR) по документации; прямой режим (code-делегация агентов ненадёжна)
- **M1 (SPEC-002 этап A)**: ✅ VERIFIED
- **M2 (BDD-алгебра WAL#2+#3)**: ✅ VERIFIED — todo.md 22/22 [x], status.md обновлён, WAL.md обновлён (пункты #2/#3 закрыты, computedCache-issue удалён, добавлен блок «Закрытие WAL #2+#3 2026-08-23»)
- **Тесты**: полный пакет bir BUILD SUCCESSFUL (job_0c3be865): 16 классов, tests=131, failed=0, skipped=0 (121 старых + 10 BirBooleanAlgebraTest)

## Остался ровно один шаг миссии
1. **Milestone commit всего рабочего дерева** (проектный протокол AGENTS.md «Заверши сессию milestone-коммитом»):
   `git add -A && git commit -m "feat(bir): SPEC-002 stage A closure + BDD boolean algebra & canonical equivalence (WAL #2/#3)"`
   Включает: изменения M1 (bir core+tests+avro+доки), BddForm.java (315 строк, Op/apply/not/constant/equivalentTo), BirBooleanAlgebraTest.java (вариант с record TtPair), .opencode/{todo,status,context}.md, WAL.md, docs/research/notes/BIR-THEORY-scan.md
   Проверить перед коммитом: git status не содержит секретов (.env и т.п.)

## Key IDs / факты
- Jobs: job_0c3be865 зелёный финал; job_e8b54b5d/job_fcace48b — инфра-сбой NoSuchFileException in-progress-results-generic.bin (битый test-results каталог, устранён переносом в /tmp/opencode/stale-testresults/)
- Агенты: Worker task_024dfa7d FAIL; Reviewer'ы task_083af1bd/task_9364db4a/task_e61fb9bb завершены (9364db4a дедупнул BddForm до 315 строк + переписал мой тест на TtPair — API цел); Planner task_c3af32df результат утерян → исследование «коннекторов» сделано напрямую grep'ами: таксономии в docs/ нет, ближайшие концепции = apply/ITE композиции, BRC, голосование клауз

## Next Front (после коммита, новые сессии)
1. Критерий A: миграция 61 call-site (эпик, дизайн кэширования форм обязателен — наив = 2^20 eval'ов)
2. Этап B: matrix-tsetlin FR-B1/B2 + EXP-002 (пререгистрация бинаризации ДО запуска)
3. Долги: H-007 embedding, H-008 пререгистрация

## Constraints
- FROZEN: ethics/, CONSTITUTION.md, существующие avro, workflows; K_MAX=20; coverage ≥82%; Java-only; детерминизм
- Полный gradle test OOM — батчами; отвечать компактно (anomaly-протокол)

[COMPACTION_COMPLETE]
