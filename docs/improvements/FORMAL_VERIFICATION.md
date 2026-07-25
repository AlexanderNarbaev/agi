# Formal Verification — Implementation Plan

**Status:** ⏳ NEXT (Phase 3)
**Priority:** HIGH
**Estimated effort:** 4-6 weeks
**Target:** v3.58

---

## Problem Statement

Current TLA+ specs exist but aren't verified against implementation. Need:
1. Model checking all 5 TLA+ specs
2. Runtime verification of critical properties
3. Property-based testing
4. Continuous verification in CI

---

## Implementation Steps

### Step 1: TLA+ Model Checking (Week 1-2)
```bash
# Install TLA+ Toolbox
# https://lamport.azurewebsites.net/tla/toolbox.html

# Check MPDTNeuron.tla
tlc2 -config formal/MPDTNeuron.cfg formal/MPDTNeuron.tla

# Check Consensus.tla
tlc2 -config formal/Consensus.cfg formal/Consensus.tla

# Check FrozenEthicalFNL.tla
tlc2 -config formal/FrozenEthicalFNL.cfg formal/FrozenEthicalFNL.tla
```

### Step 2: Runtime Verification (Week 2-3)
```java
// matrix-core/src/main/java/io/matrix/verification/RuntimeVerifier.java
@ApplicationScoped
public class RuntimeVerifier {
    
    private final List<PropertyChecker> checkers = new ArrayList<>();
    
    @PostConstruct
    void init() {
        // Add property checkers
        checkers.add(new TruthTableConsistencyChecker());
        checkers.add(new EvolutionMonotonicityChecker());
        checkers.add(new EthicsInvariantChecker());
        checkers.add(new ConsensusSafetyChecker());
    }
    
    public VerificationResult verify(Object state) {
        List<PropertyViolation> violations = new ArrayList<>();
        
        for (PropertyChecker checker : checkers) {
            Optional<PropertyViolation> violation = checker.check(state);
            violation.ifPresent(violations::add);
        }
        
        return new VerificationResult(violations);
    }
}

// Property checkers
public class TruthTableConsistencyChecker implements PropertyChecker {
    @Override
    public Optional<PropertyViolation> check(Object state) {
        if (state instanceof TruthTable tt) {
            // Property: All outputs must be 0 or 1
            for (int i = 0; i < tt.size(); i++) {
                int output = tt.evaluate(i);
                if (output != 0 && output != 1) {
                    return Optional.of(new PropertyViolation(
                        "TRUTH_TABLE_INVALID_OUTPUT",
                        "Output must be 0 or 1, got: " + output
                    ));
                }
            }
        }
        return Optional.empty();
    }
}
```

### Step 3: Property-Based Testing (Week 3-4)
```java
// matrix-core/src/test/java/io/matrix/verification/PropertyBasedTest.java
@QuarkusTest
class PropertyBasedTest {
    
    @Test
    void truthTableAlwaysBinary() {
        // Property: For any TruthTable, all outputs are 0 or 1
        forAll(genTruthTable()).check(tt -> {
            for (int i = 0; i < (1 << tt.getK()); i++) {
                int output = tt.evaluate(i);
                return output == 0 || output == 1;
            }
            return true;
        });
    }
    
    @Test
    void evolutionNeverDecreasesFitness() {
        // Property: Evolution never decreases best fitness
        Population pop = createRandomPopulation();
        double bestFitness = pop.getBestFitness();
        
        for (int gen = 0; gen < 100; gen++) {
            pop = pop.evolve();
            double newBest = pop.getBestFitness();
            assertTrue(newBest >= bestFitness,
                "Fitness decreased from " + bestFitness + " to " + newBest);
            bestFitness = newBest;
        }
    }
    
    @Test
    void frozenNeuronsNeverChange() {
        // Property: FROZEN neurons are immutable
        FrozenEthicalFNL frozen = FrozenEthicalFNL.getInstance();
        Set<String> originalNames = frozen.getNeuronNames();
        
        // Try to modify (should fail)
        assertThrows(UnsupportedOperationException.class, () -> {
            frozen.getNeurons().add(new FrozenAxiomNeuron("NEW"));
        });
        
        // Verify unchanged
        assertEquals(originalNames, frozen.getNeuronNames());
    }
}
```

### Step 4: CI Integration (Week 4-5)
```yaml
# .github/workflows/formal-verification.yml
name: Formal Verification
on: [push, pull_request]

jobs:
  tla-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Install TLA+ Toolbox
        run: |
          wget https://github.com/tlaplus/tlaplus/releases/download/v1.8.0/tla-toolbox-1.8.0-linux.gtk.x86_64.zip
          unzip tla-toolbox-1.8.0-linux.gtk.x86_64.zip
          
      - name: Check MPDTNeuron
        run: ./tlc2 formal/MPDTNeuron.tla
        
      - name: Check Consensus
        run: ./tlc2 formal/Consensus.tla
        
      - name: Check FrozenEthicalFNL
        run: ./tlc2 formal/FrozenEthicalFNL.tla
  
  property-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
          
      - name: Run property-based tests
        run: ./gradlew :matrix-core:test --tests "*PropertyBasedTest"
```

### Step 5: Violation Reporting (Week 5)
```java
// matrix-core/src/main/java/io/matrix/verification/PropertyViolation.java
public record PropertyViolation(
    String property,
    String description,
    Object context,
    Instant timestamp
) {
    public String toReport() {
        return String.format(
            "[%s] Property '%s' violated: %s\nContext: %s",
            timestamp, property, description, context
        );
    }
}

// matrix-core/src/main/java/io/matrix/verification/VerificationReport.java
@ApplicationScoped
public class VerificationReport {
    
    @Inject
    KafkaEventJournal journal;
    
    public void reportViolation(PropertyViolation violation) {
        // Log violation
        log.error("Property violation: {}", violation.toReport());
        
        // Publish event
        journal.publish(new ClusterEvent(
            ClusterEventType.VERIFICATION_VIOLATION,
            violation.toReport()
        ));
        
        // Alert if critical
        if (isCritical(violation)) {
            alertOperator(violation);
        }
    }
}
```

### Step 6: Continuous Monitoring (Week 6)
```java
// matrix-core/src/main/java/io/matrix/verification/ContinuousVerifier.java
@ApplicationScoped
public class ContinuousVerifier {
    
    @Inject
    RuntimeVerifier verifier;
    
    @Scheduled(every = "5m")
    void periodicVerification() {
        // Verify current system state
        VerificationResult result = verifier.verify(getCurrentState());
        
        if (!result.isPassing()) {
            log.error("Continuous verification failed: {}", result.getViolations());
            // Don't halt system, but alert
        }
    }
}
```

---

## Properties to Verify

| Property | Spec | Description |
|----------|------|-------------|
| TruthTable binary output | MPDTNeuron | All outputs ∈ {0, 1} |
| Evolution monotonicity | Consensus | Best fitness never decreases |
| FROZEN immutability | FrozenEthicalFNL | FROZEN neurons cannot be modified |
| Consensus safety | Consensus | No two different values agreed |
| Byzantine fault tolerance | Consensus | System tolerates f < n/3 faults |

---

## Verification

```bash
# Run TLA+ model checking
tlc2 formal/MPDTNeuron.tla

# Run property-based tests
./gradlew :matrix-core:test --tests "*PropertyBasedTest"

# Run runtime verification
./gradlew :matrix-core:test --tests "*RuntimeVerifierTest"
```
