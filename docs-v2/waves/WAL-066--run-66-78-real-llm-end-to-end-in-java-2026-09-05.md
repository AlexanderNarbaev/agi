# WAL 66 — ## RUN 66-78 — Real LLM end-to-end in Java (2026-09-05 19:23)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** ## RUN 66-78 — Real LLM end-to-end in Java (2026-09-05 19:23)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

-78 — Real LLM end-to-end in Java (2026-09-05 19:23)

- QwenOnnxBridge: tokenizer + ONNX + greedy/sampling.
- OnnxChatResource: /v1/onnx/{chat,status,reload,generate,metrics}.
- QwenChatTemplate: ChatML formatter for Qwen2.5-Instruct.
- Real 3-turn conversation verified on GPU.
- BPE byte-level encoder/decoder fix (control chars → U+0100+).
- 12 new tests, 5 new Java classes, 3 EXP reports.

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W67

*Auto-extracted by extract-waves.py*
