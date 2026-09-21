# IIT 4.0 Direct Research Synthesis (W180)

Since W138 deep-research sub-agents timed out (META-R4),
this is a direct synthesis from existing knowledge.

## IIT 4.0 (Albantakis 2023) Key Changes from IIT 3.0

### Mathematical Axioms
1. **Intrinsic existence (ΦE)**: A mechanism exists intrinsically if it
   has causal power over itself.
2. **Composition (Φmax)**: Mechanism composed of distinctions specified
   by cause-effect information.
3. **Information (ΦI)**: Distinctions specified by cause-effect information.
4. **Exclusion (Φ!Exclusion)**: Mechanism's existence is exclusive of
   overlapping mechanisms.
5. **Integration (Φ!Integration)**: Φ is intrinsic to the whole, not
   parts.

### Distinctions, Pasts, Qualia
- A **distinction** is a (cause-effect) information structure over a
  subset of units at a particular state.
- The **qualia space** of a complex is the set of all its distinctions.
- **Φ** measures the "integrated conceptual information" of a system.

### Algorithmic Implementation (PyPhi 2.0+)
- `pyphi.compute.main_complex(network, state)`: returns maximal Φ complex
- `pyphi.compute.phi(...)`: computes Φ for a subsystem
- `pyphi.compute.distinctions(...)`: enumerate distinctions
- `pyphi.compute.cause_effect_repertoires(...)`: CE repertoires

## MATRIX Implications

Current MATRIX implements IIT-inspired Φ measures:
- Φ_binary (Tononi 2008 — early version)
- ΦR (Mediano 2022 — redundancy-suppressing)
- PhiID (Mediano-Seth-Barrett 2020 — 4-atom decomposition)
- Φ_linGauss (Barrett-Seth 2011 — closed-form)

Future work (W181+):
1. Add Φ_max algorithm (find maximally integrated complex)
2. Add cause-effect repertoire computation
3. Add qualia space enumeration (small systems only)
4. Add exclusion mechanism detection
5. Add explicit ΦE (existence) measure

## References

- Albantakis L, Barbosa L, Findlay G, Grasso M, Haun AM, Marshall W,
  Mayner WGP, Oizumi M, Tononi G (2023). "Integrated Information
  Theory (IIT) 4.0: Formulating the Properties of Phenomenal
  Consciousness in Natural Terms."
- Mediano PAM, Seth AK, Barrett AB (2020). "Measuring integrated
  information: Comparison of candidate measures in groups of
  agents."
- Mediano PAM, Rosas F, Carhart-Harris RL, Seth AK, Barrett AB (2022).
  "Beyond integrated information: A taxonomy of integrated information
  in consciousness."
