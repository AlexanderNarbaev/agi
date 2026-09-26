# DESIGN-NN — Glushkov Algebraic Automaton (R-C Soviet cybernetics)

> TRUE-W11 research-engine iteration #8.
> Domain: Soviet / Ukrainian cybernetics.
> Source: Glushkov, V.M. (1961) "Synthesis of digital automata"

## Goal

Implement a Glushkov-style **algebraic automaton** for matching
mind patterns. The automaton is a finite-state machine where:
- States = pattern positions
- Transitions = labelled by tokens
- A pattern is a regular expression over a token alphabet

The Glushkov construction gives a clean way to compile a regex-like
pattern to an automaton that matches in O(n) on the input length,
with the pattern compiled as an algebraic expression.

## Status (TRUE-W11 iteration #8)

- [x] DESIGN-NN drafted
- [x] Prototype: GlushkovAutomaton (compile regex-like pattern,
                                   match tokens in O(n))
- [ ] Integration into BirInferenceStage (deferred)
