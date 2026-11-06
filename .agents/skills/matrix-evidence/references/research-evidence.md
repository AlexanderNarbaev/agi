# Research evidence

## The hypothesis register

`docs-v2/research/HYPOTHESES.md` and `docs-v2/research/HYPOTHESES-NEW.md`. A claim that has
no entry in a register is **not a project hypothesis yet** — it is an observation, and it
says so.

Each entry carries: the identifier, the statement, the falsification condition, the
artefact, and the scope label (`accepted (synthetic-scope)`, `running`, `proposed`,
`refuted-toy`). **The scope label is part of the claim.** "Accepted in synthetic scope" is
not "accepted".

## The experiment report

The reports live under `docs-v2/research/reports/`, and the shape is the same for each: the
hypothesis it tests, the setup, the measurement, the result, and the limits of the result.
The index of the research body is `docs-v2/research/RESEARCH-INDEX.md`, and the **refuted**
and abandoned material is indexed in `docs-v2/research/NEGATIVE-RESULTS-INDEX.md` — a
negative result that is not discoverable is a negative result that will be re-run.

## The preregistration rule

A measurement is registered before it is taken: the protocol states the hypothesis, the
setup, the sample size and the falsification condition, and the report then says whether
the condition was met. The protocols live under `docs-v2/research/protocols/`.

**A measurement without a preregistration is a measurement, not evidence.**

## The primary-source rule

Every substantial claim is tied to a primary source — the original paper, the original book
or article, the official documentation. **An aggregator is not a source.** A claim that
cannot be sourced is marked `UNVERIFIED` rather than dropped, and the mark is visible.

The project's own doctrine is `docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md`, and
its rule for persisting a finding is that it is written into the doctrine, into a design
document and into the hypothesis register — **a finding that exists only in the chat is
lost**.

## The scope discipline

A cross-disciplinary finding is not local-optimal by construction: the project's rule
requires that a new algorithmic decision is checked against several schools of thought at
once. When a claim is used to justify a design, say which schools it was checked against,
and name the ones that were not.
