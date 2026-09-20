package io.matrix.federation.liquid;

import java.util.*;

/**
 * W590 — Interactive Chat UI v2.
 *
 * Toggle modes (Deep/Efficient). View confidence scores and modulator
 * states per message.
 */
public final class InteractiveChatUI {

    private final CognitiveRouter router;
    private final Map<String, KineticModulator> modulators;
    private final ThoughtProcessVisualizer visualizer;

    public InteractiveChatUI(CognitiveRouter router,
                              Map<String, KineticModulator> modulators,
                              ThoughtProcessVisualizer visualizer) {
        this.router = router;
        this.modulators = modulators;
        this.visualizer = visualizer;
    }

    /**
     * Process a chat message and return response with metadata.
     */
    public ChatResponse processMessage(String message, boolean forceDeepMode) {
        // Route the message
        CognitiveRouter.RoutingDecision routing;
        if (forceDeepMode) {
            routing = new CognitiveRouter.RoutingDecision(
                    CognitiveRouter.CognitiveMode.DEEP_UNDERSTANDING,
                    "Forced deep mode", 1.0, getModulatorSnapshot());
        } else {
            routing = router.routeAuto(message);
        }

        // Record routing decision
        visualizer.recordRouting(routing.mode(), routing.reason());

        // Generate response based on mode
        String response;
        double confidence;

        if (routing.mode() == CognitiveRouter.CognitiveMode.DEEP_UNDERSTANDING) {
            response = generateDeepResponse(message);
            confidence = 0.8;
        } else {
            response = generateEfficientResponse(message);
            confidence = 0.9;
        }

        return new ChatResponse(
                response,
                confidence,
                routing.mode(),
                routing.reason(),
                getModulatorSnapshot(),
                visualizer.getStepCount()
        );
    }

    public record ChatResponse(
            String response,
            double confidence,
            CognitiveRouter.CognitiveMode mode,
            String routingReason,
            Map<String, Double> modulatorStates,
            int thoughtSteps
    ) {}

    private String generateDeepResponse(String message) {
        // Simulate deep reasoning
        visualizer.recordBirRule("rule-1", message, "analyzed", 0.8);
        visualizer.recordCausalReasoning("input", "analysis", 0.7);
        return "[DEEP] Analyzing: " + message + " → Detailed response based on BIR/Causal reasoning.";
    }

    private String generateEfficientResponse(String message) {
        // Simulate efficient response
        visualizer.recordHdcMatch("pattern-" + message.hashCode(), 0.85);
        return "[EFFICIENT] Quick response to: " + message;
    }

    private Map<String, Double> getModulatorSnapshot() {
        Map<String, Double> snapshot = new HashMap<>();
        modulators.forEach((k, v) -> snapshot.put(k, v.getCurrentLevel()));
        return snapshot;
    }

    /**
     * Generate HTML chat interface.
     */
    public String toHtml() {
        return "<!DOCTYPE html><html><head><title>MATRIX Chat v2</title>" +
            "<style>body{font-family:monospace;background:#0a0a0a;color:#00ff88;padding:20px;}" +
            "h1{color:#00ffff;}" +
            "#chat{height:400px;overflow-y:scroll;background:#111;border:1px solid #333;padding:10px;}" +
            ".msg{margin:5px 0;padding:5px;}" +
            ".user{color:#00ff88;}" +
            ".assistant{color:#00aaff;}" +
            ".meta{font-size:12px;color:#888;}" +
            "input{width:80%;padding:8px;background:#222;color:#00ff88;border:1px solid #333;}" +
            "button{padding:8px 16px;background:#003322;color:#00ff88;border:1px solid #00ff88;}" +
            ".modulators{display:flex;gap:10px;margin:10px 0;}" +
            ".mod{background:#111;padding:5px;border:1px solid #333;}" +
            "</style></head><body>" +
            "<h1>MATRIX Chat v2</h1>" +
            "<div class='modulators' id='mods'></div>" +
            "<div id='chat'></div>" +
            "<input id='msg' placeholder='Type a message...' />" +
            "<button onclick='send()'>Send</button>" +
            "<label><input type='checkbox' id='deep' /> Deep Mode</label>" +
            "<script>" +
            "async function send(){" +
            "const msg=document.getElementById('msg').value;" +
            "if(!msg)return;" +
            "document.getElementById('msg').value='';" +
            "document.getElementById('chat').innerHTML+='<div class=\"msg user\">You: '+msg+'</div>';" +
            "const deep=document.getElementById('deep').checked;" +
            "const r=await fetch('/chat',{method:'POST',headers:{'Content-Type':'application/json'}," +
            "body:JSON.stringify({message:msg,deep:deep})});" +
            "const d=await r.json();" +
            "document.getElementById('chat').innerHTML+='<div class=\"msg assistant\">MATRIX: '+d.response+'</div>';" +
            "document.getElementById('chat').innerHTML+='<div class=\"meta\">Mode: '+d.mode+' | Confidence: '+d.confidence.toFixed(2)+' | Steps: '+d.thoughtSteps+'</div>';" +
            "document.getElementById('chat').scrollTop=document.getElementById('chat').scrollHeight;" +
            "}" +
            "document.getElementById('msg').addEventListener('keydown',e=>{if(e.key==='Enter')send();});" +
            "</script></body></html>";
    }
}
