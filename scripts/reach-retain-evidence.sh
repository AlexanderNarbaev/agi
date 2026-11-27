#!/usr/bin/env bash
# CRJ-01 / COMPETENCE-REACH — retain the evidence of the milestone.
#
# WHAT IT DOES. It copies the JUnit XML of the LAST RUN into the retained evidence directory
# under its retained name, and refreshes the manifest over that directory with the SAME rule
# the suite's own manifest refresh uses (every .md, .json and .xml file, sorted by name, the
# digest and the name separated by two spaces).
#
# WHY IT IS A SCRIPT AND NOT A COPY-PASTE. The retained XML is EVIDENCE: a reader must be able
# to say which run produced it and how it was put there. A script that says "the last run's
# XML, under this name, with this manifest rule" is a procedure; a manual copy is a claim.
#
# THE TWO MODES.
#   reach-retain-evidence.sh red    — the reproduction run (the criterion switch removed).
#                                     The XML is retained as CRJ-01-REACH-REPRODUCTION-no-criterion.xml.
#   reach-retain-evidence.sh green  — the passing run. The XML is retained as
#                                     TEST-io.matrix.knowledge.CompetenceReachServedTest.xml.
#
# THE VOLATILE OUTPUT STAYS OUT. The build directory holds the volatile run; the retained
# directory holds the bytes a reader may re-check against the manifest.
set -euo pipefail

mode="${1:-}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
evidence="${root}/docs-v2/engineering/evidence/crj01-reach"
build="${root}/matrix-core/build/test-results/test"
source="${build}/TEST-io.matrix.knowledge.CompetenceReachServedTest.xml"

case "${mode}" in
  red) target="CRJ-01-REACH-REPRODUCTION-no-criterion.xml" ;;
  green) target="TEST-io.matrix.knowledge.CompetenceReachServedTest.xml" ;;
  *)
    echo "usage: $0 {red|green}" >&2
    exit 2
    ;;
esac

if [ ! -f "${source}" ]; then
  echo "there is no JUnit XML of the last run at ${source}" >&2
  exit 1
fi

mkdir -p "${evidence}"
cp "${source}" "${evidence}/${target}"

# THE CORPUS INSTRUMENT'S XML travels with the measurement: the scope's evidence names two
# files, and a file the scope names but the evidence does not hold is a claim, not evidence.
corpus_xml="${build}/TEST-io.matrix.crj.corpus.Crj01ReachCorpusReportBuilder.xml"
if [ -f "${corpus_xml}" ]; then
  cp "${corpus_xml}" "${evidence}/TEST-io.matrix.crj.corpus.Crj01ReachCorpusReportBuilder.xml"
fi

# THE MANIFEST, with the suite's own rule.
python3 - "${evidence}" <<'PY'
import hashlib
import pathlib
import sys

directory = pathlib.Path(sys.argv[1])
names = sorted(
    path.name for path in directory.iterdir()
    if path.is_file() and path.suffix in {".md", ".json", ".xml"}
    and path.name != "CHECKSUMS.sha256"
)
lines = []
for name in names:
    digest = hashlib.sha256((directory / name).read_bytes()).hexdigest()
    lines.append(f"{digest}  {name}\n")
(directory / "CHECKSUMS.sha256").write_text("".join(lines), encoding="utf-8")
print(f"the manifest covers {len(names)} file(s)")
PY

echo "retained ${target}"
