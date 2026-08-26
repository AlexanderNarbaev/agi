# WAL — Write-Ahead Log сессий (checkpoint, не лог)

**Статус: ephemeral.** Переписывается в конце каждой сессии. Детали реализации — в спеках и git-истории, не здесь.

## Активный фокус
- Этап 0 ROADMAP: честная рамка (карантин конвертации весов, правка claims). Ссылки: ROADMAP#этап-0, SPEC-001#этап-A

## Правила сессии
- НЕ ТРОГАТЬ: ethics/**, CONSTITUTION.md, avro/**, workflows
- Хэширование/схемы: только обратимо-совместимые изменения Avro

## Что сделано
- [x] Установлен пакет документации (INSTALL.md)

## Следующее действие
Карантин `scripts/pretrain_neurons.py` (SPEC-001 этап A): experimental-флаг, исключение из `/v1/models`, тест. Альтернатива: правка claims в docs/API.md.

## Известные проблемы
- Дыры покрытия: api/, cli/, cluster/events/, R2dbcEventJournal (этап 1 ROADMAP)
