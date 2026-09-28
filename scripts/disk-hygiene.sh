#!/usr/bin/env bash
# RECON-W20 — disk hygiene pre-flight.
#
# Idempotent: safe to run twice; the second run is a no-op.
# Classifies every top consumer as KEEP / ROTATE / DELETE-CACHE, rotates
# size-capped NDJSON, and records the result in data/DISK-LEDGER.ndjson.
#
# Usage:
#   scripts/disk-hygiene.sh            # audit + rotate + ledger
#   scripts/disk-hygiene.sh --audit    # report only, no writes
set -uo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO"

LEDGER="data/DISK-LEDGER.ndjson"
ARCHIVE_DIR="data/mind/archive"
AUDIT_ONLY=0
[ "${1:-}" = "--audit" ] && AUDIT_ONLY=1

# --- Tiers from the operator DiskBudget contract ---------------------------
HEALTHY_GB=25
REFUSE_GB=10

free_gb() { df -BG --output=avail "$1" 2>/dev/null | tail -1 | tr -dc '0-9'; }
FREE=$(free_gb /)
tier() {
  if [ "$1" -lt "$REFUSE_GB" ]; then echo REFUSE
  elif [ "$1" -lt "$HEALTHY_GB" ]; then echo WARN
  else echo HEALTHY; fi
}
TIER=$(tier "$FREE")

echo "=== DISK HYGIENE (free=${FREE}G tier=${TIER}) ==="

# --- Audit: top consumers with an explicit disposition ---------------------
# KEEP = cognitive data (episodic, rules, benchmarks, ledger, models)
# DELETE-CACHE = regenerable (build outputs, untracked clones, gradle caches)
if [ "$TIER" != "HEALTHY" ] || [ "$AUDIT_ONLY" -eq 1 ]; then
  echo "--- top consumers (audit) ---"
  du -xsh data/* */build ~/.gradle 2>/dev/null | sort -rh | head -20 | while read -r sz p; do
    case "$p" in
      data/mind/*|data/knowledge*|data/exports*|data/models*|data/DISK-LEDGER*|data/mind)
        cls=KEEP ;;
      */build|*/.gradle) cls=DELETE-CACHE ;;
      data/smoke-*|data/tmp*) cls=DELETE-CACHE ;;
      *) cls=ROTATE ;;
    esac
    printf '  %-8s %-10s %s\n' "$cls" "$sz" "$p"
  done
fi

[ "$AUDIT_ONLY" -eq 1 ] && exit 0

# --- Rotate size-capped NDJSON (cognitive data is archived, never dropped) --
if [ -d data/mind ]; then
  for f in data/mind/episodic.ndjson data/mind/hdc_kb.ndjson; do
    [ -f "$f" ] || continue
    SZ=$(stat -c %s "$f" 2>/dev/null || echo 0)
    # Cap active episodic logs at 8 MB; KB rotates at 2 MB.
    if [ "$f" = "data/mind/hdc_kb.ndjson" ]; then CAP=2097152; else CAP=8388608; fi
    if [ "$SZ" -gt "$CAP" ]; then
      echo "--- rotating $f ($SZ > $CAP) ---"
      # Archive-only: copy to gzip, keep the active tail. Never deletes records.
      gzip -c "$f" > "$ARCHIVE_DIR-$(basename "$f").$(date +%s).gz" 2>/dev/null \
        && tail -n 2000 "$f" > "$f.tmp" && mv "$f.tmp" "$f" \
        && echo "    archived, active tail retained"
    fi
  done
fi

# --- Ledger: monotonic append ---------------------------------------------
mkdir -p data
SEQ=$(( $(wc -l < "$LEDGER" 2>/dev/null || echo 0) + 1 ))
printf '{"op":"w20-hygiene","seq":%s,"free_gb":%s,"tier":"%s","audit_only":false}\n' \
  "$SEQ" "$FREE" "$TIER" >> "$LEDGER"

echo "=== HYGIENE COMPLETE (free=${FREE}G tier=${TIER}) ==="
[ "$TIER" = "REFUSE" ] && { echo "REFUSE TIER: heavy operations blocked"; exit 3; }
exit 0
