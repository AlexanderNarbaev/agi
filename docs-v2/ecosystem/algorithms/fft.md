---
layout: default
title: FFT — Discrete Fourier Transform
parent: Algorithms
nav_order: 5
permalink: /ecosystem/algorithms/fft/
---

# FFT — Discrete Fourier Transform

## What is FFT?

The **Fast Fourier Transform** decomposes a signal into its frequency
components. For audio, this lets MATRIX recognize tones, pitches, and
phonemes without invoking a neural network.

## Mathematical Foundation

For a discrete signal x[0], x[1], ..., x[N-1]:

```
X[k] = Σ x[n] * e^(-2πi * k * n / N)  for k = 0..N-1
```

The naive DFT is O(N²). The FFT (Cooley-Tukey, 1965) reduces this to O(N log N)
by recursively splitting the signal into even and odd indexed samples.

MATRIX uses a **radix-2 decimation-in-time** implementation.

## Implementation

Source: [`matrix-core/src/main/java/io/matrix/transcoders/AudioFFTEncoder.java`](https://github.com/AlexanderNarbaev/agi/blob/develop/matrix-core/src/main/java/io/matrix/transcoders/AudioFFTEncoder.java)

Key methods:
- `forward(double[] samples)` — returns magnitude spectrum
- `frequencyBands(magnitudes)` — groups into 8 perceptual bands
- `toHdc(bands)` — encodes as 10,000-bit HDC vector

Performance:
- **1024 samples**: ~50µs
- **4096 samples**: ~250µs
- **16,384 samples**: ~1.2ms

## History

The FFT was popularized by **Cooley & Tukey** (1965), but the algorithm was
known to **Gauss** as early as 1805. Modern implementations include:

- **Bluestein's algorithm** (chirp-z transform) for arbitrary sizes
- **Prime-factor FFT** for non-power-of-2 sizes
- **Mixed-radix FFT** for any composite size

MATRIX uses a power-of-2 radix-2 implementation for cache efficiency.

## Pros

- ✅ Closed-form (auditable)
- ✅ Microsecond latency on CPU
- ✅ Exact reconstruction (inverse FFT)
- ✅ No training data needed

## Cons

- ❌ Frequency resolution is uniform (mel-scale warping needed for audio)
- ❌ No temporal information (use STFT for time-frequency)
- ❌ Phase information is discarded (we only use magnitudes)

## When FFT runs in MATRIX

```
POST /v1/analyze  { input: <base64-wav>, content_type: "audio" }
  ↓
[AudioFFTEncoder.encode]
   ├─ FFT → magnitude spectrum
   ├─ Frequency banding (8 bands)
   └─ HDC encoding → 10,000-bit vector
  ↓
[BIR rules fire on detected frequency patterns]
```

The frequency bands are recorded in the XAI trace for audio queries.

## Code example

```java
double[] samples = readWav("speech.wav");
AudioFFTEncoder encoder = new AudioFFTEncoder();
HyperVector features = encoder.toHdc(samples);
List<Hit> hits = memory.topK(features, 5);
```

---

**Last updated:** 2026-09-21 (Wave T-03)
