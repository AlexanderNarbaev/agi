/**
 * matrix-micro — Unit Tests
 *
 * Compile and run on host:
 *   gcc -std=c11 -Wall -Wextra -o test_mpdt src/mpdt_neuron.c tests/test_mpdt.c
 *   ./test_mpdt
 */

#include "mpdt_neuron.h"
#include <stdio.h>
#include <assert.h>
#include <string.h>

#define TEST(name) printf("  TEST: %s ... ", name)
#define PASS() printf("PASS\n")
#define CHECK(cond) do { if (!(cond)) { printf("FAIL at line %d\n", __LINE__); return 1; } } while(0)

/* Pre-defined truth tables */
static const uint32_t xor_table[] = {0x6};          /* 0b0110 */
static const uint32_t and_table[] = {0x8};          /* 0b1000 */
static const uint32_t always_true[] = {0xF};        /* 0b1111 */
static const uint32_t always_false[] = {0x0};       /* 0b0000 */
static const uint32_t k8_table[8] = {               /* 256 bits = 8 words */
    0xFFFFFFFF, 0x00000000, 0xAAAAAAAA, 0x55555555,
    0xFFFFFFFF, 0x00000000, 0xAAAAAAAA, 0x55555555
};

static MPDTNeuron n_xor   = {.k = 2, .table = xor_table};
static MPDTNeuron n_and   = {.k = 2, .table = and_table};
static MPDTNeuron n_true  = {.k = 2, .table = always_true};
static MPDTNeuron n_false = {.k = 2, .table = always_false};
static MPDTNeuron n_k8    = {.k = 8, .table = k8_table};

int test_xor(void) {
    TEST("XOR neuron");
    CHECK(mpdt_evaluate(&n_xor, 0b00) == 0);
    CHECK(mpdt_evaluate(&n_xor, 0b01) == 1);
    CHECK(mpdt_evaluate(&n_xor, 0b10) == 1);
    CHECK(mpdt_evaluate(&n_xor, 0b11) == 0);
    PASS();
    return 0;
}

int test_and(void) {
    TEST("AND neuron");
    CHECK(mpdt_evaluate(&n_and, 0b00) == 0);
    CHECK(mpdt_evaluate(&n_and, 0b01) == 0);
    CHECK(mpdt_evaluate(&n_and, 0b10) == 0);
    CHECK(mpdt_evaluate(&n_and, 0b11) == 1);
    PASS();
    return 0;
}

int test_always(void) {
    TEST("Always-true/false");
    CHECK(mpdt_evaluate(&n_true, 0b00) == 1);
    CHECK(mpdt_evaluate(&n_true, 0b11) == 1);
    CHECK(mpdt_evaluate(&n_false, 0b00) == 0);
    CHECK(mpdt_evaluate(&n_false, 0b11) == 0);
    PASS();
    return 0;
}

int test_k8(void) {
    TEST("k=8 neuron (256 entries)");
    /* First word = 0xFFFFFFFF → outputs 1 for inputs 0-31 */
    CHECK(mpdt_evaluate(&n_k8, 0) == 1);
    CHECK(mpdt_evaluate(&n_k8, 31) == 1);
    /* Second word = 0x00000000 → outputs 0 for inputs 32-63 */
    CHECK(mpdt_evaluate(&n_k8, 32) == 0);
    CHECK(mpdt_evaluate(&n_k8, 63) == 0);
    /* Third word = 0xAAAAAAAA → alternating 1/0 */
    CHECK(mpdt_evaluate(&n_k8, 64) == 0);
    CHECK(mpdt_evaluate(&n_k8, 65) == 1);
    PASS();
    return 0;
}

int test_validate(void) {
    TEST("Validation");
    CHECK(mpdt_validate(&n_xor) == true);

    MPDTNeuron invalid_k = {.k = 0, .table = xor_table};
    CHECK(mpdt_validate(&invalid_k) == false);

    MPDTNeuron invalid_k2 = {.k = MPDT_K_MAX + 1, .table = xor_table};
    CHECK(mpdt_validate(&invalid_k2) == false);

    MPDTNeuron invalid_null = {.k = 2, .table = NULL};
    CHECK(mpdt_validate(&invalid_null) == false);

    CHECK(mpdt_validate(NULL) == false);
    PASS();
    return 0;
}

int test_table_size(void) {
    TEST("Table size calculation");
    /* k=2: 4 bits = 1 byte */
    CHECK(mpdt_table_size(&n_xor) == 1);
    /* k=3: 8 bits = 1 byte */
    MPDTNeuron n3 = {.k = 3, .table = xor_table};
    CHECK(mpdt_table_size(&n3) == 1);
    /* k=4: 16 bits = 2 bytes */
    MPDTNeuron n4 = {.k = 4, .table = xor_table};
    CHECK(mpdt_table_size(&n4) == 2);
    /* k=8: 256 bits = 32 bytes = 8 words */
    CHECK(mpdt_table_size(&n_k8) == 32);
    CHECK(mpdt_table_words(&n_k8) == 8);
    PASS();
    return 0;
}

int test_describe(void) {
    TEST("Describe function");
    char buf[128];
    int len = mpdt_describe(&n_xor, buf, sizeof(buf));
    CHECK(len > 0);
    CHECK(strstr(buf, "MPDT") != NULL);
    CHECK(strstr(buf, "k=2") != NULL);

    len = mpdt_describe(NULL, buf, sizeof(buf));
    CHECK(len > 0);
    CHECK(strstr(buf, "INVALID") != NULL);
    PASS();
    return 0;
}

int test_input_masking(void) {
    TEST("Input masking (extra bits ignored)");
    /* k=2: only lowest 2 bits matter */
    CHECK(mpdt_evaluate(&n_xor, 0b0000) == 0);
    CHECK(mpdt_evaluate(&n_xor, 0b0101) == 1);
    CHECK(mpdt_evaluate(&n_xor, 0b1010) == 1);
    CHECK(mpdt_evaluate(&n_xor, 0b1111) == 0);
    /* These should give same results as 00, 01, 10, 11 */
    CHECK(mpdt_evaluate(&n_xor, 0b100) == mpdt_evaluate(&n_xor, 0b00));
    CHECK(mpdt_evaluate(&n_xor, 0b101) == mpdt_evaluate(&n_xor, 0b01));
    PASS();
    return 0;
}

int main(void) {
    printf("\nmatrix-micro — Unit Tests\n");
    printf("==========================\n\n");

    int failures = 0;
    failures += test_xor();
    failures += test_and();
    failures += test_always();
    failures += test_k8();
    failures += test_validate();
    failures += test_table_size();
    failures += test_describe();
    failures += test_input_masking();

    printf("\n==========================\n");
    if (failures == 0) {
        printf("ALL TESTS PASSED\n\n");
        return 0;
    } else {
        printf("%d TEST(S) FAILED\n\n", failures);
        return 1;
    }
}
