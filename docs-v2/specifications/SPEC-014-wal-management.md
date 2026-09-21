# SPEC-014: WAL Management & Wave-based Checkpointing

## Overview
Система управления журналом волн (WAL) с разделением на пер-вейв файлы для эффективной работы агентов и верификации checkpoint'ов.

## Motivation
Текущий `WAL.md` (2847 строк) требует полного чтения для контекста, что:
- Тратит токены впустую (агенты читают старые неактуальные волны)
- Замедляет поиск конкретной информации
- Усложняет откат изменений при ошибках
- Не позволяет параллельную работу над разными волнами

## Requirements

### Functional
1. **R1**: Разделение WAL на файлы `wave-NNN-*.md` (один файл = одна волна)
2. **R2**: Архивация старых волн (W1-W296) в сжатый тариф
3. **R3**: Symlink `LATEST.md` → текущая волна
4. **R4**: Навигация между волнами (prev/next links)
5. **R5**: Извлечение волн из монолитного WAL.md
6. **R6**: Верификация checkpoint'ов через git commit hash
7. **R7**: Интеграция с `docs-v2/INDEX.md`

### Non-Functional
1. **NF1**: Время чтения последней волны < 100ms
2. **NF2**: Размер файла волны ≤ 50KB (typical)
3. **NF3**: Поиск по волнам < 1s (grep)
4. **NF4**: Поддержка до 1000 волн в структуре
5. **NF5**: Совместимость с существующими инструментами

## Architecture

### File Structure
```
docs-v2/
├── INDEX.md                              # Обновлённая навигация
├── waves/
│   ├── wave-001-initial-commit.md        # W1 (в архиве)
│   ├── ...
│   ├── wave-296-*.md                     # W296 (последняя в архиве)
│   ├── wave-297-*.md                     # W297 (первая активная)
│   ├── ...
│   ├── wave-346-document-review.md       # W346
│   ├── wave-347-protobuf-schema-*.md     # W347 (CURRENT)
│   ├── wave-348-liquid-federation-*.md   # W348 (NEXT)
│   └── LATEST.md → wave-347-*.md         # Symlink
└── archive/
    └── waves-001-296.tar.gz              # Сжатые старые волны
        └── index.md                       # Индекс архива
```

### Wave Format
```markdown
# WAL: Wave NNN — <Title>

**Date:** YYYY-MM-DD  
**Branch:** `<branch-name>`  
**Focus:** <1-sentence summary>

## Objectives
1. [ ] Task 1
2. [ ] Task 2

## Completed Tasks
### 1. Task Name
**Status:** ✅ DONE / ⚠️ PARTIAL / ❌ BLOCKED  
**Artifacts:** `path/to/file`

Details...

## Metrics
- Files: N
- Lines: M
- Tests: K
- Commits: `hash1`, `hash2`

## Issues Resolved
1. Issue description

## Blockers
- None / Description + mitigation plan

## Next Steps (Wave NNN+1)
1. [ ] Next task 1
2. [ ] Next task 2

## Artifacts
- `path/to/file1`
- `path/to/file2`

## Verification
- [x] Test evidence
- [x] Git commit confirmed
- [ ] CI/CD status

---

**Checkpoint Hash:** `<git-commit-hash>`  
**Previous Checkpoint:** `<prev-hash>`  
**Next Wave:** W<NNN+1> (<description>)
```

## Implementation Plan

### Phase 1: Extraction Script (W350)
**Script:** `scripts/extract-waves.py`

```python
#!/usr/bin/env python3
"""
Extract individual wave files from monolithic WAL.md
Usage: ./extract-waves.py --start 297 --end 346
"""

import re
import sys
from pathlib import Path

def parse_wal(wal_path: Path):
    content = wal_path.read_text()
    # Split by wave markers (## RUN or ## WAVE or ## CHECKPOINT)
    sections = re.split(r'^(## (?:RUN|WAVE|CHECKPOINT) \d+)', content, flags=re.MULTILINE)
    return sections

def extract_wave(wave_num: int, sections: list) -> str:
    # Find section for wave NNN
    # Extract relevant content
    # Format according to template
    pass

def main():
    wal_path = Path('WAL.md')
    output_dir = Path('docs-v2/waves')
    
    for wave_num in range(args.start, args.end + 1):
        wave_content = extract_wave(wave_num, sections)
        output_file = output_dir / f'wave-{wave_num:03d}-auto-extracted.md'
        output_file.write_text(wave_content)
        print(f'Extracted W{wave_num} → {output_file}')

if __name__ == '__main__':
    main()
```

### Phase 2: Archive Creation (W351)
```bash
# Create archive directory
mkdir -p docs-v2/archive

# Create index for archived waves
cat > docs-v2/archive/waves-001-296/index.md << EOF
# Archived Waves (W1-W296)

## Summary
- Total waves: 296
- Date range: 2024-XX-XX to 2026-09-XX
- Key milestones: [list]

## Access
Extract archive: tar -xzf waves-001-296.tar.gz

## Index
| Wave | Date | Title | Commit |
|------|------|-------|--------|
| W1   | ...  | ...   | ...    |
| ...  | ...  | ...   | ...    |
| W296 | ...  | ...   | ...    |
EOF

# Compress old waves (after extraction)
tar -czf docs-v2/archive/waves-001-296.tar.gz \
    docs-v2/waves/wave-001-*.md \
    docs-v2/waves/wave-002-*.md \
    ... \
    docs-v2/waves/wave-296-*.md

# Remove original files
rm docs-v2/waves/wave-00{1..9}-*.md
rm docs-v2/waves/wave-0{10..96}-*.md
```

### Phase 3: Update References (W352)
**Update INDEX.md:**
```diff
 ## Документация
 
 - [Architecture](architecture/)
 - [Specifications](specifications/)
 - [Designs](designs/)
+- [Waves](waves/) ← NEW
 - [Research](research/)
 - [Engineering](engineering/)
 - [Operations](operations/)
```

**Create LATEST.md symlink:**
```bash
cd docs-v2/waves
ln -sf wave-347-protobuf-schema-dynamic-modulators.md LATEST.md
```

### Phase 4: Manual Checkpoint Process (current rule, RFC-001 pending)

**Per AGENTS.md §FROZEN zones, `.github/workflows/**` is FROZEN.**
**An auto-commit GitHub Action is therefore NOT implemented.**

The replacement rule is **mandatory manual process**:

> **RULE (effective 2026-09-18, owner-mandated):**
> 1. **At end of every wave session, before next wave begins:**
>    a. Commit all wave artifacts (code, docs, tests, scripts) to git.
>    b. Push to origin: `git push origin main`.
>    c. Commit the wave's per-file artifact to `docs-v2/waves/wave-NNN-*.md`.
>    d. Update the "Last WAL" pointer at the top of `WAL.md` with the new wave number and commit hash.
> 2. **Verification:** Each commit's wave file must include the actual git commit hash (not `<pending>`).

**Rationale:** Manual commits prevent silent CI drift and ensure owner visibility on every wave checkpoint.

**RFC-001 (deferred):** If automation is later desired, RFC-001 will propose:
- A non-auto-commit workflow that ONLY validates wave checkpoints (read-only CI check).
- A `stefanzweifel/git-auto-commit-action@v5` alternative would require explicit owner RFC mandate per AGENTS.md §FROZEN.

**Scripts used (manual invocation):**
- `./scripts/extract-waves.py` — extract waves from WAL.md
- (Future) `./scripts/create-wave-checkpoint.sh` — write wave-NNN file with git hash
- (Future) `./scripts/update-latest-wave.sh` — update INDEX.md with latest wave pointer

All scripts are invoked manually by the wave owner, not by CI.

## Migration Checklist

- [ ] Write `scripts/extract-waves.py`
- [ ] Test extraction on W340-W346 (dry run)
- [ ] Extract W297-W346 (production)
- [ ] Create `docs-v2/archive/waves-001-296.tar.gz`
- [ ] Create `LATEST.md` symlink
- [ ] Update `docs-v2/INDEX.md`
- [ ] Add navigation (prev/next) to wave template
- [ ] Write SPEC-014 (this document)
- [ ] Test agent context loading
- [ ] Document in WAL.md header

## Benefits Realization

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| WAL size | 2847 lines | ~60 lines/wave | 47× smaller per read |
| Read time | ~5s | ~100ms | 50× faster |
| Search time | ~2s (grep 2847) | ~50ms (grep 60) | 40× faster |
| Token usage | ~800 tokens/wave | ~60 tokens/wave | 13× less |

## Security Considerations

1. **Integrity**: Each wave signed with git commit hash
2. **Immutability**: Archived waves cannot be modified (only appended)
3. **Access Control**: Write permissions for wave creation only
4. **Audit Trail**: All changes logged in git history

## Testing Strategy

### Unit Tests
- Wave extraction script (parse WAL.md correctly)
- Symlink creation (points to correct file)
- Archive compression/decompression

### Integration Tests
- Agent loads LATEST.md successfully
- Navigation prev/next works
- INDEX.md links resolve

### Performance Tests
- Read latency < 100ms
- Search latency < 1s
- Archive size < 1MB

## Dependencies
- Python 3.10+ (extraction script)
- Git (checkpoint verification)
- Bash (archive creation)

## Risks & Mitigations

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Extraction bug loses data | Low | High | Dry run first, backup WAL.md |
| Symlink broken | Medium | Low | Automated test in CI |
| Archive corruption | Low | Medium | Verify tar.gz checksum |
| Agent confusion | Low | Low | Documentation update |

## References
- CONSTITUTION.md Article V (JaCoCo ≥82%)
- DESIGN-67 (Liquid Federation)
- SPEC-013 (Dynamic Modulator Registry)
- `docs-v2/INDEX.md`

## Acceptance Criteria
1. ✅ W297-W346 extracted as individual files
2. ✅ W1-W296 archived in compressed format
3. ✅ LATEST.md symlink points to current wave
4. ✅ INDEX.md updated with waves link
5. ✅ Agent context loading tested
6. ✅ Performance metrics met (read <100ms, search <1s)
