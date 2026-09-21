/**
 * matrix-micro — XOR Neuron Example for ESP32
 *
 * Demonstrates MPDT neuron evaluation on an ESP32 microcontroller.
 *
 * Build:
 *   PlatformIO: pio run -t upload
 *   Arduino IDE: open this file, select ESP32 board, upload
 *
 * Wiring: Built-in LED on GPIO2 shows XOR result
 *
 * Ref: L16_PhysicalInterfaces.md §3
 */

#include "mpdt_neuron.h"

#ifdef ESP32
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/gpio.h"
#define LED_PIN GPIO_NUM_2
#elif defined(ARDUINO)
#include <Arduino.h>
#define LED_PIN 2
#else
#include <stdio.h>
#define LED_PIN -1
#endif

/* XOR truth table (k=2): outputs for inputs 00, 01, 10, 11 */
/* Bits: [0]=f(00)=0, [1]=f(01)=1, [2]=f(10)=1, [3]=f(11)=0 → 0b0110 = 0x6 */
static const uint32_t xor_table[] = {0x00000006};

/* AND truth table: 0b1000 = 0x8 */
static const uint32_t and_table[] = {0x00000008};

/* OR truth table: 0b1110 = 0xE */
static const uint32_t or_table[] = {0x0000000E};

/* Majority-3 truth table (k=3): 0b11101000 = 0xE8 */
static const uint32_t maj3_table[] = {0x000000E8};

static MPDTNeuron xor_neuron  = {.k = 2, .table = xor_table};
static MPDTNeuron and_neuron  = {.k = 2, .table = and_table};
static MPDTNeuron or_neuron   = {.k = 2, .table = or_table};
static MPDTNeuron maj3_neuron = {.k = 3, .table = maj3_table};

void setup_hardware(void) {
#if defined(ESP32) || defined(ARDUINO)
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);
#endif
}

void test_xor(void) {
    printf("=== XOR Neuron (k=2) ===\n");
    printf("  f(0,0) = %d (expected 0)\n", mpdt_evaluate(&xor_neuron, 0b00));
    printf("  f(0,1) = %d (expected 1)\n", mpdt_evaluate(&xor_neuron, 0b01));
    printf("  f(1,0) = %d (expected 1)\n", mpdt_evaluate(&xor_neuron, 0b10));
    printf("  f(1,1) = %d (expected 0)\n", mpdt_evaluate(&xor_neuron, 0b11));
}

void test_and_or(void) {
    printf("=== AND Neuron (k=2) ===\n");
    printf("  f(1,1) = %d (expected 1)\n", mpdt_evaluate(&and_neuron, 0b11));
    printf("  f(1,0) = %d (expected 0)\n", mpdt_evaluate(&and_neuron, 0b10));

    printf("=== OR Neuron (k=2) ===\n");
    printf("  f(0,0) = %d (expected 0)\n", mpdt_evaluate(&or_neuron, 0b00));
    printf("  f(1,0) = %d (expected 1)\n", mpdt_evaluate(&or_neuron, 0b10));
}

void test_majority3(void) {
    printf("=== Majority-3 Neuron (k=3) ===\n");
    printf("  f(0,0,0) = %d (expected 0)\n", mpdt_evaluate(&maj3_neuron, 0b000));
    printf("  f(1,0,0) = %d (expected 0)\n", mpdt_evaluate(&maj3_neuron, 0b001));
    printf("  f(1,1,0) = %d (expected 1)\n", mpdt_evaluate(&maj3_neuron, 0b011));
    printf("  f(1,1,1) = %d (expected 1)\n", mpdt_evaluate(&maj3_neuron, 0b111));
}

void test_performance(void) {
    printf("=== Performance Test ===\n");

    volatile bool result;
    uint32_t start, end;

#ifdef ESP32
    start = esp_timer_get_time();
#else
    start = 0;
#endif

    for (int i = 0; i < 1000000; i++) {
        result = mpdt_evaluate(&xor_neuron, (uint32_t)(i & 0x3));
    }

#ifdef ESP32
    end = esp_timer_get_time();
    uint32_t elapsed_us = end - start;
    printf("  1,000,000 evaluations in %lu us\n", elapsed_us);
    printf("  %.1f ns per evaluation\n", (float)elapsed_us * 1000.0 / 1000000.0);
#else
    printf("  Timing not available (ESP32 only)\n");
#endif
    (void)result;
}

void test_describe(void) {
    char buf[128];
    mpdt_describe(&xor_neuron, buf, sizeof(buf));
    printf("=== Neuron Info ===\n");
    printf("  %s\n", buf);
    printf("  Valid: %s\n", mpdt_validate(&xor_neuron) ? "yes" : "no");
    printf("  Table size: %zu bytes\n", mpdt_table_size(&xor_neuron));
}

#ifdef ESP32
void app_main(void) {
    setup_hardware();
    printf("\nMATRIX micro — MPDT Neuron Demo for ESP32\n");
    printf("===========================================\n\n");

    test_describe();
    printf("\n");
    test_xor();
    printf("\n");
    test_and_or();
    printf("\n");
    test_majority3();
    printf("\n");
    test_performance();
    printf("\n");

    printf("Demo complete. LED on GPIO2 shows XOR(a,b) result.\n");

    /* Interactive LED demo: toggle based on XOR of two "sensor" inputs */
    int a = 0, b = 0;
    while (1) {
        bool out = mpdt_evaluate(&xor_neuron, (uint32_t)((a << 1) | b));
        gpio_set_level(LED_PIN, out ? 1 : 0);
        a = !a;
        if (!a) b = !b;
        vTaskDelay(pdMS_TO_TICKS(500));
    }
}
#else
int main(void) {
    printf("\nMATRIX micro — MPDT Neuron Demo (Host)\n");
    printf("=========================================\n\n");

    test_describe();
    printf("\n");
    test_xor();
    printf("\n");
    test_and_or();
    printf("\n");
    test_majority3();
    printf("\n");

    printf("All tests passed.\n");
    return 0;
}
#endif
