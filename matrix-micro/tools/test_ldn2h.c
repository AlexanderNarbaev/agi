#include "mpdt_neuron.h"
#include "matrix_test_sample_neurons.h"
#include <stdio.h>
#include <assert.h>

int main(void) {
    printf("Testing %d neurons from ldn2h-generated headers\n", MATRIX_NEURON_COUNT);
    assert(MATRIX_NEURON_COUNT == 3);

    assert(mpdt_validate(matrix_neurons[0]));
    assert(matrix_neurons[0]->k == 2);
    assert(mpdt_evaluate(matrix_neurons[0], 0b00) == 0);
    assert(mpdt_evaluate(matrix_neurons[0], 0b01) == 1);
    assert(mpdt_evaluate(matrix_neurons[0], 0b10) == 1);
    assert(mpdt_evaluate(matrix_neurons[0], 0b11) == 0);
    printf("  Neuron 0 (XOR): PASS\n");

    assert(mpdt_validate(matrix_neurons[1]));
    assert(mpdt_evaluate(matrix_neurons[1], 0b00) == 0);
    assert(mpdt_evaluate(matrix_neurons[1], 0b11) == 1);
    printf("  Neuron 1 (AND): PASS\n");

    assert(mpdt_validate(matrix_neurons[2]));
    assert(matrix_neurons[2]->k == 3);
    printf("  Neuron 2 (k=3): PASS\n");

    printf("All ldn2h tests passed!\n");
    return 0;
}
