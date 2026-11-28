#!/usr/bin/env bash
# CRJ-01 / EPISTEMIC-NECESSITY — retain the evidence of the last run.
#
# The retained evidence is the XML the suite produced and the rows and the summary the suite
# wrote; the volatile test output under matrix-core/build/** is NOT retained. The manifest is
# refreshed after every copy, so the directory always describes itself.
#
# Usage:
#   ./scripts/necessity-retain-evidence.sh green   # retain the GREEN run
#   ./scripts/necessity-retain-evidence.sh red     # retain the RED reproduction
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

EVIDENCE="docs-v2/engineering/evidence/crj01-necessity"
RESULTS="matrix-core/build/test-results/test"
MODE="${1:-green}"

case "$MODE" in
  green)
    SOURCE="$RESULTS/TEST-io.matrix.knowledge.EpistemicNecessityServedTest.xml"
    TARGET="$EVIDENCE/TEST-io.matrix.knowledge.EpistemicNecessityServedTest.xml"
    ;;
  red)
    SOURCE="${CRJ01_NECESSITY_RED_XML:-$RESULTS/TEST-io.matrix.knowledge.EpistemicNecessityServedTest.xml}"
    TARGET="$EVIDENCE/CRJ-01-NECESSITY-REPRODUCTION-no-prerequisite.xml"
    ;;
  *)
    echo "usage: $0 green|red" >&2
    exit 2
    ;;
esac

if [ ! -f "$SOURCE" ]; then
  echo "the run's XML is absent: $SOURCE" >&2
  exit 1
fi

mkdir -p "$EVIDENCE"
cp "$SOURCE" "$TARGET"

python3 - "$EVIDENCE" <<'PY'
import hashlib
import pathlib
import sys

directory = pathlib.Path(sys.argv[1])
lines = []
for path in sorted(directory.iterdir()):
    if not path.is_file() or path.name == "CHECKSUMS.sha256":
        continue
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    lines.append("%s  %s" % (digest, path.name))
(directory / "CHECKSUMS.sha256").write_text("\n".join(lines) + "\n", encoding="utf-8")
print("the manifest covers %d file(s)" % len(lines))
PY

echo "retained $TARGET"
