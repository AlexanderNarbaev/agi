/**
 * MATRIX Federation Map (W788)
 * Interactive D3.js-style force-directed graph using SVG.
 * NO LLM, NO server calls. Pure client-side.
 */

class FederationMap {
  constructor(containerId, width = 600, height = 400) {
    this.container = document.getElementById(containerId);
    if (!this.container) return;
    this.width = width;
    this.height = height;
    this.nodes = [];
    this.links = [];
    this.render();
  }

  addNode(id, role, x, y) {
    const colors = {
      'GUARDIAN': '#ff6b6b',
      'ADULT': '#4fc3f7',
      'LEARNER': '#51cf66',
      'INFANT': '#ffd43b'
    };
    this.nodes.push({ id, role, x, y, color: colors[role] || '#888' });
    this.render();
  }

  addLink(sourceId, targetId, strength = 1) {
    this.links.push({ source: sourceId, target: targetId, strength });
    this.render();
  }

  render() {
    let svg = `<svg width="${this.width}" height="${this.height}" xmlns="http://www.w3.org/2000/svg">`;

    // Links
    this.links.forEach(link => {
      const s = this.nodes.find(n => n.id === link.source);
      const t = this.nodes.find(n => n.id === link.target);
      if (s && t) {
        svg += `<line x1="${s.x}" y1="${s.y}" x2="${t.x}" y2="${t.y}" stroke="#4fc3f7" stroke-width="${link.strength}" opacity="0.4"/>`;
      }
    });

    // Nodes
    this.nodes.forEach(node => {
      svg += `<circle cx="${node.x}" cy="${node.y}" r="15" fill="${node.color}" opacity="0.8">
        <animate attributeName="r" values="15;20;15" dur="2s" repeatCount="indefinite"/>
      </circle>`;
      svg += `<text x="${node.x}" y="${node.y + 30}" text-anchor="middle" fill="white" font-size="10">${node.role}</text>`;
    });

    svg += '</svg>';
    this.container.innerHTML = svg;
  }
}

// Demo: Create a sample federation
document.addEventListener('DOMContentLoaded', () => {
  const map = new FederationMap('federation-map-container', 600, 400);
  if (!map.container) return;

  // Create sample nodes
  map.addNode(1, 'GUARDIAN', 300, 80);
  map.addNode(2, 'ADULT', 200, 200);
  map.addNode(3, 'ADULT', 400, 200);
  map.addNode(4, 'LEARNER', 150, 320);
  map.addNode(5, 'LEARNER', 350, 320);
  map.addNode(6, 'INFANT', 250, 380);
  map.addNode(7, 'INFANT', 450, 380);

  // Create links (mesh topology)
  map.addLink(1, 2, 2);
  map.addLink(1, 3, 2);
  map.addLink(2, 3, 1);
  map.addLink(2, 4, 1);
  map.addLink(3, 5, 1);
  map.addLink(4, 6, 1);
  map.addLink(5, 7, 1);
  map.addLink(2, 5, 1);
  map.addLink(3, 4, 1);
});
