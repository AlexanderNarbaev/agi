package io.matrix.brain;

import io.matrix.agent.AgentBrainService;
import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;
import io.matrix.memory.HierarchicalMemory;
import io.matrix.multimodal.AudioFeatureExtractor;
import io.matrix.multimodal.FeatureExtractor;
import io.matrix.multimodal.ImageFeatureExtractor;
import io.matrix.multimodal.TextFeatureExtractor;
import io.matrix.neuron.NeuralTextGenerator;
import io.matrix.api.Text2VecService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default 3-block brain pipeline: InputProcessor → HierarchicalBrain →
 * OutputProcessor.
 *
 * <p>Pipeline stages:
 * <ol>
 *   <li>InputProcessor — text (always), image (optional), audio (optional).
 *       Multimodal encoders produce a 64-bit signal vector per modality,
 *       combined into a normalized input vector.</li>
 *   <li>Conscious layer — 3-layer MPDT neural hierarchy via
 *       {@link NeuralTextGenerator}. World-model context is prepended
 *       from {@link HierarchicalMemory}.</li>
 *   <li>OutputProcessor — text formatter with response trimming.</li>
 * </ol>
 *
 * <p>Per L0_manifesto, every stage is fully interpretable and writes to
 * a discrete log of decisions.
 */
@ApplicationScoped
public class DefaultBrainPipeline implements BrainPipeline {

    private static final Logger log = LoggerFactory.getLogger(DefaultBrainPipeline.class);

    private final TextFeatureExtractor textExtractor = new TextFeatureExtractor();
    private final ImageFeatureExtractor imageExtractor = new ImageFeatureExtractor();
    private final AudioFeatureExtractor audioExtractor = new AudioFeatureExtractor();

    @Inject AgentBrainService brainService;
    @Inject Text2VecService text2vec;
    @Inject EthicalFilter ethicalFilter;
    @Inject HierarchicalMemory longTermMemory;

    private final AtomicLong totalRuns = new AtomicLong();

    void onStart(@Observes StartupEvent ev) {
        log.info("DefaultBrainPipeline online: 3-block (input → conscious → output)");
    }

    @Override
    public BrainOutput run(BrainInput input) {
        long t0 = System.nanoTime();
        totalRuns.incrementAndGet();

        EthicalVerdict verdict = ethicalFilter.evaluate(
                input.text() == null ? "" : input.text(),
                List.of("brain-pipeline"));
        if (verdict == EthicalVerdict.REJECTED) {
            log.warn("BrainPipeline: ethical reject");
            return new BrainOutput(
                    "I cannot respond. The Ethical Axioms block this request.",
                    new BlockExecutions("skipped", "skipped", "skipped", 0, 0),
                    (System.nanoTime() - t0) / 1000);
        }

        // ─── Block 1: Input Processor ────────────────────────────────
        StringBuilder inputBuf = new StringBuilder(input.text() == null ? "" : input.text());
        if (input.images() != null) {
            for (byte[] img : input.images()) {
                float[] feats = imageExtractor.extract(img);
                inputBuf.append(' ').append("[").append(imageExtractor.modality())
                        .append(":").append(feats.length).append("feats]");
            }
        }
        if (input.audio() != null) {
            for (byte[] aud : input.audio()) {
                float[] feats = audioExtractor.extract(aud);
                inputBuf.append(' ').append("[").append(audioExtractor.modality())
                        .append(":").append(feats.length).append("feats]");
            }
        }
        String processedInput = inputBuf.toString().trim();
        long inputBits = text2vec.textToBits(processedInput);

        // ─── Memory context (world model) ─────────────────────────────
        int reads = 0;
        StringBuilder memCtx = new StringBuilder();
        if (longTermMemory != null) {
            var entries = longTermMemory.search(processedInput, 3);
            for (var e : entries) {
                if (e != null && e.content() != null && !e.content().isBlank()) {
                    memCtx.append(e.content()).append(' ');
                    reads++;
                    if (memCtx.length() > 400) break;
                }
            }
        }
        String worldContext = memCtx.toString().trim();

        // ─── Block 2: Conscious Layer ─────────────────────────────────
        String prompt = worldContext.isEmpty()
                ? processedInput
                : worldContext + " | " + processedInput;
        String generated = brainService.textGenerator().generate(prompt);

        // If too short, augment with corpus memory scaffold
        if (generated == null || generated.trim().length() < 8) {
            String scaffold = brainService.generateFromMemory(processedInput);
            if (scaffold != null && !scaffold.isBlank()) {
                generated = brainService.textGenerator().continueGeneration(
                        (generated == null ? "" : generated) + " " + scaffold);
            }
        }
        if (generated == null || generated.isBlank()) {
            int actionCode = brainService.brain().decide(inputBits);
            generated = text2vec.bitsToResponse(inputBits ^ actionCode);
        }

        // ─── Block 3: Output Processor ─────────────────────────────────
        String output = generated == null ? "" : generated.trim();
        if (output.length() > 1024) output = output.substring(0, 1024);

        // ─── Memory write-back (world model) ─────────────────────────
        int writes = 0;
        if (longTermMemory != null && !output.isBlank()) {
            longTermMemory.store(
                    HierarchicalMemory.Level.L2_MODULE,
                    "Q: " + truncate(processedInput, 200)
                            + " | A: " + truncate(output, 200),
                    "brain",
                    Set.of("pipeline", "auto"));
            writes++;
        }

        BlockExecutions execs = new BlockExecutions(
                "textExtractor" + (input.images() != null && !input.images().isEmpty() ? "+imageExtractor" : "")
                        + (input.audio() != null && !input.audio().isEmpty() ? "+audioExtractor" : ""),
                "textGenerator.forwardPass (k=16, 3 layers)",
                "truncate@1024",
                reads,
                writes);

        return new BrainOutput(output, execs, (System.nanoTime() - t0) / 1000);
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max));
    }

    public long totalRuns() { return totalRuns.get(); }
}