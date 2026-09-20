package io.matrix.federation.liquid;

import java.util.*;

/**
 * W587 — Biochemical Dashboard.
 *
 * D3.js visualization: Flowing nodes for hormones, heatmaps for brain activity.
 * Generates HTML/JS for browser rendering.
 */
public final class BiochemicalDashboard {

    private final Map<String, KineticModulator> modulators;
    private final CorridorHomeostat homeostat;

    public BiochemicalDashboard(Map<String, KineticModulator> modulators,
                                 CorridorHomeostat homeostat) {
        this.modulators = modulators;
        this.homeostat = homeostat;
    }

    /**
     * Generate the complete dashboard HTML.
     */
    public String generateHtml() {
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html><html><head><title>MATRIX Biochemical Dashboard</title>");
        sb.append("<script src='https://d3js.org/d3.v7.min.js'></script>");
        sb.append("<style>");
        sb.append("body{font-family:monospace;background:#0a0a0a;color:#00ff88;padding:20px;}");
        sb.append("h1{color:#00ffff;}");
        sb.append(".card{background:#111;border:1px solid #333;padding:15px;margin:10px 0;}");
        sb.append(".modulator{display:inline-block;width:120px;text-align:center;padding:10px;margin:5px;}");
        sb.append(".bar{background:#003322;height:200px;width:60px;margin:0 auto;position:relative;}");
        sb.append(".fill{background:#00ff88;position:absolute;bottom:0;width:100%;transition:height 0.5s;}");
        sb.append(".label{font-size:12px;margin-top:5px;}");
        sb.append(".value{font-size:18px;color:#00ffff;}");
        sb.append(".heatmap{display:grid;grid-template-columns:repeat(5,40px);gap:2px;}");
        sb.append(".cell{width:40px;height:40px;display:flex;align-items:center;justify-content:center;}");
        sb.append("</style></head><body>");

        sb.append("<h1>MATRIX Biochemical Dashboard</h1>");

        // Modulator bars
        sb.append("<div class='card'><h2>Hormone Levels</h2><div id='modulators'>");
        for (var entry : modulators.entrySet()) {
            KineticModulator m = entry.getValue();
            sb.append("<div class='modulator'>");
            sb.append("<div class='bar'><div class='fill' id='fill-").append(m.getId())
              .append("' style='height:").append((int) (m.getCurrentLevel() * 100)).append("%'></div></div>");
            sb.append("<div class='label'>").append(m.getName()).append("</div>");
            sb.append("<div class='value' id='val-").append(m.getId()).append("'>")
              .append(String.format("%.2f", m.getCurrentLevel())).append("</div>");
            sb.append("</div>");
        }
        sb.append("</div></div>");

        // Heatmap
        sb.append("<div class='card'><h2>Brain Activity Heatmap</h2>");
        sb.append("<div class='heatmap' id='heatmap'>");
        for (int i = 0; i < 25; i++) {
            double intensity = Math.random();
            String color = intensityToColor(intensity);
            sb.append("<div class='cell' style='background:").append(color).append("'>")
              .append(String.format("%.1f", intensity)).append("</div>");
        }
        sb.append("</div></div>");

        // Homeostat corridors
        sb.append("<div class='card'><h2>System Corridors</h2><table>");
        sb.append("<tr><th>Corridor</th><th>Value</th><th>State</th><th>Throttle</th></tr>");
        for (CorridorHomeostat.Corridor corridor : homeostat.getAllCorridors()) {
            String stateClass = corridor.getState() == CorridorHomeostat.CorridorState.VIOLATED ? "violation" :
                               corridor.getState() == CorridorHomeostat.CorridorState.WARNING ? "warning" : "";
            sb.append("<tr>");
            sb.append("<td>").append(corridor.getName()).append("</td>");
            sb.append("<td>").append(String.format("%.3f", corridor.getCurrentValue())).append("</td>");
            sb.append("<td class='").append(stateClass).append("'>").append(corridor.getState()).append("</td>");
            sb.append("<td>").append(String.format("%.2f", corridor.getThrottleFactor())).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table></div>");

        // Auto-refresh script
        sb.append("<script>");
        sb.append("setInterval(async()=>{");
        sb.append("try{const r=await fetch('/modulators');const d=await r.json();");
        sb.append("d.modulators.forEach(m=>{");
        sb.append("const fill=document.getElementById('fill-'+m.id);");
        sb.append("const val=document.getElementById('val-'+m.id);");
        sb.append("if(fill)fill.style.height=(m.level*100)+'%';");
        sb.append("if(val)val.textContent=m.level.toFixed(3);");
        sb.append("});}catch(e){}},2000);");
        sb.append("</script>");

        sb.append("</body></html>");

        return sb.toString();
    }

    private String intensityToColor(double intensity) {
        int r = (int) (intensity * 255);
        int g = (int) ((1 - intensity) * 100);
        int b = 0;
        return String.format("rgb(%d,%d,%d)", r, g, b);
    }

    /**
     * Generate modulator data as JSON for API.
     */
    public String generateModulatorJson() {
        StringBuilder sb = new StringBuilder("{\"modulators\":[");
        boolean first = true;
        for (var entry : modulators.entrySet()) {
            KineticModulator m = entry.getValue();
            if (!first) sb.append(",");
            sb.append("{\"id\":\"").append(m.getId()).append("\"")
              .append(",\"name\":\"").append(m.getName()).append("\"")
              .append(",\"level\":").append(m.getCurrentLevel())
              .append(",\"sensitivity\":").append(m.getReceptorSensitivity())
              .append(",\"frozen\":").append(m.isFrozen())
              .append("}");
            first = false;
        }
        sb.append("]}");
        return sb.toString();
    }
}
