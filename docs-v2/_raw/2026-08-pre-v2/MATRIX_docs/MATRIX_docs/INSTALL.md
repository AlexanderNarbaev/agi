# INSTALL — установка пакета документации в репозиторий

Пакет полностью самодостаточен: не требует и не предполагает существования каких-либо других документов. Содержимое папки `MATRIX_docs/` копируется в корень репозитория с заменой одноимённых файлов.

## 1. Копирование (замена)

```bash
cp -r MATRIX_docs/* /path/to/agi/        # заменить README.md, CONTRIBUTING.md и др.
```

Заменяются: `README.md`, `CONTRIBUTING.md`. Добавляются: `CONSTITUTION.md`, `AGENTS.md`, `WAL.md`, `docs/INDEX.md`, `docs/GLOSSARY.md`, `docs/vision/`, `docs/science/`, `docs/spec/`, `docs/research/`, `docs/engineering/`.

## 2. Архивация прежней документации (не удаление)

```bash
mkdir -p docs/archive
cd docs
git mv L*.md archive/ 2>/dev/null
git mv LONGTERM_PLAN.md V3_CONFIGURATION.md MODEL_RECOMMENDATIONS.md \
       HARDWARE_ANALYSIS.md PLAYER_GUIDE.md PROJECT_AUDIT_v3.58.md archive/ 2>/dev/null
git mv research/RESEARCH_SYNTHESIS_2026_Q3.md archive/ 2>/dev/null
```

Оставить на месте (рабочие документы, не противоречат пакету): `docs/API.md`, `docs/DEPLOYMENT.md`. Если они содержат утверждения, запрещённые CONSTITUTION.md Статьёй VI («не лжёт», «не забывает», «AGI», «pretrained neurons merged» как работающая фича) — исправить формулировки в рамках этапа 0 ROADMAP.

## 3. Карта соответствия (что чем заменено)

| Прежний документ | Судьба | Замена |
|---|---|---|
| `docs/L0_*.md` (аксиомы) | archive | `CONSTITUTION.md` |
| `docs/L12_Legal.md` | archive | `CONSTITUTION.md` Статья I (юридическая оговорка) |
| `docs/LONGTERM_PLAN.md` | archive | `docs/engineering/ROADMAP.md` |
| `docs/L1–L23` (спецификации) | archive | `docs/spec/SPEC-000…003.md` + `docs/vision/ARCHITECTURE.md` |
| `docs/INDEX.md` | заменён | новый `docs/INDEX.md` |
| `docs/research/RESEARCH_SYNTHESIS_2026_Q3.md` | archive | `docs/research/HYPOTHESES.md` + `docs/research/METRICS.md` |
| `docs/PROJECT_AUDIT_v3.58.md` | archive | `docs/vision/ARCHITECTURE.md` §6 («существует vs строится») |
| `README.md` | заменён | новый `README.md` (честные формулировки, то же операционное содержимое) |
| `CONTRIBUTING` | заменён | `CONTRIBUTING.md` (протокол участия человека и агента) |

## 4. Первые действия после установки

1. Прочитать `CONSTITUTION.md`, затем `AGENTS.md`.
2. Создать первую запись `WAL.md` (фокус: этап 0 ROADMAP).
3. Запустить этап 0 из `docs/engineering/ROADMAP.md` (честная рамка: карантин конвертации весов, правка claims).
4. Дальнейшая работа агентов — по спекам из `docs/spec/` в порядке: SPEC-002 (keystone) → SPEC-001 → SPEC-000 → SPEC-003.
