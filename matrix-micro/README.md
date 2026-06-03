# matrix-micro — MPDT Neuron Library for Microcontrollers

Lightweight C implementation of MPDT neurons for ESP32, Arduino, 
STM32, and other resource-constrained devices.

## Quick Start

```c
#include "mpdt_neuron.h"

// XOR neuron: k=2, truth table = 0b0110 = 0x6
static const uint32_t xor_table[] = {0x6};
MPDTNeuron neuron = {.k = 2, .table = xor_table};

bool result = mpdt_evaluate(&neuron, 0b10); // result = true
```

## Features

- **Deterministic:** Same input always produces same output
- **Blazing fast:** O(1) bit operation — nanoseconds on ESP32
- **Minimal footprint:** < 1KB RAM, < 10KB flash for k=8
- **No allocations:** Zero dynamic memory, zero heap usage
- **Flash storage:** Truth tables in PROGMEM (read-only flash)
- **Validated:** k=1..20, table size, null checks

## Building

### Host (Linux/macOS)

```bash
gcc -std=c11 -Wall -Wextra -o test_mpdt src/mpdt_neuron.c tests/test_mpdt.c
./test_mpdt
```

### ESP32 (PlatformIO)

```ini
[env:esp32dev]
platform = espressif32
board = esp32dev
framework = espidf
lib_deps = matrix-micro
```

### Arduino IDE

1. Copy `src/mpdt_neuron.h` and `src/mpdt_neuron.c` to your sketch folder
2. `#include "mpdt_neuron.h"`
3. Compile and upload

## API

### `mpdt_evaluate(neuron, inputs)`
Evaluate neuron for packed k-bit input. Returns 0 or 1.

### `mpdt_validate(neuron)`
Check neuron configuration validity.

### `mpdt_table_size(neuron)` / `mpdt_table_words(neuron)`
Get truth table size in bytes or 32-bit words.

### `mpdt_describe(neuron, buf, bufsz)`
Human-readable description.

## Performance

On ESP32 @ 240MHz:
- **~10-50 ns** per evaluation (two bit operations)
- **20 million** evaluations per second
- **~0.5 μW** per evaluation (vs ~1 μJ for GPU inference)

## Examples

- `examples/xor_example.c` — XOR, AND, OR, Majority-3 neurons
- LED demo: GPIO2 toggles based on XOR of sensor inputs

## Integration with MATRIX

This library is part of the MATRIX ecosystem:
- Train neurons on server (Java/Quarkus genetic algorithm)
- Export as `.ldn` (Lightweight Decision Neuron) JSON
- Flash to ESP32 for real-time inference

## License

AGPLv3 + Ethical Restrictions — see project root LICENSE.

## References

- L1_MPDT_neuron.md — Formal neuron specification
- L16_PhysicalInterfaces.md — Physical interface architecture
