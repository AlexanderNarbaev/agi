# DESIGN-NN — Stigmergy Routing (R-E biology / mycelium)

> TRUE-W11 research-engine iteration #9.
> Domain: biological network algorithms.
> Source: Bonabeau, E. et al. (1999) "Swarm Intelligence"

## Goal

Implement a stigmergy-inspired routing algorithm where mind-modules
leave "pheromone" markers on pathways they use, and the mind's
router follows the strongest-trail pathways. Pheromones evaporate
over time, so unused pathways fade and new paths can emerge.

## Why It Might Help MATRIX

1. **Adaptive routing**: Currently `TrueMindCycle` runs all 12 stages
   in fixed order. Stigmergy lets the mind learn which stages are
   most useful for which input classes.
2. **Decentralised**: Pheromones are local to each pathway; no
   global coordination needed.
3. **Exploration/exploitation**: Pheromones can be stochastically
   ignored to allow new paths to be discovered.

## Status (TRUE-W11 iteration #9)

- [x] DESIGN-NN drafted
- [x] Prototype: StigmergyRouter (evaporating pheromones)
- [ ] Integration into TrueMindCycle router (deferred)
