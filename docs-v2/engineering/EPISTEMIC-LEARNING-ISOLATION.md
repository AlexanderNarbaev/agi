# EPISTEMIC-LEARNING-ISOLATION — the mechanism and its evidence

**Status:** `ACTIVE` — being written by the data-integrity workstream of the EPISTEMIC-INTEGRITY
initiative; the mechanism, the tests and the measured numbers are added as they are executed.
**Date:** 2026-11-15.

**Why this document exists.** The owner's directive, P0.4: *evaluation, probe and synthetic
records must never enter durable organic learning by accident, and the already-contaminated
records must remain auditable.* The measured state it answers: two frozen evaluation probes and
59 deliberately-constructed collection episodes entered the machine's real episodic log
(`data/mind/episodic.ndjson`, 108 → 167 records), and the learning window then trained on them.

**What this document will contain:** the provenance tag that travels with a record through the
production write path, the admission gate that keeps non-organic records out of the learning
window while leaving them in the durable log, the reversible quarantine of the already
contaminated records, the leakage test that proves no evaluation record can reach the induction
input, and the measured before/after of the window and of the induction fidelity.
