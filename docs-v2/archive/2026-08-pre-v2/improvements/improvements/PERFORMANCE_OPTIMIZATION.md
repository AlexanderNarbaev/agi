# Performance Optimization — Implementation Plan

**Status:** ⏳ PLANNED
**Priority:** HIGH
**Estimated effort:** 3-4 weeks
**Target:** v3.59

---

## Problem Statement

Current performance bottlenecks:
1. TruthTable evaluation is sequential for large batches
2. Evolution loop is single-threaded for small populations
3. RAG search is linear scan
4. Kafka serialization overhead

---

## Implementation Steps

### Step 1: SIMD Optimization (Week 1)
```java
// matrix-core/src/main/java/io/matrix/neuron/SimdTruthTableEval.java
public class SimdTruthTableEval {
    
    private static final VectorSpecies<Long> LONG_SPECIES = LongVector.SPECIES_PREFERRED;
    
    public static int[] evaluateBatch(TruthTable tt, int[][] inputs) {
        int batchSize = inputs.length;
        int[] results = new int[batchSize];
        
        // Process in SIMD-width chunks
        int vectorLength = LONG_SPECIES.length();
        int i = 0;
        
        for (; i + vectorLength <= batchSize; i += vectorLength) {
            long[] packed = new long[vectorLength];
            for (int j = 0; j < vectorLength; j++) {
                packed[j] = packInputs(inputs[i + j]);
            }
            
            LongVector vec = LongVector.fromArray(LONG_SPECIES, packed, 0);
            // SIMD evaluation
            LongVector result = vec.and(0x1L); // Extract LSB
            result.intoArray(results, i);
        }
        
        // Scalar tail
        for (; i < batchSize; i++) {
            results[i] = tt.evaluate(inputs[i]);
        }
        
        return results;
    }
}
```

### Step 2: Parallel Evolution (Week 1-2)
```java
// matrix-core/src/main/java/io/matrix/evolution/ParallelEvolution.java
public class ParallelEvolution {
    
    private final ExecutorService executor;
    private final int parallelism;
    
    public ParallelEvolution(int parallelism) {
        this.executor = Executors.newWorkStealingPool(parallelism);
        this.parallelism = parallelism;
    }
    
    public Population evolve(Population pop, FitnessFn fitness) {
        List<Chromosome> chromosomes = pop.getChromosomes();
        int chunkSize = chromosomes.size() / parallelism;
        
        // Parallel fitness evaluation
        List<CompletableFuture<Chromosome>> futures = new ArrayList<>();
        for (int i = 0; i < chromosomes.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, chromosomes.size());
            List<Chromosome> chunk = chromosomes.subList(i, end);
            
            futures.add(CompletableFuture.supplyAsync(() -> {
                return evaluateChunk(chunk, fitness);
            }, executor));
        }
        
        // Collect results
        List<Chromosome> evaluated = futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
        
        return new Population(evaluated);
    }
}
```

### Step 3: Indexed RAG (Week 2)
```java
// matrix-core/src/main/java/io/matrix/rag/IndexedBooleanRag.java
@ApplicationScoped
public class IndexedBooleanRag {
    
    private final Map<String, Set<Integer>> invertedIndex = new ConcurrentHashMap<>();
    private final BooleanIndex booleanIndex;
    
    @PostConstruct
    void buildIndex() {
        // Build inverted index for fast lookup
        for (TruthTable tt : booleanIndex.getAll()) {
            String hash = tt.getContentHash();
            for (int i = 0; i < tt.size(); i++) {
                String term = extractTerm(tt, i);
                invertedIndex.computeIfAbsent(term, k -> new HashSet<>())
                             .add(tt.getId());
            }
        }
    }
    
    public List<TruthTable> search(String query, int topK) {
        Set<String> terms = tokenize(query);
        
        // Find candidate IDs using inverted index
        Set<Integer> candidates = new HashSet<>();
        for (String term : terms) {
            Set<Integer> ids = invertedIndex.get(term);
            if (ids != null) {
                candidates.addAll(ids);
            }
        }
        
        // Score candidates
        return candidates.stream()
            .map(id -> booleanIndex.getById(id))
            .map(tt -> new ScoredResult(tt, score(tt, query)))
            .sorted(Comparator.comparingDouble(ScoredResult::score).reversed())
            .limit(topK)
            .map(ScoredResult::tt)
            .collect(Collectors.toList());
    }
}
```

### Step 4: Kafka Batching (Week 2-3)
```java
// matrix-core/src/main/java/io/matrix/events/BatchKafkaJournal.java
@ApplicationScoped
public class BatchKafkaJournal implements EventJournal {
    
    private final List<ClusterEvent> buffer = new ArrayList<>();
    private final int batchSize = 100;
    private final Duration flushInterval = Duration.ofSeconds(1);
    
    @Override
    public void publish(ClusterEvent event) {
        synchronized (buffer) {
            buffer.add(event);
            if (buffer.size() >= batchSize) {
                flush();
            }
        }
    }
    
    @Scheduled(every = "1s")
    void scheduledFlush() {
        synchronized (buffer) {
            if (!buffer.isEmpty()) {
                flush();
            }
        }
    }
    
    private void flush() {
        // Batch produce to Kafka
        ProducerRecord<String, ClusterEvent>[] records = buffer.stream()
            .map(e -> new ProducerRecord<>(KafkaTopics.EVENTS, e.getId(), e))
            .toArray(ProducerRecord[]::new);
        
        kafkaProducer.send(records);
        buffer.clear();
    }
}
```

### Step 5: Connection Pooling (Week 3)
```java
// matrix-core/src/main/java/io/matrix/redis/PooledRedisConfig.java
@ApplicationScoped
public class PooledRedisConfig {
    
    @Produces
    @ApplicationScoped
    public RedisClient redisClient() {
        return RedisClient.create(
            RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .withConnectionPoolSize(20)
                .withConnectionPoolMinIdle(5)
                .build()
        );
    }
}
```

### Step 6: JMH Benchmarks (Week 3-4)
```java
// matrix-core/src/jmh/java/io/matrix/benchmark/TruthTableBenchmark.java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class TruthTableBenchmark {
    
    private TruthTable tt;
    private int[][] inputs;
    
    @Setup
    public void setup() {
        tt = TruthTable.random(12);
        inputs = new int[1000][];
        for (int i = 0; i < 1000; i++) {
            inputs[i] = randomInputs(12);
        }
    }
    
    @Benchmark
    public int[] batchEvaluate() {
        return BatchEvaluator.evaluate(tt, inputs);
    }
    
    @Benchmark
    public int[] simdEvaluate() {
        return SimdTruthTableEval.evaluateBatch(tt, inputs);
    }
}
```

---

## Performance Targets

| Metric | Current | Target | Improvement |
|--------|---------|--------|-------------|
| TruthTable eval (batch 1000) | 5ms | 1ms | 5x |
| Evolution (100 pop) | 100ms | 20ms | 5x |
| RAG search (10K docs) | 50ms | 5ms | 10x |
| Kafka publish (batch) | 10ms | 1ms | 10x |
| Startup time | 3s | 1s | 3x |

---

## Verification

```bash
# Run JMH benchmarks
./gradlew :matrix-core:jmh

# Compare before/after
jmh-prof benchmarks.json
```
