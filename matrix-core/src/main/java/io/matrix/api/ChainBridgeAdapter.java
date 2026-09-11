package io.matrix.api;

import java.util.concurrent.atomic.AtomicLong;
import io.matrix.api.QwenOnnxBridge;
import io.matrix.api.QwenChatTemplate;

/**
 * RUN 126 — Adapter connecting QwenOnnxBridge to the existing
 * chain's "learn + respond" interface.
 *
 * <p>The chain's BrainLoopService calls into this adapter to
 * actually generate text. This bridges the gap between the
 * boolean-chain knowledge substrate and the ONNX LLM.
 */
public final class ChainBridgeAdapter {

    private final QwenOnnxBridge onnx;
    private final AtomicLong callsForwarded = new AtomicLong();
    private final AtomicLong tokensForwarded = new AtomicLong();

    public ChainBridgeAdapter(QwenOnnxBridge onnx) {
        this.onnx = onnx;
    }

    /**
     * Generate a response for the given user query.
     *
     * <p>This is the entry point the chain calls when it needs
     * natural language output.
     */
    public String generateResponse(String userQuery, int maxTokens) {
        if (onnx == null || !onnx.isLoaded()) {
            return null;
        }
        try {
            String prompt = QwenChatTemplate.buildUserPrompt(userQuery);
            String reply = onnx.generate(prompt, maxTokens);
            callsForwarded.incrementAndGet();
            tokensForwarded.addAndGet(estimateTokens(reply));
            return reply;
        } catch (Exception e) {
            return null;
        }
    }

    /** Same but with multi-turn history. */
    public String generateResponseWithHistory(
            java.util.List<QwenChatTemplate.Message> history,
            String userQuery, int maxTokens) {
        if (onnx == null || !onnx.isLoaded()) {
            return null;
        }
        try {
            String reply = onnx.chatWithHistory(history, userQuery, maxTokens,
                    0.0, -1, 1.0);
            callsForwarded.incrementAndGet();
            tokensForwarded.addAndGet(estimateTokens(reply));
            return reply;
        } catch (Exception e) {
            return null;
        }
    }

    public long callsForwarded() { return callsForwarded.get(); }
    public long tokensForwarded() { return tokensForwarded.get(); }

    private static int estimateTokens(String text) {
        if (text == null) return 0;
        return (text.length() + 3) / 4;
    }

    public boolean isReady() {
        return onnx != null && onnx.isLoaded();
    }
}
