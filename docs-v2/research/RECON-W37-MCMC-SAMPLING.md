# RECON-W37 — MCMC / Gibbs sampling vs the current Tsetlin exploration

**Status:** recorded 2026-10-17, RECON-W38.0 (Meta-R1 compliance sweep)
**Question raised by the operator:** W37's sleep-consolidation waves spoke of
"stochastic exploration" of the Tsetlin clause space. Is the system doing MCMC
(at all), or is that language ahead of the code?

## Finding

**There is no MCMC sampler in the shipped training path. The current learning
loop for Tsetlin clauses is deterministic local search (hill-climbing).**

Ground truth, by source:

- `EXP-MATRIX.12` (experiments ledger): the Tsetlin learning rule is trained by
  a hill-climbing-style refine step over clause feedback — promote/demote
  single-literal flips — not by a Gibbs sweep over a posterior.
- `docs-v2/releases/MATRIX-MIND-REPORT-V18.md` (v18.0.0-mind Connectome Alpha,
  line ~160) states explicitly: **"Exploration is local search, not Gibbs
  sampling."** The release report was checked against the code before
  publication (RECON-W37.5 cross-validation).

## Why this matters (and stays on the ledger)

- The W37 sleep-consolidation results (worst-fold cross-validation,
  mutation-verified) were produced by deterministic local search. Any claim
  that "Gibbs-style" exploration produced them would be false attribution.
- A true stochastic sampler has different convergence semantics: it can accept
  a temporarily worse clause configuration (annealing schedule, temperature),
  which local search cannot do without a restart mechanism.

## Disposition — RECON-W38.3 (scheduled)

W38.3 introduces a Gibbs sampler for Tsetlin clause exploration with
**seeded determinism** (operator standing rule §3: stochastic paths seeded; an
unseeded stochastic call fails the build). Parity target: k≥7 clause-space
convergence compared against the hill-climb baseline. Until then:

- any statement that the shipped system performs MCMC is **UNVERIFIED → false**
- the honest description of the shipped system: *seeded hill-climb refinement
  under sleep consolidation*; see EXP-MATRIX.12 and V18 §Exploration.

META-R2 record: this document is the required application binder; the doctrine
file `MATRIX-CROSS-DISCIPLINARY-RESEARCH.md` is not modified by W37 because the
finding retro-describes existing behaviour rather than introducing a new
algorithm. The algorithm introduction itself is gated on W38.3 landing.
