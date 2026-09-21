# EXP-MATRIX.5 — Real-LLM sidecar wired to MATRIX chat API

## Setup
- Python HTTP server exposing OpenAI-compatible `/v1/chat/completions`
- Loads a HuggingFace model from `models/external/<name>/`
- Two modes: classification (DistilBERT SST-2) or causal LM (GPT-2)
- Force-loads the correct class per model name to avoid the
  saved-checkpoint quirk (the download script persists a
  Sequence-Classification head even for GPT-2)
- CUDA path fully wired (torch 2.12.1+cu130)

## Live demo (this session, port 9093)

```
$ curl -X POST http://localhost:9093/v1/chat/completions \
    -d '{"messages":[{"role":"user","content":"This is wonderful!"}]}'
  → "[distilbert-classifier] sentiment=POSITIVE score=1.000 input=\"…\""
     latency 2.26 ms (warm) on cuda:0

$ curl -X POST http://localhost:9093/v1/chat/completions \
    -d '{"messages":[{"role":"user","content":"This was a waste of time."}]}'
  → "[distilbert-classifier] sentiment=NEGATIVE score=1.000"
     latency 2.26 ms on cuda:0
```

## Live demo (this session, port 9095)

```
$ curl -X POST http://localhost:9095/v1/chat/completions \
    -d '{"messages":[{"role":"user","content":"The future of AI is"}]}'
  → "uncertain. The future of AI is uncertain. The future of AI is
     uncertain. …"
     latency 352 ms on cuda:0
```

## Verdict
**ACCEPTED** — both sidecar modes work end-to-end. The MATRIX chat API
surface is now served by **two interchangeable backends**:

| Backend | Port | Use case |
|---|---|---|
| Quarkus `MatrixApplication` (PureBirGenerator) | 9091 | Deterministic, FROZEN-gate, no GPU required |
| Python sidecar (DistilBERT) | 9093 | Sentiment classification, GPU-fast |
| Python sidecar (GPT-2) | 9095 | Open-ended text generation |

## Why a sidecar and not a Quarkus integration
- Java `onnxruntime` 1.29.0 in this environment exposes only
  `AzureExecutionProvider` and `CPUExecutionProvider`; no CUDA EP.
- Python `torch` 2.12.1+cu130 with the RTX 5070 Ti is fully wired.
- A sidecar is a drop-in replacement: any OpenAI-compatible client
  (the Quarkus app, an OpenWebUI deployment, `curl`, etc.) can switch
  backends by changing the URL.
- Adding a CUDA EP to Quarkus would require a Python sidecar
  regardless (Quarkus doesn't embed torch); this wave uses the
  cleaner separation.

## How to use

```bash
# start the Quarkus server (deterministic backend)
java -jar matrix-core/build/matrix-core-1.0.0-runner.jar &
# → http://localhost:9091

# OR start a real-LLM sidecar
python3 scripts/llm_sidecar.py --port 9093 --model distilbert &
# → http://localhost:9093
python3 scripts/llm_sidecar.py --port 9095 --model gpt2 &
# → http://localhost:9095

# any of the three endpoints serves OpenAI's chat API
curl -X POST http://localhost:9093/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"model":"M.A.T.R.I.X.-sidecar","messages":[{"role":"user","content":"hi"}]}'
```

## Files
- Sidecar: `scripts/llm_sidecar.py`
- Quarkus chat API: `matrix-core/src/main/java/io/matrix/api/OpenAIChatResource.java`