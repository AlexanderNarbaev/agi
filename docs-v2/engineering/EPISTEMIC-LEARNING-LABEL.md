# EPISTEMIC-LEARNING-LABEL — the meaning of the learning label

**Status:** `ACTIVE` — being written by the P3 workstream of the EPISTEMIC-INTEGRITY initiative.
**Date:** 2026-11-16.

**Why this document exists.** The owner's directive, P3: *"confidence computed from retrieval overlap is
not independent task correctness. Add a research-only independently verified outcome/utility signal
where possible (task success, error, cost, validated feedback) without laundering gold labels into the
feature. Distinguish prediction, validation, learning and evaluation provenance."* Today's label is
`confidence >= 0.7 AND accepted` — a measure of the pipeline, not of whether the task was right.

**What this document will contain:** the design of the independently verified signal (what it measures,
how it is obtained without the evaluation's gold answers, and where its provenance is recorded); the
comparison of the original feature, the fold, the informative/discriminative projections and the simple
baselines under leakage-controlled partitions; the positive control built from actual observations and
the contamination check; the acquisition cost and the cross-task generalization; and the **negative
results**, published as such.

**What is fixed and not negotiable here:** `K_MAX = 20`, the trainer width and the production fold are
retained until an owner-approved change.
