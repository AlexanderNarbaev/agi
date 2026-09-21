# Branching Strategy — MATRIX Ecosystem

**Status:** Wave T-01 (Establishment)
**Date:** 2026-09-21
**Author:** Lead Architect

---

## Goal

Separate the **Research Lab** (W1500 research core) from the **Production
Factory** (T-01 through T-10 ecosystem transformation) using a clean
Git Flow that scales to multiple teams and a public release cadence.

---

## Branch Hierarchy

```
main                 ← production releases; only merged via PR; protected
│
├── release/v1.0     ← stabilization branch; only critical hotfixes
│
└── develop          ← active integration; default branch for PRs
     │
     ├── feature/t-01-module-foundation    ← CURRENT WAVE
     ├── feature/t-02-api-gateway
     ├── feature/t-03-docs-bilingual
     ├── feature/t-04-landing-page
     ├── feature/t-05-xai-dashboard
     ├── feature/t-06-audit-compliance
     ├── feature/t-07-economic-model
     ├── feature/t-08-sdks-pilots
     ├── feature/t-09-cicd-observability
     └── feature/t-10-goal-guard-qa
```

### Backup Branches

- `backup-main-pre-merge-<timestamp>` — created before risky merges (e.g.
  the analysis-of-project-goal-implementation-5df35 merge on 2026-09-21).
  Kept indefinitely as immutable snapshots of pre-merge state.

---

## Branch Protection Rules (GitHub Settings)

| Branch | Required Reviews | CI Required | Force-Push | Deletion |
|--------|------------------|-------------|------------|----------|
| `main` | 2 | ✅ green | ❌ blocked | ❌ blocked |
| `release/v*` | 2 | ✅ green | ❌ blocked | ❌ blocked |
| `develop` | 1 | ✅ green | ⚠️ admins only | ⚠️ admins only |
| `feature/*` | 1 (squash) | ✅ green | ✅ allowed | ✅ allowed |
| `fix/*` | 1 | ✅ green | ✅ allowed | ✅ allowed |

---

## Naming Conventions

- `feature/<wave-id>-<short-description>` — new functionality
- `fix/<issue-number>-<short-description>` — bug fixes
- `chore/<description>` — maintenance (deps, configs)
- `docs/<description>` — documentation only
- `release/v<MAJOR>.<MINOR>` — versioned releases
- `backup-<branch>-<timestamp>` — immutable backup snapshots

Examples:
- ✅ `feature/t-02-api-gateway`
- ✅ `fix/1234-merge-conflict-brain-server`
- ✅ `backup-main-pre-merge-20260921-154355`
- ❌ `my-branch` (no prefix)
- ❌ `Feature_X` (wrong case)

---

## Commit Message Convention

Follow Conventional Commits with project-specific extensions:

```
<type>(<scope>): <subject>

[optional body]

[optional footer(s)]
```

### Types

| Type | Purpose |
|------|---------|
| `feat` | New user-visible functionality |
| `fix` | Bug fix |
| `chore` | Maintenance (no behavior change) |
| `docs` | Documentation only |
| `test` | Adding/fixing tests |
| `refactor` | Code change without behavior change |
| `perf` | Performance improvement |
| `security` | Security fix |

### Scopes

`api`, `sdk`, `audit`, `billing`, `observability`, `web`, `docs`,
`core`, `federation`, `ci`, `infra`

### Examples

```
feat(api): add POST /v1/analyze endpoint with JWT auth
fix(sdk): handle null response in MatrixClient.analyze
chore(ci): add branch-validation workflow
docs(architecture): add ECOSYSTEM-LAYOUT.md C4 diagram
```

---

## Merge Strategy

| Source → Target | Method | Squash? |
|-----------------|--------|---------|
| `feature/*` → `develop` | squash | ✅ yes |
| `fix/*` → `develop` | squash | ✅ yes |
| `develop` → `release/v*` | merge commit | ❌ no |
| `release/v*` → `main` | merge commit + tag | ❌ no |
| `release/v*` → `develop` (back-merge) | merge commit | ❌ no |

---

## Release Cadence

| Channel | Cadence | Branch |
|---------|---------|--------|
| Nightly | every push to `develop` | auto-built snapshot |
| Stable | bi-weekly | `release/v*` cutoff |
| LTS | quarterly | tagged on `main` |

---

## Wave Commit Rule (preserved from W1-W1500)

For each transformation wave (T-01 ... T-10):

1. `git add -A`
2. `git commit -m "feat(<scope>): <wave-id> — <description>"`
3. `git push origin <current-branch>`
4. `git push gitverse <current-branch>`
5. Update `SESSION.md` and `WAL.md`
6. Merge to `develop` via PR

---

## CONSTITUTION Enforcement

Every PR to `main` and `develop` MUST pass:

- ✅ All unit tests (matrix-core: 1,119+ tests must stay green)
- ✅ Constitution compliance check (no LLM in runtime paths)
- ✅ SpotBugs static analysis
- ✅ JaCoCo coverage ≥ 82%
- ✅ Branch validation workflow (this strategy)

Violations block merge automatically.

---

## Related Documents

- `docs-v2/architecture/ECOSYSTEM-LAYOUT.md` — C4 diagram of modules
- `CONSTITUTION.md` — Project ethics and constraints
- `WAL.md` — Wave-by-wave progress log
- `SESSION.md` — Latest wave pointer

---

**Last updated:** 2026-09-21 (Wave T-01)
