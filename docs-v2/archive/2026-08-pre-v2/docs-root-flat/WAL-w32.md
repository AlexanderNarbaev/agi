# WAL — 2026-08-24, компактификация (сессия прервана по запросу)

## Главное правило при возобновлении (урок этой сессии)
**НИКОГДА не транскрибировать файл >20–30 КБ в один create_or_update_file.**
Для больших документов: либо малые дельта-файлы (новые, без sha), либо правки
малых файлов целиком. При подготовке пуша: контент в файл, sha256 проверить,
СРАЗУ вызывать create_or_update_file. Никаких промежуточных печатей.

## Состояние main (верифицировано)
- docs/science/ALGORITHM-ATLAS-WAVE32.md — commit ba422161, blob bbdd35f0 (§116–§118) ✓
- docs/GLOSSARY.md — commit 69f68976, blob 8266b516 (термины волны 32) ✓
- docs/INDEX.md — blob 92ecd071 (волна 31; правки 32 НЕ применены)
- docs/science/SYSTEM-SYNTHESIS.md — blob c2474f75 (волна 31; правки 32 НЕ запушены)
- docs/research/TASKS.md — blob f0e30b0a
- docs/research/HYPOTHESES.md — blob d7099736 (ИЗМЕНЁН параллельным треком; был b62b9b3b)

## Подготовлено, НЕ запушено (лежит локально, готово к пушу)
1. /mnt/agents/output/w32/ss32-delta.md — дельта SYNTHESIS волны 32, 5 249 симв.,
   sha256 0e98de71bea48ae5. Содержит: основания L2/L3/L6/L7/L9 (§116–§118),
   3 строки трассировки, 4 кандидата, дополнение границы №10.
   ПУШ: create_or_update_file, новый файл docs/science/SYSTEM-SYNTHESIS-WAVE32.md,
   БЕЗ sha, message "docs(synthesis): wave 32 delta (§116–§118)".
2. /mnt/agents/output/w32/ss32.md — ПОЛНАЯ версия SYNTHESIS с правками волны 32,
   130 310 симв., sha256 1f1ef91b0476aa05055dccf90a1203d02089ec2e7670c72d87d5d6ac608d3c05.
   НЕ пушить целиком (ловушка транскрипции). Держать как канонический источник,
   откуда дельта (п.1) уже извлечена. При ревизии монолита — влить дельту.

## Незавершённые задачи (порядок при возобновлении)
1. Пуш SYSTEM-SYNTHESIS-WAVE32.md из п.1 выше (один маленький вызов).
2. INDEX.md: +волна 32 (атлас ba422161 + дельта-синтез), счётчик файлов, правка заголовков.
   Файл 31.5 КБ — править целиком из свежего remote (был 92ecd071).
3. docs/SPEC-DRIVEN-DEVELOPMENT.md — НОВЫЙ документ реорганизации под SDD
   (последняя директива пользователя). Основание: SDD-скан завершён (spec-kit /speckit.*,
   Kiro/EARS, brownfield-инкрементализм, ISO 29148 — бриф был в сессии; суть: целевой
   уровень spec-anchored; EARS-паттерны; спека только на дельту изменения; ISO 29148
   характеристики: necessary/unambiguous/verifiable/singular/complete/consistent/feasible/
   traceable/implementation-free).
4. TASKS.md += TASK-037 (JSM-генератор §117), TASK-038 (SearchClassDeclaration §118),
   TASK-039 (SDD-инвентаризация код↔спека), TASK-040 (BDD-своп L2),
   TASK-041 (контур MVP над BIR-ядром), TASK-042 (M4-журнал мин.).
5. HYPOTHESES.md += H-039/040/041 (JSM vs random; BDD-своп сертификат; энергетика
   контура MVP). ОБЯЗАТЕЛЬНО fetch fresh + проверка коллизий id (трек пишет в main).
6. Волна 33 атласа: §119 Gulwani/FlashFill (PBE), §120 Wolpert NFL (граница),
   §121 Schmidhuber speed prior (граница). Footnote: следующий свободный [^617^].

## Контекст реализации (фон)
- BIR-ядро реализовано: matrix-core Java, regress 377/0.
- EXP-002 предэтап CLOSED (root cause: TypeIb speculative pull-in vs canonical decay;
  синтетика k=8/12/16/20 зелёная; TsetlinGranmoReferenceTest как regression-gate).
- H-035 → refuted-toy (EBL ~17× медленнее на XOR: 437 vs 26 примеров).
- Бинаризация preregistered median-threshold (заморожена).

## Технические правила (наследованные)
- Пуш ТОЛЬКО через GitHub MCP create_or_update_file (нет git/gh creds).
- Анти-дрейф: правки только поверх свежего raw-fetch; верификация remote==local после пуша.
- Spurious SHA-mismatch возможен — верифицировать контентом, не текстом ошибки.
- CDN-лаг после пуша ~15 с; trailing newline обрезается (canonical = rstripped).
- IPython kernel сбрасывается молча — хелперы переопределять; canonical = remote + файлы /mnt/agents/output/.
- FROZEN: ethics/**, CONSTITUTION.md, avro/**, .github/workflows/**, docs/archive/**.
- Запрещённые заявления (Статья VI): «AGI», «не лжёт».
- Coverage gate ≥82% METHOD.
