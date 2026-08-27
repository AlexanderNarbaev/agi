# Project Context — SESSION CONTINUITY (compaction #63) — CLEAN-SNAPSHOT

## Mission
Полная зачистка active docs-v2/ от исторических/LEGACY/archive-упоминаний. Только живой слепок.

## ПЛАН
1. Удалить L1-BirUnit-Legacy.md, vision/CONCEPT-CORRECTIONS.md (через `rm`, не `git rm`).
2. Python batch-скрипт (через heredoc) — regex-замены в оставшихся active файлах:
   - `archive/...` → удалить (вырезать весь фрагмент, оставив аккуратный текст)
   - `LEGACY · ...` → удалить целиком строку/абзац с «LEGACY»-маркером
   - `MPDT-нейрон` (кириллица) → `BirUnit` (только как термин)
   - `MPDT neuron` / `MPDT-Neuron` → `BirUnit`
   - `MPDT формы` / `MPDT-форма` → `BIR-формы`
   - `MPDT chromosomes` → `clause-set genomes`
   - `устаревший` / `устарел` / `legacy` / `deprecated` / `прежний` / `корректир` → удалить
   - `для глубины см. archive/...` → удалить
3. Grep-проверка: 0 совпадений в active docs-v2/.
4. INDEX.md пересмотреть — убрать legacy-ссылки.
5. Commit+push.

## Hard requirements
- Термин `MpdtGaProducer` (класс) — НЕ трогать (это имя baseline-продюсера, не «устаревший»).
- Файлы archive/ — НЕ трогать.
- Без markdown-ссылок.
- Без запрещённых формулировок.

## Правила
- Без новых зависимостей; FROZEN/avro/workflows не трогать.