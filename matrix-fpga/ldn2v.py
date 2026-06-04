#!/usr/bin/env python3
"""
ldn2v — .ldn snapshot to Verilog FPGA compiler for matrix-fpga.

Converts MPDT neuron truth tables from .ldn JSON snapshots into
synthesizable Verilog modules. The FPGA synthesis tool (Yosys/nextpnr)
automatically maps the case statement to optimal LUT primitives.

Usage:
    python3 ldn2v.py snapshot.ldn [output_dir]
    python3 ldn2v.py --direct --k=4 --table=0x6A9C --name=xor4

Architecture:
    - k ≤ 10: combinational case statement (single-cycle)
    - k > 10: pipelined with registered output for timing closure

Output:
    <neuron_name>.v for each neuron
    matrix_neurons_top.v — top-level wrapper

Ref: L16_PhysicalInterfaces.md §4
"""

import json
import os
import sys
import argparse


def bitset_from_long_array(long_array, bit_count):
    bits = [False] * bit_count
    for word_idx, word in enumerate(long_array):
        base = word_idx * 64
        for bit in range(64):
            idx = base + bit
            if idx >= bit_count:
                break
            bits[idx] = (word >> bit) & 1 != 0
    return bits


def sanitize_verilog_identifier(name):
    result = []
    for c in name:
        if c.isalnum() or c == '_':
            result.append(c)
        else:
            result.append('_')
    return ''.join(result) or 'neuron'


def emit_combinational(name, k, bits):
    """Emit combinational Verilog using case statement (optimal for k≤10)."""
    safe_name = sanitize_verilog_identifier(name)
    module_name = f"mpdt_{safe_name}"
    num_entries = 1 << k
    table_bits = bits[:num_entries]

    verilog = f"""// MPDT Neuron: {name}
// k = {k}, entries = {num_entries}, combinational

module {module_name} (
    input  wire [{k-1}:0] inputs,
    output reg         output_
);

    always @(*) begin
        case (inputs)
"""
    for i, bit in enumerate(table_bits):
        val = 1 if bit else 0
        verilog += f"            {k}'d{i}: output_ = 1'b{val};\n"

    verilog += f"""            default: output_ = 1'b0;
        endcase
    end

endmodule
"""
    return verilog


def emit_registered(name, k, bits):
    """Emit registered-output Verilog with pipeline register for timing closure."""
    safe_name = sanitize_verilog_identifier(name)
    module_name = f"mpdt_{safe_name}"
    num_entries = 1 << k
    table_bits = bits[:num_entries]

    verilog = f"""// MPDT Neuron: {name}
// k = {k}, entries = {num_entries}, registered output

module {module_name} (
    input  wire            clk,
    input  wire            rst_n,
    input  wire [{k-1}:0] inputs,
    output reg             output_
);

    reg        lut_out;

    always @(*) begin
        case (inputs)
"""
    for i, bit in enumerate(table_bits):
        val = 1 if bit else 0
        verilog += f"            {k}'d{i}: lut_out = 1'b{val};\n"

    verilog += f"""            default: lut_out = 1'b0;
        endcase
    end

    always @(posedge clk or negedge rst_n) begin
        if (!rst_n)
            output_ <= 1'b0;
        else
            output_ <= lut_out;
    end

endmodule
"""
    return verilog


def generate_verilog(record):
    name = record.get("neuronId", "unnamed")
    k = record.get("k", 2)
    truth_bits = record.get("truthTableBits", [0])

    bit_count = 1 << k
    bits = bitset_from_long_array(truth_bits, bit_count)

    if k <= 10:
        return emit_combinational(name, k, bits)
    else:
        return emit_registered(name, k, bits)


def main():
    parser = argparse.ArgumentParser(description="ldn2v — .ldn to Verilog FPGA compiler")
    parser.add_argument("input", nargs="?", help=".ldn snapshot file path")
    parser.add_argument("output_dir", nargs="?", default=".", help="Output directory")
    parser.add_argument("--direct", action="store_true",
                        help="Generate single neuron directly")
    parser.add_argument("--k", type=int, default=4, help="Neuron input count")
    parser.add_argument("--table", type=str, default="0x6", help="Truth table hex")
    parser.add_argument("--name", type=str, default="custom", help="Neuron name")
    args = parser.parse_args()

    os.makedirs(args.output_dir, exist_ok=True)

    if args.direct:
        table_val = int(args.table, 16)
        record = {
            "neuronId": args.name,
            "k": args.k,
            "truthTableBits": [table_val & 0xFFFFFFFFFFFFFFFF,
                               (table_val >> 64) & 0xFFFFFFFFFFFFFFFF],
            "state": "STABLE"
        }
        verilog = generate_verilog(record)
        safe_name = sanitize_verilog_identifier(args.name)
        out_path = os.path.join(args.output_dir, f"mpdt_{safe_name}.v")
        with open(out_path, "w") as f:
            f.write(verilog)
        print(f"Generated: {out_path}")
        return

    if not args.input:
        parser.print_help()
        sys.exit(1)

    with open(args.input, "r") as f:
        snapshot = json.load(f)

    neurons = snapshot.get("neurons", [])
    if not neurons:
        print("No neurons found")
        sys.exit(1)

    top_modules = []
    for record in neurons:
        verilog = generate_verilog(record)
        safe_name = sanitize_verilog_identifier(record["neuronId"])
        out_path = os.path.join(args.output_dir, f"mpdt_{safe_name}.v")
        with open(out_path, "w") as f:
            f.write(verilog)
        print(f"  Generated: {out_path}")
        top_modules.append((f"mpdt_{safe_name}", record.get("k", 2)))

    lines = []
    for i, (mod, k) in enumerate(top_modules):
        lines.append(f"    input  wire [{k-1}:0] inputs_{i},")
        lines.append(f"    output wire        output_{i}"
                     + ("," if i < len(top_modules) - 1 else ""))

    top_verilog = "// matrix_neurons_top — auto-generated by ldn2v.py\n\n"
    top_verilog += "module matrix_neurons_top (\n"
    top_verilog += "\n".join(lines)
    top_verilog += "\n);\n\n"

    for i, (mod, k) in enumerate(top_modules):
        top_verilog += f"""    {mod} neuron_{i} (
        .inputs(inputs_{i}),
        .output_(output_{i})
    );

"""

    top_verilog += "endmodule\n"

    top_path = os.path.join(args.output_dir, "matrix_neurons_top.v")
    with open(top_path, "w") as f:
        f.write(top_verilog)
    print(f"  Generated: {top_path}")
    print(f"\nTotal: {len(neurons)} neurons compiled to Verilog")


if __name__ == "__main__":
    main()
