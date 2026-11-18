#!/usr/bin/env bash
# The owner's live store: the identity migration, executed.
#
# Approval basis: the owner's OD-067 (the live store is migrated on a copy first) and the
# owner's answer to D1-LIVE-STORE-MIGRATION-019 (2026-11-18, option A — migrate it now).
#
# This script is a thin wrapper around the Gradle task, so the launcher runs against the
# module's own runtime classpath:
#
#   scripts/migrate-live-store.sh [path-to-live-store]
#
# The launcher backs the live file up, migrates a copy, audits it, rehearses the rollback
# and only then replaces the live file. It ABORTS before the live file is touched if the
# audit is not clean or the rollback does not restore the workspace. The backup is kept.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-$HOME/jdk25}"
LIVE="${1:-matrix-core/data/knowledge/workbench.sqlite}"

if [ ! -f "$LIVE" ]; then
  echo "there is no live store at $LIVE" >&2
  exit 2
fi

exec flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:liveStoreMigration \
  -PliveStore="$LIVE" --offline --console=plain
