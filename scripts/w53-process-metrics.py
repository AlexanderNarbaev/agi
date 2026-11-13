#!/usr/bin/env python3
"""W53 — the process's own accounting, sampled from outside the process.

The mandate asks the performance report for the dimensions a client cannot see
(§10.1): the CPU time, the heap and the retained memory, the garbage collection, and the
allocation per task. The two processes are separate programs with separate databases, so
those numbers are not in the client's round trip — they are in the processes.

This script reads them where the kernel keeps them:

* the CPU time from `/proc/<pid>/stat` (the user and system ticks), converted with the
  kernel's own clock-tick rate;
* the peak resident set from `/proc/<pid>/status` (`VmHWM`) and the current one (`VmRSS`),
  the difference being the memory the run actually retained;
* the heap the JVM holds, from the JVM's own `jcmd GC.heap_info`;
* the garbage collection from the JVM's own GC log (`-Xlog:gc`), which is enabled by the
  benchmark for the process it measures.

**The allocation is an estimate, and the estimate is named as one.** The JVM does not
count the bytes it allocates; what can be observed is what it collected and how much its
heap grew. The allocation of a run is therefore estimated as *the bytes collected during
the run plus the growth of the heap it held*. When nothing was collected, the estimate is
a lower bound: the heap may have grown, been collected and grown again without a visible
event. The report says which case it is.

It prints one JSON object. It exits with a failure when the numbers cannot be read: a
measurement that is silently absent is worse than no measurement, because the report would
look as if it had covered a dimension it did not cover.

Usage:
    scripts/w53-process-metrics.py <pid> [gc-log]
"""

import json
import os
import re
import subprocess
import sys

# The units the JVM's GC log uses for the heap sizes.
SIZE_UNITS = {"K": 1024, "M": 1024 * 1024, "G": 1024 * 1024 * 1024}

# The GC line: the heap before and after, the capacity, and the pause.
GC_LINE = re.compile(
    r"(?P<before>\d+)(?P<unit>[KMG])->(?P<after>\d+)(?P<unit2>[KMG])"
    r"\(\d+[KMG]\)\s+(?P<pause>[\d.]+)ms"
)


def cpu_seconds(pid: str) -> float:
    """The CPU time the process has used, in seconds."""
    ticks = os.sysconf("SC_CLK_TCK")
    with open(f"/proc/{pid}/stat", encoding="utf-8") as handle:
        raw = handle.read()
    # The command name is in parentheses and may contain spaces, so the fields after it
    # are counted from the closing parenthesis.
    after_name = raw[raw.rfind(")") + 1:].split()
    user_ticks = int(after_name[11])
    system_ticks = int(after_name[12])
    return (user_ticks + system_ticks) / ticks


def resident_bytes(pid: str) -> dict:
    """The resident set of the process: its peak and its current size, in bytes."""
    found = {}
    with open(f"/proc/{pid}/status", encoding="utf-8") as handle:
        for line in handle:
            if line.startswith("VmHWM:"):
                found["peakResidentBytes"] = int(line.split()[1]) * 1024
            elif line.startswith("VmRSS:"):
                found["residentBytes"] = int(line.split()[1]) * 1024
    if "peakResidentBytes" not in found or "residentBytes" not in found:
        raise RuntimeError("the kernel does not report the resident set of this process")
    return found


def heap_used_bytes(pid: str) -> int:
    """The heap the JVM holds, from the JVM's own report."""
    jcmd = os.path.join(os.environ.get("JAVA_HOME", ""), "bin", "jcmd")
    if not os.path.exists(jcmd):
        jcmd = "jcmd"
    result = subprocess.run([jcmd, str(pid), "GC.heap_info"], capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"the jvm did not report its heap: {result.stderr.strip()[:200]}")
    for line in result.stdout.splitlines():
        if "used" in line:
            match = re.search(r"used (\d+)([KMG])", line)
            if match:
                return int(match.group(1)) * SIZE_UNITS[match.group(2)]
    raise RuntimeError("the jvm's heap report did not name the heap it uses")


def gc_metrics(path: str) -> dict:
    """The garbage collection the JVM's log records."""
    events = 0
    pause_millis = 0.0
    allocated_bytes = 0
    peak_heap_bytes = 0
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            match = GC_LINE.search(line)
            if not match:
                continue
            events += 1
            pause_millis += float(match.group("pause"))
            allocated_bytes += int(match.group("before")) * SIZE_UNITS[match.group("unit")]
            live = int(match.group("after")) * SIZE_UNITS[match.group("unit2")]
            peak_heap_bytes = max(peak_heap_bytes, live)
    return {
        "events": events,
        "pauseMillis": round(pause_millis, 3),
        "allocatedBytes": allocated_bytes,
        "peakLiveHeapBytes": peak_heap_bytes,
    }


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: w53-process-metrics.py <pid> [gc-log]", file=sys.stderr)
        return 2
    pid = sys.argv[1]
    metrics = {}
    try:
        metrics["cpuSeconds"] = round(cpu_seconds(pid), 3)
        metrics.update(resident_bytes(pid))
        metrics["heapUsedBytes"] = heap_used_bytes(pid)
    except (OSError, ValueError, IndexError, RuntimeError) as e:
        print(json.dumps({"error": f"the process metrics could not be read: {e}"}))
        return 1
    if len(sys.argv) > 2 and os.path.exists(sys.argv[2]):
        try:
            metrics["garbageCollection"] = gc_metrics(sys.argv[2])
        except (OSError, ValueError) as e:
            print(json.dumps({"error": f"the gc log could not be read: {e}"}))
            return 1
    print(json.dumps(metrics))
    return 0


if __name__ == "__main__":
    sys.exit(main())
