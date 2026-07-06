#!/usr/bin/env python3
"""Test MPDT neuron on FPGA (Amaranth simulation).

Simulates a k=4 MPDT neuron with XOR-like truth table (0x6A9C)
using the Amaranth hardware description framework.

The test enumerates all 2^k = 16 input combinations and verifies
the LUT output matches the expected truth table value.

Usage:
    python3 matrix-fpga/test_mpdt_fpga.py

Requirements:
    pip install amaranth

Dependencies:
    - amaranth: Amaranth HDL framework (https://github.com/amaranth-lang/amaranth)
"""

import sys

try:
    from amaranth import *
    from amaranth.sim import *
    AMARANTH_AVAILABLE = True
except ImportError:
    AMARANTH_AVAILABLE = False


def run_simulation():
    """Runs the full MPDT FPGA simulation test."""
    k = 4
    truth_table = 0x6A9C  # XOR-like truth table for 4-input MPDT neuron

    m = Module()
    sensor = Signal(k)
    output = Signal()

    with m.Switch(sensor):
        for i in range(1 << k):
            with m.Case(i):
                m.d.comb += output.eq((truth_table >> i) & 1)

    sim = Simulator(m)

    def test_all():
        for i in range(1 << k):
            yield sensor.eq(i)
            yield Settle()
            expected = (truth_table >> i) & 1
            actual = (yield output)
            assert actual == expected, (
                f"Mismatch at input {i:04b} (decimal {i}): "
                f"expected {expected}, got {actual}"
            )
        print(f"ALL {1 << k} TEST CASES PASSED")

    sim.add_process(test_all)
    sim.run()
    print("MPDT FPGA simulation: SUCCESS")


def main():
    if not AMARANTH_AVAILABLE:
        print("SKIP: Amaranth not installed.")
        print("      Install with: pip install amaranth")
        print("      Then run: python3 matrix-fpga/test_mpdt_fpga.py")
        sys.exit(0)

    try:
        run_simulation()
    except Exception as e:
        print(f"FAIL: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
