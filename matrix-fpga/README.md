# matrix-fpga — MPDT Neuron → FPGA Compiler

Compiles MPDT neuron truth tables (`.ldn` snapshots) into synthesizable Verilog modules.

## Usage

```bash
# From .ldn snapshot
python3 ldn2v.py snapshot.ldn output_dir/

# Single neuron directly
python3 ldn2v.py --direct --k=4 --table=0x6A9C --name=my_xor4

# Verify with Yosys
yosys -p "read_verilog output_dir/*.v; synth_ice40; stat"
```

## Architecture

- **k ≤ 10:** Combinational case statement (single-cycle, Yosys maps to LUTs automatically)
- **k > 10:** Registered output for timing closure

## FPGA Target

Tested with:
- iCE40 HX1K/HX8K (TinyFPGA BX, iCEBreaker) via Yosys + nextpnr
- ECP5 (ULX3S, OrangeCrab) via Yosys + nextpnr
