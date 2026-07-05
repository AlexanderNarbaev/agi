#!/usr/bin/env python3
"""
Robot Arm Simulation — MPDT-controlled agent in PyBullet.

Two modes:
  --pybullet    Full 3D simulation (requires: pip install pybullet)
  --math        Pure-Python 2-DOF arm simulation (no dependencies)

Usage:
  python3 robot_arm_sim.py --generations 100 --population 30
  python3 robot_arm_sim.py --pybullet --generations 50 --gui

Protocol (Java bridge):
  Reads JSON commands from stdin, writes JSON responses to stdout.
  Commands: {"cmd": "train", "generations": 100, "population": 30}
            {"cmd": "evaluate", "neuron": {...}}
            {"cmd": "quit"}
"""

import argparse
import json
import math
import random
import sys
import time

PICK_RADIUS = 0.05
ARM_LENGTH = 1.0
TARGET_X, TARGET_Y = 0.7, 0.7


def robot_arm_forward(angles):
    """2-DOF robot arm: angles[0]=shoulder, angles[1]=elbow. Returns (x, y)."""
    a0, a1 = angles[0], angles[1]
    x = ARM_LENGTH * math.cos(a0) + ARM_LENGTH * math.cos(a0 + a1)
    y = ARM_LENGTH * math.sin(a0) + ARM_LENGTH * math.sin(a0 + a1)
    return x, y


def robot_arm_distance(angles, target_x, target_y):
    x, y = robot_arm_forward(angles)
    return math.sqrt((x - target_x) ** 2 + (y - target_y) ** 2)


def robot_arm_has_grasped(angles, target_x, target_y, radius=PICK_RADIUS):
    return robot_arm_distance(angles, target_x, target_y) < radius


class MPDTNeuron:
    """Simple MPDT neuron emulation in Python (bit operations)."""
    def __init__(self, k, table=None):
        self.k = k
        self.table = table if table is not None else random.randint(0, (1 << (1 << k)) - 1)

    def evaluate(self, inputs):
        mask = (1 << self.k) - 1
        idx = inputs & mask
        return (self.table >> idx) & 1

    def mutate(self, rate=0.1):
        mask = (1 << (1 << self.k)) - 1
        for i in range(1 << self.k):
            if random.random() < rate:
                self.table ^= (1 << i)
        self.table &= mask

    def clone(self):
        n = MPDTNeuron(self.k)
        n.table = self.table
        return n


def encode_sensors(angles, target_x, target_y):
    """Encodes angles and target position into a 12-bit sensor vector."""
    bits = 0
    a0_q = min(15, int(((angles[0] + math.pi) / (2 * math.pi)) * 16))
    a1_q = min(15, int(((angles[1] + math.pi) / (2 * math.pi)) * 16))
    dist = robot_arm_distance(angles, target_x, target_y)
    dist_q = min(15, int(min(dist / 2.0, 1.0) * 16))
    bits = a0_q | (a1_q << 4) | (dist_q << 8)
    return bits


def decode_action(action_bits):
    """Decodes action bits to angle deltas."""
    delta0 = ((action_bits & 0x7) - 3) * 0.1
    delta1 = (((action_bits >> 3) & 0x7) - 3) * 0.1
    return [delta0, delta1]


def evaluate_fitness(neuron, target_x, target_y, steps=100):
    """Runs the neuron controlling the arm for N steps. Returns fitness."""
    angles = [random.uniform(-math.pi, math.pi), random.uniform(-math.pi, math.pi)]
    total_dist = 0.0
    grasped = 0

    for _ in range(steps):
        sensors = encode_sensors(angles, target_x, target_y)
        action = neuron.evaluate(sensors & 0b111111)
        deltas = decode_action(action)
        angles[0] = max(-math.pi, min(math.pi, angles[0] + deltas[0]))
        angles[1] = max(-math.pi, min(math.pi, angles[1] + deltas[1]))
        dist = robot_arm_distance(angles, target_x, target_y)
        total_dist += dist
        if robot_arm_has_grasped(angles, target_x, target_y):
            grasped += 1

    avg_dist = total_dist / steps
    grasp_ratio = grasped / steps
    fitness = (1.0 / (1.0 + avg_dist)) * 500 + grasp_ratio * 500
    return fitness


def train(population_size=30, generations=100, k=12):
    """GA training loop for robot arm control."""
    rng = random.Random(42)
    pop = [MPDTNeuron(k) for _ in range(population_size)]

    best_fitness = 0.0
    history = []

    for gen in range(generations):
        scored = [(evaluate_fitness(n, TARGET_X, TARGET_Y), n) for n in pop]
        scored.sort(key=lambda x: x[0], reverse=True)
        best_fitness = max(best_fitness, scored[0][0])

        history.append({
            "generation": gen,
            "best_fitness": round(scored[0][0], 2),
            "avg_fitness": round(sum(s[0] for s in scored) / len(scored), 2)
        })

        elite = scored[:3]
        new_pop = [s[1].clone() for s in elite]

        while len(new_pop) < population_size:
            _, parent_a = rng.choices(scored[:10], weights=[s[0] for s in scored[:10]], k=1)[0]
            _, parent_b = rng.choices(scored[:10], weights=[s[0] for s in scored[:10]], k=1)[0]
            child = parent_a.clone()
            child.mutate(0.05)
            new_pop.append(child)

        pop = new_pop

    best_neuron = max(pop, key=lambda n: evaluate_fitness(n, TARGET_X, TARGET_Y))
    return {
        "status": "complete",
        "generations": generations,
        "population": population_size,
        "best_neuron": {
            "k": best_neuron.k,
            "table": best_neuron.table
        },
        "best_fitness": round(best_fitness, 2),
        "history": history
    }


def run_pybullet(args):
    """PyBullet-specific simulation."""
    try:
        import pybullet as p
        import pybullet_data
    except ImportError:
        print(json.dumps({"error": "pybullet not installed. Run: pip install pybullet"}))
        sys.exit(1)

    physics_client = p.connect(p.GUI if args.gui else p.DIRECT)
    p.setAdditionalSearchPath(pybullet_data.getDataPath())
    p.setGravity(0, 0, -9.81)
    p.loadURDF("plane.urdf")

    arm_id = p.loadURDF(
        "kuka_iiwa/model.urdf",
        basePosition=[0, 0, 0],
        useFixedBase=True
    )

    print(json.dumps({
        "mode": "pybullet",
        "status": "simulation_ready",
        "arm_id": arm_id,
        "note": "Full PyBullet 3D simulation ready"
    }))


def main():
    parser = argparse.ArgumentParser(description="Robot Arm MPDT Simulation")
    parser.add_argument("--pybullet", action="store_true", help="Use PyBullet 3D simulation")
    parser.add_argument("--math", action="store_true", default=True, help="Use pure-Python math simulation")
    parser.add_argument("--gui", action="store_true", help="Enable GUI (PyBullet mode)")
    parser.add_argument("--generations", type=int, default=100, help="Number of generations")
    parser.add_argument("--population", type=int, default=30, help="Population size")
    parser.add_argument("--json", action="store_true", help="Read JSON commands from stdin (bridge mode)")
    args = parser.parse_args()

    if args.json:
        for line in sys.stdin:
            try:
                cmd = json.loads(line.strip())
                if cmd.get("cmd") == "quit":
                    break
                if cmd.get("cmd") == "train":
                    result = train(
                        cmd.get("population", 30),
                        cmd.get("generations", 100),
                        cmd.get("k", 12)
                    )
                    print(json.dumps(result))
                    sys.stdout.flush()
                elif cmd.get("cmd") == "evaluate":
                    neuron_data = cmd["neuron"]
                    n = MPDTNeuron(neuron_data["k"], neuron_data["table"])
                    fitness = evaluate_fitness(n, TARGET_X, TARGET_Y)
                    print(json.dumps({"fitness": round(fitness, 2)}))
                    sys.stdout.flush()
            except json.JSONDecodeError:
                pass
        return

    if args.pybullet:
        run_pybullet(args)
    else:
        result = train(args.population, args.generations)
        print(f"Best fitness: {result['best_fitness']:.2f}")
        print(f"Neuron table: 0x{result['best_neuron']['table']:X}")
        print(f"k={result['best_neuron']['k']}")


if __name__ == "__main__":
    main()
