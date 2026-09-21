# EXP-024 — Preregistered Skeleton Report

**PREREGISTRATION TIMESTAMP:** 2026-08-10
**Status:** proposed
**Hypothesis:** H-024

---

## Hypothesis Statement

> Наличие пост‑фактумной рационализации (RAT‑индекс > 0.3) повышает конформность и снижает predictive accuracy суждений против baseline без рационализации.

---

## Preregistered Metrics

(See `docs/research/reports/EXP-023-card.md` §1 for full operational definitions.)

---

## Results

**STATUS: PENDING** — real data not yet collected. Synthetic preregistered run below.

```json
{
  "spearman_rho": {
    "rho": 0.045839182886619124,
    "p_value": 0.2622563781553627,
    "ci_low": -0.03304982487941001,
    "ci_high": 0.12254481852114331
  },
  "median_rat": 0.17588774981059654,
  "accuracy_low_rat": 0.5933333333333334,
  "accuracy_high_rat": 0.5866666666666667,
  "delta_accuracy": 0.00666666666666671,
  "verdict": "rejected"
}
```

---

## Acceptance / Rejection

**Verdict (synthetic run):** `rejected`

> ⚠ This verdict is based on preregistered SYNTHETIC data only.
> Real‑data verdict will be recorded after data collection.

---

## Changelog

| Date | Change | Author |
|------|--------|--------|
| 2026-08-10 | Preregistration skeleton created | duality_protocol.py |
