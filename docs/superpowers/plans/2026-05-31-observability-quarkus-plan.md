# MATRIX Observability + Quarkus Migration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate MATRIX to Quarkus 3.24, add Micrometer+OTEL observability, improve simulation fitness, prepare Spigot plugin for real Minecraft launch, and deploy Prometheus+Jaeger+Grafana infrastructure.

**Architecture:** CDI-based DI via Quarkus Arc, Micrometer counters/timers injected into evolution loop/actors/HADES, OpenTelemetry spans via `@WithSpan`, JSON structured logging via `application.properties`, Picocli CLI replacing static main(), docker-compose for monitoring stack.

**Tech Stack:** Quarkus 3.24, Micrometer (Prometheus), OpenTelemetry SDK, SmallRye Health, Picocli, Pekko 1.6.0, Paper API 1.20.4, Docker Compose, Grafana 11.

---

### Task 1: Quarkus build.gradle migration

**Files:**
- Modify: `matrix-core/build.gradle`

- [ ] **Step 1: Replace plugins block with Quarkus**

Replace the entire contents of `matrix-core/build.gradle` with:

```groovy
plugins {
    id 'java'
    id 'jacoco'
    id 'io.quarkus' version '3.24.0'
}

group = 'io.matrix'
version = '1.0.0'

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenCentral()
}

dependencies {
    implementation enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}")
    implementation 'io.quarkus:quarkus-arc'
    implementation 'io.quarkus:quarkus-micrometer-registry-prometheus'
    implementation 'io.quarkus:quarkus-opentelemetry'
    implementation 'io.quarkus:quarkus-logging-json'
    implementation 'io.quarkus:quarkus-smallrye-health'
    implementation 'io.quarkus:quarkus-picocli'

    implementation 'org.apache.avro:avro:1.12.0'

    implementation platform('org.apache.pekko:pekko-bom_2.13:1.6.0')
    implementation 'org.apache.pekko:pekko-actor-typed_2.13'
    implementation 'org.apache.pekko:pekko-serialization-jackson_2.13'
    implementation 'org.apache.pekko:pekko-slf4j_2.13'

    testImplementation 'io.quarkus:quarkus-junit5'
    testImplementation 'org.assertj:assertj-core:3.26.0'
    testImplementation 'org.apache.pekko:pekko-actor-testkit-typed_2.13'
}

tasks.withType(Test) {
    useJUnitPlatform()
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        csv.required = false
        html.required = true
    }
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.70
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify Quarkus resolves**

Run: `./gradlew :matrix-core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add matrix-core/build.gradle
git commit -m "build: migrate matrix-core to Quarkus 3.24 with Micrometer, OTEL, Health, Picocli"
```

---

### Task 2: application.properties — observability config

**Files:**
- Create: `matrix-core/src/main/resources/application.properties`

- [ ] **Step 1: Create application.properties**

```properties
# ─── Logging: JSON structured ───
quarkus.log.console.json=true
quarkus.log.console.json.excluded-keys=sequence,loggerClassName
quarkus.log.file.json.enable=true
quarkus.log.file.json.path=logs/matrix.json
quarkus.log.file.json.rotation.max-file-size=10M
quarkus.log.file.json.rotation.max-backup-index=5
quarkus.log.level=INFO

# ─── OpenTelemetry ───
quarkus.otel.enabled=true
quarkus.otel.exporter.otlp.endpoint=http://localhost:4317
quarkus.otel.service.name=matrix
quarkus.otel.traces.sampler=always_on

# ─── Micrometer / Prometheus ───
quarkus.micrometer.enabled=true
quarkus.micrometer.export.prometheus.enabled=true
quarkus.micrometer.export.prometheus.path=/metrics

# ─── Health ───
quarkus.smallrye-health.root-path=/q/health

# ─── HTTP (metrics endpoint) ───
quarkus.http.port=9091

# ─── Native compilation ───
quarkus.native.additional-build-args=--initialize-at-build-time=org.apache.pekko
quarkus.native.resources.includes=**
```

- [ ] **Step 2: Verify config loads**

Run: `./gradlew :matrix-core:processResources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add matrix-core/src/main/resources/application.properties
git commit -m "feat: add Quarkus observability config — JSON logs, OTEL, Micrometer, Health"
```

---

### Task 3: MatrixMetrics — custom Micrometer metrics

**Files:**
- Create: `matrix-core/src/main/java/io/matrix/observability/MatrixMetrics.java`

- [ ] **Step 1: Create MatrixMetrics class**

```java
package superpowers/src/main/java/io/matrix/observability/MatrixMetrics.java

package io.matrix.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MATRIX-specific Micrometer metrics — counters, gauges, timers
 * for evolution, neurons, HADES, mediators, and survival simulation.
 */
public class MatrixMetrics {

    private final Counter evolutionGenerations;
    private final AtomicLong fitnessBest = new AtomicLong(0);
    private final AtomicLong fitnessAvg = new AtomicLong(0);

    private final AtomicInteger neuronsActive = new AtomicInteger(0);
    private final AtomicInteger neuronsFrozen = new AtomicInteger(0);

    private final Counter actorMessages;
    private final Counter actorErrors;
    private final Timer actorProcessingTime;

    private final Counter hadesAlerts;
    private final Counter hadesIsolations;

    private final AtomicLong driverEnergy = new AtomicLong(0);
    private final AtomicLong driverCuriosity = new AtomicLong(0);
    private final AtomicLong driverSafety = new AtomicLong(0);

    private final Timer neuronEvaluateTime;
    private final Counter survivalRuns;
    private final Timer survivalRunTime;

    public MatrixMetrics(MeterRegistry registry) {
        evolutionGenerations = registry.counter("matrix_evolution_generations_total");
        Gauge.builder("matrix_evolution_fitness_best", fitnessBest, AtomicLong::get)
                .register(registry);
        Gauge.builder("matrix_evolution_fitness_avg", fitnessAvg, AtomicLong::get)
                .register(registry);

        Gauge.builder("matrix_neurons_active", neuronsActive, AtomicInteger::get)
                .register(registry);
        Gauge.builder("matrix_neurons_frozen", neuronsFrozen, AtomicInteger::get)
                .register(registry);

        actorMessages = registry.counter("matrix_actor_messages_total");
        actorErrors = registry.counter("matrix_actor_errors_total");
        actorProcessingTime = registry.timer("matrix_actor_processing_seconds");

        hadesAlerts = registry.counter("matrix_hades_alerts_total");
        hadesIsolations = registry.counter("matrix_hades_isolations_total");

        Gauge.builder("matrix_driver_energy", driverEnergy, AtomicLong::get)
                .register(registry);
        Gauge.builder("matrix_driver_curiosity", driverCuriosity, AtomicLong::get)
                .register(registry);
        Gauge.builder("matrix_driver_safety", driverSafety, AtomicLong::get)
                .register(registry);

        neuronEvaluateTime = registry.timer("matrix_neuron_evaluate_seconds");
        survivalRuns = registry.counter("matrix_survival_runs_total");
        survivalRunTime = registry.timer("matrix_survival_run_seconds");
    }

    public void evolutionGeneration() { evolutionGenerations.increment(); }
    public void fitnessBest(long v) { fitnessBest.set(v); }
    public void fitnessAvg(long v) { fitnessAvg.set(v); }

    public void neuronsActive(int n) { neuronsActive.set(n); }
    public void neuronsFrozen(int n) { neuronsFrozen.set(n); }

    public void actorMessage() { actorMessages.increment(); }
    public void actorError() { actorErrors.increment(); }
    public Timer.Sample startActorTimer() { return Timer.start(); }
    public void stopActorTimer(Timer.Sample sample) { sample.stop(actorProcessingTime); }

    public void hadesAlert() { hadesAlerts.increment(); }
    public void hadesIsolation() { hadesIsolations.increment(); }

    public void driverEnergy(long v) { driverEnergy.set(v); }
    public void driverCuriosity(long v) { driverCuriosity.set(v); }
    public void driverSafety(long v) { driverSafety.set(v); }

    public Timer.Sample startNeuronEval() { return Timer.start(); }
    public void stopNeuronEval(Timer.Sample sample) { sample.stop(neuronEvaluateTime); }

    public void survivalRun() { survivalRuns.increment(); }
    public Timer.Sample startSurvivalRun() { return Timer.start(); }
    public void stopSurvivalRun(Timer.Sample sample) { sample.stop(survivalRunTime); }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :matrix-core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add matrix-core/src/main/java/io/matrix/observability/MatrixMetrics.java
git commit -m "feat: add MatrixMetrics — Micrometer counters, gauges, timers for MATRIX"
```

---

### Task 4: MatrixApplication — Quarkus entry point + CDI producers

**Files:**
- Create: `matrix-core/src/main/java/io/matrix/MatrixApplication.java`

- [ ] **Step 1: Create MatrixApplication with CDI producers**

```java
package io.matrix;

import io.matrix.observability.MatrixMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import picocli.CommandLine;

@QuarkusMain
public class MatrixApplication implements QuarkusApplication {

    @Inject
    CommandLine.IFactory factory;

    @Override
    public int run(String... args) {
        return new CommandLine(new MatrixTopCommand(), factory).execute(args);
    }

    @Produces
    @Singleton
    public ActorSystem<Void> actorSystem() {
        return ActorSystem.create(Behaviors.empty(), "matrix");
    }

    @Produces
    @Singleton
    public MatrixMetrics matrixMetrics(MeterRegistry registry) {
        return new MatrixMetrics(registry);
    }
}
```

- [ ] **Step 2: Create MatrixTopCommand placeholder**

Create `matrix-core/src/main/java/io/matrix/MatrixTopCommand.java`:

```java
package io.matrix;

import io.matrix.cli.SimulateCommand;
import io.matrix.cli.EvolutionCommand;
import io.matrix.cli.DemoCommand;
import picocli.CommandLine.Command;

@Command(name = "matrix", mixinStandardHelpOptions = true,
        subcommands = {SimulateCommand.class, EvolutionCommand.class, DemoCommand.class},
        description = "MATRIX Cognitive Architecture CLI")
public class MatrixTopCommand {}
```

- [ ] **Step 3: Verify Quarkus builds**

Run: `./gradlew :matrix-core:quarkusBuild`
Expected: BUILD SUCCESSFUL (may warn about missing subcommand classes — that's fine for now)

- [ ] **Step 4: Commit**

```bash
git add matrix-core/src/main/java/io/matrix/MatrixApplication.java matrix-core/src/main/java/io/matrix/MatrixTopCommand.java
git commit -m "feat: add MatrixApplication — QuarkusMain entry with CDI ActorSystem + MatrixMetrics producers"
```

---

### Task 5: CLI commands — Simulate, Evolution, Demo

**Files:**
- Create: `matrix-core/src/main/java/io/matrix/cli/SimulateCommand.java`
- Create: `matrix-core/src/main/java/io/matrix/cli/EvolutionCommand.java`
- Create: `matrix-core/src/main/java/io/matrix/cli/DemoCommand.java`

- [ ] **Step 1: Create Democommand**

```java
package io.matrix.cli;

import io.matrix.SystemDemo;
import io.matrix.observability.MatrixMetrics;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import picocli.CommandLine.Command;

@Command(name = "demo", description = "Run full system demo — all phases smoke test")
public class DemoCommand implements Runnable {

    @Inject
    Logger log;

    @Inject
    MatrixMetrics metrics;

    @Override
    public void run() {
        log.info("MATRIX System Demo starting...");
        metrics.actorMessage();
        try {
            SystemDemo.main(new String[0]);
            log.info("MATRIX System Demo complete — ALL SYSTEMS NOMINAL");
        } catch (Exception e) {
            log.error("System demo failed", e);
            metrics.actorError();
        }
    }
}
```

- [ ] **Step 2: Create EvolutionCommand**

```java
package io.matrix.cli;

import io.matrix.minecraft.*;
import io.matrix.observability.MatrixMetrics;
import io.matrix.neuron.DecisionTree;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Command(name = "evolution", description = "Run Minecraft survival evolution experiment")
public class EvolutionCommand implements Runnable {

    @Option(names = {"-g", "--generations"}, defaultValue = "200", description = "Number of generations")
    int generations;

    @Option(names = {"-p", "--population"}, defaultValue = "50", description = "Population size")
    int population;

    @Option(names = {"-s", "--steps"}, defaultValue = "500", description = "Max steps per agent")
    int maxSteps;

    @Option(names = {"-w", "--world-size"}, defaultValue = "50", description = "World size (NxN)")
    int worldSize;

    @Option(names = {"--seed"}, defaultValue = "42", description = "Random seed")
    long seed;

    @Inject
    Logger log;

    @Inject
    MatrixMetrics metrics;

    @Override
    public void run() {
        log.infof("Evolution: %d generations × %d agents × %d steps, world=%dx%d",
                generations, population, maxSteps, worldSize, worldSize);

        Random rng = new Random(seed);
        List<NeuralBrain> brains = new ArrayList<>();
        for (int i = 0; i < population; i++) {
            brains.add(new NeuralBrain(new Random(rng.nextLong())));
        }

        List<Double> fitnesses = new ArrayList<>();
        double bestFitness = 0;
        SurvivalRunner.SurvivalResult bestResult = null;

        for (int gen = 0; gen < generations; gen++) {
            fitnesses.clear();
            double totalFitness = 0;
            double genBest = 0;

            for (int i = 0; i < brains.size(); i++) {
                BlockWorld world = new BlockWorld(worldSize, worldSize, new Random(rng.nextLong()));
                BlockAgent agent = new BlockAgent(
                        new BlockWorld.Position(worldSize / 2, worldSize * 2 / 3 - 2));

                metrics.survivalRun();
                Timer.Sample sample = metrics.startSurvivalRun();
                SurvivalRunner runner = new SurvivalRunner(world, agent, brains.get(i), maxSteps, new Random(rng.nextLong()));
                var result = runner.run();
                metrics.stopSurvivalRun(sample);

                double fitness = result.score();
                fitnesses.add(fitness);
                totalFitness += fitness;
                if (fitness > genBest) genBest = fitness;
                if (fitness > bestFitness) {
                    bestFitness = fitness;
                    bestResult = result;
                }
            }

            metrics.evolutionGeneration();
            metrics.fitnessBest((long) genBest);
            metrics.fitnessAvg((long) (totalFitness / population));

            if (gen % 20 == 0 || gen == generations - 1) {
                log.infof("Gen %3d | best=%.1f avg=%.2f | %s",
                        gen, genBest, totalFitness / population,
                        bestResult != null ? bestResult.toString() : "");
            }

            List<NeuralBrain> nextGen = new ArrayList<>();
            for (int i = 0; i < population / 2; i++) {
                int p1 = tournamentSelect(fitnesses, rng);
                int p2 = tournamentSelect(fitnesses, rng);
                DecisionTree move = rng.nextBoolean() ? brains.get(p1).moveTree() : brains.get(p2).moveTree();
                DecisionTree mine = rng.nextBoolean() ? brains.get(p1).mineTree() : brains.get(p2).mineTree();
                DecisionTree craft = rng.nextBoolean() ? brains.get(p1).craftTree() : brains.get(p2).craftTree();
                DecisionTree eat = rng.nextBoolean() ? brains.get(p1).eatTree() : brains.get(p2).eatTree();
                nextGen.add(new NeuralBrain(move, mine, craft, eat, eat));
                nextGen.add(brains.get(p1));
            }
            while (nextGen.size() < population) {
                nextGen.add(new NeuralBrain(new Random(rng.nextLong())));
            }
            brains = nextGen;
        }

        log.infof("Evolution complete: bestFitness=%.1f blocks=%d crafted=%d tool=%s",
                bestFitness,
                bestResult != null ? bestResult.blocksMined() : 0,
                bestResult != null ? bestResult.itemsCrafted() : 0,
                bestResult != null ? bestResult.finalTool().toString() : "NONE");
    }

    private static int tournamentSelect(List<Double> fitnesses, Random rng) {
        int t1 = rng.nextInt(fitnesses.size());
        int t2 = rng.nextInt(fitnesses.size());
        return fitnesses.get(t1) >= fitnesses.get(t2) ? t1 : t2;
    }
}
```

- [ ] **Step 3: Create SimulateCommand**

```java
package io.matrix.cli;

import io.matrix.evolution.EvolutionLoop;
import io.matrix.evolution.FitnessFn;
import io.matrix.observability.MatrixMetrics;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Random;

@Command(name = "simulate", description = "Run GridWorld agent training via genetic algorithm")
public class SimulateCommand implements Runnable {

    @Option(names = {"-g", "--generations"}, defaultValue = "100", description = "Number of generations")
    int generations;

    @Option(names = {"-p", "--population"}, defaultValue = "20", description = "Population size")
    int population;

    @Option(names = {"-k", "--inputs"}, defaultValue = "18", description = "Neuron inputs (k)")
    int k;

    @Option(names = {"--seed"}, defaultValue = "42", description = "Random seed")
    long seed;

    @Inject
    Logger log;

    @Inject
    MatrixMetrics metrics;

    @Override
    public void run() {
        log.infof("GridWorld simulation: %d generations × %d agents × k=%d", generations, population, k);

        Random rng = new Random(seed);
        FitnessFn fitnessFn = new FitnessFn(20, 20, 15, 10, 200, 3, rng);
        EvolutionLoop loop = new EvolutionLoop(generations, population, k, fitnessFn, rng);

        loop.run();

        var bestHistory = loop.bestFitnessHistory();
        var avgHistory = loop.avgFitnessHistory();
        for (int g = 0; g < bestHistory.size(); g += 10) {
            log.infof("  Gen %3d | best=%d avg=%d", g, bestHistory.get(g), avgHistory.get(g));
        }

        metrics.fitnessBest(bestHistory.get(bestHistory.size() - 1));
        metrics.evolutionGeneration();
        log.info("GridWorld simulation complete");
    }
}
```

- [ ] **Step 4: Build and verify**

Run: `./gradlew :matrix-core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add matrix-core/src/main/java/io/matrix/cli/
git commit -m "feat: add CLI commands — simulate, evolution, demo with Quarkus injection"
```

---

### Task 6: Instrument EvolutionLoop, InstanceMediator, HadesProtocol, SurvivalRunner

**Files:**
- Modify: `matrix-core/src/main/java/io/matrix/evolution/EvolutionLoop.java`
- Modify: `matrix-core/src/main/java/io/matrix/mediator/InstanceMediator.java`
- Modify: `matrix-core/src/main/java/io/matrix/hades/HadesProtocol.java`
- Modify: `matrix-core/src/main/java/io/matrix/minecraft/SurvivalRunner.java`

- [ ] **Step 1: Instrument EvolutionLoop**

Add to `EvolutionLoop.java`:

```java
// Add at top of file, after existing imports:
import io.matrix.observability.MatrixMetrics;
import io.micrometer.core.instrument.Timer;

// Add field:
private final MatrixMetrics metrics;

// Modify constructor:
public EvolutionLoop(int generations, int populationSize, int k,
                      FitnessFn fitnessFn, MatrixMetrics metrics, Random rng) {
    // ... existing init ...
    this.metrics = metrics;
}

// Add at start of run() method:
public void run() {
    nPop.initialize();
    sPop.initialize();
    wPop.initialize();
    ePop.initialize();

    for (int gen = 0; gen < generations; gen++) {
        metrics.evolutionGeneration();
        evaluateGeneration();
        recordHistory();
        metrics.fitnessBest(bestFitnessHistory.get(bestFitnessHistory.size() - 1));
        metrics.fitnessAvg(avgFitnessHistory.get(avgFitnessHistory.size() - 1));
        nPop.evolve();
        sPop.evolve();
        wPop.evolve();
        ePop.evolve();
    }
    evaluateGeneration();
    recordHistory();
    metrics.fitnessBest(bestFitnessHistory.get(bestFitnessHistory.size() - 1));
    metrics.fitnessAvg(avgFitnessHistory.get(avgFitnessHistory.size() - 1));
}
```

- [ ] **Step 2: Instrument InstanceMediator**

Add to `InstanceMediator.java`:

```java
// Add import:
import io.matrix.observability.MatrixMetrics;

// Add field:
private final MatrixMetrics metrics;

// Modify constructors:
public InstanceMediator(MediatorConfig config, MatrixMetrics metrics, Random rng) {
    // ... existing init ...
    this.metrics = metrics;
}

public static InstanceMediator withDefaults(MatrixMetrics metrics, Random rng) {
    return new InstanceMediator(MediatorConfig.defaults(), metrics, rng);
}

// Add at end of tick():
public List<String> tick() {
    // ... existing code ...
    metrics.driverEnergy(Math.round(energy.level() * 100));
    metrics.driverCuriosity(Math.round(curiosity.level() * 100));
    metrics.driverSafety(Math.round(safety.level() * 100));
    return actions;
}
```

- [ ] **Step 3: Instrument HadesProtocol**

Add to `HadesProtocol.java`:

```java
// Add import:
import io.matrix.observability.MatrixMetrics;

// Add field:
private final MatrixMetrics metrics;

// Modify constructor:
public HadesProtocol(SnapshotStore snapshotStore, MatrixMetrics metrics) {
    this.snapshotStore = snapshotStore;
    this.detector = new DerangementDetector();
    this.metrics = metrics;
}

// Add in execute() after scanAll:
var alerts = detector.scanAll(new ArrayList<>(neurons.values()), signalRates);
hadesLog.add("HADES:SCAN alerts=" + alerts.size());
alerts.forEach(a -> metrics.hadesAlert());     // <-- ADD

// Add after isolation:
hadesLog.add("HADES:ISOLATE count=" + quarantine.size() + " ids=" + affected);
quarantine.forEach(n -> metrics.hadesIsolation());  // <-- ADD
```

- [ ] **Step 4: Instrument SurvivalRunner**

Add to `SurvivalRunner.java`:

```java
// Add import:
import io.matrix.observability.MatrixMetrics;
import io.micrometer.core.instrument.Timer;

// Add field:
private final MatrixMetrics metrics;

// Modify constructor:
public SurvivalRunner(BlockWorld world, BlockAgent agent, NeuralBrain brain,
                       int maxSteps, MatrixMetrics metrics, Random rng) {
    this.world = world;
    this.agent = agent;
    this.brain = brain;
    this.crafting = new CraftingSystem();
    this.maxSteps = maxSteps;
    this.metrics = metrics;
    this.rng = rng;
}

// Wrap run() contents:
public SurvivalResult run() {
    metrics.survivalRun();
    Timer.Sample sample = Timer.start();
    try {
        // ... existing run body ...
    } finally {
        sample.stop(metrics.survivalRunTime().getClass()...);
    }
}
```

- [ ] **Step 5: Fix all callers**

Update ALL call sites that construct EvolutionLoop, InstanceMediator, HadesProtocol, SurvivalRunner to pass MatrixMetrics:
- `SystemDemo.java` — use `MatrixMetrics noop` or `null`-guard with defaults
- `MatrixSimulation.java` — pass `MatrixMetrics`
- `MinecraftExperiment.java` — pass `MatrixMetrics`
- All test files — pass `null` or a `noop` instance

- [ ] **Step 6: Build**

Run: `./gradlew :matrix-core:compileJava`
Expected: BUILD SUCCESSFUL with no compilation errors

- [ ] **Step 7: Commit**

```bash
git add matrix-core/src/main/java/io/matrix/evolution/EvolutionLoop.java \
        matrix-core/src/main/java/io/matrix/mediator/InstanceMediator.java \
        matrix-core/src/main/java/io/matrix/hades/HadesProtocol.java \
        matrix-core/src/main/java/io/matrix/minecraft/SurvivalRunner.java
git commit -m "feat: instrument evolution loop, mediator, HADES, survival runner with MatrixMetrics"
```

---

### Task 7: Improved fitness function + curriculum milestones

**Files:**
- Modify: `matrix-core/src/main/java/io/matrix/minecraft/SurvivalRunner.java`

- [ ] **Step 1: Replace score() in SurvivalResult**

```java
public double score() {
    double raw = blocksMined * 2.0 + itemsCrafted * 10.0
            + (alive ? survived * 0.02 : 0) + finalTool.ordinal() * 20.0;
    double milestones = 0;
    if (blocksMined > 0) milestones += 50;
    if (finalTool.ordinal() >= BlockType.ToolTier.WOOD.ordinal() && finalTool != BlockType.ToolTier.NONE) milestones += 100;
    if (finalTool.ordinal() >= BlockType.ToolTier.IRON.ordinal()) milestones += 200;
    if (finalTool.ordinal() >= BlockType.ToolTier.DIAMOND.ordinal()) milestones += 500;
    return raw + milestones;
}
```

- [ ] **Step 2: Update toString()**

```java
@Override
public String toString() {
    return String.format("steps=%d alive=%s blocks=%d crafted=%d tool=%s health=%d hunger=%d score=%.1f",
            steps, alive, blocksMined, itemsCrafted, finalTool, health, hunger, score());
}
```

- [ ] **Step 3: Remove tool gating in findAdjacentBlock**

Change:
```java
if (block.mineable() && agent.toolTier().ordinal() >= block.minTool().ordinal()) {
```
To:
```java
if (block.mineable()) {
```

This allows agents to mine any block regardless of tool tier.

- [ ] **Step 4: Increase eat restoration**

In `BlockAgent.eat()` change:
```java
hunger = Math.min(maxHunger, hunger + 5);
```
To:
```java
hunger = Math.min(maxHunger, hunger + 10);
```

- [ ] **Step 5: Build and test**

Run: `./gradlew :matrix-core:test`
Expected: All tests pass (may need to update test assertions for new score())

- [ ] **Step 6: Commit**

```bash
git add matrix-core/src/main/java/io/matrix/minecraft/SurvivalRunner.java \
        matrix-core/src/main/java/io/matrix/minecraft/BlockAgent.java
git commit -m "feat: improved fitness — milestones bonuses, no tool gating, better hunger recovery"
```

---

### Task 8: Clean up old main() classes, port SystemDemo to Quarkus

**Files:**
- Remove: `matrix-core/src/main/java/io/matrix/LongEvolutionExperiment.java`
- Remove: `matrix-core/src/main/java/io/matrix/NativeCompileTest.java`
- Modify: `matrix-core/src/main/java/io/matrix/MinecraftExperiment.java`
- Modify: `matrix-core/src/main/java/io/matrix/MatrixSimulation.java`
- Modify: `matrix-core/src/main/java/io/matrix/SystemDemo.java`

- [ ] **Step 1: Delete obsolete files**

```bash
rm matrix-core/src/main/java/io/matrix/LongEvolutionExperiment.java
rm matrix-core/src/main/java/io/matrix/NativeCompileTest.java
```

- [ ] **Step 2: Add MatrixMetrics null-guard to SystemDemo**

In `SystemDemo.java`, add a constructor or method that accepts `MatrixMetrics`:

```java
public static void main(String[] args) { main(args, null); }

public static void main(String[] args, MatrixMetrics metrics) {
    // ... existing body ...
}
```

- [ ] **Step 3: Update MinecraftExperiment and MatrixSimulation**

Add `MatrixMetrics` parameter to their `main()` methods (or overload `main(String[], MatrixMetrics)`).

- [ ] **Step 4: Build**

Run: `./gradlew :matrix-core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: remove obsolete main classes, add MatrixMetrics null-guard to SystemDemo"
```

---

### Task 9: Spigot plugin — structured logging + metrics per tick

**Files:**
- Modify: `matrix-spigot/src/main/java/io/matrix/spigot/MatrixPlugin.java`

- [ ] **Step 1: Add structured logging fields**

Replace `getLogger().info(msg)` calls with structured format:

```java
// Replace simple info logs:
getLogger().info("MATRIX Neural Plugin enabled | instance=" + getServer().getName());

// In tick():
private void tick() {
    tickCount++;
    long tickStart = System.currentTimeMillis();
    long sensors = readSensors();
    BlockAgent.Action action = brain.act(sensors);
    executeAction(action);
    long tickDuration = System.currentTimeMillis() - tickStart;

    if (tickCount % 100 == 0) {
        getLogger().info(String.format(
                "MATRIX tick=%d mined=%d health=%d hunger=%d tool=%s action=%s duration=%dms",
                tickCount, blocksMined,
                botPlayer != null ? (int) botPlayer.getHealth() : 0,
                botPlayer != null ? botPlayer.getFoodLevel() : 0,
                detectToolTier(),
                action.getClass().getSimpleName(),
                tickDuration));
    }
}
```

- [ ] **Step 2: Build Spigot**

```bash
./gradlew :matrix-spigot:build
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add matrix-spigot/src/main/java/io/matrix/spigot/MatrixPlugin.java
git commit -m "feat: structured tick logging with metrics in Spigot plugin"
```

---

### Task 10: Docker Compose infrastructure — Prometheus, Jaeger, Grafana

**Files:**
- Create: `infra/docker-compose.yml`
- Create: `infra/prometheus/prometheus.yml`
- Create: `infra/grafana/datasources/prometheus.yml`
- Create: `infra/grafana/dashboards/matrix-overview.json`

- [ ] **Step 1: Create docker-compose.yml**

```yaml
version: '3.8'

services:
  prometheus:
    image: prom/prometheus:v3.7.0
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
    network_mode: host

  jaeger:
    image: jaegertracing/all-in-one:1.67.0
    ports:
      - "16686:16686"
      - "4317:4317"
    environment:
      - COLLECTOR_OTLP_ENABLED=true
    network_mode: host

  grafana:
    image: grafana/grafana:11.6.0
    ports:
      - "3000:3000"
    volumes:
      - ./grafana/datasources:/etc/grafana/provisioning/datasources
      - ./grafana/dashboards:/etc/grafana/provisioning/dashboards
      - grafana_data:/var/lib/grafana
    environment:
      - GF_AUTH_ANONYMOUS_ENABLED=true
      - GF_AUTH_ANONYMOUS_ORG_ROLE=Admin
    network_mode: host

volumes:
  prometheus_data:
  grafana_data:
```

- [ ] **Step 2: Create prometheus.yml**

```yaml
global:
  scrape_interval: 5s
  evaluation_interval: 5s

scrape_configs:
  - job_name: 'matrix'
    static_configs:
      - targets: ['localhost:9091']
    metrics_path: '/metrics'
```

- [ ] **Step 3: Create grafana datasource**

```yaml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://localhost:9090
    isDefault: true
```

- [ ] **Step 4: Create grafana dashboard JSON**

```json
{
  "title": "MATRIX Overview",
  "panels": [
    {
      "title": "Neurons Active",
      "type": "stat",
      "targets": [{"expr": "matrix_neurons_active"}]
    },
    {
      "title": "Evolution Fitness (Best)",
      "type": "graph",
      "targets": [{"expr": "matrix_evolution_fitness_best"}]
    },
    {
      "title": "Evolution Fitness (Avg)",
      "type": "graph",
      "targets": [{"expr": "matrix_evolution_fitness_avg"}]
    },
    {
      "title": "Actor Messages / sec",
      "type": "graph",
      "targets": [{"expr": "rate(matrix_actor_messages_total[1m])"}]
    },
    {
      "title": "HADES Alerts / min",
      "type": "graph",
      "targets": [{"expr": "rate(matrix_hades_alerts_total[1m])"}]
    },
    {
      "title": "JVM Heap Used",
      "type": "graph",
      "targets": [{"expr": "jvm_memory_used_bytes{area=\"heap\"}"}]
    }
  ]
}
```

- [ ] **Step 5: Commit**

```bash
git add infra/
git commit -m "feat: add docker-compose stack — Prometheus, Jaeger, Grafana with MATRIX dashboard"
```

---

### Task 11: Run full test suite + native compilation test

**Files:**
- No new files — verification only

- [ ] **Step 1: Run all tests**

```bash
./gradlew :matrix-core:test
```
Expected: All tests pass (414+ tests, 0 failures)

- [ ] **Step 2: Build Quarkus application**

```bash
./gradlew :matrix-core:quarkusBuild
```
Expected: BUILD SUCCESSFUL, produces `build/quarkus-app/`

- [ ] **Step 3: Run CLI demo**

```bash
java -jar matrix-core/build/quarkus-app/quarkus-run.jar demo
```
Expected: "ALL SYSTEMS NOMINAL" with JSON log output

- [ ] **Step 4: Test health endpoint (if runtime test possible)**

```bash
# Start in background, check /q/health
java -jar matrix-core/build/quarkus-app/quarkus-run.jar &
sleep 5
curl http://localhost:9091/q/health
curl http://localhost:9091/metrics | head -20
kill %1
```
Expected: Health UP, metrics with `matrix_*` entries

- [ ] **Step 5: Native compilation test**

```bash
./gradlew :matrix-core:build -Dquarkus.package.jar.type=native
```
Expected: Native binary produced (may take 2-5 minutes), then run:
```bash
./matrix-core/build/matrix-core-1.0.0-runner demo
```
Expected: "ALL SYSTEMS NOMINAL"

- [ ] **Step 6: Commit any remaining changes**

```bash
git add -A
git commit -m "test: full test suite passed, native compilation confirmed"
```

---

### Task 12: Final WAL update

**Files:**
- Modify: `wal/GLOBAL_WAL.md`
- Modify: `wal/SESSION_WAL.md`

- [ ] **Step 1: Update GLOBAL_WAL.md**

Append:
```
| Observability + Quarkus | ✅ Complete | developer | Micrometer, OTEL, JSON logs, Grafana, Docker, improved fitness |
```

- [ ] **Step 2: Update SESSION_WAL.md**

```markdown
📍 Статус: Observability stack готов. Quarkus 3.24 + Micrometer + OTEL + JSON-логи + Grafana-дашборды. Фитнес улучшен, Spigot с метриками. Нативная компиляция подтверждена.
🚀 Активный этап: Запуск docker-compose, запуск реального Minecraft-сервера со Spigot-плагином.
🛑 Защищённые зоны: Pekko 1.6.0, K_MAX=20, FROZEN-neurons, Quarkus 3.24 LTS. Модели: deepseek-v4-pro → v4-flash → opencode free.
```

- [ ] **Step 3: Commit WAL**

```bash
git add wal/
git commit -m "wal: observability stack complete, ready for launch"
```
