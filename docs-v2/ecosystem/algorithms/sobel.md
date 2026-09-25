---
layout: default
title: Sobel — Pure Symbolic Edge Detection
parent: Algorithms
nav_order: 4
permalink: /ecosystem/algorithms/sobel/
---

# Sobel — Pure Symbolic Edge Detection

## What is Sobel?

The **Sobel operator** is a classical edge-detection filter that uses two
3×3 convolution kernels — one for horizontal gradients, one for vertical.
It produces an image where each pixel's value approximates the gradient
magnitude at that point.

MATRIX uses Sobel instead of a neural-network-based edge detector for two
reasons:
1. **Explainability** — Sobel is a closed-form formula, not learned weights
2. **No GPU needed** — runs in microseconds on CPU

## Mathematical Foundation

Two 3×3 kernels:

```
Gx (horizontal):          Gy (vertical):
-1  0  +1                 -1  -2  -1
-2  0  +2                  0   0   0
-1  0  +1                 +1  +2  +1
```

For each pixel:

```
G_x = sum(Gx[i,j] * I[i,j])
G_y = sum(Gy[i,j] * I[i,j])
G  = sqrt(G_x^2 + G_y^2)
```

The gradient magnitude G is high where edges exist.

## Implementation

Source: [`matrix-core/src/main/java/io/matrix/transcoders/VisionEdgeEncoder.java`](https://github.com/AlexanderNarbaev/agi/blob/develop/matrix-core/src/main/java/io/matrix/transcoders/VisionEdgeEncoder.java)

Key methods:
- `encode(byte[] image)` — returns edge map
- `extractShapes(edges)` — clusters edges into shape primitives
- `toHdc(edges)` — converts edge map to 10,000-bit HDC vector

Performance:
- **256×256 image**: ~5ms
- **1024×1024 image**: ~80ms

## History

The Sobel operator was published by **Irwin Sobel** and **Gary Feldman** in
1968 as part of Stanford's SAIL. It became the standard edge-detection
algorithm in classical computer vision (pre-deep-learning era).

## Pros

- ✅ Closed-form (auditable)
- ✅ Fast on CPU (no GPU required)
- ✅ No training data needed
- ✅ Deterministic given input
- ✅ Interpretable: high values = edges

## Cons

- ❌ Sensitive to noise (Gaussian pre-blur recommended)
- ❌ Doesn't capture semantic edges (a cat is just a bunch of pixels)
- ❌ Fixed kernel size limits scale invariance

## When Sobel runs in MATRIX

```
POST /v1/analyze  { input: <base64-image>, content_type: "image" }
  ↓
[VisionEdgeEncoder.encode]
   ├─ Sobel filter → edge map
   ├─ Shape clustering → primitives
   └─ HDC encoding → 10,000-bit vector
  ↓
[BIR rules fire on detected shapes]
```

The edge map is recorded in the XAI trace for image queries.

## Code example

```java
byte[] image = loadPng("cat.png");
VisionEdgeEncoder encoder = new VisionEdgeEncoder();
HyperVector features = encoder.toHdc(image);
// Similarity search against known object vectors
List<Hit> hits = memory.topK(features, 5);
```

---

**Last updated:** 2026-09-21 (Wave T-03)
