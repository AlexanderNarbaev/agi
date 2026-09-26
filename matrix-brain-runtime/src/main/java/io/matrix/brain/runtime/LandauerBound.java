package io.matrix.brain.runtime;

/**
 * TRUE-W11 iteration #10 — Landauer bound on mind learning.
 *
 * <p>The minimum thermodynamic energy required to erase one bit of
 * information is kT × ln(2), where k is Boltzmann's constant and T
 * is the absolute temperature. At room temperature (300K) this is
 * approximately 2.85 × 10⁻²¹ J per bit.</p>
 *
 * <p>This class computes the minimum energy cost for the mind to
 * erase N bits of memory — useful for sanity-checking "infinite
 * memory" or "instant learning" claims against physics.</p>
 */
public final class LandauerBound {

    /** Boltzmann constant in J/K. */
    public static final double K_BOLTZMANN = 1.380649e-23;

    /** Standard room temperature (300K). */
    public static final double T_ROOM = 300.0;

    /** Minimum energy per bit at room temperature: kT ln(2) in joules. */
    public static final double E_MIN_ROOM = K_BOLTZMANN * T_ROOM * Math.log(2);

    /** Landauer bound at given temperature (K) per bit erased. */
    public static double perBitJoules(double temperatureK) {
        if (temperatureK <= 0) throw new IllegalArgumentException("T > 0");
        return K_BOLTZMANN * temperatureK * Math.log(2);
    }

    /** Total minimum energy (J) to erase nBits at given temperature. */
    public static double totalJoules(double temperatureK, long nBits) {
        if (nBits < 0) throw new IllegalArgumentException("nBits >= 0");
        return perBitJoules(temperatureK) * nBits;
    }

    /** Total minimum energy (J) to erase nBits at room temperature. */
    public static double totalJoulesRoom(long nBits) {
        return totalJoules(T_ROOM, nBits);
    }

    /**
     * Maximum theoretical "forget rate" (bits/s) for a given power budget.
     * Useful to upper-bound how aggressively the mind can consolidate.
     */
    public static double maxForgetRateBitsPerSec(double temperatureK, double watts) {
        if (watts <= 0) throw new IllegalArgumentException("watts > 0");
        return watts / perBitJoules(temperatureK);
    }
}
