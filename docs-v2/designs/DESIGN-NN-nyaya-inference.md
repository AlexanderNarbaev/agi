# DESIGN-NN — Nyaya Inference (R-C Asian / Indian logic)

> TRUE-W11 research-engine iteration #7.
> Domain: Asian (Indian) logic school.
> Source: Nyaya Sutra (Aksapada, ~2nd century CE) — "5-membered syllogism"

## Goal

Implement the Nyaya **5-membered syllogism** (pañcāvayava) as a
soundness check for mind reasoning chains:

1. **Pratijñā** — proposition (e.g. "X is Y")
2. **Hetu** — reason (e.g. "because Z")
3. **Dṛṣṭānta** — example (e.g. "this is known from analogous cases")
4. **Upanaya** — application (e.g. "therefore in this case too")
5. **Nigamana** — conclusion (e.g. "hence X is Y")

A 5-membered syllogism is *more rigorous* than Western 3-membered
syllogism because the example + application explicitly handle
inductive/analogical reasoning. This is exactly the mind's task
(analogy stage) and gives a structured soundness check.

## Status (TRUE-W11 iteration 7)

- [x] DESIGN-NN drafted
- [x] Prototype: NyayaSyllogism (5-step structure validator)
- [ ] Integration into AnalogyStage (deferred)
