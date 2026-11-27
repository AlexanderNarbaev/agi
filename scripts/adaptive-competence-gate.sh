#!/usr/bin/env bash
#
# CRJ-01 / ADAPTIVE-COMPETENCE — the deterministic supervisor gate of the milestone.
#
# The gate is a Python program; this wrapper is the shell entry point the test catalogue
# names, so that the gate is invoked the same way as every other gate in this repository.
# It is read-only, offline and deterministic, and it never runs a build.
#
# Usage:
#   bash scripts/adaptive-competence-gate.sh
#   bash scripts/adaptive-competence-gate.sh --json /tmp/adaptive-gate.json
#
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT" || exit 1
exec python3 scripts/adaptive-competence-gate.py "$@"
