#!/usr/bin/env python3
"""
matrix-ros2-bridge — MPDT Neuron ↔ ROS2 Bridge

Bridges MPDT neuron inference with ROS2 topics. Two modes:
  --ros        Real ROS2 mode (requires: pip install rclpy, ROS2 Humble+)
  --sim        Simulation mode with mock topics (no ROS2 needed)

Architecture:
  ROS2 Sensor Topic → Bridge → MPDT Neuron (Java via JSON subprocess) → ROS2 Actuator Topic

Usage:
  python3 ros2_bridge.py --sim
  python3 ros2_bridge.py --ros --neuron-config neuron.json

Ref: L16_PhysicalInterfaces.md §2, docs/MASTER_PLAN.md
"""

import argparse
import json
import math
import os
import subprocess
import sys
import time
import threading
from typing import Any, Dict, List, Optional

ROS_AVAILABLE = False
try:
    import rclpy
    from rclpy.node import Node
    from std_msgs.msg import String, Float64, Float64MultiArray
    ROS_AVAILABLE = True
except ImportError:
    pass


class MPDTNeuron:
    def __init__(self, k: int, table: int = 0):
        self.k = k
        self.table = table

    def evaluate(self, inputs: int) -> bool:
        mask = (1 << self.k) - 1
        idx = inputs & mask
        return (self.table >> idx) & 1 != 0

    @staticmethod
    def from_dict(d: dict) -> "MPDTNeuron":
        return MPDTNeuron(d.get("k", 2), d.get("table", 0))


class SensorSimulator:
    def __init__(self):
        self._t = 0.0

    def read_joint_states(self) -> List[float]:
        self._t += 0.1
        return [
            math.sin(self._t) * 0.5,
            math.cos(self._t * 1.3) * 0.3,
        ]

    def read_distance(self) -> float:
        self._t += 0.1
        return 1.0 - abs(math.sin(self._t * 0.7)) * 0.8


class MockROSBridge:
    def __init__(self, neurons: List[MPDTNeuron], sensor_config: dict):
        self.neurons = neurons
        self.sensors = SensorSimulator()
        self.running = False
        self.thread = None

    def start(self):
        self.running = True
        self.thread = threading.Thread(target=self._loop, daemon=True)
        self.thread.start()

    def stop(self):
        self.running = False
        if self.thread:
            self.thread.join(timeout=2.0)

    def _loop(self):
        print("[bridge] Mock ROS2 bridge started")
        tick = 0
        while self.running:
            tick += 1
            joints = self.sensors.read_joint_states()
            distance = self.sensors.read_distance()

            encoded = self._encode_sensors(joints, distance)

            for i, neuron in enumerate(self.neurons):
                result = neuron.evaluate(encoded)
                if tick % 10 == 0:
                    print(f"  tick={tick} neuron[{i}] inputs=0x{encoded:04X} → {result}")

            time.sleep(0.1)

    def _encode_sensors(self, joints: List[float], distance: float) -> int:
        bits = 0
        for i, val in enumerate(joints):
            q = min(15, int(max(0, (val + 1.0) / 2.0) * 16))
            bits |= (q << (i * 4))
        dist_q = min(15, int(distance * 16))
        bits |= (dist_q << 8)
        return bits


class RealROSBridge:
    def __init__(self, neurons: List[MPDTNeuron], node_name: str = "matrix_bridge"):
        if not ROS_AVAILABLE:
            raise RuntimeError("rclpy not installed. Run: pip install rclpy")
        self.neurons = neurons
        self.node_name = node_name
        self.node: Optional[Node] = None
        self.running = False

    def start(self):
        rclpy.init()
        self.node = Node(self.node_name)

        self.sub = self.node.create_subscription(
            Float64MultiArray, "/matrix/sensors", self._sensor_callback, 10)
        self.pub = self.node.create_publisher(
            Float64MultiArray, "/matrix/actuators", 10)
        self.log_pub = self.node.create_publisher(
            String, "/matrix/log", 10)

        self.node.get_logger().info(f"MATRIX ROS2 Bridge started with {len(self.neurons)} neurons")
        self.running = True

        import threading
        self.spin_thread = threading.Thread(target=self._spin, daemon=True)
        self.spin_thread.start()

    def _spin(self):
        while self.running and rclpy.ok():
            rclpy.spin_once(self.node, timeout_sec=0.1)

    def _sensor_callback(self, msg: Any):
        if not self.neurons:
            return

        sensor_bits = 0
        for i, val in enumerate(msg.data[:6]):
            q = min(15, int(max(0, (val + 1.0) / 2.0) * 16))
            sensor_bits |= (q << (i * 4))

        actuator_bits = 0
        for i, neuron in enumerate(self.neurons[:4]):
            result = neuron.evaluate(sensor_bits)
            actuator_bits |= (1 << i) if result else 0

        out_msg = Float64MultiArray()
        out_msg.data = [float((actuator_bits >> i) & 1) for i in range(4)]
        self.pub.publish(out_msg)

        self.node.get_logger().debug(
            f"sensors=0x{sensor_bits:06X} → actuators=0x{actuator_bits:01X}")

    def stop(self):
        self.running = False
        if self.node:
            self.node.destroy_node()
        if rclpy.ok():
            rclpy.shutdown()


def main():
    parser = argparse.ArgumentParser(description="matrix-ros2-bridge")
    parser.add_argument("--ros", action="store_true", help="Real ROS2 mode")
    parser.add_argument("--sim", action="store_true", default=True, help="Simulation mode")
    parser.add_argument("--neuron-config", type=str, help="Path to neuron JSON config")
    parser.add_argument("--node-name", type=str, default="matrix_bridge", help="ROS2 node name")
    parser.add_argument("--duration", type=int, default=30, help="Sim duration in seconds")
    args = parser.parse_args()

    neurons = []
    if args.neuron_config and os.path.exists(args.neuron_config):
        with open(args.neuron_config) as f:
            config = json.load(f)
            for n in config.get("neurons", []):
                neurons.append(MPDTNeuron.from_dict(n))
    else:
        neurons = [
            MPDTNeuron(k=8, table=0x6A9C),   # XOR-like pattern
            MPDTNeuron(k=8, table=0xFFFF0000),
        ]
        print(f"Using default neurons ({len(neurons)})")

    if args.ros:
        bridge = RealROSBridge(neurons, args.node_name)
    else:
        bridge = MockROSBridge(neurons, {})

    bridge.start()
    try:
        time.sleep(args.duration)
    except KeyboardInterrupt:
        pass
    finally:
        bridge.stop()
        print("[bridge] Stopped")


if __name__ == "__main__":
    main()
