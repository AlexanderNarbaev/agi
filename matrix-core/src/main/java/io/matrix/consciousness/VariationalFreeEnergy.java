package io.matrix.consciousness;

import java.util.Random;

/**
 * W182 — Variational Free Energy (Friston).
 *
 * <p>Computes VFE = E_q[log q(s) - log p(o,s)] for cognitive states.
 * This is the bound on surprise used in active inference.
 *
 * <p>For MATRIX cognitive profiles, we approximate:
 * - States = cognitive profile fields
 * - Observations = current cycle measurements
 * - Generative model = learned profile distribution
 *
 * <p>VFE lower bound on log model evidence (ELBO):
 * VFE ≥ -log p(o) ⟹ minimizing VFE increases model evidence
 *
 * <p>CONSTITUTION VI compliance: variational bound on cognitive
 * state likelihood, not phenomenal consciousness claim.
 */
public final class VariationalFreeEnergy {

    private VariationalFreeEnergy() {}

    /**
     * Compute VFE for a single cognitive profile against a reference
     * (e.g., mean profile).
     *
     * @param profile the current profile
     * @param reference the reference (mean) profile
     * @return VFE in bits (KL-based, non-negative)
     */
    public static double vfe(CognitiveGenesisProfile profile, CognitiveGenesisProfile reference) {
        if (profile == null || reference == null) return 0.0;
        // Compute KL divergence as approximation of VFE
        // KL(P_profile || P_reference)
        double[] p = profileToVector(profile);
        double[] q = profileToVector(reference);
        // Normalize to probabilities
        double sumP = sum(p), sumQ = sum(q);
        if (sumP == 0 || sumQ == 0) return 0.0;
        for (int i = 0; i < p.length; i++) {
            p[i] /= sumP;
            q[i] /= sumQ;
        }
        double kl = 0;
        for (int i = 0; i < p.length; i++) {
            if (p[i] > 0) {
                if (q[i] == 0) return Double.POSITIVE_INFINITY;
                kl += p[i] * Math.log(p[i] / q[i]);
            }
        }
        return kl / Math.log(2);  // log base 2
    }

    /**
     * Compute expected free energy for a profile given a target:
     * epistemic + pragmatic value.
     *
     * @param profile current profile
     * @param target desired profile
     * @return expected free energy (lower = more aligned with target)
     */
    public static double expectedFreeEnergy(CognitiveGenesisProfile profile,
                                              CognitiveGenesisProfile target) {
        if (profile == null || target == null) return 0.0;
        // Use ProfileDistance as proxy for expected free energy
        return ProfileDistance.l2Distance(profile, target);
    }

    /**
     * Active inference: select action index minimizing EFE.
     *
     * @param profiles list of candidate profiles
     * @param target target profile
     * @return index of profile with minimum EFE
     */
    public static int selectAction(java.util.List<CognitiveGenesisProfile> profiles,
                                     CognitiveGenesisProfile target) {
        if (profiles == null || profiles.isEmpty() || target == null) return -1;
        int bestIdx = 0;
        double bestEfe = Double.POSITIVE_INFINITY;
        for (int i = 0; i < profiles.size(); i++) {
            double efe = expectedFreeEnergy(profiles.get(i), target);
            if (efe < bestEfe) {
                bestEfe = efe;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /**
     * Compute ELBO (Evidence Lower Bound) for profile sequences:
     * mean likelihood - KL divergence from prior.
     *
     * @param profiles observed profiles
     * @param prior prior mean profile (default: zeros)
     * @return ELBO in bits (higher is better)
     */
    public static double elbo(java.util.List<CognitiveGenesisProfile> profiles,
                               CognitiveGenesisProfile prior) {
        if (profiles == null || profiles.isEmpty() || prior == null) return 0.0;
        CognitiveGenesisProfile mean = meanProfile(profiles);
        double kl = vfe(mean, prior);
        if (Double.isInfinite(kl)) return 0.0;
        // Likelihood approximation: -mean VFE within sequence
        double meanInternalVfe = 0;
        int count = 0;
        for (int i = 1; i < profiles.size(); i++) {
            meanInternalVfe += vfe(profiles.get(i), profiles.get(i - 1));
            count++;
        }
        if (count == 0) return -kl;
        return -(meanInternalVfe / count) - kl;
    }

    private static CognitiveGenesisProfile meanProfile(java.util.List<CognitiveGenesisProfile> profiles) {
        double phiB = 0, phiF = 0, phiR = 0, phiLG = 0;
        double iap = 0, stab = 0, clp = 0, k = 0;
        double anal = 0, excl = 0, mem = 0;
        for (CognitiveGenesisProfile p : profiles) {
            phiB += p.phiBinary();
            phiF += p.phiF();
            phiR += p.phiR();
            phiLG += p.phiLinGauss();
            iap += p.interAgentPhi();
            stab += p.stabilityPhi();
            clp += p.crossLevelPhi();
            k += p.kolmogorovK();
            anal += p.analogicalSimilarity();
            excl += p.conceptualExclusion();
            mem += p.memristorConductance();
        }
        int n = profiles.size();
        return new CognitiveGenesisProfile(
            phiB / n, phiF / n, phiR / n, phiLG / n,
            iap / n, stab / n, clp / n,
            k / n, anal / n, excl / n,
            2, mem / n, 1.0
        );
    }

    private static double[] profileToVector(CognitiveGenesisProfile p) {
        return new double[]{
            p.phiBinary(), p.phiF(), p.phiR(), p.phiLinGauss(),
            p.interAgentPhi(), p.stabilityPhi(), p.crossLevelPhi(),
            Math.min(1.0, p.kolmogorovK() / 100.0),
            p.analogicalSimilarity(), p.conceptualExclusion(),
            p.nkEdgeOfChaosK() / 8.0, p.memristorConductance(),
            p.lSystemComplexityRatio() / 5.0
        };
    }

    private static double sum(double[] v) {
        double s = 0;
        for (double x : v) s += x;
        return s;
    }
}
