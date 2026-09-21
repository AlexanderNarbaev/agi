---
layout: default
title: "MATRIX: The Hybrid Intelligence Encyclopedia"
description: "An open-source hybrid neuro-symbolic AI system that thinks like a city of minds."
---

<div class="hero-section">
  <div class="hero-background">
    <svg width="100%" height="400" viewBox="0 0 800 400" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <radialGradient id="nodeGrad" cx="50%" cy="50%" r="50%">
          <stop offset="0%" style="stop-color:#4fc3f7;stop-opacity:1" />
          <stop offset="100%" style="stop-color:#1976d2;stop-opacity:0.3" />
        </radialGradient>
        <radialGradient id="pulseGrad" cx="50%" cy="50%" r="50%">
          <stop offset="0%" style="stop-color:#ff6b6b;stop-opacity:0.8" />
          <stop offset="100%" style="stop-color:#ff6b6b;stop-opacity:0" />
        </radialGradient>
      </defs>
      <!-- Animated neural network background -->
      <g stroke="#4fc3f7" stroke-width="0.5" opacity="0.4">
        <line x1="100" y1="100" x2="300" y2="200">
          <animate attributeName="opacity" values="0.2;0.8;0.2" dur="3s" repeatCount="indefinite"/>
        </line>
        <line x1="300" y1="200" x2="500" y2="150">
          <animate attributeName="opacity" values="0.2;0.8;0.2" dur="2.5s" repeatCount="indefinite"/>
        </line>
        <line x1="500" y1="150" x2="700" y2="250">
          <animate attributeName="opacity" values="0.2;0.8;0.2" dur="4s" repeatCount="indefinite"/>
        </line>
        <line x1="200" y1="300" x2="400" y2="100">
          <animate attributeName="opacity" values="0.2;0.8;0.2" dur="3.5s" repeatCount="indefinite"/>
        </line>
        <line x1="400" y1="100" x2="600" y2="350">
          <animate attributeName="opacity" values="0.2;0.8;0.2" dur="2s" repeatCount="indefinite"/>
        </line>
      </g>
      <!-- Nodes -->
      <circle cx="100" cy="100" r="20" fill="url(#nodeGrad)">
        <animate attributeName="r" values="20;25;20" dur="2s" repeatCount="indefinite"/>
      </circle>
      <circle cx="300" cy="200" r="25" fill="url(#nodeGrad)">
        <animate attributeName="r" values="25;30;25" dur="3s" repeatCount="indefinite"/>
      </circle>
      <circle cx="500" cy="150" r="22" fill="url(#nodeGrad)">
        <animate attributeName="r" values="22;27;22" dur="2.5s" repeatCount="indefinite"/>
      </circle>
      <circle cx="700" cy="250" r="20" fill="url(#nodeGrad)">
        <animate attributeName="r" values="20;25;20" dur="3.5s" repeatCount="indefinite"/>
      </circle>
      <circle cx="200" cy="300" r="18" fill="url(#nodeGrad)">
        <animate attributeName="r" values="18;23;18" dur="2s" repeatCount="indefinite"/>
      </circle>
      <circle cx="400" cy="100" r="22" fill="url(#nodeGrad)">
        <animate attributeName="r" values="22;27;22" dur="3s" repeatCount="indefinite"/>
      </circle>
      <circle cx="600" cy="350" r="20" fill="url(#nodeGrad)">
        <animate attributeName="r" values="20;25;20" dur="2.5s" repeatCount="indefinite"/>
      </circle>
      <!-- Pulsing center -->
      <circle cx="400" cy="200" r="40" fill="url(#pulseGrad)">
        <animate attributeName="r" values="40;60;40" dur="2s" repeatCount="indefinite"/>
        <animate attributeName="opacity" values="0.5;0.1;0.5" dur="2s" repeatCount="indefinite"/>
      </circle>
    </svg>
  </div>

  <div class="hero-content">
    <h1 class="hero-title">🧠 MATRIX</h1>
    <p class="hero-tagline">The Hybrid Intelligence Encyclopedia</p>
    <p class="hero-description">
      An open-source <strong>neuro-symbolic</strong> AI system that combines boolean logic,
      hyperdimensional computing, and emergent swarm intelligence — without the bloat of large language models.
    </p>

    <div class="hero-stats">
      <div class="stat">
        <span class="stat-number">724</span>
        <span class="stat-label">Tests Passing</span>
      </div>
      <div class="stat">
        <span class="stat-number">33,000×</span>
        <span class="stat-label">Faster than LLMs</span>
      </div>
      <div class="stat">
        <span class="stat-number">100%</span>
        <span class="stat-label">Constitution Compliant</span>
      </div>
    </div>

    <div class="hero-buttons">
      <a href="story/overview.html" class="btn btn-primary">🚀 Start the Journey</a>
      <a href="guide/getting-started.html" class="btn btn-secondary">⚡ Quick Start</a>
      <a href="science-v2/math-foundations.html" class="btn btn-secondary">📐 The Math</a>
    </div>
  </div>
</div>

<div class="audience-toggles" id="audience-toggles">
  <h2>Who are you?</h2>
  <div class="toggle-buttons">
    <button class="toggle-btn active" data-audience="beginner">🌱 I'm Curious</button>
    <button class="toggle-btn" data-audience="engineer">🔧 I Build Things</button>
    <button class="toggle-btn" data-audience="scientist">🔬 I Prove Things</button>
  </div>
  <div class="toggle-content" id="beginner-content">
    <h3>Welcome, curious mind!</h3>
    <p>MATRIX is like a <strong>city of minds</strong>. Each "citizen" is a simple thinking unit, but together they create something greater than the sum of their parts. No one citizen is in charge — the intelligence emerges from their collaboration.</p>
    <p><a href="story/overview.html">→ Read the Story</a></p>
  </div>
  <div class="toggle-content hidden" id="engineer-content">
    <h3>Welcome, builder!</h3>
    <p>MATRIX is a <strong>production-grade hybrid AI framework</strong>. BIR for logic, HDC for patterns, MCTS for planning, biochemical modulators for adaptive behavior. 33,000× faster than LLMs. 724 tests passing. CONSTITUTION-compliant.</p>
    <p><a href="guide/getting-started.html">→ Get Started in 5 Minutes</a></p>
  </div>
  <div class="toggle-content hidden" id="scientist-content">
    <h3>Welcome, researcher!</h3>
    <p>MATRIX is a <strong>mathematically rigorous system</strong> with formal proofs, benchmarks, and 90% accuracy retention from LLM distillation. Every claim is backed by tests, benchmarks, or TLA+ specs.</p>
    <p><a href="science-v2/math-foundations.html">→ See the Math</a></p>
  </div>
</div>

<div class="key-concepts">
  <h2>The Five Pillars of MATRIX</h2>
  <div class="pillar-grid">
    <div class="pillar">
      <div class="pillar-icon">🔮</div>
      <h3>BIR: Logic</h3>
      <p>Boolean inference engine — fast, deterministic, explainable. 75% accuracy on logic tasks.</p>
      <a href="story/brain-analogy.html#bir">Learn more →</a>
    </div>
    <div class="pillar">
      <div class="pillar-icon">🌌</div>
      <h3>HDC: Patterns</h3>
      <p>Hyperdimensional computing — learns from 50 examples, not 50,000. 89.9% AUC score.</p>
      <a href="story/brain-analogy.html#hdc">Learn more →</a>
    </div>
    <div class="pillar">
      <div class="pillar-icon">♟️</div>
      <h3>MCTS: Planning</h3>
      <p>Monte Carlo Tree Search — thinks 5 moves ahead. 92% success in Minecraft survival.</p>
      <a href="story/brain-analogy.html#mcts">Learn more →</a>
    </div>
    <div class="pillar">
      <div class="pillar-icon">🧪</div>
      <h3>Biochemistry</h3>
      <p>Non-linear modulator interactions — creates realistic "moods" and stress responses.</p>
      <a href="story/brain-analogy.html#biochemistry">Learn more →</a>
    </div>
    <div class="pillar">
      <div class="pillar-icon">🐝</div>
      <h3>Federation</h3>
      <p>Liquid swarm of nodes that share knowledge and emerge solutions collectively.</p>
      <a href="story/federation.html">Learn more →</a>
    </div>
  </div>
</div>

<div class="interactive-demo">
  <h2>Try It: Interactive Modulator Simulator</h2>
  <p>Adjust the sliders to see how modulators interact in real-time. This runs entirely in your browser — no server calls.</p>

  <div id="modulator-simulator" class="simulator-container">
    <div class="slider-row">
      <label>DOPAMINE: <span id="sim-dop">0.50</span></label>
      <input type="range" id="sim-dop-slider" min="0" max="1" step="0.05" value="0.5">
    </div>
    <div class="slider-row">
      <label>CORTISOL: <span id="sim-cor">0.20</span></label>
      <input type="range" id="sim-cor-slider" min="0" max="1" step="0.05" value="0.2">
    </div>
    <div class="slider-row">
      <label>SEROTONIN: <span id="sim-ser">0.50</span></label>
      <input type="range" id="sim-ser-slider" min="0" max="1" step="0.05" value="0.5">
    </div>
    <div class="slider-row">
      <label>NOREPINEPHRINE: <span id="sim-nor">0.30</span></label>
      <input type="range" id="sim-nor-slider" min="0" max="1" step="0.05" value="0.3">
    </div>
    <div class="mood-display">
      <strong>Current Mood: <span id="sim-mood">NEUTRAL</span></strong>
    </div>
    <div class="stress-cascade">
      <strong>Stress Cascade Active: <span id="sim-cascade">No</span></strong>
    </div>
  </div>
</div>

<div class="benchmark-section">
  <h2>Performance at a Glance</h2>
  <div class="benchmark-grid">
    <div class="benchmark-card">
      <h3>Logic Tasks</h3>
      <div class="benchmark-value">75%</div>
      <p>vs 25% random baseline</p>
    </div>
    <div class="benchmark-card">
      <h3>Sample Efficiency</h3>
      <div class="benchmark-value">89.9%</div>
      <p>AUC with only 50 examples</p>
    </div>
    <div class="benchmark-card">
      <h3>Energy Efficiency</h3>
      <div class="benchmark-value">33,000×</div>
      <p>vs GPU-based LLMs</p>
    </div>
    <div class="benchmark-card">
      <h3>Federation Resilience</h3>
      <div class="benchmark-value">100%</div>
      <p>consensus with 50% nodes killed</p>
    </div>
  </div>
  <p><a href="research/BENCHMARK-REPORT-W620.html">→ Full benchmark report</a></p>
</div>

<div class="cta-section">
  <h2>Ready to dive deeper?</h2>
  <div class="cta-grid">
    <a href="story/overview.html" class="cta-card">
      <h3>📖 Read the Story</h3>
      <p>Understand MATRIX through analogies and visualizations. No jargon.</p>
    </a>
    <a href="guide/getting-started.html" class="cta-card">
      <h3>⚡ Get Started</h3>
      <p>5-minute setup. Docker, native, or JVM. Run your first query in minutes.</p>
    </a>
    <a href="science-v2/math-foundations.html" class="cta-card">
      <h3>🔬 See the Science</h3>
      <p>Formal proofs, benchmarks, and mathematical foundations.</p>
    </a>
  </div>
</div>

<script>
// Audience toggle
document.querySelectorAll('.toggle-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.toggle-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    document.querySelectorAll('.toggle-content').forEach(c => c.classList.add('hidden'));
    const target = btn.getAttribute('data-audience') + '-content';
    document.getElementById(target).classList.remove('hidden');
  });
});

// Modulator simulator
function updateSimulator() {
  const dop = parseFloat(document.getElementById('sim-dop-slider').value);
  const cor = parseFloat(document.getElementById('sim-cor-slider').value);
  const ser = parseFloat(document.getElementById('sim-ser-slider').value);
  const nor = parseFloat(document.getElementById('sim-nor-slider').value);

  document.getElementById('sim-dop').textContent = dop.toFixed(2);
  document.getElementById('sim-cor').textContent = cor.toFixed(2);
  document.getElementById('sim-ser').textContent = ser.toFixed(2);
  document.getElementById('sim-nor').textContent = nor.toFixed(2);

  let mood = "NEUTRAL";
  if (cor > 0.7 && ser < 0.3) mood = "STRESSED";
  else if (dop > 0.7 && ser > 0.6) mood = "HAPPY";
  else if (dop < 0.3 && ser < 0.3) mood = "LOW";
  else if (nor > 0.7) mood = "ALERT";
  else if (dop > 0.5 && nor > 0.5) mood = "FLOW";

  document.getElementById('sim-mood').textContent = mood;

  // Stress cascade: high cortisol suppresses dopamine
  const cascade = cor > 0.5;
  document.getElementById('sim-cascade').textContent = cascade ? "Yes (Cortisol → Dopamine suppression)" : "No";
  document.getElementById('sim-cascade').style.color = cascade ? "#ff6b6b" : "#51cf66";
}

document.querySelectorAll('#modulator-simulator input[type="range"]').forEach(slider => {
  slider.addEventListener('input', updateSimulator);
});
updateSimulator();
</script>
