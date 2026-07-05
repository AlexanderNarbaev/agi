📍 Status: v2.4.0 — Multi-agent swarm + online training implemented. Spigot plugin: /matrix list|add|remove|switch, AgentRole enum, BotState per-agent, feedback + hill-climbing retraining. Neuron sharing endpoints (/agent/share, /agent/neurons/{role}). 6 files changed, 621 lines added.
🚀 Active: Next — test multi-agent in real Minecraft, verify online training convergence, wire neuron sharing between bots.
🛑 Protected: K_MAX=20, coverage floor 82%, backward compat (deprecated accessors), single WebSocket for all agents, AGPLv3+ethics
