package io.matrix.knowledge.acquisition;

import java.util.EnumSet;
import java.util.Set;

/**
 * The promotion layers of an acquired claim (RECON-W42.2).
 *
 * <p>The mission's §5.4 names eight layers:
 * `RAW → EXTRACTED → CANDIDATE → SUPPORTED → VERIFIED → DISPUTED →
 * SUPERSEDED → INVALIDATED`.
 *
 * <p><b>The layers are not a score.</b> A layer says what the system has
 * done with the claim and what it has behind it — not how likely it is to
 * be true. A claim at SUPPORTED has evidence; a claim at VERIFIED has
 * evidence from a second, independent source; a claim at DISPUTED has
 * evidence on both sides. None of that is a probability, and the support
 * score the W41 store carries remains explicitly uncalibrated.
 */
public enum PromotionStage {

    /** The document's bytes. Nothing has been read yet. */
    RAW,
    /** The text has been read and segmented. */
    EXTRACTED,
    /** A claim has been proposed from a segment. It has no evidence yet. */
    CANDIDATE,
    /** The claim has at least one piece of evidence behind it. */
    SUPPORTED,
    /** The claim has evidence from a second, independent source. */
    VERIFIED,
    /** The sources disagree about the claim. */
    DISPUTED,
    /** A newer claim replaced this one. */
    SUPERSEDED,
    /** The claim was refuted, or its evidence was withdrawn. */
    INVALIDATED;

    /** The layers a claim may move to from here. */
    public Set<PromotionStage> successors() {
        return switch (this) {
            case RAW -> EnumSet.of(EXTRACTED);
            case EXTRACTED -> EnumSet.of(CANDIDATE);
            case CANDIDATE -> EnumSet.of(SUPPORTED, INVALIDATED);
            case SUPPORTED -> EnumSet.of(VERIFIED, DISPUTED, SUPERSEDED, INVALIDATED);
            case VERIFIED -> EnumSet.of(DISPUTED, SUPERSEDED, INVALIDATED);
            case DISPUTED -> EnumSet.of(SUPPORTED, INVALIDATED, SUPERSEDED);
            case SUPERSEDED -> EnumSet.noneOf(PromotionStage.class);
            case INVALIDATED -> EnumSet.noneOf(PromotionStage.class);
        };
    }

    /** Whether a move from this layer to another is legal. */
    public boolean mayMoveTo(PromotionStage target) {
        return target != null && successors().contains(target);
    }

    /** Whether the layer means the claim is in the knowledge store as trusted. */
    public boolean trusted() {
        return this == SUPPORTED || this == VERIFIED;
    }
}
