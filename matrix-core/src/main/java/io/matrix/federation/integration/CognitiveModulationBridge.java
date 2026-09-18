package io.matrix.federation.integration;

import io.matrix.federation.runtime.FederationRuntime;
import io.matrix.consciousness.CognitiveGenesisProfile;

import java.util.Optional;

/**
 * W369 — Bridge between federation modulators and CognitiveGenesisProfile.
 * 
 * Reads modulator values from the federation runtime and produces
 * cognitive profiles with hormonal modulation applied.
 * 
 * Per SPEC-013 Phase 3: Integration with cognitive layers.
 */
public final class CognitiveModulationBridge {
    
    /** Modulator IDs that affect cognitive processing. */
    public static final String MOD_CORTISOL = "cortisol_like_stress";
    public static final String MOD_DOPAMINE = "dopamine_reward";
    
    private final BiochemicalMediator mediator;
    
    public CognitiveModulationBridge(BiochemicalMediator mediator) {
        if (mediator == null) {
            throw new IllegalArgumentException("mediator cannot be null");
        }
        this.mediator = mediator;
    }
    
    /**
     * Apply hormonal modulation to a cognitive profile.
     */
    public CognitiveGenesisProfile modulate(CognitiveGenesisProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile cannot be null");
        }
        
        Optional<Float> cortisol = mediator.getValue(MOD_CORTISOL);
        Optional<Float> dopamine = mediator.getValue(MOD_DOPAMINE);
        
        double cortisolVal = cortisol.orElse(0.5f);
        double dopamineVal = dopamine.orElse(0.5f);
        
        // High cortisol → lower phiBinary (stress impairs integration)
        double modulatedPhi = profile.phiBinary() * (1.0 - 0.3 * cortisolVal);
        
        // High dopamine → higher phiF (reward enhances feedback)
        double modulatedPhiF = profile.phiF() * (1.0 + 0.2 * dopamineVal);
        
        // Bound to [0, 1]
        modulatedPhi = Math.max(0.0, Math.min(1.0, modulatedPhi));
        modulatedPhiF = Math.max(0.0, Math.min(1.0, modulatedPhiF));
        
        return new CognitiveGenesisProfile(
            modulatedPhi,
            modulatedPhiF,
            profile.phiR(),
            profile.phiLinGauss(),
            profile.interAgentPhi(),
            profile.stabilityPhi(),
            profile.crossLevelPhi(),
            profile.kolmogorovK(),
            profile.analogicalSimilarity(),
            profile.conceptualExclusion(),
            profile.nkEdgeOfChaosK(),
            profile.memristorConductance(),
            profile.lSystemComplexityRatio()
        );
    }
    
    public BiochemicalMediator getMediator() {
        return mediator;
    }
    
    public FederationRuntime getRuntime() {
        return mediator.getRuntime();
    }
}
