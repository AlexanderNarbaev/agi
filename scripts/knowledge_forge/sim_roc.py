#!/usr/bin/env python3
"""RECON-W31.2 — SIM-1: measure, do not guess, the retrieval threshold.

The W31.2 plan asks for an "ROC-calibrated threshold" chosen by statistical fit rather
than manual tuning. This tool exists to find out whether such a threshold CAN exist
under the current similarity function, and to compare candidate functions on the same
labeled data rather than on intuition.

Method:
  1. Build a labeled set from the CLEAN knowledge store.
     POSITIVE = (question, its own fact).  The system should retrieve the fact.
     NEGATIVE = (question, the whole store). No fact answers it, so every pair is a
     false positive if retrieved. This mirrors the live failure: a question the mind
     cannot answer is answered anyway.
  2. Score every candidate similarity function over the same pairs.
  3. Sweep thresholds and compute ROC-AUC, plus the best achievable
     (TPR, FPR) operating point. A function with AUC <= 0.5 cannot be thresholded into
     working, no matter where the line is drawn — that is the finding, not a failure
     of the sweep.

Honesty rule: this script prints the AUC even when it is embarrassing. A threshold
quoted without its AUC is a number with no meaning.
"""
from __future__ import annotations
import json, re, sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
KB = REPO / "data/mind/hdc_kb.ndjson"

# Paraphrases of each of the 10 CLEAN facts. A retrieval function must find the fact
# from a phrasing it was never indexed under, or it is matching tokens, not meaning.
# Several are deliberately different wordings: "capital city of Japan" vs "Tokyo is the
# capital of Japan" share only 'capital' and 'of'.
PARAPHRASES = {
    "seed-capital-france": [
        "What is the capital of France?",
        "capital city of France",
        "Which city is France's capital?",
    ],
    "seed-capital-japan": [
        "What is the capital of Japan?",
        "Japan's capital city is",
        "Where is the Japanese government seated?",
    ],
    "seed-capital-russia": [
        "What is the capital of Russia?",
        "capital city of Russia",
        "Russia is administered from",
    ],
    "seed-color-sky": [
        "Why is the sky blue?",
        "What colour is the daytime sky?",
        "What makes the sky look blue?",
    ],
    "seed-planet-count": [
        "How many planets are in the solar system?",
        "count of planets orbiting the sun",
        "How many planets do we have?",
    ],
    "seed-ai-definition": [
        "What is artificial intelligence?",
        "define AI",
        "What field builds systems that do tasks needing intelligence?",
    ],
    "canonical-db74ff0707ca554f": [
        "What is gravity?",
        "define gravity",
        "What force pulls objects together?",
    ],
    "canonical-d272abd690a81985": [
        "Tell me about gravity",
        "gravity",
        "What is gravitational force?",
    ],
    "fed-node-B-fed-1": [
        "What is gravity?",
        "how strong is gravity",
    ],
    "fed-node-B-f-1": [
        "gravity",
        "value of gravitational acceleration",
    ],
}

# Questions no fact in the clean store answers. Any retrieval is a fabrication.
# Spans the failure modes seen live: unrelated-domain questions, near-miss entities
# (Peru/Australia look like the capital facts but are absent), and general trivia.
NEGATIVE = [
    "How many legs does a spider have?",
    "What is the chemical formula of water?",
    "Who was the first person on the moon?",
    "capital of Peru?",
    "What is the capital of Australia?",
    "What is the boiling point of mercury?",
    "What is the largest planet?",
    "How do I bake bread?",
    "Who wrote the novel Dune?",
    "What is the capital of Brazil?",
    "How many continents are there?",
    "Who painted the Mona Lisa?",
    "What is the atomic number of carbon?",
    "Which ocean is the largest?",
    "What is the speed of sound in air?",
    "Who discovered penicillin?",
    "What is the tallest mountain on Earth?",
    "How many days are in a year?",
    "What language is spoken in Brazil?",
    "What is the capital of Germany?",
]

STOPWORDS = {
    "what","is","the","a","an","of","in","on","at","to","for","and","or","are","was",
    "were","be","been","do","does","did","how","many","much","who","whom","whose",
    "this","that","these","those","it","its","as","by","with","from","into","there",
}


def fnv1a(s: str) -> int:
    """Must mirror PersistentHdcStore.hashToVector exactly (Article III)."""
    h = 0x811c9dc5
    for ch in s:
        h ^= ord(ch)
        h = (h * 0x01000193) & 0xFFFFFFFF
    return h


def vector(text: str, dim: int, drop_stopwords: bool) -> set[int]:
    bits: set[int] = set()
    for tok in text.lower().split():
        if not tok:
            continue
        if drop_stopwords and tok in STOPWORDS:
            continue
        bits.add((fnv1a(tok) & 0x7FFFFFFF) % dim)
    return bits


def jaccard(a: set[int], b: set[int]) -> float:
    if not a and not b:
        return 0.0
    inter = len(a & b)
    union = len(a | b)
    return inter / union if union else 0.0


def content_weighted_jaccard(q: set[int], f: set[int], full_q: set[int]) -> float:
    """Jaccard over CONTENT bits, scaled by how much of the query was content.

    A question made entirely of stopwords carries no evidence; scoring it as a plain
    Jaccard against everything rewards documents that happen to contain 'what is the'.
    """
    if not q:
        return 0.0
    inter = len(q & f)
    if inter == 0:
        return 0.0
    # Coverage: what fraction of the query's content bits did we hit, discounted by
    # how many bits the fact has (so a long fact is not rewarded for being long).
    coverage = inter / len(q)
    precision = inter / max(1, len(f))
    return coverage * precision


def load_facts() -> dict[str, str]:
    out: dict[str, str] = {}
    if KB.exists():
        for line in KB.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            r = json.loads(line)
            out[r["id"]] = r["content"]
    return out


def auc(pos: list[float], neg: list[float]) -> float:
    """ROC-AUC via rank statistic (ties counted as 0.5). Chance = 0.5."""
    if not pos or not neg:
        return float("nan")
    wins = 0.0
    for p in pos:
        for n in neg:
            wins += 1.0 if p > n else (0.5 if p == n else 0.0)
    return wins / (len(pos) * len(neg))


def evaluate(name: str, scorer, facts: dict[str, str], dim: int) -> None:
    pos_scores, neg_scores = [], []
    detail_pos, detail_neg = [], []
    for fid, qs in PARAPHRASES.items():
        if fid not in facts:
            continue
        for q in qs:
            s = scorer(q, facts[fid], dim)
            pos_scores.append(s)
            detail_pos.append((q, s))
    for q in NEGATIVE:
        best, bf = 0.0, ""
        for k, f in facts.items():
            s = scorer(q, f, dim)
            if s > best:
                best, bf = s, k
        neg_scores.append(best)
        detail_neg.append((q, best, bf))

    a = auc(pos_scores, neg_scores)
    prod_floor_breaches = [s for s in neg_scores if s >= 0.20]
    print(f"\n--- {name} ---")
    print(f"  positives: " + ", ".join(f"{s:.3f}" for _, s in detail_pos))
    print(f"  negatives(max): " + ", ".join(f"{s:.3f}" for _, s, _ in detail_neg))
    print(f"  ROC-AUC = {a:.3f}   {'USABLE' if a >= 0.9 else ('MARGINAL' if a >= 0.75 else 'CHANCE-LEVEL: no threshold can fix this')}")
    print(f"  negatives served at the CURRENT production floor 0.20: "
          f"{len(prod_floor_breaches)}/{len(neg_scores)}"
          + (f"  <- these are the live fabrications: "
             + ", ".join(f'{q[:26]}={s:.3f}' for (q, s, _) in detail_neg if s >= 0.20)
             if prod_floor_breaches else "  <- none"))

    # Best operating point over all observed scores.
    cands = sorted(set(pos_scores + neg_scores))
    best = None
    for t in cands:
        tpr = sum(1 for s in pos_scores if s >= t) / len(pos_scores)
        fpr = sum(1 for s in neg_scores if s >= t) / len(neg_scores)
        if fpr == 0.0 and (best is None or tpr > best[1]):
            best = (t, tpr, fpr)
    if best:
        print(f"  best zero-FPR threshold = {best[0]:.3f} (TPR={best[1]:.2f}, FPR={best[2]:.2f})")
    else:
        print("  no zero-FPR threshold exists: some negative outranks every positive")


def main() -> int:
    facts = load_facts()
    if not facts:
        print("clean KB is empty; run kb_audit.py --quarantine first", file=sys.stderr)
        return 2
    dim = 512
    print("=" * 78)
    print("RECON-W31.2 SIM-1 — retrieval threshold, measured")
    print("=" * 78)
    npos = sum(len(v) for k, v in PARAPHRASES.items() if k in facts)
    print(f"  clean facts: {len(facts)}  positives: {npos}  negatives: {len(NEGATIVE)}")
    if npos < 10:
        print("  WARNING: fewer than 10 positives — any AUC here is a small-sample")
        print("  artifact and MUST NOT be quoted as a calibrated result.")

    evaluate("A. current: BitSet Jaccard over ALL tokens (production today)",
             lambda q, f, d: jaccard(vector(q, d, False), vector(f, d, False)), facts, dim)

    def stopword_fn(q, f, d):
        return jaccard(vector(q, d, True), vector(f, d, True))
    evaluate("B. Jaccard over CONTENT tokens only (stopwords dropped)", stopword_fn, facts, dim)

    def content_fn(q, f, d):
        full = vector(q, d, False)
        return content_weighted_jaccard(vector(q, d, True), vector(f, d, True), full)
    evaluate("C. content coverage x precision", content_fn, facts, dim)

    print()
    print("  NOTE: a threshold is only meaningful if the AUC says the signal exists.")
    print("  Quoting a tuned number from a CHANCE-LEVEL function is fitting to noise.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
