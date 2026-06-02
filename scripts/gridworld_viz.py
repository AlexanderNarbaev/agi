#!/usr/bin/env python3
"""GridWorld Agent — Visualization Script.

Expects the matrix-core JAR in matrix-core/build/.
Run: python3 scripts/gridworld_viz.py
"""
import subprocess, json, sys, os
from pathlib import Path

PROJECT_DIR = Path(__file__).resolve().parent.parent
JAR = next(PROJECT_DIR.glob("matrix-core/build/matrix-core-*-runner.jar"), None)

if JAR is None:
    print("Building matrix-core...")
    subprocess.run(["./gradlew", ":matrix-core:quarkusBuild",
                    "-Dquarkus.package.jar.type=uber-jar"], cwd=PROJECT_DIR)
    JAR = next(PROJECT_DIR.glob("matrix-core/build/matrix-core-*-runner.jar"))

GENS = int(sys.argv[1]) if len(sys.argv) > 1 else 50
POP = int(sys.argv[2]) if len(sys.argv) > 2 else 20
K = int(sys.argv[3]) if len(sys.argv) > 3 else 16

print(f"MATRIX GridWorld — {GENS} generations × {POP} agents × k={K}")
print("=" * 60)

proc = subprocess.run(
    ["java", "-jar", str(JAR), "simulate",
     "-g", str(GENS), "-p", str(POP), "-k", str(K), "--seed", "42"],
    capture_output=True, text=True
)

data = []
for line in proc.stderr.split("\n"):
    if "Gen" in line and "best=" in line:
        parts = line.strip().split()
        g = int(parts[1])
        b = int(parts[3].split("=")[1])
        a = int(parts[4].split("=")[1])
        data.append({"generation": g, "best": b, "avg": a})

if data:
    first = data[0]
    last = data[-1]
    improvement = last["best"] - first["best"]
    print(f"\nResults:")
    print(f"  Initial best fitness: {first['best']}")
    print(f"  Final best fitness:   {last['best']}")
    print(f"  Improvement:           +{improvement}")
    print(f"  Final average:         {last['avg']}")

    output = PROJECT_DIR / "gridworld_results.json"
    json.dump(data, output.open("w"), indent=2)
    print(f"\nData saved to: {output}")
else:
    print("No data generated. Check JAR path.")
