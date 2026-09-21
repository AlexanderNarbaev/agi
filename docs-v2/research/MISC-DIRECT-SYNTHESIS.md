# Misc Direct Research Synthesis (W181)

Direct synthesis replacing timed-out sub-agents (W138).

## 1. Free Energy Principle (Friston 2010, 2019)

### Core idea
- Biological systems minimize variational free energy (VFE)
- VFE bounds surprise under posterior approximation
- Self-organization = Markov blanket + active inference

### Equations
- VFE = E_q[log q(s) - log p(o,s)] = -ELBO
- Expected free energy (EFE) = epistemic value + pragmatic value
- G = -E_q[log p(o|π)] - E_q[KL(q(s|o,π) || p(s|π))]
- Active inference: select actions minimizing G

### pymdp Library
- Python implementation of active inference
- State factors, observations, policies
- Continuous and discrete versions
- https://github.com/infer-actively/pymdp

### MATRIX Integration
- Add `VariationalFreeEnergy` class
- Compute VFE for cognitive states
- Use G as action selection criterion

## 2. Causal Emergence (Hoel 2013, Mediano 2022)

### Core idea
- Macro-scale descriptions can be MORE causal than micro-scale
- Φ_CE: causal emergence measure = EI_macro - EI_micro
- Coarse-graining that preserves causal structure

### Equations
- EI(P) = Σ_i P(i) log[P(cause_i)/P_max(cause_i)]
- Φ_CE = EI(P_macro) - EI(P_micro)
- Positive Φ_CE → macro > micro

### Ising Model Example
- 1D Ising at critical temp: Φ_CE > 0 (macro wins)
- Below critical temp: Φ_CE ≈ 0 (micro and macro similar)
- Above critical temp: Φ_CE < 0 (micro wins)

### MATRIX Integration
- Add `CausalEmergence.measure(microStates, macroStates)`
- Use NKBooleanNetwork as test substrate
- Compute Φ_CE for varying K and N

## 3. Attention Φ (transformers)

### Multi-Head Self-Attention (Vaswani 2017)
- Attention(Q, K, V) = softmax(QK^T / sqrt(d_k)) V
- Multi-head: parallel attention layers

### Attention and Integration
- Attention as selective integration mechanism
- Empirical: attention heads specialize (induction, prefix, etc.)
- Φ_attention could measure integration within attention pattern

### MATRIX Integration (not direct)
- MATRIX uses HdcBrain (not transformer)
- Could add attention-like selection to HDC operations
- Φ measures could guide attention selection

## 4. Spiking Neural Net Libraries

| Library | Language | Best for | Maturity |
|---|---|---|---|
| Nengo / NengoLoihi | Python | Large-scale SNN | Mature |
| Norse | Python/PyTorch | Gradient SNN | Active |
| snnTorch | Python/PyTorch | Surrogate gradient | Active |
| BindsNET | Python | Reward STDP | Active |
| NEST | C++ | Large-scale | Very mature |
| Lava | Python/C++ | Intel neuromorphic | Active |

### MATRIX Substrate Choice
- Current: Discrete state dynamics (NOT spike-based)
- Could add NEST or Nengo integration
- But pure JVM is faster for production

## 5. EEG Integration with Cognitive Architectures

### Hardware Comparison
| Hardware | Channels | Cost | SDK |
|---|---|---|---|
| OpenBCI Cyton | 8/16 | $500-1000 | Open |
| Emotiv EPOC X | 14 | $800 | Open |
| Muse 2 | 4 | $300 | Closed |
| Neurosity Crown | 8 | $1000 | Closed |

### Pipeline
- EEG → preprocessing (band-pass, artifact removal)
- Feature extraction (power bands, ERPs, microstates)
- Cognitive state mapping (Φ as target)
- Closed-loop < 100ms

## CONSTITUTION Compliance

All suggested integrations comply with:
- Article I: seeded RNG, no wall-clock in decision paths
- Article VI: integration as measurement, no phenomenal claims
