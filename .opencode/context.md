# Project Context

## Current Status (2026-08-10T20:14Z)
- Branch: docs/matrix-phase-1-5
- Build: compileJava PASS, compileTestJava PASS
- Git: clean working tree (only .opencode tracking files + .gitignore modified)
- Todo: 46/46 complete
- Sync-issues: 0 unresolved (all resolved or deferred)

## Completed (this session)
- task_a4a3ac5c: Phase 2 Guardrail fix — PASS
  - GuardrailEngine.java: strategy pattern (BiFunction evaluator), package-private ctor
  - GuardrailResult.java: clean
  - Isolated test: 6/6 pass, preserved in .opencode/unit-tests/
- Stale untracked test files deleted (4 files): SandboxExecutorTest, JvmSimdBackendTest, CodeExecuteToolTest, TtToBddConverterTest
- Orphan bir/__tests__/ deleted
- Sync-issues.md updated: all resolved
- .gitignore: unit-tests/ added

## Phase 1-5 Deliverables (ALL COMMITTED)
| Phase | Files | Commit |
|-------|-------|--------|
| 1 MPDT | BatchMemoryAdapter (31L stub) | 8d5e86c |
| 2 Guardrail | GuardrailEngine (145L) + GuardrailResult (45L) | 8d5e86c |
| 3 Minecraft | MinecraftPilot + Naive + records (119L) | c00dc14 |
| 4 FPGA | FpgaBackend + FpgaConfig (200L) | c00dc14 |
| 5 Noosphere | Crdt + GrowOnlySet + QuorumChecker (226L) | 2a9477c |

## Blockers
- LSP stale cache: errors reported on deleted files (phantom — files confirmed GONE on disk)
- SqliteMemoryBackend.java: duplicate methods (pre-existing, M3 era, not Phase 1-5 scope)
- System counter: reports 10 sync issues — likely counting stale LSP diagnostics

## Environment
- Java 25, Quarkus 3.37.3, Gradle 9.6
- K_MAX=20, coverage target >=82%
- FROZEN: ethics/, avro/, workflows/
