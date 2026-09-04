/**
 * libtruthy — high-speed TruthTable evaluation for MATRIX.
 *
 * Compiled to a shared library. Called from Java via Project Panama
 * (JEP 424, java.lang.foreign). Falls back gracefully if the lib
 * is not on the loader path.
 *
 * Public API (C ABI):
 *   int truthy_layer_evaluate(
 *       const uint8_t* input_bits,   // 0/1 bytes, length = neurons * k
 *       int neurons,                  // number of neurons in the layer
 *       int k,                         // bits per neuron
 *       const uint64_t* table,        // packed truth table: bit (cell % 64) at cell / 64
 *       uint8_t* out_bits             // 0/1 bytes, length = neurons
 *   )
 *   returns 0 on success, negative on error.
 *
 * Each neuron gets a k-bit slice of input_bits. The slice value is the
 * cell index; out_bits[i] = (table[cell / 64] >> (cell % 64)) & 1.
 */
#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

int truthy_layer_evaluate(
        const uint8_t* input_bits,
        int neurons,
        int k,
        const uint64_t* table,
        uint8_t* out_bits) {
    if (!input_bits || !table || !out_bits) return -1;
    if (k < 1 || k > 20) return -2;
    if (neurons < 1) return -3;

    for (int n = 0; n < neurons; n++) {
        int cell = 0;
        const int base = n * k;
        // tight inner loop: unrolled to avoid per-iteration branches
        for (int j = 0; j < k; j++) {
            if (input_bits[base + j]) cell |= (1 << j);
        }
        const uint64_t word = table[cell >> 6];
        out_bits[n] = (uint8_t) ((word >> (cell & 63)) & 1ULL);
    }
    return 0;
}

#ifdef __cplusplus
}
#endif
