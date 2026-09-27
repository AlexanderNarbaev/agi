# RECON-W15 Report — True Persistence & Contradiction Intelligence (closes L-2, L-3)

> Date: 2026-09-27
> Status: L-2 closed (load-on-boot), L-3 closed (contradiction quarantine)

## Built this wave

### 1. Article VII RFC — BirRegistry persistence
- `matrix-core/.../BirRegistryPersistence.java` — append-only NDJSON
  persistence layer for `BirRegistry`. Each register event serialized
  to one JSON line.
- `kWordsForPersistence()` and `clausesForPersistence()` package-private
  accessors on `ClauseSetForm` so persistence can read internal state.
- `BirKnowledgeBase` — high-level wrapper with load-on-boot + persistent
  register. Re-derivation stays as repair path.

### 2. /v1/bir + /v1/conflicts endpoints
- `GET /v1/bir` — registry stats + on-disk line count + quarantine size.
- `POST /v1/bir {"id": "..."}` — register a rule with contradiction check.
- `GET /v1/conflicts` — list quarantined contradictions.

### 3. Semantic contradiction detection (L-3 closure)
- POS bits → FNV-1a precondition hash. On collision, compare clause pos+neg.
- Different conclusions with same precondition → quarantined, NOT registered.
- Contradictions accumulate in `quarantined` list, exposed via `/v1/conflicts`.

### Tests
- `BirRegistryPersistenceTest` (2 tests): register-append-replay-roundtrip +
  empty file replay.
- `BirKnowledgeBaseRestartTest` (2 tests): registry state survives close+reopen
  + contradiction quarantines don't silently overwrite.

## What does NOT work yet (HONEST LIMITATION)
- Contradiction `quarantined` list is IN-MEMORY only — does not persist to disk.
  Carry-forward to W19+ (continuation list is ephemeral until process restart).
- Contradiction detection is POS-only fingerprint with full-clause conclusions;
  no semantic-overlap (HDC similarity) yet (planned in W19+).

## Live verification (operator-reproducible)
```bash
# Restart-survival proof:
TOKEN=$(curl -s -X POST http://localhost:8765/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"pro@test.com"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')
curl -X POST http://localhost:8765/v1/bir -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"id":"persistent-rule"}'
# Restart gateway, then:
curl http://localhost:8765/v1/bir -H "Authorization: Bearer $TOKEN"
# → registry_size=1, on_disk_lines=1 (RULE SURVIVED)

# Contradiction flow (programmatic):
curl http://localhost:8765/v1/conflicts -H "Authorization: Bearer $TOKEN"
# → "engine":"BirKnowledgeBase.contradiction","count":N,"conflicts":[...]
```

## Article VIII compliance maintained
- `ProdCallerExistsTest` — new wrapping class has production caller (/v1/bir).
- `NoFutureClaimsTest` — endpoint reports actual quarantine counts.
- `SingleInstanceGuardTest` — single BirKnowledgeBase per gateway.
- Every register appends to disk; every contradiction is exposed.

## Honest limitations carry-forward
- Contradiction list is in-memory only (survives only until restart).
- POS-only fingerprint may cause false positives (treats semantically
  similar preconditions as identical); refine to Jaccard overlap later.
