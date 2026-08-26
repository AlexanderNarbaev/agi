# L16 — Physical Interfaces

**Status:** normative · **Layer:** 16 (hardware) · **Date:** 2026-08-26
**Changelog:** 2026-08-26 — brain wave v4 levels; densified from archive copy
archive/2026-08-pre-v2/docs-root-flat/L16_PhysicalInterfaces.md.

## 1. Purpose
L16 defines how Matrix runs on physical devices: microcontrollers,
single-board computers, FPGA fabric, neuromorphic chips, and
hardware accelerators. The discrete MPDT core maps cleanly to
LUT-based logic, keeping inference fast, deterministic, and
energy-efficient at the edge.

## 2. Integration Tiers
| Tier | Device | Memory | Power | Example |
|---|---|---|---|---|
| L0 | MCU (ESP32, Arduino) | kB | µW | Smart switch, sensor |
| L1 | SBC (Raspberry Pi) | GB | W | Local instance, robot |
| L2 | FPGA | hundreds of kB LUT | mW per op | High-throughput inference |
| L3 | Neuromorphic (Loihi, TrueNorth) | MB SRAM | pJ per spike | Ultra-efficient learning |

## 3. Microcontrollers — `matrix-micro`
A C/C++ library implementing an MPDT neuron. The truth table
sits in flash as `const uint8_t[]` or `const uint32_t[]`;
evaluation is bitwise and runs in tens of nanoseconds on ESP32
cores. Training does not occur on device; a neuron is trained
on a server, compressed, and flashed. The k=16 footprint is
about 8 kB; the k=20 ceiling is about 128 kB and needs
sufficient flash. Application scenarios: smart-home preferences
without an internet uplink, wearable activity classifiers,
industrial vibration-based failure predictors. Updates arrive
over OTA when adaptation is needed.

## 4. Robots and ROS 2
`matrix-ros2-bridge` links the MPDT core to ROS 2. Sensors
publish to topics (LaserScan, Image) and the sensor proxy
converts them to binary vectors for the core; binary outputs
map to control commands (Twist, JointTrajectory). Training runs
in simulation (Gazebo, PyBullet); the best neurons deploy to
the physical robot. Scenarios: mobile navigation with obstacle
avoidance and docking, manipulator grasp learning (Pilot P4),
and swarm robotics where each unit runs a local instance and
swaps FNL through Noosphere. Determinism and verifiability are
the headline advantages: navigation logic can be model-checked
against the four prohibitions before deployment, and inference
on FPGA or MCU extends battery life.

## 5. FPGA — Direct Logic Mapping
An MPDT neuron is a truth table; an FPGA is an array of LUTs.
A single neuron maps to one or several LUTs, with inference
latency in fractions of a nanosecond and energy in femtojoules
per operation, close to the thermodynamic limit. The
`matrix-fpga-compiler` tool ingests an `.ldn` snapshot and emits
Verilog or VHDL; each neuron becomes a LUT-initialised module;
cluster topology becomes wiring; the result synthesises through
Vivado or Quartus. Low-cost boards (TinyFPGA BX, Lattice iCE40)
fit several thousand LUTs, enough for hundreds of neurons.
Mutation can run on CPU while candidate evaluation runs on
FPGA; a population of 100 evaluates in microseconds.

## 6. Neuromorphic Hardware
Existing platforms: Intel Loihi 2 (programmable neurons, spike-
based communication, on-chip learning), IBM TrueNorth (one
million neurons, very low power), SpiNNaker (large-scale spike
simulation). MPDT neurons are not spike-based, but their
discrete nature maps: truth tables encode into synaptic weights
and thresholds; batched signal transfer (L2) maps to Address
Event Representation. Mapping MPDT onto neuromorphic chips is
a Phase 5 research item; open questions cover encoding
efficiency, Loihi SNN compatibility, and on-chip plasticity.

## 7. Accelerators and Hardware Security
GPUs: not matrix-multiplication-friendly, but parallelism still
helps batched inference; truth tables sit in texture memory and
input vectors run across thousands of threads. ASIC: a bespoke
chip that implements MPDT logic directly would maximise energy
efficiency but needs significant capital; long-horizon option,
not a current commitment. Security: (i) Trusted Execution
Environment — on TrustZone or SGX devices, FROZEN neurons and
the ethical filter execute in a protected region that even the
device owner cannot modify; (ii) Secure Boot — firmware is
signed and the device refuses unsigned code, blocking
substitution of a trained neuron with hostile logic;
(iii) Tamper resistance — for safety-critical deployments, FPGA
boards may be housed in tamper-evident or tamper-resistant
enclosures. Roadmap: phases 0–1 simulation only (Gridworld,
PyBullet); phase 2 `matrix-micro` for ESP32, ROS 2 bridge in
beta; phase 3 FPGA prototype, robot with on-device learning;
phase 4 and beyond bespoke ASIC, neuromorphic-chip integration.

## 8. Worked Example
ESP32 XOR in five minutes: clone `matrix-micro`, enter the
`examples/xor_esp32` directory, build with `idf.py build`,
flash with `idf.py flash`. The example initialises a 2-input
neuron with the XOR truth table and logs the four input
combinations; expected output is 0, 1, 1, 0 for inputs 00, 01,
10, 11 respectively.

Next: L17 closes the spec stack with operational runbooks,
incident response, and metrics discipline.
