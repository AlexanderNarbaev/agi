# matrix-fpga/synth_xilinx.tcl
# Xilinx Vivado synthesis script for MPDT neurons
#
# Usage:
#   vivado -mode batch -source synth_xilinx.tcl
#
# Prerequisites:
#   - Generated Verilog files in ./src/
#   - Constraints in ./constraints/
#   - Vivado 2024.1+ installed

# Project settings
set project_name "mpdt_neuron"
set part "xc7a35tcpg236-1"  ;# Artix-7 on Basys 3
set output_dir "./vivado_project"
set src_dir "./src"
set tb_dir "./testbench"

# Create project
create_project $project_name $output_dir -part $part -force

# Add source files
if {[glob -nocomplain "$src_dir/*.v"] ne ""} {
    add_files -norecurse [glob "$src_dir/*.v"]
    puts "Added source files from $src_dir"
} else {
    puts "ERROR: No Verilog files found in $src_dir"
    exit 1
}

# Add testbench files
if {[glob -nocomplain "$tb_dir/*.v"] ne ""} {
    add_files -fileset sim_1 -norecurse [glob "$tb_dir/*.v"]
    puts "Added testbench files from $tb_dir"
}

# Add constraints
if {[glob -nocomplain "./constraints/*.xdc"] ne ""} {
    add_files -fileset constrs_1 -norecurse [glob "./constraints/*.xdc"]
    puts "Added constraint files"
}

# Set top module
set_property top matrix_neurons_top [current_fileset]

# Synthesis
puts "Starting synthesis..."
launch_runs synth_1 -jobs 4
wait_on_run synth_1

if {[get_property STATUS [get_runs synth_1]] eq "synth_design Complete!"} {
    puts "Synthesis completed successfully"
} else {
    puts "ERROR: Synthesis failed"
    exit 1
}

# Implementation
puts "Starting implementation..."
launch_runs impl_1 -jobs 4
wait_on_run impl_1

if {[get_property STATUS [get_runs impl_1]] eq "route_design Complete!"} {
    puts "Implementation completed successfully"
} else {
    puts "ERROR: Implementation failed"
    exit 1
}

# Generate bitstream
puts "Generating bitstream..."
launch_runs impl_1 -to_step write_bitstream
wait_on_run impl_1

# Copy bitstream
set bitstream [glob -nocomplain "$output_dir/$project_name.runs/impl_1/*.bit"]
if {$bitstream ne ""} {
    file copy $bitstream "./output/${project_name}.bit"
    puts "Bitstream: ./output/${project_name}.bit"
}

# Report utilization
open_run impl_1
report_utilization -file "./output/utilization.rpt"
report_timing_summary -file "./output/timing.rpt"

puts "=== Synthesis Complete ==="
puts "Bitstream: ./output/${project_name}.bit"
puts "Utilization: ./output/utilization.rpt"
puts "Timing: ./output/timing.rpt"
