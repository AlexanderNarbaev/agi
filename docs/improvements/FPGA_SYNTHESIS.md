# FPGA Synthesis — Implementation Plan

**Status:** 🔧 PROTOTYPE (Python scripts only)
**Priority:** MEDIUM
**Estimated effort:** 4-6 weeks
**Target:** v3.60

---

## Problem Statement

Current FPGA implementation is Python-only (ldn2v.py converter). Need:
1. Real Verilog/VHDL generation from TruthTable
2. Synthesis for Xilinx/Intel FPGAs
3. Hardware verification with testbenches
4. Integration with matrix-core for hardware acceleration

---

## Implementation Steps

### Step 1: Verilog Generator Enhancement (Week 1-2)
```python
# matrix-fpga/ldn2v.py — enhanced
class TruthTableToVerilog:
    def __init__(self, truth_table: dict, k: int):
        self.truth_table = truth_table
        self.k = k  # max 20 inputs
    
    def generate_module(self) -> str:
        """Generate Verilog module from TruthTable"""
        inputs = ', '.join([f'input_{i}' for i in range(self.k)])
        return f"""
module mpdt_neuron (
    input wire [{self.k-1}:0] inputs,
    output wire result,
    input wire clk,
    input wire rst
);
    wire [{2**self.k - 1}:0] lookup;
    assign lookup = {self._generate_lookup()};
    assign result = lookup[inputs];
endmodule
"""
    
    def _generate_lookup(self) -> str:
        """Generate lookup table from TruthTable"""
        entries = []
        for i in range(2**self.k):
            entries.append(f"1'b{self.truth_table.get(i, 0)}")
        return '{' + ', '.join(reversed(entries)) + '}'
```

### Step 2: Testbench Generator (Week 2)
```python
# matrix-fpga/testbench_generator.py
class TestbenchGenerator:
    def generate(self, module_name: str, k: int) -> str:
        return f"""
`timescale 1ns/1ps

module {module_name}_tb;
    reg [{k-1}:0] inputs;
    wire result;
    
    {module_name} uut (
        .inputs(inputs),
        .result(result)
    );
    
    initial begin
        $dumpfile("{module_name}_tb.vcd");
        $dumpvars(0, {module_name}_tb);
        
        // Test all combinations
        for (int i = 0; i < {2**k}; i++) begin
            inputs = i;
            #10;
            $display("Input: %b, Output: %b", inputs, result);
        end
        
        $finish;
    end
endmodule
"""
```

### Step 3: Synthesis Scripts (Week 3)
```bash
# matrix-fpga/synth_xilinx.tcl
# Xilinx Vivado synthesis script

create_project mpdt_neuron ./vivado_project -part xc7a35tcpg236-1
add_files {./src/mpdt_neuron.v}
add_files -fileset sim_1 {./tb/mpdt_neuron_tb.v}
launch_runs synth_1
wait_on_run synth_1
launch_runs impl_1
wait_on_run impl_1
write_bitstream ./output/mpdt_neuron.bit
```

### Step 4: Hardware Acceleration Interface (Week 4)
```java
// matrix-core/src/main/java/io/matrix/fpga/FpgaAccelerator.java
@ApplicationScoped
public class FpgaAccelerator {
    
    @ConfigProperty(name = "matrix.fpga.enabled", defaultValue = "false")
    boolean enabled;
    
    @ConfigProperty(name = "matrix.fpga.device", defaultValue = "/dev/ttyUSB0")
    String device;
    
    public int evaluate(byte[] inputs) {
        if (!enabled) {
            throw new IllegalStateException("FPGA not enabled");
        }
        // Serial communication with FPGA
        return serialEvaluate(inputs);
    }
    
    private native int serialEvaluate(byte[] inputs);
}
```

### Step 5: FPGA-Java Bridge (Week 5)
```java
// matrix-core/src/main/java/io/matrix/fpga/FpgaBridge.java
public class FpgaBridge {
    static {
        System.loadLibrary("fpga_bridge");
    }
    
    public native void connect(String device);
    public native int[] evaluateBatch(int[][] inputs);
    public native void disconnect();
}
```

### Step 6: Integration Tests (Week 6)
```java
@QuarkusTest
class FpgaAcceleratorTest {
    
    @Inject
    FpgaAccelerator accelerator;
    
    @Test
    void testSingleEvaluation() {
        byte[] inputs = {1, 0, 1, 0};
        int result = accelerator.evaluate(inputs);
        assertTrue(result == 0 || result == 1);
    }
}
```

---

## Hardware Targets

| FPGA | Board | LUTs | FFs | Purpose |
|------|-------|------|-----|---------|
| Xilinx Artix-7 | Basys 3 | 20K | 40K | Development |
| Intel Cyclone V | DE10-Lite | 25K | 50K | Alternative |
| Xilinx Kintex-7 | KC705 | 100K | 200K | Production |

---

## Verification

```bash
# Generate Verilog
python3 matrix-fpga/ldn2v.py --input models/pretrained/qwen3-1.7b/layer_0/neuron_0.json

# Simulate
iverilog -o sim matrix-fpga/src/mpdt_neuron.v matrix-fpga/tb/mpdt_neuron_tb.v
./sim

# Synthesize (Xilinx)
vivado -mode batch -source matrix-fpga/synth_xilinx.tcl
```
