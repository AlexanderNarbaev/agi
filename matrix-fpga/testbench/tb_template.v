`timescale 1ns / 1ps

// Testbench template for MPDT neuron modules
// Copy this file, rename to <neuron>_tb.v, and adapt port names
// Usage: make sim TOP=<neuron>

module tb_top;
    reg clock;
    reg [3:0] sensor;
    wire led;
    wire [3:0] debug_leds;

    // Instantiate the neuron module under test
    // Replace 'mpdt_xor_example' with your generated module name
    // Adjust port connections to match your module's interface
    mpdt_custom uut (
        .clk(clock),
        .rst_n(1'b1),
        .inputs({sensor[1:0]}),
        .output_(led)
    );

    // Debug: map output to LEDs for waveform viewing
    assign debug_leds = {3'b0, led};

    // Clock generation (100 MHz → 10 ns period)
    always #5 clock = ~clock;

    initial begin
        $dumpfile("tb_top.vcd");
        $dumpvars(0, tb_top);

        clock = 0;
        sensor = 4'b0000;
        #10;

        // Test all input combinations
        $display("=== MPDT Neuron Testbench ===");
        $display("Time\tSensor\tLED");
        $display("------------------------");
        repeat (16) begin
            #10;
            $display("%0t\t%b\t%b", $time, sensor[1:0], led);
            sensor = sensor + 1;
        end

        #20;
        $display("=== Test Complete ===");
        $finish;
    end
endmodule
