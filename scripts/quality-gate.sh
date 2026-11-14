#!/usr/bin/env bash
# RECON-W30 — clean-code gate: static analysis with zero magic numbers.
#
# The wave plan asks for a committed, runnable check that fails on new violations, with
# magic numbers as the headline rule. This is that check. It is a script rather than a
# detekt/PMD config because neither is available on this host (`detekt` is absent) and a
# gate that cannot run is not a gate — the same reasoning that made hardware-probe.sh
# record missing tools instead of assuming them.
#
# What it enforces:
#   1. MAGIC-1  numeric literals in shell scripts outside matrix.env and its whitelist
#   2. MAGIC-2  numeric literals in production Java outside named constants
#   3. CFG-1    the tuning env file exists and every variable is provenance-tagged
#   4. FROZEN-1 the five frozen zones have zero diff against the wave baseline
#   5. DOCS-1   the documentation links and the stable identifiers resolve
#               (W46.3; the checker is scripts/docs-link-check.py)
#   6. TEST-1   the test evidence catalogue agrees with the evidence on this
#               machine (W46.3; scripts/validate-test-catalogue.py)
#   6b. CURRENT-1 the current-state documents do not contradict each other
#                (EVID-BASE; the checker is scripts/validate-current-state.py)
#   7. RETENTION-1 the backup retention policy proves itself on a synthetic tree
#   8. SNAPSHOT-RETENTION-1 the snapshot retention policy proves itself, the
#               snapshot payload's size bound is configured, and the dry run
#               leaves the owner's database byte-identical (W48.7)
#
# Exit 0 = clean, 1 = violations found. New violations BLOCK; a baseline file records
# what already exists so this gate is adoptable without a 3000-line cleanup first.
#
# Usage:
#   scripts/quality-gate.sh                  # report, exit 1 on any NEW violation
#   scripts/quality-gate.sh --report         # report only, always exit 0
#   scripts/quality-gate.sh --update-baseline  # accept current state as the baseline
#
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT" || exit 1

BASELINE_FILE="docs-v2/quality/quality-baseline.txt"
# RECON-W43.0: the recorded, time-bounded exceptions for the frozen zones.
FROZEN_EXCEPTIONS_FILE="docs-v2/quality/frozen-zone-baseline.txt"
MODE="${1:-}"
VIOLATIONS=0

# The wave this gate was introduced in. Violations are measured against this ref, so a
# pre-existing problem is grandfathered and a NEW one is a merge blocker.
readonly BASELINE_REF="b144563e"

# Shell scripts that legitimately contain numeric literals: they are either test
# harnesses whose numbers ARE the subject, or infrastructure whose values are documented
# at the point of use.
readonly SHELL_ALLOWLIST=(
    "scripts/benchmark-regression.sh"
    "scripts/fresh-clone-smoke.sh"
    "scripts/two-node-federation.sh"
    "scripts/w13-live-benchmark.sh"
)

# Production Java exempt from MAGIC-2, with the reason recorded per package rather than
# per file, so a new file in an exempt package is visibly a decision.
readonly JAVA_EXEMPT_PACKAGES=(
    # Protocol and wire-format constants: a 4-byte length prefix, a 16-byte UUID, a
    # 64-bit word are not magic numbers, they are the specification.
    "io.matrix.protocol"
    "io.matrix.federation"
    # Numeric research kernels whose parameters are the experiment definition and are
    # named constants in their own right; enforced by review, not by this gate.
    "io.matrix.research"
    "io.matrix.consciousness"
)

# Numeric literals that are structural, not tuned: array indices, small fixed-width
# fields, and powers of two used as bit widths.
# Literals that are constants of a published standard rather than tunables. Omitting
# these produced 166 "magic number" hits on the first run, the large majority of them
# HTTP status codes (200/201/400/401/403/404/500) and the FNV-1a 64-bit offset basis
# (1099511628211). A gate that reports a protocol constant as a tunable trains its
# readers to ignore it, which is the same as having no gate.
#   - 3-digit numbers 100-599 are the entire HTTP status-code space
#   - 1099511628211 is the FNV-64 offset basis; 14695981039346656037 is its prime
#   - powers of two and 255/256 are bit widths
readonly STRUCTURAL_LITERALS='(^|[^A-Za-z0-9_])(0|1|2|-1|8|16|32|64|100|101|200|201|202|204|400|401|403|404|405|409|422|429|500|502|503|1099511628211|14695981039346656037|128|255|256|1024)([^A-Za-z0-9_]|$)'

have() { command -v "$1" >/dev/null 2>&1; }

report() { printf '%-10s %s\n' "$1" "$2"; }

# ---------------------------------------------------------------------------
# MAGIC-1: numeric literals in shell scripts
# ---------------------------------------------------------------------------
check_shell_magic() {
    local found=0 f
    SHELL_CANDIDATES=0
    for f in scripts/*.sh; do
        [ -f "$f" ] || continue
        # matrix.env IS the constants file; exempting it is the whole point.
        [ "$f" = "scripts/matrix.env" ] && continue
        local allowed=0 a
        for a in "${SHELL_ALLOWLIST[@]}"; do
            [ "$f" = "$a" ] && allowed=1
        done
        [ "$allowed" -eq 1 ] && continue

        # First pass at this rule flagged every 2+ digit number in the file, which is
        # useless: the top hits were ANSI SGR escapes (\033[0;31m), human-readable echo
        # text ("504 probes across 9 categories") and kubectl timeouts (--timeout=120s).
        # None is a tunable, and a queue of 16 files that are all noise teaches its
        # reader to skip it — which is the same as no gate.
        #
        # Narrowed to what the rule can actually decide: a literal in an ASSIGNMENT or
        # ARITHMETIC context, excluding
        #   - a `readonly` declaration and any UPPERCASE-named assignment. Naming a
        #     value in SCREAMING_CASE *is* extracting it to a constant: `HEALTHY_GB=25`
        #     is a tunable that has been named, and flagging it is the rule refusing to
        #     recognise the exact thing it exists to encourage;
        #   - a comment, a quoted human-facing string, an ANSI escape, a URL;
        #   - byte/magnitude divisors (1048576 = MiB, 1024 = KiB).
        local hits
        hits="$(grep -nE '[^A-Za-z0-9_"-][0-9]{2,}' "$f" 2>/dev/null \
            | grep -vE '^[0-9]+:[[:space:]]*(#|readonly|\.)' \
            | grep -vE 'https?://' \
            | grep -vE "\\033\\[|\\e\\[" \
            | grep -vE '^\s*[0-9]+:[[:space:]]*(echo|printf|log|warn|info)\b' \
            | grep -vE '(^|[^A-Za-z0-9_])(echo|printf)[^|]*"[0-9]' \
            | grep -vE '^[0-9]+:[[:space:]]*[A-Z_][A-Z0-9_]*=' \
            | grep -vE '^[0-9]+:.*(1048576|1024|1000000|3600)' \
            || true)"
        if [ -n "$hits" ]; then
            local n
            n="$(printf '%s\n' "$hits" | wc -l)"
            found=$((found + n))
            printf '  MAGIC-1 %s (%s literal(s))\n' "$f" "$n"
            printf '%s\n' "$hits" | head -3 | sed 's/^/           /'
        fi
    done
    SHELL_CANDIDATES=$found
    report MAGIC-1 "shell literal candidates: $found  (occurrences, not proven violations)"
}

# ---------------------------------------------------------------------------
# MAGIC-2: numeric literals in production Java
# ---------------------------------------------------------------------------
check_java_magic() {
    local exempt_re="" p
    CANDIDATE_FILES=0
    for p in "${JAVA_EXEMPT_PACKAGES[@]}"; do
        exempt_re="${exempt_re}|${p//./\\.}"
    done
    exempt_re="${exempt_re#|}"

    local total=0
    for m in matrix-core matrix-brain-runtime matrix-api-gateway; do
        [ -d "$m/src/main/java" ] || continue
        while IFS= read -r f; do
            # Skip exempt packages.
            if [ -n "$exempt_re" ] && printf '%s' "$f" | grep -qE "/$exempt_re/"; then
                continue
            fi
            # Numeric literals >= 3 digits, excluding the structural set and excluding
            # lines that define a named constant.
            local hits
            # RECON-W43.0: the structural filter used to run on the NUMBERED
            # line, and the number itself could match it — grep -n prints
            # "2:...", and "2" is in the structural set, so a literal on
            # line 2 (or 8, 16, 32, 64, 100, 101, 200, 400, 401, ...) was
            # filtered out along with the line number. The gate was blind
            # on thirty line numbers. Found by deliberately injecting a
            # violation and watching the gate stay green.
            #
            # The filter now runs on the CONTENT: the line-number prefix is
            # stripped before the structural test and restored afterwards.
            hits="$(grep -nE '(^|[^A-Za-z0-9_.])[0-9]{3,}([LlFfDd]?)([^A-Za-z0-9_]|$)' "$f" 2>/dev/null \
                | grep -vE 'static final|final static|// |/\*|\* ' \
                | awk -v structural="$STRUCTURAL_LITERALS" '{ content=$0; sub(/^[0-9]+:/, "", content); if (content !~ structural) print }' \
                || true)"
            if [ -n "$hits" ]; then
                local n
                n="$(printf '%s\n' "$hits" | wc -l)"
                total=$((total + n))
                printf '  MAGIC-2 %s (%s literal(s))\n' "$f" "$n"
                printf '%s\n' "$hits" | head -2 | sed 's/^/           /'
            fi
        done < <(find "$m/src/main/java" -name '*.java' 2>/dev/null)
    done
    CANDIDATE_FILES=$total
    report MAGIC-2 "java literal candidates: $total  (occurrences, not proven violations)"
}

# ---------------------------------------------------------------------------
# CFG-1: the tuning env file must exist and be provenance-tagged
# ---------------------------------------------------------------------------
check_env_provenance() {
    local envf="scripts/matrix.env"
    if [ ! -f "$envf" ]; then
        report CFG-1 "MISSING $envf — tuning constants have no home"
        VIOLATIONS=$((VIOLATIONS + 1))
        return
    fi
    # Every non-comment assignment should sit near a provenance marker. Counting tagged
    # assignments against total assignments is a proxy that catches a value added with
    # no reasoning at all.
    local assigned tagged
    assigned="$(grep -cE '^[A-Z_]+="\$\{[A-Z_]+:-' "$envf" 2>/dev/null || echo 0)"
    # Count the tag anywhere in a comment: provenance is frequently written mid-sentence
    # in a wrapped comment block, and requiring the tag to start the line undercounted a
    # fully documented file as untagged.
    tagged="$(grep -cE '(MEASURED|DERIVED|CONVENTION)' "$envf" 2>/dev/null || echo 0)"
    if [ "$tagged" -lt 4 ]; then
        printf '  CFG-1 %s has %s assignments but only %s provenance tags\n' "$envf" "$assigned" "$tagged"
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
    report CFG-1 "matrix.env: $assigned assignments, $tagged provenance tags"
}

# ---------------------------------------------------------------------------
# FROZEN-1: frozen zones must be untouched
# ---------------------------------------------------------------------------
check_frozen() {
    if ! have git; then
        report FROZEN-1 "git unavailable — SKIPPED, not passed"
        return
    fi
    if ! git rev-parse --verify "$BASELINE_REF" >/dev/null 2>&1; then
        report FROZEN-1 "baseline ref $BASELINE_REF not found — SKIPPED, not passed"
        return
    fi
    local total=0 z
    for z in ".github/" "CONSTITUTION.md" "AGENTS.md" "ethics/" \
             "matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/EvalBattery.java"; do
        local n accepted expiry
        n="$(git diff "$BASELINE_REF" -- "$z" 2>/dev/null | wc -l)"
        accepted="$(frozen_exception_lines "$z")"
        if [ "$accepted" -lt 0 ]; then
            # No exception is recorded for this zone: it must be untouched.
            if [ "$n" -ne 0 ]; then
                printf '  FROZEN-1 %s has %s diff lines vs %s and no recorded exception\n' \
                       "$z" "$n" "$BASELINE_REF"
                total=$((total + 1))
            fi
            continue
        fi
        expiry="$(frozen_exception_expiry "$z")"
        if [ -n "$expiry" ] && [ "$(date +%s)" -gt "$(date -d "$expiry" +%s 2>/dev/null || echo 0)" ]; then
            printf '  FROZEN-1 %s: the recorded exception expired on %s\n' "$z" "$expiry"
            total=$((total + 1))
            continue
        fi
        if [ "$n" -gt "$accepted" ]; then
            printf '  FROZEN-1 %s has %s diff lines vs %s; the exception accepts %s\n' \
                   "$z" "$n" "$BASELINE_REF" "$accepted"
            total=$((total + 1))
        fi
    done
    if [ "$total" -eq 0 ]; then
        report FROZEN-1 "all 5 frozen zones within their recorded exceptions vs $BASELINE_REF"
    else
        VIOLATIONS=$((VIOLATIONS + total))
    fi
}

# The accepted diff-line count for a frozen zone, or -1 when none is recorded.
frozen_exception_lines() {
    local zone="$1"
    [ -f "$FROZEN_EXCEPTIONS_FILE" ] || { echo -1; return; }
    local row
    row="$(grep -E "^[[:space:]]*${zone//\//\\/}[[:space:]]*\|" "$FROZEN_EXCEPTIONS_FILE" 2>/dev/null | head -1)"
    [ -n "$row" ] || { echo -1; return; }
    echo "$row" | awk -F'|' '{gsub(/[[:space:]]/,"",$2); print ($2 ~ /^[0-9]+$/) ? $2 : -1}'
}

# The expiry date of a frozen zone's exception, or an empty string.
frozen_exception_expiry() {
    local zone="$1"
    [ -f "$FROZEN_EXCEPTIONS_FILE" ] || { echo ""; return; }
    local row
    row="$(grep -E "^[[:space:]]*${zone//\//\\/}[[:space:]]*\|" "$FROZEN_EXCEPTIONS_FILE" 2>/dev/null | head -1)"
    [ -n "$row" ] || { echo ""; return; }
    echo "$row" | awk -F'|' '{gsub(/[[:space:]]/,"",$4); print $4}'
}

echo "=============================================="
echo "  MATRIX clean-code gate"
echo "  baseline ref: $BASELINE_REF"
echo "=============================================="

# ---------------------------------------------------------------------------
# DOCS-1: the documentation links and the stable identifiers
# ---------------------------------------------------------------------------
#
# W46.3. The link checker is a separate tool (scripts/docs-link-check.py) because it
# is useful on its own; the gate calls it so that a broken link blocks the build.
#
# The checker's own strict set excludes the archive and the historical waves: they
# describe the tree as it was, and their broken references are the history. A
# current document that points at a document that is not there IS a defect, and it
# fails the gate.
check_docs_links() {
    local checker="$REPO_ROOT/scripts/docs-link-check.py"
    if [ ! -f "$checker" ]; then
        report DOCS-1 "the link checker is absent: scripts/docs-link-check.py"
        VIOLATIONS=$((VIOLATIONS + 1))
        return
    fi
    local output
    if ! output="$(python3 "$checker" --root "$REPO_ROOT" --strict 2>&1)"; then
        printf '%s\n' "$output" | grep -E "BROKEN_LINK|BROKEN_ANCHOR|MISSING_PATH|UNDEFINED_IDENTIFIER|DUPLICATE_IDENTIFIER" | head -20
        report DOCS-1 "the documentation links do not resolve"
        VIOLATIONS=$((VIOLATIONS + 1))
        return
    fi
    report DOCS-1 "the documentation links resolve"
}

# ---------------------------------------------------------------------------
# TEST-1: the test evidence catalogue against the evidence on this machine
# ---------------------------------------------------------------------------
#
# The catalogue is the project's claim about its own baseline. A stale claim is
# worse than no claim, so the gate compares the catalogue with the JUnit XML
# that the last run left behind.
check_test_catalogue() {
    local checker="$REPO_ROOT/scripts/validate-test-catalogue.py"
    if [ ! -f "$checker" ]; then
        report TEST-1 "the catalogue validator is absent"
        VIOLATIONS=$((VIOLATIONS + 1))
        return
    fi
    local output
    if ! output="$(python3 "$checker" --root "$REPO_ROOT" --strict 2>&1)"; then
        printf '%s\n' "$output" | grep -E "STALE_COUNT|UNKNOWN_COMMIT|MISSING_" | head -20
        report TEST-1 "the test catalogue is stale"
        VIOLATIONS=$((VIOLATIONS + 1))
        return
    fi
    report TEST-1 "the test catalogue agrees with the evidence"
}

# ---------------------------------------------------------------------------
# CURRENT-1: the current-state documents do not contradict each other
# ---------------------------------------------------------------------------
#
# The link checker proves that a document points at something that exists; this
# proves that a document which claims to describe the CURRENT state does not
# contradict another one. The checker is separate (scripts/validate-current-state.py)
# because it is useful on its own, and it carries its own negative control
# (scripts/validate-current-state-selftest.sh) - a checker that has never gone red
# on a wrong tree is not evidence.
check_current_state() {
    local checker="$REPO_ROOT/scripts/validate-current-state.py"
    if [ ! -f "$checker" ]; then
        report CURRENT-1 "the current-state checker is absent: scripts/validate-current-state.py"
        VIOLATIONS=$((VIOLATIONS + 1))
        return
    fi
    local output
    if ! output="$(python3 "$checker" --root "$REPO_ROOT" --strict 2>&1)"; then
        printf '%s\n' "$output" | grep -E "CONTRADICTION|STALE|CONFLICT" | head -20
        report CURRENT-1 "the current-state documents contradict each other"
        VIOLATIONS=$((VIOLATIONS + 1))
        return
    fi
    report CURRENT-1 "the current-state documents agree with each other"
}

# ---------------------------------------------------------------------------
# RETENTION-1: the backup retention policy proves itself on a synthetic tree
# ---------------------------------------------------------------------------
#
# The retention script DELETES files. A policy that is not exercised is a
# policy that is not known to work, so the gate runs the script's own
# self-test: a synthetic tree of valid, expired, empty, corrupt and symlinked
# backups, and every rule of the policy is checked against it.
check_retention() {
    local tool="$REPO_ROOT/scripts/backup-retention.py"
    if [ ! -f "$tool" ]; then
        report RETENTION-1 "the retention tool is absent"
        VIOLATIONS=$((VIOLATIONS + 1))
        return
    fi
    local output
    if ! output="$(python3 "$tool" --self-test 2>&1)"; then
        printf '%s\n' "$output" | grep -E "FAIL" | head -20
        report RETENTION-1 "the retention policy does not hold"
        VIOLATIONS=$((VIOLATIONS + 1))
        return
    fi
    report RETENTION-1 "the retention policy holds"
}

# ---------------------------------------------------------------------------
# SNAPSHOT-RETENTION-1: the snapshot retention policy, the payload's size bound
# ---------------------------------------------------------------------------
#
# W48.7. The snapshot rows of the knowledge database get the same three-tier
# calendar the backups have (OD-039), and the payload of a snapshot gets a size
# bound that is a REFUSAL and never a trim.
#
# The check proves three things, and each of them can fail:
#
#   1. the retention tool's own self-test: a synthetic database of valid,
#      expired, corrupt, undated and legacy snapshots, and every rule of the
#      policy is checked against it;
#   2. the size bound is CONFIGURED: the store reads it from a property, and a
#      bound that is hard-coded is a bound that cannot be moved;
#   3. the dry run is the DEFAULT: the tool runs against the owner's database
#      without being told to apply, and the database is byte-identical
#      afterwards.
#
# The third one is the load-bearing part: the tool DELETES rows, and a gate
# that cannot show that the default does not delete is a gate that has not
# proved the safe path.
check_snapshot_retention() {
    local tool="$REPO_ROOT/scripts/snapshot-retention.py"
    if [ ! -f "$tool" ]; then
        report SNAPSHOT-RETENTION-1 "the snapshot retention tool is absent"
        VIOLATIONS=$((VIOLATIONS + 1))
        return
    fi

    # 1. the policy proves itself on a synthetic database
    local output
    if ! output="$(python3 "$tool" --self-test 2>&1)"; then
        printf '%s\n' "$output" | grep -E "FAIL" | head -20
        report SNAPSHOT-RETENTION-1 "the snapshot retention policy does not hold"
        VIOLATIONS=$((VIOLATIONS + 1))
        return
    fi

    # 2. the bound on the payload is configured, not hard-coded
    local store="$REPO_ROOT/matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java"
    if [ ! -f "$store" ]; then
        report SNAPSHOT-RETENTION-1 "the knowledge store is absent: the bound cannot be checked"
        VIOLATIONS=$((VIOLATIONS + 1))
        return
    fi
    if ! grep -q "matrix.knowledge.snapshot.max-payload-bytes" "$store"; then
        report SNAPSHOT-RETENTION-1 "the bound on the snapshot payload is not configurable"
        VIOLATIONS=$((VIOLATIONS + 1))
        return
    fi
    if ! grep -q "DEFAULT_MAX_SNAPSHOT_PAYLOAD_BYTES" "$store"; then
        report SNAPSHOT-RETENTION-1 "the default bound on the snapshot payload is not named"
        VIOLATIONS=$((VIOLATIONS + 1))
        return
    fi

    # 3. the dry run is the default, and it leaves the database alone
    local database="$REPO_ROOT/data/knowledge/workbench.sqlite"
    if [ -f "$database" ]; then
        local before after
        before="$(sha256sum "$database" | cut -d' ' -f1)"
        if ! output="$(python3 "$tool" --database "$database" 2>&1)"; then
            printf '%s\n' "$output" | grep -E "REFUSED" | head -5
            report SNAPSHOT-RETENTION-1 "the retention pass refuses the owner's database"
            VIOLATIONS=$((VIOLATIONS + 1))
            return
        fi
        after="$(sha256sum "$database" | cut -d' ' -f1)"
        if [ "$before" != "$after" ]; then
            report SNAPSHOT-RETENTION-1 "the dry run changed the owner's database"
            VIOLATIONS=$((VIOLATIONS + 1))
            return
        fi
    fi

    report SNAPSHOT-RETENTION-1 "the snapshot retention policy holds, the bound is configured, and the dry run changes nothing"
}

check_shell_magic
check_java_magic
check_env_provenance
check_frozen
check_docs_links
check_test_catalogue
check_current_state
check_retention
check_snapshot_retention

echo "----------------------------------------------"
if [ "$MODE" = "--report" ]; then
    echo "REPORT ONLY (--report): not failing the build."
    exit 0
fi

TOTAL_CANDIDATES=$(( SHELL_CANDIDATES + CANDIDATE_FILES ))

echo "----------------------------------------------"
echo "literal candidates: $TOTAL_CANDIDATES (shell $SHELL_CANDIDATES, java $CANDIDATE_FILES)"
echo ""
echo "WHAT THIS GATE CAN AND CANNOT DO"
echo "  It CAN: find numeric literals outside a named constant or a config file."
echo "  It CANNOT: tell a tunable from a specification constant. A literal that survives"
echo "  both filters may still be a magic number, and one removed may have been fine."
echo "  So it is a REVIEW QUEUE, not an oracle. Treat every hit as something to look at."
echo "  It BLOCKS only on an increase over the accepted baseline — a literal you add is"
echo "  caught; inherited ones are not a reason to disable the gate."

if [ "$MODE" = "--update-baseline" ]; then
    printf 'literal_candidates=%s\n' "$TOTAL_CANDIDATES" > "$BASELINE_FILE"
    {
        echo "# quality-baseline.txt — accepted state for scripts/quality-gate.sh"
        echo "# updated: $(date +%Y-%m-%dT%H:%M:%S%z)   ref=$BASELINE_REF"
        echo "# The gate blocks when this number increases. Lower it by extracting constants."
    } >> "$BASELINE_FILE"
    echo "baseline accepted: literal_candidates=$TOTAL_CANDIDATES"
    exit 0
fi

if [ ! -f "$BASELINE_FILE" ]; then
    echo "NO BASELINE. Run: scripts/quality-gate.sh --update-baseline"
    exit 1
fi

# Parse the KEYED field. The first version took the first integer anywhere in the file,
# which was the year in the header comment — so the accepted baseline silently became
# 2026 and the gate could never block. A check that cannot fail is not a check, and this
# one failed in exactly the way this campaign has repeatedly found.
ACCEPTED="$(grep -oE '^literal_candidates=[0-9]+' "$BASELINE_FILE" | head -1 | cut -d= -f2)"
if [ -z "$ACCEPTED" ]; then
    echo "BASELINE UNREADABLE: $BASELINE_FILE"
    exit 1
fi

if [ "$TOTAL_CANDIDATES" -gt "$ACCEPTED" ]; then
    echo "FAIL: literal candidates rose from $ACCEPTED to $TOTAL_CANDIDATES."
    echo "      $(( TOTAL_CANDIDATES - ACCEPTED )) new magic number(s). Extract them to a"
    echo "      named constant or scripts/matrix.env, or document why the literal is structural."
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# RECON-W43.0: the counter is finally READ.
#
# VIOLATIONS was incremented at three places and never read, so the CFG-1
# and FROZEN-1 rules could not fail the run. A check that cannot fail is
# not a check. Every rule now contributes to the exit status.
if [ "$VIOLATIONS" -gt 0 ] && [ "$MODE" != "--report" ]; then
    echo "FAIL: $VIOLATIONS violation(s) outside the accepted baseline."
    exit 1
fi
echo "PASS: $TOTAL_CANDIDATES candidate(s), at or below the accepted baseline of $ACCEPTED; $VIOLATIONS other violation(s)."
