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

## Hardware Synthesis (iCE40)

### Prerequisites

```bash
# OSS CAD Suite or individual tools:
# yosys, nextpnr-ice40, icepack, iceprog, iverilog

# Ubuntu/Debian:
sudo apt install yosys nextpnr-ice40 fpga-icestorm iverilog

# macOS (Homebrew):
brew install yosys nextpnr-ice40 icestorm iverilog
```

### Flow

```bash
cd matrix-fpga

# Generate Verilog directly (single neuron)
make direct NEURON=xor4 TABLE=0x6A9C K=4

# Synthesize with Yosys → JSON
make synth TOP=xor4

# Place & route → ASC
make pnr TOP=xor4

# Generate bitstream → BIN
make bit TOP=xor4

# Flash to FPGA
make prog TOP=xor4

# Run Verilog simulation (requires testbench/<name>_tb.v)
make sim TOP=xor4

# Clean artifacts
make clean
```

### Pin Constraints

Edit `constraints/ice40-hx1k.pcf` to match your board layout. Default pins target TinyFPGA BX / Lattice iCEstick.

### Testbench

Copy `testbench/tb_template.v` to `testbench/<neuron>_tb.v` and adapt:
- Module instantiation name (replace `mpdt_custom`)
- Port connections
- Test vectors

Then run: `make sim TOP=<neuron>`

## FPGA Target

Tested with:
- iCE40 HX1K/HX8K (TinyFPGA BX, iCEBreaker) via Yosys + nextpnr
- ECP5 (ULX3S, OrangeCrab) via Yosys + nextpnr
