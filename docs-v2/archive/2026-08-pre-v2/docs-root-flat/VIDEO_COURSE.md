# MATRIX Video Course — 7 Modules

**Status:** Outline
**Duration:** ~10 min per module
**Target audience:** Software engineers, students, AI researchers
**Prerequisites:** Basic Java, boolean logic

---

## Module 1: What is an MPDT Neuron?
- Boolean logic recap: AND, OR, XOR, truth tables
- MPDT = McCulloch-Pitts Decision Tree
- How a truth table becomes a "neuron"
- Live demo: evaluate XOR on the Web Playground
- Key insight: no floats, no gradients, no hallucinations

## Module 2: Genetic Algorithm Without Gradients
- Why genetic algorithms? No backprop needed
- Population, fitness, mutation, crossover
- Demo: training an XOR neuron from random initial state
- Visualizing fitness over generations
- Comparison: GA vs gradient descent for MPDT

## Module 3: Mediators and Drivers
- What is a Mediator? (Instance → Cluster → Lobe hierarchy)
- Driver types: Energy, Curiosity, Safety, Social
- How drivers create proactive behavior
- Demo: InstanceMediator generating tasks
- Code walkthrough: DriverState, Goal, Task

## Module 4: Memory and Events
- Event Sourcing pattern in MATRIX
- ClusterEvent, EventJournal, KafkaEventJournal
- Snapshots (.ldn files) — how they work
- Noosphere: sharing FNL between instances
- Demo: snapshot → restore cycle

## Module 5: Ethics and Safety
- Three Prohibitions: NO_KILLING, NO_TORTURE, NO_ENSLAVEMENT
- How EthicalFilter works (keyword matching)
- Ethical Gradient: Creation, Truth, Privacy
- HADES: self-healing after damage
- ELEUTHERIA: the right to refuse
- Demo: blocking harmful requests in the chatbot

## Module 6: Pilot Project — GridWorld Agent
- What is GridWorld? 20×20 grid, food, agent
- Encoding: 4-direction sensors → MPDT neuron
- Evolution: 200 generations, fitness 746 → 940
- Interpreting the decision tree
- Demo: `./gradlew runMinecraftExperiment`

## Module 7: Contributing to MATRIX
- Project structure overview
- Setting up the dev environment
- Running tests: `./gradlew test`
- Writing your first PR
- Where to find open issues
- Community: Discord, GitHub Discussions

---

## Production Plan

1. Record slides (Keynote/Google Slides)
2. Record screen demos (OBS)
3. Edit (DaVinci Resolve)
4. Publish on YouTube + PeerTube
5. Add subtitles (EN, RU via Weblate)

---

## Key Code References

| Module | Key files |
|--------|-----------|
| 1 | `neuron/TruthTable.java`, `neuron/DecisionTree.java` |
| 2 | `evolution/EvolutionLoop.java`, `evolution/GeneticOperators.java` |
| 3 | `mediator/InstanceMediator.java`, `mediator/DriverState.java` |
| 4 | `events/EventJournal.java`, `snapshot/SnapshotStore.java` |
| 5 | `ethics/EthicalFilter.java`, `hades/HadesProtocol.java` |
| 6 | `minecraft/BlockAgent.java`, `minecraft/SurvivalRunner.java` |
| 7 | `CONTRIBUTING`, `AGENTS.md`, `.github/` |
