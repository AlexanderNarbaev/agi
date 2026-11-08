/**
 * matrix_kernel — the C side of the MATRIX Java/C boundary experiment (W51).
 *
 * One kernel only: the batched evaluation of a BOOLEAN TRUTH TABLE (the BIR
 * `TtForm` form of io.matrix.bir), over a batch of packed input words.
 *
 * The Java reference is io.matrix.bir.TtForm.eval / TtForm.evalBatch:
 *
 *     int  idx = (int) (input[0] & ((1L << k) - 1));
 *     out[0] = (table[idx >>> 6] >>> (idx & 63)) & 1L;
 *
 * This C function reproduces that arithmetic EXACTLY, in unsigned integer
 * arithmetic, so the two paths are bit-identical. There is no floating point
 * anywhere in this file: every comparison is on integers and the result is a
 * single bit.
 *
 * The ABI is deliberately flat: three pointers and three scalars. One packed
 * uint64_t per input row and one packed uint64_t per output row, so the
 * marshalling on the Java side is a straight bulk copy with no per-element
 * conversion.
 *
 * The contract of every entry point:
 *   - it validates its arguments BEFORE dereferencing any pointer,
 *   - it returns MATRIX_KERNEL_OK (0) on success and a NEGATIVE status code
 *     on refusal,
 *   - it never writes outside the buffers it was given,
 *   - it never aborts, never traps and never reads uninitialised memory.
 *
 * A caller that passes a malformed argument gets a status code, not a
 * segfault: that is the property the Java-side boundary test asserts.
 */

#ifndef MATRIX_KERNEL_H
#define MATRIX_KERNEL_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ── status codes ─────────────────────────────────────────────────────────── */

/** The call succeeded. */
#define MATRIX_KERNEL_OK 0
/** A required pointer argument was NULL. */
#define MATRIX_KERNEL_ERR_NULL (-1)
/** The arity was outside the supported range. */
#define MATRIX_KERNEL_ERR_ARITY (-2)
/** The truth table was shorter than the arity requires. */
#define MATRIX_KERNEL_ERR_TABLE (-3)
/** The batch count was negative or above the supported maximum. */
#define MATRIX_KERNEL_ERR_COUNT (-4)
/** A buffer was not aligned on a uint64_t boundary. */
#define MATRIX_KERNEL_ERR_ALIGNMENT (-5)

/* ── bounds ──────────────────────────────────────────────────────────────── */

/** The smallest arity a truth table may have. */
#define MATRIX_KERNEL_MIN_ARITY 1
/** The largest arity a truth table may have (matches io.matrix.bir.TtForm). */
#define MATRIX_KERNEL_MAX_ARITY 20
/** The largest batch this library will process in one call. */
#define MATRIX_KERNEL_MAX_BATCH (1L << 24)

/**
 * Evaluates a truth table over a batch of packed inputs.
 *
 * @param table       the packed truth table: 2^k bits, little-endian, so the
 *                    bit for row r lives at bit (r % 64) of word (r / 64).
 * @param table_words the number of uint64_t words in `table`. It must be at
 *                    least ceil(2^k / 64); a larger table is accepted and the
 *                    surplus words are ignored.
 * @param k           the arity, in [MATRIX_KERNEL_MIN_ARITY,
 *                    MATRIX_KERNEL_MAX_ARITY].
 * @param inputs      `n` packed input words. Only the low k bits of each word
 *                    are read, exactly as the Java reference masks them.
 * @param outputs     `n` output words, each written with 0 or 1.
 * @param n           the batch size, in [0, MATRIX_KERNEL_MAX_BATCH].
 * @return MATRIX_KERNEL_OK on success, a negative status code on refusal.
 */
int matrix_tt_eval_batch(const uint64_t* table,
                         int64_t table_words,
                         int32_t k,
                         const uint64_t* inputs,
                         uint64_t* outputs,
                         int64_t n);

/**
 * The same ABI and the same validation as matrix_tt_eval_batch, but it does
 * not compute anything and does not write to `outputs`.
 *
 * <p>This exists so the benchmark can separate the cost of the CALL and of
 * the MARSHALLING from the cost of the KERNEL: the difference between the
 * two timings is the cost of the computation itself.
 */
int matrix_tt_eval_batch_noop(const uint64_t* table,
                               int64_t table_words,
                               int32_t k,
                               const uint64_t* inputs,
                               uint64_t* outputs,
                               int64_t n);

/**
 * A human-readable name of a status code.
 *
 * @param status a status code returned by this library.
 * @return a NUL-terminated static string; never NULL.
 */
const char* matrix_kernel_status_text(int32_t status);

#ifdef __cplusplus
}
#endif

#endif /* MATRIX_KERNEL_H */
