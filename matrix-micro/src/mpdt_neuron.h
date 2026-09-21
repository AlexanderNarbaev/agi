/**
 * matrix-micro — MPDT Neuron Library for Microcontrollers
 *
 * Lightweight C implementation of MPDT (McCulloch-Pitts Decision Tree)
 * neurons for ESP32, Arduino, and other resource-constrained devices.
 *
 * Key features:
 * - Truth table stored as const uint32_t[] in flash (PROGMEM)
 * - evaluate() via bit operations: O(1) time, nanoseconds
 * - Supports k=1..20 inputs (table size 2^k bits)
 * - No dynamic memory allocation
 * - < 1KB RAM, < 10KB flash for typical k=8
 *
 * Usage:
 *   #include "mpdt_neuron.h"
 *
 *   // XOR neuron (k=2): table = 0b0110 = {00→0, 01→1, 10→1, 11→0}
 *   static const uint32_t xor_table[] PROGMEM = {0x6};
 *   MPDTNeuron xor_neuron = {.k = 2, .table = xor_table};
 *
 *   bool result = mpdt_evaluate(&xor_neuron, 0b10); // result = 1
 *
 * Ref: L1_MPDT_neuron.md, L16_PhysicalInterfaces.md §3
 */

#ifndef MPDT_NEURON_H
#define MPDT_NEURON_H

#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/** Maximum number of neuron inputs */
#define MPDT_K_MAX 20

/** Maximum truth table size in 32-bit words (2^20 bits / 32) */
#define MPDT_TABLE_MAX_WORDS (1 << (MPDT_K_MAX - 5))

/**
 * MPDT Neuron descriptor.
 *
 * The truth table `table` is an array of 2^(k-5) uint32_t values
 * stored in flash memory (PROGMEM on AVR/ESP). Each bit represents
 * the output for a specific input combination.
 *
 * Input encoding: bit i (LSB) of the input word corresponds to
 * input x_i. The input word itself IS the index into the truth table.
 */
typedef struct {
    uint8_t k;                          /**< Number of inputs (1..20) */
    const uint32_t *table;              /**< Truth table in flash */
} MPDTNeuron;

/**
 * Evaluates the neuron for a given input.
 *
 * @param neuron  Pointer to neuron descriptor
 * @param inputs  k-bit input word (bits 0..k-1)
 * @return        Boolean output (0 or 1)
 *
 * Complexity: O(1) — two bit operations.
 * On ESP32 @ 240MHz: ~10-50 nanoseconds.
 */
static inline bool mpdt_evaluate(const MPDTNeuron *neuron, uint32_t inputs) {
    uint32_t mask = (1UL << neuron->k) - 1;
    uint32_t idx = inputs & mask;
    uint32_t word_idx = idx >> 5;
    uint32_t bit_idx = idx & 0x1F;
    return (neuron->table[word_idx] >> bit_idx) & 1;
}

/**
 * Evaluates the neuron with individual boolean inputs.
 *
 * @param neuron  Pointer to neuron descriptor
 * @param ...     k boolean arguments (must match neuron->k)
 * @return        Boolean output
 *
 * Usage: mpdt_evaluate_bool(&n, true, false, true);
 */
static inline bool mpdt_evaluate_bool(const MPDTNeuron *neuron, ...) {
    /* For fixed-k usage; for variable k, use mpdt_evaluate with packed input */
    return mpdt_evaluate(neuron, 0); /* Stub — override per application */
}

/**
 * Returns the size of the truth table in bytes.
 */
static inline size_t mpdt_table_size(const MPDTNeuron *neuron) {
    return ((1UL << neuron->k) + 7) / 8;
}

/**
 * Returns the number of 32-bit words in the truth table.
 */
static inline size_t mpdt_table_words(const MPDTNeuron *neuron) {
    return ((1UL << neuron->k) + 31) / 32;
}

/**
 * Validates that the neuron configuration is within limits.
 *
 * @return true if valid, false if k out of range or table is NULL
 */
static inline bool mpdt_validate(const MPDTNeuron *neuron) {
    return neuron != NULL
        && neuron->k >= 1
        && neuron->k <= MPDT_K_MAX
        && neuron->table != NULL;
}

/**
 * Converts a neuron to a human-readable description.
 *
 * Writes to buf (max bufsz chars). Returns number of chars written.
 * Format: "MPDT(k=N, table_size=M bytes)"
 */
int mpdt_describe(const MPDTNeuron *neuron, char *buf, size_t bufsz);

#ifdef __cplusplus
}
#endif

#endif /* MPDT_NEURON_H */
