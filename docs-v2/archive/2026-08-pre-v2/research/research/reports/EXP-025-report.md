# EXP-025 — Preregistered Skeleton Report

**PREREGISTRATION TIMESTAMP:** 2026-08-10
**Status:** proposed
**Hypothesis:** H-025

---

## Hypothesis Statement

> Асимметрия дуальности (H‑023) воспроизводится на ≥3 синтетических корпусах, представляющих различные культурные контексты, с низкой гетерогенностью (I² < 50 %).

---

## Preregistered Metrics

(See `docs/research/reports/EXP-023-card.md` §1 for full operational definitions.)

---

## Results

**STATUS: PENDING** — real data not yet collected. Synthetic preregistered run below.

```json
{
  "per_corpus": {
    "modernist_western": {
      "cohens_h": 0.0,
      "var": 0.02,
      "n_effective": 200
    },
    "collectivist_east": {
      "cohens_h": 0.0,
      "var": 0.02,
      "n_effective": 200
    },
    "traditional_african": {
      "cohens_h": 0.0,
      "var": 0.02,
      "n_effective": 200
    }
  },
  "meta_analysis": {
    "k": 3,
    "cochrans_q": 0.0,
    "p_q": 1.0,
    "i2_percent": 0.0,
    "tau2": 0.0,
    "theta_fixed": 0.0,
    "theta_random": 0.0,
    "se_random": 0.08164965809277261,
    "ci_low_random": -0.1600333298618343,
    "ci_high_random": 0.1600333298618343,
    "same_direction": true
  },
  "verdict": "accepted"
}
```

---

## Acceptance / Rejection

**Verdict (synthetic run):** `accepted`

> ⚠ This verdict is based on preregistered SYNTHETIC data only.
> Real‑data verdict will be recorded after data collection.

---

## Changelog

| Date | Change | Author |
|------|--------|--------|
| 2026-08-10 | Preregistration skeleton created | duality_protocol.py |
