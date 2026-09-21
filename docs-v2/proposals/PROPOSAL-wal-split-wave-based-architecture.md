# WAL Split Plan — Wave-based Architecture

> **Wave numbering:** This proposal was created in **W349**.
> - W349 (this proposal) = WAL Split proposal
> - W350 = SPEC-014 (WAL Management spec)
> - W351 = extract-waves.py (extraction script)
> - W352+ = Implementation (extraction execution, archive creation, INDEX updates)
>
> The phases below are forward-looking (W352+), not W349-W351.

## Problem
Current `WAL.md` is 2847 lines monolithic file. Agents must read entire file for context, wasting tokens and time.

## Solution
Split into per-wave files: `docs-v2/waves/wave-NNN-*.md`

### Structure
```
docs-v2/waves/
├── wave-001-initial-commit.md
├── wave-002-core-setup.md
├── ...
├── wave-346-document-review.md
├── wave-347-protobuf-schema-dynamic-modulators.md  ← CURRENT (just created)
├── wave-348-liquid-federation-design.md            ← NEXT
└── LATEST.md                                        ← Symlink to latest wave (checkpoint)
```

### Format (per wave)
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

## Issues Resolved
1. Issue description

## Blockers
- None / Description

## Next Steps (Wave NNN+1)
1. [ ] Next task 1
2. [ ] Next task 2

## Checkpoint
**Hash:** `<git-commit-hash>`  
**Previous:** `<prev-hash>`
```

### Migration Strategy

#### Phase 1: Extract Recent Waves (W352)
Extract last 50 waves (W297-W346) from current WAL.md:
```bash
# Parse WAL.md by "## RUN" or "## WAVE" sections
# Create individual files
for wave in $(seq 297 346); do
  extract_wave $wave > docs-v2/waves/wave-$wave-*.md
done
```

#### Phase 2: Archive Old Waves (W353)
Waves W1-W296 → compressed archive:
```bash
docs-v2/archive/waves-001-296.tar.gz
```
Keep index file with summary of each wave.

#### Phase 3: Update References (W354)
- Update `docs-v2/INDEX.md` to point to `waves/` directory
- Update `LATEST.md` symlink to current wave
- Add navigation between waves (prev/next)

### Benefits
1. **Token Efficiency**: Agents read only relevant waves (last 5-10)
2. **Faster Search**: Grep specific wave vs 2847 lines
3. **Checkpoint Clarity**: Each wave = verified commit hash
4. **Parallel Work**: Multiple agents can work on different waves
5. **Rollback Safety**: Easy to revert single wave if needed

### Implementation Checklist
- [ ] Write extraction script (Python/Bash)
- [ ] Extract W297-W346 (last 50 waves)
- [ ] Compress W1-W296 archive
- [ ] Create LATEST.md symlink
- [ ] Update INDEX.md
- [ ] Test agent context loading
- [ ] Document in SPEC-014 (WAL Management)

### Example: Current Wave 347
File: `docs-v2/waves/wave-347-protobuf-schema-dynamic-modulators.md`
- Created: 2026-09-17
- Branch: `feature/liquid-federation-dynamic-modulators`
- Commit: `e954637`
- Artifacts: 3 files (proto + spec + design)
- Next: W348 (continue implementation)

---

**Decision:** This approach aligns with specification-driven development where each wave is a verifiable checkpoint with clear objectives, artifacts, and next steps.
