#!/usr/bin/env python3
"""
MATRIX Real-LLM Sidecar (M-A.T.R.I.X. integration, this wave).

Serves an OpenAI-compatible /v1/chat/completions endpoint backed by a
real DistilBERT or GPT-2 model loaded from the local models/external
directory. The Quarkus app already exposes the same API; this sidecar
is an alternative front-end that uses real LLM weights (CUDA when
available) instead of the project's deterministic PureBirGenerator.

Why a sidecar and not a Java change:
  - Java ONNX Runtime 1.29.0 does not have CUDA EP wired here (only
    AzureExecutionProvider + CPUExecutionProvider).
  - torch + cu130 is fully wired in Python (used by M-A.T.R.I.X.3/4).
  - A sidecar is non-invasive: drop-in replacement for the chat.

Run:
  python3 scripts/llm_sidecar.py --port 9093 --model distilbert-base-sst2
  # then:
  curl http://localhost:9093/v1/chat/completions \
    -H 'Content-Type: application/json' \
    -d '{"model":"distilbert","messages":[{"role":"user","content":"hi"}]}'
"""
import argparse
import http.server
import json
import os
import socketserver
import sys
import time
from pathlib import Path

DEFAULT_MODEL_DIR = {
    "distilbert": "models/external/distilbert-base-sst2",
    "distilbert-tiny": "models/external/distilbert-sst2",
    "gpt2": "models/external/gpt2",
    "dialogpt": "models/external/dialogpt-small",
}


class LLMSidecar:
    def __init__(self, model_name: str):
        import numpy as np
        import torch
        from transformers import AutoTokenizer, AutoModelForSequenceClassification, AutoModelForCausalLM
        self.torch = torch
        self.has_cuda = torch.cuda.is_available()
        model_dir = DEFAULT_MODEL_DIR.get(model_name, model_name)
        if not Path(model_dir).exists():
            raise SystemExit(f"model dir not found: {model_dir}")
        self.model_dir = model_dir
        self.model_name = model_name
        print(f"[sidecar] loading {model_name} from {model_dir}…", flush=True)
        self.tok = AutoTokenizer.from_pretrained(model_dir)
        if self.tok.pad_token is None:
            self.tok.pad_token = self.tok.eos_token
        # DialoGPT and GPT-2 are causal; force the right loader so
        # saved config quirks (the download script persists a Sequence
        # Classification head even for causal models — we override here).
        force_causal = model_name.startswith("gpt") or model_name.startswith("dialogpt")
        try:
            if force_causal:
                self.model = AutoModelForCausalLM.from_pretrained(model_dir).eval()
                self.is_classifier = False
                print(f"[sidecar] loaded as causal LM (forced)", flush=True)
            else:
                self.model = AutoModelForSequenceClassification.from_pretrained(model_dir).eval()
                self.is_classifier = True
                print(f"[sidecar] loaded as classifier (2 labels)", flush=True)
        except (OSError, ValueError):
            self.model = AutoModelForCausalLM.from_pretrained(model_dir).eval()
            self.is_classifier = False
            print(f"[sidecar] loaded as causal LM (fallback)", flush=True)
        if self.has_cuda:
            self.device = torch.device("cuda:0")
            self.model = self.model.to(self.device)
            print(f"[sidecar] CUDA: {torch.cuda.get_device_name(0)}", flush=True)
        else:
            self.device = torch.device("cpu")
            print(f"[sidecar] CPU only", flush=True)
        self.calls = 0

    def chat(self, messages):
        # take last user message
        last_user = next((m["content"] for m in reversed(messages)
                          if m.get("role") == "user"), "")
        if not last_user.strip():
            return "I received your message but it appears to be empty."

        if self.is_classifier:
            # DistilBERT SST-2: sentiment classifier; respond with a
            # templated answer that includes the predicted sentiment
            enc = self.tok(last_user, return_tensors="pt",
                           truncation=True, max_length=128)
            enc = {k: v.to(self.device) for k, v in enc.items()}
            with self.torch.no_grad():
                logits = self.model(**enc).logits
            pred = int(logits.argmax(-1).item())
            label = self.model.config.id2label.get(pred, str(pred))
            score = self.torch.softmax(logits, -1)[0, pred].item()
            return (f"[distilbert-classifier] sentiment={label} "
                    f"score={score:.3f}  input=\"{last_user[:80]}\"")
        else:
            # GPT-2 / DialoGPT: text generation with multi-turn awareness
            history = messages[-6:] if len(messages) > 1 else messages
            prompt_parts = []
            for m in history:
                role = m.get("role", "")
                content = m.get("content", "")
                if role == "user":
                    prompt_parts.append(content + self.tok.eos_token)
                elif role == "assistant":
                    prompt_parts.append(content + self.tok.eos_token)
            prompt = "".join(prompt_parts)
            if not prompt.endswith(self.tok.eos_token):
                prompt = last_user + self.tok.eos_token
            enc = self.tok(prompt, return_tensors="pt",
                           truncation=True, max_length=512)
            enc = {k: v.to(self.device) for k, v in enc.items()}
            with self.torch.no_grad():
                out = self.model.generate(
                    **enc,
                    max_new_tokens=80,
                    do_sample=True,
                    top_k=50,
                    top_p=0.92,
                    temperature=0.7,
                    pad_token_id=self.tok.eos_token_id,
                )
            new_tokens = out[0][enc["input_ids"].shape[1]:]
            text = self.tok.decode(new_tokens, skip_special_tokens=True)
            text = text.strip()
            # DialoGPT produces many <|endoftext|> boundaries; trim
            text = text.split(self.tok.eos_token)[0].strip()
            return text or "(no output)"


class Handler(http.server.BaseHTTPRequestHandler):
    sidecar: LLMSidecar = None  # set in main()

    def _json(self, status: int, body: dict):
        body_b = json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body_b)))
        self.end_headers()
        self.wfile.write(body_b)

    def do_GET(self):
        if self.path == "/v1/models":
            self._json(200, {"object": "list", "data": [
                {"id": "M.A.T.R.I.X.-sidecar", "object": "model",
                 "owned_by": "matrix", "created": int(time.time())},
            ]})
            return
        if self.path == "/health":
            self._json(200, {"ok": True,
                             "calls": self.sidecar.calls,
                             "model": self.sidecar.model_name,
                             "cuda": self.sidecar.has_cuda})
            return
        self._json(404, {"error": "not found"})

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self._json(404, {"error": "not found"})
            return
        length = int(self.headers.get("Content-Length", "0"))
        try:
            req = json.loads(self.rfile.read(length))
        except json.JSONDecodeError as e:
            self._json(400, {"error": f"bad json: {e}"})
            return
        messages = req.get("messages", [])
        t0 = time.perf_counter_ns()
        try:
            response = self.sidecar.chat(messages)
        except Exception as e:
            self._json(500, {"error": f"generation failed: {e}"})
            return
        elapsed_ms = (time.perf_counter_ns() - t0) / 1_000_000
        self.sidecar.calls += 1
        self._json(200, {
            "id": f"chatcmpl-sidecar-{self.sidecar.calls}",
            "object": "chat.completion",
            "created": int(time.time()),
            "model": "M.A.T.R.I.X.-sidecar",
            "choices": [{
                "index": 0,
                "message": {"role": "assistant", "content": response},
                "finish_reason": "stop",
            }],
            "usage": {
                "prompt_tokens": sum(len(m.get("content", "").split())
                                     for m in messages),
                "completion_tokens": len(response.split()),
                "total_tokens": (sum(len(m.get("content", "").split())
                                     for m in messages)
                                 + len(response.split())),
            },
            "_meta": {"elapsed_ms": round(elapsed_ms, 2),
                      "device": "cuda" if self.sidecar.has_cuda else "cpu"},
        })

    def log_message(self, fmt, *args):
        sys.stderr.write("[sidecar-http] " + fmt % args + "\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=9093)
    ap.add_argument("--model", default="distilbert",
                    choices=list(DEFAULT_MODEL_DIR.keys()) + ["custom"])
    ap.add_argument("--model-dir", default=None,
                    help="custom model dir (when --model=custom)")
    args = ap.parse_args()
    if args.model == "custom" and args.model_dir:
        DEFAULT_MODEL_DIR["custom"] = args.model_dir
    Handler.sidecar = LLMSidecar(args.model)
    with socketserver.ThreadingTCPServer(("0.0.0.0", args.port), Handler) as srv:
        print(f"[sidecar] listening on :{args.port}  model={args.model}  "
              f"cuda={Handler.sidecar.has_cuda}", flush=True)
        try:
            srv.serve_forever()
        except KeyboardInterrupt:
            print("[sidecar] shutting down")


if __name__ == "__main__":
    main()