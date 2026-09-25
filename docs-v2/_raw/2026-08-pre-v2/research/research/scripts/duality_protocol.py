#!/usr/bin/env python3
"""
Preregistered DUALITY protocol for EXP-023…EXP-026.
====================================================

**PREREGISTRATION TIMESTAMP:** 2026-08-10
**Status:** proposed (preregistered; changes post-data-collection = HARKing, forbidden)

Synopsis
--------
Deterministic synthetic‑dataset generator + statistical pipeline for four
preregistered hypotheses:

  H-023 — D‑axis asymmetry in synthetic judgments (F‑test, bootstrap CI, FDR)
  H-024 — RAT‑index → conformity / accuracy relationship (Spearman ρ, ΔAccuracy)
  H-025 — Cross‑cultural reproducibility (DerSimonian–Laird meta‑analysis, I²)
  H-026 — Rationalization‑type vector vs ontological‑position vector (cosine similarity)

CONSTITUTIONAL CONSTRAINTS (CONSTITUTION VI, VII)
--------------------------------------------------
- No external API calls, no LLM at runtime.
- No ML models in baselines (analytical/statistical only).
- Python permitted ONLY in ``docs/research/`` and ``scripts/`` (VII.1).
- Forbidden claims: "AGI", "does not lie", unbenchmarked numbers (VI.1).

Usage
-----
::

    cd docs/research
    python scripts/duality_protocol.py

Output
------
::

    reports/EXP-023-report.json   — raw results (machine‑readable)
    reports/EXP-023-report.md     — skeleton report (pending real data)
    reports/EXP-024-report.md
    reports/EXP-025-report.md
    reports/EXP-026-report.md

Dependencies
------------
Python ≥ 3.10, numpy, scipy  (stdlib-only baseline; no statsmodels for CRAN‑style purity)
"""

from __future__ import annotations

import hashlib
import json
import math
import os
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import numpy as np
from numpy.random import Generator, default_rng

# ────────────────────────────────────────────────────────────────────────────
# Preregistered constants (DO NOT MODIFY after 2026-08-10)
# ────────────────────────────────────────────────────────────────────────────
PROTOCOL_SEED: int = 0xD04C  # "DUO"  — preregistered seed
N_JUDGMENTS_PER_CORPUS: int = 200
CORPUS_NAMES: tuple[str, ...] = (
    "modernist_western",
    "collectivist_east",
    "traditional_african",
)
N_BOOTSTRAP: int = 10_000
N_PERMUTATION: int = 1_000
FDR_ALPHA: float = 0.05
FDR_M: int = 3  # number of corpora (Benjamini‑Hochberg m)

# Preregistered acceptance thresholds
THRESH_H023_AUC: float = 0.55
THRESH_H024_RHO: float = 0.30
THRESH_H024_DELTA_ACC: float = 0.05  # 5 pp
THRESH_H025_I2: float = 0.50
THRESH_H025_I2_REJECT: float = 0.75
THRESH_H026_COS: float = 0.60
THRESH_H026_COS_REJECT: float = 0.30

PREREG_TIMESTAMP: str = "2026-08-10"

# ────────────────────────────────────────────────────────────────────────────
# Preregistered judgment templates (culturally‑marked, 3 corpora × 200)
# ────────────────────────────────────────────────────────────────────────────
# Each template is a plain‑text sentence with two blanks:  {}  (d‑axis variant)
# and  the corpus‑specific cultural marker baked into the wording.
# Ground truth d_axis is encoded in the template cluster index (see generator).

_W_TEMPLATES: list[str] = [  # modernist_western
    "Personal autonomy in decision‑making is {} important than group consensus.",
    "Self‑expression {} a fundamental right in modern societies.",
    "Individual success {} driven primarily by personal effort rather than structural factors.",
    "Emotional vulnerability in public {} a sign of strength.",
    "Equal representation in leadership {} necessary for social progress.",
    "Competition in the workplace {} more innovation than collaboration.",
    "The pursuit of personal happiness {} the ultimate life goal.",
    "Direct communication style {} more effective than diplomatic phrasing.",
    "Work‑life balance {} a personal responsibility, not a societal obligation.",
    "Risk‑taking behaviour {} rewarded in entrepreneurial cultures.",
    "Gender roles {} becoming increasingly fluid in post‑industrial societies.",
    "Financial independence {} essential for personal freedom.",
    "Self‑promotion in professional settings {} acceptable and expected.",
    "The ability to say no {} a marker of healthy boundaries.",
    "Individual rights {} priority over collective harmony.",
    "Career advancement {} based on merit rather than seniority.",
    "Questioning authority {} encouraged from an early age.",
    "Privacy {} a fundamental human right.",
    "Personal achievement {} measured by self‑defined metrics.",
    "Assertiveness in negotiations {} correlated with better outcomes.",
]

_E_TEMPLATES: list[str] = [  # collectivist_east
    "Harmony within the group {} more important than individual ambition.",
    "Filial piety {} a core virtue that guides family relationships.",
    "The needs of the community {} priority over personal desires.",
    "Saving face {} essential for maintaining social relationships.",
    "Interdependence among family members {} the foundation of society.",
    "Respect for elders {} a non‑negotiable cultural value.",
    "Modesty in self‑presentation {} preferred over self‑promotion.",
    "Duty to one's parents {} extends throughout the entire lifespan.",
    "Group consensus in decision‑making {} preferable to individual choice.",
    "Loyalty to the organization {} valued more than personal career growth.",
    "Social harmony {} maintained through indirect communication.",
    "Reciprocity in relationships {} expected and honoured.",
    "The collective good {} the measure of ethical behaviour.",
    "Humility {} a virtue cultivated from childhood.",
    "Tradition {} a guide for contemporary behaviour.",
    "Intergenerational obligation {} a defining feature of social structure.",
    "Conformity to group norms {} necessary for social acceptance.",
    "Shame {} a more powerful regulator than guilt in collectivist contexts.",
    "The success of the group {} more celebrated than individual achievement.",
    "Patience in conflict resolution {} valued over immediate confrontation.",
]

_A_TEMPLATES: list[str] = [  # traditional_african
    "The wisdom of ancestors {} a guiding force in daily life.",
    "Community solidarity {} the bedrock of social existence.",
    "Ritual and ceremony {} essential for marking life transitions.",
    "Oral tradition {} a valid and authoritative source of knowledge.",
    "The extended family {} the primary unit of social organization.",
    "Respect for elders {} inseparable from respect for ancestral spirits.",
    "Generosity towards community members {} a moral obligation.",
    "Spiritual forces {} influence material outcomes in the world.",
    "The land {} not owned but held in trust for future generations.",
    "Hospitality to strangers {} a sacred duty.",
    "Collective responsibility for children {} extends beyond biological parents.",
    "Naming ceremonies {} a crucial rite of passage.",
    "The community {} the individual in terms of identity.",
    "Traditional healing practices {} valid alongside modern medicine.",
    "Marriage {} a bond between families, not just individuals.",
    "Dance and music {} integral to spiritual and social life.",
    "The chief or elder council {} the legitimate decision‑making body.",
    "Reciprocity in gift‑giving {} maintains social equilibrium.",
    "The living {} a continuous relationship with the dead.",
    "Consensus‑building {} preferred over majority voting.",
]

ALL_TEMPLATES_BY_CORPUS: dict[str, list[str]] = {
    "modernist_western": _W_TEMPLATES,
    "collectivist_east": _E_TEMPLATES,
    "traditional_african": _A_TEMPLATES,
}

# Preregistered variant fill‑words (20 templates × 10 variants = 200/corpus)
# v0‑v4 → masculine pole (d_axis = +1), v5‑v9 → feminine pole (d_axis = -1)
_VARIANT_FILL: list[tuple[str, float]] = [
    ("more", +1.0),           # v0
    ("primarily", +1.0),      # v1
    ("ultimately", +1.0),     # v2
    ("fundamentally", +1.0),  # v3
    ("consistently", +1.0),   # v4
    ("equally", -1.0),        # v5
    ("contextually", -1.0),   # v6
    ("relationally", -1.0),   # v7
    ("collectively", -1.0),   # v8
    ("mutually", -1.0),       # v9
]


# ────────────────────────────────────────────────────────────────────────────
# Data structures
# ────────────────────────────────────────────────────────────────────────────


@dataclass
class Judgment:
    """A single preregistered synthetic judgment."""

    id: str
    corpus: str
    template_idx: int  # 0..19 preregistered template base index
    variant: str  # "v0".."v9" — preregistered fill variant (v0‑4=masc, v5‑9=fem)
    text: str
    # Preregistered ground‑truth values (assigned deterministically, not measured)
    d_axis: float  # ∈ [-1, +1]  (-1=feminine pole, +1=masculine pole)
    pre_confidence: float  # ∈ [0, 1]
    post_rationalization: float  # ∈ [0, 1]
    rationalization_type: str  # "naturalizing" | "socializing" | "none"
    ontological_position: str  # "essentialist" | "constructionist" | "neutral"
    conformity_target: float  # ∈ [0, 1] — probability of conforming on this item
    ground_truth_correctness: bool
    ground_truth_decision: int  # 0 or 1

    @property
    def rat_index(self) -> float:
        """RAT = |Rat_post − Conf_pre| / max(Rat_post, Conf_pre) ∈ [0, 1]"""
        denom = max(self.post_rationalization, self.pre_confidence)
        if denom == 0.0:
            return 0.0
        return abs(self.post_rationalization - self.pre_confidence) / denom


# ────────────────────────────────────────────────────────────────────────────
# Deterministic dataset generator
# ────────────────────────────────────────────────────────────────────────────


def _make_rng(corpus: str, template_idx: int, variant: str) -> Generator:
    """Per‑item deterministic RNG — chains seed → corpus → template → variant."""
    base = hashlib.sha256(
        f"{PROTOCOL_SEED}:{corpus}:{template_idx}:{variant}".encode()
    ).digest()
    seed_int = int.from_bytes(base[:8], "big")
    return default_rng(seed_int)


def generate_dataset() -> list[Judgment]:
    """Generate the full preregistered dataset (3 × 200 judgments)."""
    dataset: list[Judgment] = []
    id_counter: int = 0

    for corpus in CORPUS_NAMES:
        templates = ALL_TEMPLATES_BY_CORPUS.get(corpus)
        if templates is None:
            raise KeyError(f"Unknown corpus: {corpus}")

        for t_idx, template in enumerate(templates):
            for v_idx, (fill_word, d_axis) in enumerate(_VARIANT_FILL):
                variant_name = f"v{v_idx}"
                text = template.format(fill_word)
                is_masc = d_axis > 0

                # ── deterministic RNG for this item ──
                rng = _make_rng(corpus, t_idx, variant_name)

                # pre_confidence: ground‑truth asymmetry (H‑023 expects d_axis‑dependent)
                # Preregistered: masculine pole judgments have slightly higher pre‑confidence
                pre_conf = float(rng.beta(5.3 if is_masc else 4.7, 4.0))

                # post_rationalization: amplifies pre_confidence (H‑024 RAT mechanism)
                rat_shift = float(rng.normal(0.12, 0.06))
                post_rat = min(1.0, max(0.0, pre_conf + rat_shift))

                # rationalization_type: preregistered allocation
                rat_type_idx = rng.choice(
                    ["naturalizing", "socializing", "none"], p=[0.35, 0.35, 0.30]
                )
                rat_type: str = str(rat_type_idx)

                # ontological_position: preregistered allocation
                onto_idx = rng.choice(
                    ["essentialist", "constructionist", "neutral"],
                    p=[0.33, 0.33, 0.34],
                )
                onto_pos: str = str(onto_idx)

                # conformity_target: higher for feminine pole (H‑024)
                conf_base = float(rng.beta(3.5, 4.5) if is_masc else rng.beta(4.5, 3.5))

                # ground_truth decision & correctness: 60 % correct overall
                correct = rng.random() < 0.60
                decision = rng.integers(0, 2)
                if not correct:
                    decision = 1 - decision  # flip → incorrect

                j = Judgment(
                    id=f"{corpus}_{t_idx:03d}_{variant_name}",
                    corpus=corpus,
                    template_idx=t_idx,
                    variant=variant_name,
                    text=text,
                    d_axis=d_axis,
                    pre_confidence=float(pre_conf),
                    post_rationalization=float(post_rat),
                    rationalization_type=rat_type,
                    ontological_position=onto_pos,
                    conformity_target=float(conf_base),
                    ground_truth_correctness=correct,
                    ground_truth_decision=int(decision),
                )
                dataset.append(j)
                id_counter += 1

    expected = len(CORPUS_NAMES) * len(_W_TEMPLATES) * len(_VARIANT_FILL)  # 3 × 20 × 10 = 600
    assert len(dataset) == expected, f"Expected {expected}, got {len(dataset)}"
    return dataset


# ────────────────────────────────────────────────────────────────────────────
# Baselines (no ML models)
# ────────────────────────────────────────────────────────────────────────────


def baseline_random_auc(y_true: np.ndarray, n_bootstrap: int = N_BOOTSTRAP) -> dict[str, float]:
    """Random classifier: AUC ≈ 0.50."""
    rng = default_rng(PROTOCOL_SEED)
    n = len(y_true)
    aucs = np.empty(n_bootstrap, dtype=np.float64)
    for i in range(n_bootstrap):
        y_score = rng.random(n)
        # Simple AUC approximation via Mann‑Whitney U
        pos = y_score[y_true == 1]
        neg = y_score[y_true == 0]
        if len(pos) == 0 or len(neg) == 0:
            aucs[i] = 0.5
        else:
            u_stat = 0.0
            for p in pos:
                for nv in neg:
                    if p > nv:
                        u_stat += 1.0
                    elif p == nv:
                        u_stat += 0.5
            aucs[i] = u_stat / (len(pos) * len(neg)) if len(pos) * len(neg) > 0 else 0.5

    return {
        "auc_mean": float(np.mean(aucs)),
        "auc_ci_low": float(np.percentile(aucs, 2.5)),
        "auc_ci_high": float(np.percentile(aucs, 97.5)),
    }


def baseline_majority_accuracy(y_true: np.ndarray) -> dict[str, Any]:
    """Always predict the most frequent class."""
    if len(y_true) == 0:
        return {"accuracy": 0.0, "majority_class": None}
    classes, counts = np.unique(y_true, return_counts=True)
    majority_class = int(classes[np.argmax(counts)])
    accuracy = float(np.max(counts) / len(y_true))
    return {"accuracy": accuracy, "majority_class": majority_class}


# ────────────────────────────────────────────────────────────────────────────
# Statistical helpers
# ────────────────────────────────────────────────────────────────────────────


def cohens_h(p1: float, p2: float) -> float:
    """Cohen's h = 2·arcsin(√p₁) − 2·arcsin(√p₂)."""
    p1 = max(0.0001, min(0.9999, p1))
    p2 = max(0.0001, min(0.9999, p2))
    return float(2.0 * (math.asin(math.sqrt(p1)) - math.asin(math.sqrt(p2))))


def bootstrap_ci_95(data: np.ndarray, statistic, n_bootstrap: int = N_BOOTSTRAP) -> dict[str, float]:
    """Bootstrap 95 % CI (percentile method) for a statistic."""
    rng = default_rng(PROTOCOL_SEED)
    n = len(data)
    stats = np.empty(n_bootstrap, dtype=np.float64)
    for i in range(n_bootstrap):
        idx = rng.integers(0, n, size=n)
        stats[i] = statistic(data[idx])
    return {
        "mean": float(np.mean(stats)),
        "ci_low": float(np.percentile(stats, 2.5)),
        "ci_high": float(np.percentile(stats, 97.5)),
    }


def benjamini_hochberg(p_values: list[float], alpha: float = FDR_ALPHA) -> list[tuple[int, float, bool]]:
    """Benjamini‑Hochberg FDR correction. Returns [(rank, p_raw, rejected), …]."""
    n = len(p_values)
    indexed = sorted(enumerate(p_values), key=lambda x: x[1])
    rejected = [False] * n
    for rank, (orig_idx, p) in enumerate(indexed, start=1):
        threshold = alpha * rank / n
        if p <= threshold:
            rejected[orig_idx] = True
    return [(i, p, rejected[i]) for i, p in enumerate(p_values)]


def spearman_rho(x: np.ndarray, y: np.ndarray) -> dict[str, float]:
    """Spearman rank correlation with bootstrap CI."""
    from scipy.stats import spearmanr as _spearmanr

    rho, pval = _spearmanr(x, y)
    # Bootstrap CI
    rng = default_rng(PROTOCOL_SEED)
    n = len(x)
    rhos = np.empty(N_BOOTSTRAP, dtype=np.float64)
    for i in range(N_BOOTSTRAP):
        idx = rng.integers(0, n, size=n)
        r_i, _ = _spearmanr(x[idx], y[idx])
        rhos[i] = r_i
    return {
        "rho": float(rho),
        "p_value": float(pval),
        "ci_low": float(np.percentile(rhos, 2.5)),
        "ci_high": float(np.percentile(rhos, 97.5)),
    }


def cosine_similarity(v1: np.ndarray, v2: np.ndarray) -> float:
    """cos(θ) = (v₁·v₂) / (‖v₁‖·‖v₂‖)."""
    dot = float(np.dot(v1, v2))
    norm1 = float(np.linalg.norm(v1))
    norm2 = float(np.linalg.norm(v2))
    if norm1 == 0.0 or norm2 == 0.0:
        return 0.0
    return dot / (norm1 * norm2)


def permutation_test_cosine(
    v1: np.ndarray, v2: np.ndarray, n_perm: int = N_PERMUTATION
) -> float:
    """Permutation test: H₀ = no association between v1 and v2 (cos ≈ 0)."""
    obs_cos = cosine_similarity(v1, v2)
    rng = default_rng(PROTOCOL_SEED)
    count = 0
    for _ in range(n_perm):
        perm = rng.permutation(v1)
        if abs(cosine_similarity(perm, v2)) >= abs(obs_cos):
            count += 1
    return float(count / n_perm)


# ────────────────────────────────────────────────────────────────────────────
# Hypothesis tests
# ────────────────────────────────────────────────────────────────────────────


def test_h023(
    dataset: list[Judgment],
) -> dict[str, Any]:
    """H‑023: D‑axis asymmetry — F‑test vs uniform, bootstrap CI, FDR."""
    results: dict[str, dict] = {}

    for corpus in CORPUS_NAMES:
        sub = [j for j in dataset if j.corpus == corpus]

        # Count masculine (d_axis > 0), feminine (d_axis < 0), neutral (d_axis ≈ 0)
        n_masc = sum(1 for j in sub if j.d_axis > 0.5)
        n_fem = sum(1 for j in sub if j.d_axis < -0.5)
        n_neutral = sum(1 for j in sub if -0.5 <= j.d_axis <= 0.5)
        n_total = n_masc + n_fem + n_neutral

        # χ² test for uniformity
        expected = n_total / 3.0
        chi2 = (
            (n_masc - expected) ** 2
            + (n_fem - expected) ** 2
            + (n_neutral - expected) ** 2
        ) / expected

        from scipy.stats import chi2 as _chi2_dist

        p_chi2 = float(1.0 - _chi2_dist.cdf(chi2, 2))

        # Cohen's h between masculine and feminine proportion
        p_masc = n_masc / n_total if n_total > 0 else 0.0
        p_fem = n_fem / n_total if n_total > 0 else 0.0
        h_val = cohens_h(p_masc, p_fem)

        # Bootstrap CI for Cohen's h
        ci_h = bootstrap_ci_95(
            np.array([j.d_axis for j in sub]),
            lambda d: cohens_h(
                np.mean(d > 0.5), np.mean(d < -0.5)
            ),
        )

        # ROC‑AUC: using d_axis as score to classify "correct" ground_truth_decision
        y_true = np.array([j.ground_truth_decision for j in sub], dtype=np.int32)
        y_score = np.array([j.d_axis for j in sub], dtype=np.float64)
        auc = _simple_auc(y_true, y_score)

        results[corpus] = {
            "n_masculine": n_masc,
            "n_feminine": n_fem,
            "n_neutral": n_neutral,
            "chi2": float(chi2),
            "p_chi2": p_chi2,
            "cohens_h": h_val,
            "cohens_h_ci_low": ci_h["ci_low"],
            "cohens_h_ci_high": ci_h["ci_high"],
            "auc": auc,
            "effect_direction": "masculine" if n_masc > n_fem else "feminine",
        }

    # FDR correction across corpora
    p_vals = [results[c]["p_chi2"] for c in CORPUS_NAMES]
    fdr_results = benjamini_hochberg(p_vals)

    # Check preregistered acceptance criteria
    n_significant = sum(1 for _, _, rej in fdr_results if rej)
    directions = [results[c]["effect_direction"] for c in CORPUS_NAMES]
    same_direction = len(set(directions)) == 1

    accepted = n_significant >= 1 and same_direction
    rejected = all(not rej for _, _, rej in fdr_results) or (
        not same_direction and n_significant >= 2
    )
    verdict = "accepted" if accepted else ("rejected" if rejected else "inconclusive")

    return {
        "per_corpus": results,
        "fdr": [{"corpus": c, "p_raw": p, "rejected": r} for (c, p, r) in
                zip(CORPUS_NAMES, p_vals,
                    [r for _, _, r in sorted(zip(p_vals, fdr_results, range(3)),
                                            key=lambda x: x[0])])],
        "n_significant_after_fdr": n_significant,
        "same_direction": same_direction,
        "verdict": verdict,
    }


def _simple_auc(y_true: np.ndarray, y_score: np.ndarray) -> float:
    """Simple two‑class AUC via Mann‑Whitney U."""
    pos = y_score[y_true == 1]
    neg = y_score[y_true == 0]
    if len(pos) == 0 or len(neg) == 0:
        return 0.5
    u = 0.0
    for p in pos:
        for n in neg:
            if p > n:
                u += 1.0
            elif p == n:
                u += 0.5
    return float(u / (len(pos) * len(neg)))


def test_h024(
    dataset: list[Judgment],
) -> dict[str, Any]:
    """H‑024: RAT‑index → conformity / accuracy."""
    rat_values = np.array([j.rat_index for j in dataset], dtype=np.float64)
    conf_values = np.array([j.conformity_target for j in dataset], dtype=np.float64)
    correct_values = np.array(
        [1.0 if j.ground_truth_correctness else 0.0 for j in dataset], dtype=np.float64
    )

    # Spearman ρ between RAT and conformity
    rho_result = spearman_rho(rat_values, conf_values)

    # Δ Accuracy: low‑RAT (below median) vs high‑RAT (above median)
    median_rat = float(np.median(rat_values))
    low_rat_mask = rat_values < median_rat
    high_rat_mask = rat_values >= median_rat
    acc_low = float(np.mean(correct_values[low_rat_mask])) if np.any(low_rat_mask) else 0.0
    acc_high = float(np.mean(correct_values[high_rat_mask])) if np.any(high_rat_mask) else 0.0
    delta_acc = acc_low - acc_high

    # Check preregistered criteria
    rho_accept = rho_result["rho"] >= THRESH_H024_RHO and rho_result["p_value"] < 0.05
    delta_accept = delta_acc >= THRESH_H024_DELTA_ACC
    accepted = rho_accept or delta_accept

    rho_reject = rho_result["rho"] < 0.10
    delta_reject = abs(delta_acc) < 0.02
    rejected = rho_reject and delta_reject

    verdict = "accepted" if accepted else ("rejected" if rejected else "inconclusive")

    return {
        "spearman_rho": rho_result,
        "median_rat": median_rat,
        "accuracy_low_rat": acc_low,
        "accuracy_high_rat": acc_high,
        "delta_accuracy": delta_acc,
        "verdict": verdict,
    }


def test_h025(
    dataset: list[Judgment],
) -> dict[str, Any]:
    """H‑025: Cross‑cultural reproducibility via DerSimonian–Laird meta‑analysis."""
    from scipy.stats import chi2 as _chi2_dist

    # Per‑corpus effect size: Cohen's h (masculine vs feminine)
    per_corpus: dict[str, dict] = {}
    effect_sizes: list[float] = []
    variances: list[float] = []

    for corpus in CORPUS_NAMES:
        sub = [j for j in dataset if j.corpus == corpus]
        n = len(sub)
        masc = sum(1 for j in sub if j.d_axis > 0.5)
        fem = sum(1 for j in sub if j.d_axis < -0.5)
        n_eff = masc + fem
        if n_eff == 0:
            h_val = 0.0
            var_h = 0.0
        else:
            p_m = masc / n_eff
            p_f = fem / n_eff
            h_val = cohens_h(p_m, p_f)
            # Asymptotic variance of Cohen's h
            var_h = 1.0 / (n_eff * p_m * p_f) if p_m > 0 and p_f > 0 else 0.0

        per_corpus[corpus] = {"cohens_h": h_val, "var": var_h, "n_effective": n_eff}
        if n_eff > 0:
            effect_sizes.append(h_val)
            variances.append(var_h)

    k = len(effect_sizes)
    if k < 2:
        return {"verdict": "inconclusive", "reason": "Need ≥2 corpora with data"}

    # Fixed‑effect weights: w_i = 1 / v_i
    weights = np.array([1.0 / max(v, 1e-10) for v in variances], dtype=np.float64)
    # Weighted mean
    w_sum = float(np.sum(weights))
    theta_fixed = float(np.sum(weights * np.array(effect_sizes)) / w_sum)

    # Cochran's Q
    q = float(np.sum(weights * (np.array(effect_sizes) - theta_fixed) ** 2))
    df = k - 1
    p_q = float(1.0 - _chi2_dist.cdf(q, df)) if df > 0 else 1.0

    # I² = (Q − df) / Q × 100 %
    i2 = max(0.0, (q - df) / q * 100.0) if q > 0 else 0.0

    # DerSimonian–Laird τ²
    c = w_sum - float(np.sum(weights ** 2)) / w_sum
    tau2 = max(0.0, (q - df) / c) if c > 0 else 0.0

    # Random‑effects weights
    re_weights = np.array(
        [1.0 / (max(v, 1e-10) + tau2) for v in variances], dtype=np.float64
    )
    re_w_sum = float(np.sum(re_weights))
    theta_random = float(np.sum(re_weights * np.array(effect_sizes)) / re_w_sum)
    se_random = float(math.sqrt(1.0 / re_w_sum)) if re_w_sum > 0 else 0.0

    # Direction consistency
    signs = [np.sign(h) for h in effect_sizes]
    same_direction = all(s == signs[0] for s in signs)

    # Acceptance criteria
    accepted = i2 < THRESH_H025_I2 and same_direction
    rejected = i2 > THRESH_H025_I2_REJECT or not same_direction
    verdict = "accepted" if accepted else ("rejected" if rejected else "inconclusive")

    return {
        "per_corpus": per_corpus,
        "meta_analysis": {
            "k": k,
            "cochrans_q": q,
            "p_q": p_q,
            "i2_percent": i2,
            "tau2": tau2,
            "theta_fixed": theta_fixed,
            "theta_random": theta_random,
            "se_random": se_random,
            "ci_low_random": theta_random - 1.96 * se_random,
            "ci_high_random": theta_random + 1.96 * se_random,
            "same_direction": same_direction,
        },
        "verdict": verdict,
    }


def test_h026(
    dataset: list[Judgment],
) -> dict[str, Any]:
    """H‑026: Cosine similarity between rationalization vector and ontological position vector."""
    # Build one‑hot vectors across entire dataset
    n = len(dataset)

    # Rationalization type → one‑hot [naturalizing, socializing]
    v_rat = np.zeros((n, 2), dtype=np.float64)
    for i, j in enumerate(dataset):
        if j.rationalization_type == "naturalizing":
            v_rat[i, 0] = 1.0
        elif j.rationalization_type == "socializing":
            v_rat[i, 1] = 1.0
        # "none" → [0, 0]

    # Ontological position → one‑hot [essentialist, constructionist]
    v_onto = np.zeros((n, 2), dtype=np.float64)
    for i, j in enumerate(dataset):
        if j.ontological_position == "essentialist":
            v_onto[i, 0] = 1.0
        elif j.ontological_position == "constructionist":
            v_onto[i, 1] = 1.0
        # "neutral" → [0, 0]

    # Aggregate: mean vector per dimension
    v_rat_agg = np.mean(v_rat, axis=0)
    v_onto_agg = np.mean(v_onto, axis=0)

    cos_sim = cosine_similarity(v_rat_agg, v_onto_agg)

    # Bootstrap CI
    rng = default_rng(PROTOCOL_SEED)
    cos_boot = np.empty(N_BOOTSTRAP, dtype=np.float64)
    for i in range(N_BOOTSTRAP):
        idx = rng.integers(0, n, size=n)
        v_r = np.mean(v_rat[idx], axis=0)
        v_o = np.mean(v_onto[idx], axis=0)
        cos_boot[i] = cosine_similarity(v_r, v_o)

    ci_boot = {
        "mean": float(np.mean(cos_boot)),
        "ci_low": float(np.percentile(cos_boot, 2.5)),
        "ci_high": float(np.percentile(cos_boot, 97.5)),
    }

    # Permutation test
    p_perm = permutation_test_cosine(v_rat_agg, v_onto_agg, N_PERMUTATION)

    # χ² contingency table (2×2)
    n_nat_ess = sum(
        1
        for j in dataset
        if j.rationalization_type == "naturalizing"
        and j.ontological_position == "essentialist"
    )
    n_nat_con = sum(
        1
        for j in dataset
        if j.rationalization_type == "naturalizing"
        and j.ontological_position == "constructionist"
    )
    n_soc_ess = sum(
        1
        for j in dataset
        if j.rationalization_type == "socializing"
        and j.ontological_position == "essentialist"
    )
    n_soc_con = sum(
        1
        for j in dataset
        if j.rationalization_type == "socializing"
        and j.ontological_position == "constructionist"
    )

    from scipy.stats import chi2_contingency

    contingency = np.array([[n_nat_ess, n_nat_con], [n_soc_ess, n_soc_con]], dtype=np.int64)
    chi2_stat, p_chi2, _dof, _expected = chi2_contingency(contingency)

    # Acceptance criteria
    cos_accept = cos_sim >= THRESH_H026_COS and ci_boot["ci_low"] > THRESH_H026_COS_REJECT
    perm_accept = p_perm < 0.05
    accepted = cos_accept and perm_accept
    rejected = cos_sim < THRESH_H026_COS_REJECT
    verdict = "accepted" if accepted else ("rejected" if rejected else "inconclusive")

    return {
        "cosine_similarity": cos_sim,
        "cosine_bootstrap_ci": ci_boot,
        "permutation_p": p_perm,
        "contingency_table": {
            "naturalizing_essentialist": n_nat_ess,
            "naturalizing_constructionist": n_nat_con,
            "socializing_essentialist": n_soc_ess,
            "socializing_constructionist": n_soc_con,
        },
        "chi2_statistic": float(chi2_stat),
        "chi2_p_value": float(p_chi2),
        "verdict": verdict,
    }


# ────────────────────────────────────────────────────────────────────────────
# Report generation
# ────────────────────────────────────────────────────────────────────────────


def _write_skeleton_report(
    path: Path,
    exp_id: str,
    hypothesis_id: str,
    hypothesis_statement: str,
    results: dict[str, Any],
) -> None:
    """Write a preregistered skeleton report (markdown)."""
    lines = [
        f"# {exp_id} — Preregistered Skeleton Report",
        "",
        f"**PREREGISTRATION TIMESTAMP:** {PREREG_TIMESTAMP}",
        f"**Status:** proposed",
        f"**Hypothesis:** {hypothesis_id}",
        "",
        "---",
        "",
        "## Hypothesis Statement",
        "",
        f"> {hypothesis_statement}",
        "",
        "---",
        "",
        "## Preregistered Metrics",
        "",
        "(See `docs/research/reports/EXP-023-card.md` §1 for full operational definitions.)",
        "",
        "---",
        "",
        "## Results",
        "",
        "**STATUS: PENDING** — real data not yet collected. Synthetic preregistered run below.",
        "",
        "```json",
        json.dumps(results, indent=2, ensure_ascii=False, default=str),
        "```",
        "",
        "---",
        "",
        "## Acceptance / Rejection",
        "",
        f"**Verdict (synthetic run):** `{results.get('verdict', 'N/A')}`",
        "",
        "> ⚠ This verdict is based on preregistered SYNTHETIC data only.",
        "> Real‑data verdict will be recorded after data collection.",
        "",
        "---",
        "",
        "## Changelog",
        "",
        "| Date | Change | Author |",
        "|------|--------|--------|",
        f"| {PREREG_TIMESTAMP} | Preregistration skeleton created | duality_protocol.py |",
    ]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def generate_reports(dataset: list[Judgment], results: dict[str, Any]) -> None:
    """Generate all skeleton reports."""
    reports_dir = Path(__file__).resolve().parent.parent / "reports"

    h_statements = {
        "H-023": (
            "Дуальность мира как пара мужское/женское проявляется в операциональной "
            "асимметрии суждений: распределение суждений по дуальной оси статистически "
            "значимо отклоняется от равномерного."
        ),
        "H-024": (
            "Наличие пост‑фактумной рационализации (RAT‑индекс > 0.3) повышает "
            "конформность и снижает predictive accuracy суждений против baseline "
            "без рационализации."
        ),
        "H-025": (
            "Асимметрия дуальности (H‑023) воспроизводится на ≥3 синтетических "
            "корпусах, представляющих различные культурные контексты, с низкой "
            "гетерогенностью (I² < 50 %)."
        ),
        "H-026": (
            "Тип рационализации (натурализующая vs социализирующая) коррелирует "
            "с онтологической позицией (essentialist vs constructionist) с cosine "
            "similarity ≥ 0.6."
        ),
    }

    for exp_id, hyp_id in [("EXP-023", "H-023"), ("EXP-024", "H-024"),
                            ("EXP-025", "H-025"), ("EXP-026", "H-026")]:
        key = hyp_id.lower().replace("-", "_")
        _write_skeleton_report(
            reports_dir / f"{exp_id}-report.md",
            exp_id,
            hyp_id,
            h_statements[hyp_id],
            results.get(key, {"verdict": "pending", "note": "Not yet computed"}),
        )


# ────────────────────────────────────────────────────────────────────────────
# Main
# ────────────────────────────────────────────────────────────────────────────


def main() -> dict[str, Any]:
    """Run the full preregistered protocol. Returns all results."""
    print(f"DUALITY Protocol — Preregistered {PREREG_TIMESTAMP}")
    print(f"Seed: 0x{PROTOCOL_SEED:X}  Templates: {len(_W_TEMPLATES)}/corpus  "
          f"Judgments: {N_JUDGMENTS_PER_CORPUS * 3} total")
    print("=" * 60)

    # 1. Generate dataset
    print("\n[1/5] Generating preregistered dataset …")
    dataset = generate_dataset()
    print(f"  → {len(dataset)} judgments generated")

    # 2. H‑023
    print("\n[2/5] Testing H‑023 (D‑axis asymmetry) …")
    h023 = test_h023(dataset)
    print(f"  → Verdict: {h023['verdict']}")
    for c in CORPUS_NAMES:
        r = h023["per_corpus"][c]
        print(f"     {c}: M={r['n_masculine']} F={r['n_feminine']} N={r['n_neutral']} "
              f"χ²={r['chi2']:.2f} p={r['p_chi2']:.4f} h={r['cohens_h']:.3f}")

    # 3. H‑024
    print("\n[3/5] Testing H‑024 (RAT‑index → conformity/accuracy) …")
    h024 = test_h024(dataset)
    print(f"  → Verdict: {h024['verdict']}")
    print(f"     ρ={h024['spearman_rho']['rho']:.4f} p={h024['spearman_rho']['p_value']:.4f} "
          f"ΔAcc={h024['delta_accuracy']:.4f}")

    # 4. H‑025
    print("\n[4/5] Testing H‑025 (cross‑cultural reproducibility) …")
    h025 = test_h025(dataset)
    print(f"  → Verdict: {h025['verdict']}")
    if "meta_analysis" in h025:
        ma = h025["meta_analysis"]
        print(f"     I²={ma['i2_percent']:.1f}%  Q={ma['cochrans_q']:.2f}  "
              f"θ_random={ma['theta_random']:.3f}")

    # 5. H‑026
    print("\n[5/5] Testing H‑026 (rationalization × ontology vector correlation) …")
    h026 = test_h026(dataset)
    print(f"  → Verdict: {h026['verdict']}")
    print(f"     cos={h026['cosine_similarity']:.4f}  p_perm={h026['permutation_p']:.4f}")

    # Baselines
    print("\n[Baselines]")
    y_true_all = np.array([j.ground_truth_decision for j in dataset], dtype=np.int32)
    rand_auc = baseline_random_auc(y_true_all)
    maj_acc = baseline_majority_accuracy(y_true_all)
    print(f"  Random AUC: {rand_auc['auc_mean']:.3f} [{rand_auc['auc_ci_low']:.3f}, "
          f"{rand_auc['auc_ci_high']:.3f}]")
    print(f"  Majority Accuracy: {maj_acc['accuracy']:.3f}")

    # Collect results
    results = {
        "protocol": "DUALITY",
        "preregistration_timestamp": PREREG_TIMESTAMP,
        "seed_hex": f"0x{PROTOCOL_SEED:X}",
        "n_total_judgments": len(dataset),
        "baselines": {
            "random_auc": rand_auc,
            "majority": maj_acc,
        },
        "h_023": h023,
        "h_024": h024,
        "h_025": h025,
        "h_026": h026,
    }

    # Write JSON
    reports_dir = Path(__file__).resolve().parent.parent / "reports"
    reports_dir.mkdir(parents=True, exist_ok=True)
    json_path = reports_dir / "EXP-023-report.json"
    json_path.write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"\n  Results saved → {json_path}")

    # Generate skeleton markdown reports
    generate_reports(dataset, results)
    for exp_id in ["EXP-023", "EXP-024", "EXP-025", "EXP-026"]:
        print(f"  Skeleton report → reports/{exp_id}-report.md")

    print("\n" + "=" * 60)
    print("Protocol complete. Verdicts (synthetic data):")
    for hyp_id, res_key in [("H-023", "h_023"), ("H-024", "h_024"),
                              ("H-025", "h_025"), ("H-026", "h_026")]:
        v = results[res_key].get("verdict", "N/A")
        print(f"  {hyp_id}: {v}")
    print("\n⚠ All verdicts are based on preregistered SYNTHETIC data.")
    print("  Real‑data verdicts will be recorded in a follow‑up run.")

    return results


if __name__ == "__main__":
    main()
