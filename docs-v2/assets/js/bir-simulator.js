/**
 * MATRIX BIR Simulator (W786)
 * Client-side Boolean Inference Rule engine.
 * NO LLM, NO server calls. Pure JS.
 */

class BIRSimulator {
  constructor() {
    this.rules = [];
    this.variables = new Set();
  }

  addRule(name, premises, conclusion) {
    // premises: array of {var, negated}
    this.rules.push({ name, premises, conclusion });
    premises.forEach(p => this.variables.add(p.var));
    this.variables.add(conclusion);
  }

  evaluate(facts) {
    // facts: {var: true/false}
    const results = [];
    for (const rule of this.rules) {
      const allTrue = rule.premises.every(p => {
        const val = facts[p.var] || false;
        return p.negated ? !val : val;
      });
      results.push({
        rule: rule.name,
        fired: allTrue,
        conclusion: allTrue ? rule.conclusion : null
      });
    }
    return results;
  }
}

// UI Controller
document.addEventListener('DOMContentLoaded', () => {
  const container = document.getElementById('bir-simulator');
  if (!container) return;

  const sim = new BIRSimulator();

  // Default rules
  sim.addRule('Ethical', [
    { var: 'harm_others', negated: false }
  ], 'unethical');

  sim.addRule('Logical', [
    { var: 'rain', negated: false },
    { var: 'umbrella', negated: true }
  ], 'wet');

  sim.addRule('Inference', [
    { var: 'A', negated: false },
    { var: 'B', negated: false }
  ], 'C');

  // Build UI
  let html = '<div class="bir-ui">';
  html += '<h3>Define Rules</h3>';
  html += '<div id="bir-rules">';
  sim.rules.forEach((rule, i) => {
    html += `<div class="bir-rule">
      <strong>${rule.name}:</strong> IF ${rule.premises.map(p => (p.negated ? 'NOT ' : '') + p.var).join(' AND ')} THEN ${rule.conclusion}
    </div>`;
  });
  html += '</div>';
  html += '<h3>Set Facts</h3>';
  html += '<div id="bir-facts">';
  Array.from(sim.variables).forEach(v => {
    html += `<label><input type="checkbox" class="bir-fact" data-var="${v}"> ${v}</label><br>`;
  });
  html += '</div>';
  html += '<h3>Results</h3>';
  html += '<div id="bir-results"></div>';
  html += '</div>';
  container.innerHTML = html;

  // Wire up
  container.addEventListener('change', () => {
    const facts = {};
    document.querySelectorAll('.bir-fact').forEach(cb => {
      facts[cb.dataset.var] = cb.checked;
    });
    const results = sim.evaluate(facts);
    let out = '<ul>';
    results.forEach(r => {
      const status = r.fired ? '✅ FIRED' : '⏸️ WAITING';
      const conc = r.fired ? ` → <strong>${r.conclusion}</strong>` : '';
      out += `<li>${r.rule}: ${status}${conc}</li>`;
    });
    out += '</ul>';
    document.getElementById('bir-results').innerHTML = out;
  });

  // Initial evaluation
  container.dispatchEvent(new Event('change'));
});
