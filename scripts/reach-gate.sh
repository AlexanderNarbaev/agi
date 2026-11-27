#!/usr/bin/env bash
# CRJ-01 / COMPETENCE-REACH — the independent supervisor gate of the milestone.
#
# The gate is deterministic, offline and READ-ONLY: it verifies the retained evidence and
# writes nothing. A gate that cannot fail is not a gate, and the self-test
# (scripts/reach-gate-selftest.sh) proves that six specific mutations of the evidence are
# each caught by the check they belong to.
#
# Exit code 0 when every check passed, 1 otherwise.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "${here}/reach-gate.py" "$@"
