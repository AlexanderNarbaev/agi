# MATRIX FPGA Synthesis Flow
# Usage:
#   make synth TOP=xor_example     — synthesize to Verilog JSON
#   make pnr  TOP=xor_example     — place and route
#   make bit  TOP=xor_example     — generate bitstream
#   make prog TOP=xor_example     — program FPGA
#   make sim  TOP=xor_example     — run Verilog simulation (iverilog)
#   make clean

LDN2V = python3 ldn2v.py
TOP ?= xor_example
BOARD ?= ice40
BUILD_DIR = build

all: synth

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

# Generate Verilog from LDN snapshot via ldn2v.py
# ldn2v.py takes .ldn path + output_dir → generates .v files into BUILD_DIR
$(BUILD_DIR)/.ldn2v-stamp: %.ldn $(LDN2V) | $(BUILD_DIR)
	$(LDN2V) $< -o $(BUILD_DIR)
	touch $@

# Convenience: generate Verilog for a named neuron using --direct mode
# Usage: make direct NEURON=xor4 TABLE=0x6A9C K=4
NEURON ?= $(TOP)
TABLE ?= 0x6A9C
K ?= 4
direct: | $(BUILD_DIR)
	$(LDN2V) --direct --k=$(K) --table=$(TABLE) --name=$(NEURON) -o $(BUILD_DIR)

# Synthesize with Yosys (iCE40 target)
synth: $(BUILD_DIR)/mpdt_$(TOP).v
	yosys -p "read_verilog $(BUILD_DIR)/mpdt_$(TOP).v; synth_ice40 -top mpdt_$(TOP) -json $(BUILD_DIR)/$(TOP).json"

# Place and route with nextpnr
pnr: $(BUILD_DIR)/$(TOP).json
	nextpnr-ice40 --hx1k --package tq144 --json $(BUILD_DIR)/$(TOP).json --pcf constraints/ice40-hx1k.pcf --asc $(BUILD_DIR)/$(TOP).asc

# Generate bitstream
bit: $(BUILD_DIR)/$(TOP).asc
	icepack $(BUILD_DIR)/$(TOP).asc $(BUILD_DIR)/$(TOP).bin

# Program FPGA
prog: $(BUILD_DIR)/$(TOP).bin
	iceprog $(BUILD_DIR)/$(TOP).bin

# Simulation with iverilog
sim: testbench/$(TOP)_tb.v $(BUILD_DIR)/mpdt_$(TOP).v
	iverilog -o $(BUILD_DIR)/$(TOP)_tb.vvp testbench/$(TOP)_tb.v $(BUILD_DIR)/mpdt_$(TOP).v
	vvp $(BUILD_DIR)/$(TOP)_tb.vvp

# Clean
clean:
	rm -rf $(BUILD_DIR)/*.json $(BUILD_DIR)/*.asc $(BUILD_DIR)/*.bin $(BUILD_DIR)/*.vvp $(BUILD_DIR)/*.vcd $(BUILD_DIR)/.ldn2v-stamp

.PHONY: all synth pnr bit prog sim clean direct
