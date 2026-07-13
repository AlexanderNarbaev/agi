package io.matrix.noosphere;

import io.matrix.consensus.ConsensusEngine;
import io.matrix.consensus.ConsensusLevel;
import io.matrix.consensus.Proposal;
import io.matrix.consensus.Vote;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Global Mediator — distributed council for Noosphere governance.
 *
 * <p>Composed of top-reputation instances. Governs:
 * <ul>
 * <li>FNL publication approval</li>
 * <li>Global axiom updates (FROZEN rules)</li>
 * <li>Credit model parameters</li>
 * <li>Deprecation and revocation decisions</li>
 * </ul>
 *
 * <p>When a Kafka topic is configured, decisions are published as events
 * for multi-node consensus distribution.
 *
 * <p>Ref: L6_Memory.md §6.1, L4_Mediator.md §2.1
 */
public class GlobalMediator {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalMediator.class);

    private final NoosphereRegistry registry;
    private final CreditModel creditModel;
    private final ConsensusEngine consensus;
    private final List<String> councilMembers;
    private final List<String> actionLog = new ArrayList<>();
    private final KafkaProducer<String, String> kafkaProducer;
    private final String kafkaTopic;

    /**
     * Creates a mediator without Kafka distribution (for testing).
     */
    public GlobalMediator(NoosphereRegistry registry, CreditModel creditModel) {
        this.registry = registry;
        this.creditModel = creditModel;
        this.consensus = new ConsensusEngine();
        this.councilMembers = new ArrayList<>();
        this.kafkaProducer = null;
        this.kafkaTopic = null;
    }

    /**
     * Creates a mediator with Kafka-based decision distribution.
     *
     * @param registry          the Noosphere registry
     * @param creditModel       the credit model
     * @param bootstrapServers  Kafka bootstrap servers
     * @param topic             Kafka topic for mediator decisions
     */
    public GlobalMediator(NoosphereRegistry registry, CreditModel creditModel,
                          String bootstrapServers, String topic) {
        this.registry = registry;
        this.creditModel = creditModel;
        this.consensus = new ConsensusEngine();
        this.councilMembers = new ArrayList<>();
        this.kafkaTopic = topic;
        this.kafkaProducer = createProducer(bootstrapServers);
        LOG.info("GlobalMediator initialized with Kafka distribution — topic={}", topic);
    }

    /**
     * Elects council members based on reputation.
     */
    public void electCouncil(int size) {
        var top = creditModel.topReputation(size);
        councilMembers.clear();
        for (var member : top) {
            councilMembers.add(member.instanceId());
        }
        actionLog.add("COUNCIL_ELECTED:" + councilMembers.size() + " members");
        publishEvent("COUNCIL_ELECTED", String.join(",", councilMembers));
    }

    /**
     * Proposes FNL publication to the council.
     */
    public UUID proposePublication(FnlPackage fnl, String proposerId) {
        Proposal proposal = Proposal.create(ConsensusLevel.LEVEL_3,
                proposerId, "PUBLISH_FNL",
                fnl.name() + ":" + fnl.type() + ":" + fnl.accuracy());
        UUID propId = consensus.propose(proposal);
        actionLog.add("PROPOSE_PUBLISH:" + fnl.name() + " by " + proposerId);
        publishEvent("PROPOSE_PUBLISH", propId + ":" + fnl.name());
        return propId;
    }

    /**
     * Council member votes on a proposal.
     */
    public void councilVote(UUID proposalId, String voterId, boolean approve) {
        double weight = creditModel.getCredits(voterId).reputation();
        Vote vote = approve
                ? Vote.approve(proposalId, voterId, Math.max(weight, 0.1))
                : Vote.reject(proposalId, voterId, Math.max(weight, 0.1));
        consensus.castVote(vote);
    }

    /**
     * Evaluates a proposal and executes the decision.
     *
     * <p>When Kafka is configured, the decision is published as an event
     * for multi-node consensus distribution.
     */
    public String decide(UUID proposalId) {
        var decision = consensus.evaluate(proposalId);
        var prop = consensus.getProposal(proposalId);

        if (decision == ConsensusEngine.Decision.APPROVED && prop != null
                && prop.action().equals("PUBLISH_FNL")) {

            String[] parts = prop.payload().split(":");
            if (parts.length >= 3) {
                actionLog.add("DECIDE:APPROVED " + prop.payload());
                publishEvent("DECISION", proposalId + ":APPROVED:" + prop.payload());
                return "APPROVED";
            }
        }

        String result = decision.name();
        actionLog.add("DECIDE:" + result + " " + (prop != null ? prop.payload() : ""));
        publishEvent("DECISION", proposalId + ":" + result);
        return result;
    }

    public List<String> councilMembers() { return List.copyOf(councilMembers); }

    public List<String> actionLog() { return List.copyOf(actionLog); }

    public ConsensusEngine consensus() { return consensus; }

    public NoosphereRegistry registry() { return registry; }

    public CreditModel creditModel() { return creditModel; }

    // ─── Kafka distribution ───

    private void publishEvent(String eventType, String payload) {
        if (kafkaProducer == null || kafkaTopic == null) return;
        try {
            String value = eventType + "|" + System.currentTimeMillis() + "|" + payload;
            var record = new ProducerRecord<>(kafkaTopic, eventType, value);
            kafkaProducer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    LOG.error("Failed to publish {} event to Kafka: {}", eventType, exception.getMessage());
                } else {
                    LOG.trace("Published {} event to {} partition {} offset {}",
                            eventType, metadata.topic(), metadata.partition(), metadata.offset());
                }
            });
        } catch (Exception e) {
            LOG.warn("Kafka publish failed for {}: {}", eventType, e.getMessage());
        }
    }

    private static KafkaProducer<String, String> createProducer(String bootstrapServers) {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.ACKS_CONFIG, "1",
                ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy"
        );
        return new KafkaProducer<>(props);
    }

    /**
     * Closes the Kafka producer if active.
     */
    public void close() {
        if (kafkaProducer != null) {
            kafkaProducer.flush();
            kafkaProducer.close();
        }
    }
}
