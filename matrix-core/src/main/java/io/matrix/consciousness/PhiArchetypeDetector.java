package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * W194 — Φ archetype detector.
 *
 * <p>Identify recurring archetypes (typical patterns) in cognitive
 * profile sequences. An archetype is a profile pattern that occurs
 * multiple times across the sequence.
 *
 * <p>Uses coarse-graining: bin each profile field into K bins, then
 * count distinct (binned) profiles. Profiles in the same bin are
 * considered same archetype.
 *
 * <p>CONSTITUTION VI compliance: archetype detection on cognitive
 * state descriptors, not phenomenal consciousness claim.
 */
public final class PhiArchetypeDetector {

    private PhiArchetypeDetector() {}

    /**
     * Bin a single profile field value into [0, nBins-1].
     */
    public static int bin(double value, int nBins) {
        if (nBins < 1) return 0;
        int bin = (int) (value * nBins);
        if (bin >= nBins) bin = nBins - 1;
        if (bin < 0) bin = 0;
        return bin;
    }

    /**
     * Convert a profile to a bin signature (one int per field).
     */
    public static int[] signature(CognitiveGenesisProfile profile, int nBins) {
        int[] sig = new int[13];
        sig[0] = bin(profile.phiBinary(), nBins);
        sig[1] = bin(profile.phiF(), nBins);
        sig[2] = bin(profile.phiR(), nBins);
        sig[3] = bin(profile.phiLinGauss(), nBins);
        sig[4] = bin(profile.interAgentPhi(), nBins);
        sig[5] = bin(profile.stabilityPhi(), nBins);
        sig[6] = bin(profile.crossLevelPhi(), nBins);
        sig[7] = bin(Math.min(1.0, profile.kolmogorovK() / 100.0), nBins);
        sig[8] = bin(profile.analogicalSimilarity(), nBins);
        sig[9] = bin(profile.conceptualExclusion(), nBins);
        sig[10] = bin(profile.nkEdgeOfChaosK() / 8.0, nBins);
        sig[11] = bin(profile.memristorConductance(), nBins);
        sig[12] = bin(Math.min(1.0, profile.lSystemComplexityRatio() / 5.0), nBins);
        return sig;
    }

    /**
     * Convert signature array to a string key.
     */
    public static String signatureKey(int[] sig) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sig.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(sig[i]);
        }
        return sb.toString();
    }

    /**
     * Find archetypes in a sequence of profiles.
     * Returns list of (archetype_signature, count, first_cycle_index).
     */
    public static List<Archetype> findArchetypes(List<CognitiveGenesisProfile> profiles, int nBins) {
        List<Archetype> archetypes = new ArrayList<>();
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        java.util.Map<String, int[]> firstSig = new java.util.HashMap<>();
        java.util.Map<String, Integer> firstCycle = new java.util.HashMap<>();
        for (int i = 0; i < profiles.size(); i++) {
            int[] sig = signature(profiles.get(i), nBins);
            String key = signatureKey(sig);
            counts.merge(key, 1, Integer::sum);
            if (!firstSig.containsKey(key)) {
                firstSig.put(key, sig);
                firstCycle.put(key, i);
            }
        }
        for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
            archetypes.add(new Archetype(firstSig.get(entry.getKey()), entry.getValue(), firstCycle.get(entry.getKey())));
        }
        // Sort by count descending
        archetypes.sort((a, b) -> Integer.compare(b.count(), a.count()));
        return archetypes;
    }

    /**
     * Top-K most common archetypes.
     */
    public static List<Archetype> topArchetypes(List<CognitiveGenesisProfile> profiles, int nBins, int k) {
        List<Archetype> all = findArchetypes(profiles, nBins);
        return all.subList(0, Math.min(k, all.size()));
    }

    public record Archetype(int[] signature, int count, int firstCycleIndex) {}
}
