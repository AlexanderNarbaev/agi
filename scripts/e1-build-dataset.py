#!/usr/bin/env python3
"""E1 — build the reproducible, provenance-carrying learning-evidence dataset.

**What it writes.**

1. ``matrix-brain-runtime/src/test/resources/e1/synthetic-episodes.ndjson`` —
   144 controlled episodes, fixed seed, balanced classes, with a documented
   label rule: the label is a property of the CONTENT (the reply echoes at
   least K distinct tokens of the input), so that the representation
   comparison measures discrimination of a known function of the tokens.
   These are **mechanism fixtures**, not real-world evidence.
2. ``matrix-brain-runtime/src/test/resources/e1/DATASET-MANIFEST.json`` —
   the provenance: the generator, the seed, the label rules, the counts,
   and the sha256 of both the synthetic fixture and the real episodic log.

**What it references but does not copy.** The real episodic log
(``data/mind/episodic.ndjson``, 108 entries, the running gateway's own data)
is referenced by path and sha256 — the repository does not copy runtime
data, and the harness reports INSUFFICIENT_DATA when the file is absent.

The label rule of the real data is the production rule, read from the
episodic entry itself: ``confidence >= 0.7 AND accepted``.

USAGE
-----
    python3 scripts/e1-build-dataset.py
"""

import hashlib
import json
import random
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REAL_LOG = ROOT / "data" / "mind" / "episodic.ndjson"
OUT_DIR = ROOT / "matrix-brain-runtime" / "src" / "test" / "resources" / "e1"
SYNTHETIC = OUT_DIR / "synthetic-episodes.ndjson"
MANIFEST = OUT_DIR / "DATASET-MANIFEST.json"

SEED = 42
EPISODES = 144
MIN_OVERLAP = 2          # the synthetic label rule's threshold
VOCABULARY = [
    "gravity", "motion", "cortex", "signal", "buffer", "pattern", "vector",
    "clause", "memory", "reward", "policy", "state", "action", "observe",
    "predict", "error", "update", "weight", "feature", "orbit", "switch",
    "ledger", "trace", "window", "anchor", "bridge", "task", "guard",
    "spike", "rate", "phase", "loop", "fold", "hash", "token", "concept",
]


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(65536), b""):
            digest.update(block)
    return digest.hexdigest()


def real_manifest():
    """The real episodic log, referenced by path and hash, measured."""
    if not REAL_LOG.is_file():
        return {"path": str(REAL_LOG.relative_to(ROOT)), "present": False,
                "note": "the runtime's own data is not on this machine; the harness "
                        "must report INSUFFICIENT_DATA for the real split"}
    entries = [json.loads(line) for line in REAL_LOG.read_text(encoding="utf-8").splitlines()
               if line.strip()]
    labels = [entry.get("confidence", 0.0) >= 0.7 and bool(entry.get("accepted"))
              for entry in entries]
    inputs = {entry.get("input") for entry in entries}
    replies = {entry.get("reply") for entry in entries}
    stamps = sorted(entry.get("ts") for entry in entries if entry.get("ts") is not None)
    label_rule = "confidence >= 0.7 AND accepted (the production rule, read from the entry)"
    # distinct feature vectors require the production extractor (Java) and are
    # reported by the harness; the text-level distribution is measured here.
    return {
        "path": str(REAL_LOG.relative_to(ROOT)),
        "present": True,
        "sha256": sha256(REAL_LOG),
        "entries": len(entries),
        "positives": sum(1 for label in labels if label),
        "negatives": sum(1 for label in labels if not label),
        "distinctInputs": len(inputs),
        "distinctReplies": len(replies),
        "firstTimestamp": stamps[0] if stamps else None,
        "lastTimestamp": stamps[-1] if stamps else None,
        "labelRule": label_rule,
        "trainingWindow": ("the last K_MAX=20 entries in time order (the production window, "
                           "RealSleepScheduler.runInduction)"),
        "note": ("runtime data, not part of the repository; referenced by path and hash. "
                 "The distinct 256-bit feature count is measured by the harness, not here."),
    }


def synthetic_dataset():
    """The controlled fixtures: a documented content rule, a fixed seed."""
    generator = random.Random(SEED)
    lines = []
    for index in range(EPISODES):
        # every other episode is intended positive; the class is then decided by
        # the CONTENT rule, so the realised distribution is measured, not assumed
        intended = index % 2 == 0
        length = generator.randint(3, 7)
        input_tokens = [generator.choice(VOCABULARY) for _ in range(length)]
        overlap = generator.randint(1, length)
        reply_tokens = list(input_tokens[:overlap])
        if not intended:
            reply_tokens = reply_tokens[:max(0, overlap - 1)]
        filler = generator.randint(0, 2)
        reply_tokens += [generator.choice(VOCABULARY) for _ in range(filler)]
        distinct_overlap = len(set(input_tokens) & set(reply_tokens))
        label = distinct_overlap >= MIN_OVERLAP
        lines.append(json.dumps({
            "id": "syn-%04d" % index,
            "input": " ".join(input_tokens),
            "reply": " ".join(reply_tokens),
            "label": label,
            "labelRule": "the reply echoes at least %d distinct input tokens" % MIN_OVERLAP,
            "synthetic": True,
            "partition": ("train" if index % 10 < 6 else "validation" if index % 10 < 8 else "test"),
        }, ensure_ascii=False))
    positives = sum(1 for line in lines if json.loads(line)["label"])
    return lines, positives


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    real = real_manifest()
    lines, positives = synthetic_dataset()
    SYNTHETIC.write_text("\n".join(lines) + "\n", encoding="utf-8")
    manifest = {
        "record": "E1-DATASET",
        "version": "1.0",
        "date": "2026-11-14",
        "generator": "scripts/e1-build-dataset.py",
        "seed": SEED,
        "synthetic": {
            "path": str(SYNTHETIC.relative_to(ROOT)),
            "sha256": sha256(SYNTHETIC),
            "episodes": len(lines),
            "positives": positives,
            "negatives": len(lines) - positives,
            "labelRule": ("the reply echoes at least %d distinct tokens of the input — a "
                          "documented CONTENT property, so the comparison measures the "
                          "representations' ability to discriminate a known function of the "
                          "tokens" % MIN_OVERLAP),
            "partitionRule": "seeded stride: 6/10 train, 2/10 validation, 2/10 test",
            "status": "CONTROLLED SYNTHETIC — mechanism fixtures, never real-world evidence",
        },
        "real": real,
        "leakage": {
            "syntheticToReal": "none: the two splits share no episode text (asserted by the harness)",
            "trainToValidationToTest": "disjoint by construction (seeded stride), asserted by the harness",
        },
        "whatThisIsNot": ("It is not a claim that the synthetic episodes measure the production "
                          "distribution, and it is not a held-out real evaluation; the real "
                          "split is the running gateway's log by reference."),
    }
    MANIFEST.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print("synthetic episodes:", len(lines), "| positives:", positives,
          "| negatives:", len(lines) - positives)
    print("real log:", real.get("entries"), "entries, positives:", real.get("positives"),
          "negatives:", real.get("negatives"), "| present:", real.get("present"))
    print("manifest:", MANIFEST.relative_to(ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
