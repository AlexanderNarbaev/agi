# EXP-026 — Preregistered Skeleton Report

**PREREGISTRATION TIMESTAMP:** 2026-08-10
**Status:** proposed
**Hypothesis:** H-026

---

## Hypothesis Statement

> Тип рационализации (натурализующая vs социализирующая) коррелирует с онтологической позицией (essentialist vs constructionist) с cosine similarity ≥ 0.6.

---

## Preregistered Metrics

(See `docs/research/reports/EXP-023-card.md` §1 for full operational definitions.)

---

## Results

**STATUS: PENDING** — real data not yet collected. Synthetic preregistered run below.

```json
{
  "cosine_similarity": 0.9992383912666831,
  "cosine_bootstrap_ci": {
    "mean": 0.9968886332369818,
    "ci_low": 0.9849205009423415,
    "ci_high": 0.9999964077030074
  },
  "permutation_p": 1.0,
  "contingency_table": {
    "naturalizing_essentialist": 71,
    "naturalizing_constructionist": 62,
    "socializing_essentialist": 61,
    "socializing_constructionist": 72
  },
  "chi2_statistic": 1.2181139755766621,
  "chi2_p_value": 0.26973110285962126,
  "verdict": "inconclusive"
}
```

---

## Acceptance / Rejection

**Verdict (synthetic run):** `inconclusive`

> ⚠ This verdict is based on preregistered SYNTHETIC data only.
> Real‑data verdict will be recorded after data collection.

---

## Changelog

| Date | Change | Author |
|------|--------|--------|
| 2026-08-10 | Preregistration skeleton created | duality_protocol.py |
