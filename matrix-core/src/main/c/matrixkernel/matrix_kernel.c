/**
 * matrix_kernel — the C side of the MATRIX Java/C boundary experiment (W51).
 *
 * See matrix_kernel.h for the contract. The arithmetic here is the arithmetic
 * of io.matrix.bir.TtForm.eval, in unsigned integers, with no floating point
 * and no tolerance: the two paths are bit-identical or they are wrong.
 */

#include "matrix_kernel.h"

#include <stdint.h>

/**
 * Whether a pointer is aligned for a uint64_t access.
 *
 * The Java side always hands over naturally aligned segments, so this is a
 * belt-and-braces check: a caller that passes a misaligned buffer gets a
 * status code rather than undefined behaviour.
 */
static int matrix_kernel_aligned(const void* p) {
    return (((uintptr_t)p) % (uintptr_t)sizeof(uint64_t)) == 0;
}

/**
 * The validation every entry point shares.
 *
 * Returns MATRIX_KERNEL_OK when the call may proceed, or the first failing
 * status code. Nothing is dereferenced here.
 */
static int matrix_kernel_validate(const uint64_t* table,
                                   int64_t table_words,
                                   int32_t k,
                                   const uint64_t* inputs,
                                   uint64_t* outputs,
                                   int64_t n) {
    if (table == NULL || inputs == NULL || outputs == NULL) {
        return MATRIX_KERNEL_ERR_NULL;
    }
    if (k < MATRIX_KERNEL_MIN_ARITY || k > MATRIX_KERNEL_MAX_ARITY) {
        return MATRIX_KERNEL_ERR_ARITY;
    }
    /* ceil(2^k / 64) words are required. 1 << k is safe: k <= 20. */
    {
        int64_t cells = (int64_t)1 << k;
        int64_t required = (cells + 63) / 64;
        if (table_words < required) {
            return MATRIX_KERNEL_ERR_TABLE;
        }
    }
    if (n < 0 || n > MATRIX_KERNEL_MAX_BATCH) {
        return MATRIX_KERNEL_ERR_COUNT;
    }
    if (!matrix_kernel_aligned(table) || !matrix_kernel_aligned(inputs)
            || !matrix_kernel_aligned(outputs)) {
        return MATRIX_KERNEL_ERR_ALIGNMENT;
    }
    return MATRIX_KERNEL_OK;
}

int matrix_tt_eval_batch(const uint64_t* table,
                         int64_t table_words,
                         int32_t k,
                         const uint64_t* inputs,
                         uint64_t* outputs,
                         int64_t n) {
    int status = matrix_kernel_validate(table, table_words, k, inputs, outputs, n);
    if (status != MATRIX_KERNEL_OK) {
        return status;
    }
    if (n == 0) {
        return MATRIX_KERNEL_OK;
    }
    /* The Java reference masks the input with ((1L << k) - 1). k <= 20, so the
     * shift is well defined and the mask is exact. */
    {
        const uint64_t mask = (UINT64_C(1) << k) - UINT64_C(1);
        int64_t i;
        for (i = 0; i < n; i++) {
            const uint64_t v = inputs[i] & mask;
            const uint32_t idx = (uint32_t)v;
            outputs[i] = (table[idx >> 6] >> (idx & 63)) & UINT64_C(1);
        }
    }
    return MATRIX_KERNEL_OK;
}

int matrix_tt_eval_batch_noop(const uint64_t* table,
                               int64_t table_words,
                               int32_t k,
                               const uint64_t* inputs,
                               uint64_t* outputs,
                               int64_t n) {
    /* The same validation, so the timing of this call is the timing of the
     * real call minus the arithmetic. */
    return matrix_kernel_validate(table, table_words, k, inputs, outputs, n);
}

const char* matrix_kernel_status_text(int32_t status) {
    switch (status) {
        case MATRIX_KERNEL_OK:
            return "ok";
        case MATRIX_KERNEL_ERR_NULL:
            return "null argument";
        case MATRIX_KERNEL_ERR_ARITY:
            return "arity out of range";
        case MATRIX_KERNEL_ERR_TABLE:
            return "truth table too short";
        case MATRIX_KERNEL_ERR_COUNT:
            return "batch count out of range";
        case MATRIX_KERNEL_ERR_ALIGNMENT:
            return "buffer not aligned";
        default:
            return "unknown status";
    }
}
