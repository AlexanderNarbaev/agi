package io.matrix.consciousness;

import io.matrix.consciousness.PhiId.PhiIdSystem;

/**
 * Wave 92 — Extended integration metrics: closed-form linear-Gaussian Φ + PhiID 4-atom decomposition.
 *
 * <p>Extends the discrete Φ_binary/ΦR/ΦF/C_N/ticklingFlag set with continuous metrics that
 * complement the discrete tier:
 * <ul>
 *   <li>{@code phiLinGauss}: closed-form linear-Gaussian Φ via covariance ln-determinant
 *       (Barrett-Seth 2011). O(2^N · N³) for N ≤ 16.</li>
 *   <li>{@code phiIdRedundancy / synergy / unqX / unqY}: Mediano-Seth-Barrett 2020 4-atom PID
 *       decomposition for the trajectory's effective Gaussian distribution.</li>
 * </ul>
 *
 * <p>CONSTITUTION VI compliance: these are information-theoretic measurements, not
 * phenomenal consciousness claims.
 */
public record ExtendedIntegrationMetrics(
        Double phiLinGauss,
        Double phiIdRedundancy,
        Double phiIdSynergy,
        Double phiIdUnqX,
        Double phiIdUnqY,
        Integer pairCount) {

    /**
     * Build from raw Φ_linGauss value and a PhiId.PhiIdSystem, or return null if either
     * input is null.
     */
    public static ExtendedIntegrationMetrics of(Double phiLinGauss, PhiIdSystem system) {
        if (phiLinGauss == null && system == null) return null;
        return new ExtendedIntegrationMetrics(
                phiLinGauss,
                system != null ? system.redundancy() : null,
                system != null ? system.synergy() : null,
                system != null ? system.unqX() : null,
                system != null ? system.unqY() : null,
                system != null ? system.pairCount() : null);
    }

    public boolean isEmpty() {
        return phiLinGauss == null
                && phiIdRedundancy == null
                && phiIdSynergy == null
                && phiIdUnqX == null
                && phiIdUnqY == null;
    }
}
