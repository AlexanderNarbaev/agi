#!/usr/bin/env bash
# RECON-W30 — hardware inventory probe.
#
# Emits docs-v2/operations/HARDWARE-PROFILE.md from live probes of THIS machine.
# Every value in the generated profile carries the command that produced it, so a
# reviewer can re-run the probe and diff rather than take the profile on trust.
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
# Usage:
#   scripts/hardware-probe.sh            # write docs-v2/operations/HARDWARE-PROFILE.md
#   scripts/hardware-probe.sh --stdout   # print the profile, write nothing
#   scripts/hardware-probe.sh --json     # emit machine-readable inventory
#
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROFILE_PATH="${REPO_ROOT}/docs-v2/operations/HARDWARE-PROFILE.md"
MODE="${1:-write}"

# Pin the locale for the whole script. Both the decimal separator and the sort order of
# every generated table depend on it, so an unpinned probe is not reproducible.
export LC_ALL=C
export LANG=C

# /proc/meminfo reports KiB. One GiB is 2^30 bytes = 1048576 KiB.
readonly MEMINFO_KIB_PER_GIB=1048576
# Times 1024 yields MiB; used where MiB is the wanted unit.
readonly MEMINFO_KIB_PER_MIB=1024

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
    [ -n "$kib" ] || { printf 'unavailable\n'; return; }
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
    have nvidia-smi || { printf 'no nvidia-smi\n'; return; }
    local out
    out="$(nvidia-smi --query-gpu="$1" --format=csv,noheader 2>/dev/null | head -1)"
    printf '%s\n' "${out:-unavailable}"
}

emit_json() {
    printf '{\n'
    printf '  "generatedAt": "%s",\n' "$(date +%Y-%m-%dT%H:%M:%S%z)"
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
    printf '    "rootAvailBytes": %s\n'  "$(df --output=avail -B1 / 2>/dev/null | tail -1 | tr -dc '0-9')"
    printf '  }\n'
    printf '}\n'
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
    if [ "$cores_per_socket" != "unavailable" ] && [ "$sockets" != "unavailable" ]; then
        physical_cores=$(( cores_per_socket * sockets ))
    fi

    root_avail_gib="$(df --output=avail -BG / 2>/dev/null | tail -1 | tr -dc '0-9')"
    root_avail_bytes="$(df --output=avail -B1 / 2>/dev/null | tail -1 | tr -dc '0-9')"
    heap_ceiling_gib="$(awk -v r="$(meminfo_gib MemTotal)" 'BEGIN { printf "%.0f", r * 0.5 }')"

    cat <<PROFILE
<!-- GENERATED by scripts/hardware-probe.sh — do not hand-edit. -->
# HARDWARE-PROFILE.md — RECON-W30

The tuning baseline for later waves, generated by \`scripts/hardware-probe.sh\`.

\`\`\`bash
scripts/hardware-probe.sh          # rewrite this file
scripts/hardware-probe.sh --json   # machine-readable inventory
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
| JVM | $(java -version 2>&1 | head -1 || echo unavailable) | \`java -version\` |

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

## GPU

| Field | Value | Probe |
|---|---|---|
| \`nvidia-smi\` present | $(have nvidia-smi && echo yes || echo no) | \`command -v\` |
| Device | $(gpu_field name) | \`nvidia-smi --query-gpu=name\` |
| VRAM | $(nvidia-smi --query-gpu=memory.total --format=csv,noheader 2>/dev/null | head -1) | \`nvidia-smi\` |
| Driver | $(gpu_field driver_version) | \`nvidia-smi\` |
| CUDA | $(nvidia-smi 2>/dev/null | awk -F'CUDA Version: ' '/CUDA Version/{gsub(/[ \t|]+$/,"",$2); print $2; exit}') | \`nvidia-smi\` |
| Display adapters | $(lspci -nn 2>/dev/null | grep -icE 'vga|3d controller' || echo unavailable) | \`lspci -nn\` |

## Storage

| Field | Value | Probe |
|---|---|---|
| Root filesystem | $(df -h / 2>/dev/null | tail -1 | awk '{print $1}') | \`df -h /\` |
| Root available | $root_avail_gib GiB ($root_avail_bytes bytes) | \`df --output=avail -B1 /\` |
| Block devices | $(lsblk -d -n -o NAME,ROTA,SIZE 2>/dev/null | grep -vE '^loop|^zram' | wc -l) | \`lsblk -d\` |

| Device | Size | Rotational | Model | Transport |
|---|---|---|---|---|
$(lsblk -d -n -o NAME,ROTA,SIZE,MODEL,TRAN 2>/dev/null | grep -vE '^loop|^zram' | awk -F' ' '{printf "| `%s` | %s | %s | %s | %s |\n", $1, $3, $2, $4, $5}')

Queue scheduler (nvme0n1): $(cat /sys/block/nvme0n1/queue/scheduler 2>/dev/null || echo unavailable)

## Tools present / absent

A tuning claim that depends on a missing tool is a claim nobody can reproduce, so the
absence is on the record.

| Tool | State |
|---|---|
$(for t in lscpu nproc numactl lspci nvidia-smi nvme sysbench sqlite3 python3 jq detekt; do
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
- It does not establish GPU suitability. A discrete GPU is present on this host, but no
  MATRIX code path is verified against it in RECON-W30; native kernels stay deferred
  until a benchmark shows a win on this card.
- \`avx512f\` being present is a capability, not a win. Whether a Vector API HDC kernel
  beats scalar popcount here is a measurement question, and the wave plan treats it as
  one.
PROFILE
}

case "$MODE" in
    --json)   emit_json ;;
    --stdout) emit_profile ;;
    write|"") mkdir -p "$(dirname "$PROFILE_PATH")"
               emit_profile > "$PROFILE_PATH"
               echo "wrote $PROFILE_PATH" ;;
    *)        echo "usage: $0 [--stdout|--json]" >&2; exit 2 ;;
esac
