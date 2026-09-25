# Federated Learning — Implementation Plan

**Status:** 📋 SPEC ONLY
**Priority:** MEDIUM
**Estimated effort:** 6-8 weeks
**Target:** v3.62

---

## Problem Statement

Current learning is centralized. Need:
1. Distributed training across multiple nodes
2. Privacy-preserving gradient aggregation
3. Byzantine-resilient aggregation
4. Communication efficiency

---

## Implementation Steps

### Step 1: Federated Protocol (Week 1-2)
```java
// matrix-core/src/main/java/io/matrix/federated/FederatedProtocol.java
@ApplicationScoped
public class FederatedProtocol {
    
    @Inject
    P2PNetwork network;
    
    @ConfigProperty(name = "matrix.federated.rounds", defaultValue = "10")
    int rounds;
    
    @ConfigProperty(name = "matrix.federated.min-participants", defaultValue = "3")
    int minParticipants;
    
    public void federatedTrain(List<String> participants) {
        for (int round = 0; round < rounds; round++) {
            // 1. Select participants
            List<String> selected = selectParticipants(participants);
            
            // 2. Send global model
            TruthTable globalModel = getGlobalModel();
            network.broadcastModel(globalModel, selected);
            
            // 3. Collect local updates
            List<LocalUpdate> updates = collectUpdates(selected);
            
            // 4. Aggregate
            TruthTable aggregated = aggregate(updates);
            
            // 5. Update global model
            updateGlobalModel(aggregated);
        }
    }
}
```

### Step 2: Local Training (Week 2)
```java
// matrix-core/src/main/java/io/matrix/federated/LocalTrainer.java
@ApplicationScoped
public class LocalTrainer {
    
    @Inject
    AgentBrainService brainService;
    
    public LocalUpdate trainLocal(TruthTable globalModel, List<TruthTable> localData) {
        // Load global model
        brainService.load(globalModel);
        
        // Train on local data
        for (TruthTable data : localData) {
            brainService.train(data);
        }
        
        // Compute update (difference from global)
        TruthTable localModel = brainService.export();
        TruthTable update = computeUpdate(globalModel, localModel);
        
        return new LocalUpdate(
            getNodeId(),
            update,
            localData.size(),
            computeLoss(localModel, localData)
        );
    }
    
    private TruthTable computeUpdate(TruthTable global, TruthTable local) {
        // XOR difference
        boolean[] globalBits = global.toBits();
        boolean[] localBits = local.toBits();
        boolean[] diff = new boolean[globalBits.length];
        for (int i = 0; i < diff.length; i++) {
            diff[i] = globalBits[i] ^ localBits[i];
        }
        return TruthTable.fromBits(diff);
    }
}
```

### Step 3: Secure Aggregation (Week 3-4)
```java
// matrix-core/src/main/java/io/matrix/federated/SecureAggregator.java
@ApplicationScoped
public class SecureAggregator {
    
    public TruthTable aggregate(List<LocalUpdate> updates) {
        // Remove outliers (Byzantine resilience)
        List<LocalUpdate> filtered = removeOutliers(updates);
        
        // Weighted aggregation
        boolean[] aggregated = null;
        double totalWeight = 0;
        
        for (LocalUpdate update : filtered) {
            double weight = computeWeight(update);
            boolean[] bits = update.getUpdate().toBits();
            
            if (aggregated == null) {
                aggregated = new boolean[bits.length];
            }
            
            for (int i = 0; i < bits.length; i++) {
                if (bits[i]) {
                    aggregated[i] = !aggregated[i]; // XOR aggregation
                }
            }
            
            totalWeight += weight;
        }
        
        return TruthTable.fromBits(aggregated);
    }
    
    private List<LocalUpdate> removeOutliers(List<LocalUpdate> updates) {
        // Median-based outlier detection
        double medianLoss = median(updates.stream()
            .mapToDouble(LocalUpdate::getLoss)
            .toArray());
        
        return updates.stream()
            .filter(u -> Math.abs(u.getLoss() - medianLoss) < 2 * medianLoss)
            .collect(Collectors.toList());
    }
    
    private double computeWeight(LocalUpdate update) {
        // Weight by data size and inverse loss
        return update.getDataSize() / (1 + update.getLoss());
    }
}
```

### Step 4: Privacy Mechanisms (Week 4-5)
```java
// matrix-core/src/main/java/io/matrix/federated/PrivacyMechanism.java
@ApplicationScoped
public class PrivacyMechanism {
    
    @ConfigProperty(name = "matrix.federated.dp.epsilon", defaultValue = "1.0")
    double epsilon;
    
    public LocalUpdate addDifferentialPrivacy(LocalUpdate update) {
        // Add Laplacian noise for differential privacy
        boolean[] bits = update.getUpdate().toBits();
        boolean[] noisy = new boolean[bits.length];
        
        double sensitivity = 1.0;
        double scale = sensitivity / epsilon;
        
        for (int i = 0; i < bits.length; i++) {
            if (bits[i]) {
                // Flip bit with probability proportional to noise
                double noise = laplacianNoise(scale);
                if (Math.random() < noise) {
                    noisy[i] = !bits[i];
                } else {
                    noisy[i] = bits[i];
                }
            } else {
                noisy[i] = bits[i];
            }
        }
        
        return new LocalUpdate(
            update.getNodeId(),
            TruthTable.fromBits(noisy),
            update.getDataSize(),
            update.getLoss()
        );
    }
    
    private double laplacianNoise(double scale) {
        double u = Math.random() - 0.5;
        return -scale * Math.signum(u) * Math.log(1 - 2 * Math.abs(u));
    }
}
```

### Step 5: Communication Efficiency (Week 5-6)
```java
// matrix-core/src/main/java/io/matrix/federated/CompressionCodec.java
public class CompressionCodec {
    
    public byte[] compress(TruthTable update) {
        boolean[] bits = update.toBits();
        
        // Run-length encoding
        List<Byte> compressed = new ArrayList<>();
        int count = 1;
        boolean current = bits[0];
        
        for (int i = 1; i < bits.length; i++) {
            if (bits[i] == current && count < 255) {
                count++;
            } else {
                compressed.add((byte) (current ? count : -count));
                current = bits[i];
                count = 1;
            }
        }
        compressed.add((byte) (current ? count : -count));
        
        return toByteArray(compressed);
    }
    
    public TruthTable decompress(byte[] data) {
        // Reverse RLE
        List<Boolean> bits = new ArrayList<>();
        for (byte b : data) {
            boolean value = b > 0;
            int count = Math.abs(b);
            for (int i = 0; i < count; i++) {
                bits.add(value);
            }
        }
        
        boolean[] result = new boolean[bits.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = bits.get(i);
        }
        return TruthTable.fromBits(result);
    }
}
```

### Step 6: REST API (Week 6)
```java
// matrix-core/src/main/java/io/matrix/federated/FederatedResource.java
@Path("/api/v1/federated")
@Produces(MediaType.APPLICATION_JSON)
public class FederatedResource {
    
    @Inject
    FederatedProtocol protocol;
    
    @POST
    @Path("/start")
    public Response startFederatedTraining(FederatedRequest request) {
        protocol.federatedTrain(request.getParticipants());
        return Response.ok().build();
    }
    
    @GET
    @Path("/status")
    public FederatedStatus getStatus() {
        return protocol.getStatus();
    }
}
```

### Step 7: Integration Tests (Week 7-8)
```java
@QuarkusTest
class FederatedTest {
    
    @Inject
    FederatedProtocol protocol;
    
    @Test
    void testFederatedTraining() {
        // Simulate 3 nodes
        List<String> participants = List.of("node1", "node2", "node3");
        
        // Run federated training
        protocol.federatedTrain(participants);
        
        // Verify convergence
        TruthTable globalModel = protocol.getGlobalModel();
        assertNotNull(globalModel);
    }
    
    @Test
    void testByzantineResilience() {
        // One node sends malicious update
        // Verify system still converges
    }
}
```

---

## Communication Protocol

```
┌─────────┐     ┌─────────┐     ┌─────────┐
│ Node 1  │────▶│ Aggregator│◀────│ Node 2  │
└─────────┘     └─────────┘     └─────────┘
                      │
                      ▼
              ┌─────────────┐
              │ Global Model│
              └─────────────┘

Messages:
- MODEL_UPDATE: Global → Local (send model)
- LOCAL_UPDATE: Local → Aggregator (send update)
- AGGREGATED: Aggregator → All (send aggregated model)
```

---

## Verification

```bash
# Start federated training
curl -X POST http://localhost:9091/api/v1/federated/start \
  -H 'Content-Type: application/json' \
  -d '{"participants": ["node1", "node2", "node3"]}'

# Check status
curl http://localhost:9091/api/v1/federated/status
```
