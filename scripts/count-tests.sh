#!/usr/bin/env bash
# count-tests.sh — RECON-W38.4
#
# Counts the test methods in the repository, per module and in total.
#
# WHY THIS EXISTS
# ---------------
# The published front page advertised "724 Tests Passing". No measurement in
# this repository produced 724; it is also the tokenizer's vocabulary size,
# which is how it most likely arose. A capability claim must name the
# command that produced it, so this script is that command.
#
# WHAT IT COUNTS
# --------------
# JUnit 5 test METHODS: every source line that declares @Test or
# @ParameterizedTest, per module, across src/test. This is the same counting
# method the build itself uses (:matrix-core:generateBuildInfo), so the
# number printed by a binary and the number printed here are produced the
# same way.
#
# It is a SOURCE count, not an execution count: a @Disabled method is
# counted here and is not executed. For the executed count, run
# `./gradlew test` and sum the `tests="N"` attributes of the JUnit XML result
# files.
#
# Usage:
#   ./scripts/count-tests.sh          # per module and total
#   ./scripts/count-tests.sh --total  # total only
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO"

python3 - "$@" <<'PY'
import glob, re, sys, collections

PER_MODULE = collections.Counter()
TOTAL = 0
for path in glob.glob('**/src/test/**/*.java', recursive=True):
    if '/build/' in path or '/node_modules/' in path:
        continue
    try:
        src = open(path, encoding='utf-8', errors='ignore').read()
    except OSError:
        continue
    n = len(re.findall(r'^\s*@(Test|ParameterizedTest|TestFactory|RepeatedTest)\b', src, re.M))
    if n:
        module = path.split('/src/test/')[0]
        PER_MODULE[module] += n
        TOTAL += n

if '--total' in sys.argv:
    print(TOTAL)
else:
    for module, count in PER_MODULE.most_common():
        print(f"{count:>6}  {module}")
    print(f"{TOTAL:>6}  TOTAL across {len(PER_MODULE)} modules")
PY
