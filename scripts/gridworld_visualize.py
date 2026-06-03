#!/usr/bin/env python3
"""GridWorld Pilot — Visualization and Interpretability."""
import json, sys
from pathlib import Path

def main():
    data_file = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("output/pilot-gridworld.json")
    with open(data_file) as f:
        data = json.load(f)

    results = data["results"]
    config = data["config"]

    print("=" * 60)
    print("MATRIX Pilot #1 — GridWorld Agent Results")
    print("=" * 60)
    print(f"Config: {config['generations']} gens × {config['population']} pop × k={config['k']}")
    print(f"Fitness: {results['initialBest']} → {results['finalBest']} (+{results['improvement']})")
    print(f"Time: {results['elapsedMs']}ms ({results['generationsPerSecond']:.1f} gen/s)")
    print(f"Average final: {results['finalAvg']}")
    print()

    demo = data["demo"]
    print("Demo simulation (best agent):")
    print(f"  Steps: {demo['steps']}, Food: {demo['food']}, Collisions: {demo['collisions']}")
    print(f"  Energy: {demo['initialEnergy']} → {demo['finalEnergy']}, Score: {demo['score']}")
    print()

    print("Best agent logic (interpretability):")
    for direction, info in data["bestLogic"].items():
        paths_count = len(info.get("paths", []))
        print(f"  {direction}: leaves={info['leaves']}, splits={info['splits']}, "
              f"depth={info['depth']}, paths={paths_count}")
    print()

    try:
        import matplotlib
        matplotlib.use('Agg')
        import matplotlib.pyplot as plt
        import numpy as np

        gens = [d["generation"] for d in data["generations"]]
        best = [d["best"] for d in data["generations"]]
        avg = [d["avg"] for d in data["generations"]]

        fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 6))

        ax1.plot(gens, best, 'b-', linewidth=2, label='Best Fitness')
        ax1.plot(gens, avg, 'orange', linewidth=1.5, alpha=0.7, label='Average Fitness')
        ax1.fill_between(gens, avg, best, alpha=0.1, color='blue')
        ax1.axhline(y=0, color='gray', linestyle='--', alpha=0.3)
        improvement = results['finalBest'] - results['initialBest']
        ax1.annotate(f'+{improvement}', xy=(gens[-1], best[-1]),
                     xytext=(gens[-1] - 30, best[-1] + 30),
                     arrowprops=dict(arrowstyle='->', color='green'),
                     fontsize=12, color='green', fontweight='bold')
        ax1.set_xlabel('Generation', fontsize=11)
        ax1.set_ylabel('Fitness Score', fontsize=11)
        ax1.set_title('MPDT Agent Evolution in GridWorld', fontsize=13, fontweight='bold')
        ax1.legend(fontsize=10)
        ax1.grid(True, alpha=0.3)

        improvement_pct = []
        for g in range(len(best)):
            if best[g] > 0:
                improvement_pct.append((avg[g] - avg[0]) / max(abs(avg[0]), 1) * 100)
            else:
                improvement_pct.append(0)

        ax2.plot(gens, improvement_pct, 'g-', linewidth=2)
        ax2.fill_between(gens, 0, improvement_pct, alpha=0.15, color='green')
        ax2.axhline(y=0, color='gray', linestyle='--', alpha=0.3)
        ax2.set_xlabel('Generation', fontsize=11)
        ax2.set_ylabel('Avg Improvement (%)', fontsize=11)
        ax2.set_title('Learning Progress Over Time', fontsize=13, fontweight='bold')
        ax2.grid(True, alpha=0.3)

        plt.tight_layout()
        output_path = Path("output/gridworld_evolution.png")
        plt.savefig(output_path, dpi=150, bbox_inches='tight')
        print(f"Chart saved: {output_path}")

        plt.close()

        fig2, ax = plt.subplots(figsize=(8, 5))
        directions = list(data["bestLogic"].keys())
        leaves = [data["bestLogic"][d]["leaves"] for d in directions]
        splits = [data["bestLogic"][d]["splits"] for d in directions]
        x = np.arange(len(directions))
        width = 0.35
        ax.bar(x - width/2, leaves, width, label='Leaves', color='steelblue')
        ax.bar(x + width/2, splits, width, label='Splits', color='coral')
        ax.set_xlabel('Direction Neuron', fontsize=11)
        ax.set_ylabel('Count', fontsize=11)
        ax.set_title('Best Agent — Decision Tree Complexity', fontsize=13, fontweight='bold')
        ax.set_xticks(x)
        ax.set_xticklabels(directions)
        ax.legend()
        ax.grid(True, alpha=0.3, axis='y')
        plt.tight_layout()
        output_path2 = Path("output/gridworld_trees.png")
        plt.savefig(output_path2, dpi=150, bbox_inches='tight')
        print(f"Tree chart saved: {output_path2}")
        plt.close()

    except ImportError:
        print("matplotlib not available — skipping charts")

if __name__ == "__main__":
    main()
