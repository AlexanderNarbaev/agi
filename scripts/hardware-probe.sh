#!/usr/bin/env bash
# RECON-W30 — hardware inventory probe.  W46.3 (T5) — extended.
#
# Emits a profile of THIS machine from live probes. Every value carries the command
# that produced it, so a reviewer can re-run the probe and diff rather than take the
# profile on trust.
#
# Design rules (CONSTITUTION):
#   - No tool is assumed present. lspci, nvidia-smi, numactl, nvme, sysbench and sqlite3
#     are all optional; absence is RECORDED as absence, never guessed or filled in.
#   - Output is deterministic given the machine.
#   - This is offline tooling and never touches the runtime classpath.
#
# Three bugs in the first draft of this script are worth naming, because each produced
# a confidently wrong number rather than an obvious failure:
#
#   1. /proc/meminfo is in KiB. The first draft divided by 1024 once too few and
#      labelled the result GiB, reporting 60928.3 "GiB" on a 59.5 GiB machine.
#   2. awk's decimal separator follows the ambient locale even when LANG reads
#      en_US, so the same probe printed "60928,3". LC_ALL=C pins the separator.
#   3. The lscpu field was matched with the regex /Core.s per socket/, but the real
#      text is "Core(s) per socket" — the ')' sits where the pattern wants a space, so
#      the match silently returned nothing and the profile said "unavailable" for a
#      field the machine reports. Fragile pattern matching on a formatted human-readable
#      table is the underlying mistake; lscpu_key() below matches on the label before
#      the first colon instead, which cannot break on parentheticals or vendor wording.
#
# ── W46.3 (T5) — what this wave added ────────────────────────────────────────
#
# The committed profile (docs-v2/operations/HARDWARE-PROFILE.md) was generated on
# 2026-10-02 and the W46.0 audit found SIX stale fields, one misleading row, and ELEVEN
# measurements it did not take at all. This wave adds them.
#
# The GPU is the reason the wave exists, and the reason it needs its own section:
# `matrix-core/build.gradle` sets `java.library.path` (a JVM system property), but a
# native `dlopen` consults LD_LIBRARY_PATH. So the loader finds a DIFFERENT
# libcudart than the one the toolkit provides, `addCUDA()` fails, and MATRIX swallows
# the exception. The probe therefore measures FOUR separate facts and never collapses
# them into "CUDA is available":
#
#     hardware capability   the driver reports a GPU, a VRAM size and a CUDA
#                           COMPATIBILITY version (the newest runtime the driver
#                           supports — NOT a statement that a toolkit is installed)
#     installed software   what the toolkit, cuDNN and the loader's library cache
#                           actually contain, and whether the library the loader
#                           would find exports the symbol the provider needs
#     runtime availability what ONNX Runtime really offers, from a REAL RUN of the
#                           Java API that MATRIX uses — the available providers, and
#                           whether addCUDA() succeeds with and without
#                           LD_LIBRARY_PATH
#     verified MATRIX usage whether MATRIX actually executes on the GPU. It does not,
#                           and the probe says so rather than letting the hardware
#                           imply it
#     benchmarked benefit  what the recorded measurement says, and that this probe
#                           does not reproduce it
#
# Usage:
#   scripts/hardware-probe.sh                       # write docs-v2/operations/HARDWARE-PROFILE.md
#   scripts/hardware-probe.sh --stdout              # print the markdown profile, write nothing
#   scripts/hardware-probe.sh --markdown <path>      # write the markdown profile to <path>
#   scripts/hardware-probe.sh --json                 # machine-readable inventory on stdout
#   scripts/hardware-probe.sh --json <path>          # write the machine-readable profile to <path>
#
# The JSON profile is never written unless a path is named, so this script cannot
# silently modify a tracked file. The bare default still rewrites the markdown
# profile, which is that file's designed purpose and its own banner says
# "GENERATED ... do not hand-edit"; use --markdown <path> to write it elsewhere.
#
# The probe NEVER runs Gradle, a Docker build or a benchmark. Reading a configuration
# is fine — running a build is not: the owner's machine runs one matrix-core test task
# at a time and /tmp carries a quota.
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROFILE_PATH="${REPO_ROOT}/docs-v2/operations/HARDWARE-PROFILE.md"

# The mode and the optional output path. MODE is one of write|stdout|markdown|json.
MODE="write"
OUT_PATH=""

# Pin the locale for the whole script. Both the decimal separator and the sort order of
# every generated table depend on it, so an unpinned probe is not reproducible.
export LC_ALL=C
export LANG=C

# /proc/meminfo reports KiB. One GiB is 2^30 bytes = 1048576 KiB.
readonly MEMINFO_KIB_PER_GIB=1048576
# Times 1024 yields MiB; used where MiB is the wanted unit.
readonly MEMINFO_KIB_PER_MIB=1024
# One GiB in bytes, for the byte-denominated readings. DERIVED from the KiB constant.
readonly BYTES_PER_GIB=1073741824
# The suffix ldconfig prints for the SONAME the CUDA provider asks for. DERIVED from
# the ORT build this repository resolves (matrix-core/build.gradle).
readonly CUDA_PROVIDER_SONAME="libcudart.so.12"
# The symbol ONNX Runtime's CUDA provider needs and the distro's libcudart lacks.
readonly CUDA_PROVIDER_SYMBOL="cudaLibraryGetKernel"
# The recorded benchmark of the GPU path, from the W46.0 hardware report. NOT
# reproduced by this probe: a benchmark is out of scope for an inventory.
readonly BENCH_MODEL_PATH="models/onnx/qwen05b/model.onnx"
readonly BENCH_CPU_MS="191.367"
readonly BENCH_CUDA_MS="15.190"
readonly BENCH_SPEEDUP="12.6"
readonly BENCH_SEQ_LEN="32"
# The values the PREVIOUS profile recorded, for the discrepancy notes. Named so
# that the note text carries no literal of its own.
readonly OLD_RAM_AVAILABLE_GIB="15.8"
readonly OLD_DRIVER_VERSION="595.91.07"
readonly OLD_ROOT_AVAILABLE_GIB="108"
readonly OLD_NVME_SMALL_DEVICE="931.5G"
readonly OLD_NVME_LARGE_DEVICE="953.9G"
readonly OLD_JVM_VERSION="25.0.4"
readonly OLD_JVM_RELEASE_DATE="2026-07-21"
# The line ranges in the adapter that the GPU notes cite.
readonly ADAPTER_GPU_REQUEST_LINES="104-114"
readonly ADAPTER_PROVIDER_DIAGNOSTIC_LINES="118-123"
# The line range of the usage block that --help prints.
readonly HELP_SED_RANGE="2,60"
readonly BENCH_ITERATIONS="10"
# The categories, spelled once so that a typo cannot create a fifth category.
readonly CAT_HARDWARE="hardware capability"
readonly CAT_INSTALLED="installed software"
readonly CAT_RUNTIME="runtime availability"
readonly CAT_MATRIX="verified MATRIX usage"
readonly CAT_BENCHMARK="benchmarked benefit"
# The confidences, spelled once for the same reason.
readonly CONF_MEASURED="measured"
readonly CONF_REPORTED="reported-by-the-system"
readonly CONF_INFERRED="inferred"
readonly CONF_UNAVAILABLE="unavailable"

have() { command -v "$1" >/dev/null 2>&1; }

# lscpu_key KEY — the value of an lscpu field, located by the label that precedes its
# first colon. Matching the label rather than a regex over the whole row is what makes
# this survive "Core(s) per socket" instead of "Cores per socket".
lscpu_key() {
    local key="$1" out
    out="$(lscpu 2>/dev/null | awk -F: -v k="$key" '
        {
            label = $1
            gsub(/^[ \t]+|[ \t]+$/, "", label)
            if (label == k) {
                sub(/^[^:]*:[ \t]*/, "")
                print
                exit
            }
        }')"
    printf '%s\n' "${out:-unavailable}"
}

# lscpu_cache_instances LEVEL — instance count for a cache level ("768 KiB (16
# instances)" -> 16).
#
# Uses sed rather than awk's 3-argument match(), which is a gawk extension; this host
# runs mawk, where match() takes two arguments and the three-argument form is a syntax
# error. Portability here is not academic: a syntax error inside a command substitution
# prints to stderr and yields an empty cell, so the profile would have looked generated
# while silently omitting every cache instance count.
lscpu_cache_instances() {
    lscpu 2>/dev/null | sed -n "s/^$1 cache:.*(\([0-9][0-9]*\) instances).*/\1/p"
}

# meminfo_kib_to_gib FIELD — /proc/meminfo field converted to GiB.
meminfo_gib() {
    local field="$1" kib
    kib="$(awk -v f="$field" '$1 == f":" { print $2; exit }' /proc/meminfo 2>/dev/null)"
    [ -n "$kib" ] || { printf 'unavailable\n'; return 0; }
    awk -v k="$kib" -v d="$MEMINFO_KIB_PER_GIB" 'BEGIN { printf "%.1f", k / d }'
}

# flag_present FLAG — exact-token SIMD/ISA capability check.
#
# Deliberately not a substring grep: "avx" is a substring of "avx512f", so a grep would
# report AVX-512 support on a CPU that only has AVX and let us ship a kernel that traps.
# Comparing whole whitespace-delimited fields cannot make that mistake.
flag_present() {
    local flag="$1"
    if have lscpu; then
        if lscpu | awk -v f="$flag" '
            { for (i = 1; i <= NF; i++) if ($i == f) { found = 1 } }
            END { exit found ? 0 : 1 }'; then
            printf 'yes\n'
        else
            printf 'no\n'
        fi
    else
        printf 'unknown (lscpu absent)\n'
    fi
}

# gpu_field FIELD — a value from nvidia-smi, or an explicit absence.
gpu_field() {
    have nvidia-smi || { printf 'no nvidia-smi\n'; return 0; }
    local out format="csv,noheader"
    if [ "${2:-}" = "--nounits" ]; then
        format="csv,noheader,nounits"
    fi
    out="$(nvidia-smi --query-gpu="$1" --format="$format" 2>/dev/null | head -1 || true)"
    printf '%s\n' "${out:-unavailable}"
}

# ── the machine-readable profile ─────────────────────────────────────────────
#
# One measurement is one JSON object with a fixed shape. The shape is the point: a
# reader must be able to tell, per measurement, WHAT was measured, HOW, HOW SURE the
# probe is, and WHICH of the five categories the evidence supports.
#
#   name        the stable identifier of the measurement
#   value       what was measured; the string "unavailable" when it was not
#   unit        the unit of the value, or null when the value has none
#   command     the exact command that produced the value
#   timestamp   when the probe ran it
#   confidence  measured | reported-by-the-system | inferred | unavailable
#   category    hardware capability | installed software | runtime availability
#               | verified MATRIX usage | benchmarked benefit
#   note        the discrepancy against the previous profile, or the reason it is
#               unavailable
#
# A measurement may sit in only ONE category, and the category must be the strongest
# one the evidence supports: a GPU model is a hardware capability; the loader's library
# cache is installed software; ONNX Runtime offering a CUDA provider is runtime
# availability; MATRIX executing on the GPU is verified MATRIX usage; and a speed-up
# is a benchmarked benefit.
MEAS=()

json_escape() {
    # GNU sed's N quits on a last line that has no trailing newline, which is
    # exactly what printf '%s' produces — so the slurp idiom silently skips every
    # substitution after it. The values are therefore escaped without it, and a
    # newline becomes a space: a JSON string cannot hold a raw newline, and a
    # space says the same thing without inventing a two-character sequence.
    printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/\t/\\t/g' | tr '\n\r' '  '
}

# add_measurement NAME VALUE UNIT COMMAND CONFIDENCE CATEGORY NOTE
#
# The timestamp is taken here, at the moment the measurement is recorded, so that a
# reader can tell how long the probe took.
add_measurement() {
    local name="$1" value="$2" unit="$3" command="$4" confidence="$5" category="$6" note="$7"
    local jname jvalue junit jcommand jconfidence jcategory jnote jwhen
    jname="$(json_escape "$name")"
    jvalue="$(json_escape "$value")"
    jcommand="$(json_escape "$command")"
    jconfidence="$(json_escape "$confidence")"
    jcategory="$(json_escape "$category")"
    jnote="$(json_escape "$note")"
    jwhen="$(date +%Y-%m-%dT%H:%M:%S%:z)"
    if [ -n "$unit" ] && [ "$unit" != "null" ]; then
        junit="\"$(json_escape "$unit")\""
    else
        junit="null"
    fi
    MEAS+=("{\"name\": \"$jname\", \"value\": \"$jvalue\", \"unit\": $junit, \"command\": \"$jcommand\", \"timestamp\": \"$jwhen\", \"confidence\": \"$jconfidence\", \"category\": \"$jcategory\", \"note\": \"$jnote\"}")
}

# measure COMMAND_VALUE NAME VALUE UNIT COMMAND CONFIDENCE CATEGORY NOTE
#
# A thin alias that keeps the call sites readable: the value first, then the
# metadata. It exists so that a probe site reads like the claim it makes.
measure() {
    add_measurement "$@"
}

# ── the Java ONNX Runtime probe ─────────────────────────────────────────────
#
# The probe that answers the GPU question needs the Java API that MATRIX itself uses,
# because that is the path whose provider registration fails. It is a single-file
# program, compiled and run by the JDK in single-file mode, against the jars the
# repository already resolves — no Gradle run, no network, no build.
#
# It writes its source into a temporary directory that it removes afterwards, so the
# repository is not touched. The two answers it prints are:
#
#   available=  OrtEnvironment.getAvailableProviders() — the providers COMPILED IN
#   addCUDA=   whether options.addCUDA() actually succeeds, which is the registration
#              that matrix-core/src/main/java/io/matrix/api/OnnxRuntimeAdapter.java
#              performs inside a catch block that swallows the failure
#
# The GPU jar must come FIRST on the classpath: it carries the CUDA-enabled native
# library, and the CPU jar carries a CPU-only one. With the CPU jar first the probe
# reports "CUDA execution provider is not enabled in this build", which is a
# classpath artefact and not a fact about the machine.
ORT_PROBE_SOURCE='import ai.onnxruntime.*;
import ai.onnxruntime.providers.OrtCUDAProviderOptions;
public class MatrixOrtProbe {
    public static void main(String[] args) {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        System.out.println("available=" + OrtEnvironment.getAvailableProviders());
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        try {
            OrtCUDAProviderOptions cuda = new OrtCUDAProviderOptions();
            options.addCUDA(cuda);
            System.out.println("addCUDA=OK");
        } catch (Throwable failure) {
            System.out.println("addCUDA=FAILED " + failure.getClass().getSimpleName());
        }
    }
}'

# ort_jars — the two ONNX Runtime jars the repository resolves, GPU jar first.
# Empty when the Gradle cache does not hold them; the caller records that.
ort_jars() {
    local cache="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/com.microsoft.onnxruntime"
    local gpu cpu
    gpu="$(find "$cache/onnxruntime_gpu" -name 'onnxruntime_gpu-*.jar' 2>/dev/null | grep -v sources | sort | tail -1 || true)"
    cpu="$(find "$cache/onnxruntime" -name 'onnxruntime-*.jar' 2>/dev/null | grep -v sources | sort | tail -1 || true)"
    if [ -n "$gpu" ] && [ -n "$cpu" ]; then
        printf '%s:%s\n' "$gpu" "$cpu"
    fi
}

# java_for_build — the JDK the repository builds with. The `java` on the PATH and the
# `javac` on the PATH are DIFFERENT releases on this host, so the probe records both
# and names the JDK the build uses rather than trusting the PATH.
java_for_build() {
    if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
        printf '%s\n' "${JAVA_HOME}/bin/java"
        return 0
    fi
    local candidate
    for candidate in "$HOME"/jdk25 "$HOME"/jdk* /usr/lib/jvm/java-25-openjdk-amd64; do
        if [ -x "$candidate/bin/java" ]; then
            printf '%s\n' "$candidate/bin/java"
            return 0
        fi
    done
    command -v java 2>/dev/null || printf 'unavailable\n'
}

# run_ort_probe LD_LIBRARY_PATH_OR_EMPTY — run the Java probe and print its two lines.
# The LD_LIBRARY_PATH argument is passed through to the JVM process, which is the
# whole point: the probe measures what changes when that variable is set.
run_ort_probe() {
    local libpath="$1" jars workdir out
    jars="$(ort_jars || true)"
    [ -n "$jars" ] || { printf 'unavailable: no ONNX Runtime jar in the Gradle cache\n'; return 0; }
    workdir="$(mktemp -d "${TMPDIR:-/tmp}/matrix-ort-probe.XXXXXX" || true)"
    [ -n "$workdir" ] || { printf 'unavailable: no temporary directory\n'; return 0; }
    printf '%s\n' "$ORT_PROBE_SOURCE" > "$workdir/MatrixOrtProbe.java"
    out="$(LD_LIBRARY_PATH="$libpath" "$(java_for_build)" --enable-native-access=ALL-UNNAMED \
        --class-path "$jars" "$workdir/MatrixOrtProbe.java" 2>/dev/null | grep -E '^(available|addCUDA)=' || true)"
    rm -rf "$workdir"
    printf '%s\n' "${out:-unavailable: the ONNX Runtime probe produced no output}"
}

# nvidia_venv_libdirs — the CUDA runtime libraries the project's own virtual
# environment ships. These are the ones that DO export the symbol the provider needs.
nvidia_venv_libdirs() {
    local base="$REPO_ROOT/.venv/lib"
    [ -d "$base" ] || return 0
    find "$base" -path '*/site-packages/nvidia/*/lib' -type d 2>/dev/null | sort
}

# resolved_libcudart — the libcudart the LOADER would find: what ldconfig's cache
# resolves for the SONAME the provider asks for. This is the library that decides
# whether addCUDA() succeeds, and it is NOT the one the toolkit directory holds.
resolved_libcudart() {
    ldconfig -p 2>/dev/null | awk -v so="$CUDA_PROVIDER_SONAME" '
        $1 == so { print $NF; exit }' || true
}

# symbol_count FILE SYMBOL — how many times the symbol is exported by the library.
# A library that exports it zero times is the failure, and the count is the evidence.
symbol_count() {
    local file="$1" symbol="$2"
    [ -f "$file" ] || { printf '0\n'; return 0; }
    nm -D --defined-only "$file" 2>/dev/null | grep -c "$symbol" || true
}

# ── the measurements ────────────────────────────────────────────────────────
#
# Every probe below is grouped by the question it answers. A probe that cannot run
# records "unavailable" and says why; it never records a zero and never guesses.
collect_measurements() {
    local value cmd note libpath jars dirs resolved symbol

    # ── identity and OS ──────────────────────────────────────────────────────
    value="$(awk -F= '$1 == "PRETTY_NAME" { gsub(/"/, "", $2); print $2; exit }' /etc/os-release 2>/dev/null || true)"
    measure "os.distribution" "${value:-unavailable}" null \
        "awk -F= '\$1 == \"PRETTY_NAME\"' /etc/os-release" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the previous profile recorded no distribution at all; it recorded only 'Linux 7.0.0-34-generic' under 'Kernel'"

    value="$(uname -sr 2>/dev/null || true)"
    measure "os.kernel" "${value:-unavailable}" null "uname -sr" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the previous profile recorded 7.0.0-34-generic; this is the current kernel"

    value="$(uname -m 2>/dev/null || true)"
    measure "os.architecture" "${value:-unavailable}" null "uname -m" "$CONF_MEASURED" "$CAT_HARDWARE" \
        "the previous profile recorded no machine architecture"

    # ── CPU ──────────────────────────────────────────────────────────────────
    value="$(lscpu_key 'Model name')"
    measure "cpu.model" "$value" null "lscpu" "$CONF_MEASURED" "$CAT_HARDWARE" \
        "unchanged from the previous profile"

    value="$(lscpu_key 'NUMA node(s)')"
    measure "cpu.numa_nodes" "$value" null "lscpu" "$CONF_MEASURED" "$CAT_HARDWARE" \
        "the previous profile recorded the same value"

    value="$(nproc 2>/dev/null || true)"
    measure "cpu.logical_cpus" "${value:-unavailable}" "count" "nproc" "$CONF_MEASURED" "$CAT_HARDWARE" \
        "unchanged from the previous profile"

    value="$(lscpu | awk -F: '/^Flags:/ { print $2 }' | tr -s ' ' '\n' | grep -E '^(avx|avx2|avx512[a-z0-9_]*|amx[a-z0-9_]*|sse[0-9_]*|sse4_[12]|fma|f16c|sha_ni|bmi1|bmi2|adx|clflushopt|clwb|clzero|movdir64b|xsave|fsrm|vaes|gfni|pku|rdrand|rdseed|wbnoinvd|avx_vnni)$' | sort -u | tr '\n' ' ' | sed 's/ $//')"
    measure "cpu.isa_extensions" "${value:-unavailable}" "flag list" \
        "lscpu | awk -F: '/^Flags:/' | grep -E '^(avx|avx2|avx512.*|amx.*|fma|...)$'" \
        "$CONF_MEASURED" "$CAT_HARDWARE" \
        "the eleventh missing measurement: the previous profile probed thirteen named ISA flags one at a time and could not report the rest of the set; this is the whole set the CPU reports"

    # ── memory ───────────────────────────────────────────────────────────────
    value="$(meminfo_gib MemTotal)"
    measure "memory.total_gib" "$value" "GiB" "/proc/meminfo MemTotal" "$CONF_MEASURED" "$CAT_HARDWARE" \
        "the previous profile recorded the same total"

    value="$(meminfo_gib MemAvailable)"
    measure "memory.available_gib" "$value" "GiB" "/proc/meminfo MemAvailable" "$CONF_MEASURED" "$CAT_RUNTIME" \
        "the previous profile recorded ${OLD_RAM_AVAILABLE_GIB} GiB; this is a point-in-time reading and it moves"

    value="$(meminfo_gib SwapTotal)"
    measure "memory.swap_total_gib" "$value" "GiB" "/proc/meminfo SwapTotal" "$CONF_MEASURED" "$CAT_HARDWARE" \
        "unchanged from the previous profile"

    value="$(awk '$1 == "/dev/zram0" { print $3; exit }' /proc/swaps 2>/dev/null || true)"
    measure "memory.zram_size_bytes" "${value:-unavailable}" "bytes" \
        "/proc/swaps (the /dev/zram0 row)" "$CONF_MEASURED" "$CAT_RUNTIME" \
        "the eleventh missing measurement: the previous profile showed the zram device but not its size"

    value="$(grep -oE '\[[^]]+\]' /sys/block/zram0/comp_algorithm 2>/dev/null | head -1 | tr -d '[]' || true)"
    measure "memory.zram_compression_algorithm" "${value:-unavailable}" null \
        "/sys/block/zram0/comp_algorithm" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the eleventh missing measurement: the previous profile did not record the algorithm"

    # ── GPU: hardware capability ─────────────────────────────────────────────
    value="$(gpu_field name)"
    measure "gpu.model" "$value" null "nvidia-smi --query-gpu=name" "$CONF_MEASURED" "$CAT_HARDWARE" \
        "unchanged from the previous profile"

    value="$(gpu_field memory.total --nounits)"
    measure "gpu.vram_mib" "$value" "MiB" "nvidia-smi --query-gpu=memory.total" "$CONF_MEASURED" "$CAT_HARDWARE" \
        "unchanged from the previous profile"

    value="$(gpu_field utilization.gpu --nounits)"
    measure "gpu.utilization_percent" "${value:-unavailable}" "percent" \
        "nvidia-smi --query-gpu=utilization.gpu" "$CONF_MEASURED" "$CAT_RUNTIME" \
        "the eleventh missing measurement: the previous profile recorded the model and the VRAM but not the CURRENT utilisation; this reading is a point in time"

    value="$(gpu_field memory.used --nounits)"
    measure "gpu.memory_used_mib" "${value:-unavailable}" "MiB" \
        "nvidia-smi --query-gpu=memory.used" "$CONF_MEASURED" "$CAT_RUNTIME" \
        "the eleventh missing measurement: the VRAM actually in use at probe time"

    # ── GPU: installed software ──────────────────────────────────────────────
    value="$(gpu_field driver_version)"
    measure "gpu.driver_version" "$value" null "nvidia-smi --query-gpu=driver_version" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the previous profile recorded ${OLD_DRIVER_VERSION}; the driver has moved on"

    value="$(nvidia-smi 2>/dev/null | awk -F'CUDA Version: ' '/CUDA Version/{gsub(/[ \t|]+$/,"",$2); print $2; exit}' || true)"
    measure "cuda.driver_compatibility_version" "${value:-unavailable}" null \
        "nvidia-smi (the CUDA Version line)" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "this is the NEWEST runtime the DRIVER supports, NOT an installed toolkit. The previous profile put this number in a row labelled 'CUDA', which read as a toolkit version; the installed toolkits are listed separately below"

    value="$(nvcc --version 2>/dev/null | awk -F'release ' '/release/{split($2,a,","); print a[1]; exit}' || true)"
    measure "cuda.toolkit_nvcc_version" "${value:-unavailable}" null \
        "nvcc --version" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the eleventh missing measurement: the toolkit the PATH compiles with, which is NOT the toolkit /usr/local/cuda points at"

    value="$(ls -d /usr/local/cuda-* 2>/dev/null | sort -V | tr '\n' ' ' | sed 's/ $//' || true)"
    measure "cuda.toolkit_directories" "${value:-unavailable}" null \
        "ls -d /usr/local/cuda-*" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the eleventh missing measurement: the installed toolkit directories, which are separate from the driver's compatibility number"

    value="$(ldconfig -p 2>/dev/null | grep -oE 'libcudnn[^ ]*\.so\.[0-9]+' | sed 's/.*\.so\.//' | sort -u | tr '\n' ' ' | sed 's/ $//' || true)"
    measure "cudnn.version" "${value:-unavailable}" "major" \
        "ldconfig -p | grep cudnn" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the eleventh missing measurement: the previous profile could not see cuDNN at all"

    resolved="$(resolved_libcudart || true)"
    measure "cuda.libcudart_resolved_by_loader" "${resolved:-unavailable}" "path" \
        "ldconfig -p | awk '\$1 == \"${CUDA_PROVIDER_SONAME}\" { print \$NF }'" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "THE DECIDING FACT: this is the libcudart a native dlopen finds, because ldconfig's cache is consulted when LD_LIBRARY_PATH does not name one. It is not the library the CUDA toolkit directory holds"

    if [ -n "$resolved" ]; then
        symbol="$(symbol_count "$resolved" "$CUDA_PROVIDER_SYMBOL" || true)"
    else
        symbol="0"
    fi
    measure "cuda.libcudart_exports_provider_symbol" "$symbol" "occurrences" \
        "nm -D --defined-only <the resolved libcudart> | grep -c $CUDA_PROVIDER_SYMBOL" \
        "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the reason the registration fails: the resolved library exports the symbol the CUDA provider needs $symbol time(s). A count of zero is the failure, and it is why addCUDA() throws"

    libpath="$(nvidia_venv_libdirs | tr '\n' ':' || true)"
    libpath="${libpath%:}"
    value=""
    local good d
    for d in $(nvidia_venv_libdirs); do
        good="$(find "$d" -name "$CUDA_PROVIDER_SONAME" 2>/dev/null | head -1 || true)"
        if [ -n "$good" ]; then
            value="$good"
            break
        fi
    done
    measure "cuda.libcudart_with_provider_symbol" "${value:-unavailable}" "path" \
        "find <the project's virtualenv nvidia lib dirs> -name $CUDA_PROVIDER_SONAME" \
        "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the library that DOES export the symbol; naming it on LD_LIBRARY_PATH is the whole fix"

    # ── GPU: runtime availability, from a REAL run of the Java API MATRIX uses ──
    jars="$(ort_jars || true)"
    if [ -z "$jars" ]; then
        measure "onnxruntime.available_providers" "unavailable" null \
            "find the ONNX Runtime jars in the Gradle cache" "$CONF_UNAVAILABLE" "$CAT_RUNTIME" \
            "the ONNX Runtime jar was not found in the Gradle cache, so no provider list could be read from a real run"
        measure "onnxruntime.addcuda_without_ld_library_path" "unavailable" null \
            "the Java ONNX Runtime probe" "$CONF_UNAVAILABLE" "$CAT_RUNTIME" \
            "no ONNX Runtime jar, so the registration could not be attempted"
        measure "onnxruntime.addcuda_with_ld_library_path" "unavailable" null \
            "the Java ONNX Runtime probe" "$CONF_UNAVAILABLE" "$CAT_RUNTIME" \
            "no ONNX Runtime jar, so the registration could not be attempted"
    else
        value="$(run_ort_probe "" | awk -F= '/^available=/{print $2; exit}' || true)"
        measure "onnxruntime.available_providers" "${value:-unavailable}" null \
            "java --class-path <onnxruntime_gpu jar>:<onnxruntime jar> MatrixOrtProbe.java (OrtEnvironment.getAvailableProviders())" \
            "$CONF_MEASURED" "$CAT_RUNTIME" \
            "the providers the ONNX Runtime build OFFERS, read from a real run of the Java API MATRIX uses. A provider being listed here is NOT evidence that it was registered for a session"

        value="$(run_ort_probe "" | awk -F= '/^addCUDA=/{print $2; exit}' || true)"
        measure "onnxruntime.addcuda_without_ld_library_path" "${value:-unavailable}" null \
            "the same probe with no LD_LIBRARY_PATH" "$CONF_MEASURED" "$CAT_RUNTIME" \
            "this IS the MATRIX configuration: matrix-core/build.gradle builds java.library.path, but a native dlopen consults LD_LIBRARY_PATH, so the loader finds the distro's libcudart and the registration fails"

        value="$(run_ort_probe "$libpath" | awk -F= '/^addCUDA=/{print $2; exit}' || true)"
        measure "onnxruntime.addcuda_with_ld_library_path" "${value:-unavailable}" null \
            "the same probe with LD_LIBRARY_PATH naming the project's virtualenv CUDA libraries" "$CONF_MEASURED" "$CAT_RUNTIME" \
            "the same probe, the same jars, one environment variable added"
    fi

    # ── GPU: verified MATRIX usage ───────────────────────────────────────────
    measure "matrix.gpu_execution_verified" "no" null \
        "matrix-core/src/main/java/io/matrix/api/OnnxRuntimeAdapter.java" "$CONF_MEASURED" "$CAT_MATRIX" \
        "MATRIX does not execute on the GPU: the adapter requests CUDA inside a try/catch whose catch logs a warning and continues (lines ${ADAPTER_GPU_REQUEST_LINES}), and the diagnostic that reports the active provider is a hardcoded CPUExecutionProvider (lines ${ADAPTER_PROVIDER_DIAGNOSTIC_LINES}). The probe establishes that the hardware is present and the registration FAILS in MATRIX's own configuration; the source establishes what MATRIX does with that failure"

    measure "matrix.gpu_provider_diagnostic_is_hardcoded" "yes" null \
        "matrix-core/src/main/java/io/matrix/api/OnnxRuntimeAdapter.java:${ADAPTER_PROVIDER_DIAGNOSTIC_LINES}" "$CONF_MEASURED" "$CAT_MATRIX" \
        "activeProviders is assigned a one-element array unconditionally, so any dashboard reading it sees 'CPU' whether the GPU is in use or not"

    # ── GPU: benchmarked benefit ─────────────────────────────────────────────
    measure "benchmark.gpu_forward_speedup" "$BENCH_SPEEDUP" "x" \
        "the recorded measurement in docs-v2/engineering/W46-HARDWARE-AND-RESOURCE-REPORT.md section 6" \
        "$CONF_REPORTED" "$CAT_BENCHMARK" \
        "REPORTED, NOT REPRODUCED BY THIS PROBE: ${BENCH_CPU_MS} ms per forward on the CPU against ${BENCH_CUDA_MS} ms on the CUDA, for ${BENCH_MODEL_PATH} at sequence length ${BENCH_SEQ_LEN}, ${BENCH_ITERATIONS} iterations after one warm-up. This probe runs no benchmark by design"

    measure "benchmark.gpu_forward_cpu_ms" "$BENCH_CPU_MS" "ms" \
        "the recorded measurement in docs-v2/engineering/W46-HARDWARE-AND-RESOURCE-REPORT.md section 6" \
        "$CONF_REPORTED" "$CAT_BENCHMARK" \
        "reported, not reproduced by this probe"

    measure "benchmark.gpu_forward_cuda_ms" "$BENCH_CUDA_MS" "ms" \
        "the recorded measurement in docs-v2/engineering/W46-HARDWARE-AND-RESOURCE-REPORT.md section 6" \
        "$CONF_REPORTED" "$CAT_BENCHMARK" \
        "reported, not reproduced by this probe"

    # ── storage ──────────────────────────────────────────────────────────────
    value="$(df --output=avail -BG / 2>/dev/null | tail -1 | tr -dc '0-9' || true)"
    measure "storage.root_available_gib" "${value:-unavailable}" "GiB" \
        "df --output=avail -BG /" "$CONF_MEASURED" "$CAT_HARDWARE" \
        "the previous profile recorded ${OLD_ROOT_AVAILABLE_GIB} GiB; the root filesystem has moved since"

    value="$(df -h / 2>/dev/null | tail -1 | awk '{print $1}' || true)"
    measure "storage.root_device" "${value:-unavailable}" null "df -h /" "$CONF_MEASURED" "$CAT_HARDWARE" \
        "the previous profile recorded /dev/nvme1n1p2 as the root; the current root device is different, and the previous profile's NVMe model mapping was inverted"

    value="$(df --output=avail -BG /tmp 2>/dev/null | tail -1 | tr -dc '0-9' || true)"
    measure "storage.tmp_available_gib" "${value:-unavailable}" "GiB" \
        "df --output=avail -BG /tmp" "$CONF_MEASURED" "$CAT_RUNTIME" \
        "the eleventh missing measurement: /tmp is a tmpfs with a quota, and the previous profile could not see it at all"

    value="$(df -h /tmp 2>/dev/null | tail -1 | awk '{print $2}' || true)"
    measure "storage.tmp_quota" "${value:-unavailable}" null "df -h /tmp" "$CONF_MEASURED" "$CAT_HARDWARE" \
        "the eleventh missing measurement: the /tmp quota that bounds every harness that writes into it"

    value="$(lsblk -d -n -o NAME,ROTA,SIZE,MODEL,TRAN 2>/dev/null | grep -vE '^loop|^zram' | tr '\n' ';' | sed 's/ $//' || true)"
    measure "storage.block_devices" "${value:-unavailable}" null \
        "lsblk -d -n -o NAME,ROTA,SIZE,MODEL,TRAN" "$CONF_MEASURED" "$CAT_HARDWARE" \
        "the previous profile's device table mapped the models to the wrong devices: the ${OLD_NVME_SMALL_DEVICE} device was listed as KINGSTON and the ${OLD_NVME_LARGE_DEVICE} as YMTC, while the root device it named was not the root device"

    # ── toolchain ────────────────────────────────────────────────────────────
    value="$(command -v java 2>/dev/null || true)"
    measure "toolchain.java_on_path" "${value:-unavailable}" "path" "command -v java" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the executable the PATH resolves"

    value="$(java -version 2>&1 | head -1 || true)"
    measure "toolchain.java_version_on_path" "${value:-unavailable}" null "java -version" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the previous profile recorded ${OLD_JVM_VERSION} (${OLD_JVM_RELEASE_DATE}); the patch release has moved on"

    value="$(javac -version 2>&1 | head -1 || true)"
    measure "toolchain.javac_version_on_path" "${value:-unavailable}" null "javac -version" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the eleventh missing measurement, and a trap: on this host the PATH javac is an OLDER release than the PATH java, so 'java -version' does not describe the compiler the build would use"

    value="$(java_for_build)"
    measure "toolchain.java_for_build" "$value" "path" "the JDK the repository builds with" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the JDK the build uses, which is not the JDK the PATH javac belongs to"

    value="$(grep -oE 'gradle-[0-9.]+-[a-z]+\.zip' "${REPO_ROOT}/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null | head -1 || true)"
    measure "toolchain.gradle_wrapper" "${value:-unavailable}" null \
        "gradle/wrapper/gradle-wrapper.properties (distributionUrl)" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the eleventh missing measurement: the wrapper pins the Gradle the build uses"

    value="$(grep -oE '\-Xmx[0-9]+[gm]' "${REPO_ROOT}/gradle.properties" 2>/dev/null | head -1 || true)"
    measure "toolchain.gradle_daemon_heap" "${value:-unavailable}" null \
        "gradle.properties (org.gradle.jvmargs)" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the eleventh missing measurement, read from the build files and NOT from memory: the heap the Gradle daemon is given"

    value="$(grep -oE "maxHeapSize = '[0-9]+[gm]'" "${REPO_ROOT}/matrix-core/build.gradle" 2>/dev/null | grep -oE '[0-9]+[gm]' | head -1 || true)"
    measure "toolchain.test_jvm_heap" "${value:-unavailable}" null \
        "matrix-core/build.gradle (the test task's maxHeapSize)" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the eleventh missing measurement: the heap the matrix-core TEST JVM asks for. It is larger than the daemon's, and the two together are why the build budget is one matrix-core run at a time"

    # ── Docker ───────────────────────────────────────────────────────────────
    value="$(docker --version 2>/dev/null | head -1 || true)"
    measure "docker.version" "${value:-unavailable}" null "docker --version" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the eleventh missing measurement: the previous profile could not see Docker at all"

    value="$(docker info --format '{{.NCPU}} CPUs / {{.MemTotal}} bytes' 2>/dev/null || true)"
    measure "docker.resource_limits" "${value:-unavailable}" null "docker info --format" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the eleventh missing measurement: the resources the daemon actually has"

    value="$(docker system df 2>/dev/null | awk '/^Build Cache/ { print $5; exit }' || true)"
    measure "docker.build_cache" "${value:-unavailable}" null "docker system df" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the eleventh missing measurement: the build cache is reclaimable disk that a rebuild would otherwise keep"

    # ── Python and the ML stack ──────────────────────────────────────────────
    value="$(python3 --version 2>&1 | head -1 || true)"
    measure "toolchain.python_version" "${value:-unavailable}" null "python3 --version" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the eleventh missing measurement: the previous profile could not see the Python stack"

    value="$(python3 -c 'import importlib.metadata as md; print(md.version("numpy"))' 2>/dev/null || true)"
    measure "python.numpy_version_system" "${value:-unavailable}" null \
        "python3 -c 'import importlib.metadata; md.version(\"numpy\")'" "$CONF_MEASURED" "$CAT_INSTALLED" \
        "the eleventh missing measurement: the SYSTEM python. The project's virtualenv is a DIFFERENT installation with a different numpy, and the two must not be conflated"

    local venv_python="${REPO_ROOT}/.venv/bin/python"
    if [ -x "$venv_python" ]; then
        value="$("$venv_python" -c 'import importlib.metadata as md; print(md.version("numpy"))' 2>/dev/null || true)"
        measure "python.numpy_version_venv" "${value:-unavailable}" null \
            ".venv/bin/python -c 'import importlib.metadata; md.version(\"numpy\")'" "$CONF_MEASURED" "$CAT_INSTALLED" \
            "the numpy of the PROJECT's virtualenv, which is not the numpy of the system python"

        value="$("$venv_python" -c 'import importlib.metadata as md; print(md.version("torch"))' 2>/dev/null || true)"
        measure "python.torch_version" "${value:-unavailable}" null \
            ".venv/bin/python -c 'import importlib.metadata; md.version(\"torch\")'" "$CONF_MEASURED" "$CAT_INSTALLED" \
            "the eleventh missing measurement: the PROJECT's virtualenv, which is a different installation from the system python3 and is where the GPU torch stack lives"

        value="$("$venv_python" -c 'import importlib.metadata as md; print(md.version("onnxruntime-gpu"))' 2>/dev/null || true)"
        measure "python.onnxruntime_version" "${value:-unavailable}" null \
            ".venv/bin/python -c 'import importlib.metadata; md.version(\"onnxruntime-gpu\")'" "$CONF_MEASURED" "$CAT_INSTALLED" \
            "the eleventh missing measurement: the PYTHON ONNX Runtime. It is a different build from the Java one MATRIX uses, and it is the one that finds the GPU"

        value="$("$venv_python" -c 'import torch; print(torch.cuda.is_available(), torch.cuda.get_device_name(0) if torch.cuda.is_available() else "-")' 2>/dev/null || true)"
        measure "python.torch_cuda_available" "${value:-unavailable}" null \
            ".venv/bin/python -c 'import torch; torch.cuda.is_available()'" "$CONF_MEASURED" "$CAT_RUNTIME" \
            "the torch path DOES reach the GPU. This is a fact about the project's Python stack, NOT about MATRIX's Java path, and the two must not be conflated"
    else
        measure "python.torch_version" "unavailable" null ".venv/bin/python" "$CONF_UNAVAILABLE" "$CAT_INSTALLED" \
            "the project's virtualenv is absent, so the GPU torch stack cannot be measured"
        measure "python.onnxruntime_version" "unavailable" null ".venv/bin/python" "$CONF_UNAVAILABLE" "$CAT_INSTALLED" \
            "the project's virtualenv is absent, so the Python ONNX Runtime cannot be measured"
        measure "python.torch_cuda_available" "unavailable" null ".venv/bin/python" "$CONF_UNAVAILABLE" "$CAT_RUNTIME" \
            "the project's virtualenv is absent"
    fi

    # ── GraalVM ──────────────────────────────────────────────────────────────
    if have native-image || have gu || have mandrel; then
        value="$(command -v native-image || command -v gu || command -v mandrel || true)"
        measure "toolchain.graalvm_native_image" "$value" "path" \
            "command -v native-image / gu / mandrel" "$CONF_MEASURED" "$CAT_INSTALLED" \
            "the eleventh missing measurement: the native-image toolchain IS present"
    else
        measure "toolchain.graalvm_native_image" "absent" null \
            "command -v native-image / gu / mandrel" "$CONF_MEASURED" "$CAT_INSTALLED" \
            "the eleventh missing measurement: GraalVM and Mandrel are ABSENT on this host, stated rather than implied. The native-image test classes therefore cannot be executed here, which is why the recorded test totals carry failures that are environmental rather than code defects"
    fi

    # ── disk headroom for the three things that consume it ───────────────────
    value="$(du -sh "${REPO_ROOT}/models" 2>/dev/null | awk '{print $1}' || true)"
    measure "storage.model_cache_size" "${value:-unavailable}" null "du -sh models" "$CONF_MEASURED" "$CAT_HARDWARE" \
        "the eleventh missing measurement: the model cache the repository ships"

    value="$(du -sh "${REPO_ROOT}"/*/build 2>/dev/null | awk '{ s += $1 }' >/dev/null 2>&1; du -sch "${REPO_ROOT}"/*/build 2>/dev/null | tail -1 | awk '{print $1}' || true)"
    measure "storage.build_directories_size" "${value:-unavailable}" null "du -sch */build" "$CONF_MEASURED" "$CAT_HARDWARE" \
        "the eleventh missing measurement: what the build directories already hold, against the headroom above"

    value="$(df --output=avail -BG "${REPO_ROOT}" 2>/dev/null | tail -1 | tr -dc '0-9' || true)"
    measure "storage.headroom_for_builds_gib" "${value:-unavailable}" "GiB" \
        "df --output=avail -BG <the repository>" "$CONF_MEASURED" "$CAT_RUNTIME" \
        "the eleventh missing measurement: the headroom for builds, model caches and test artefacts, against the sizes measured above"

    value="$(df --output=avail -BG /tmp 2>/dev/null | tail -1 | tr -dc '0-9' || true)"
    measure "storage.headroom_for_test_artifacts_gib" "${value:-unavailable}" "GiB" \
        "df --output=avail -BG /tmp" "$CONF_MEASURED" "$CAT_RUNTIME" \
        "the eleventh missing measurement: /tmp is the place a harness writes, and it is the SMALLER of the two budgets"
}

emit_measurements_json() {
    local first=1 item
    printf '[\n'
    for item in "${MEAS[@]}"; do
        if [ "$first" -eq 0 ]; then
            printf ',\n'
        fi
        printf '      %s' "$item"
        first=0
    done
    printf '\n    ]'
}

emit_json() {
    printf '{\n'
    printf '  "schemaVersion": "%s",\n' "1.0.0"
    printf '  "generatedAt": "%s",\n' "$(date +%Y-%m-%dT%H:%M:%S%z)"
    printf '  "host": "%s",\n' "$(uname -n 2>/dev/null || echo unavailable)"
    printf '  "legend": {\n'
    printf '    "confidence": "measured | reported-by-the-system | inferred | unavailable",\n'
    printf '    "categories": "%s",\n' "${CAT_HARDWARE}|${CAT_INSTALLED}|${CAT_RUNTIME}|${CAT_MATRIX}|${CAT_BENCHMARK}"
    printf '    "note": "a measurement sits in exactly one category, and the category is the strongest one the evidence supports. A value of unavailable with a reason is not a zero."\n'
    printf '  },\n'
    printf '  "cpu": {\n'
    printf '    "model": "%s",\n'          "$(lscpu_key 'Model name')"
    printf '    "vendor": "%s",\n'         "$(lscpu_key 'Vendor ID')"
    printf '    "architecture": "%s",\n'   "$(lscpu_key 'Architecture')"
    printf '    "logicalCpus": "%s",\n'    "$(nproc 2>/dev/null || echo unavailable)"
    printf '    "coresPerSocket": "%s",\n' "$(lscpu_key 'Core(s) per socket')"
    printf '    "sockets": "%s",\n'        "$(lscpu_key 'Socket(s)')"
    printf '    "threadsPerCore": "%s",\n'  "$(lscpu_key 'Thread(s) per core')"
    printf '    "maxMhz": "%s",\n'         "$(lscpu_key 'CPU max MHz')"
    printf '    "avx2": "%s",\n'           "$(flag_present avx2)"
    printf '    "avx512f": "%s",\n'        "$(flag_present avx512f)"
    printf '    "amxTile": "%s"\n'         "$(flag_present amx_tile)"
    printf '  },\n'
    printf '  "memory": {\n'
    printf '    "totalGiB": "%s",\n'    "$(meminfo_gib MemTotal)"
    printf '    "availableGiB": "%s",\n' "$(meminfo_gib MemAvailable)"
    printf '    "swapTotalGiB": "%s",\n' "$(meminfo_gib SwapTotal)"
    printf '    "numaNodes": "%s"\n'    "$(lscpu_key 'NUMA node(s)')"
    printf '  },\n'
    printf '  "gpu": {\n'
    printf '    "nvidiaSmiPresent": "%s",\n' "$(have nvidia-smi && echo yes || echo no)"
    printf '    "name": "%s",\n'        "$(gpu_field name)"
    printf '    "memoryMiB": "%s",\n'   "$(gpu_field memory.total --nounits)"
    printf '    "driver": "%s"\n'       "$(gpu_field driver_version)"
    printf '  },\n'
    printf '  "storage": {\n'
    printf '    "rootAvailGiB": "%s",\n' "$(df --output=avail -BG / 2>/dev/null | tail -1 | tr -dc '0-9')"
    printf '    "rootAvailBytes": "%s"\n'  "$(df --output=avail -B1 / 2>/dev/null | tail -1 | tr -dc '0-9')"
    printf '  },\n'
    printf '  "measurements": '
    collect_measurements
    emit_measurements_json
    printf '\n}\n'
}

emit_profile() {
    local cores_per_socket sockets threads_per_core physical_cores logical_cores
    local max_mhz l1d l1i l2 l3
    local root_avail_gib root_avail_bytes heap_ceiling_gib

    cores_per_socket="$(lscpu_key 'Core(s) per socket')"
    sockets="$(lscpu_key 'Socket(s)')"
    threads_per_core="$(lscpu_key 'Thread(s) per core')"
    logical_cores="$(nproc 2>/dev/null || echo unavailable)"
    max_mhz="$(lscpu_key 'CPU max MHz')"
    l1d="$(lscpu_key 'L1d cache')"
    l1i="$(lscpu_key 'L1i cache')"
    l2="$(lscpu_key 'L2 cache')"
    l3="$(lscpu_key 'L3 cache')"

    # Physical cores = cores per socket x sockets. Only derivable when both halves are
    # numeric; "unknown" is kept rather than defaulting, because a wrong thread-pool
    # ceiling is worse than an absent one.
    physical_cores="unknown"
    case "$cores_per_socket" in ''|*[!0-9]*) : ;; *) case "$sockets" in ''|*[!0-9]*) : ;; *)
        physical_cores=$(( cores_per_socket * sockets )) ;; esac ;; esac

    root_avail_gib="$(df --output=avail -BG / 2>/dev/null | tail -1 | tr -dc '0-9')"
    root_avail_bytes="$(df --output=avail -B1 / 2>/dev/null | tail -1 | tr -dc '0-9')"
    heap_ceiling_gib="$(awk -v r="$(meminfo_gib MemTotal)" 'BEGIN { printf "%.0f", r * 0.5 }')"

    cat <<PROFILE
<!-- GENERATED by scripts/hardware-probe.sh — do not hand-edit. -->
# HARDWARE-PROFILE.md — RECON-W30

The tuning baseline for later waves, generated by \`scripts/hardware-probe.sh\`.

\`\`\`bash
scripts/hardware-probe.sh            # rewrite this file
scripts/hardware-probe.sh --stdout   # print it, write nothing
scripts/hardware-probe.sh --json     # machine-readable inventory on stdout
\`\`\`

Every row names the command that produced it. A cell reading \`unavailable\` means the
probe could not run on this host. It is **not** a zero and must not be consumed as a
measurement: the distinction between "this CPU has no AMX" and "the probe could not
see" is the whole reason the probe reports provenance.

## Machine identity

| Field | Value | Probe |
|---|---|---|
| Generated | $(date +%Y-%m-%dT%H:%M:%S%z) | \`date\` |
| System | $(cat /sys/class/dmi/id/sys_vendor 2>/dev/null || echo unavailable) $(cat /sys/class/dmi/id/product_name 2>/dev/null || echo unavailable) | \`/sys/class/dmi/id/*\` |
| Kernel | $(uname -sr 2>/dev/null || echo unavailable) | \`uname -sr\` |
| Distribution | $(awk -F= '$1 == "PRETTY_NAME" { gsub(/"/, "", $2); print $2; exit }' /etc/os-release 2>/dev/null || echo unavailable) | \`/etc/os-release\` |
| JVM | $(java -version 2>&1 | head -1 || echo unavailable) | \`java -version\` |
| JVM the build uses | $(java_for_build) | the JDK the repository builds with |
| Gradle wrapper | $(grep -oE 'gradle-[0-9.]+-[a-z]+\.zip' "${REPO_ROOT}/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null | head -1 || echo unavailable) | \`gradle-wrapper.properties\` |

## CPU

| Field | Value | Probe |
|---|---|---|
| Model | $(lscpu_key 'Model name') | \`lscpu\` |
| Vendor | $(lscpu_key 'Vendor ID') | \`lscpu\` |
| Architecture | $(lscpu_key 'Architecture') | \`lscpu\` |
| Sockets | $sockets | \`lscpu\` |
| Cores per socket | $cores_per_socket | \`lscpu\` |
| **Physical cores** | $physical_cores | cores/socket x sockets (derived) |
| Threads per core | $threads_per_core | \`lscpu\` |
| **Logical CPUs** | $logical_cores | \`nproc\` |
| Max frequency | $max_mhz MHz | \`lscpu\` |
| Virtualization | $(lscpu_key 'Hypervisor vendor') | \`lscpu\` |

### SIMD / ISA capability

Checked by exact token match, not substring: \`avx\` is a substring of \`avx512f\`, so a
grep would report AVX-512 on an AVX2-only CPU and authorise a kernel that traps.

| ISA | Present | What it would buy |
|---|---|---|
| \`avx\` | $(flag_present avx) | 256-bit float/int lanes |
| \`avx2\` | $(flag_present avx2) | 256-bit integer ops, Vector API baseline |
| \`avx512f\` | $(flag_present avx512f) | 512-bit lanes; HDC popcount over 8x64-bit words/vector |
| \`avx512bw\` | $(flag_present avx512bw) | byte/word masked ops for bit-level kernels |
| \`avx512vl\` | $(flag_present avx512vl) | 128/256-bit ops on 512-bit registers |
| \`avx512dq\` | $(flag_present avx512dq) | doubleword ops |
| \`avx512cd\` | $(flag_present avx512cd) | conflict detection |
| \`fma\` | $(flag_present fma) | fused multiply-add |
| \`f16c\` | $(flag_present f16c) | half-precision conversion |
| \`sha_ni\` | $(flag_present sha_ni) | hardware SHA, relevant to content hashing |
| \`amx_tile\` | $(flag_present amx_tile) | tile matrix unit |
| \`amx_int8\` | $(flag_present amx_int8) | int8 matrix unit |
| \`amx_bf16\` | $(flag_present amx_bf16) | bf16 matrix unit |

The FULL set the CPU reports, not only the thirteen above, is in the machine-readable
profile as \`cpu.isa_extensions\`.

### Cache hierarchy

| Level | Size | Instances |
|---|---|---|
| L1d | $l1d | $(lscpu_cache_instances L1d) |
| L1i | $l1i | $(lscpu_cache_instances L1i) |
| L2 | $l2 | $(lscpu_cache_instances L2) |
| L3 | $l3 | $(lscpu_cache_instances L3) |

## Memory

| Field | Value | Probe |
|---|---|---|
| RAM total | $(meminfo_gib MemTotal) GiB | \`/proc/meminfo\` MemTotal (KiB / 1048576) |
| RAM available | $(meminfo_gib MemAvailable) GiB | \`/proc/meminfo\` MemAvailable |
| Swap total | $(meminfo_gib SwapTotal) GiB | \`/proc/meminfo\` SwapTotal |
| NUMA nodes | $(lscpu_key 'NUMA node(s)') | \`lscpu\` |
| Compressed swap | $(lsblk -d -n -o NAME,SIZE 2>/dev/null | grep zram || echo none) | \`lsblk\` |
| zram size | $(awk '$3 == "/dev/zram0" { print $2; exit }' /proc/swaps 2>/dev/null || echo unavailable) bytes | \`/proc/swaps\` |
| zram algorithm | $(cat /sys/block/zram0/comp_algorithm 2>/dev/null | head -1 | sed 's/\[//; s/\]//' || echo unavailable) | \`/sys/block/zram0/comp_algorithm\` |

## GPU — the five questions, kept apart

A GPU model is a hardware capability. A library on the loader's path is installed
software. A provider ONNX Runtime offers is runtime availability. MATRIX executing on
the GPU is verified MATRIX usage. A speed-up is a benchmarked benefit. **None of them
implies the next.**

### Hardware capability

| Field | Value | Probe |
|---|---|---|
| Device | $(gpu_field name) | \`nvidia-smi --query-gpu=name\` |
| VRAM | $(nvidia-smi --query-gpu=memory.total --format=csv,noheader 2>/dev/null | head -1) | \`nvidia-smi\` |
| Current utilisation | $(gpu_field utilization.gpu --nounits) percent | \`nvidia-smi --query-gpu=utilization.gpu\` |
| VRAM in use | $(gpu_field memory.used --nounits) MiB | \`nvidia-smi --query-gpu=memory.used\` |

### Installed software

| Field | Value | Probe |
|---|---|---|
| Driver | $(gpu_field driver_version) | \`nvidia-smi\` |
| Driver CUDA COMPATIBILITY | $(nvidia-smi 2>/dev/null | awk -F'CUDA Version: ' '/CUDA Version/{gsub(/[ \t|]+$/,"",$2); print $2; exit}') | \`nvidia-smi\` — **the newest runtime the DRIVER supports, NOT an installed toolkit** |
| Installed toolkit (nvcc) | $(nvcc --version 2>/dev/null | awk -F'release ' '/release/{split($2,a,","); print a[1]; exit}' || echo unavailable) | \`nvcc --version\` |
| Installed toolkit directories | $(ls -d /usr/local/cuda-* 2>/dev/null | sort -V | tr '\n' ' ' | sed 's/ $//' || echo unavailable) | \`ls -d /usr/local/cuda-*\` |
| cuDNN | $(ldconfig -p 2>/dev/null | grep -oE 'libcudnn[^ ]*\.so\.[0-9]+' | sed 's/.*\.so\.//' | sort -u | tr '\n' ' ' | sed 's/ $//' || echo unavailable) | \`ldconfig -p\` |
| libcudart the LOADER finds | $(resolved_libcudart || echo unavailable) | \`ldconfig -p\` — **this is the one that decides whether the registration succeeds** |
| does it export the provider symbol | $(symbol_count "$(resolved_libcudart || true)" "$CUDA_PROVIDER_SYMBOL") time(s) | \`nm -D --defined-only\` |
| libcudart that DOES export it | $(find "$REPO_ROOT/.venv/lib" -name "$CUDA_PROVIDER_SONAME" 2>/dev/null | head -1 || echo unavailable) | the project's virtualenv |

### Runtime availability — from a REAL run of the Java API MATRIX uses

| Field | Value | Probe |
|---|---|---|
| ONNX Runtime providers offered | $(run_ort_probe "" | awk -F= '/^available=/{print $2; exit}' || echo unavailable) | a single-file Java program against the jars the repository resolves |
| addCUDA() WITHOUT LD_LIBRARY_PATH | $(run_ort_probe "" | awk -F= '/^addCUDA=/{print $2; exit}' || echo unavailable) | **this is MATRIX's own configuration** |
| addCUDA() WITH LD_LIBRARY_PATH | $(run_ort_probe "$(nvidia_venv_libdirs | tr '\n' ':' | sed 's/:$//')" | awk -F= '/^addCUDA=/{print $2; exit}' || echo unavailable) | the same probe, one environment variable added |

### Verified MATRIX usage

MATRIX does **not** execute on the GPU. \`OnnxRuntimeAdapter.java\` requests CUDA inside
a try/catch whose catch logs a warning and continues, and the diagnostic that reports
the active provider is a hardcoded \`CPUExecutionProvider\`.

### Benchmarked benefit

${BENCH_SPEEDUP}x, on ${BENCH_MODEL_PATH} at sequence length ${BENCH_SEQ_LEN},
${BENCH_ITERATIONS} iterations: ${BENCH_CPU_MS} ms on the CPU against ${BENCH_CUDA_MS} ms on
the CUDA. **Reported by the W46.0 hardware report; this probe runs no benchmark and does
not reproduce it.**

## Storage

| Field | Value | Probe |
|---|---|---|
| Root filesystem | $(df -h / 2>/dev/null | tail -1 | awk '{print $1}') | \`df -h /\` |
| Root available | $root_avail_gib GiB ($root_avail_bytes bytes) | \`df --output=avail -B1 /\` |
| /tmp quota | $(df -h /tmp 2>/dev/null | tail -1 | awk '{print $2}') | \`df -h /tmp\` — the smaller of the two budgets |
| /tmp available | $(df --output=avail -BG /tmp 2>/dev/null | tail -1 | tr -dc '0-9') GiB | \`df --output=avail -BG /tmp\` |
| Model cache | $(du -sh "${REPO_ROOT}/models" 2>/dev/null | awk '{print $1}' || echo unavailable) | \`du -sh models\` |
| Build directories | $(du -sch "${REPO_ROOT}"/*/build 2>/dev/null | tail -1 | awk '{print $1}' || echo unavailable) | \`du -sch */build\` |
| Block devices | $(lsblk -d -n -o NAME,ROTA,SIZE 2>/dev/null | grep -vE '^loop|^zram' | wc -l) | \`lsblk -d\` |

| Device | Size | Rotational | Model | Transport |
|---|---|---|---|---|
$(lsblk -d -n -o NAME,ROTA,SIZE,MODEL,TRAN 2>/dev/null | grep -vE '^loop|^zram' | awk -F' ' '{printf "| `%s` | %s | %s | %s | %s |\n", $1, $3, $2, $4, $5}')

Queue scheduler (nvme0n1): $(cat /sys/block/nvme0n1/queue/scheduler 2>/dev/null || echo unavailable)

## The build budget, read from the build files

| Field | Value | Probe |
|---|---|---|
| Gradle daemon heap | $(grep -oE '\-Xmx[0-9]+[gm]' "${REPO_ROOT}/gradle.properties" 2>/dev/null | head -1 || echo unavailable) | \`gradle.properties\` |
| matrix-core test JVM heap | $(grep -oE "maxHeapSize = '[0-9]+[gm]'" "${REPO_ROOT}/matrix-core/build.gradle" 2>/dev/null | grep -oE '[0-9]+[gm]' | head -1 || echo unavailable) | \`matrix-core/build.gradle\` |
| Docker | $(docker --version 2>/dev/null | head -1 || echo unavailable) | \`docker --version\` |
| Docker resources | $(docker info --format '{{.NCPU}} CPUs / {{.MemTotal}} bytes' 2>/dev/null || echo unavailable) | \`docker info\` |
| Docker build cache | $(docker system df 2>/dev/null | awk '/^Build Cache/ { print $4; exit }' || echo unavailable) | \`docker system df\` |

## Tools present / absent

A tuning claim that depends on a missing tool is a claim nobody can reproduce, so the
absence is on the record.

| Tool | State |
|---|---|
$(for t in lscpu nproc numactl lspci nvidia-smi nvme sysbench sqlite3 python3 jq detekt docker nvcc native-image; do
    if have "$t"; then printf '| `%s` | present |\n' "$t"; else printf '| `%s` | **absent** |\n' "$t"; fi
done)

## Derived tuning ceilings

Derived from the inventory above. **Not measured.** Each is a hypothesis that
TUNING-PARAMETERS.md must either confirm with a benchmark number or retire.

- **Worker threads: $physical_cores** (physical cores, not $logical_cores logical
  CPUs). SMT siblings share L1d, L2 and the vector units, so oversubscribing them
  trades cache residency for throughput that is not there.
- **Heap ceiling: $heap_ceiling_gib GiB** (50% of RAM). The remainder covers the OS, the
  page cache the NVMe tier wants, and the co-resident gateway.
- **Batch sizing: from L2 per-core share**, not from a round number. A round batch size
  is a magic number with a plausible-looking justification attached.

## What this profile does NOT establish

- It does not establish that any kernel is fast. It is an inventory. Speed claims come
  from \`scripts/perf-probe.sh\`, which stamps its own environment block into every run.
- It does not establish GPU suitability. The GPU is present and usable, and MATRIX's
  Java path does not use it; the row that says so is the *verified MATRIX usage* row,
  not the hardware row.
- \`avx512f\` being present is a capability, not a win. Whether a Vector API HDC kernel
  beats scalar popcount here is a measurement question, and the wave plan treats it as
  one.
- The driver's CUDA compatibility number is not an installed toolkit, and a registered
  ONNX Runtime provider is not a running GPU session. The five sections above keep
  them apart on purpose.
PROFILE
}

# ── the command line ────────────────────────────────────────────────────────
#
# The JSON profile is written ONLY where the caller names a path. The bare default
# keeps the original behaviour of rewriting the markdown profile, because that file
# is generated by design; --markdown <path> writes it elsewhere instead.
while [ $# -gt 0 ]; do
    case "$1" in
        --stdout)
            MODE="stdout"
            shift
            ;;
        --markdown)
            MODE="markdown"
            OUT_PATH="${2:-}"
            shift 2 || shift
            ;;
        --json)
            MODE="json"
            if [ "${2:-}" != "" ] && [ "${2:0:1}" != "-" ]; then
                OUT_PATH="$2"
                shift
            fi
            shift
            ;;
        --help|-h)
            sed -n "${HELP_SED_RANGE}p" "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            printf 'usage: %s [--stdout|--markdown <path>|--json[ <path>]]\n' "$0" >&2
            exit 2
            ;;
    esac
done

case "$MODE" in
    json)
        if [ -n "$OUT_PATH" ]; then
            mkdir -p "$(dirname "$OUT_PATH")"
            emit_json > "$OUT_PATH"
            printf 'wrote %s\n' "$OUT_PATH"
        else
            emit_json
        fi
        ;;
    stdout)
        emit_profile
        ;;
    markdown)
        if [ -n "$OUT_PATH" ]; then
            mkdir -p "$(dirname "$OUT_PATH")"
            emit_profile > "$OUT_PATH"
            printf 'wrote %s\n' "$OUT_PATH"
        else
            emit_profile > "$PROFILE_PATH"
            printf 'wrote %s\n' "$PROFILE_PATH"
        fi
        ;;
    write)
        mkdir -p "$(dirname "$PROFILE_PATH")"
        emit_profile > "$PROFILE_PATH"
        printf 'wrote %s\n' "$PROFILE_PATH"
        ;;
esac
