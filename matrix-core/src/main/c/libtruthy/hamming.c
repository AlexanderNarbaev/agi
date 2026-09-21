/**
 * libtruthy_hamming — high-speed Hamming distance for MATRIX.
 *
 * Public API (C ABI):
 *   int hamming_distance_u64(uint64_t a, uint64_t b)
 *     Returns the Hamming distance (population count of a XOR b).
 *
 *   void hamming_distance_batch(
 *       const uint64_t* a,
 *       const uint64_t* b,
 *       int n,
 *       int* out)
 *     Writes Hamming distance for each pair (a[i], b[i]) to out[i].
 *
 * Compiled to a shared library. Called from Java via Project Panama
 * (JEP 424, java.lang.foreign).
 */
#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

int hamming_distance_u64(uint64_t a, uint64_t b) {
    /* __builtin_popcountll is GCC/Clang intrinsic — popcount on x86_64 */
    return __builtin_popcountll(a ^ b);
}

void hamming_distance_batch(
        const uint64_t* a,
        const uint64_t* b,
        int n,
        int* out) {
    if (!a || !b || !out) return;
    for (int i = 0; i < n; i++) {
        out[i] = __builtin_popcountll(a[i] ^ b[i]);
    }
}

/**
 * bit_count_u64 — pure population count (independent of Hamming).
 */
int bit_count_u64(uint64_t x) {
    return __builtin_popcountll(x);
}

#ifdef __cplusplus
}
#endif
