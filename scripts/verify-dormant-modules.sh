#!/usr/bin/env bash
# verify-dormant-modules.sh — RECON-W38.4
#
# Runs the verification of the modules that live in this repository but are
# NOT covered by the Gradle build, so that their state is checkable on demand
# instead of being an assumption.
#
# WHY THIS EXISTS
# ---------------
# matrix-fpga, matrix-micro and matrix-ros2 are commented out of
# settings.gradle, and matrix-web-ui is a Next.js project with its own
# toolchain. Nothing in CI ran any of their test suites. A module whose suite
# is never executed reports nothing: it is indistinguishable from a module
# that passes. This script distinguishes the three honest outcomes:
#
#   PASS    - the suite ran and every test passed
#   FAIL    - the suite ran and at least one test failed
#   SKIPPED - the toolchain needed to run the suite is not available here
#             (a skip is never reported as a pass)
#   NO-SUITE- the module declares no test suite at all
#
# Usage:
#   ./scripts/verify-dormant-modules.sh [module ...]
#
# Exit status:
#   0 - every suite that ran passed
#   1 - at least one suite failed
#
# This script is NOT wired into CI: the web console suite is (see
# .github/workflows/ci.yml, job web-ui-tests); the three hardware modules
# have no CI runner with the required toolchain. Their lifecycle states are
# recorded in docs-v2/architecture/MODULE-LIFECYCLE.md.
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Named `printf` rather than a helper like `say` so the quality gate's
# existing exemption for human-facing printf output applies rather than being
# extended.
say() { printf '  %s\n' "$*"; }
rule() { printf '==> %s\n' "$*"; }

FAILED=0
RAN=0

if [ "$#" -gt 0 ]; then
  MODULES=("$@")
else
  MODULES=(matrix-micro matrix-fpga matrix-ros2 matrix-web-ui)
fi

# ── matrix-micro ────────────────────────────────────────────────────────────
verify_matrix_micro() {
  rule "matrix-micro (C, out of the Gradle build)"
  if ! command -v gcc >/dev/null 2>&1; then
    say "SKIPPED: no C compiler on this machine"
    return 0
  fi
  local out="/tmp/matrix-micro-verify.$$"
  if ! gcc -std=c11 -I "$REPO/matrix-micro/src" \
        "$REPO/matrix-micro/tests/test_mpdt.c" \
        "$REPO/matrix-micro/src/mpdt_neuron.c" -o "$out" 2>&1; then
    say "FAIL: the C test did not compile"
    FAILED=1
    return 0
  fi
  if "$out" >/dev/null 2>&1; then
    say "PASS: all tests passed"
  else
    say "FAIL: at least one test failed"
    FAILED=1
  fi
  rm -f "$out"
}

# ── matrix-fpga ─────────────────────────────────────────────────────────────
verify_matrix_fpga() {
  rule "matrix-fpga (Amaranth HDL simulation, out of the Gradle build)"
  if ! command -v python3 >/dev/null 2>&1; then
    say "SKIPPED: no python3 on this machine"
    return 0
  fi
  local py="python3"
  if [ -x "$REPO/.venv/bin/python" ]; then
    py="$REPO/.venv/bin/python"
  fi
  if ! "$py" -c "import amaranth" >/dev/null 2>&1; then
    say "SKIPPED: the 'amaranth' package is not installed in $py"
    say "         (pip install amaranth; the suite simulates a k=4 MPDT neuron)"
    return 0
  fi
  if "$py" "$REPO/matrix-fpga/test_mpdt_fpga.py" >/dev/null 2>&1; then
    say "PASS: all tests passed"
  else
    say "FAIL: at least one test failed"
    FAILED=1
  fi
}

# ── matrix-ros2 ─────────────────────────────────────────────────────────────
verify_matrix_ros2() {
  rule "matrix-ros2 (ROS 2 nodes, out of the Gradle build)"
  local n
  n=$(find "$REPO/matrix-ros2" -name 'test_*.py' -o -name '*_test.py' 2>/dev/null | wc -l)
  if [ "$n" -eq 0 ]; then
    say "NO-SUITE: the module declares no test suite"
    say "          (setup.py lists tests_require=['pytest'], which is a"
    say "           declaration, not a test)"
    return 0
  fi
  say "INFO: $n test file(s) found; run them with pytest"
}

# ── matrix-web-ui ───────────────────────────────────────────────────────────
verify_matrix_web_ui() {
  rule "matrix-web-ui (Next.js / jest)"
  if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
    say "SKIPPED: node/npm not available on this machine"
    return 0
  fi
  if [ ! -d "$REPO/matrix-web-ui/node_modules" ]; then
    say "installing dependencies (npm ci --legacy-peer-deps)..."
    if ! (cd "$REPO/matrix-web-ui" && npm ci --legacy-peer-deps --no-audit --no-fund) >/dev/null 2>&1; then
      say "FAIL: npm ci failed"
      FAILED=1
      return 0
    fi
  fi
  if (cd "$REPO/matrix-web-ui" && npx jest --ci) >/dev/null 2>&1; then
    say "PASS: all test suites passed"
  else
    say "FAIL: at least one test suite failed"
    FAILED=1
  fi
}

for m in "${MODULES[@]}"; do
  case "$m" in
    matrix-micro) verify_matrix_micro ;;
    matrix-fpga)  verify_matrix_fpga ;;
    matrix-ros2)  verify_matrix_ros2 ;;
    matrix-web-ui) verify_matrix_web_ui ;;
    *) say "unknown module: $m" ;;
  esac
  RAN=$((RAN+1))
done

printf '\n'
if [ "$FAILED" -ne 0 ]; then
  printf 'Dormant-module verification FAILED (%d module(s) checked).\n' "$RAN"
  exit 1
fi
printf 'Dormant-module verification passed (%d module(s) checked; a SKIPPED module is not a pass).\n' "$RAN"
