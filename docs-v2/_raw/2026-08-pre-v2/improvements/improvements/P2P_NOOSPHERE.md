# P2P Noosphere — Implementation Plan

**Status:** 📋 SPEC ONLY
**Priority:** HIGH
**Estimated effort:** 6-8 weeks
**Target:** v3.62

---

## Problem Statement

Current Noosphere is centralized (single GlobalMediator). Need:
1. Peer-to-peer knowledge exchange
2. Decentralized trust/reputation
3. Consensus on shared knowledge
4. Privacy-preserving federation

---

## Implementation Steps

### Step 1: P2P Network Layer (Week 1-2)
```java
// matrix-core/src/main/java/io/matrix/noosphere/p2p/P2PNetwork.java
@ApplicationScoped
public class P2PNetwork {
    
    @ConfigProperty(name = "matrix.p2p.port", defaultValue = "9092")
    int port;
    
    private final Map<String, Peer> peers = new ConcurrentHashMap<>();
    private final PeerDiscovery discovery;
    
    public P2PNetwork() {
        this.discovery = new PeerDiscovery();
    }
    
    @PostConstruct
    void start() {
        // Start listening for peers
        discovery.start(port);
        discovery.onPeerDiscovered(this::onPeerDiscovered);
    }
    
    public void broadcastKnowledge(FnlPackage pkg) {
        for (Peer peer : peers.values()) {
            peer.sendKnowledge(pkg);
        }
    }
    
    public List<FnlPackage> requestKnowledge(String topic) {
        List<FnlPackage> results = new ArrayList<>();
        for (Peer peer : peers.values()) {
            results.addAll(peer.queryKnowledge(topic));
        }
        return results;
    }
}
```

### Step 2: Peer Discovery (Week 2)
```java
// matrix-core/src/main/java/io/matrix/noosphere/p2p/PeerDiscovery.java
public class PeerDiscovery {
    
    private static final String MULTICAST_GROUP = "239.255.255.250";
    private static final int MULTICAST_PORT = 9093;
    
    public void start(int port) {
        // mDNS-style discovery
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(port)) {
                socket.joinGroup(InetAddress.getByName(MULTICAST_GROUP));
                byte[] buffer = new byte[1024];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    handleDiscovery(new String(packet.getData(), 0, packet.getLength()));
                }
            }
        }).start();
    }
    
    private void handleDiscovery(String message) {
        // Parse peer announcement
        PeerInfo info = PeerInfo.fromJson(message);
        onPeerDiscovered.accept(info);
    }
}
```

### Step 3: Trust/Reputation System (Week 3-4)
```java
// matrix-core/src/main/java/io/matrix/noosphere/p2p/TrustManager.java
@ApplicationScoped
public class TrustManager {
    
    private final Map<String, PeerReputation> reputations = new ConcurrentHashMap<>();
    
    public double getTrustScore(String peerId) {
        PeerReputation rep = reputations.get(peerId);
        if (rep == null) return 0.5; // Default neutral
        
        return rep.calculateScore();
    }
    
    public void recordInteraction(String peerId, InteractionResult result) {
        reputations.computeIfAbsent(peerId, k -> new PeerReputation())
                   .record(result);
    }
    
    public static class PeerReputation {
        private int successful = 0;
        private int failed = 0;
        private double qualitySum = 0.0;
        
        public double calculateScore() {
            if (successful + failed == 0) return 0.5;
            double successRate = (double) successful / (successful + failed);
            double avgQuality = qualitySum / successful;
            return (successRate * 0.7) + (avgQuality * 0.3);
        }
    }
}
```

### Step 4: Knowledge Consensus (Week 4-5)
```java
// matrix-core/src/main/java/io/matrix/noosphere/p2p/KnowledgeConsensus.java
@ApplicationScoped
public class KnowledgeConsensus {
    
    @Inject
    TrustManager trustManager;
    
    public FnlPackage resolveConflict(List<FnlPackage> conflicting) {
        // Weighted voting based on trust scores
        Map<String, Double> votes = new HashMap<>();
        
        for (FnlPackage pkg : conflicting) {
            double trust = trustManager.getTrustScore(pkg.getSourcePeer());
            String hash = pkg.getContentHash();
            votes.merge(hash, trust, Double::sum);
        }
        
        // Return highest-voted version
        String winner = votes.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElseThrow();
        
        return conflicting.stream()
            .filter(p -> p.getContentHash().equals(winner))
            .findFirst()
            .orElseThrow();
    }
}
```

### Step 5: Privacy-Preserving Exchange (Week 5-6)
```java
// matrix-core/src/main/java/io/matrix/noosphere/p2p/PrivacyPreserver.java
public class PrivacyPreserver {
    
    public FnlPackage anonymize(FnlPackage pkg) {
        // Remove identifying information
        return FnlPackage.builder()
            .content(pkg.getContent())
            .metadata(anonymizeMetadata(pkg.getMetadata()))
            .sourcePeer("anonymous-" + hashPeer(pkg.getSourcePeer()))
            .build();
    }
    
    public boolean verifyIntegrity(FnlPackage pkg) {
        // Verify content hasn't been tampered with
        String expected = calculateHash(pkg.getContent());
        return expected.equals(pkg.getContentHash());
    }
}
```

### Step 6: REST API (Week 6)
```java
// matrix-core/src/main/java/io/matrix/noosphere/p2p/P2PResource.java
@Path("/api/v1/noosphere/p2p")
@Produces(MediaType.APPLICATION_JSON)
public class P2PResource {
    
    @Inject
    P2PNetwork network;
    
    @GET
    @Path("/peers")
    public List<Peer> listPeers() {
        return network.getPeers();
    }
    
    @POST
    @Path("/publish")
    public Response publish(FnlPackage pkg) {
        network.broadcastKnowledge(pkg);
        return Response.ok().build();
    }
    
    @GET
    @Path("/query")
    public List<FnlPackage> query(@QueryParam("topic") String topic) {
        return network.requestKnowledge(topic);
    }
}
```

### Step 7: Integration Tests (Week 7-8)
```java
@QuarkusTest
class P2PNetworkTest {
    
    @Inject
    P2PNetwork network;
    
    @Test
    void testPeerDiscovery() {
        // Start two nodes
        P2PNetwork node1 = new P2PNetwork(9092);
        P2PNetwork node2 = new P2PNetwork(9093);
        
        // Wait for discovery
        await().atMost(Duration.ofSeconds(5))
               .until(() -> node1.getPeers().size() > 0);
    }
    
    @Test
    void testKnowledgeExchange() {
        FnlPackage pkg = createTestPackage();
        network.broadcastKnowledge(pkg);
        
        // Verify received by peers
        // ...
    }
}
```

---

## Network Protocol

```
┌─────────────┐     ┌─────────────┐
│   Node A    │────▶│   Node B    │
│ (Publisher) │     │ (Subscriber)│
└─────────────┘     └─────────────┘
       │                   │
       │   ┌───────────┐   │
       └──▶│ Discovery │◀──┘
           │  Service  │
           └───────────┘

Messages:
- ANNOUNCE: Node presence announcement
- QUERY: Knowledge request
- RESPONSE: Knowledge response
- ACK: Delivery confirmation
```

---

## Verification

```bash
# Start two nodes
./gradlew :matrix-core:quarkusRun -- -Dmatrix.p2p.port=9092
./gradlew :matrix-core:quarkusRun -- -Dmatrix.p2p.port=9093

# Check peers
curl http://localhost:9091/api/v1/noosphere/p2p/peers

# Publish knowledge
curl -X POST http://localhost:9091/api/v1/noosphere/p2p/publish \
  -H 'Content-Type: application/json' \
  -d '{"content": "test knowledge"}'

# Query from other node
curl http://localhost:9092/api/v1/noosphere/p2p/query?topic=test
```
