# EXP-023 — Preregistered Skeleton Report

**PREREGISTRATION TIMESTAMP:** 2026-08-10
**Status:** proposed
**Hypothesis:** H-023

---

## Hypothesis Statement

> Дуальность мира как пара мужское/женское проявляется в операциональной асимметрии суждений: распределение суждений по дуальной оси статистически значимо отклоняется от равномерного.

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
      "n_masculine": 100,
      "n_feminine": 100,
      "n_neutral": 0,
      "chi2": 100.0,
      "p_chi2": 0.0,
      "cohens_h": 0.0,
      "cohens_h_ci_low": -0.2809228294197115,
      "cohens_h_ci_high": 0.2809228294197115,
      "auc": 0.5200722601364913,
      "effect_direction": "feminine"
    },
    "collectivist_east": {
      "n_masculine": 100,
      "n_feminine": 100,
      "n_neutral": 0,
      "chi2": 100.0,
      "p_chi2": 0.0,
      "cohens_h": 0.0,
      "cohens_h_ci_low": -0.2809228294197115,
      "cohens_h_ci_high": 0.2809228294197115,
      "auc": 0.5,
      "effect_direction": "feminine"
    },
    "traditional_african": {
      "n_masculine": 100,
      "n_feminine": 100,
      "n_neutral": 0,
      "chi2": 100.0,
      "p_chi2": 0.0,
      "cohens_h": 0.0,
      "cohens_h_ci_low": -0.2809228294197115,
      "cohens_h_ci_high": 0.2809228294197115,
      "auc": 0.4797979797979798,
      "effect_direction": "feminine"
    }
  },
  "fdr": [
    {
      "corpus": "modernist_western",
      "p_raw": 0.0,
      "rejected": 0
    },
    {
      "corpus": "collectivist_east",
      "p_raw": 0.0,
      "rejected": 1
    },
    {
      "corpus": "traditional_african",
      "p_raw": 0.0,
      "rejected": 2
    }
  ],
  "n_significant_after_fdr": 3,
  "same_direction": true,
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
