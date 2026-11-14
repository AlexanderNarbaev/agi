#!/usr/bin/env bash
# B3 — reproduce the Evidence-to-Action demonstration.
#
# Prerequisites: JDK 25 (Gradle toolchain) and the Gradle wrapper of this checkout.
# The positive artifact is written to docs-v2/demos/evidence-to-action/output/; the
# negative controls run in a throwaway directory. The measurement result is written to
# docs-v2/engineering/B3-EVIDENCE-TO-ACTION-RESULT.json.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
./gradlew :matrix-core:test \
  --tests "io.matrix.actions.EvidenceToActionTest" \
  --tests "io.matrix.knowledge.EvidenceToActionDemonstrationTest" \
  --console=plain \
  -Dmatrix.b3.dir="$PWD/docs-v2/demos/evidence-to-action/output" \
  -Dmatrix.b3.result="$PWD/docs-v2/engineering/B3-EVIDENCE-TO-ACTION-RESULT.json"
echo "artifact: docs-v2/demos/evidence-to-action/output/evidence-report.md"
echo "trace:    docs-v2/demos/evidence-to-action/output/.artifact-index.ndjson"
echo "result:   docs-v2/engineering/B3-EVIDENCE-TO-ACTION-RESULT.json"
