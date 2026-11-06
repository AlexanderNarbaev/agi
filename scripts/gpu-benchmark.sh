#!/usr/bin/env bash
# W48-09 — the reproducible GPU benchmark of the ONNX execution path.
#
# OWNER'S DECISION OD-043. The benchmark answers ONE question with evidence:
#
#     does the CUDA execution provider ACTUALLY execute MATRIX's ONNX path, and
#     what does it cost compared with the CPU execution provider on the same
#     machine, the same model, the same sequence length, the same warm-up count
#     and the same iteration count?
#
# Four rules, and each of them exists because the previous wave broke it:
#
#   1. THE PREFLIGHT IS RECORDED. The device, the driver, the CUDA version, the
#      CUDA libraries and their paths, the ONNX Runtime version, the Java
#      version, the model's SHA-256 and the free VRAM before the run. A number
#      without a machine is not a measurement.
#
#   2. THE CPU AND THE CUDA RUN ARE IDENTICAL. Same model, same sequence length
#      (32), same warm-up count, same iteration count, same JVM flags, same
#      classpath, same machine, same LD_LIBRARY_PATH. The ONLY difference is the
#      execution provider the session is configured with. That is why the script
#      exports LD_LIBRARY_PATH for BOTH runs: without it the CUDA provider
#      cannot even be loaded, and comparing a "CPU without CUDA" run with a
#      "CUDA with CUDA" run would confound the provider with the environment.
#
#   3. THE THREE COSTS ARE SEPARATE. The transformer forward pass, the point
#      call and the end-to-end path are different costs and are NEVER averaged
#      into one number. Each is reported with its median AND its dispersion
#      (min / median / p95 / max / standard deviation).
#
#   4. THE BENCHMARK FAILS IF CUDA DID NOT EXECUTE. The execution profile must
#      name CUDAExecutionProvider. A CPU-only run reported as a GPU result is
#      worse than no result, so this is a HARD FAILURE (exit code 5), not a
#      warning. On a machine with no GPU at all the script exits non-zero with
#      "NOT VERIFIED" (exit code 3) instead of quietly reporting a CPU number
#      as a GPU number.
#
# Usage:
#   scripts/gpu-benchmark.sh                       # the full run
#   scripts/gpu-benchmark.sh --iterations 50       # more samples
#   scripts/gpu-benchmark.sh --skip-default-cpu    # skip the MATRIX-default run
#   scripts/gpu-benchmark.sh --help
#
# The artefacts (the preflight table, the raw JSON, the execution profiles and
# the summary) are written under matrix-core/build/gpu-benchmark/, which is
# inside the gitignored build directory. The script writes nothing else.
#
# Exit codes:
#   0  the benchmark completed and the CUDA execution provider was OBSERVED
#   2  the script could not run (usage, missing tool, missing source)
#   3  NOT VERIFIED — no GPU, no usable CUDA libraries, no ONNX Runtime GPU jar
#      or no model. Nothing was measured.
#   4  the CUDA execution provider could not be REGISTERED
#   5  the CUDA run completed but the execution profile did NOT name
#      CUDAExecutionProvider — the hard failure
set -euo pipefail

export LC_ALL=C
export LANG=C

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${REPO_ROOT}/matrix-core/build/gpu-benchmark"

# ── the protocol ─────────────────────────────────────────────────────────────
LABEL="w48"
SEQ_LEN=32
WARMUP=5
ITERATIONS=20
MAX_NEW_TOKENS=16
PROMPT="The GPU benchmark measures the ONNX execution path."
SKIP_DEFAULT_CPU=0
KEEP_TMP=0
REBUILD_CLASSES=0

# The model the historical 12.6x figure was measured on. The weights live in a
# SEPARATE external-data file, so BOTH digests are recorded: the graph alone
# does not identify the model.
TRANSFORMER_MODEL="${REPO_ROOT}/models/onnx/qwen05b/model.onnx"
TRANSFORMER_WEIGHTS="${REPO_ROOT}/models/onnx/qwen05b/model.onnx_data"
TEACHER_MODEL="${REPO_ROOT}/models/teacher/teacher_ffn16.onnx"
MODEL_DIR="${REPO_ROOT}/models/onnx/qwen05b"

# The exit codes, named once so that a typo cannot create a fifth meaning.
EXIT_OK=0
EXIT_USAGE=2
EXIT_NOT_VERIFIED=3
EXIT_PROVIDER_UNAVAILABLE=4
EXIT_CUDA_NOT_EXECUTED=5

# The CUDA ABI sonames and the help window are facts of the toolchain, not
# tunables. They are named here so that the shell-literal gate reads an
# extraction rather than a bare number.
CUDA_RUNTIME_SONAME="libcudart.so.12"
CUDA_BLAS_SONAME="libcublas.so.12"
CUDA_BLASLT_SONAME="libcublasLt.so.12"
CUDA_CURAND_SONAME="libcurand.so.10"
HELP_HEAD_LINES=60

usage() {
    sed -n "2,${HELP_HEAD_LINES}p" "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit "$EXIT_USAGE"
}

while [ $# -gt 0 ]; do
    case "$1" in
        --label) LABEL="$2"; shift 2 ;;
        --seq-len) SEQ_LEN="$2"; shift 2 ;;
        --warmup) WARMUP="$2"; shift 2 ;;
        --iterations) ITERATIONS="$2"; shift 2 ;;
        --max-new-tokens) MAX_NEW_TOKENS="$2"; shift 2 ;;
        --prompt) PROMPT="$2"; shift 2 ;;
        --out-dir) OUT_DIR="$2"; shift 2 ;;
        --model) TRANSFORMER_MODEL="$2"; shift 2 ;;
        --teacher-model) TEACHER_MODEL="$2"; shift 2 ;;
        --skip-default-cpu) SKIP_DEFAULT_CPU=1; shift ;;
        --keep-tmp) KEEP_TMP=1; shift ;;
        --rebuild-classes) REBUILD_CLASSES=1; shift ;;
        --help|-h) usage ;;
        *) echo "gpu-benchmark.sh: unknown argument: $1" >&2; exit "$EXIT_USAGE" ;;
    esac
done

have() { command -v "$1" >/dev/null 2>&1; }

# ── the preflight table ──────────────────────────────────────────────────────
#
# Every row is a measurement with the command that produced it. A row that
# cannot be measured says "unavailable" and WHY — never a zero.
PREFLIGHT_ROWS=()
row() { PREFLIGHT_ROWS+=("|$1|$2|$3|"); }

not_verified() {
    echo "" >&2
    echo "NOT VERIFIED: $1" >&2
    echo "The benchmark did not run and no GPU number is reported." >&2
    exit "$EXIT_NOT_VERIFIED"
}

# java_for_build — the JDK the repository builds with. The `java` on the PATH
# and the `javac` on the PATH are DIFFERENT releases on this host, so the script
# names the JDK the build uses rather than trusting the PATH.
java_for_build() {
    if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ] \
            && [ -x "${JAVA_HOME}/bin/javac" ]; then
        printf '%s\n' "${JAVA_HOME}"
        return 0
    fi
    local candidate
    for candidate in "$HOME"/jdk25 "$HOME"/jdk* /usr/lib/jvm/java-25-openjdk-amd64; do
        if [ -x "$candidate/bin/java" ] && [ -x "$candidate/bin/javac" ]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done
    printf 'unavailable\n'
}

# gradle_cache_jar GROUP ARTIFACT — the jar the Gradle cache holds for a module.
gradle_cache_jar() {
    local cache="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/$1/$2"
    [ -d "$cache" ] || return 0
    find "$cache" -name "$2-*.jar" 2>/dev/null | grep -v -- '-sources' | sort -V | tail -1
}

# ── 1. the preflight ─────────────────────────────────────────────────────────
echo "== W48-09 GPU benchmark =="
echo "label=${LABEL} seqLen=${SEQ_LEN} warmup=${WARMUP} iterations=${ITERATIONS} maxNewTokens=${MAX_NEW_TOKENS}"
echo ""

have nvidia-smi || not_verified "nvidia-smi is not present on this machine, so there is no GPU to benchmark."
GPU_NAME="$(nvidia-smi --query-gpu=name --format=csv,noheader 2>/dev/null | sed -n '1p' || true)"
[ -n "$GPU_NAME" ] || not_verified "nvidia-smi reports no GPU on this machine."
DRIVER_VERSION="$(nvidia-smi --query-gpu=driver_version --format=csv,noheader 2>/dev/null | sed -n '1p' || true)"
CUDA_COMPAT="$(nvidia-smi 2>/dev/null | awk -F'CUDA Version: ' '/CUDA Version/{gsub(/[ \t|]+$/,"",$2); print $2; exit}' || true)"
VRAM_TOTAL_MIB="$(nvidia-smi --query-gpu=memory.total --format=csv,noheader,nounits 2>/dev/null | sed -n '1p' || true)"
VRAM_FREE_MIB="$(nvidia-smi --query-gpu=memory.free --format=csv,noheader,nounits 2>/dev/null | sed -n '1p' || true)"
VRAM_USED_MIB="$(nvidia-smi --query-gpu=memory.used --format=csv,noheader,nounits 2>/dev/null | sed -n '1p' || true)"
GPU_UTIL="$(nvidia-smi --query-gpu=utilization.gpu --format=csv,noheader,nounits 2>/dev/null | sed -n '1p' || true)"

JDK_HOME="$(java_for_build)"
[ "$JDK_HOME" != "unavailable" ] || not_verified "no JDK with both java and javac was found."
JAVA_VERSION="$("$JDK_HOME/bin/java" -version 2>&1 | sed -n '1p')"
JAVA_HOME_USED="$JDK_HOME"

# The CUDA libraries. The ONNX Runtime CUDA provider is a native shared object
# that a native dlopen resolves against LD_LIBRARY_PATH, NOT against
# -Djava.library.path. The libraries it needs are named by the provider's own
# NEEDED list; the script resolves each of them and checks that the one that
# matters — libcudart — exports cudaLibraryGetKernel.
#
# The ONNX Runtime 1.29 CUDA provider is a CUDA 12 build: `objdump -p` on
# libonnxruntime_providers_cuda.so names libcudart.so.12, libcublas.so.12,
# libcublasLt.so.12, libcurand.so.10 and libcuda.so.1 (the driver). The
# project's virtualenv carries those CUDA-12 builds; the CUDA-13 tree that sits
# beside them in the same directory tree is NOT used, because it exports the
# .so.13 SONAMEs the provider does not ask for.
cuda_lib_dir() {
    local soname="$1" dir candidate
    for dir in \
        "${REPO_ROOT}"/.venv/lib/*/site-packages/nvidia/cuda_runtime/lib \
        "${REPO_ROOT}"/.venv/lib/*/site-packages/nvidia/cublas/lib \
        "${REPO_ROOT}"/.venv/lib/*/site-packages/nvidia/curand/lib \
        "${REPO_ROOT}"/.venv/lib/*/site-packages/nvidia/cudnn/lib \
        "${REPO_ROOT}"/.venv/lib/*/site-packages/nvidia/nvjitlink/lib \
        /usr/local/cuda/lib64 \
        "${REPO_ROOT}"/.venv/lib/*/site-packages/nvidia/*/lib; do
        [ -d "$dir" ] || continue
        if [ -e "$dir/$soname" ]; then
            printf '%s\n' "$dir"
            return 0
        fi
        for candidate in "$dir/$soname".*; do
            if [ -e "$candidate" ]; then
                printf '%s\n' "$dir"
                return 0
            fi
        done
    done
    return 1
}

CUDA_LIB_DIRS=""
for soname in "$CUDA_RUNTIME_SONAME" "$CUDA_BLAS_SONAME" "$CUDA_BLASLT_SONAME" "$CUDA_CURAND_SONAME"; do
    found="$(cuda_lib_dir "$soname" || true)"
    [ -n "$found" ] || not_verified "the CUDA library $soname, which the ONNX Runtime CUDA provider needs, was not found on this machine."
    case ":$CUDA_LIB_DIRS:" in
        *":$found:"*) ;;
        *) CUDA_LIB_DIRS="${CUDA_LIB_DIRS}${found}:" ;;
    esac
done
CUDA_LIB_DIRS="${CUDA_LIB_DIRS%:}"
LD_LIBRARY_PATH_BENCH="$CUDA_LIB_DIRS"

# The libcudart the LOADER would find without the variable: the deciding fact.
RESOLVED_LIBCUDART="$(ldconfig -p 2>/dev/null | awk -v soname="$CUDA_RUNTIME_SONAME" '$1 == soname { print $NF; exit }')"
RESOLVED_SYMBOLS=0
[ -n "$RESOLVED_LIBCUDART" ] && \
    RESOLVED_SYMBOLS="$(nm -D --defined-only "$RESOLVED_LIBCUDART" 2>/dev/null | grep -c cudaLibraryGetKernel || true)"
BENCH_LIBCUDART=""
for dir in $(printf '%s' "$CUDA_LIB_DIRS" | tr ':' ' '); do
    if [ -e "$dir/$CUDA_RUNTIME_SONAME" ]; then
        BENCH_LIBCUDART="$dir/$CUDA_RUNTIME_SONAME"
        break
    fi
done
BENCH_SYMBOLS=0
[ -n "$BENCH_LIBCUDART" ] && \
    BENCH_SYMBOLS="$(nm -D --defined-only "$BENCH_LIBCUDART" 2>/dev/null | grep -c cudaLibraryGetKernel || true)"
[ "$BENCH_SYMBOLS" -gt 0 ] || not_verified "the libcudart on the benchmark's LD_LIBRARY_PATH does not export cudaLibraryGetKernel, so the CUDA provider cannot be loaded."

CUDA_TOOLKIT="$(nvcc --version 2>/dev/null | awk -F'release ' '/release/{split($2,a,","); print a[1]; exit}')"
[ -n "$CUDA_TOOLKIT" ] || CUDA_TOOLKIT="unavailable"
CUDA_TOOLKIT_DIRS="$(ls -d /usr/local/cuda-* 2>/dev/null | sort -V | tr '\n' ' ' | sed 's/ $//')"
[ -n "$CUDA_TOOLKIT_DIRS" ] || CUDA_TOOLKIT_DIRS="unavailable"
# The toolkit the PATH compiles with is NOT the toolkit /usr/local/cuda points
# at — the same trap the W46 hardware report recorded.
CUDA_HOME_VERSION="unavailable"
if [ -f /usr/local/cuda/version.json ]; then
    CUDA_HOME_VERSION="$(awk -F'"' '/"cuda" :/ { found = 1 } found && /"version"/ { print $4; exit }' /usr/local/cuda/version.json)"
fi
[ -n "$CUDA_HOME_VERSION" ] || CUDA_HOME_VERSION="unavailable"

# The ONNX Runtime build the repository resolves. The GPU jar carries the
# CUDA-enabled native library and MUST come first on the classpath.
ORT_GPU_JAR="$(gradle_cache_jar com.microsoft.onnxruntime onnxruntime_gpu)"
ORT_CPU_JAR="$(gradle_cache_jar com.microsoft.onnxruntime onnxruntime)"
[ -n "$ORT_GPU_JAR" ] || not_verified "the ONNX Runtime GPU jar was not found in the Gradle cache, so the CUDA provider cannot be tested."
[ -n "$ORT_CPU_JAR" ] || not_verified "the ONNX Runtime CPU jar was not found in the Gradle cache."
ORT_VERSION="$(basename "$ORT_GPU_JAR" | sed -E 's/^onnxruntime_gpu-(.*)\.jar$/\1/')"

SLF4J_JAR="$(gradle_cache_jar org.slf4j slf4j-api)"
[ -n "$SLF4J_JAR" ] || not_verified "the slf4j-api jar was not found in the Gradle cache."

# The models.
[ -f "$TRANSFORMER_MODEL" ] || not_verified "the transformer graph $TRANSFORMER_MODEL is not present."
[ -f "$TRANSFORMER_WEIGHTS" ] || not_verified "the external weights $TRANSFORMER_WEIGHTS are not present."
[ -f "$TEACHER_MODEL" ] || not_verified "the teacher graph $TEACHER_MODEL is not present."
sha256_of() { sha256sum "$1" 2>/dev/null | awk '{print $1}'; }
MODEL_SHA="$(sha256_of "$TRANSFORMER_MODEL")"
WEIGHTS_SHA="$(sha256_of "$TRANSFORMER_WEIGHTS")"
TEACHER_SHA="$(sha256_of "$TEACHER_MODEL")"

# The class the benchmark needs, and the compiled MATRIX classes it uses.
HARNESS_SRC="${REPO_ROOT}/matrix-core/src/test/java/io/matrix/benchmark/GpuBenchmarkHarness.java"
[ -f "$HARNESS_SRC" ] || { echo "gpu-benchmark.sh: the harness $HARNESS_SRC is missing" >&2; exit "$EXIT_USAGE"; }
MAIN_CLASSES="${REPO_ROOT}/matrix-core/build/classes/java/main"
if [ "$REBUILD_CLASSES" -eq 1 ] || [ ! -d "$MAIN_CLASSES" ]; then
    echo "== compiling matrix-core main classes =="
    ( cd "$REPO_ROOT" && JAVA_HOME="$JDK_HOME" ./gradlew --no-daemon -q :matrix-core:classes ) \
        || { echo "gpu-benchmark.sh: the matrix-core classes could not be compiled" >&2; exit "$EXIT_USAGE"; }
fi
[ -d "$MAIN_CLASSES" ] || { echo "gpu-benchmark.sh: $MAIN_CLASSES is missing" >&2; exit "$EXIT_USAGE"; }

# ── the preflight table ──────────────────────────────────────────────────────
row "the device" "$GPU_NAME" "nvidia-smi --query-gpu=name"
row "the driver version" "$DRIVER_VERSION" "nvidia-smi --query-gpu=driver_version"
row "the CUDA version the driver supports" "${CUDA_COMPAT:-unavailable}" "nvidia-smi (the CUDA Version line)"
row "the CUDA toolkit on the PATH" "$CUDA_TOOLKIT" "nvcc --version"
row "the CUDA toolkit /usr/local/cuda points at" "${CUDA_HOME_VERSION} (${CUDA_TOOLKIT_DIRS})" "/usr/local/cuda/version.json; ls -d /usr/local/cuda-*"
row "the CUDA libraries on LD_LIBRARY_PATH" "$CUDA_LIB_DIRS" "resolved from the provider's NEEDED list"
row "the libcudart the loader finds WITHOUT the variable" "${RESOLVED_LIBCUDART:-unavailable} (exports cudaLibraryGetKernel ${RESOLVED_SYMBOLS}x)" "ldconfig -p; nm -D --defined-only"
row "the libcudart the benchmark uses" "${BENCH_LIBCUDART:-unavailable} (exports cudaLibraryGetKernel ${BENCH_SYMBOLS}x)" "nm -D --defined-only"
row "the ONNX Runtime version" "$ORT_VERSION" "the jar in the Gradle cache"
row "the Java version" "$JAVA_VERSION" "$JDK_HOME/bin/java -version"
row "the JDK used" "$JAVA_HOME_USED" "the JDK the repository builds with"
row "the transformer graph" "$TRANSFORMER_MODEL" "the model under test"
row "the transformer's SHA-256" "$MODEL_SHA" "sha256sum"
row "the transformer's external weights" "$TRANSFORMER_WEIGHTS" "the weights are a separate file"
row "the weights' SHA-256" "$WEIGHTS_SHA" "sha256sum"
row "the teacher graph" "$TEACHER_MODEL" "the point-call model"
row "the teacher graph's SHA-256" "$TEACHER_SHA" "sha256sum"
row "the free VRAM before the run" "${VRAM_FREE_MIB} MiB of ${VRAM_TOTAL_MIB} MiB (${VRAM_USED_MIB} MiB in use, ${GPU_UTIL}% utilisation)" "nvidia-smi --query-gpu=memory.free,memory.total,memory.used,utilization.gpu"
row "the sequence length" "$SEQ_LEN" "--seq-len"
row "the warm-up iterations" "$WARMUP" "--warmup"
row "the measured iterations" "$ITERATIONS" "--iterations"

mkdir -p "$OUT_DIR"
{
    echo "| the measurement | the value | the command |"
    echo "|---|---|---|"
    for r in "${PREFLIGHT_ROWS[@]}"; do
        echo "${r#|}" | awk -F'|' '{print "| " $1 " | " $2 " | `" $3 "` |"}'
    done
} > "$OUT_DIR/preflight.md"
cat "$OUT_DIR/preflight.md"

# ── 2. the classpath and the compiled harness ────────────────────────────────
#
# The classpath is MINIMAL and EXPLICIT: the compiled MATRIX classes, the
# ONNX Runtime GPU jar FIRST, the ONNX Runtime CPU jar and slf4j. It is not the
# whole matrix-core runtime classpath on purpose — see the note in the report:
# on this machine a long classpath delivered on the command line crashes the
# JVM with SIGSEGV while the ONNX Runtime native library is being loaded.
BENCH_CLASSES="${OUT_DIR}/classes"
mkdir -p "$BENCH_CLASSES"
CP="${MAIN_CLASSES}:${REPO_ROOT}/matrix-core/build/resources/main:${ORT_GPU_JAR}:${ORT_CPU_JAR}:${SLF4J_JAR}"
echo ""
echo "== compiling the benchmark harness =="
"$JDK_HOME/bin/javac" -nowarn -cp "$CP" -d "$BENCH_CLASSES" "$HARNESS_SRC" \
    || { echo "gpu-benchmark.sh: the harness did not compile" >&2; exit "$EXIT_USAGE"; }

# The JVM flags are IDENTICAL for every configuration. Only the provider and
# the LD_LIBRARY_PATH differ between the CPU and the CUDA run.
JVM_FLAGS=(
    --enable-native-access=ALL-UNNAMED
    -Xms1g
    -Xmx4g
    -XX:+UseG1GC
)

WORK_TMP="$(mktemp -d "${TMPDIR:-/tmp}/w48-gpu-bench.XXXXXX")"
cleanup() {
    if [ "$KEEP_TMP" -eq 0 ]; then
        rm -rf "$WORK_TMP"
    else
        echo "the temporary directory was kept: $WORK_TMP"
    fi
}
trap cleanup EXIT

# ── 3. the runs ──────────────────────────────────────────────────────────────
#
# run_config LABEL PROVIDER LD_LIBRARY_PATH REQUIRE_CUDA — one measurement
# configuration. The harness exits 3 when the CUDA provider was requested and
# did not execute; the script turns that into the script's own hard failure.
run_config() {
    local label="$1" provider="$2" libpath="$3" require_cuda="$4"
    local json="${OUT_DIR}/${label}.json"
    local profile="${OUT_DIR}/profile/${label}"
    local require_flag=()
    [ "$require_cuda" -eq 1 ] && require_flag=(--require-cuda)
    echo ""
    echo "== the ${label} run (provider=${provider}) =="
    rm -rf "$profile"
    mkdir -p "$profile"
    # `|| code=$?` instead of `set +e`: the exit status is captured WITHOUT
    # switching errexit off inside the function, so a non-zero status does not
    # terminate the shell before the caller can map it.
    local code=0
    LD_LIBRARY_PATH="$libpath" "$JDK_HOME/bin/java" "${JVM_FLAGS[@]}" \
        -Djava.io.tmpdir="$WORK_TMP" \
        -cp "${BENCH_CLASSES}:${CP}" \
        io.matrix.benchmark.GpuBenchmarkHarness \
        --label "$label" \
        --provider "$provider" \
        --model "$TRANSFORMER_MODEL" \
        --teacher-model "$TEACHER_MODEL" \
        --model-dir "$MODEL_DIR" \
        --seq-len "$SEQ_LEN" \
        --warmup "$WARMUP" \
        --iterations "$ITERATIONS" \
        --prompt "$PROMPT" \
        --max-new-tokens "$MAX_NEW_TOKENS" \
        --profile-dir "$profile" \
        --out "$json" \
        "${require_flag[@]}" || code=$?
    if [ "$code" -ne 0 ]; then
        echo "the ${label} run failed with exit code ${code}" >&2
    fi
    return "$code"
}

# The CPU baseline, in the SAME environment as the CUDA run: the LD_LIBRARY_PATH
# is exported for both, so the provider is the only variable.
set +e
run_config "cpu" "cpu" "$LD_LIBRARY_PATH_BENCH" 0
CPU_CODE=$?
set -e
if [ "$CPU_CODE" -ne 0 ]; then
    echo "gpu-benchmark.sh: the CPU baseline failed (exit ${CPU_CODE}); nothing is reported." >&2
    exit "$CPU_CODE"
fi

# The CUDA run. The harness exits 3 when the execution profile does not name
# CUDAExecutionProvider, and that is the script's hard failure.
set +e
run_config "cuda" "cuda" "$LD_LIBRARY_PATH_BENCH" 1
CUDA_CODE=$?
set -e
if [ "$CUDA_CODE" -eq 3 ]; then
    echo "" >&2
    echo "HARD FAILURE: the CUDA execution provider did NOT execute." >&2
    echo "The execution profile did not name CUDAExecutionProvider. A CPU-only run" >&2
    echo "must never be reported as a GPU result." >&2
    exit "$EXIT_CUDA_NOT_EXECUTED"
fi
if [ "$CUDA_CODE" -eq 4 ]; then
    echo "" >&2
    echo "HARD FAILURE: the CUDA execution provider could not be REGISTERED." >&2
    exit "$EXIT_PROVIDER_UNAVAILABLE"
fi
if [ "$CUDA_CODE" -ne 0 ]; then
    echo "gpu-benchmark.sh: the CUDA run failed (exit ${CUDA_CODE}); nothing is reported." >&2
    exit "$CUDA_CODE"
fi

# The MATRIX default configuration: the CPU provider WITHOUT the CUDA
# libraries on LD_LIBRARY_PATH. This is what MATRIX does today, and it is the
# configuration the W46 report measured the 12.6x against.
DEFAULT_CODE=0
if [ "$SKIP_DEFAULT_CPU" -eq 0 ]; then
    set +e
    run_config "cpu-default" "cpu" "" 0
    DEFAULT_CODE=$?
    set -e
    if [ "$DEFAULT_CODE" -ne 0 ]; then
        echo "gpu-benchmark.sh: the MATRIX-default CPU run failed (exit ${DEFAULT_CODE})" >&2
        exit "$DEFAULT_CODE"
    fi
fi

# ── 4. the summary ───────────────────────────────────────────────────────────
echo ""
echo "== the summary =="
have python3 || { echo "gpu-benchmark.sh: python3 is required for the summary" >&2; exit "$EXIT_USAGE"; }
python3 - "$OUT_DIR" "$LABEL" <<'PYTHON'
import json
import os
import sys

out_dir, label = sys.argv[1], sys.argv[2]

ORDER = [
    ("transformer_forward", "the transformer forward pass (the raw session.run)"),
    ("transformer_forward_adapter", "the transformer forward pass (MATRIX's own call, with the readback)"),
    ("point_call_onnx", "the ONNX point call (the teacher FFN, batch 1)"),
    ("bir_point_call", "the BIR point call (MATRIX's own boolean runtime)"),
    ("end_to_end", "the end-to-end path (tokenise, generate, detokenise)"),
]

def load(name):
    path = os.path.join(out_dir, name + ".json")
    if not os.path.isfile(path):
        return None
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)

cpu = load("cpu")
cuda = load("cuda")
default = load("cpu-default")

def fmt(value):
    return ("%.3f" % value) if value is not None else "n/a"

print("")
print("## the configuration")
print("")
print("| the configuration | the provider | the LD_LIBRARY_PATH | the execution profile |")
print("|---|---|---|---|")
for name, data in (("cpu-default", default), ("cpu", cpu), ("cuda", cuda)):
    if data is None:
        continue
    profile = data.get("executionProfile", {}).get("providers", {})
    libpath = "exported" if name != "cpu-default" else "NOT exported"
    print("| %s | %s | %s | %s |" % (
        name, data.get("provider"), libpath,
        ", ".join("%s x%d" % (k, v) for k, v in profile.items()) or "not captured"))

print("")
print("## the median and the dispersion")
print("")
print("| the measurement | the unit | the CPU median | the CPU min / p95 / max | the CPU sd | the CUDA median | the CUDA min / p95 / max | the CUDA sd | the CPU / CUDA |")
print("|---|---|---|---|---|---|---|---|---|")
for key, title in ORDER:
    c = cpu["measurements"].get(key) if cpu else None
    g = cuda["measurements"].get(key) if cuda else None
    if not c or not g:
        continue
    ratio = (c["median"] / g["median"]) if g["median"] else None
    print("| %s | %s | %s | %s / %s / %s | %s | %s | %s / %s / %s | %s | %s |" % (
        title, c["unit"],
        fmt(c["median"]), fmt(c["min"]), fmt(c["p95"]), fmt(c["max"]), fmt(c["stddev"]),
        fmt(g["median"]), fmt(g["min"]), fmt(g["p95"]), fmt(g["max"]), fmt(g["stddev"]),
        ("%.2fx" % ratio) if ratio else "n/a"))

print("")
print("## the raw per-iteration samples")
print("")
for name, data in (("cpu-default", default), ("cpu", cpu), ("cuda", cuda)):
    if data is None:
        continue
    print("### %s" % name)
    print("")
    for key, title in ORDER:
        series = data.get("measurements", {}).get(key)
        if not series:
            continue
        samples = series["samples"]
        print("- **%s** (%s, warmup %d): %s" % (
            title, series["unit"], series["warmup"],
            ", ".join("%.3f" % s for s in samples)))
    print("")

if cpu and cuda:
    profile = cuda.get("executionProfile", {})
    print("## the CUDA execution proof")
    print("")
    print("- the execution profile of the CUDA run names: %s" % (
        ", ".join("%s x%d" % (k, v) for k, v in profile.get("providers", {}).items())))
    print("- the CUDA provider was observed: %s" % profile.get("cudaObserved"))
    print("- the CPU run's execution profile names: %s" % (
        ", ".join("%s x%d" % (k, v) for k, v in
                  cpu.get("executionProfile", {}).get("providers", {}).items())))
    print("")
PYTHON

{
    echo "the label: ${LABEL}"
    echo "the sequence length: ${SEQ_LEN}"
    echo "the warm-up iterations: ${WARMUP}"
    echo "the measured iterations: ${ITERATIONS}"
    echo "the generated tokens: ${MAX_NEW_TOKENS}"
    echo ""
    for f in cpu cpu-default cuda; do
        [ -f "${OUT_DIR}/${f}.json" ] && echo "the raw result: ${OUT_DIR}/${f}.json"
    done
    echo "the preflight: ${OUT_DIR}/preflight.md"
    echo "the execution profiles: ${OUT_DIR}/profile/"
} > "$OUT_DIR/summary.txt"

echo "the artefacts are under ${OUT_DIR}"
exit "$EXIT_OK"
