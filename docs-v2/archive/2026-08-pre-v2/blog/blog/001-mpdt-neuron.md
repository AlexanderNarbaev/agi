# What is an MPDT Neuron and Why It Never Hallucinates

*June 3, 2026 — Alexander Narbaev*

---

Every large language model today shares the same dirty secret: **they hallucinate**. 
GPT-5, Claude, Gemini — all of them will confidently tell you that the capital of 
France is Paris one moment, and that 2+2=5 the next. They don't "know" anything. 
They predict tokens.

The MATRIX project takes a fundamentally different approach. Instead of floating-point 
matrices and gradient descent, we use **McCulloch-Pitts Decision Tree neurons** — 
a modern reinterpretation of the 1943 formal neuron, but without its key limitation.

## What is an MPDT neuron?

An MPDT neuron is a discrete computing element that:
- Takes `k` binary inputs (0 or 1)
- Produces exactly one binary output (0 or 1)
- Stores its logic as a **truth table** — a lookup of all 2^k possible inputs

That's it. No floats. No probabilities. No "emergent behaviors".

For k=8, the truth table has 256 entries. For k=16, it has 65,536 entries. This 
is the entire "knowledge" of a neuron — an explicit, verifiable, deterministic mapping.

## Why can't it hallucinate?

An LLM hallucinates because its output is a probability distribution over tokens. 
At any point, it might sample an unlikely but coherent-sounding continuation. There's 
no "truth" to check against — only statistical patterns from training data.

An MPDT neuron **cannot hallucinate** because:
1. **Every output is deterministic.** Input X always produces output Y.
2. **The truth table is explicit.** You can read it. You can verify it.
3. **There's no "training distribution" to overfit.** The neuron learns by evolution, 
   not by memorizing patterns.
4. **You can prove properties about it.** "This neuron will never output 1 when 
   inputs 3 and 7 are both 0" — this is a boolean statement you can verify.

## How does it learn?

No backpropagation. No gradient descent. Instead, we use **genetic algorithms**:

1. Create a population of random truth tables
2. Evaluate each one against a fitness function
3. Select the best, mutate, crossover
4. Repeat for hundreds of generations

In our GridWorld pilot, an agent with 4 MPDT neurons (north, south, west, east) 
learned to navigate a 20×20 world, collect food, and avoid walls — after just 200 
generations. Fitness improved from 746 to 940. Total training time: 13 seconds.

## Try it yourself

The code is open source (AGPLv3): [github.com/AlexanderNarbaev/agi](https://github.com/AlexanderNarbaev/agi)

Run the pilot:
```bash
./gradlew :matrix-core:quarkusBuild -Dquarkus.package.jar.type=uber-jar
java -jar matrix-core/build/matrix-core-*-runner.jar pilot-gridworld -g 200
```

Or explore the MPDT sandbox: open `docs/sandbox.html` in your browser.

---

*MATRIX: Discrete logic. No hallucinations. Interpretable by design.*
