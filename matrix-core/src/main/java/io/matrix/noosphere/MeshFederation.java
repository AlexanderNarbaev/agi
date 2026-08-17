package io.matrix.noosphere;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Mesh Federation (M4): Distributed mesh for CRDT-based state synchronization.
 *
 * <p>Combines CRDT (GrowOnlySet), QuorumChecker, RealTimeExchange, and Kafka
 * for distributed state management across MATRIX instances.
 *
 * <p>Ref: DESIGN-08, H-013
 */
public final class MeshFederation {

    private static final Logger LOG = LoggerFactory.getLogger(MeshFederation.class);

    private final String nodeId;
    private final Set<String> knownPeers = ConcurrentHashMap.newKeySet();
    private final RealTimeExchange exchange;
    private final KafkaProducer<String, String> kafkaProducer;
    private final String kafkaTopic;
    private final Map<String, FnlPackage> localState = new ConcurrentHashMap<>();
    private final List<MeshListener> listeners = new CopyOnWriteArrayList<>();
    private final int quorumThreshold;

    public MeshFederation(String nodeId, int quorumThreshold, String kafkaBootstrap, String kafkaTopic) {
        this.nodeId = nodeId;
        this.knownPeers.add(nodeId);
        this.exchange = new RealTimeExchange(nodeId);
        this.kafkaProducer = createKafkaProducer(kafkaBootstrap);
        this.kafkaTopic = kafkaTopic;
        this.quorumThreshold = quorumThreshold;
    }

    /**
     * Join the mesh network.
     */
    public void join() {
        knownPeers.add(nodeId);
        LOG.info("Node {} joined mesh federation", nodeId);
        notifyListeners("join", nodeId);
    }

    /**
     * Leave the mesh network.
     */
    public void leave() {
        LOG.info("Node {} left mesh federation", nodeId);
        notifyListeners("leave", nodeId);
    }

    /**
     * Add a peer node to the mesh.
     */
    public void addPeer(String peerId) {
        knownPeers.add(peerId);
        LOG.info("Peer {} added to mesh", peerId);
        notifyListeners("peer_added", peerId);
    }

    /**
     * Publish an FNL package to the mesh.
     */
    public void publish(FnlPackage pkg) {
        localState.put(pkg.name(), pkg);
        exchange.publish(pkg);

        // Publish to Kafka for distributed consensus
        if (kafkaProducer != null && kafkaTopic != null) {
            try {
                String json = pkg.name(); // Simplified serialization
                kafkaProducer.send(new ProducerRecord<>(kafkaTopic, pkg.name(), json));
                LOG.debug("Published {} to Kafka topic {}", pkg.name(), kafkaTopic);
            } catch (Exception e) {
                LOG.warn("Failed to publish to Kafka: {}", e.getMessage());
            }
        }

        notifyListeners("publish", pkg.name());
    }

    /**
     * Subscribe to FNL package updates.
     */
    public void subscribe(String channel, Consumer<FnlPackage> handler) {
        exchange.subscribe(channel, handler);
    }

    /**
     * Check if quorum is reached.
     */
    public boolean hasQuorum() {
        return QuorumChecker.hasQuorum(knownPeers.size(), quorumThreshold);
    }

    /**
     * Get current peer count.
     */
    public int peerCount() {
        return knownPeers.size();
    }

    /**
     * Get local state.
     */
    public Map<String, FnlPackage> localState() {
        return Collections.unmodifiableMap(localState);
    }

    /**
     * Add a mesh listener.
     */
    public void addListener(MeshListener listener) {
        listeners.add(listener);
    }

    /**
     * Notify all listeners.
     */
    private void notifyListeners(String event, String data) {
        for (MeshListener listener : listeners) {
            try {
                listener.onEvent(event, data);
            } catch (Exception e) {
                LOG.warn("Listener error: {}", e.getMessage());
            }
        }
    }

    /**
     * Create Kafka producer.
     */
    private KafkaProducer<String, String> createKafkaProducer(String bootstrap) {
        if (bootstrap == null || bootstrap.isEmpty()) {
            return null;
        }
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrap);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        return new KafkaProducer<>(props);
    }

    /**
     * Close resources.
     */
    public void close() {
        if (kafkaProducer != null) {
            kafkaProducer.close();
        }
        LOG.info("Mesh federation closed for node {}", nodeId);
    }

    /**
     * Mesh event listener.
     */
    public interface MeshListener {
        void onEvent(String event, String data);
    }
}
