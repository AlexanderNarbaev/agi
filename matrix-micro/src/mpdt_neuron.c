/**
 * matrix-micro — MPDT Neuron Library Implementation
 *
 * Ref: L16_PhysicalInterfaces.md §3
 */

#include "mpdt_neuron.h"
#include <stdio.h>

int mpdt_describe(const MPDTNeuron *neuron, char *buf, size_t bufsz) {
    if (!mpdt_validate(neuron)) {
        return snprintf(buf, bufsz, "MPDT(INVALID)");
    }
    return snprintf(buf, bufsz, "MPDT(k=%u, table_size=%zu bytes, words=%zu)",
                    neuron->k, mpdt_table_size(neuron), mpdt_table_words(neuron));
}
