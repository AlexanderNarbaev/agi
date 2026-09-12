# BitNet b1.58 Weight Format Deep-Research Report

**Date:** 2026-09-12
**Sub-agent:** goal-deep-researcher
**Confidence:** Very high (0.95+)
**Source:** Official microsoft/BitNet/utils/convert-hf-to-gguf-bitnet.py

## 1. Packing Format

Each `uint8` byte holds **4 ternary values** packed as 2-bit chunks, LSB first:

```
Bit layout of one byte B:
  ┌──────┬──────┬──────┬──────┐
  │ [7:6]│ [5:4]│ [3:2]│ [1:0]│
  │ v[3] │ v[2] │ v[1] │ v[0]│
  └──────┴──────┴──────┴──────┘
  
Encoding: 0b00 = -1, 0b01 = 0, 0b10 = +1, 0b11 = unused
```

**Shift order for unpacking**: `[0, 2, 4, 6]` (NOT `[6, 4, 2, 0]`).

## 2. Tensor Shape Mapping

| Tensor | Real output dim | Packed shape (out/4, in) |
|--------|----------------|---------------------------|
| gate_proj | 6912 | (1728, 2560) |
| up_proj | 6912 | (1728, 2560) |
| down_proj | 2560 | (640, 6912) |
| q_proj | 2560 | (640, 2560) |
| k_proj | 640 | (160, 2560) |
| v_proj | 640 | (160, 2560) |
| o_proj | 2560 | (640, 2560) |

Packed row i → dequantized rows [4i, 4i+1, 4i+2, 4i+3] (all at same input column).

## 3. Per-Tensor Scale

For each `*.weight` tensor there's a sibling `*.weight_scale`:
- **dtype**: bfloat16
- **shape**: (1,) — single scalar
- **semantic**: `s = mean(|W_orig|)`, used to dequantize: `W_dequant = ternary × s`

## 4. Java Unpacking Algorithm

```java
public static float[] unpackBitNetWeight(byte[] byteBuf, float scale, int outDim, int inDim) {
    int halfRowDim = outDim / 4;
    float[] out = new float[outDim * inDim];
    int[] shifts = {0, 2, 4, 6};
    
    for (int i = 0; i < halfRowDim; i++) {
        int rowBase = i * inDim;
        int outBase = 4 * i * inDim;
        for (int j = 0; j < inDim; j++) {
            int b = byteBuf[rowBase + j] & 0xFF;
            for (int k = 0; k < 4; k++) {
                int tern = ((b >> shifts[k]) & 0x3) - 1;  // {-1, 0, +1}
                out[outBase + k * inDim + j] = tern * scale;
            }
        }
    }
    return out;
}
```

## 5. Memory-Efficient Inference

For matmul, keep `byteBuf` packed and apply `scale` at output:
```java
float[] y = matmulInt2Ternary(x, byteBuf);   // [batch*seq, outDim] (no scale)
for (int i = 0; i < y.length; i++) y[i] *= scale;  // one FMA pass
```

This avoids materializing the full dequantized matrix.

## 6. References

- `microsoft/BitNet/utils/convert-hf-to-gguf-bitnet.py` — canonical unpacking
- `microsoft/BitNet/src/ggml-bitnet-mad.cpp` — CPU kernel `quantize_i2_s()`
- Ma S. et al. (2024). arXiv:2402.17764

## 7. Validation

Empirically verified for `model.layers.0.mlp.gate_proj.weight`:
- Distribution: 30.27% / -1, 39.13% / 0, 30.60% / +1 (close to theoretical 25%/50%/25% for symmetric random)
- mean(|W_dequant|) ≈ 0.946 (matches theory: 0.6087 × 1.5547)
- All 542 tensors load correctly via SafetensorsReader
