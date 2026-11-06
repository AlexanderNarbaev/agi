#!/usr/bin/env python3
"""W44.0 — subsystem evidence extraction (read-only).

Answers, from the inventory and the bytecode call graph, the state of every
class the RECON-W44.0 directive names by family (A cognitive execution,
B perception, C substrate, D prediction/self-model, E neuromodulation,
F goals/autonomy, G memory/learning, H federation, I integration/measurement).

The output is a table per family: the class, its real FQN, its module, its
production reachability, how many production callers it has, how many test
classes touch it, and whether it is a CDI/REST entry point.

Usage:
    scripts/w44-audit-subsystems.py > docs-v2/engineering/W44-SUBSYSTEM-EVIDENCE.md
"""

from __future__ import annotations

import csv
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INV = ROOT / "docs-v2" / "engineering" / "W44-CLASS-INVENTORY.csv"

FAMILIES: dict[str, list[str]] = {
    "A. Cognitive execution": [
        "TrueMindCycle", "MindCycle", "BirBrainCycle", "ConsciousnessLoop",
        "BrainLoopService", "BrainLoopServiceV2", "BrainLoopTopology",
        "ConsciousBrain", "CognitiveProcessor", "CognitiveAdaptiveCompute",
        "MindRuntimeCli", "ProductionBrainClient", "MinimalHttpServer",
        "ConsciousLoop", "CognitiveGenesisProfile",
    ],
    "B. Perception and signal processing": [
        "SignalModule", "SignalModuleRegistry", "SignalRegistry", "TextEncoder",
        "TextSignalModule", "ImageSignalModule", "AudioSignalModule",
        "SensorPacket", "CrossModalAligner", "UnifiedRepresentation",
        "SaliencyEngine", "BrainVision", "SignalProcessor", "MultimodalExtractor",
        "TextEmbedder", "FeatureExtractor",
    ],
    "C. Neural and computational substrate": [
        "HDC", "HdcBrain", "HdcEncoding", "HdcBinding", "BitLinear", "BitNet",
        "BIR", "BooleanRuntime", "TruthTable", "CLAUSESET", "BDD", "Tsetlin",
        "WiSARD", "Hebbian", "SDM", "MPDT", "Dijkstra", "BellmanFord", "Trie",
        "BloomFilter", "SparseHDC", "TopologicalMemoryAnalyzer",
    ],
    "D. Prediction and self-model": [
        "PredictiveCoder", "PredictionModel", "SelfModel", "FreeEnergyLoss",
        "FreeEnergyEvaluator", "FreeEnergyEstimator", "ActiveInferenceEngine",
        "WuWeiPolicy", "PragmaticTest", "EmbodiedNcaCortex", "SensorimotorLoop",
        "SensorimotorCycle", "VariationalFreeEnergy", "NcaBrain",
    ],
    "E. Neuromodulation and regulation": [
        "NeuroModulatorHub", "BiochemicalNetwork", "BiochemicalOrchestrator",
        "KineticModulator", "CognitiveModulationBridge", "BiochemicalMediator",
        "Neurotransmitter", "ArousalDynamics", "DriverState", "Impulse",
        "AttentionRouter", "InstanceMediator", "ImpulseScheduler",
        "DynamicModulatorRegistry", "Regulator", "Modulator",
        "RegulationController", "StigmergyProtocol", "Neurotransmitter",
    ],
    "F. Goals, autonomy and action": [
        "GoalManager", "GoalTracker", "AgentLoop", "ReActAgentLoop",
        "AutonomyEngine", "SelfImprovingEngine", "AutonomyLoop", "ConsciousLoop",
        "TaskCell", "TaskCellV2", "FnlGate", "ActionArena", "PlanRunner",
        "ActionGate", "TaskScheduler", "Mediator", "HADES", "ConjugateBudgeter",
        "Executor", "ProactiveChat", "PilotProactiveChat",
        "ActionGatePolicy", "FnlGateV2", "ConjugateBudgeterMulti", "MultiAgentLoop",
        "CognitiveTask", "BrainLoopDemo",
    ],
    "G. Memory and learning": [
        "KnowledgeStore", "KnowledgeWorkbench", "KnowledgeRetriever",
        "HierarchicalMemory", "PersistentHierarchicalMemory", "PersistentHdcStore",
        "TwoStageConsolidator", "SleepConsolidator", "DreamReplayer",
        "ErrorDrivenLearner", "ReplayPhase", "ConsolidationCycle",
        "KnowledgeConsolidator", "MemoryIndex", "Case", "CognitiveStore",
        "ConsolidationCycle", "SleepCycle", "RealSleepScheduler",
    ],
    "H. Federation and distributed cognition": [
        "Noosphere", "CausalCrdt", "MeshFederation", "ElspChannel",
        "StigmergyProtocol", "IntegratedFederation", "ConsensusEngine",
        "CapabilityConsensusEngine", "EpochAwareCausalSet", "FederationNode",
    ],
    "I. Integration and measurement": [
        "Connectome", "CognitiveNode", "Synapse", "ReplayPhase",
        "IntegrationMetrics", "ExtendedIntegrationMetrics", "PhiId",
        "PhiLinGauss", "InterAgentPhi", "CognitiveGenesisProfile",
        "CognitiveGenesisProfileBuilder", "CognitiveGenesisProfileBuilder2",
        "CrossLevelPhi", "Synapse",
    ],
}


def main() -> int:
    with INV.open(encoding="utf-8") as fh:
        rows = list(csv.DictReader(fh))
    prod = {r["fqn"]: r for r in rows if r["source_set"] == "main" and r["built"] == "yes"}
    allrows = {r["fqn"]: r for r in rows}
    by_simple: dict[str, list[str]] = defaultdict(list)
    for fqn in prod:
        by_simple[fqn.rsplit(".", 1)[-1]].append(fqn)

    out = ["# W44.0 — subsystem evidence",
           "",
           "Generated by `scripts/w44-audit-subsystems.py` from "
           "`W44-CLASS-INVENTORY.csv` (source scan) and `W44-CALL-GRAPH.csv` "
           "(bytecode call graph). The `state` column is derived, never asserted:",
           "",
           "| state | meaning |",
           "|---|---|",
           "| production-reachable | at least one production caller in the bytecode call graph, or a CDI/REST/observer entry point |",
           "| inactive-but-tested | no production caller, but at least one test class references it |",
           "| dormant | no production caller and no test reference |",
           "| absent | no type with that name exists in a built module |",
           ""]
    for family, names in FAMILIES.items():
        out.append(f"## {family}")
        out.append("")
        out.append("| named in the directive | real FQN | module | state | prod callers | test refs | entry point |")
        out.append("|---|---|---|---|---|---|---|")
        for name in names:
            matches = by_simple.get(name, [])
            if not matches:
                out.append(f"| {name} | — | — | **absent** | — | — | — |")
                continue
            for fqn in sorted(matches):
                r = prod[fqn]
                out.append(f"| {name} | `{fqn}` | {r['module']} | {r['lifecycle']} | "
                           f"{r['incoming_callers']} | {r['test_callers']} | "
                           f"{r['entry_point_kind'] or '—'} |")
        out.append("")
    sys.stdout.write("\n".join(out) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
