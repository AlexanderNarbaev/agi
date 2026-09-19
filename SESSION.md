# SESSION

**Status:** ephemeral single-entry pointer to the latest wave.

---

## Latest Wave: W400

- **File:** `docs-v2/waves/WAL-400.md`
- **Checkpoint Hash:** `ec7e0560`
- **Date:** 2026-09-19
- **Previous:** [W399](docs-v2/waves/WAL-399.md) (`845f6ba2`)

### W400 Summary
Updated matrix-conv.sh to include `train` command. 5 commands total:
cli, server, replay, list, train.

---

## MILESTONE: Full Conversation Stack Deployed (W356-W400)

### Real Conversation (W392)
- `RealConversationCli` — interactive CLI with local Qwen2.5-0.5B ONNX model
- Real responses generated in ~2s (CPU mode)

### HTTP REST API (W394)
- `RealConversationServer` — `com.sun.net.httpserver` based
- 3 endpoints: `/health`, `/sessions`, `/chat`

### Persistence (W393)
- NDJSON storage per session in `data/conversations/`
- Multi-session continuity (load prior turns on resume)

### Replay Tool (W395)
- `RealConversationReplay` — pretty-print with role prefixes

### Training Pipeline (W399)
- `NdjsonToTraining` — convert NDJSON → JSONL training pairs

### End-to-End Tests (W396)
- 8 JUnit tests for edge cases (escapes, boundaries, etc.)

### Launcher (W398, W400)
- `matrix-conv.sh` — single script with all 5 commands

## Verified Working
```
$ echo "What is 2+2?" | java io.matrix.cli.RealConversationCli
[MATRIX] 2+2 is equal to 4.

$ curl -X POST -d '{"message":"Hello"}' http://localhost:9093/chat
{"reply":"Hello! How can I help..."}

$ ./matrix-conv.sh train /tmp/training.jsonl
Wrote 104 training pairs
```

## Cumulative Session Stats (W356-W400)

- **Waves completed:** 45 (W356-W400)
- **New CLI tools:** 4 (Cli, Server, Replay, NdjsonToTraining)
- **New tests:** 16 (CLI integration tests)
- **Launcher script:** `matrix-core/src/main/scripts/matrix-conv.sh`

---

**Last updated:** 2026-09-19 (W400 complete)
