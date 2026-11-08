#!/usr/bin/env bash
# W51 — measure the Java/C boundary on the BIR truth-table kernel.
#
# It does three things:
#
#   1. builds the shared object from the C source with gcc;
#   2. compiles the Java measurement harness against the module's BIR classes
#      WITHOUT --enable-preview — the java.lang.foreign API is final since
#      JDK 22, so this compile is also the proof that the FFM path needs no
#      preview flag on this JDK;
#   3. runs the harness and prints its raw report.
#
# The build is deliberately OUTSIDE the Gradle build. A machine without gcc, or
# without the shared object, must not break `gradlew build`: the tests in
# io.matrix.nativekernel are written to pass either way. With the library they
# check that the two paths agree bit for bit; without it they check that the
# Java path is chosen and that it agrees with the BIR reference.
#
# The shared object is written into matrix-core/build/, which is generated
# output and is not tracked by git.
#
# Usage:
#   scripts/w51-native-kernel-bench.sh                 # build and measure
#   scripts/w51-native-kernel-bench.sh --build-only    # build the shared object only
#   scripts/w51-native-kernel-bench.sh --bench-only    # measure with the library as it is
#   scripts/w51-native-kernel-bench.sh --with-gradle   # refresh the module classes first
#
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT" || exit 1

readonly C_SOURCE="matrix-core/src/main/c/matrixkernel/matrix_kernel.c"
readonly C_HEADER="matrix-core/src/main/c/matrixkernel/matrix_kernel.h"
readonly LIBRARY_DIR="matrix-core/build/native"
readonly LIBRARY_NAME="libmatrixkernel.so"
readonly LIBRARY_PATH="${LIBRARY_DIR}/${LIBRARY_NAME}"
readonly CLASSES_DIR="matrix-core/build/nativekernel/classes"
readonly MODULE_CLASSES="matrix-core/build/classes/java/main"
readonly MAIN_CLASS="io.matrix.nativekernel.NativeKernelBenchmark"
readonly PROPERTY_NAME="matrix.nativekernel.lib"
readonly SOURCE_DIR="matrix-core/src/main/java/io/matrix/nativekernel"
readonly COMPILER="${CC:-gcc}"
readonly C_FLAGS=(-O2 -fPIC -shared -std=c11 -Wall -Wextra)
readonly JDK_HOME="${JAVA_HOME:-${HOME}/jdk25}"
readonly JAVAC="${JDK_HOME}/bin/javac"
readonly JAVA="${JDK_HOME}/bin/java"
readonly FFM_FINAL_SINCE="JDK 22"
readonly MODE="all"
readonly USE_GRADLE=0

for argument in "$@"; do
    case "$argument" in
        --build-only) MODE="build" ;;
        --bench-only) MODE="bench" ;;
        --with-gradle) USE_GRADLE=1 ;;
        *)
            printf 'unknown argument: %s\n' "$argument" >&2
            printf 'usage: scripts/w51-native-kernel-bench.sh [--build-only|--bench-only|--with-gradle]\n' >&2
            exit 2
            ;;
    esac
done

say() {
    printf '%s\n' "$*"
}

say "=== W51 native kernel — build and measurement ==="
say "repository     : ${REPO_ROOT}"
say "compiler       : ${COMPILER}"
say "jdk home       : ${JDK_HOME}"
say "java           : $("${JAVA}" -version 2>&1 | head -1)"
say ""

build_library() {
    if ! command -v "${COMPILER}" >/dev/null 2>&1; then
        say "the C compiler ${COMPILER} is not available; the library cannot be built"
        return 1
    fi
    if [ ! -f "${C_SOURCE}" ] || [ ! -f "${C_HEADER}" ]; then
        say "the C source is missing: ${C_SOURCE} or ${C_HEADER}"
        return 1
    fi
    mkdir -p "${LIBRARY_DIR}" || return 1
    say "compiling ${C_SOURCE}"
    say "  ${COMPILER} ${C_FLAGS[*]} -o ${LIBRARY_PATH} ${C_SOURCE}"
    if ! "${COMPILER}" "${C_FLAGS[@]}" -o "${LIBRARY_PATH}" "${C_SOURCE}"; then
        say "the compilation failed"
        return 1
    fi
    say "built ${LIBRARY_PATH}"
    return 0
}

refresh_module_classes() {
    if [ ! -x "./gradlew" ]; then
        say "the Gradle wrapper is not present; the module classes were not refreshed"
        return 1
    fi
    say "refreshing the module classes with Gradle (offline)"
    if JAVA_HOME="${JDK_HOME}" ./gradlew :matrix-core:compileJava --offline -q; then
        say "the module classes are current"
        return 0
    fi
    say "the Gradle compile did not succeed; the existing module classes are used as they are"
    return 1
}

compile_harness() {
    if [ ! -f "${MODULE_CLASSES}/io/matrix/bir/TtForm.class" ]; then
        say "the BIR classes are not built at ${MODULE_CLASSES}"
        return 1
    fi
    mkdir -p "${CLASSES_DIR}" || return 1
    say "compiling the harness against ${MODULE_CLASSES}"
    say "  (no --enable-preview: java.lang.foreign is final since ${FFM_FINAL_SINCE}, so this"
    say "   compile is the proof that the FFM path needs no preview flag)"
    if ! "${JAVAC}" -nowarn -cp "${MODULE_CLASSES}" -d "${CLASSES_DIR}" "${SOURCE_DIR}"/*.java; then
        say "the harness did not compile"
        return 1
    fi
    say "compiled the harness into ${CLASSES_DIR}"
    return 0
}

run_measurement() {
    if [ ! -f "${LIBRARY_PATH}" ]; then
        say "the shared object ${LIBRARY_PATH} does not exist; the Java path will be measured"
    fi
    say "--- raw benchmark output ---"
    "${JAVA}" \
        --enable-native-access=ALL-UNNAMED \
        -cp "${CLASSES_DIR}:${MODULE_CLASSES}" \
        -D"${PROPERTY_NAME}=${REPO_ROOT}/${LIBRARY_PATH}" \
        "${MAIN_CLASS}"
    return $?
}

if [ "${MODE}" != "bench" ]; then
    if [ "${USE_GRADLE}" -eq 1 ]; then
        refresh_module_classes || true
        say ""
    fi
    build_library || exit 1
    say ""
    if [ "${MODE}" = "build" ]; then
        say "the shared object was built; run the script again without --build-only to measure"
        exit 0
    fi
fi

compile_harness || exit 1
say ""
run_measurement
exit $?
