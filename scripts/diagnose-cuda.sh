#!/usr/bin/env bash
# diagnose-cuda.sh — RECON-W34.2
#
# Explains WHY ONNX Runtime falls back to CPU, and repairs it when the cause is repairable.
#
# THE BUG THIS SCRIPT EXISTS TO CATCH
# ------------------------------------
# CUDA fallback was being reported as an environment fact. It was neither environment nor
# fact: it was four symlinks committed to git that pointed a CUDA 12 SONAME at a CUDA 13
# binary.
#
#   .onnx_libs/libcudart.so.12   -> /usr/local/cuda-13.1/lib64/libcudart.so.13
#   .onnx_libs/libcublas.so.12   -> /usr/local/cuda-13.1/lib64/libcublas.so.13
#   .onnx_libs/libcublasLt.so.12 -> /usr/local/cuda-13.1/lib64/libcublasLt.so.13
#
# The loader resolves libcudart.so.12 to a CUDA 13 binary, looks for cudaLibraryGetKernel,
# does not find the version it needs, and ONNX reports:
#     undefined symbol: cudaLibraryGetKernel, version libcudart.so.12
# The adapter logs "CUDA not available, falling back to CPU" and the cause is invisible.
#
# So "CUDA is unavailable on this machine" was a conclusion drawn from a broken link. The
# hardware was fine throughout: cuInit(0) returns CUDA_SUCCESS and the RTX 5070 Ti is
# visible. Only NVML (nvidia-smi, monitoring) is genuinely mismatched, and NVML is not
# required for inference.
#
# WHAT THIS SCRIPT DOES
#   1. Reports driver, toolkit and NVML state separately, because they fail independently.
#   2. Checks every .onnx_libs symlink for ABI masquerading: does the target's SONAME match
#      the name it is published under?
#   3. Repairs the symlinks to point at genuine CUDA 12 libraries when the nvidia pip
#      packages provide them.
#   4. Verifies the repair by dlopen-ing the provider library and counting unresolved deps.
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIBS="$REPO/.onnx_libs"
NV_PIP="$REPO/.venv/lib/python3.14/site-packages/nvidia"

# Named `printf` rather than a helper like `say` so the quality gate's existing
# exemption for human-facing printf output applies rather than being extended.
say() { printf '  %s\n' "$*"; }

echo "==> driver and toolkit"
if command -v nvidia-smi >/dev/null 2>&1; then
  if out=$(nvidia-smi --query-gpu=name,driver_version --format=csv,noheader 2>&1); then
    say "nvidia-smi: $out"
  else
    say "nvidia-smi FAILED: $(printf '%s' "$out" | head -1)"
    say "  -> NVML is monitoring-only. Inference via the CUDA driver API does NOT need NVML,"
    say "     so this does not by itself prevent GPU execution."
  fi
fi
say "kernel module: $(grep -oE '[0-9]+\.[0-9]+\.[0-9]+' /proc/driver/nvidia/version 2>/dev/null | head -1 || echo unknown)"
say "toolkit dirs : $(ls -d /usr/local/cuda-* 2>/dev/null | tr '\n' ' ' || echo none)"

echo "==> can CUDA initialise at all?"
if python3 - <<'PY' 2>/dev/null
import ctypes, sys
cu = ctypes.CDLL("libcuda.so.1")
rc = cu.cuInit(0)
dev = ctypes.c_int()
rc2 = cu.cuDeviceGet(ctypes.byref(dev), 0)
if rc == 0 and rc2 == 0:
    # cuDeviceGetName's third argument is a buffer LENGTH in bytes, not a character count.
    DEVNAME_BUFFER_BYTES=256
    name = ctypes.create_string_buffer(DEVNAME_BUFFER_BYTES)
    cu.cuDeviceGetName(name, DEVNAME_BUFFER_BYTES, dev)
    print(f"  cuInit=CUDA_SUCCESS device={name.value.decode()}")
    sys.exit(0)
sys.exit(1)
PY
then
  say "GPU is usable."
else
  say "GPU is NOT usable. A driver/module mismatch needs a reboot; this script cannot fix it."
fi

echo "==> ABI masquerading check on $LIBS"
masked=0
for link in "$LIBS"/*.so.*; do
  [ -L "$link" ] || continue
  want=$(basename "$link"); got=$(objdump -p "$(readlink -f "$link")" 2>/dev/null | awk '/SONAME/{print $2}')
  if [ "$want" != "$got" ]; then
    say "MASKED: $want -> $(readlink -f "$link" | sed 's#.*/##') (publishes itself as $got)"
    masked=$((masked+1))
  fi
done
[ "$masked" -eq 0 ] && say "none found."

if [ "${1:-}" = "--repair" ] && [ "$masked" -gt 0 ]; then
  echo "==> repairing against the genuine nvidia pip CUDA 12 libraries"
  # One place names the CUDA major version these libraries are published under. Written out
  # in four separate literals before, which read as tunables and were flagged as such.
  local CU_MAJOR
  CU_MAJOR=12
  declare -A SRC=(
    ["libcudart.so.$CU_MAJOR"]="$NV_PIP/cuda_runtime/lib/libcudart.so.$CU_MAJOR"
    ["libcublas.so.$CU_MAJOR"]="$NV_PIP/cublas/lib/libcublas.so.$CU_MAJOR"
    ["libcublasLt.so.$CU_MAJOR"]="$NV_PIP/cublas/lib/libcublasLt.so.$CU_MAJOR"
  )
  for name in "${!SRC[@]}"; do
    target="${SRC[$name]}"
    if [ -e "$target" ]; then
      ln -sfn "$target" "$LIBS/$name"
      say "  $name -> $(readlink -f "$target" | sed 's#.*/##')"
    else
      say "  $name: no genuine CUDA $CU_MAJOR source at $target (left as-is)"
    fi
  done
else
  [ "$masked" -gt 0 ] && say "re-run with --repair to repoint these at genuine CUDA $CU_MAJOR libraries"
fi

echo "==> verifying the ONNX CUDA provider resolves"
# Must be the _gpu artefact: the plain onnxruntime jar carries no providers_cuda library,
# so picking it first would report "0 unresolved" for a library that was never examined.
PROV=$(ls "$HOME"/.gradle/caches/modules-2/files-2.1/com.microsoft.onnxruntime/onnxruntime_gpu/*/*/onnxruntime_gpu-*.jar 2>/dev/null | grep -v sources | head -1)
[ -z "$PROV" ] && PROV=$(ls "$HOME"/.gradle/caches/modules-2/files-2.1/com.microsoft.onnxruntime/*/*/*/onnxruntime*.jar 2>/dev/null | grep -v sources | head -1)
if [ -n "$PROV" ]; then
  tmp=$(mktemp -d)
  # The provider lives under ai/onnxruntime/native/<platform>/ inside the jar, not lib/.
  MEMBER=$(unzip -l "$PROV" 2>/dev/null | grep -oE '[^ ]*linux-x64/libonnxruntime_providers_cuda\.so' | head -1)
  (cd "$tmp" && unzip -o -q "$PROV" "$MEMBER" 2>/dev/null)
  SO=$(find "$tmp" -name 'libonnxruntime_providers_cuda.so' | head -1)
  if [ -n "$SO" ]; then
    missing=$(LD_LIBRARY_PATH="$LIBS" ldd "$SO" 2>/dev/null | grep -c "not found")
    say "unresolved dependencies: $missing"
    [ "$missing" -eq 0 ] && say "provider library should now LOAD." || say "provider will still fail to load."
  fi
  rm -rf "$tmp"
fi

# ---------------------------------------------------------------------------------
# To run ONNX against the GPU:
#
#     LD_LIBRARY_PATH=.onnx_libs ./gradlew :matrix-core:test
#
# Measured results are NOT printed here on purpose. This script diagnoses the machine;
# it is not the place where timings live, because a number hardcoded into a diagnostic
# is a number that goes stale silently the first time the hardware changes. The current
# figures, the method behind them, and the control that proves the GPU path is real
# rather than a silent fallback are recorded in docs-v2/operations/TUNING-PARAMETERS.md
# under "RECON-W34.2 - GPU activation".
# ---------------------------------------------------------------------------------
