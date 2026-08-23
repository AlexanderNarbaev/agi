# MATRIX Project Context - Session State

## Current Status
- **Mission**: волны команды; каждая волна = commit+push оба remote ✅ работает
- **Wave 0**: f2b8874 (M1+M2) → pushed both
- **Wave 2**: commit 4e3744a feat(tsetlin) FR-B1/B2 — **origin (github) pushed ✅; gitverse ЗАБЛОКИРОВАН**: «Cannot update this protected ref. Changes must be made through a pull request» (правило защиты main появилось на gitverse). Требует PR-флоу или ослабления правила владельцем — зафиксировать в WAL как известную проблему, продолжать пушить только origin + пробовать gitverse (возможно разово сработает позже/через PR)
- **Тесты**: 146 green (131 bir + 15 tsetlin), BUILD SUCCESSFUL

## Осталась Wave 3 (todo.md 2 unchecked: S5.1, S5.2)
1. **S5.1 Доки**:
   - `docs/GLOSSARY.md`: добавить термины Tsetlin automaton / clause / team vote (компактно, стиль файла соблюдать)
   - `docs/spec/SPEC-002*.md`: changelog строка в шапке (normative-формат: дата 2026-08-23, причина, суть: этап B начат — TsetlinTrainer producer в io.matrix.tsetlin внутри matrix-core; отклонение от «модуль matrix-tsetlin»: выделение отдельного Gradle-модуля отложено до анализа влияния на CI/jacoco-гейт)
   - `README.md`: одна строка в подходящем месте про tsetlin-producer этапа B (опционально, если есть место типа архитектуры/статусов)
2. **S5.2 Финал**: переписать WAL.md по шаблону (Активный фокус=этап B начат; Что сделано+=Wave2 Tsetlin; Следующее действие: Критерий A эпик / EXP-002 пререгистрация бинаризации ДО запуска / gitverse protected-ref проблема; Известные проблемы += gitverse main защищён (PR-only), LSP-кэш выдаёт фантомные дубли в tsetlin при чистом диске); status.md обновить; commit `WAL: Stage B Tsetlin producer + docs` + push origin (+gitverse попытка)

## Ключевые факты для правок доков
- GLOSSARY путь: docs/GLOSSARY.md — прочитать хвост перед вставкой (формат терминов)
- SPEC-002 файл: docs/spec/SPEC-002-*.md (найти точное имя ls docs/spec/) — шапка содержит Changelog секцию? Проверить формат существующих записей и добавить в том же стиле
- Автомат семантика (для глоссария): состояния 1..2N, 1..N=exclude, N+1..2N=include; reward→include-сторона, penalty→exclude; TypeI: litTrue→reward w.p.(s−1)/s, litFalse→penalty w.p.1/s; TypeII(fired,target0): excluded&litFalse→includeNow; клауза=конъюнкция включённых литералов (omission≠¬x); экспорт DNF→ClauseSetForm точен (property exhaustive k≤6)
- Trainer API: ctor(inputBits,nClauses,nStates,Random); trainStep(long[] words,bool); trainBatch; toClauseSet(String); predict(long packed); S=8.0 const; inputBits≤64
- Constraints: FROZEN не трогать (ethics/, CONSTITUTION.md, старые avro, workflows); SPEC-002 normative → изменения ТОЛЬКО через changelog строку, тело спеки не ломать
- LSP фантом: io.matrix.tsetlin/TsetlinAutomaton показывает дубли-ошибки при чистом файле (79 строк, компилятор зелёный) — игнорировать, верифицировать gradlew

## IDs
- Commits: f2b8874 (wave0) → 4e3744a (wave2). Remote origin=github OK; gitverse=protected main (PR-only) ❌
- Jobs последние: bir-after-clean job_0c3be865 зелёный; далее прямые запуски
- todo.md: 32/34 [x]; остались только S5.1, S5.2

[COMPACTION_COMPLETE]
