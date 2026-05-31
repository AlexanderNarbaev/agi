package io.matrix.mediator;

/**
 * Motivation driver types.
 *
 * <p>Ref: L4_Mediator.md §3.2
 */
public enum DriverType {

    /**
     * Need for computational resources and energy.
     * Rises under high load or low battery.
     */
    ENERGY,

    /**
     * Need for safety and stability.
     * Rises when anomalies, attacks, or internal errors are detected.
     */
    SAFETY,

    /**
     * Need for novelty and learning.
     * Rises after long periods without new data or successful predictions.
     */
    CURIOSITY
}
