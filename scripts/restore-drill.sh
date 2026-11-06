#!/usr/bin/env bash
#
# W48-08 — the restore drill, performed against a COPY of the live database.
#
# WHAT THIS IS
#   The recovery runbook (docs-v2/operations/RECOVERY-RUNBOOK.md) says how to back the
#   epistemic database up and how to restore it, and its §5 says the restore has never
#   been performed: "a backup procedure that has never been restored from is a procedure,
#   not a capability". This script performs it — against a copy, never against the
#   original.
#
# THE RULES THIS SCRIPT FOLLOWS
#   1. THIS DRILL WORKS ON A COPY. The original is never written. It is opened READ-ONLY
#      (the mode=ro URI), and a read-only connection cannot checkpoint the write-ahead
#      log into the database file, so the drill cannot change a byte of it even by
#      accident. The application is never pointed at the original either: the store
#      migrates on open, and a migration of the owner's live database during a drill
#      would be exactly the damage the drill exists to prevent.
#   2. THE DIGEST IS PROVED. The sha256 of the original — and of its write-ahead log and
#      its shared-memory file — is taken before the drill and after it. The drill FAILS
#      when they differ.
#   3. THE COPY IS WAL-SAFE. The copy is produced with VACUUM INTO, the SQLite backup
#      API, over a read-only connection, so it holds the rows the write-ahead log holds.
#      A plain copy of the main file does not, and the drill MEASURES the difference.
#   4. EVERY STAGE PRINTS ITS MEASUREMENTS, and the script exits non-zero when any
#      stage fails. A drill that cannot fail is not a drill.
#
# USAGE
#   bash scripts/restore-drill.sh [--dry-run] [--original PATH] [--workdir PATH] [--keep]
#
#   --dry-run     print what the drill would do and stop; nothing is written
#   --original    the live database (default: data/knowledge/workbench.sqlite)
#   --workdir     where the copies live (default: a fresh directory under the temp root)
#   --keep        keep the working directory after the drill
#
# THE MEASUREMENT ENGINE
#   The measurements are made by io.matrix.knowledge.RestoreDrillProbe, which lives in
#   the test tree of matrix-core and is the current code itself: the migration, the
#   dangling references, the writer lock and the clean shutdown can only be measured by
#   the code that implements them. This script compiles it, drives it, and checks the
#   digests.
#
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT" || exit 1

# ── the constants ────────────────────────────────────────────────────────────

# The default live database of the epistemic store.
readonly DEFAULT_ORIGINAL="data/knowledge/workbench.sqlite"

# The Java home the repository builds with.
readonly DEFAULT_JAVA_HOME="/home/alexandr-narbaev/jdk25"

# The probe: the measurement engine of the drill.
readonly PROBE_CLASS="io.matrix.knowledge.RestoreDrillProbe"
readonly PROBE_SOURCE="matrix-core/src/test/java/io/matrix/knowledge/RestoreDrillProbe.java"

# The classes of the current code.
readonly MAIN_CLASSES="matrix-core/build/classes/java/main"
readonly MAIN_RESOURCES="matrix-core/build/resources/main"

# The jars the probe needs: the SQLite driver, the JSON mapper and the annotations the
# workbench carries.
readonly DEPENDENCY_PATTERN="sqlite-jdbc|slf4j-api|jackson-databind|jackson-core|jackson-annotations|jakarta.annotation-api|jakarta.inject-api|jakarta.enterprise.cdi-api|microprofile-config-api"

# The names of the files whose digests are taken before and after the drill.
readonly ORIGINAL_SIDECARS=("-wal" "-shm")

# ── the output ───────────────────────────────────────────────────────────────

say() { printf '%s\n' "$*"; }
rule() { printf -- '--------------------------------------------------------------------------\n'; }
stage() { rule; printf '%s\n' "$*"; rule; }
fail() { printf 'FAIL: %s\n' "$*"; exit 1; }

# What the drill is and how to run it.
usage() {
    printf '%s\n' "W48-08 — the restore drill, performed against a COPY of the live database."
    printf '%s\n' ""
    printf '%s\n' "  bash scripts/restore-drill.sh [--dry-run] [--original PATH] [--workdir PATH] [--keep]"
    printf '%s\n' ""
    printf '%s\n' "  --dry-run     print what the drill would do and stop; nothing is written"
    printf '%s\n' "  --original    the live database (default: data/knowledge/workbench.sqlite)"
    printf '%s\n' "  --workdir     where the copies live (default: a fresh directory under the temp root)"
    printf '%s\n' "  --keep        keep the working directory after the drill"
    printf '%s\n' ""
    printf '%s\n' "The drill works on a copy. The original is opened READ-ONLY and never through"
    printf '%s\n' "MATRIX, and its digest is compared before and after."
}

# ── the helpers ──────────────────────────────────────────────────────────────

digest_of() {
    sha256sum "$1" | cut -d' ' -f1
}

digest_line() {
    printf '%s  %s\n' "$(digest_of "$1")" "$1"
}

run_probe() {
    "$JAVA" --enable-native-access=ALL-UNNAMED -cp "$PROBE_CLASSPATH" "$PROBE_CLASS" "$@"
}

# The probe's own exit code is the stage's verdict: the probe compares, asserts and
# refuses, so the script does not have to re-implement any of it.
probe_stage() {
    local label="$1"; shift
    printf -- '--- %s\n' "$label"
    if run_probe "$@"; then
        printf -- '--- %s: PASS\n' "$label"
        return 0
    fi
    printf -- '--- %s: FAIL\n' "$label"
    return 1
}

# ── the options ──────────────────────────────────────────────────────────────

ORIGINAL="$DEFAULT_ORIGINAL"
WORKDIR=""
KEEP=0
DRY_RUN=0

while [ $# -gt 0 ]; do
    case "$1" in
        --dry-run) DRY_RUN=1; shift ;;
        --original) ORIGINAL="${2:-}"; shift 2 ;;
        --workdir) WORKDIR="${2:-}"; shift 2 ;;
        --keep) KEEP=1; shift ;;
        --help|-h) usage; exit 0 ;;
        *) printf 'unknown option: %s\n' "$1"; exit 2 ;;
    esac
done

JAVA_HOME="${JAVA_HOME:-$DEFAULT_JAVA_HOME}"
JAVA="$JAVA_HOME/bin/java"
JAVAC="$JAVA_HOME/bin/javac"
TEMP_ROOT="${TMPDIR:-/tmp}"

# ── the preflight ────────────────────────────────────────────────────────────

ORIGINAL_ABS="$(cd "$(dirname "$ORIGINAL")" && pwd)/$(basename "$ORIGINAL")"

if [ ! -f "$ORIGINAL_ABS" ]; then
    fail "the live database is not there: $ORIGINAL_ABS"
fi

# The newest file of a tree, by the instant it was written.
newest_of() {
    local listing
    listing="$(find "$1" -type f -name "$2" -printf '%T@ %p\n' 2>/dev/null | sort -rn)"
    printf '%s' "${listing%%$'\n'*}"
}

if [ "$DRY_RUN" -eq 1 ]; then
    STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
    WORKDIR="${WORKDIR:-$TEMP_ROOT/matrix-restore-drill-$STAMP}"
    stage "W48-08 — the restore drill — DRY RUN"
    say "THIS DRILL WOULD WORK ON A COPY. The original would be opened READ-ONLY and"
    say "never through MATRIX, and its digest would be compared before and after."
    say ""
    say "the live database   : $ORIGINAL_ABS"
    say "the working copy in : $WORKDIR"
    say "the engine          : $PROBE_SOURCE"
    say ""
    say "the stages would be:"
    say "  A  the WAL-safe copy (VACUUM INTO over a read-only connection)"
    say "     the measurement of the original and of the copy, and their comparison"
    say "     the measurement of a plain copy of the main file — the hazard the"
    say "     backup API exists to avoid"
    say "  B  the copy opened under the current code: the migration, the integrity"
    say "     check, the dangling references, the writer lock, the clean shutdown"
    say "     the pre-v17 image of the copy, migrated forward by the current code"
    say "  C  the restore into an isolated target, verified against the copy's own"
    say "     recorded counts and fingerprints"
    say "  D  the digest of the original, compared with the one taken before"
    say ""
    say "nothing was written. This was a dry run."
    exit 0
fi

# ── the working directory ────────────────────────────────────────────────────

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
WORKDIR="${WORKDIR:-$TEMP_ROOT/matrix-restore-drill-$STAMP}"

case "$WORKDIR" in
    "$REPO_ROOT"/*)
        fail "the working directory is inside the repository: $WORKDIR" ;;
esac
case "$ORIGINAL_ABS" in
    "$WORKDIR"/*)
        fail "the working directory would hold the original: $WORKDIR" ;;
esac
case "$(dirname "$ORIGINAL_ABS")" in
    "$WORKDIR"|"$WORKDIR"/*)
        fail "the working directory sits in the directory of the original: $WORKDIR" ;;
esac

# The temporary root itself is not a working directory: the drill would scatter its
# copies and its measurements across every other tool's scratch space, and the
# cleanup below could not tell its own files from somebody else's.
case "$WORKDIR" in
    "$TEMP_ROOT"|"/tmp"|"/var/tmp"|"/var/folders")
        fail "the working directory must be a directory of its own, not the temporary root: $WORKDIR" ;;
esac

# Whether the drill CREATED the working directory. Only a directory the drill
# created is one the drill may remove.
WORKDIR_CREATED=0
if [ ! -d "$WORKDIR" ]; then
    WORKDIR_CREATED=1
fi

mkdir -p "$WORKDIR" || fail "the working directory could not be created: $WORKDIR"
WORKDIR_ABS="$(cd "$WORKDIR" && pwd)"

cleanup() {
    # The drill removes only a directory it created, and only one that sits
    # strictly inside the temporary root. The temporary root itself is never
    # removed, and a directory the operator named is left alone.
    if [ "$KEEP" -eq 0 ] && [ "$WORKDIR_CREATED" -eq 1 ] && [ -n "${WORKDIR_ABS:-}" ]; then
        case "$WORKDIR_ABS" in
            "$TEMP_ROOT"/*)
                rm -rf "$WORKDIR_ABS" ;;
        esac
    fi
}
trap cleanup EXIT

# ── the banner ───────────────────────────────────────────────────────────────

stage "W48-08 — the restore drill"
say "THIS DRILL WORKS ON A COPY."
say "  the live database  : $ORIGINAL_ABS   (opened READ-ONLY, never through MATRIX)"
say "  the working copy  : $WORKDIR_ABS"
say "  the engine        : $PROBE_SOURCE"
say "  the java          : $JAVA_HOME"
say ""
say "The original is opened through the mode=ro URI, so SQLite cannot checkpoint the"
say "write-ahead log into it, and the application is never pointed at it: the store"
say "migrates on open, and a migration of the live database during a drill would be"
say "the damage the drill exists to prevent."

# ── the tools ────────────────────────────────────────────────────────────────

for tool in "$JAVA" "$JAVAC"; do
    [ -x "$tool" ] || fail "the tool is missing: $tool"
done
command -v sha256sum >/dev/null 2>&1 || fail "sha256sum is missing"

# ── the current code ─────────────────────────────────────────────────────────

if [ ! -d "$REPO_ROOT/$MAIN_CLASSES" ]; then
    say ""
    say "the current code is not compiled yet — compiling it (no tests are run)"
    "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :matrix-core:classes -q || fail "the build failed"
fi

STALE=""
NEWEST_CLASS="$(newest_of "$REPO_ROOT/$MAIN_CLASSES" '*.class')"
NEWEST_SOURCE="$(newest_of "$REPO_ROOT/matrix-core/src/main/java" '*.java')"
if [ -n "$NEWEST_CLASS" ] && [ -n "$NEWEST_SOURCE" ] && [ "$NEWEST_SOURCE" -nt "$NEWEST_CLASS" ]; then
    STALE="$NEWEST_SOURCE"
fi
if [ -n "$STALE" ]; then
    say ""
    say "the compiled code is older than the sources — recompiling it (no tests are run)"
    "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :matrix-core:classes -q || fail "the build failed"
fi

DEPENDENCIES="$(tr ':' '\n' < "$REPO_ROOT/scripts/runtime-classpath.snapshot.txt" \
    | grep -E "$DEPENDENCY_PATTERN" | tr '\n' ':')"
[ -n "$DEPENDENCIES" ] || fail "the runtime classpath snapshot holds none of the jars the probe needs"

mkdir -p "$WORKDIR_ABS/classes"
"$JAVAC" -nowarn -cp "$REPO_ROOT/$MAIN_CLASSES:$DEPENDENCIES" \
    -d "$WORKDIR_ABS/classes" "$REPO_ROOT/$PROBE_SOURCE" \
    || fail "the probe did not compile"

PROBE_CLASSPATH="$WORKDIR_ABS/classes:$REPO_ROOT/$MAIN_CLASSES:$REPO_ROOT/$MAIN_RESOURCES:$DEPENDENCIES"

# ── stage 0: the digest before ───────────────────────────────────────────────

stage "STAGE 0 — the digest of the original, BEFORE the drill"
say "the original, its write-ahead log and its shared-memory file:"
BEFORE_MAIN="$(digest_of "$ORIGINAL_ABS")"
BEFORE_SIDECARS=()
say "  $BEFORE_MAIN  $ORIGINAL_ABS"
for sidecar in "${ORIGINAL_SIDECARS[@]}"; do
    if [ -f "$ORIGINAL_ABS$sidecar" ]; then
        BEFORE_SIDECARS+=("$(digest_of "$ORIGINAL_ABS$sidecar")")
        say "  ${BEFORE_SIDECARS[-1]}  $ORIGINAL_ABS$sidecar"
    else
        BEFORE_SIDECARS+=("")
        say "  (absent)  $ORIGINAL_ABS$sidecar"
    fi
done

# ── stage A: the fidelity of the copy ────────────────────────────────────────

SNAPSHOT="$WORKDIR_ABS/snapshot.sqlite"
ORIGINAL_JSON="$WORKDIR_ABS/original.json"
SNAPSHOT_JSON="$WORKDIR_ABS/snapshot.json"
NAIVE="$WORKDIR_ABS/naive-main-file-copy.sqlite"
NAIVE_JSON="$WORKDIR_ABS/naive.json"

stage "STAGE A — the fidelity of the copy"
say "A1 the WAL-safe copy. The method is VACUUM INTO — the SQLite backup API — over a"
say "   READ-ONLY connection to the original. A plain copy of the main file is not a"
say "   backup: it is the state of the last checkpoint, and the rows the write-ahead log"
say "   holds are not in it. The copy is produced from the live database's own"
say "   transaction-consistent image, and the original stays openable and unchanged."
probe_stage "A1 the WAL-safe copy" backup --source "$ORIGINAL_ABS" --target "$SNAPSHOT" \
    || fail "stage A1 failed"

say ""
say "A2 the measurement of the original (read-only) and of the copy."
probe_stage "A2 the measurement of the original" measure --db "$ORIGINAL_ABS" --out "$ORIGINAL_JSON" \
    || fail "stage A2 failed"
probe_stage "A2 the measurement of the copy" measure --db "$SNAPSHOT" --out "$SNAPSHOT_JSON" \
    || fail "stage A2 failed"

say ""
say "A3 the comparison: integrity, schema version, the table list, the row count of every"
say "   table and the per-table fingerprint of every table must agree."
probe_stage "A3 the original and the copy are the same database" \
    compare --a "$ORIGINAL_JSON" --b "$SNAPSHOT_JSON" \
    || fail "stage A3 failed: the copy is not a faithful image of the original"

say ""
say "A4 the hazard the backup API exists to avoid: a plain copy of the MAIN FILE only."
say "   This copy is what a cp of data/knowledge/workbench.sqlite would produce, and the"
say "   drill measures it rather than assuming what it would lose."
cp "$ORIGINAL_ABS" "$NAIVE" || fail "stage A4 failed"
probe_stage "A4 the measurement of the plain copy" measure --db "$NAIVE" --out "$NAIVE_JSON" \
    || fail "stage A4 failed"

# ── stage B: the current code ────────────────────────────────────────────────

STAGE_B="$WORKDIR_ABS/stage-b.sqlite"
STAGE_B_JSON="$WORKDIR_ABS/stage-b.json"
V16_IMAGE="$WORKDIR_ABS/pre-v17-image.sqlite"
V16_JSON="$WORKDIR_ABS/pre-v17-open.json"

stage "STAGE B — the current-code compatibility of the copy"
say "B1 the copy is opened under the CURRENT code: the store's additive migration, the"
say "   integrity check, the dangling references, the writer lock and the clean shutdown."
cp "$SNAPSHOT" "$STAGE_B" || fail "stage B1 failed"
probe_stage "B1 the copy under the current code" open --db "$STAGE_B" --out "$STAGE_B_JSON" \
    || fail "stage B1 failed"

say ""
say "B2 the migration path itself. The copy is at the current schema version, so the"
say "   migration chain has nothing to do; the pre-v17 image of the copy is therefore"
say "   DERIVED (the tables v17, v18 and v19 added are dropped and the version is set"
say "   back), and the current code is asked to migrate it forward. The migrated image"
say "   must then hold exactly what the copy holds."
cp "$SNAPSHOT" "$V16_IMAGE" || fail "stage B2 failed"
probe_stage "B2 the derivation of the pre-v17 image" downgrade-v16 --db "$V16_IMAGE" \
    || fail "stage B2 failed"
probe_stage "B2 the pre-v17 image under the current code" \
    open --db "$V16_IMAGE" --expected "$SNAPSHOT_JSON" --out "$V16_JSON" \
    || fail "stage B2 failed: the migration did not reproduce the copy"

# ── stage C: the restore ─────────────────────────────────────────────────────

RESTORE_DIR="$WORKDIR_ABS/restore"
RESTORE_TARGET="$RESTORE_DIR/workbench.sqlite"

stage "STAGE C — the restore, into an isolated target"
mkdir -p "$RESTORE_DIR" || fail "stage C failed"
say "C1 the target is damaged on purpose: the drill restores a damaged database, because a"
say "   restore that is never performed is a procedure, not a capability."
cp "$SNAPSHOT" "$RESTORE_TARGET" || fail "stage C1 failed"
printf 'DAMAGED' | dd of="$RESTORE_TARGET" bs=1 seek=0 conv=notrunc status=none \
    || fail "stage C1 failed"
say "   the damaged file is $RESTORE_TARGET"

say ""
say "C2 the restore, exactly as the runbook §3 writes it — into the isolated target, and"
say "   never over the original."
mv "$RESTORE_TARGET" "$RESTORE_TARGET.damaged-$STAMP" || fail "stage C2 failed"
cp "$SNAPSHOT" "$RESTORE_TARGET" || fail "stage C2 failed"
rm -f "$RESTORE_TARGET-wal" "$RESTORE_TARGET-shm" || fail "stage C2 failed"
say "   the damaged file was kept as $RESTORE_TARGET.damaged-$STAMP"

say ""
say "C3 the verification: the restored database must hold what the copy recorded — the"
say "   same row counts, the same per-table fingerprints, the same schema digest — and it"
say "   must open under the current code."
probe_stage "C3 the restored database against the copy's own record" \
    open --db "$RESTORE_TARGET" --expected "$SNAPSHOT_JSON" --out "$WORKDIR_ABS/restore.json" \
    || fail "stage C3 failed: the restore does not reproduce the copy"

# ── stage D: the digest after ────────────────────────────────────────────────

stage "STAGE D — the digest of the original, AFTER the drill"
say "the original, its write-ahead log and its shared-memory file:"
AFTER_MAIN="$(digest_of "$ORIGINAL_ABS")"
AFTER_SIDECARS=()
say "  $AFTER_MAIN  $ORIGINAL_ABS"
for sidecar in "${ORIGINAL_SIDECARS[@]}"; do
    if [ -f "$ORIGINAL_ABS$sidecar" ]; then
        AFTER_SIDECARS+=("$(digest_of "$ORIGINAL_ABS$sidecar")")
        say "  ${AFTER_SIDECARS[-1]}  $ORIGINAL_ABS$sidecar"
    else
        AFTER_SIDECARS+=("")
        say "  (absent)  $ORIGINAL_ABS$sidecar"
    fi
done
say ""
say "before: $BEFORE_MAIN"
say "after : $AFTER_MAIN"

UNCHANGED=1
if [ "$BEFORE_MAIN" != "$AFTER_MAIN" ]; then
    UNCHANGED=0
fi
for index in "${!BEFORE_SIDECARS[@]}"; do
    if [ "${BEFORE_SIDECARS[$index]}" != "${AFTER_SIDECARS[$index]}" ]; then
        UNCHANGED=0
    fi
done

if [ "$UNCHANGED" -eq 1 ]; then
    say ""
    say "the original is BYTE-IDENTICAL before and after the drill."
else
    fail "the original changed during the drill — the drill is not safe"
fi

# ── the verdict ──────────────────────────────────────────────────────────────

stage "THE VERDICT"
say "  stage A  the copy is a faithful image of the original      PASS"
say "  stage B  the copy opens under the current code              PASS"
say "  stage C  the restore reproduces the copy                    PASS"
say "  stage D  the original is byte-identical                     PASS"
say ""
say "The drill worked on a copy. The original was never written, and the digest above"
say "is the proof."
if [ "$KEEP" -eq 1 ]; then
    say "The measurements are in the working directory, which was kept:"
    say "  $WORKDIR_ABS"
elif [ "$WORKDIR_CREATED" -eq 1 ]; then
    say "The measurements were in the working directory, which the drill removed:"
    say "  $WORKDIR_ABS"
else
    say "The measurements are in the working directory, which was left in place:"
    say "  $WORKDIR_ABS"
fi
exit 0
